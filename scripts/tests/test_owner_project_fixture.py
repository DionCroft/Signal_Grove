"""The real-owner project probe forwards only finite reads and preserves complete stored metadata."""
import ast,contextlib,hashlib,json,sqlite3,unittest
from pathlib import Path

source=Path(__file__).resolve().parents[1]/'compatibility_fixture_owner_workspace_tcp.py'
tree=ast.parse(source.read_text(encoding='utf-8'));names={'project_android_rpc_allowed','project_fixture_tables','verify_project_fixture_store','history_snapshot_digest'}
nodes=[n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name in names];assert len(nodes)==4
space={'hashlib':hashlib,'json':json,'re':__import__('re')};exec(compile(ast.Module(body=nodes,type_ignores=[]),str(source),'exec'),space)
class OwnerProjectFixtureTest(unittest.TestCase):
    def allowed(self,method,params,profile='default'):
        return space['project_android_rpc_allowed'](method,params,profile=profile,projects={'default':'p_00000001','field':'p_00000002'})
    def test_descriptor_is_the_only_unbound_request(self):
        self.assertTrue(self.allowed('runtime.describe',{},None));self.assertFalse(self.allowed('projects.list',{'profile':'default'},None));self.assertFalse(self.allowed('runtime.describe',{'profile':'default'}))
    def test_metadata_profile_must_match_the_observed_runtime_binding(self):
        self.assertTrue(self.allowed('projects.list',{'profile':'field'},'field'));self.assertFalse(self.allowed('projects.list',{'profile':'field'},'default'));self.assertFalse(self.allowed('projects.list',{'profile':'default','scan':True}))
    def test_only_seeded_project_ids_can_be_looked_up_for_owned_or_foreign_refusal(self):
        for value in ['p_00000001','p_00000002']:self.assertTrue(self.allowed('projects.get',{'profile':'default','id':value}))
        for value in ['unreviewed','fixture-project','../secret',None]:self.assertFalse(self.allowed('projects.get',{'profile':'default','id':value}))
    def test_grouping_request_is_exactly_owned_and_globally_bounded(self):
        params={'profile':'default','project_id':'p_00000001','session_limit':500};self.assertTrue(self.allowed('projects.project_sessions',params))
        for key,value in [('project_id','p_00000002'),('profile','field'),('session_limit',True),('session_limit',500.0),('session_limit','500'),('session_limit',501)]:self.assertFalse(self.allowed('projects.project_sessions',{**params,key:value}))
        self.assertFalse(self.allowed('projects.project_sessions',{**params,'scan':True}))
    def test_only_stored_inventory_can_create_a_pure_review(self):
        self.assertTrue(self.allowed('session.list',{'scope':'stored','limit':200}))
        for params in [{'scope':'live','limit':200},{'scope':'stored','limit':True},{'scope':'stored','limit':201},{'scope':'stored','limit':200,'profile':'field'}]:self.assertFalse(self.allowed('session.list',params))
    def test_discovery_adoption_mutation_execution_and_approvals_never_forward(self):
        for method in ['projects.tree','projects.discover_repos','projects.record_repos','projects.create','projects.update','session.create','session.resume','session.mutate','session.submit','session.cancel','approval.respond','worker.execute','gateway.ping','session.events.since']:
            self.assertFalse(self.allowed(method,{}),method)
    def test_lossless_snapshot_rejects_unknown_tables_and_retains_blob_types(self):
        with contextlib.closing(sqlite3.connect(':memory:')) as db:
            db.execute('CREATE TABLE sessions (id TEXT, content BLOB)');db.execute('CREATE TABLE messages (id TEXT, content BLOB)');db.execute('INSERT INTO sessions VALUES (?,?)',('fixture',b'\x00\xff'))
            value=space['project_fixture_tables'](db,('sessions','messages'));self.assertEqual(b'\x00\xff',value['sessions'][0]['content'])
            with self.assertRaises(AssertionError):space['project_fixture_tables'](db,('state_meta',))
    def store(self):
        db=sqlite3.connect(':memory:');db.executescript('CREATE TABLE sessions (id TEXT,cwd TEXT,ended_at INTEGER);CREATE TABLE messages (id INTEGER,content BLOB);CREATE TABLE state_meta (key TEXT);'+''.join('CREATE TABLE '+name+' (id TEXT);' for name in ('session_admissions','hosted_rooms','worker_executions','session_turn_leases','compression_locks')))
        db.executemany('INSERT INTO sessions VALUES (?,?,?)',[(str(i),'/fixture',1) for i in range(7)]);db.executemany('INSERT INTO messages VALUES (?,?)',[(i,b'\x00\xff') for i in range(12)])
        expected={'historyDigest':space['history_snapshot_digest'](space['project_fixture_tables'](db,('sessions','messages')))};return db,expected
    def test_final_store_check_accepts_exact_seed_and_rejects_each_work_effect(self):
        db,expected=self.store()
        try:
            self.assertEqual(7,space['verify_project_fixture_store'](db,expected)['sessions'])
            for table in ('session_admissions','hosted_rooms','worker_executions','session_turn_leases','compression_locks'):
                with self.subTest(table=table):
                    db.execute('INSERT INTO '+table+' VALUES (?)',('unexpected',))
                    with self.assertRaises(AssertionError):space['verify_project_fixture_store'](db,expected)
                    db.execute('DELETE FROM '+table)
        finally:db.close()
    def test_final_store_check_rejects_changed_history_or_workspace_without_count_changes(self):
        for sql in ["UPDATE sessions SET cwd='/other' WHERE id='0'","UPDATE messages SET content='00ff' WHERE id=0","UPDATE sessions SET ended_at=NULL WHERE id='0'","INSERT INTO state_meta VALUES ('gateway.mutation.v1.fixture')"]:
            db,expected=self.store()
            try:
                db.execute(sql)
                with self.assertRaises(AssertionError):space['verify_project_fixture_store'](db,expected)
            finally:db.close()
    def test_snapshot_helpers_exist_before_supervisor_use(self):
        parent=next(n for n in tree.body if isinstance(n,ast.If) and ast.unparse(n.test)=='not a.owned_child')
        self.assertTrue(all(n.lineno<parent.lineno for n in nodes))
    def test_cli_mode_is_exclusive_and_preserves_the_native_owner_dispatch(self):
        text=source.read_text();self.assertIn("effect.add_argument('--android-project-conversations'",text);self.assertIn('await real_app(forwarded,bounded_ws,observed_ws)',text)
        fn=next(n for n in tree.body if isinstance(n,ast.AsyncFunctionDef) and n.name=='serve_project_conversations_android');body=ast.get_source_segment(text,fn)
        self.assertNotIn("rpc('session.create'",body);self.assertNotIn('runner._unserve_profile',body)
if __name__=='__main__':unittest.main()
