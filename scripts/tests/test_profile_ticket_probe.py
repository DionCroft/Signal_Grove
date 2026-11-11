"""Backend-only probe cannot turn matching paths into mutation or Android acceptance."""
import ast
import copy
from pathlib import Path
import sqlite3
import subprocess
import sys
import tempfile
import unittest
import uuid

SCRIPT=Path(__file__).resolve().parents[1]/'compatibility_fixture_owner_workspace_tcp.py'
node=next(n for n in ast.parse(SCRIPT.read_text(encoding='utf-8')).body if isinstance(n,ast.FunctionDef) and n.name=='verify_profile_ticket_store')
space={};exec(compile(ast.Module(body=[node],type_ignores=[]),str(SCRIPT),'exec'),space)
verify=space['verify_profile_ticket_store']

class ProfileTicketStoreEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.db=sqlite3.connect(':memory:');self.expected=dict(sessionId='session-fixture',cwd='/fixture/project',revision=2,generation=3)
        self.db.execute('CREATE TABLE sessions(id TEXT,cwd TEXT,runtime_revision INTEGER,runtime_generation INTEGER)')
        self.db.execute('INSERT INTO sessions VALUES (?,?,?,?)',tuple(self.expected.values()))
        for table in ('messages','session_admissions','hosted_rooms','worker_executions','session_turn_leases','compression_locks'):self.db.execute('CREATE TABLE '+table+'(id INTEGER)')
        self.db.execute('CREATE TABLE state_meta(key TEXT,value TEXT)');self.db.commit()
    def tearDown(self):self.db.close()
    def test_exact_state_is_verified_with_sqlite_query_only(self):
        self.db.execute('PRAGMA query_only=ON');counts=verify(self.db,self.expected);self.assertEqual(1,counts['sessions']);self.assertEqual(0,counts['worker_executions'])
    def test_matching_path_with_changed_revision_is_not_acceptance(self):
        self.db.execute('UPDATE sessions SET runtime_revision=4')
        with self.assertRaises(AssertionError):verify(self.db,self.expected)
    def test_matching_path_with_changed_generation_is_not_acceptance(self):
        self.db.execute('UPDATE sessions SET runtime_generation=4')
        with self.assertRaises(AssertionError):verify(self.db,self.expected)
    def test_foreign_session_id_or_directory_is_refused(self):
        for key,value in [('sessionId','foreign'),('cwd','/different')]:
            with self.subTest(key=key):
                expected={**self.expected,key:value}
                with self.assertRaises(AssertionError):verify(self.db,expected)
    def test_mutation_receipt_is_not_ignored_when_path_matches(self):
        self.db.execute('INSERT INTO state_meta VALUES (?,?)',('gateway.mutation.v1.fixture','{}'))
        with self.assertRaises(AssertionError):verify(self.db,self.expected)
    def test_unrelated_metadata_is_not_a_false_mutation_receipt(self):
        self.db.execute('INSERT INTO state_meta VALUES (?,?)',('fixture.read-only-setting','{}'));self.assertEqual(1,verify(self.db,self.expected)['sessions'])
    def test_each_kind_of_message_or_work_or_lock_breaks_zero_work_claim(self):
        for table in ('messages','session_admissions','hosted_rooms','worker_executions','session_turn_leases','compression_locks'):
            with self.subTest(table=table):
                self.db.execute('INSERT INTO '+table+' VALUES (1)')
                with self.assertRaises(AssertionError):verify(self.db,self.expected)
                self.db.execute('DELETE FROM '+table)
    def test_extra_session_cannot_be_accepted_as_the_same_profile(self):
        self.db.execute('INSERT INTO sessions VALUES (?,?,?,?)',('other','/fixture/project',2,3))
        with self.assertRaises(AssertionError):verify(self.db,self.expected)
    def test_boolean_negative_or_missing_fences_are_refused(self):
        variants=[{**self.expected,key:value} for key in ('revision','generation') for value in (True,-1,2**63)]
        variants.append({k:v for k,v in self.expected.items() if k!='generation'})
        for expected in variants:
            with self.subTest(expected=expected):
                with self.assertRaises(AssertionError):verify(self.db,expected)

class ProfileTicketProbeCliBoundaryTest(unittest.TestCase):
    def refused(self,flags,message):
        work=Path(tempfile.gettempdir())/('signal-grove-profile-cli-'+uuid.uuid4().hex);self.assertFalse(work.exists())
        command=[sys.executable,'-I','-S','-B',str(SCRIPT),'--source-root',str(SCRIPT.parent),'--dependency-site',str(SCRIPT.parent),'--work-root',str(work),*flags]
        result=subprocess.run(command,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=20)
        self.assertEqual(2,result.returncode,result.stdout);self.assertIn(message,result.stdout);self.assertFalse(work.exists())
    def test_backend_probe_cannot_claim_android_or_controlled_host_signals(self):
        for flags in [('--android-api','26'),('--emit-host-signals',)]:
            self.refused(['--probe-profile-tickets','webapp',*flags],'Backend profile probe does not accept')
    def test_probe_cannot_combine_with_mutating_or_history_fixture_modes(self):
        for flags in [('--drop-workspace-ack',),('--workspace-refusal','turn-lease'),('--read-api-history',)]:
            self.refused(['--probe-profile-tickets','gateway',*flags],'not allowed with argument')
    def test_android_fixture_still_requires_explicit_api(self):self.refused([],'Android fixture modes require --android-api')
    def test_android_profile_mode_requires_api_and_excludes_other_effects(self):
        self.refused(['--android-profile-tickets','webapp'],'Android fixture modes require --android-api')
        self.refused(['--android-profile-tickets','webapp','--android-api','37','--emit-host-signals'],'Android profile mode does not emit')
        for flags in [('--drop-workspace-ack',),('--workspace-refusal','turn-lease'),('--read-api-history',),('--probe-profile-tickets','gateway')]:
            self.refused(['--android-profile-tickets','gateway','--android-api','26',*flags],'not allowed with argument')


class AndroidProfileRpcBoundaryTest(unittest.TestCase):
    def setUp(self):
        tree=ast.parse(SCRIPT.read_text(encoding='utf-8'))
        node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='profile_android_rpc_allowed')
        space={};exec(compile(ast.Module(body=[node],type_ignores=[]),str(SCRIPT),'exec'),space)
        self.guard=space['profile_android_rpc_allowed'];self.cursor=dict(last_sequence=7,replay_epoch='actual-ack-epoch')
    def allowed(self,method,params,**extra):
        return self.guard(method,params,**(dict(profile='field',session_id='real-field-session',resume_cursor=self.cursor,resumed=False,replayed=False,pinged=False)|extra))
    def test_only_describe_is_allowed_before_actual_profile_binding(self):
        self.assertTrue(self.allowed('runtime.describe',{},profile=None))
        for method,params in [('gateway.ping',{}),('session.list',dict(scope='live',limit=200)),('session.resume',dict(session_id='real-field-session',omit_messages=True))]:
            self.assertFalse(self.allowed(method,params,profile=None))
        self.assertFalse(self.allowed('runtime.describe',dict(profile='field')))
    def test_inventory_is_live_exact_and_never_work(self):
        self.assertTrue(self.allowed('session.list',dict(scope='live',limit=200)))
        for params in [dict(scope='stored',limit=200),dict(scope='live',limit=True),dict(scope='live',limit=201),dict(scope='live',limit=200,profile='default')]:self.assertFalse(self.allowed('session.list',params))
        for method in ['session.create','session.mutate','session.submit','session.cancel','groups.list']:
            self.assertFalse(self.allowed(method,{}))
    def test_resume_is_one_reviewed_session_and_cannot_repeat(self):
        params=dict(session_id='real-field-session',omit_messages=True)
        self.assertTrue(self.allowed('session.resume',params))
        self.assertFalse(self.allowed('session.resume',params,resumed=True))
        for changed in [dict(session_id='default-session'),dict(omit_messages=1),dict(omit_messages=False),dict(profile='default')]:self.assertFalse(self.allowed('session.resume',params|changed))
    def test_replay_requires_exact_real_ack_and_one_resume(self):
        params=dict(session_id='real-field-session',**self.cursor)
        self.assertTrue(self.allowed('session.events.since',params,resumed=True))
        self.assertFalse(self.allowed('session.events.since',params))
        self.assertFalse(self.allowed('session.events.since',params,resumed=True,replayed=True))
        for changed in [dict(last_sequence=6),dict(last_sequence=True),dict(replay_epoch='stale'),dict(session_id='foreign'),dict(profile='field')]:self.assertFalse(self.allowed('session.events.since',params|changed,resumed=True))
        for cursor in [None,dict(last_sequence=-1,replay_epoch='actual-ack-epoch'),dict(last_sequence=True,replay_epoch='actual-ack-epoch'),dict(last_sequence=7,replay_epoch='')]:self.assertFalse(self.allowed('session.events.since',params,resumed=True,resume_cursor=cursor))
    def test_heartbeat_cannot_repeat_or_smuggle_profile(self):
        self.assertTrue(self.allowed('gateway.ping',{}));self.assertFalse(self.allowed('gateway.ping',{},pinged=True));self.assertFalse(self.allowed('gateway.ping',dict(profile='field')))

if __name__=='__main__':unittest.main()
