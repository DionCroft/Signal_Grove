"""Real Windows provider behind checked broker frames; no production broker or listener."""
import hashlib
import json
import os
from pathlib import Path
import socket
import tempfile
import threading
from concurrent.futures import ThreadPoolExecutor
import unittest
from unittest.mock import patch
from server_extensions.worker_workspaces import broker_protocol as b
from server_extensions.worker_workspaces.workspace_provider import RegisteredProject,WorkspaceProvider


class BrokerNativeTest(unittest.TestCase):
    def setUp(self):
        self.parent=Path(os.environ['TEMP']).resolve(strict=True);self.assertNotEqual('c:',self.parent.drive.lower())
        self.temp=tempfile.TemporaryDirectory(prefix='worker-broker-',dir=self.parent);self.base=Path(self.temp.name).resolve(strict=True)
        self.assertTrue(self.base.is_relative_to(self.parent));self.addCleanup(self.cleanup);self.root=self.base/'project';self.root.mkdir()
        self.scope='1'*64;self.host=socket.gethostname().upper();self.context=b.CheckedReadContext(self.scope,self.host,'policy1',True);self.sequence=0
        self.binding=RegisteredProject('fixture',self.host,str(self.root),'registry1',self.scope)
        self.provider=WorkspaceProvider(lambda scope,ident:self.binding,scope_id=self.scope)
        self.adapter=b.BrokerReadAdapter(self.provider,checked_context=lambda:self.context)
    def cleanup(self):
        if self.base.exists():self.assertTrue(self.base.resolve(strict=True).is_relative_to(self.parent) and self.base!=self.parent)
        self.temp.cleanup()
    def frame(self,operation,**fields):
        ident=f'{self.sequence:032x}';self.sequence+=1
        return json.dumps(dict(schema=1,request_id=ident,worker_instance=self.adapter.instance,payload=dict(schema=1,scope_id=self.scope,operation=operation,project_id='fixture',**fields))).encode()
    def request(self,operation,**fields):return json.loads(self.adapter.handle(self.frame(operation,**fields)))
    def fixture(self):
        (self.root/'notes.txt').write_text('Signal Grove 🌱\n',encoding='utf8')
        token=self.request('prepare')['result']['binding_token']
        row=self.request('list',binding_token=token,relative_path='',entry_token=None)['result']['entries'][0]
        return dict(binding_token=token,relative_path=row['relative_path'],entry_token=row['entry_token'])
    def test_actual_prepare_list_preview_keeps_unicode_hash_and_correlation(self):
        fields=self.fixture();frame=self.frame('preview',**fields);reply=self.adapter.handle(frame);result=json.loads(reply)
        self.assertEqual(json.loads(frame)['request_id'],result['request_id']);self.assertEqual('ok',result['status'])
        raw=(self.root/'notes.txt').read_bytes();self.assertEqual(raw.decode(),result['result']['text']);self.assertEqual(hashlib.sha256(raw).hexdigest(),result['result']['preview_sha256']);self.assertLessEqual(len(reply),b.MAX_REPLY_BYTES)
    def test_lost_interactive_context_refuses_before_content_read(self):
        fields=self.fixture();native=self.provider._files();self.context=b.CheckedReadContext(self.scope,self.host,'policy1',False)
        with patch.object(native,'read',wraps=native.read) as read:
            reply=self.request('preview',**fields);self.assertEqual('DENIED',reply['code']);read.assert_not_called();self.assertNotIn('result',reply)
    def test_policy_revoked_after_real_read_drops_observed_content(self):
        fields=self.fixture();native=self.provider._files();original=native.read
        def revoked(handle,amount):
            result=original(handle,amount);self.context=b.CheckedReadContext(self.scope,self.host,'policy2',True);return result
        with patch.object(native,'read',side_effect=revoked):reply=self.request('preview',**fields)
        self.assertEqual('CHANGED',reply['code']);self.assertNotIn('result',reply);self.assertNotIn('Signal Grove',json.dumps(reply))
    def test_foreign_profile_scope_refuses_before_content_read(self):
        fields=self.fixture();frame=json.loads(self.frame('preview',**fields));frame['payload']['scope_id']='2'*64;native=self.provider._files()
        with patch.object(native,'read',wraps=native.read) as read:
            result=json.loads(self.adapter.handle(json.dumps(frame).encode()));self.assertEqual('CHANGED',result['code']);read.assert_not_called()
    def test_duplicate_request_id_does_not_repeat_provider_reads(self):
        fields=self.fixture();frame=self.frame('preview',**fields)
        with patch.object(self.provider,'dispatch',wraps=self.provider.dispatch) as call:
            self.assertEqual('ok',json.loads(self.adapter.handle(frame))['status']);self.assertEqual('DUPLICATE',json.loads(self.adapter.handle(frame))['code']);self.assertEqual(1,call.call_count)
    def test_close_during_read_retires_bindings_and_drops_content(self):
        fields=self.fixture();native=self.provider._files();original=native.read
        def closed(handle,amount):result=original(handle,amount);self.adapter.close();return result
        with patch.object(native,'read',side_effect=closed):reply=self.request('preview',**fields)
        self.assertEqual('UNAVAILABLE',reply['code']);self.assertNotIn('result',reply);self.assertEqual('UNAVAILABLE',self.request('prepare')['code'])
        with self.assertRaises(Exception) as caught:self.provider.prepare('fixture')
        self.assertEqual('UNAVAILABLE',str(caught.exception))
    def test_close_from_post_read_context_callback_cannot_return_content(self):
        fields=self.fixture();native=self.provider._files();original=native.read;observed=[False]
        def read(handle,amount):result=original(handle,amount);observed[0]=True;return result
        def context():
            if observed[0]:self.adapter.close()
            return self.context
        self.adapter._checked_context=context
        with patch.object(native,'read',side_effect=read):reply=self.request('preview',**fields)
        self.assertEqual('UNAVAILABLE',reply.get('code'));self.assertNotIn('result',reply)
    def test_trusted_context_failure_is_redacted_before_disk_read(self):
        fields=self.fixture();native=self.provider._files()
        def failed():raise RuntimeError('private credential and root')
        self.adapter._checked_context=failed
        with patch.object(native,'read',wraps=native.read) as read:
            reply=self.request('preview',**fields);self.assertEqual('UNAVAILABLE',reply['code']);read.assert_not_called();self.assertNotIn('private',json.dumps(reply))
    def test_four_actual_concurrent_reads_and_fifth_busy_are_bounded(self):
        fields=self.fixture();native=self.provider._files();original=native.read;entered=threading.Barrier(5);release=threading.Event()
        def bounded(handle,amount):entered.wait(timeout=10);self.assertTrue(release.wait(10));return original(handle,amount)
        frames=[self.frame('preview',**fields) for _ in range(5)]
        with patch.object(native,'read',side_effect=bounded),ThreadPoolExecutor(max_workers=4) as pool:
            futures=[pool.submit(self.adapter.handle,frame) for frame in frames[:4]]
            try:entered.wait(timeout=10);self.assertEqual('BUSY',json.loads(self.adapter.handle(frames[4]))['code'])
            finally:release.set()
            replies=[json.loads(f.result(timeout=15)) for f in futures]
        self.assertTrue(all(row['status']=='ok' for row in replies));self.assertEqual(4,len({row['request_id'] for row in replies}))
    def test_request_memory_budget_refuses_without_evicting_old_identity(self):
        token=self.request('prepare')['result']['binding_token'];fields=dict(binding_token=token,relative_path='',entry_token=None)
        for _ in range(b.MAX_REQUEST_IDS-1):self.assertEqual('ok',self.request('list',**fields)['status'])
        with patch.object(self.provider,'dispatch',wraps=self.provider.dispatch) as call:
            self.assertEqual('BUSY',self.request('list',**fields)['code']);call.assert_not_called()
