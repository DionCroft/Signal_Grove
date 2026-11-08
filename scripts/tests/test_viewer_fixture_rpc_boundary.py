"""The real-source fixture may observe a selected synthetic session, never submit work."""
import ast
from pathlib import Path
import unittest

_tree = ast.parse((Path(__file__).resolve().parents[1]/'compatibility_fixture_native_auth_tcp.py').read_text())
_function = next(n for n in _tree.body if isinstance(n, ast.FunctionDef) and n.name == 'canonical_fixture_rpc_allowed')
_namespace = {}
exec(compile(ast.Module(body=[_function], type_ignores=[]), '<local-fixture-rpc-boundary>', 'exec'), _namespace)
allowed = _namespace['canonical_fixture_rpc_allowed']

class ViewerFixtureRpcBoundaryTest(unittest.TestCase):
    def test_heartbeat_requires_explicit_opt_in_empty_params_and_finite_count(self):
        for flag in (False, None, 1, 'true'):
            self.assertFalse(allowed('gateway.ping', {}, 0, flag))
        self.assertTrue(allowed('gateway.ping', {}, 7, True))
        for params in ({'session_id':'shared-fixture'}, {'work':True}, [], None):
            self.assertFalse(allowed('gateway.ping', params, 0, True))
        for count in (-1, 8, 100, False, 0.0):
            self.assertFalse(allowed('gateway.ping', {}, count, True))

    def test_existing_reads_and_resume_keep_exact_payloads_and_session_scope(self):
        for method, params in [('runtime.describe', {}), ('session.list', {'scope':'stored','limit':200}),
                               ('session.list', {'scope':'live','limit':200}),
                               ('session.resume', {'session_id':'shared-fixture','omit_messages':True})]:
            with self.subTest(method=method,params=params):
                self.assertTrue(allowed(method,params,0))
                self.assertFalse(allowed(method,dict(params,extra=True),0,True))
        for scope in ('worker','all','unknown'):
            self.assertFalse(allowed('session.list', {'scope':scope,'limit':200},0,True))
        for limit in (True, 200.0, 201, 0):
            self.assertFalse(allowed('session.list', {'scope':'stored','limit':limit},0,True))
        self.assertFalse(allowed('session.resume', {'session_id':'foreign-history','omit_messages':True},0,True))
        self.assertFalse(allowed('session.resume', {'session_id':'shared-fixture','omit_messages':False},0,True))

    def test_replay_cursor_types_bounds_and_foreign_identity_are_refused(self):
        params={'session_id':'shared-fixture','last_sequence':0,'replay_epoch':'fixture-ring'}
        self.assertTrue(allowed('session.events.since',params,0))
        self.assertTrue(allowed('session.events.since',dict(params,last_sequence=2**63-1),7,True))
        for field, values in [('session_id',['foreign-history',None]),('last_sequence',[-1,2**63,True,0.0]),
                              ('replay_epoch',['','x'*257,'line\n',chr(0x80),1])]:
            for value in values:
                with self.subTest(field=field,value=value):
                    self.assertFalse(allowed('session.events.since',dict(params,**{field:value}),0,True))

    def test_opt_in_never_admits_work_controls_files_or_unknown_methods(self):
        for method in ('session.send','session.submit','session.interrupt','session.create','session.end',
                       'workspace.apply','worker.dispatch','cron.manage','tool.run','files.read','projects.set_active','unknown'):
            for params in ({},{'session_id':'shared-fixture'},{'scope':'stored','limit':200}):
                with self.subTest(method=method,params=params):self.assertFalse(allowed(method,params,0,True))

if __name__=='__main__':unittest.main()
