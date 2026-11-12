"""Real local Windows pipe acceptance with disposable owner storage."""
import ctypes
from ctypes import wintypes as w
from dataclasses import asdict,replace
import gc
import json
import os
from pathlib import Path
import socket
import tempfile
import subprocess
import sys
import threading
import time
from unittest.mock import patch
from concurrent.futures import ThreadPoolExecutor
import unittest
from server_extensions.worker_workspaces import broker_protocol as b
from server_extensions.worker_workspaces import local_pipe as t
from server_extensions.worker_workspaces.workspace_provider import RegisteredProject,WorkspaceProvider


class PipeNativeTest(unittest.TestCase):
    def setUp(self):
        self.parent=Path(os.environ['TEMP']).resolve(strict=True);self.assertNotEqual('c:',self.parent.drive.lower())
        self.temp=tempfile.TemporaryDirectory(prefix='worker-pipe-',dir=self.parent);self.base=Path(self.temp.name).resolve(strict=True)
        self.assertTrue(self.base.is_relative_to(self.parent));self.addCleanup(self.cleanup);self.root=self.base/'project';self.root.mkdir()
        self.scope='1'*64;self.host=socket.gethostname().upper();self.context=b.CheckedReadContext(self.scope,self.host,'policy1',True);self.sequence=0
        self.binding=RegisteredProject('fixture',self.host,str(self.root),'registry1',self.scope)
        self.provider=WorkspaceProvider(lambda scope,ident:self.binding,scope_id=self.scope)
        self.adapter=b.BrokerReadAdapter(self.provider,checked_context=lambda:self.context);self.pipe=t.LocalReadPipe(self.adapter);self.addCleanup(self.pipe.close)
    def cleanup(self):
        if self.base.exists():self.assertTrue(self.base.resolve(strict=True).is_relative_to(self.parent) and self.base!=self.parent)
        self.temp.cleanup()
    def frame(self,operation,**fields):
        ident=f'{self.sequence:032x}';self.sequence+=1
        return json.dumps(dict(schema=1,request_id=ident,worker_instance=self.adapter.instance,payload=dict(schema=1,scope_id=self.scope,operation=operation,project_id='fixture',**fields))).encode()
    def call(self,raw):
        with ThreadPoolExecutor(max_workers=1) as pool:
            server=pool.submit(self.pipe.serve_one,timeout=3)
            reply=t.call_once(self.pipe.endpoint,raw,timeout=3)
            self.assertEqual('served',server.result(timeout=5));return json.loads(reply)
    def test_real_owner_pipe_prepare_list_and_preview(self):
        (self.root/'notes.txt').write_text('Signal Grove 🌱',encoding='utf8');token=self.call(self.frame('prepare'))['result']['binding_token']
        row=self.call(self.frame('list',binding_token=token,relative_path='',entry_token=None))['result']['entries'][0]
        result=self.call(self.frame('preview',binding_token=token,relative_path=row['relative_path'],entry_token=row['entry_token']))
        self.assertEqual('Signal Grove 🌱',result['result']['text']);self.assertEqual('ok',result['status'])

    def assert_descriptor_owner(self,descriptor,expected_sid,mask):
        native=self.pipe._windows;a=native.a;present=w.BOOL();defaulted=w.BOOL();dacl=ctypes.c_void_p();control=w.WORD();revision=w.DWORD();expected=ctypes.c_void_p();ace=ctypes.c_void_p()
        class Acl(ctypes.Structure):_fields_=[('revision',w.BYTE),('pad',w.BYTE),('size',w.WORD),('count',w.WORD),('pad2',w.WORD)]
        class Ace(ctypes.Structure):_fields_=[('kind',w.BYTE),('flags',w.BYTE),('size',w.WORD),('mask',w.DWORD),('sid_start',w.DWORD)]
        a.GetSecurityDescriptorDacl.argtypes=[ctypes.c_void_p,ctypes.POINTER(w.BOOL),ctypes.POINTER(ctypes.c_void_p),ctypes.POINTER(w.BOOL)];a.GetSecurityDescriptorDacl.restype=w.BOOL
        a.GetSecurityDescriptorControl.argtypes=[ctypes.c_void_p,ctypes.POINTER(w.WORD),ctypes.POINTER(w.DWORD)];a.GetSecurityDescriptorControl.restype=w.BOOL
        a.GetAce.argtypes=[ctypes.c_void_p,w.DWORD,ctypes.POINTER(ctypes.c_void_p)];a.GetAce.restype=w.BOOL
        a.ConvertStringSidToSidW.argtypes=[w.LPCWSTR,ctypes.POINTER(ctypes.c_void_p)];a.ConvertStringSidToSidW.restype=w.BOOL
        a.EqualSid.argtypes=[ctypes.c_void_p,ctypes.c_void_p];a.EqualSid.restype=w.BOOL
        native.check(a.GetSecurityDescriptorDacl(descriptor,ctypes.byref(present),ctypes.byref(dacl),ctypes.byref(defaulted)));self.assertTrue(present.value);self.assertTrue(dacl.value);self.assertFalse(defaulted.value)
        native.check(a.GetSecurityDescriptorControl(descriptor,ctypes.byref(control),ctypes.byref(revision)));self.assertTrue(control.value&0x1000)
        self.assertEqual(1,ctypes.cast(dacl,ctypes.POINTER(Acl)).contents.count);native.check(a.GetAce(dacl,0,ctypes.byref(ace)));entry=ctypes.cast(ace,ctypes.POINTER(Ace)).contents
        self.assertEqual(0,entry.kind);self.assertEqual(0,entry.flags);self.assertEqual(mask,entry.mask);native.check(a.ConvertStringSidToSidW(expected_sid,ctypes.byref(expected)))
        try:self.assertTrue(a.EqualSid(ace.value+Ace.sid_start.offset,expected))
        finally:self.assertFalse(native.k.LocalFree(expected))

    def test_native_pipe_dacl_is_protected_owner_only_and_handle_is_not_inheritable(self):
        native=self.pipe._windows;a=native.a;k=native.k;needed=w.DWORD()
        a.GetKernelObjectSecurity.argtypes=[w.HANDLE,w.DWORD,ctypes.c_void_p,w.DWORD,ctypes.POINTER(w.DWORD)];a.GetKernelObjectSecurity.restype=w.BOOL
        a.GetKernelObjectSecurity(self.pipe._handle,4,None,0,ctypes.byref(needed));self.assertGreater(needed.value,0);buffer=ctypes.create_string_buffer(needed.value)
        native.check(a.GetKernelObjectSecurity(self.pipe._handle,4,buffer,len(buffer),ctypes.byref(needed)));self.assert_descriptor_owner(buffer,self.pipe.endpoint.owner_sid,0x1f01ff)
        k.GetHandleInformation.argtypes=[w.HANDLE,ctypes.POINTER(w.DWORD)];k.GetHandleInformation.restype=w.BOOL;flags=w.DWORD();native.check(k.GetHandleInformation(self.pipe._handle,ctypes.byref(flags)));self.assertEqual(0,flags.value&1)

    def test_administrator_sid_alias_has_the_same_binary_identity_as_its_numeric_sid(self):
        native=self.pipe._windows;a=native.a;descriptor=ctypes.c_void_p();literal=ctypes.c_void_p();dacl=ctypes.c_void_p();present=w.BOOL();defaulted=w.BOOL();ace=ctypes.c_void_p()
        native.check(a.ConvertStringSecurityDescriptorToSecurityDescriptorW('D:P(A;;GA;;;LA)',1,ctypes.byref(descriptor),None))
        try:
            a.GetSecurityDescriptorDacl.argtypes=[ctypes.c_void_p,ctypes.POINTER(w.BOOL),ctypes.POINTER(ctypes.c_void_p),ctypes.POINTER(w.BOOL)];a.GetSecurityDescriptorDacl.restype=w.BOOL
            a.GetAce.argtypes=[ctypes.c_void_p,w.DWORD,ctypes.POINTER(ctypes.c_void_p)];a.GetAce.restype=w.BOOL
            native.check(a.GetSecurityDescriptorDacl(descriptor,ctypes.byref(present),ctypes.byref(dacl),ctypes.byref(defaulted)));native.check(a.GetAce(dacl,0,ctypes.byref(ace)));native.check(a.ConvertSidToStringSidW(ace.value+8,ctypes.byref(literal)))
            self.assert_descriptor_owner(descriptor,ctypes.wstring_at(literal),0x10000000)
        finally:
            if literal:self.assertFalse(native.k.LocalFree(literal))
            self.assertFalse(native.k.LocalFree(descriptor))

    def test_administrators_group_dacl_does_not_count_as_the_owner_account(self):
        native=self.pipe._windows;descriptor=ctypes.c_void_p();native.check(native.a.ConvertStringSecurityDescriptorToSecurityDescriptorW('D:P(A;;GA;;;BA)',1,ctypes.byref(descriptor),None))
        try:
            with self.assertRaises(AssertionError):self.assert_descriptor_owner(descriptor,self.pipe.endpoint.owner_sid,0x10000000)
        finally:self.assertFalse(native.k.LocalFree(descriptor))

    def test_actual_restricted_token_cannot_open_pipe(self):
        native=self.pipe._windows;a=native.a;k=native.k;token=w.HANDLE();restricted=w.HANDLE();sid=ctypes.c_void_p()
        a.ConvertStringSidToSidW.argtypes=[w.LPCWSTR,ctypes.POINTER(ctypes.c_void_p)];a.ConvertStringSidToSidW.restype=w.BOOL
        a.CreateRestrictedToken.argtypes=[w.HANDLE,w.DWORD,w.DWORD,ctypes.POINTER(native.Sid),w.DWORD,ctypes.c_void_p,w.DWORD,ctypes.POINTER(native.Sid),ctypes.POINTER(w.HANDLE)];a.CreateRestrictedToken.restype=w.BOOL
        a.ImpersonateLoggedOnUser.argtypes=[w.HANDLE];a.ImpersonateLoggedOnUser.restype=w.BOOL
        native.check(a.OpenProcessToken(k.GetCurrentProcess(),10,ctypes.byref(token)))
        try:
            native.check(a.ConvertStringSidToSidW('S-1-1-0',ctypes.byref(sid)));entry=native.Sid(sid,0)
            native.check(a.CreateRestrictedToken(token,1,0,None,0,None,1,ctypes.byref(entry),ctypes.byref(restricted)))
            try:
                native.check(a.ImpersonateLoggedOnUser(restricted))
                try:
                    handle=k.CreateFileW(self.pipe.endpoint.address,0xc0000000,0,None,3,0x40000000,None);code=ctypes.get_last_error()
                    if handle not in (None,ctypes.c_void_p(-1).value):k.CloseHandle(handle);self.fail('Restricted token unexpectedly opened the owner pipe')
                    self.assertEqual(5,code)
                finally:native.check(a.RevertToSelf())
            finally:native.check(k.CloseHandle(restricted))
        finally:
            if sid:self.assertFalse(k.LocalFree(sid))
            native.check(k.CloseHandle(token))

    def test_server_pid_start_time_and_session_mismatch_refuse_before_submission(self):
        raw=self.frame('prepare')
        for changed in [dict(server_pid=self.pipe.endpoint.server_pid+1),dict(server_started=self.pipe.endpoint.server_started+1),dict(server_session=self.pipe.endpoint.server_session+1)]:
            with self.subTest(fields=sorted(changed)),ThreadPoolExecutor(max_workers=1) as pool,patch.object(self.provider,'dispatch',wraps=self.provider.dispatch) as dispatch:
                server=pool.submit(self.pipe.serve_one,timeout=2)
                with self.assertRaises(t.ReadTransportError) as error:t.call_once(replace(self.pipe.endpoint,**changed),raw,timeout=2)
                self.assertEqual('DENIED',error.exception.code);self.assertFalse(error.exception.request_submitted)
                with self.assertRaises(Exception):server.result(timeout=4)
                dispatch.assert_not_called()

    def test_client_read_timeout_reports_submission_uncertainty_without_retry(self):
        started=time.monotonic()
        with patch.object(self.provider,'dispatch',wraps=self.provider.dispatch) as dispatch,self.assertRaises(t.ReadTransportError) as error:t.call_once(self.pipe.endpoint,self.frame('prepare'),timeout=.15)
        self.assertEqual('UNAVAILABLE',error.exception.code);self.assertTrue(error.exception.request_submitted);self.assertLess(time.monotonic()-started,5);dispatch.assert_not_called();self.assertEqual(set(),self.adapter._seen)

    def test_idle_accept_deadline_cancels_drains_and_allows_a_fresh_request(self):
        with self.assertRaises(Exception) as error:self.pipe.serve_one(timeout=.1)
        self.assertEqual('UNAVAILABLE',str(error.exception));self.assertEqual('ok',self.call(self.frame('prepare'))['status'])

    def test_client_arriving_late_in_idle_accept_gets_a_full_exchange_budget(self):
        original=self.adapter.handle
        def bounded_slow_read(raw):
            time.sleep(1.25)
            return original(raw)
        with patch.object(self.adapter,'handle',side_effect=bounded_slow_read) as dispatch,ThreadPoolExecutor(max_workers=1) as pool:
            server=pool.submit(self.pipe.serve_one,timeout=3)
            self.assertTrue(self.pipe._listening.wait(2),'Native accept did not become observable')
            time.sleep(2)
            reply=t.call_once(self.pipe.endpoint,self.frame('prepare'),timeout=5)
            self.assertEqual('served',server.result(timeout=6));self.assertEqual('ok',json.loads(reply)['status'])
            dispatch.assert_called_once()

    def test_disconnect_after_reply_does_not_replay_completed_request(self):
        native=self.pipe._windows;raw=self.frame('prepare')
        with ThreadPoolExecutor(max_workers=1) as pool,patch.object(self.provider,'dispatch',wraps=self.provider.dispatch) as dispatch:
            server=pool.submit(self.pipe.serve_one,timeout=2);handle=native.k.CreateFileW(self.pipe.endpoint.address,0xc0000000,0,None,3,0x40000000|0x100000|0x10000,None);self.assertNotIn(handle,(None,ctypes.c_void_p(-1).value))
            try:
                native.api.SetNamedPipeHandleState(handle,2,None,None);deadline=time.monotonic()+2;native.write(handle,raw,deadline);reply=native.read(handle,b.MAX_REPLY_BYTES,deadline);self.assertEqual('ok',json.loads(reply)['status'])
            finally:native.check(native.k.CloseHandle(handle))
            with self.assertRaises(Exception):server.result(timeout=4)
            self.assertEqual(1,dispatch.call_count);self.assertEqual('DUPLICATE',self.call(raw)['code']);self.assertEqual(1,dispatch.call_count)

    def test_oversized_native_message_refuses_before_provider_dispatch(self):
        native=self.pipe._windows
        with ThreadPoolExecutor(max_workers=1) as pool,patch.object(self.provider,'dispatch',wraps=self.provider.dispatch) as dispatch:
            server=pool.submit(self.pipe.serve_one,timeout=2);handle=native.k.CreateFileW(self.pipe.endpoint.address,0xc0000000,0,None,3,0x40000000|0x100000|0x10000,None);self.assertNotIn(handle,(None,ctypes.c_void_p(-1).value))
            try:
                try:native.write(handle,b'x'*(b.MAX_REQUEST_BYTES+4096),time.monotonic()+2)
                except Exception:pass # A rejected oversized message may lose its client write acknowledgement.
            finally:native.check(native.k.CloseHandle(handle))
            with self.assertRaises(Exception) as error:server.result(timeout=4)
            self.assertEqual('TOO_LARGE',str(error.exception));dispatch.assert_not_called()

    def test_close_cancels_an_observed_pending_native_accept_and_retires_context(self):
        native=self.pipe._windows;original=native.api.ConnectNamedPipe;pending=threading.Event()
        def observed(*args,**kwargs):operation=original(*args,**kwargs);pending.set();return operation
        with patch.object(native.api,'ConnectNamedPipe',side_effect=observed),ThreadPoolExecutor(max_workers=1) as pool:
            server=pool.submit(self.pipe.serve_one,timeout=3);self.assertTrue(pending.wait(2));self.pipe.close()
            with self.assertRaises(Exception):server.result(timeout=4)
        self.assertTrue(self.pipe.wait_closed(2))
        with self.assertRaises(Exception):self.pipe.serve_one()
        with patch.object(native,'pipe',wraps=native.pipe) as factory,self.assertRaises(Exception):t.LocalReadPipe(self.adapter)
        factory.assert_not_called()

    def test_close_during_real_preview_discards_contents_and_finishes_handles(self):
        (self.root/'notes.txt').write_text('private fixture contents');token=self.call(self.frame('prepare'))['result']['binding_token'];row=self.call(self.frame('list',binding_token=token,relative_path='',entry_token=None))['result']['entries'][0]
        native=self.provider._files();original=native.read;entered=threading.Event();release=threading.Event()
        def observed(handle,amount):result=original(handle,amount);entered.set();self.assertTrue(release.wait(3));return result
        raw=self.frame('preview',binding_token=token,relative_path=row['relative_path'],entry_token=row['entry_token'])
        with patch.object(native,'read',side_effect=observed),ThreadPoolExecutor(max_workers=2) as pool:
            server=pool.submit(self.pipe.serve_one,timeout=3);client=pool.submit(t.call_once,self.pipe.endpoint,raw,timeout=3)
            try:self.assertTrue(entered.wait(2));self.pipe.close()
            finally:release.set()
            with self.assertRaises(Exception):server.result(timeout=4)
            with self.assertRaises(t.ReadTransportError) as error:client.result(timeout=4)
            self.assertTrue(error.exception.request_submitted)
        self.assertTrue(self.pipe.wait_closed(2))

    def test_impersonation_failure_permanently_closes_before_file_dispatch(self):
        def denied(*args):ctypes.set_last_error(5);return False
        with patch.object(self.pipe._windows.a,'ImpersonateNamedPipeClient',side_effect=denied),ThreadPoolExecutor(max_workers=1) as pool,patch.object(self.provider,'dispatch',wraps=self.provider.dispatch) as dispatch:
            server=pool.submit(self.pipe.serve_one,timeout=2)
            with self.assertRaises(t.ReadTransportError):t.call_once(self.pipe.endpoint,self.frame('prepare'),timeout=2)
            with self.assertRaises(Exception) as error:server.result(timeout=4)
            self.assertEqual('DENIED',str(error.exception));dispatch.assert_not_called()
        self.assertTrue(self.pipe.wait_closed(2))

    def test_reported_revert_failure_is_fail_stop_without_executing_files(self):
        original=self.pipe._windows.a.RevertToSelf
        def reported_failure():self.assertTrue(original());return False # Real reversion occurs before safe fault injection.
        with patch.object(self.pipe._windows.a,'RevertToSelf',side_effect=reported_failure),ThreadPoolExecutor(max_workers=1) as pool,patch.object(self.provider,'dispatch',wraps=self.provider.dispatch) as dispatch:
            server=pool.submit(self.pipe.serve_one,timeout=2)
            with self.assertRaises(t.ReadTransportError):t.call_once(self.pipe.endpoint,self.frame('prepare'),timeout=2)
            with self.assertRaises(t.IdentityRevertFailure):server.result(timeout=4)
            dispatch.assert_not_called()
        self.assertTrue(self.pipe.wait_closed(2))
        with self.assertRaises(Exception):self.pipe.serve_one()

    def test_separate_owned_process_uses_actual_pipe_identity_and_prepare(self):
        raw=self.frame('prepare');code="import json,sys;sys.path.insert(0,sys.argv[1]);from server_extensions.worker_workspaces.local_pipe import PipeEndpoint,call_once;v=json.loads(sys.stdin.buffer.read(32768));sys.stdout.buffer.write(call_once(PipeEndpoint(**v['endpoint']),v['request'].encode(),timeout=10))"
        env={k:v for k,v in os.environ.items() if k.upper() in ('SYSTEMROOT','WINDIR','PATH','PATHEXT')};env.update(TEMP=str(self.parent),TMP=str(self.parent))
        with ThreadPoolExecutor(max_workers=1) as pool:
            server=pool.submit(self.pipe.serve_one,timeout=10);child=subprocess.run([sys.executable,'-I','-S','-B','-c',code,str(Path(__file__).resolve().parents[3])],input=json.dumps(dict(endpoint=asdict(self.pipe.endpoint),request=raw.decode())).encode(),env=env,stdin=None,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=15,creationflags=subprocess.CREATE_NO_WINDOW)
            self.assertEqual(0,child.returncode,child.stderr.decode(errors='replace'));self.assertEqual('served',server.result(timeout=12));reply=json.loads(child.stdout);self.assertEqual('ok',reply['status']);self.assertEqual(self.scope,reply['result']['scope_id'])

    def test_repeated_idle_cancellation_does_not_leak_native_events(self):
        native=self.pipe._windows;k=native.k;k.GetProcessHandleCount.argtypes=[w.HANDLE,ctypes.POINTER(w.DWORD)];k.GetProcessHandleCount.restype=w.BOOL
        def count():value=w.DWORD();native.check(k.GetProcessHandleCount(k.GetCurrentProcess(),ctypes.byref(value)));return value.value
        gc.collect();before=count()
        def cancel():
            with self.assertRaises(Exception) as error:self.pipe.serve_one(timeout=.05)
            self.assertEqual('UNAVAILABLE',str(error.exception))
        for _ in range(10):cancel()
        gc.collect();self.assertLessEqual(count(),before+1)
