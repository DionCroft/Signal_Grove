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
        'cron':"@router.get('/api/cron/jobs')\ndef list_cron_jobs():\n    return []\n",
        'cron_helpers':"raise RuntimeError('Do not execute upstream')\ndef _call_cron_for_profile():\n    return []\n",
        'cron_time':"MIN_GRACE_MINUTES=0\nMAX_GRACE_MINUTES=24*60\n",
        'cron_invariants':"raise RuntimeError('Do not execute upstream')\ndef validate_schedule_knobs():\n    pass\n",
        'cron_scheduler':"raise RuntimeError('Do not execute upstream')\ndef create_job_with_scheduler_registration():\n    return {}\n",
        'gateway_models':"class CronAction:\n    pause='pause'\nclass CronManageParams:\n    profile:str|None=None\n",
        'gateway_rpc':"@_scoped_rpc('cron.manage',5023)\ndef _(rid,params):\n    return params\n@method('other')\ndef _(rid,params):\n    return {}\n",
        'gateway_scope':'','gateway_runtime':'','gateway_ws':'','chat_ws':"@router.websocket('/api/ws')\nasync def gateway_ws(ws):\n    pass\n",
        'chat_auth':'','ws_boundary':"_GATEWAY_WS_PROTOCOL='hermes-gateway-v1'\n_GATEWAY_WS_TICKET_PROTOCOL_PREFIX='hermes-gateway-ticket.'\n",'ws_tickets':'TTL_SECONDS=30\n','cron_cancel':'','cron_tool':"_JOB_ACTIONS={'pause':handler}\n",'cron_format':"_FORMAT_JOB_OPTIONAL_KEYS=('script',)\n",
        'skills':"@router.get('/api/skills')\ndef get_skills():\n    return []\n",
        'profile_helpers':'',
        'web_common':'',
        'host_actions':'',
        'bundled_ops':'',
        'gateway_plugin_models':'',
        'gateway_common':'',
        'plugin_core':'',
        'plugin_servers':'',
        'gateway_lookup':'',
        'gateway_method_ctx':'',
        'plugin_inspect':'',
        'plugin_presence':'',
        'plugin_catalog_gate':'',
        'plugin_catalog_loader':'',
        'plugin_git':'',
        'plugin_install':'',
        'plugin_activation':'',
        'plugin_transaction':'',
        'plugin_admission':'',
        'plugin_legacy':'',
        'pm_client':'',
        'plugin_remove':'',
        'plugin_oauth':'',
        'plugin_grant_roots':'',
        'plugin_update':'',
        'plugin_guard':'',
        'hub_cli':'',
        'hub_search':'',
        'hub_claw':'',
        'hub_official':'',
        'hub_guard':'',
        'hub_tier1':'',
        'hub_install':'',
        'hub_models':'',
        'skills_config':'', 'skill_usage':'', 'skill_manager':'', 'skill_catalog':'',
        'skill_utils':"ESSENTIAL_SKILLS:frozenset=frozenset({'hermes-agent'})\n",
        'web_bootstrap':"raise RuntimeError('Do not execute upstream')\napp=FastAPI()\n",
        'cron_jobs':"raise RuntimeError('Do not execute upstream')\n_CREATE_FIELD_NORMALIZERS:dict={}\ndef get_job():\n    return {}\n",
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
    def test_plugin_update_grants_scan_policy_surfaces_and_new_revision_guards_require_review(self):
        initial=sources();initial['plugin_update']="def dashboard_update_user_plugin(name,accept_capabilities=False,force=False):\n    return name\n"
        initial['plugin_guard']="def should_allow_plugin_install(verdict,force=False):\n    return verdict!='dangerous'\n"
        initial['plugin_core']="def _scan_plugin_tree(reviewed_pin=False):\n    return reviewed_pin\n"
        initial['plugin_catalog_gate']="_SURFACE_LABELS={'tools':'tools'}\n"
        initial['gateway_plugin_models']="class PluginsManageParams:\n    accept_capabilities:bool|None=None\n"
        old=checker.extract(initial);tracked=baseline(old);tracked['mobile_used_handlers']+=['plugin_update:dashboard_update_user_plugin','plugin_guard:should_allow_plugin_install','plugin_core:_scan_plugin_tree','plugin_catalog_gate:_SURFACE_LABELS','gateway_plugin_models:schema:PluginsManageParams']
        for source,before,after in [('plugin_update','accept_capabilities=False','accept_capabilities=True'),('plugin_guard',"verdict!='dangerous'",'True'),('plugin_core','return reviewed_pin','return True'),('plugin_catalog_gate',"'tools':'tools'","'oauth':'oauth'"),('gateway_plugin_models','accept_capabilities:bool|None=None','accept_capabilities:bool|None=None\n    expected_revision:str|None=None')]:
            changed=dict(initial);changed[source]=changed[source].replace(before,after)
            self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'])

    def test_plugin_removal_target_pending_delete_grants_and_profile_path_require_review(self):
        initial=sources();initial['plugin_remove']="_PENDING_DELETE_DIR='plugin-pending-delete'\ndef _remove_user_plugin(name):\n    return name\n"
        initial['plugin_oauth']="def forget_plugin_grants(plugin):\n    return plugin\n"
        initial['profiles']="def _profile_to_dict(info):\n    return {'path':info.path}\n"
        initial['gateway_plugin_models']="class PluginsManageResult:\n    cleared_memory_provider:bool|None=None\n"
        old=checker.extract(initial);tracked=baseline(old);tracked['mobile_used_handlers']+=['plugin_remove:_PENDING_DELETE_DIR','plugin_remove:_remove_user_plugin','plugin_oauth:forget_plugin_grants','profiles:_profile_to_dict'];tracked['mobile_used_request_fields']+=['gateway_plugin_models:PluginsManageResult.cleared_memory_provider']
        for source,before,after in [('plugin_remove','plugin-pending-delete','other'),('plugin_remove','return name','return None'),('plugin_oauth','return plugin','return None'),('profiles','info.path','info.name'),('gateway_plugin_models','cleared_memory_provider:bool','cleared_memory_provider:str')]:
            changed=dict(initial);changed[source]=changed[source].replace(before,after)
            self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'])

    def test_plugin_toggle_and_legacy_redirect_table_changes_require_review(self):
        initial=sources();initial['plugin_legacy']="LEFT_CORE:tuple=('observability/langfuse',)\ndef legacy_plugin_alias(name):\n    return name in LEFT_CORE\n"
        initial['gateway_rpc']="def _plugins_toggle(params):\n    return params['key']\n"
        old=checker.extract(initial);tracked=baseline(old);tracked['mobile_used_handlers']+=['plugin_legacy:LEFT_CORE','plugin_legacy:legacy_plugin_alias','gateway_rpc:_plugins_toggle']
        for source,before,after in [('plugin_legacy','observability/langfuse','other/redirect'),('plugin_legacy','name in LEFT_CORE','True'),('gateway_rpc',"params['key']","params['name']")]:
            changed=dict(initial);changed[source]=changed[source].replace(before,after)
            self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'])

    def test_plugin_inspected_guard_optional_activation_and_restart_targets_require_review(self):
        initial=sources();initial['plugin_install']="def _place_tree(ref,inspected_sha):\n    return None if ref else inspected_sha\n"
        initial['gateway_plugin_models']="class PluginsManageParams:\n    inspected_sha:str|None=None\nclass PluginActivation:\n    deferred:dict={}\nclass PluginRestartTarget:\n    backend='backend'\n"
        old=checker.extract(initial);tracked=baseline(old)
        tracked['mobile_used_handlers']+=['plugin_install:_place_tree','gateway_plugin_models:enum:PluginRestartTarget']
        tracked['mobile_used_request_fields']+=['gateway_plugin_models:PluginsManageParams.inspected_sha','gateway_plugin_models:PluginActivation.deferred']
        for source,before,after in [('plugin_install','None if ref else inspected_sha','None'),('gateway_plugin_models','inspected_sha:str','inspected_sha:int'),('gateway_plugin_models','deferred:dict','deferred:list'),('gateway_plugin_models',"backend='backend'","backend='other'")]:
            changed=dict(initial);changed[source]=changed[source].replace(before,after)
            self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'])

    def test_plugin_catalog_lookup_registration_and_inspection_cache_are_watched(self):
        initial=sources();initial['gateway_lookup']="@method('plugins.catalog.lookup')\ndef _(rid,params):\n    return params\n"
        initial['plugin_inspect']="_RESULT_TTL_SECONDS=600\ndef inspect_plugin_source(identifier):\n    return identifier\n"
        initial['gateway_plugin_models']="class PluginCatalogLookupStatus:\n    found='found'\nclass PluginCatalogLookupEntry:\n    sha:str\n"
        old=checker.extract(initial);tracked=baseline(old)
        tracked['mobile_used_handlers']+=['gateway_lookup:rpc:plugins.catalog.lookup','plugin_inspect:_RESULT_TTL_SECONDS','plugin_inspect:inspect_plugin_source','gateway_plugin_models:enum:PluginCatalogLookupStatus']
        tracked['mobile_used_request_fields'].append('gateway_plugin_models:PluginCatalogLookupEntry.sha')
        for source,before,after in [('gateway_lookup','plugins.catalog.lookup','plugins.lookup.renamed'),('plugin_inspect','600','60'),('plugin_inspect','return identifier','return None'),('gateway_plugin_models',"found='found'","found='changed'"),('gateway_plugin_models','sha:str','sha:int')]:
            changed=dict(initial);changed[source]=changed[source].replace(before,after)
            self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'])

    def test_scoped_plugin_dispatch_mapping_action_enum_and_profile_are_watched(self):
        initial=sources();initial['gateway_rpc']+="_PLUGINS_ACTIONS={'list':_plugins_list}\n@_scoped_rpc('plugins.manage')\ndef _(rid,params):\n    return params\n"
        initial['gateway_plugin_models']="class PluginsAction:\n    list='list'\nclass AgentPluginRow:\n    key:str\n"
        initial['gateway_common']="class ProfileParams:\n    profile:str|None=None\n"
        old=checker.extract(initial);tracked=baseline(old)
        tracked['mobile_used_handlers']+=['gateway_rpc:rpc:plugins.manage','gateway_rpc:_PLUGINS_ACTIONS','gateway_plugin_models:enum:PluginsAction']
        tracked['mobile_used_request_fields']+=['gateway_common:ProfileParams.profile','gateway_plugin_models:AgentPluginRow.key']
        for source,before,after in [('gateway_rpc',"'list':_plugins_list","'list':different_handler"),('gateway_rpc','plugins.manage','plugins.renamed'),('gateway_plugin_models',"list='list'","list='other'"),('gateway_plugin_models','key:str','key:int'),('gateway_common','profile:str','profile:int')]:
            changed=dict(initial);changed[source]=changed[source].replace(before,after)
            self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'])

    def test_maintenance_routes_and_profile_only_update_contract_require_review(self):
        initial=sources();initial['skills']+="@hub_router.post('/api/skills/hub/uninstall')\ndef uninstall_skill_hub():\n    return {}\n@hub_router.post('/api/skills/hub/update')\ndef update_skills_hub():\n    return {}\n"
        initial['models']+="class SkillUninstallRequest:\n    name:str\n    profile:str|None=None\nclass SkillsUpdateRequest:\n    profile:str|None=None\n"
        old=checker.extract(initial);tracked=baseline(old)
        tracked['mobile_used_management_routes']+=['POST /api/skills/hub/uninstall','POST /api/skills/hub/update']
        tracked['mobile_used_request_fields']+=['models:SkillUninstallRequest.name','models:SkillUninstallRequest.profile','models:SkillsUpdateRequest.profile']
        for source,before,after in [('skills','/api/skills/hub/update','/api/skills/hub/changed'),('skills','/api/skills/hub/uninstall','/api/skills/hub/changed'),('models','name:str','name:int'),('models','profile:str|None=None','profile:int|None=None')]:
            changed=dict(initial);changed[source]=changed[source].replace(before,after)
            self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'])

    def test_bundled_restore_prefix_and_installer_profile_field_are_tracked(self):
        initial=sources();initial['bundled_ops']="_OWN_REPO_PREFIXES=('nousresearch/hermes-agent/skills/',)\n"
        initial['models']+="class SkillInstallRequest:\n    identifier:str\n    profile:str|None=None\n"
        old=checker.extract(initial);tracked=baseline(old)
        tracked['mobile_used_handlers'].append('bundled_ops:_OWN_REPO_PREFIXES')
        tracked['mobile_used_request_fields']+=['models:SkillInstallRequest.identifier','models:SkillInstallRequest.profile']
        for source,before,after in [('bundled_ops','nousresearch/hermes-agent/skills/','changed/'),('models','identifier:str','identifier:int'),('models','profile:str|None=None','scope:str|None=None')]:
            changed=dict(initial);changed[source]=changed[source].replace(before,after)
            self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'])

    def test_hub_routes_policy_timeout_and_metadata_changes_require_review(self):
        initial=sources()
        initial['skills']+="@hub_router.get('/api/skills/hub/scan')\ndef scan_skill_hub():\n    return {}\n"
        initial['hub_guard']="INSTALL_POLICY={'community':{'safe':'allow'}}\nVERDICT_INDEX={'safe':0}\n"
        initial['hub_tier1']="SCAN_TIMEOUT_SECONDS=120\nSECRETS_CLASS_CHECKS=frozenset({'hardcoded_secrets'})\n"
        initial['hub_models']="class SkillMeta:\n    identifier:str\n"
        old=checker.extract(initial);tracked=baseline(old)
        tracked['mobile_used_management_routes'].append('GET /api/skills/hub/scan')
        tracked['mobile_used_handlers']+=['skills:scan_skill_hub','hub_guard:INSTALL_POLICY','hub_tier1:SCAN_TIMEOUT_SECONDS','hub_tier1:SECRETS_CLASS_CHECKS']
        tracked['mobile_used_request_fields'].append('hub_models:SkillMeta.identifier')
        self.assertFalse(checker.compare(tracked,{'commit':'b'*40,'contract':old})['mobile_review_required'])
        for source,before,after in [('skills','/api/skills/hub/scan','/api/skills/hub/changed'),('hub_guard',"'allow'","'ask'"),('hub_tier1','120','30'),('hub_tier1','hardcoded_secrets','changed_check'),('hub_models','identifier:str','identifier:int')]:
            changed=dict(initial);changed[source]=changed[source].replace(before,after)
            self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'])

    def test_gateway_action_enum_mapping_and_request_profile_are_reviewed_contracts(self):
        old=checker.extract(sources());tracked=baseline(old)
        tracked['mobile_used_handlers']+=['gateway_models:enum:CronAction','cron_tool:_JOB_ACTIONS','cron_format:_FORMAT_JOB_OPTIONAL_KEYS']
        tracked['mobile_used_request_fields'].append('gateway_models:CronManageParams.profile')
        for source,before,after in [('gateway_models',"pause='pause'","pause='other'"),('gateway_models','profile:str|None=None','profile:int|None=None'),('cron_tool',"'pause':handler","'pause':different_handler"),('cron_format',"('script',)","('script','new_field')")]:
            changed=sources();changed[source]=changed[source].replace(before,after)
            self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'])

    def test_reviewed_handler_variant_is_exact_and_requires_pinned_evidence(self):
        old=checker.extract(sources());changed=sources();changed['mcp']=changed['mcp'].replace("return {'ok':True}","return {'ok':False}");new=checker.extract(changed)
        tracked=baseline(old);key='mcp:set_mcp_server_enabled'
        tracked['reviewed_handler_variants']={key:[{'sha256':new['handlers'][key],'commit':'c'*40,'reason':'Synthetic review fixture'}]}
        self.assertFalse(checker.compare(tracked,{'commit':'b'*40,'contract':new})['mobile_review_required'])
        changed['mcp']=changed['mcp'].replace("return {'ok':False}","return {'ok':None}")
        self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'])
        for field,value in [('commit','main'),('sha256','*'),('reason',''),('reason',None),('sha256',None)]:
            invalid=copy.deepcopy(tracked);invalid['reviewed_handler_variants'][key][0][field]=value
            with self.assertRaises(ValueError):checker.compare(invalid,{'commit':'b'*40,'contract':new})

    def test_named_rpc_handlers_do_not_collapse_to_the_last_underscore_function(self):
        old=checker.extract(sources());self.assertIn('gateway_rpc:rpc:cron.manage',old['handlers']);self.assertIn('gateway_rpc:rpc:other',old['handlers'])
        self.assertIn('WEBSOCKET /api/ws',old['management_routes'])
        self.assertIn('ws_boundary:_GATEWAY_WS_PROTOCOL',old['handlers']);self.assertIn('ws_tickets:TTL_SECONDS',old['handlers'])
        tracked=baseline(old);tracked['mobile_used_handlers'].append('gateway_rpc:rpc:cron.manage')
        changed=sources();changed['gateway_rpc']=changed['gateway_rpc'].replace('return params','return {}')
        self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'])
        changed=sources();changed['gateway_rpc']=changed['gateway_rpc'].replace("'cron.manage'","'renamed'")
        self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'])

    def test_skill_routes_and_essential_exclusions_are_tracked_without_execution(self):
        old=checker.extract(sources());self.assertIn('GET /api/skills',old['management_routes'])
        tracked=baseline(old);tracked['mobile_used_management_routes'].append('GET /api/skills');tracked['mobile_used_handlers'].append('skill_utils:ESSENTIAL_SKILLS')
        changed=sources();changed['skills']='';self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'])
        changed=sources();changed['skill_utils']=changed['skill_utils'].replace("'hermes-agent'","'hermes-agent','new-required'")
        self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'])

    def test_implicit_openapi_route_removal_and_dynamic_configuration_cannot_pass_silently(self):
        old=checker.extract(sources());self.assertIn('GET /openapi.json',old['management_routes'])
        changed=sources();changed['web_bootstrap']='app=FastAPI(openapi_url=None)';new=checker.extract(changed)
        tracked=baseline(old);tracked['mobile_used_management_routes'].append('GET /openapi.json')
        self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':new})['mobile_review_required'])
        changed['web_bootstrap']='app=FastAPI(openapi_url=unsafe())'
        with self.assertRaises(ValueError):checker.extract(changed)
    def test_management_grace_bounds_are_read_as_data_without_arbitrary_evaluation(self):
        old=checker.extract(sources());self.assertEqual(1440,old['job_constraints']['management_max_grace_minutes'])
        changed=sources();changed['cron_time']='MIN_GRACE_MINUTES=0\nMAX_GRACE_MINUTES=10*60\n'
        self.assertTrue(compare(old,checker.extract(changed))['mobile_review_required'])
        changed['cron_time']='MIN_GRACE_MINUTES=0\nMAX_GRACE_MINUTES=unsafe()\n'
        with self.assertRaises(ValueError):checker.extract(changed)
    def test_create_normalizer_mapping_is_tracked_with_executable_definitions(self):
        old=checker.extract(sources());changed=sources();changed['cron_jobs']=changed['cron_jobs'].replace('_CREATE_FIELD_NORMALIZERS:dict={}',"_CREATE_FIELD_NORMALIZERS:dict={'model':normalize}")
        tracked=baseline(old);tracked['mobile_used_handlers'].append('cron_jobs:_CREATE_FIELD_NORMALIZERS')
        self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'])
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
