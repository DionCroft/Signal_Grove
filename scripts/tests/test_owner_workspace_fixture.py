"""Security boundaries of the actual-owner Android conformance fixture."""
import ast
from pathlib import Path
import re
import unittest

SOURCE=Path(__file__).resolve().parents[1]/'compatibility_fixture_owner_workspace_tcp.py'
namespace={'re':re}
tree=ast.parse(SOURCE.read_text(encoding='utf-8'))
selected=[node for node in tree.body if isinstance(node,ast.FunctionDef) and node.name in {'workspace_fixture_rpc_allowed','catalog_fixture_request_blocked'}]
assert len(selected)==2
exec(compile(ast.Module(body=selected,type_ignores=[]),str(SOURCE),'exec'),namespace)

class OwnerWorkspaceFixturePolicyTest(unittest.TestCase):
    def setUp(self):
        self.sid='local-owned';self.cwd='/fixture/workspace/project-b'
        self.command=dict(session_id=self.sid,request_id='11111111-2222-4333-8444-555555555555',
            expected_revision=7,expected_generation=8,operation='workspace',payload={'cwd':self.cwd})
    def allowed(self,method,params,claimed=False):
        return namespace['workspace_fixture_rpc_allowed'](method,params,session_id=self.sid,directory=self.cwd,
            revision=7,generation=8,mutation_claimed=claimed)
    def metadata(self,url='https://openrouter.ai/api/v1/models',data=None,headers=None,method='GET',callers=None):
        return namespace['catalog_fixture_request_blocked'](url,data,{} if headers is None else headers,method,
            [('hermes_cli.models','_urlopen_model_catalog_request')] if callers is None else callers)
    def test_one_exact_disposable_workspace_write_only(self):
        self.assertTrue(self.allowed('session.mutate',self.command))
        self.assertFalse(self.allowed('session.mutate',self.command,True))
        for key,value in [('session_id','foreign'),('operation','model'),('payload',{'cwd':'/other'}),
            ('expected_revision',6),('expected_generation',9),('request_id','not-a-uuid')]:
            self.assertFalse(self.allowed('session.mutate',{**self.command,key:value}),key)
    def test_fences_reject_boolean_float_and_string_coercion(self):
        for key in ('expected_revision','expected_generation'):
            for value in (True,False,7.0,'7',None):
                self.assertFalse(self.allowed('session.mutate',{**self.command,key:value}),(key,value))
        for key,value in (('expected_revision',False),('expected_revision',0.0),('expected_generation',True),('expected_generation',1.0)):
            params={**self.command,'expected_revision':0,'expected_generation':1,key:value}
            self.assertFalse(namespace['workspace_fixture_rpc_allowed']('session.mutate',params,session_id=self.sid,
                directory=self.cwd,revision=0,generation=1,mutation_claimed=False),(key,value))
    def test_uuid_is_canonical_v4_and_payload_has_no_other_fields(self):
        for value in ('11111111-2222-5333-8444-555555555555','11111111-2222-4333-7444-555555555555','A'*36):
            self.assertFalse(self.allowed('session.mutate',{**self.command,'request_id':value}))
        self.assertFalse(self.allowed('session.mutate',{**self.command,'profile':'field'}))
        self.assertFalse(self.allowed('session.mutate',{**self.command,'payload':{'cwd':self.cwd,'other':True}}))
    def test_no_other_work_or_control_methods_are_forwarded(self):
        for method in ('session.submit','session.create','session.cancel','session.close','slash.exec',
            'worker.register','session.resolve_unknown','groups.create','approval.respond','unreviewed'):
            self.assertFalse(self.allowed(method,self.command),method)
    def test_only_closed_identity_ping_and_inventory_shapes_are_allowed(self):
        for method in ('runtime.describe','gateway.ping'):
            self.assertTrue(self.allowed(method,{}));self.assertFalse(self.allowed(method,{'profile':'field'}))
        for scope in ('stored','live'):
            self.assertTrue(self.allowed('session.list',{'scope':scope,'limit':200}))
        for params in ({'scope':'all','limit':200},{'scope':'live','limit':True},{'scope':'live','limit':200.0},
            {'scope':'live','limit':201},{'scope':'live','limit':200,'include_hidden':True}):
            self.assertFalse(self.allowed('session.list',params))
    def test_resume_is_exact_and_never_reads_messages(self):
        self.assertTrue(self.allowed('session.resume',{'session_id':self.sid,'omit_messages':True}))
        for value in (False,1,0,None,'true'):
            self.assertFalse(self.allowed('session.resume',{'session_id':self.sid,'omit_messages':value}))
        self.assertFalse(self.allowed('session.resume',{'session_id':'other','omit_messages':True}))
        self.assertFalse(self.allowed('session.resume',{'session_id':self.sid,'omit_messages':True,'title':'other'}))
    def test_replay_is_bounded_to_one_session_and_exact_cursor_types(self):
        params={'session_id':self.sid,'last_sequence':0,'replay_epoch':'ring'}
        self.assertTrue(self.allowed('session.events.since',params))
        for key,values in [('session_id',['other']),('last_sequence',[-1,True,0.0,'0',2**63]),('replay_epoch',['','x'*257,None,1])]:
            for value in values:self.assertFalse(self.allowed('session.events.since',{**params,key:value}),(key,value))
        self.assertFalse(self.allowed('session.events.since',{**params,'profile':'field'}))
    def test_non_mapping_rpc_params_never_reach_actual_dispatch(self):
        for value in (None,[],True,1,'{}'):
            for method in ('runtime.describe','session.mutate','session.resume'):
                self.assertFalse(self.allowed(method,value))
    def test_only_named_public_metadata_reads_are_intentionally_refused(self):
        self.assertTrue(self.metadata())
        self.assertTrue(self.metadata('https://portal.nousresearch.com/api/nous/recommended-models'))
        self.assertTrue(self.metadata('https://web-assets.nousresearch.com/dynamic/portal.nousresearch.com/api/nous/recommended-models'))
        for url in ('https://hermes-agent.nousresearch.com/docs/api/model-catalog.json',
            'https://raw.githubusercontent.com/NousResearch/hermes-agent/main/website/static/api/model-catalog.json'):
            self.assertTrue(self.metadata(url,callers=[('hermes_cli.model_catalog','_fetch_manifest')]))
    def test_unknown_urls_callers_and_writes_are_not_metadata_exceptions(self):
        for url in ('https://example.test/models','https://openrouter.ai/api/v1/models?token=secret',
            'http://openrouter.ai/api/v1/models','https://openrouter.ai/api/v1/chat/completions'):
            self.assertFalse(self.metadata(url))
        self.assertFalse(self.metadata(callers=[]))
        self.assertFalse(self.metadata(callers=[('hermes_cli.models','unreviewed')]))
        for method in ('POST','PUT','DELETE'):self.assertFalse(self.metadata(method=method))
        self.assertFalse(self.metadata(data=b'{}'))
    def test_credential_bearing_requests_never_get_a_metadata_exception(self):
        for key in ('Authorization','authorization','COOKIE','Cookie'):
            self.assertFalse(self.metadata(headers={key:'fixture-only'}))
    def test_manifest_urls_require_the_manifest_call_site(self):
        self.assertFalse(self.metadata('https://hermes-agent.nousresearch.com/docs/api/model-catalog.json'))

if __name__=='__main__':unittest.main()
