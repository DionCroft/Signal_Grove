"""Real Windows filesystem acceptance; run on Windows with TEMP/TMP off C:."""
import ctypes
import hashlib
import importlib.util
import os
from pathlib import Path
import socket
import subprocess
import sys
import tempfile
import threading
from concurrent.futures import ThreadPoolExecutor
import unittest
from unittest.mock import patch

if sys.platform!='win32':raise RuntimeError('This is a Windows native gate, not a portable or skipped pass')
spec=importlib.util.spec_from_file_location('worker_workspace_native',Path(__file__).resolve().parents[1]/'workspace_provider.py')
p=importlib.util.module_from_spec(spec);sys.modules[spec.name]=p;spec.loader.exec_module(p)


class WindowsWorkspaceTest(unittest.TestCase):
    def setUp(self):
        parent=Path(os.environ['TEMP']).resolve(strict=True)
        self.assertNotEqual('c:',parent.drive.lower(),'Native fixtures must not use C: temporary storage')
        self.temp=tempfile.TemporaryDirectory(prefix='signal-grove-worker-',dir=parent)
        self.base=Path(self.temp.name).resolve(strict=True);self.parent=parent
        self.assertTrue(self.base.is_relative_to(parent));self.addCleanup(self.cleanup)
        self.root=self.base/'project';self.root.mkdir();self.outside=self.base/'outside';self.outside.mkdir()
        self.host=socket.gethostname().upper();self.revision='registry-v1';self.scope='1'*64;self.now=[0.]
        self.binding=p.RegisteredProject('fixture',self.host,str(self.root),self.revision,self.scope)
        self.provider=p.WorkspaceProvider(lambda scope,ident:self.binding,scope_id=self.scope,clock=lambda:self.now[0])
    def cleanup(self):
        if self.base.exists():
            resolved=self.base.resolve(strict=True)
            self.assertTrue(resolved.is_relative_to(self.parent) and resolved!=self.parent)
        self.temp.cleanup()
    def request(self,op,**fields):return self.provider.dispatch(dict(schema=1,scope_id=self.scope,operation=op,project_id='fixture',**fields))
    def prepare(self):return self.request('prepare')['binding_token']
    def listing(self,token,relative_path='',entry_token=None):return self.request('list',binding_token=token,relative_path=relative_path,entry_token=entry_token)
    def preview(self,token,row):return self.request('preview',binding_token=token,relative_path=row['relative_path'],entry_token=row['entry_token'])
    def entry(self,token,name):return next(row for row in self.listing(token)['entries'] if row['name']==name)
    def refuses(self,code,fn):
        with self.assertRaises(p.WorkspaceError) as caught:fn()
        self.assertEqual(code,caught.exception.code);self.assertEqual(code,str(caught.exception))
    def test_real_unicode_nested_listing_and_preview_preserve_content(self):
        folder=self.root/'源 文件';folder.mkdir();file=folder/'notes 中.txt';file.write_text('Signal Grove 🌱\n只读验证\n',encoding='utf-8')
        before=hashlib.sha256(file.read_bytes()).hexdigest();token=self.prepare();row=self.entry(token,folder.name)
        nested=self.listing(token,row['relative_path'],row['entry_token']);self.assertEqual(1,len(nested['entries']))
        result=self.preview(token,nested['entries'][0]);self.assertEqual(file.read_bytes().decode('utf-8'),result['text']);self.assertFalse(result['truncated']);self.assertFalse(result['binary']);self.assertEqual(before,hashlib.sha256(file.read_bytes()).hexdigest())
        self.assertEqual(hashlib.sha256(file.read_bytes()).hexdigest(),result['preview_sha256'])
    def test_full_file_id_matches_native_stat_across_open_and_rename(self):
        file=self.root/'identity.txt';file.write_text('identity fixture');native=self.provider._files()
        with native.opened(file) as (_,first),native.opened(file) as (_,second):
            self.assertEqual(first.identity,second.identity);self.assertEqual(file.stat().st_ino,first.index)
            self.assertGreater(first.index,0);self.assertEqual(24,ctypes.sizeof(native.FileIdInfo))
        renamed=self.root/'renamed.txt';file.rename(renamed)
        with native.opened(renamed) as (_,same):self.assertEqual(first.identity,same.identity)
        file.write_text('new object at original name')
        with native.opened(file) as (_,replacement):self.assertNotEqual(first.identity,replacement.identity)
    def test_unavailable_full_file_id_refuses_without_legacy_fallback(self):
        native=self.provider._files()
        def unsupported(*args):ctypes.set_last_error(50);return False
        with patch.object(native.k,'GetFileInformationByHandleEx',side_effect=unsupported),patch.object(native,'read',wraps=native.read) as read:
            self.refuses('UNSUPPORTED',lambda:self.prepare());read.assert_not_called()
    def test_retirement_between_validation_and_grant_commit_refuses(self):
        original=self.provider._mac
        def retire_before_commit(*values):
            result=original(*values);self.provider.retire();return result
        with patch.object(self.provider,'_mac',side_effect=retire_before_commit):self.refuses('UNAVAILABLE',lambda:self.prepare())
        self.assertEqual({},self.provider._grants)
    def test_changed_file_is_refused_before_content_read(self):
        file=self.root/'notes.txt';file.write_text('before');token=self.prepare();row=self.entry(token,file.name);file.write_text('after contents changed')
        native=self.provider._files()
        with patch.object(native,'read',wraps=native.read) as read:self.refuses('CHANGED',lambda:self.preview(token,row));read.assert_not_called()
    def test_replaced_root_and_foreign_registry_revision_invalidate_bindings(self):
        (self.root/'notes.txt').write_text('before');token=self.prepare();row=self.entry(token,'notes.txt')
        old=self.base/'old-project';self.root.rename(old);self.root.mkdir();(self.root/'notes.txt').write_text('different root')
        self.refuses('CHANGED',lambda:self.preview(token,row))
        token=self.prepare();self.binding=p.RegisteredProject('fixture',self.host,str(self.root),'registry-v2',self.scope)
        self.refuses('CHANGED',lambda:self.listing(token))
    def test_foreign_worker_project_and_restarted_provider_refuse_old_binding(self):
        token=self.prepare();self.binding=p.RegisteredProject('fixture','ANOTHER-WORKER',str(self.root),'registry-v1',self.scope);self.refuses('CHANGED',lambda:self.listing(token))
        self.binding=p.RegisteredProject('fixture',self.host,str(self.root),'registry-v1',self.scope)
        self.refuses('CHANGED',lambda:self.provider.dispatch(dict(schema=1,scope_id=self.scope,operation='list',project_id='other',binding_token=token,relative_path='',entry_token=None)))
        self.provider=p.WorkspaceProvider(lambda scope,ident:self.binding,scope_id=self.scope,clock=lambda:self.now[0]);self.refuses('CHANGED',lambda:self.listing(token))
    def test_cross_profile_scope_cannot_reuse_a_known_binding_token(self):
        (self.root/'notes.txt').write_text('private fixture');token=self.prepare();row=self.entry(token,'notes.txt');native=self.provider._files()
        with patch.object(native,'read',wraps=native.read) as read:
            self.refuses('CHANGED',lambda:self.provider.dispatch(dict(schema=1,scope_id='2'*64,operation='preview',project_id='fixture',binding_token=token,relative_path=row['relative_path'],entry_token=row['entry_token'])));read.assert_not_called()
    def test_expired_binding_does_not_read_and_fresh_prepare_is_explicit(self):
        token=self.prepare();self.now[0]=p.TTL;self.refuses('EXPIRED',lambda:self.listing(token))
        self.assertEqual([],self.listing(self.prepare())['entries'])
    def junction(self,path,target):
        def quote(value):return "'"+str(value).replace("'","''")+"'"
        result=subprocess.run(['powershell.exe','-NoProfile','-Command','New-Item -ItemType Junction -Path '+quote(path)+' -Target '+quote(target)+' -ErrorAction Stop | Out-Null'],stdin=subprocess.DEVNULL,capture_output=True,text=True,timeout=30,creationflags=subprocess.CREATE_NO_WINDOW)
        self.assertEqual(0,result.returncode,result.stderr)
    def test_directory_junction_escape_is_never_followed_or_previewed(self):
        (self.outside/'secret.txt').write_text('outside private fixture');self.junction(self.root/'linked',self.outside);token=self.prepare()
        result=self.listing(token);self.assertEqual([],result['entries']);self.assertEqual(1,result['excluded_entries'])
        native=self.provider._files()
        with patch.object(native,'read',wraps=native.read) as read:
            self.refuses('DENIED',lambda:self.request('preview',binding_token=token,relative_path='linked/secret.txt',entry_token='a'*64));read.assert_not_called()
    def test_junction_registered_root_is_refused(self):
        link=self.base/'registered-link';self.junction(link,self.root);self.binding=p.RegisteredProject('fixture',self.host,str(link),'registry-v1',self.scope)
        self.refuses('DENIED',lambda:self.prepare())
    def test_hard_link_cannot_serve_another_path(self):
        secret=self.outside/'secret.txt';secret.write_text('outside fixture');os.link(secret,self.root/'visible.txt');token=self.prepare()
        result=self.listing(token);self.assertEqual([],result['entries']);self.assertEqual(1,result['excluded_entries'])
        self.refuses('DENIED',lambda:self.request('preview',binding_token=token,relative_path='visible.txt',entry_token='a'*64))
    def test_new_hard_link_invalidates_a_previously_regular_file(self):
        file=self.root/'notes.txt';file.write_text('fixture');token=self.prepare();row=self.entry(token,file.name);os.link(file,self.outside/'another-name.txt')
        self.refuses('DENIED',lambda:self.preview(token,row))
    def test_credentials_and_control_trees_are_not_listed(self):
        for name in ['.env','.ENV.local','.envrc','auth.json','config.yaml','id_ed25519','.git-credentials']:(self.root/name).write_text('fixture secret')
        for name in ['.ssh','.git','mcp-tokens','pairing']:(self.root/name).mkdir()
        (self.root/'readme.txt').write_text('safe');result=self.listing(self.prepare());self.assertEqual(['readme.txt'],[row['name'] for row in result['entries']]);self.assertEqual(11,result['excluded_entries'])
    def test_utf8_truncation_keeps_only_complete_characters_and_reports_preview_bytes(self):
        data=b'a'*(p.MAX_PREVIEW-1)+'中中中'.encode();file=self.root/'long.txt';file.write_bytes(data);token=self.prepare();result=self.preview(token,self.entry(token,file.name))
        self.assertTrue(result['truncated']);self.assertFalse(result['binary']);self.assertEqual('a'*(p.MAX_PREVIEW-1),result['text']);self.assertEqual(p.MAX_PREVIEW,result['preview_bytes']);self.assertEqual(len(data),result['byte_size']);self.assertEqual(hashlib.sha256(data[:p.MAX_PREVIEW]).hexdigest(),result['preview_sha256'])
    def test_binary_invalid_utf8_and_empty_files_have_explicit_states(self):
        for name,data in [('binary.dat',b'ab\0cd'),('invalid.txt',b'\xffbad'),('empty.txt',b'')]:
            (self.root/name).write_bytes(data)
        token=self.prepare()
        for name in ['binary.dat','invalid.txt']:
            result=self.preview(token,self.entry(token,name));self.assertTrue(result['binary']);self.assertIsNone(result['text'])
        result=self.preview(token,self.entry(token,'empty.txt'));self.assertEqual('',result['text']);self.assertFalse(result['binary']);self.assertEqual(0,result['byte_size'])
    def test_large_file_refuses_before_reading(self):
        file=self.root/'large.txt'
        with file.open('wb') as stream:stream.seek(p.MAX_SOURCE);stream.write(b'x')
        token=self.prepare();row=self.entry(token,file.name);native=self.provider._files()
        with patch.object(native,'read',wraps=native.read) as read:self.refuses('TOO_LARGE',lambda:self.preview(token,row));read.assert_not_called()
    def test_listing_is_bounded_and_marks_incompleteness(self):
        for index in range(p.MAX_ENTRIES+1):(self.root/(str(index)+'.txt')).write_text('x')
        result=self.listing(self.prepare());self.assertEqual(p.MAX_ENTRIES,len(result['entries']));self.assertTrue(result['truncated'])
        self.assertLess(len(__import__('json').dumps(result).encode()),128*1024)
    def test_encoded_metadata_budget_is_a_distinct_incomplete_boundary(self):
        folder=self.root/('中'*40);folder.mkdir()
        for index in range(120):(folder/(('文'*60)+str(index)+'.txt')).write_text('x')
        token=self.prepare();row=self.entry(token,folder.name);result=self.listing(token,row['relative_path'],row['entry_token'])
        self.assertTrue(result['truncated']);self.assertGreater(len(result['entries']),0);self.assertLess(len(result['entries']),120);self.assertLess(len(__import__('json').dumps(result).encode()),128*1024)
    def test_percent_encoded_looking_names_are_literal_not_traversal(self):
        folder=self.root/'%2e%2e';folder.mkdir();(folder/'ok.txt').write_text('inside literal folder');(self.outside/'ok.txt').write_text('outside fixture')
        token=self.prepare();row=self.entry(token,folder.name);nested=self.listing(token,row['relative_path'],row['entry_token']);self.assertEqual('inside literal folder',self.preview(token,nested['entries'][0])['text'])
    def test_native_handles_prevent_root_replacement_during_content_read(self):
        (self.root/'notes.txt').write_text('fixture');token=self.prepare();row=self.entry(token,'notes.txt');native=self.provider._files();original=native.read;denied=[]
        def observed(handle,amount):
            try:self.root.rename(self.base/'moved-during-read')
            except OSError:denied.append(True)
            else:raise AssertionError('Root was replaceable while native content handle was held')
            return original(handle,amount)
        with patch.object(native,'read',side_effect=observed):self.assertEqual('fixture',self.preview(token,row)['text'])
        self.assertEqual([True],denied)
    def test_existing_writer_is_busy_instead_of_an_unstable_preview(self):
        file=self.root/'notes.txt';file.write_text('fixture');token=self.prepare();row=self.entry(token,file.name);native=self.provider._files()
        handle=native.k.CreateFileW(str(file),0x40000000,3,None,3,0,None);self.assertNotIn(handle,(None,ctypes.c_void_p(-1).value))
        try:self.refuses('BUSY',lambda:self.preview(token,row))
        finally:self.assertTrue(native.k.CloseHandle(handle))
    def test_four_live_reads_bound_concurrency_without_holding_a_global_io_lock(self):
        (self.root/'notes.txt').write_text('fixture');token=self.prepare();row=self.entry(token,'notes.txt');native=self.provider._files();original=native.read;gate=threading.Event();ready=threading.Event();count=[0];lock=threading.Lock()
        def paused(handle,amount):
            with lock:
                count[0]+=1
                if count[0]==4:ready.set()
            if not gate.wait(10):raise AssertionError('Fixture release not observed')
            return original(handle,amount)
        with patch.object(native,'read',side_effect=paused),ThreadPoolExecutor(max_workers=4) as pool:
            futures=[pool.submit(self.preview,token,row) for _ in range(4)]
            try:
                self.assertTrue(ready.wait(10));self.refuses('BUSY',lambda:self.preview(token,row))
            finally:gate.set()
            self.assertEqual(['fixture']*4,[future.result(timeout=10)['text'] for future in futures])
    def test_repeated_operations_release_native_handles_and_never_launch_tools(self):
        (self.root/'notes.txt').write_text('fixture');token=self.prepare();native=self.provider._files();k=native.k
        k.GetCurrentProcess.restype=ctypes.c_void_p;k.GetProcessHandleCount.argtypes=[ctypes.c_void_p,ctypes.POINTER(ctypes.c_ulong)];k.GetProcessHandleCount.restype=ctypes.c_int
        def handles():
            count=ctypes.c_ulong();self.assertTrue(k.GetProcessHandleCount(k.GetCurrentProcess(),ctypes.byref(count)));return count.value
        before=handles()
        with patch.object(subprocess,'run',side_effect=AssertionError('No shell/tool execution')),patch.object(socket,'create_connection',side_effect=AssertionError('No network service client')):
            for _ in range(20):self.assertEqual('fixture',self.preview(token,self.entry(token,'notes.txt'))['text'])
        self.assertEqual(before,handles())


if __name__=='__main__':unittest.main()
