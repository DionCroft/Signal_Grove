"""Real Windows handles and pipes; fixture owner observation is explicitly injected."""
from dataclasses import replace
import hashlib,json,os,socket,subprocess,tempfile,threading,unittest
from pathlib import Path
from unittest.mock import patch
from server_extensions.worker_workspaces import broker_enrollment as e,local_pipe as t,operator_file as f
from server_extensions.worker_workspaces.workspace_provider import WorkspaceError,WindowsFiles


class EnrollmentNativeTest(unittest.TestCase):
    def setUp(self):
        self.parent=Path(os.environ['TEMP']).resolve(strict=True);self.assertNotEqual('c:',self.parent.drive.lower())
        self.temp=tempfile.TemporaryDirectory(prefix='enrollment-',dir=self.parent);self.base=Path(self.temp.name).resolve(strict=True);self.addCleanup(self.cleanup)
        self.root=self.base/'project';self.root.mkdir();(self.root/'notes.txt').write_bytes('Scoped read 🌱\n'.encode('utf8'))
        self.scope=e.scope_for('fixture-authority','fixture-owner','fixture-profile');self.host=socket.gethostname().upper()
        sid,started,session=t._Windows().process_identity(os.getpid());self.owner=e.OwnerObservation(self.host,sid,os.getpid(),started,max(1,session),True)
        self.policy=dict(schema=1,device=self.host,owner_sid=sid,grants=[dict(scope_id=self.scope,project_ids=['fixture'])]);self.raw_registry=b'{"fixture":"registered"}'
        self.rows=({'project_id':'fixture','root':str(self.root)},);self.validated=0;self.sequence=0
        self.session=e.EnrolledReadSession(scope_id=self.scope,read_policy=lambda:json.dumps(self.policy).encode(),read_registry=self.registry,validate_project=self.validate,observe=lambda:self.owner);self.addCleanup(self.session.adapter.close)
    def cleanup(self):
        if self.base.exists():self.assertTrue(self.base.resolve(strict=True).is_relative_to(self.parent) and self.base!=self.parent)
        self.temp.cleanup()
    def registry(self):return e.RegistrySnapshot(hashlib.sha256(self.raw_registry).hexdigest(),self.rows)
    def validate(self,ident,rows):
        self.validated+=1;self.assertIs(rows,self.rows);self.assertEqual('fixture',ident);return dict(id=ident,root=str(self.root),device=self.host)
    def frame(self,operation,**fields):
        self.sequence+=1;return json.dumps(dict(schema=1,request_id=f'{self.sequence:032x}',worker_instance=self.session.adapter.instance,payload=dict(schema=1,scope_id=self.scope,project_id='fixture',operation=operation,**fields))).encode()
    def request(self,operation,**fields):return json.loads(self.session.adapter.handle(self.frame(operation,**fields)))
    def fixture(self):
        token=self.request('prepare')['result']['binding_token'];row=self.request('list',binding_token=token,relative_path='',entry_token=None)['result']['entries'][0]
        return dict(binding_token=token,relative_path=row['relative_path'],entry_token=row['entry_token'])
    def test_enrolled_scope_reads_real_file_through_existing_owner_pipe(self):
        pipe=t.LocalReadPipe(self.session.adapter);self.addCleanup(pipe.close)
        def call(operation,**fields):
            errors=[]
            def serve():
                try:pipe.serve_one(timeout=10)
                except BaseException as error:errors.append(type(error).__name__)
            thread=threading.Thread(target=serve);thread.start()
            try:reply=json.loads(t.call_once(pipe.endpoint,self.frame(operation,**fields),timeout=10))
            finally:thread.join(12)
            self.assertFalse(thread.is_alive());self.assertEqual([],errors);self.assertEqual('ok',reply['status']);return reply['result']
        token=call('prepare')['binding_token'];row=call('list',binding_token=token,relative_path='',entry_token=None)['entries'][0]
        result=call('preview',binding_token=token,relative_path=row['relative_path'],entry_token=row['entry_token']);raw=(self.root/'notes.txt').read_bytes()
        self.assertEqual(raw.decode(),result['text']);self.assertEqual(hashlib.sha256(raw).hexdigest(),result['preview_sha256']);self.assertGreaterEqual(self.validated,3)
    def test_revoked_policy_after_real_read_discards_contents_and_retires_tokens(self):
        fields=self.fixture();native=self.session._provider._files();original=native.read
        def read(*args):
            result=original(*args);self.policy['grants'][0]['project_ids']=['different'];return result
        with patch.object(native,'read',side_effect=read):reply=self.request('preview',**fields)
        self.assertEqual('error',reply['status']);self.assertNotIn('result',reply);self.assertTrue(self.session._provider._retired)
        self.policy['grants'][0]['project_ids']=['fixture'];self.assertEqual('error',self.request('preview',**fields)['status'])
    def test_inactive_owner_blocks_before_real_file_read_and_never_revives(self):
        fields=self.fixture();native=self.session._provider._files();self.owner=replace(self.owner,active=False)
        with patch.object(native,'read',wraps=native.read) as read:self.assertEqual('error',self.request('preview',**fields)['status']);read.assert_not_called()
        self.owner=replace(self.owner,active=True);self.assertEqual('error',self.request('preview',**fields)['status'])
    def test_registry_change_invalidates_real_lease_even_when_project_id_is_unchanged(self):
        fields=self.fixture();self.raw_registry=b'{"fixture":"changed"}';reply=self.request('preview',**fields)
        self.assertNotIn('result',reply);self.assertEqual('error',reply['status']);self.assertTrue(self.session._provider._retired)
    def test_default_observer_returns_actual_token_process_and_explicit_wts_state(self):
        observed=e.observe_owner();sid,started,session=t._Windows().process_identity(os.getpid())
        self.assertEqual((sid,started,session),(observed.owner_sid,observed.started,observed.session));self.assertEqual(os.getpid(),observed.pid);self.assertIs(type(observed.active),bool)
        if session==0:self.assertFalse(observed.active)
    def test_operator_source_read_holds_real_file_against_writes_and_directory_rename(self):
        path=self.root/'registry.yaml';raw=b'project metadata\n'*10000;path.write_bytes(raw);original=WindowsFiles.read;observed=[]
        def read(native,*args):
            if not observed:
                for action in [lambda:path.write_bytes(b'changed'),lambda:self.root.rename(self.base/'renamed')]:
                    with self.assertRaises(OSError):action()
                observed.append(True)
            return original(native,*args)
        with patch.object(WindowsFiles,'read',new=read):self.assertEqual(raw,f.read_operator_file(str(path)))
        self.assertEqual([True],observed);self.assertEqual(raw,path.read_bytes())
    def test_operator_source_rejects_hard_links_and_oversized_empty_or_nonregular_inputs(self):
        path=self.root/'registry.yaml';path.write_bytes(b'operator metadata');link=self.base/'linked.yaml';os.link(path,link)
        with self.assertRaises(WorkspaceError):f.read_operator_file(str(path))
        link.unlink()
        for raw in [b'',b'x'*1025]:
            path.write_bytes(raw)
            with self.assertRaises(WorkspaceError):f.read_operator_file(str(path),maximum=1024)
        with self.assertRaises(WorkspaceError):f.read_operator_file(str(self.root))
    def test_operator_source_rejects_junction_ancestor_and_has_no_identity_fallback(self):
        link=self.base/'junction'
        def quote(value):return "'"+str(value).replace("'","''")+"'"
        result=subprocess.run(['powershell.exe','-NoProfile','-Command','New-Item -ItemType Junction -Path '+quote(link)+' -Target '+quote(self.root)+' -ErrorAction Stop | Out-Null'],stdin=subprocess.DEVNULL,capture_output=True,text=True,timeout=30,creationflags=subprocess.CREATE_NO_WINDOW)
        self.assertEqual(0,result.returncode,result.stderr)
        with self.assertRaises(WorkspaceError):f.read_operator_file(str(link/'notes.txt'))
        self.assertEqual(b'Scoped read \xf0\x9f\x8c\xb1\n',f.read_operator_file(str(self.root/'notes.txt')))
