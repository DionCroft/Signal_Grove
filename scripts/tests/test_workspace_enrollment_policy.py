"""Portable policy checks; native identity is tested separately on Windows."""
from dataclasses import replace
import hashlib,json,os,socket,unittest
from unittest.mock import patch
from server_extensions.worker_workspaces import broker_enrollment as e
from server_extensions.worker_workspaces.workspace_provider import WorkspaceError


class EnrollmentPolicyTest(unittest.TestCase):
    def setUp(self):
        self.scope=e.scope_for('authority','uid:1000','/profile');self.host=socket.gethostname().upper()
        self.owner=e.OwnerObservation(self.host,'S-1-5-21-1-2-3-1000',os.getpid(),1,1,True)
        self.policy=dict(schema=1,device=self.host,owner_sid=self.owner.owner_sid,grants=[dict(scope_id=self.scope,project_ids=['fixture'])])
        self.registry=e.RegistrySnapshot('a'*64,({'project_id':'fixture'},));self.lookups=0
    def raw(self):return json.dumps(self.policy).encode()
    def validate(self,ident,rows):
        self.lookups+=1;return dict(id=ident,root='E:\\Projects\\Fixture',device=self.host)
    def session(self):return e.EnrolledReadSession(scope_id=self.scope,read_policy=self.raw,read_registry=lambda:self.registry,validate_project=self.validate,observe=lambda:self.owner)
    def test_scope_separates_authority_owner_profile_and_ambiguous_concatenation(self):
        values=[e.scope_for(*v) for v in [('a','bc','d'),('ab','c','d'),('a','b','cd'),('b','bc','d'),('a','bc','e')]]
        self.assertEqual(5,len(set(values)));self.assertEqual(self.scope,e.scope_for('authority','uid:1000','/profile'))
    def test_scope_rejects_missing_controls_surrogates_and_unbounded_identity(self):
        for value in ['',None,True,'\nsecret','x\x7f','\ud800','x'*4097]:
            with self.subTest(kind=type(value).__name__),self.assertRaises(WorkspaceError):e.scope_for('a',value,'p')
    def test_policy_requires_explicit_closed_unique_nonempty_bounded_grants(self):
        original=json.loads(self.raw())
        variants=[{**original,'schema':True},{**original,'root':'E:\\secret'},{**original,'grants':[]},{**original,'grants':original['grants']*9},{**original,'grants':original['grants']*2},{**original,'owner_sid':'BA'},{**original,'grants':[dict(scope_id=self.scope,project_ids=['fixture','fixture'])]},{**original,'grants':[dict(scope_id=self.scope,project_ids=['../bad'])]}]
        for value in variants:
            with self.subTest(keys=sorted(value)),self.assertRaises(WorkspaceError):e.parse_policy(json.dumps(value).encode())
        for raw in [b'{"schema":1,"schema":1}',b'\xff',b' '*32769,b'{"x":NaN}']:
            with self.assertRaises(WorkspaceError):e.parse_policy(raw)
        self.assertEqual(frozenset({'fixture'}),e.parse_policy(self.raw()).projects(self.scope))
    def test_initial_wrong_user_device_session_process_or_inactive_owner_refuses(self):
        original=self.owner
        for values in [dict(owner_sid='S-1-5-18'),dict(device='OTHER'),dict(session=0),dict(session=True),dict(pid=os.getpid()+1),dict(started=0),dict(active=False),dict(active=1)]:
            self.owner=replace(original,**values)
            with self.subTest(values=values),self.assertRaises(WorkspaceError):self.session()
        self.assertEqual(0,self.lookups)
    def test_fresh_registry_and_owner_loss_permanently_retire_existing_grants(self):
        for mutate in ['registry','owner','policy']:
            with self.subTest(mutate=mutate):
                self.setUp();session=self.session();session.lookup(self.scope,'fixture')
                if mutate=='registry':self.registry=e.RegistrySnapshot('b'*64,self.registry.rows)
                elif mutate=='owner':self.owner=replace(self.owner,active=False)
                else:self.policy['grants'][0]['project_ids']=['other']
                with self.assertRaises(WorkspaceError):session.checked_context()
                self.assertTrue(session._provider._retired)
                self.setUp()
                with self.assertRaises(WorkspaceError):session.checked_context()
    def test_unenrolled_project_or_scope_never_calls_owner_validator(self):
        for scope,ident in [('f'*64,'fixture'),(self.scope,'other')]:
            session=self.session()
            with self.assertRaises(WorkspaceError):session.lookup(scope,ident)
        self.assertEqual(0,self.lookups)
    def test_owner_existing_validator_denial_is_preserved_and_session_cannot_resume(self):
        def deny(*args):raise ValueError('private policy denial')
        session=e.EnrolledReadSession(scope_id=self.scope,read_policy=self.raw,read_registry=lambda:self.registry,validate_project=deny,observe=lambda:self.owner)
        with self.assertRaises(ValueError):session.lookup(self.scope,'fixture')
        with self.assertRaises(WorkspaceError):session.checked_context()
    def test_owner_loss_during_blocking_project_validation_discards_result(self):
        def validate(*args):
            self.owner=replace(self.owner,active=False);return dict(id='fixture',root='E:\\Projects\\Fixture',device=self.host)
        session=e.EnrolledReadSession(scope_id=self.scope,read_policy=self.raw,read_registry=lambda:self.registry,validate_project=validate,observe=lambda:self.owner)
        with self.assertRaises(WorkspaceError):session.lookup(self.scope,'fixture')
        self.assertTrue(session._provider._retired)
    def test_fresh_binding_preserves_scope_device_root_and_both_source_revisions(self):
        session=self.session();bound=session.lookup(self.scope,'fixture');self.assertEqual(self.scope,bound.scope_id);self.assertEqual(self.host,bound.device)
        expected=hashlib.sha256((hashlib.sha256(self.raw()).hexdigest()+':'+self.registry.revision).encode()).hexdigest()
        self.assertEqual(expected,bound.registry_revision);self.assertEqual(expected,session.checked_context().policy_revision)
    def test_no_root_override_or_foreign_validator_result_is_admitted(self):
        for root,device in [('relative',self.host),('E:\\Projects\\Fixture','OTHER')]:
            session=e.EnrolledReadSession(scope_id=self.scope,read_policy=self.raw,read_registry=lambda:self.registry,validate_project=lambda *args:dict(id='fixture',root=root,device=device),observe=lambda:self.owner)
            with self.assertRaises(WorkspaceError):session.lookup(self.scope,'fixture')
    def test_snapshot_type_revision_and_callback_failures_never_return_a_context(self):
        for value in [None,e.RegistrySnapshot('bad',self.registry.rows),e.RegistrySnapshot('a'*64,[]),e.RegistrySnapshot('a'*64,(None,))]:
            self.registry=value
            with self.assertRaises(WorkspaceError):self.session()
    def test_policy_and_owner_representations_omit_scope_and_sid(self):
        policy=e.parse_policy(self.raw());self.assertNotIn(self.scope,repr(policy));self.assertNotIn(self.owner.owner_sid,repr(policy));self.assertNotIn(self.owner.owner_sid,repr(self.owner))


class OperatorSourcePolicyTest(unittest.TestCase):
    def test_unc_and_drive_root_separators_do_not_confuse_distinct_paths(self):
        from server_extensions.worker_workspaces.operator_file import _same_path
        self.assertTrue(_same_path(r'\\server\share', '\\\\SERVER\\share\\'))
        self.assertTrue(_same_path('E:\\', 'e:\\'))
        for other in [r'\\server\share-other',r'\\other\share',r'\\server\share\other']:
            self.assertFalse(_same_path(r'\\server\share',other))
    def test_invalid_operator_source_paths_and_budgets_fail_before_native_io(self):
        from server_extensions.worker_workspaces import operator_file as f
        for path,maximum in [('relative',1024),('E:\\root\\..\\config.yaml',1024),('E:\\root\\config:stream',1024),('E:/root/config',1024),('E:\\root\\config',True),('E:\\root\\config',1024*1024+1)]:
            with patch.object(f,'WindowsFiles') as native,self.assertRaises(WorkspaceError):f.read_operator_file(path,maximum=maximum)
            native.assert_not_called()
