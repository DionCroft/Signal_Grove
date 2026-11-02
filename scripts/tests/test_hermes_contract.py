import ast
import copy
import importlib.util
from pathlib import Path
import unittest

spec=importlib.util.spec_from_file_location('hermes_contract',Path(__file__).resolve().parents[1]/'check_hermes_contract.py')
checker=importlib.util.module_from_spec(spec);spec.loader.exec_module(checker)

def sources():
    route="def _http_routes():\n    return [('POST','/v1/runs',handler)]\n"
    return {
        'api':"_STATIC_FEATURE_FLAGS={'enabled':True}\n_MAX_NAME_LENGTH=160\n_MAX_PROMPT_LENGTH=5000\n_UPDATE_ALLOWED_FIELDS={'enabled'}\ndef _http_route_table():\n    return [('GET','/v1/kept',handler)]\n",
        'runs':route,'rooms':route,
        'mcp_oauth':"raise RuntimeError('Do not execute upstream')\nclass DashboardOAuthFlow:\n    def snapshot(self):\n        return {}\n",
        'mcp_catalog':"raise RuntimeError('Do not execute upstream')\ndef install_entry():\n    pass\n",
        'mcp_helpers':"raise RuntimeError('Do not execute upstream')\ndef _mcp_server_summary():\n    return {}\n",
        'authority':"raise RuntimeError('Upstream must never execute')\ndef run_projection():\n    return {}\n",
        'auth':"class _NativeTokenBody:\n    code:str\n    code_verifier:str\n@router.post('/auth/native/token')\ndef auth_native_token():\n    return {}\n",
        'profiles':"@router.get('/api/profiles')\ndef list_profiles_endpoint():\n    return {}\n",
        'models':"class MCPEnabledToggle:\n    enabled:bool\n    profile:str|None=None\n",
        'mcp':"@router.put('/api/mcp/servers/{name}/enabled')\ndef set_mcp_server_enabled(body):\n    \"Docstring\"\n    return {'ok':True}\n",
    }

def baseline(contract):
    return {'upstream':{'commit':'a'*40,'contract':contract},'mobile_used_routes':['GET /v1/kept'],'mobile_used_static_flags':['enabled'],'mobile_used_job_update_fields':['enabled'],
        'mobile_used_management_routes':['PUT /api/mcp/servers/{name}/enabled'],'mobile_used_handlers':['mcp:set_mcp_server_enabled'],'mobile_used_request_fields':['models:MCPEnabledToggle.enabled']}

def compare(old,new):return checker.compare(baseline(old),{'commit':'b'*40,'contract':new})

class ContractWatchTest(unittest.TestCase):
    def test_optional_empty_ast_fields_do_not_depend_on_the_python_minor_version(self):
        first=ast.parse('def sample():\n    return b"fixture", ..., 2j\n').body[0]
        second=copy.deepcopy(first)
        if hasattr(second,'type_params'):delattr(second,'type_params')
        self.assertEqual(checker.fingerprint(first),checker.fingerprint(second))
    def test_sources_are_parsed_without_executing_top_level_code(self):
        result=checker.extract(sources())
        self.assertIn('authority:run_projection',result['handlers'])
        self.assertIn('POST /auth/native/token',result['management_routes'])
    def test_missing_source_cannot_produce_a_successful_empty_contract(self):
        partial=sources();partial.pop('mcp')
        with self.assertRaises(ValueError):checker.extract(partial)
    def test_management_removal_is_not_hidden_by_identical_api_route(self):
        old=checker.extract(sources());changed=sources();changed['mcp']=changed['mcp'].replace('/api/mcp/servers/{name}/enabled','/api/other')
        changed['api']=changed['api'].replace("('GET','/v1/kept',handler)","('GET','/v1/kept',handler),('PUT','/api/mcp/servers/{name}/enabled',handler)")
        result=compare(old,checker.extract(changed))
        self.assertTrue(result['mobile_review_required']);self.assertEqual(['PUT /api/mcp/servers/{name}/enabled'],result['mobile_relevant_changes']['removed_used_management_routes'])
    def test_used_body_type_and_handler_changes_require_review(self):
        old=checker.extract(sources());changed=sources();changed['models']=changed['models'].replace('enabled:bool','enabled:str');changed['mcp']=changed['mcp'].replace("'ok':True","'ok':False")
        result=compare(old,checker.extract(changed))
        self.assertTrue(result['mobile_review_required']);self.assertIn('models:MCPEnabledToggle.enabled',result['mobile_relevant_changes']['changed_used_request_fields']);self.assertIn('mcp:set_mcp_server_enabled',result['mobile_relevant_changes']['changed_used_handlers'])
    def test_comments_and_top_level_handler_docstrings_do_not_create_drift(self):
        old=checker.extract(sources());changed=sources();changed['mcp']='# comment\n'+changed['mcp'].replace('Docstring','Changed explanation')
        self.assertFalse(compare(old,checker.extract(changed))['mobile_review_required'])
    def test_unused_additions_are_visible_without_fabricating_a_break(self):
        old=checker.extract(sources());changed=sources();changed['mcp']+="\n@router.get('/api/new-feature')\ndef extra():\n    return {}\n"
        result=compare(old,checker.extract(changed))
        self.assertFalse(result['mobile_review_required']);self.assertEqual(['GET /api/new-feature'],result['added_management_routes'])
    def test_used_native_limits_and_fields_cannot_disappear_silently(self):
        old=checker.extract(sources());changed=sources();changed['api']=changed['api'].replace('_MAX_PROMPT_LENGTH=5000','_MAX_PROMPT_LENGTH=1000').replace("_UPDATE_ALLOWED_FIELDS={'enabled'}","_UPDATE_ALLOWED_FIELDS=unknown()")
        # Arbitrary function calls must not be evaluated while extracting a contract.
        with self.assertRaises(ValueError):checker.extract(changed)
        changed['api']=changed['api'].replace('unknown()','{}');result=compare(old,checker.extract(changed))
        self.assertTrue(result['mobile_review_required']);self.assertEqual(['enabled'],result['mobile_relevant_changes']['removed_used_job_update_fields'])

if __name__=='__main__':unittest.main()
