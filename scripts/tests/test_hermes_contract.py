import ast
import copy
import json
import importlib.util
from pathlib import Path
import unittest

spec=importlib.util.spec_from_file_location('hermes_contract',Path(__file__).resolve().parents[1]/'check_hermes_contract.py')
checker=importlib.util.module_from_spec(spec);spec.loader.exec_module(checker)

def sources():
    route="def _http_routes():\n    return [('POST','/v1/runs',handler)]\n"
    return {**{key:'' for key in checker.PLUGIN_UPDATE_CONTRACT_SOURCES},**{key:'' for key in checker.CHANNEL_CONTRACT_SOURCES},**{key:'' for key in checker.PROJECT_CONTRACT_SOURCES},**{key:'' for key in checker.KANBAN_CONTRACT_SOURCES},
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
        'web_deps':'',
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
        'plugin_settings':'',
        'env_routes':'',
        'custom_provider_config':'',
        'probe_metadata':'',
        'probe_route_resolver':'',
        'env_lifecycle':'',
        'env_config':'',
        'env_external':'',
        'model_routes':'',
        'moa_config':'',
        'oauth_routes':'','oauth_helpers':'','provider_auth':'',
        'pool_routes':'','credential_pool':'','pool_admin':'','pool_sources':'','pool_persistence':'','auth_middleware':'',
        'fallback_config':'','config_backend':'','config_worker_save':'','provider_identity':'',
        'fallback_wire':'','fallback_binding':'',
        'model_config':'',
        'model_inventory':'',
        'model_catalog':'',
        'model_auxiliary':'',
        'model_aux_runtime':'',
        'model_custom_runtime':'',
        'model_reasoning':'',
        'model_defaults':'',
        'model_aux_defaults':'',

        'model_switch':'',
        'model_guards':'',
        'model_cost':'',
        'model_data':'',
        'model_route_identity':'',



        'plugin_state':'',
        'mcp_discovery':'',

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

    def test_relay_link_mint_guards_original_code_state_and_optional_metadata_require_review(self):
        initial=sources()
        initial['relay_routes']="@router.post('/api/messaging/relay/{platform}/link')\nasync def request_relay_link(platform):\n    return await _call({'action':'request','platform':platform})\n@router.get('/api/messaging/relay/link/{code}')\nasync def get_relay_link_state(code):\n    return await _call({'action':'state','code':code})\n"
        initial['channel_relay_links']="def request_link(platform):\n    if platform not in relay_fronted_platforms():raise RelayLinkError('platform_not_fronted','refused')\n    if managed_by()=='portal':raise RelayLinkError('managed_by_portal','refused',409)\n    payload=_post('/manage/link',{'platform':platform})\n    code=str(payload.get('code') or '').strip()\n    return {'platform':platform,'code':code,'command':f'/link {code}','expires_at':payload.get('expiresAt'),'bot_url':shared_bot_url(platform,code)}\ndef link_code_state(code):\n    payload=_post('/manage/link/state',{'code':code})\n    return {'state':payload.get('state') or 'inactive','reason':payload.get('reason')}\n"
        old=checker.extract(initial);tracked=baseline(old);tracked['mobile_used_handlers']+=['relay_routes:request_relay_link','relay_routes:get_relay_link_state','channel_relay_links:request_link','channel_relay_links:link_code_state'];tracked['mobile_used_management_routes']+=['POST /api/messaging/relay/{platform}/link','GET /api/messaging/relay/link/{code}']
        mutations=[('relay_routes',"'/api/messaging/relay/{platform}/link'","'/api/other'"),('relay_routes',"'action':'request'","'action':'unlink'"),('relay_routes',"'platform':platform","'platform':other"),('relay_routes',"'action':'state'","'action':'request'"),('relay_routes',"'code':code","'code':other"),('relay_routes','request_relay_link(platform):','request_relay_link(platform,profile=None):'),('channel_relay_links','platform not in relay_fronted_platforms()','False'),('channel_relay_links',"managed_by()=='portal'","False"),('channel_relay_links',"'managed_by_portal'","'connector_refused'"),('channel_relay_links',"'/manage/link'","'/manage/other'"),('channel_relay_links',"f'/link {code}'","f'/unsafe {code}'"),('channel_relay_links',"payload.get('expiresAt')","payload.get('other')"),('channel_relay_links','shared_bot_url(platform,code)','other_bot(code)'),('channel_relay_links',"'/manage/link/state'","'/manage/link'"),('channel_relay_links',"or 'inactive'","or 'linked'"),('channel_relay_links',"'reason':payload.get('reason')","'reason':payload")]
        for module,before,after in mutations:
            with self.subTest(module=module,before=before):
                changed=copy.deepcopy(initial);self.assertIn(before,changed[module]);changed[module]=changed[module].replace(before,after);self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'])


    def test_relay_unlink_route_exact_target_forwarding_and_acknowledgement_require_review(self):
        initial=sources()
        initial['relay_routes']="@router.delete('/api/messaging/relay/{platform}/accounts/{platform_user_id}')\nasync def unlink_relay_account(platform,platform_user_id):\n    return await _call({'action':'unlink','platform':platform,'platform_user_id':platform_user_id})\n"
        initial['channel_relay_links']="_ACTIONS={'unlink':lambda p:unlink(p['platform'],p['platform_user_id'])}\ndef unlink(platform,platform_user_id):\n    _post('/manage/unlink',{'platform':platform,'platformUserId':platform_user_id})\n    return {'unlinked':True}\n"
        old=checker.extract(initial);tracked=baseline(old);tracked['mobile_used_management_routes'].append('DELETE /api/messaging/relay/{platform}/accounts/{platform_user_id}');tracked['mobile_used_handlers']+=['relay_routes:unlink_relay_account','channel_relay_links:unlink','channel_relay_links:binding:_ACTIONS']
        mutations=[('relay_routes',"'/api/messaging/relay/{platform}/accounts/{platform_user_id}'","'/api/other'"),('relay_routes',"'action':'unlink'","'action':'status'"),('relay_routes',"'platform_user_id':platform_user_id","'platform_user_id':other_account"),('relay_routes','platform,platform_user_id):','platform,platform_user_id,profile=None):'),('channel_relay_links',"'/manage/unlink'","'/manage/link'"),('channel_relay_links',"'platformUserId':platform_user_id","'platform_user_id':platform_user_id"),('channel_relay_links',"'unlinked':True","'unlinked':False"),('channel_relay_links',"lambda p:unlink(p['platform'],p['platform_user_id'])","lambda p:unlink('other',p['platform_user_id'])")]
        for module,before,after in mutations:
            with self.subTest(module=module,before=before):
                changed=copy.deepcopy(initial);self.assertIn(before,changed[module]);changed[module]=changed[module].replace(before,after);self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'])


    def test_relay_account_route_process_scope_authority_and_private_dependencies_require_review(self):
        initial=sources()
        initial['relay_routes']="import asyncio\n_NOT_READY=frozenset({'gateway_not_answering'})\n@router.get('/api/messaging/relay')\nasync def get_relay_links():\n    return await _call({'action':'status'})\nasync def _call(params):\n    return await asyncio.to_thread(_ask_gateway,params)\ndef _ask_gateway(params):\n    return query_gateway_control(get_process_hermes_home(),'relay-link',params=params,timeout=15)\n"
        initial['channel_relay_links']="import urllib.request\n_ACTIONS={'status':lambda p:link_status()}\ndef link_status():\n    return {'managed_by':managed_by(),'platforms':fronted_accounts()}\ndef managed_by():\n    return 'portal'\ndef portal_agents_url():\n    return portal_base()+'/agents'\n"
        initial['relay_control']="CONTROL_PROTOCOL_VERSION=1\ndef query_gateway_control(home,verb):\n    return send_to_home(home,verb)\n"
        initial['relay_auth']="def make_upgrade_token(id,secret):\n    return sign(id,secret)\n"
        initial['relay_media']="def media_base_url(url):\n    return url.removesuffix('/relay')\n"
        initial['relay_portal']="def _nous_portal_base_url(state):\n    return trusted_operator_override_or_allowed_host(state)\n"
        initial['relay_diagnostics']="_BODY_READ_LIMIT=4096\ndef read_error_body(error):\n    return error.read(_BODY_READ_LIMIT)\n"
        old=checker.extract(initial);tracked=baseline(old)
        keys=['relay_routes:get_relay_links','relay_routes:_call','relay_routes:_ask_gateway','relay_routes:import:asyncio','relay_routes:binding:_NOT_READY','channel_relay_links:binding:_ACTIONS','channel_relay_links:link_status','channel_relay_links:managed_by','channel_relay_links:portal_agents_url','relay_control:query_gateway_control','relay_control:binding:CONTROL_PROTOCOL_VERSION','relay_auth:make_upgrade_token','relay_media:media_base_url','relay_portal:_nous_portal_base_url','relay_diagnostics:read_error_body','relay_diagnostics:binding:_BODY_READ_LIMIT']
        tracked['mobile_used_handlers']+=keys;tracked['mobile_used_management_routes'].append('GET /api/messaging/relay')
        mutations=[('relay_routes',"'/api/messaging/relay'","'/api/changed'"),('relay_routes',"'action':'status'","'action':'unlink'"),('relay_routes','get_process_hermes_home()','selected_request_home()'),('relay_routes','timeout=15','timeout=1'),('relay_routes','import asyncio','import malicious as asyncio'),('relay_routes',"{'gateway_not_answering'}","{'other'}"),('channel_relay_links',"'status':lambda p:link_status()","'status':lambda p:unlink_everything()"),('channel_relay_links',"'portal'","'connector'"),('channel_relay_links',"'/agents'","'/unsafe'"),('relay_control','send_to_home(home,verb)','send_to_home(other,verb)'),('relay_control','VERSION=1','VERSION=2'),('relay_auth','sign(id,secret)','send_secret(id,secret)'),('relay_media',"'/relay'","'/other'"),('relay_portal','trusted_operator_override_or_allowed_host(state)','untrusted_network_value(state)'),('relay_diagnostics','error.read(_BODY_READ_LIMIT)','error.read()'),('relay_diagnostics','LIMIT=4096','LIMIT=999999')]
        for module,before,after in mutations:
            with self.subTest(module=module,before=before):
                changed=copy.deepcopy(initial);self.assertIn(before,changed[module]);changed[module]=changed[module].replace(before,after);self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'])
        introduced=copy.deepcopy(initial);introduced['relay_routes']='';original=checker.extract(introduced);accepted=baseline(original);accepted['mobile_used_handlers'].append('relay_routes:_ask_gateway');accepted['reviewed_handler_variants']={'relay_routes:_ask_gateway':[{'sha256':old['handlers']['relay_routes:_ask_gateway'],'commit':'c'*40,'reason':'Synthetic exact introduced process-scope fixture'}]}
        self.assertFalse(checker.compare(accepted,{'commit':'b'*40,'contract':old})['mobile_review_required'])
        initial['relay_routes']=initial['relay_routes'].replace('get_process_hermes_home()','other_home()');self.assertTrue(checker.compare(accepted,{'commit':'b'*40,'contract':checker.extract(initial)})['mobile_review_required'])

    def test_channel_profile_private_metadata_and_exact_introduced_helper_contracts_require_review(self):
        initial=sources()
        initial['models']+="class MessagingPlatformUpdate:\n    enabled:bool|None=None\n    env:dict[str,str]={}\n    clear_env:list[str]=[]\n    profile:str|None=None\n"
        initial['channels']="from hooks import late\n_profile_scope=late('_profile_scope','profile_helpers')\n@router.get('/api/messaging/platforms')\nasync def get_messaging_platforms(profile=None):\n    return _profile_scope(profile)\n@router.put('/api/messaging/platforms/{platform_id}')\nasync def update_messaging_platform(platform_id,body,profile=None):\n    target=body.profile or profile\n    validate_whole_request(body)\n    write_selected_fields(body,target)\n    return {'ok':True,'platform':platform_id,'hot_served':False}\n@router.post('/api/messaging/platforms/{platform_id}/test')\nasync def test_messaging_platform(platform_id,profile=None):\n    return posture(platform_id,profile)\ndef _messaging_platform_payload(entry):\n    return {'value':entry.value if entry.is_list and not entry.is_password else None}\ndef _relay_channel_view(home):\n    if home!=process_home():return set(),False\n    return relay_channel_view()\n"
        initial['channel_catalog']="_PLATFORM_OVERRIDES={'webhook':{'env_vars':('WEBHOOK_SECRET',)}}\ndef _write_platform_enabled(platform,enabled):\n    return narrow_field_write(platform,'enabled',enabled)\n"
        initial['channel_registry']="class PlatformEntry:\n    dashboard_env_vars:tuple=()\n    env_value_rules:dict={}\n"
        initial['channel_status']="def resolve_gateway_liveness(profile_dir=None):\n    return read_scoped_runtime(profile_dir)\n"
        initial['channel_gate']="def platform_gate_env(name):\n    return profile_secret(name)\n"
        initial['channel_relay_config']="def relay_explicitly_disabled():\n    return launch_profile_disable()\n"
        initial['channel_relay_links']="import os\ndef relay_channel_view():\n    return fronted_ids(), bool(os.environ.get('RELAY_EXCLUSIVE'))\n"
        tracked=baseline(checker.extract(initial))
        tracked['mobile_used_handlers']+=['models:schema:MessagingPlatformUpdate','channels:import:late','channels:binding:_profile_scope','channels:get_messaging_platforms','channels:update_messaging_platform','channels:test_messaging_platform','channels:_messaging_platform_payload','channels:_relay_channel_view','channel_catalog:binding:_PLATFORM_OVERRIDES','channel_catalog:_write_platform_enabled','channel_registry:schema:PlatformEntry','channel_status:resolve_gateway_liveness','channel_gate:platform_gate_env','channel_relay_config:relay_explicitly_disabled','channel_relay_links:import:os','channel_relay_links:relay_channel_view']
        tracked['mobile_used_request_fields']+=['models:MessagingPlatformUpdate.'+key for key in ('enabled','env','clear_env','profile')]
        tracked['mobile_used_management_routes']+=['GET /api/messaging/platforms','PUT /api/messaging/platforms/{platform_id}','POST /api/messaging/platforms/{platform_id}/test']
        self.assertFalse(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(initial)})['mobile_review_required'])
        mutations=[('channels','from hooks','from unsafe_hooks'),('channels',"'profile_helpers'","'root_helpers'"),('channels','_profile_scope(profile)','_profile_scope(None)'),('channels','target=body.profile or profile','target=None'),('channels','validate_whole_request(body)','pass'),('channels','write_selected_fields(body,target)','replace_all_fields(body,target)'),('channels',"'hot_served':False","'hot_served':'unknown'"),('channels','posture(platform_id,profile)','posture(platform_id,None)'),('channels','entry.is_list and not entry.is_password','True'),('channels','home!=process_home()','False'),('channel_catalog',"('WEBHOOK_SECRET',)","('WEBHOOK_SECRET','PATH')"),('channel_catalog','narrow_field_write','replace_platform'),('channel_registry','dashboard_env_vars:tuple=()','dashboard_env_vars:list=[]'),('channel_status','read_scoped_runtime(profile_dir)','read_process_runtime()'),('channel_gate','profile_secret(name)','process_secret(name)'),('channel_relay_config','launch_profile_disable()','False'),('models','enabled:bool|None=None','enabled:str|None=None'),('models','env:dict[str,str]={}','env:dict[str,str]={}\n    expected_revision:str|None=None'),('models','clear_env:list[str]=[]','clear_env:str=""'),('models','profile:str|None=None','profile:str="current"'),('channel_relay_links','import os','import unsafe_os as os')]
        for module,before,after in mutations:
            with self.subTest(module=module,change=before):
                self.assertIn(before,initial[module]);changed=dict(initial);changed[module]=changed[module].replace(before,after)
                self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'])
        # A genuinely absent original module is explicit, rather than a replacement original hash.
        for key in ('channel_relay_links:import:os','channel_relay_links:relay_channel_view'):
            current_hash=tracked['upstream']['contract']['handlers'].pop(key)
            tracked.setdefault('reviewed_handler_variants',{})[key]=[{'sha256':current_hash,'commit':'b'*40,'reason':'Reviewed new metadata helper and exact standard-library binding, not account linking.'}]
        tracked['upstream']['contract']['source_hashes']['channel_relay_links']=None
        self.assertFalse(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(initial)})['mobile_review_required'])
        changed=dict(initial);changed['channel_relay_links']=changed['channel_relay_links'].replace('fronted_ids()','all_ids()')
        self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'])

    def test_imported_endpoint_alias_fields_reasoning_and_lossless_merge_changes_require_review(self):
        initial=sources()
        initial['models']+="class CustomEndpointModelDetail:\n    id:str\n    canonical_model:str|None=None\n    reasoning_effort:str|None=None\nclass CustomEndpointUpdate:\n    model_details:list[CustomEndpointModelDetail]|None=None\n"
        initial['env_routes']="def _write_custom_endpoint(cfg,body):\n    if alias!=canonical:cfg.agent.reasoning_overrides[canonical]=parse_reasoning_effort(effort)\n    preserve_model_metadata(cfg)\n"
        initial['model_reasoning']="VALID_REASONING_EFFORTS=('low','high')\ndef parse_reasoning_effort(value):\n    if value in ('none','false','disabled'):return {'enabled':False}\n    return value if value in VALID_REASONING_EFFORTS else None\n"
        initial['custom_provider_config']="def _normalize_provider_models(models):\n    return lossless_list_and_map(models)\ndef _custom_provider_entry_to_provider_config(entry,provider_key):\n    return preserve_unknown_fields(entry,provider_key)\n"
        initial['config_backend']="def refusing_locked_omissions(cfg):\n    return managed_policy(cfg)\n"
        tracked=baseline(checker.extract(initial))
        tracked['mobile_used_handlers']+=['models:schema:CustomEndpointModelDetail','models:schema:CustomEndpointUpdate','env_routes:_write_custom_endpoint','model_reasoning:VALID_REASONING_EFFORTS','model_reasoning:parse_reasoning_effort','custom_provider_config:_normalize_provider_models','custom_provider_config:_custom_provider_entry_to_provider_config','config_backend:refusing_locked_omissions']
        tracked['mobile_used_request_fields']+=['models:CustomEndpointUpdate.model_details','models:CustomEndpointModelDetail.id','models:CustomEndpointModelDetail.canonical_model','models:CustomEndpointModelDetail.reasoning_effort']
        self.assertFalse(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(initial)})['mobile_review_required'])
        changes=[('models','id:str','id:int'),('models','canonical_model:str|None=None','canonical_model:str'),('models','reasoning_effort:str|None=None','reasoning_effort:dict|None=None'),('models','model_details:list[CustomEndpointModelDetail]|None=None','model_details:list[CustomEndpointModelDetail]=[]'),('env_routes','alias!=canonical','True'),('env_routes','cfg.agent.reasoning_overrides[canonical]','cfg.agent.reasoning_overrides[alias]'),('env_routes','preserve_model_metadata(cfg)','replace_model_metadata(cfg)'),('model_reasoning',"('low','high')","('low','high','new')"),('model_reasoning',"'none','false','disabled'","'none'"),('model_reasoning','else None','else value'),('custom_provider_config','lossless_list_and_map','drop_list_metadata'),('custom_provider_config','preserve_unknown_fields','replace_unknown_fields'),('config_backend','managed_policy(cfg)','silently_drop_agent(cfg)')]
        for module,before,after in changes:
            with self.subTest(module=module,change=before):
                self.assertIn(before,initial[module]);changed=dict(initial);changed[module]=changed[module].replace(before,after)
                self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'])

    def test_endpoint_outbound_profile_quota_proxy_and_alias_contract_changes_require_review(self):
        initial=sources();initial['env_routes']="from profile_helpers import _parse_model_entries\n_TRANSPORT_ROUTES={'chat_completions':'/chat/completions'}\n_TRANSPORT_LABELS={'chat_completions':'Chat Completions'}\n@router.post('/api/providers/custom-endpoints/validate')\nasync def validate_custom_endpoint(body):\n    return await listener_probe(body)\nasync def _probe_transport_route(client,url,mode,model,headers):\n    return await client.post(url,json={'max_tokens':1})\ndef _endpoint_probe_client(url,timeout):\n    return Client(trust_env=not is_local_endpoint(url))\n"
        initial['probe_metadata']="_LOCAL_HOSTS={'localhost'}\n_CONTAINER_LOCAL_SUFFIXES=('.internal',)\n_TAILSCALE_CGNAT='100.64.0.0/10'\ndef is_local_endpoint(url):\n    return local_or_tailscale(url)\n"
        initial['probe_route_resolver']="_HOST_MANDATED_API_MODES={'api.anthropic.com':'anthropic_messages'}\ndef _detect_api_mode_for_url(url):\n    return exact_host(url)\n"
        initial['profile_helpers']="_MODEL_ENTRY_METADATA=('canonical_model','reasoning_effort')\ndef _parse_model_entries(response):\n    return aliases(response)\n"
        tracked=baseline(checker.extract(initial));tracked['mobile_used_handlers']+=['env_routes:import:_parse_model_entries','env_routes:_TRANSPORT_ROUTES','env_routes:_TRANSPORT_LABELS','env_routes:validate_custom_endpoint','env_routes:_probe_transport_route','env_routes:_endpoint_probe_client','probe_metadata:_LOCAL_HOSTS','probe_metadata:_CONTAINER_LOCAL_SUFFIXES','probe_metadata:_TAILSCALE_CGNAT','probe_metadata:is_local_endpoint','probe_route_resolver:_HOST_MANDATED_API_MODES','probe_route_resolver:_detect_api_mode_for_url','profile_helpers:_MODEL_ENTRY_METADATA','profile_helpers:_parse_model_entries'];tracked['mobile_used_management_routes']+=['POST /api/providers/custom-endpoints/validate']
        for module,before,after in [('env_routes','validate_custom_endpoint(body)','validate_custom_endpoint(body,profile=None)'),('env_routes','listener_probe(body)','profile_probe(body)'),('env_routes',"'max_tokens':1","'max_tokens':1000"),('env_routes','trust_env=not is_local_endpoint(url)','trust_env=True'),('env_routes',"'/chat/completions'","'/other'"),('env_routes','from profile_helpers','from unsafe_helpers'),('probe_metadata','local_or_tailscale','loopback_only'),('probe_metadata',"{'localhost'}","{'other'}"),('probe_metadata',"'100.64.0.0/10'","'127.0.0.0/8'"),('probe_route_resolver','exact_host','substring'),('probe_route_resolver',"'anthropic_messages'","'chat_completions'"),('profile_helpers',"'canonical_model',",''),('profile_helpers','aliases(response)','ids_only(response)')]:
            changed=dict(initial);changed[module]=changed[module].replace(before,after);self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'],(module,before))

    def test_custom_endpoint_profile_key_choice_unknown_fields_and_main_detach_require_review(self):
        initial=sources();initial['env_routes']="from config_helpers import find_provider_entry,custom_endpoint_key_env\n_ROUTE_SECTIONS=('model','providers','custom_providers')\n_DESKTOP_API_MODES={'chat_completions','codex_responses','anthropic_messages'}\nsave_env_value=late('save_env_value')\ndef _write_custom_endpoint(cfg,body):\n    entry=dict(existing)\n    if body.api_mode is not None:entry.pop('transport',None)\n    if body.api_key is not None:write_key(custom_endpoint_key_env(body.id),body.api_key)\n    return entry\n@router.post('/api/providers/custom-endpoints')\ndef upsert_custom_endpoint(body,profile=None):\n    with _config_profile_scope(profile),_CONFIG_MUTATION_LOCK:return _write_custom_endpoint(cfg,body)\n@router.delete('/api/providers/custom-endpoints/{endpoint_id}')\ndef delete_custom_endpoint(endpoint_id,profile=None):\n    detach_main(endpoint_id)\n    remove_generated_key(endpoint_id)\n    return {'ok':True}\n"
        initial['models']+="class CustomEndpointUpdate:\n    id:str=''\n    api_key:str|None=None\n    api_mode:str|None=None\n"
        tracked=baseline(checker.extract(initial));tracked['mobile_used_handlers']+=['env_routes:import:find_provider_entry','env_routes:_ROUTE_SECTIONS','env_routes:_DESKTOP_API_MODES','env_routes:save_env_value','env_routes:_write_custom_endpoint','env_routes:upsert_custom_endpoint','env_routes:delete_custom_endpoint','models:schema:CustomEndpointUpdate'];tracked['mobile_used_request_fields']+=['models:CustomEndpointUpdate.api_key','models:CustomEndpointUpdate.api_mode'];tracked['mobile_used_management_routes']+=['POST /api/providers/custom-endpoints','DELETE /api/providers/custom-endpoints/{endpoint_id}']
        for module,before,after in [('env_routes','from config_helpers','from unsafe_helpers'),('env_routes',"('model','providers','custom_providers')","('providers',)"),('env_routes',"'codex_responses',",""),('env_routes',"late('save_env_value')","late('other_write')"),('env_routes','entry=dict(existing)','entry={}'),('env_routes','body.api_key is not None','bool(body.api_key)'),('env_routes',"entry.pop('transport',None)",'pass'),('env_routes','_config_profile_scope(profile)','_config_profile_scope(None)'),('env_routes','detach_main(endpoint_id)','pass'),('env_routes','remove_generated_key(endpoint_id)','pass'),('models','api_key:str|None=None',"api_key:str=''"),('models','api_mode:str|None=None','api_mode:str|None=None\n    revision:int=0')]:
            changed=dict(initial);changed[module]=changed[module].replace(before,after);self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'],(module,before))

    def test_custom_provider_identity_legacy_conversion_aliases_and_reexports_require_review(self):
        initial=sources();initial['env_config']="from config_providers import coerce_provider_id,find_provider_entry,get_compatible_custom_providers\ndef custom_endpoint_key_env(identity):\n    return slot(identity)\n"
        initial['custom_provider_config']="_CAMEL_ALIASES={'apiKeyEnv':'key_env'}\n_KNOWN_PROVIDER_KEYS={'extra_headers','key_env'}\ndef coerce_provider_id(value):\n    return str(value).strip()\ndef find_provider_entry(providers,key):\n    return exact_hit_then_trimmed_scan(providers,key)\ndef _custom_provider_entry_to_provider_config(entry,provider_key):\n    return preserve_unknown_fields(entry,provider_key)\ndef get_compatible_custom_providers(config):\n    return merge_legacy_and_named(config)\n"
        tracked=baseline(checker.extract(initial));tracked['mobile_used_handlers']+=['env_config:import:coerce_provider_id','env_config:custom_endpoint_key_env','custom_provider_config:_CAMEL_ALIASES','custom_provider_config:_KNOWN_PROVIDER_KEYS','custom_provider_config:coerce_provider_id','custom_provider_config:find_provider_entry','custom_provider_config:_custom_provider_entry_to_provider_config','custom_provider_config:get_compatible_custom_providers']
        for module,before,after in [('env_config','from config_providers','from other_providers'),('env_config','slot(identity)','slot(identity.lower())'),('custom_provider_config',"'apiKeyEnv':'key_env'","'apiKeyEnv':'other_key'"),('custom_provider_config',"'extra_headers',",""),('custom_provider_config','str(value).strip()','str(value).strip().lower()'),('custom_provider_config','exact_hit_then_trimmed_scan','slug_first'),('custom_provider_config','preserve_unknown_fields','drop_unknown_fields'),('custom_provider_config','merge_legacy_and_named','named_only')]:
            changed=dict(initial);changed[module]=changed[module].replace(before,after);self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'],(module,before))

    def test_fallback_transport_alias_and_runtime_credential_binding_require_review(self):
        initial=sources();initial['fallback_wire']="def _fallback_api_mode_hint(entry,provider,url):\n    return parse(entry.get('api_mode') or entry.get('transport'))\n";initial['fallback_binding']="def bind_route_entry(agent,entry,provider,model):\n    return resolve(provider,model,explicit_base_url=entry.get('base_url'),explicit_api_key=resolve_entry_api_key(entry))\n"
        tracked=baseline(checker.extract(initial));tracked['mobile_used_handlers']+=['fallback_wire:_fallback_api_mode_hint','fallback_binding:bind_route_entry']
        for module,before,after in [('fallback_wire',"entry.get('api_mode') or entry.get('transport')","entry.get('api_mode')"),('fallback_binding',"entry.get('base_url')","None"),('fallback_binding','resolve_entry_api_key(entry)','global_key()')]:
            changed=dict(initial);changed[module]=changed[module].replace(before,after);self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'],(module,before))

    def test_fallback_partial_config_merge_scopes_and_secret_preservation_require_review(self):
        initial=sources();initial['env_routes']="from config_helpers import _deep_merge,read_raw_config\n_profile_scope=late('_profile_scope')\n_CONFIG_MUTATION_LOCK=LateState('_CONFIG_MUTATION_LOCK')\n@config_router.get('/api/config')\ndef get_config(profile=None,include_defaults=True):\n    return scoped(profile,read_raw_config())\n@router.put('/api/config')\ndef update_config(body,profile=None):\n    with _profile_scope(body.profile or profile):\n        return save_config(_deep_merge(existing,body.config))\n"
        initial['models']+="class ConfigUpdate:\n    config:dict\n    profile:str|None=None\n"
        initial['fallback_config']="def get_fallback_chain(config):\n    return merge(config['fallback_providers'],config['fallback_model'])\ndef resolve_entry_api_key(entry):\n    return entry.get('api_key') or scoped_secret(entry.get('key_env'))\n"
        initial['env_config']="def _deep_merge(base,override):\n    return replace_arrays_and_merge_dicts(base,override)\ndef _preserve_env_ref_templates(new,old,expanded):\n    return preserve_templates(new,old,expanded)\n"
        initial['config_worker_save']="_SAME_AS_BASE=object()\ndef rebase_for_save(config,raw,merge_existing):\n    return preserve_live_edits(config,raw)\n"
        initial['provider_identity']="ALIASES={'zen':'opencode'}\ndef normalize_provider(name):\n    return ALIASES.get(name,name)\n"
        old=checker.extract(initial);tracked=baseline(old);tracked['mobile_used_handlers']+=['env_routes:import:_deep_merge','env_routes:_profile_scope','env_routes:_CONFIG_MUTATION_LOCK','env_routes:get_config','env_routes:update_config','models:schema:ConfigUpdate','fallback_config:get_fallback_chain','fallback_config:resolve_entry_api_key','env_config:_deep_merge','env_config:_preserve_env_ref_templates','config_worker_save:rebase_for_save','config_worker_save:_SAME_AS_BASE','provider_identity:ALIASES'];tracked['mobile_used_request_fields']+=['models:ConfigUpdate.config','models:ConfigUpdate.profile'];tracked['mobile_used_management_routes']+=['GET /api/config','PUT /api/config']
        for module,before,after in [('env_routes','from config_helpers','from unsafe_helpers'),('env_routes',"late('_profile_scope')","late('_other_scope')"),('env_routes',"LateState('_CONFIG_MUTATION_LOCK')","LateState('_OTHER_LOCK')"),('env_routes','include_defaults=True','include_defaults=False'),('env_routes','body.profile or profile','None'),('env_routes',"'/api/config'","'/api/config/all'"),('models','config:dict','config:list'),('models','profile:str|None=None','profile:str|None=None\n    revision:int=0'),('fallback_config',"config['fallback_providers'],config['fallback_model']","config['fallback_model'],config['fallback_providers']"),('fallback_config','scoped_secret','global_secret'),('env_config','replace_arrays_and_merge_dicts','merge_arrays'),('env_config','preserve_templates','expand_templates'),('config_worker_save','preserve_live_edits','replace_live'),('config_worker_save','object()','None'),('provider_identity',"'opencode'","'different'")]:
            changed=dict(initial);changed[module]=changed[module].replace(before,after);self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'],(module,before))

    def test_credential_pool_order_scope_cleanup_merge_and_request_changes_require_review(self):
        initial=sources();initial['pool_routes']="from scope_helpers import config_scoped_to_thread,destructive_profile\n@router.delete('/api/credentials/pool/{provider}/{index}')\ndef remove_credential_pool_entry(provider,index,profile=None):\n    removed=pool.remove_index(index)\n    cleanup(removed.source)\n    return config_scoped_to_thread(destructive_profile(profile),result)\n"
        initial['models']+="class CredentialPoolAdd:\n    provider:str\n    api_key:str\n    label:str|None=None\n"
        initial['credential_pool']="CUSTOM_POOL_PREFIX='custom:'\nclass PooledCredential:\n    source:str\ndef persist_pool_entries(provider,payloads,removed_ids=None):\n    return write_pool(provider,payloads,removed_ids=removed_ids)\n"
        initial['pool_admin']="class CredentialPoolAdminMixin:\n    def remove_index(self,index):\n        return self.entries.pop(index-1)\n"
        initial['pool_sources']="_REGISTRY=('env','device_code')\ndef find_removal_step(provider,source):\n    return profile_hook(provider,source) or registry_match(provider,source)\n"
        initial['provider_auth']="def read_credential_pool(provider):\n    return profile_pool(provider) or root_pool(provider)\n"
        initial['web_common']="_config_profile_scope=late('_config_profile_scope','profiles')\ndef config_scoped_to_thread(profile,fn):\n    return _config_profile_scope(profile,fn)\n"
        old=checker.extract(initial);tracked=baseline(old);tracked['mobile_used_handlers']+=['web_common:_config_profile_scope','web_common:config_scoped_to_thread','pool_routes:import:config_scoped_to_thread','pool_routes:remove_credential_pool_entry','models:schema:CredentialPoolAdd','credential_pool:CUSTOM_POOL_PREFIX','credential_pool:persist_pool_entries','pool_admin:CredentialPoolAdminMixin.remove_index','pool_sources:_REGISTRY','pool_sources:find_removal_step','provider_auth:read_credential_pool'];tracked['mobile_used_request_fields']+=['models:CredentialPoolAdd.api_key','credential_pool:PooledCredential.source'];tracked['mobile_used_management_routes']+=['DELETE /api/credentials/pool/{provider}/{index}']
        for module,before,after in [('web_common',"late('_config_profile_scope','profiles')","late('_config_profile_scope','other')"),('web_common','_config_profile_scope(profile,fn)','unscoped(fn)'),('pool_routes','from scope_helpers','from unsafe_helpers'),('pool_routes','/api/credentials/pool/{provider}/{index}','/api/pool/{provider}/{index}'),('pool_routes','destructive_profile(profile)','None'),('pool_routes','cleanup(removed.source)','pass'),('models','api_key:str','api_key:dict'),('models','label:str|None=None','label:str|None=None\n    revision:str|None=None'),('credential_pool',"CUSTOM_POOL_PREFIX='custom:'","CUSTOM_POOL_PREFIX='endpoint:'"),('credential_pool','source:str','source:dict'),('credential_pool','removed_ids=removed_ids','removed_ids=None'),('pool_admin','pop(index-1)','pop(index)'),('pool_sources',"('env','device_code')","('env',)"),('pool_sources','profile_hook(provider,source) or ','') ,('provider_auth','profile_pool(provider) or root_pool(provider)','root_pool(provider)')]:
            changed=dict(initial);changed[module]=changed[module].replace(before,after);self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'],(module,before))

    def test_dashboard_native_bearer_session_gate_and_public_route_changes_require_review(self):
        initial=sources();initial['auth_middleware']="_GATE_PUBLIC_PREFIXES=('/auth/native/token',)\n_SAFE_METHODS=('GET','HEAD')\ndef _verify_access_token(request,access_token):\n    return scan_session_providers(access_token)\nasync def gated_auth_middleware(request,next):\n    bearer_session=_verify_access_token(request,request.bearer)\n    if bearer_session is not None:request.state.session=bearer_session\n    else:raise Unauthorized()\n    return await next(request)\n"
        old=checker.extract(initial);tracked=baseline(old);tracked['mobile_used_handlers']+=['auth_middleware:_GATE_PUBLIC_PREFIXES','auth_middleware:_SAFE_METHODS','auth_middleware:_verify_access_token','auth_middleware:gated_auth_middleware']
        for before,after in [("('/auth/native/token',)","('/api/',)"),("('GET','HEAD')","('GET','HEAD','POST')"),('scan_session_providers(access_token)','allow_any(access_token)'),('request.state.session=bearer_session','request.state.session=None'),('else:raise Unauthorized()','else:pass')]:
            changed=dict(initial);changed['auth_middleware']=changed['auth_middleware'].replace(before,after);self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'],before)

    def test_native_route_local_auth_guard_changes_require_review(self):
        initial=sources();initial['web_bootstrap']+="\ndef _has_valid_session_token(request):\n    return hmac.compare_digest(request.headers.get('authorization',''),f'Bearer {SESSION_TOKEN}')\ndef _require_token(request):\n    if getattr(request.state,'native_http_principal',None):return\n    if request.app.state.auth_required:\n        ok=request.state.session is not None\n    else:ok=_has_valid_session_token(request)\n    if not ok:raise Unauthorized()\n"
        old=checker.extract(initial);tracked=baseline(old);tracked['mobile_used_handlers']+=['web_bootstrap:_require_token','web_bootstrap:_has_valid_session_token']
        for before,after in [("getattr(request.state,'native_http_principal',None)",'True'),('request.state.session is not None','True'),('hmac.compare_digest','unsafe_prefix_compare'),('if not ok:raise Unauthorized()','pass')]:
            changed=dict(initial);changed['web_bootstrap']=changed['web_bootstrap'].replace(before,after);self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'],before)

    def test_provider_oauth_profile_provider_cancel_save_and_catalog_changes_require_review(self):
        initial=sources()
        initial['oauth_routes']="from hermes_cli.web_routers._common import scoped_to_thread\n_profile_scope=late('_profile_scope','profiles')\n_OAUTH_SESSION_TTL_SECONDS=900\n@router.post('/api/providers/oauth/{provider_id}/start')\ndef start_oauth_login(provider_id,profile=None):\n    _require_token()\n    return _start_device_code_flow(provider_id,profile)\n@router.get('/api/providers/oauth/{provider_id}/poll/{session_id}')\ndef poll_oauth_session(provider_id,session_id,profile=None):\n    assert session.provider==provider_id and session.profile==profile\n    return session.status\n@router.delete('/api/providers/oauth/sessions/{session_id}')\ndef cancel_oauth_session(session_id,profile=None):\n    assert session.profile==profile\n    session.cancelled=True\n"
        initial['oauth_helpers']="_OAUTH_PROVIDER_CATALOG=('device_code','external')\ndef _oauth_profile_name(profile):\n    return profile\ndef _profile_device_poller(session):\n    with lock:\n        if not session.cancelled:save_device_login(session.profile)\n"
        initial['provider_auth']="def clear_provider_auth(provider):\n    for section in ('providers','credential_pool'):clear(section,provider)\n"
        old=checker.extract(initial);tracked=baseline(old)
        tracked['mobile_used_handlers']+=['oauth_routes:import:hermes_cli.web_routers._common','oauth_routes:_profile_scope','oauth_routes:_OAUTH_SESSION_TTL_SECONDS','oauth_routes:start_oauth_login','oauth_routes:poll_oauth_session','oauth_routes:cancel_oauth_session','oauth_helpers:_OAUTH_PROVIDER_CATALOG','oauth_helpers:_oauth_profile_name','oauth_helpers:_profile_device_poller','provider_auth:clear_provider_auth']
        tracked['mobile_used_management_routes']+=['POST /api/providers/oauth/{provider_id}/start','GET /api/providers/oauth/{provider_id}/poll/{session_id}','DELETE /api/providers/oauth/sessions/{session_id}']
        for module,before,after in [('oauth_routes','scoped_to_thread','unscoped_thread'),('oauth_routes',"late('_profile_scope','profiles')","late('_profile_scope','other')"),('oauth_routes','_OAUTH_SESSION_TTL_SECONDS=900','_OAUTH_SESSION_TTL_SECONDS=3600'),('oauth_routes','_require_token()','pass'),('oauth_routes','session.provider==provider_id and session.profile==profile','True'),('oauth_routes','assert session.profile==profile','pass'),('oauth_routes','/api/providers/oauth/sessions/{session_id}','/api/oauth/sessions/{session_id}'),('oauth_helpers',"('device_code','external')","('device_code',)"),('oauth_helpers','return profile','return None'),('oauth_helpers','if not session.cancelled','if True'),('provider_auth',"('providers','credential_pool')","('providers',)")]:
            changed=dict(initial);changed[module]=changed[module].replace(before,after);self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'],(module,before))

    def test_live_profile_and_config_lock_bindings_cannot_redirect_silently(self):
        initial=sources();initial['web_common']="from hermes_cli.web_deps import LateState,late\n_profile_scope=late('_profile_scope','hermes_cli.web_server_profiles')\n_CONFIG_MUTATION_LOCK=LateState('_CONFIG_MUTATION_LOCK')\ndef config_write_scope(profile):\n    with _profile_scope(profile),_CONFIG_MUTATION_LOCK:yield\n"
        initial['profile_helpers']="def _profile_scope(profile):\n    with _config_profile_scope(profile):yield\n"
        initial['web_deps']="WEB_SERVER='hermes_cli.web_server'\ndef late(name,module=WEB_SERVER):\n    return getattr(server(module),name)\nclass LateState:\n    def _target(self):return getattr(server(),self._name)\n    def __enter__(self):return self._target().__enter__()\n"
        initial['web_bootstrap']+="\n_CONFIG_MUTATION_LOCK=threading.RLock()\n"
        old=checker.extract(initial);tracked=baseline(old);tracked['mobile_used_handlers']+=['web_common:import:late','web_common:import:LateState','web_common:_profile_scope','web_common:_CONFIG_MUTATION_LOCK','web_common:config_write_scope','profile_helpers:_profile_scope','web_deps:WEB_SERVER','web_deps:late','web_deps:LateState._target','web_deps:LateState.__enter__','web_bootstrap:_CONFIG_MUTATION_LOCK']
        for module,before,after in [('web_common','hermes_cli.web_deps','other.deps'),('web_common',"'hermes_cli.web_server_profiles'","'other.profiles'"),('web_common',"LateState('_CONFIG_MUTATION_LOCK')","LateState('_OTHER_LOCK')"),('web_common','_profile_scope(profile),','_profile_scope(None),'),('profile_helpers','_config_profile_scope(profile)','_config_profile_scope(None)'),('web_deps',"'hermes_cli.web_server'","'other.server'"),('web_deps','getattr(server(module),name)','getattr(server(module),wrong_name)'),('web_deps','self._name',"'wrong_lock'"),('web_deps','self._target().__enter__()','None'),('web_bootstrap','threading.RLock()','threading.Lock()')]:
            changed=dict(initial);changed[module]=changed[module].replace(before,after);self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'],module)

    def test_moa_full_map_profile_privacy_validation_merge_and_schema_changes_require_review(self):
        initial=sources()
        initial['model_routes']="_MOA_PRESET_FIELDS=('fanout','enabled')\n@router.get('/api/model/moa')\ndef get_moa_models(profile=None):\n    with _profile_scope(profile):return normalize_moa_config(load_config())\n@router.put('/api/model/moa')\ndef set_moa_models(body,profile=None):\n    with config_write_scope(body.profile or profile):\n        validate_moa_payload(body.presets)\n        raw['privacy_filter']=existing['privacy_filter']\n        updated=_merge_partial_save(raw,body.presets)\n        return {k:v for k,v in updated.items() if k in body.presets}\n"
        initial['models']+="class MoaModelSlot:\n    provider:str\n    model:str\n    reasoning_effort:str|None=None\n    enabled:bool=True\nclass _MoaReferenceControls:\n    reference_timeout:float|None=None\nclass MoaPresetPayload(_MoaReferenceControls):\n    fanout:str|None=None\nclass MoaConfigPayload(_MoaReferenceControls):\n    presets:dict={}\n    profile:str|None=None\n"
        initial['moa_config']="DEFAULT_MOA_REFERENCE_TIMEOUT=None\ndef _slot_problem(slot):\n    return slot['provider']=='moa'\ndef _coerce_fanout(value):\n    return 'user_turn' if value=='every_n:0' else value\ndef coerce_privacy_filter(value):\n    return value if value in ('display','full') else ''\ndef validate_moa_payload(raw):\n    return reject_incomplete(raw)\n"
        initial['env_config']="def _merge_partial_save(raw,override):\n    return _deep_merge(raw,override)\ndef _deep_merge(raw,override):\n    return preserve_omitted_keys(raw,override)\n"
        old=checker.extract(initial);tracked=baseline(old);tracked['mobile_used_management_routes']+=['GET /api/model/moa','PUT /api/model/moa'];tracked['mobile_used_handlers']+=['model_routes:_MOA_PRESET_FIELDS','model_routes:get_moa_models','model_routes:set_moa_models','models:schema:MoaModelSlot','models:schema:_MoaReferenceControls','models:schema:MoaPresetPayload','models:schema:MoaConfigPayload','moa_config:DEFAULT_MOA_REFERENCE_TIMEOUT','moa_config:_slot_problem','moa_config:_coerce_fanout','moa_config:coerce_privacy_filter','moa_config:validate_moa_payload','env_config:_merge_partial_save','env_config:_deep_merge'];tracked['mobile_used_request_fields']+=['models:MoaModelSlot.reasoning_effort','models:_MoaReferenceControls.reference_timeout','models:MoaConfigPayload.profile']
        for module,before,after in [('model_routes','/api/model/moa','/api/model/new'),('model_routes',"('fanout','enabled')","('fanout',)"),('model_routes','body.profile or profile','None'),('model_routes','validate_moa_payload(body.presets)','normalize_moa_config(body.presets)'),('model_routes',"raw['privacy_filter']=existing['privacy_filter']","raw['privacy_filter']=''"),('model_routes','if k in body.presets','if True'),('models','reasoning_effort:str|None=None','reasoning_effort:dict|None=None'),('models','reference_timeout:float|None=None','reference_timeout:float=30'),('models','presets:dict={}','presets:dict={}\n    expected_revision:str|None=None'),('moa_config','DEFAULT_MOA_REFERENCE_TIMEOUT=None','DEFAULT_MOA_REFERENCE_TIMEOUT=30'),('moa_config',"slot['provider']=='moa'",'False'),('moa_config',"'user_turn'","'per_iteration'"),('moa_config',"('display','full')","('full',)"),('moa_config','reject_incomplete(raw)','[]'),('env_config','preserve_omitted_keys(raw,override)','replace_all_keys(override)')]:
            changed=dict(initial);changed[module]=changed[module].replace(before,after);self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'],module)

    def test_auxiliary_reset_reasoning_registered_slots_defaults_and_runtime_routes_require_review(self):
        initial=sources()
        initial['model_config']="def _apply_aux_assignment_sync(cfg,provider,model,task,reasoning_effort=UNSET):\n    if task=='__reset__':\n        clear_model_endpoint_credentials(cfg)\n    if reasoning_effort is None:clear_reasoning(cfg)\n    return save_config(cfg)\ndef _aux_task_slots():\n    return BUILTINS+plugins()\n"
        initial['model_reasoning']="VALID_REASONING_EFFORTS=('low','high')\ndef parse_reasoning_effort(value):\n    return value if value in VALID_REASONING_EFFORTS else None\n"
        initial['model_defaults']="from hermes_cli.config_defaults_auxiliary import _aux\nDEFAULT_CONFIG={'auxiliary':{'voice_chat':{'reasoning_effort':'none'}},'display':{}}\n"
        initial['model_aux_runtime']="from agent.auxiliary_task_config import _get_auxiliary_task_config\ndef _resolve_task_provider_model(task):\n    return task_config(task)\n"
        initial['model_custom_runtime']="def _get_named_custom_provider(name):\n    return scoped_config(name)\n"
        initial['env_config']="def clear_model_endpoint_credentials(cfg):\n    cfg.pop('key_env',None)\n"
        old=checker.extract(initial);tracked=baseline(old);tracked['mobile_used_handlers']+=['model_config:_apply_aux_assignment_sync','model_config:_aux_task_slots','model_reasoning:VALID_REASONING_EFFORTS','model_reasoning:parse_reasoning_effort','model_defaults:auxiliary','model_defaults:import:_aux','model_aux_runtime:import:_get_auxiliary_task_config','model_aux_runtime:_resolve_task_provider_model','model_custom_runtime:_get_named_custom_provider','env_config:clear_model_endpoint_credentials']
        for module,before,after in [('model_config',"'__reset__'","'other'"),('model_config','reasoning_effort is None','reasoning_effort is UNSET'),('model_config','BUILTINS+plugins()','BUILTINS'),('model_reasoning',"('low','high')","('low',)"),('model_reasoning','else None','else value'),('model_defaults',"'reasoning_effort':'none'","'reasoning_effort':'high'"),('model_defaults','hermes_cli.config_defaults_auxiliary','other.defaults'),('model_aux_runtime','agent.auxiliary_task_config','other.task_config'),('model_aux_runtime','task_config(task)','main_config()'),('model_custom_runtime','scoped_config(name)','default_config(name)'),('env_config',"'key_env'","'other'")]:
            changed=dict(initial);changed[module]=changed[module].replace(before,after);self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'],module)
        unrelated=dict(initial);unrelated['model_defaults']=unrelated['model_defaults'].replace("'display':{}","'display':{'theme':'changed'}");self.assertFalse(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(unrelated)})['mobile_review_required'])

    def test_main_model_scope_known_warning_policy_normalized_ack_and_revision_fields_require_review(self):
        initial=sources();initial['model_routes']="@router.post('/api/model/set')\nasync def set_model_assignment(body,profile=None):\n    if not body.confirm_expensive_model:\n        return warning_before_assignment(body)\n    with _profile_scope(body.profile or profile):\n        return apply_main(body)\n"
        initial['models']+="class ModelAssignment:\n    scope:str\n    provider:str\n    model:str\n    confirm_expensive_model:bool=False\n    profile:str|None=None\n"
        initial['model_config']="def _apply_main_assignment_sync(cfg,provider,model):\n    return {'ok':True,'provider':result.target_provider,'model':result.new_model}\n"
        initial['model_inventory']="def _apply_pricing(rows):\n    rows[0]['unavailable_models']=restricted_models()\n"
        initial['model_guards']="_GUARDS=(_cost_guard,_data_policy_guard)\ndef combined_selection_warning(model):\n    return all_guards(model)\n"
        initial['model_cost']="INPUT_COST_WARNING_THRESHOLD=Decimal(20)\n"
        initial['model_data']="_RULES=((contributor,'Data terms'),)\n"
        initial['model_switch']="def model_selection_config_updates(result,cfg):\n    return clear_stale_credentials(result,cfg)\n"
        initial['model_route_identity']="def should_clear_context_pin(before,after):\n    return before!=after\n"
        old=checker.extract(initial);tracked=baseline(old);tracked['mobile_used_management_routes'].append('POST /api/model/set');tracked['mobile_used_handlers']+=['model_routes:set_model_assignment','models:schema:ModelAssignment','model_config:_apply_main_assignment_sync','model_inventory:_apply_pricing','model_guards:_GUARDS','model_guards:combined_selection_warning','model_cost:INPUT_COST_WARNING_THRESHOLD','model_data:_RULES','model_switch:model_selection_config_updates','model_route_identity:should_clear_context_pin'];tracked['mobile_used_request_fields'].append('models:ModelAssignment.confirm_expensive_model')
        for module,before,after in [('model_routes','/api/model/set','/api/model/other'),('model_routes','body.profile or profile','None'),('model_routes','return warning_before_assignment(body)','apply_main(body); return warning_before_assignment(body)'),('models','confirm_expensive_model:bool=False','confirm_expensive_model:str=\"\"'),('models','scope:str','scope:str\n    expected_revision:str|None=None'),('model_config','result.target_provider','provider'),('model_inventory',"restricted_models()",'[]'),('model_guards','(_cost_guard,_data_policy_guard)','(_cost_guard,)'),('model_guards','all_guards(model)','None'),('model_cost','Decimal(20)','Decimal(200)'),('model_data','Data terms','Different terms'),('model_switch','clear_stale_credentials(result,cfg)','{}'),('model_route_identity','before!=after','False')]:
            changed=dict(initial);changed[module]=changed[module].replace(before,after);self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'],module)

    def test_model_catalog_and_auxiliary_scope_refresh_inheritance_and_slot_changes_require_review(self):
        initial=sources();initial['model_routes']="@router.get('/api/model/options')\nasync def get_model_options(profile=None,refresh=False):\n    with _config_profile_scope(profile):\n        return build_model_options_payload(refresh=refresh)\n@router.get('/api/model/auxiliary')\ndef get_auxiliary_models(profile=None):\n    with _profile_scope(profile):\n        return inherited_tasks(profile)\n"
        initial['model_config']="_AUX_TASK_SLOTS:tuple=('vision',)\ndef _plugin_aux_tasks():\n    return current_profile_plugins()\n"
        initial['model_inventory']="def build_model_options_payload(ctx,refresh=False):\n    return build_models_payload(ctx,probe_custom_providers=refresh)\n"
        initial['model_catalog']="def _save_discovered_models_to_config(url,models):\n    save_config(models)\n"
        initial['model_auxiliary']="def _get_auxiliary_task_config(task):\n    return inherited(task)\n"
        old=checker.extract(initial);tracked=baseline(old);tracked['mobile_used_management_routes']+=['GET /api/model/options','GET /api/model/auxiliary'];tracked['mobile_used_handlers']+=['model_routes:get_model_options','model_routes:get_auxiliary_models','model_config:_AUX_TASK_SLOTS','model_config:_plugin_aux_tasks','model_inventory:build_model_options_payload','model_catalog:_save_discovered_models_to_config','model_auxiliary:_get_auxiliary_task_config']
        for module,before,after in [('model_routes','/api/model/options','/api/other'),('model_routes','_config_profile_scope(profile)','_config_profile_scope(None)'),('model_routes','inherited_tasks(profile)','inherited_tasks(None)'),('model_config',"('vision',)","('vision','new')"),('model_config','current_profile_plugins()','global_plugins()'),('model_inventory','probe_custom_providers=refresh','probe_custom_providers=True'),('model_catalog','save_config(models)','save_global_config(models)'),('model_auxiliary','inherited(task)','default_profile(task)')]:
            changed=dict(initial);changed[module]=changed[module].replace(before,after);self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'],module)

    def test_empty_environment_mirror_pool_and_retained_key_semantics_require_review(self):
        initial=sources();initial['env_lifecycle']="def save_provider_env_credential(key,value):\n    if value and old_value:\n        scrub_mirrors(old_value,value)\n    unsuppress(key)\n    return {'ok':True,'key':key}\n"
        initial['env_routes']="def _get_env_vars_sync(profile):\n    return {key:{'is_set':bool(value)} for key,value in env_on_disk.items()}\n"
        initial['env_config']="def _quote_env_value(value):\n    if value=='':\n        return value\n    return quote(value)\n"
        old=checker.extract(initial);tracked=baseline(old);tracked['mobile_used_handlers']+=['env_lifecycle:save_provider_env_credential','env_routes:_get_env_vars_sync','env_config:_quote_env_value']
        for module,before,after in [('env_lifecycle','if value and old_value:','if old_value:'),('env_lifecycle','unsuppress(key)','suppress(key)'),('env_routes','in env_on_disk.items()}','in env_on_disk.items() if value}'),('env_config',"return value\n    return quote(value)","return None\n    return quote(value)")]:
            changed=dict(initial);changed[module]=changed[module].replace(before,after);self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'],module)

    def test_environment_routes_scope_mirrors_masks_policy_and_added_revision_guards_require_review(self):
        initial=sources();initial['env_routes']="@router.delete('/api/env')\nasync def remove_env_var(body,profile=None):\n    return scoped_to_thread(body.profile or profile,remove)\n"
        initial['env_lifecycle']="def save_provider_env_credential(key,value):\n    return scrub_mirrors(key,value)\n"
        initial['env_config']="_ENV_VAR_NAME_DENY_PREFIXES:tuple=('LD_',)\ndef validate_env_var_name_for_write(key):\n    return key not in denied\n"
        initial['web_common']="_LEGACY_MASK_RE=regex('mask')\ndef is_redacted_credential_preview(value):\n    return value.startswith('redacted')\n"
        initial['models']+="class EnvVarUpdate:\n    key:str\n    value:str\n    provider_setup:bool=False\n"
        old=checker.extract(initial);tracked=baseline(old);tracked['mobile_used_management_routes'].append('DELETE /api/env');tracked['mobile_used_handlers']+=['env_routes:remove_env_var','env_lifecycle:save_provider_env_credential','env_config:_ENV_VAR_NAME_DENY_PREFIXES','env_config:validate_env_var_name_for_write','web_common:_LEGACY_MASK_RE','web_common:is_redacted_credential_preview','models:schema:EnvVarUpdate'];tracked['mobile_used_request_fields'].append('models:EnvVarUpdate.provider_setup')
        for module,before,after in [('env_routes',"/api/env","/api/other"),('env_routes','body.profile or profile','None'),('env_lifecycle','scrub_mirrors(key,value)','None'),('env_config',"('LD_',)",'()'),('env_config','key not in denied','True'),('web_common',"regex('mask')","regex('other')"),('web_common',"value.startswith('redacted')",'False'),('models','provider_setup:bool=False','provider_setup:str=""'),('models','value:str','value:str\n    expected_revision:str|None=None')]:
            changed=dict(initial);changed[module]=changed[module].replace(before,after);self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'],module)

    def test_plugin_settings_types_namespaced_writer_and_restart_effects_are_review_gated(self):
        initial=sources();initial['plugin_settings']="_FIELD_TYPES:dict={'secret':'secret'}\n_VALUE_TYPES:dict={'number':(int,float)}\ndef plugin_settings_fields(current,key):\n    return current.get(key)\n"
        initial['plugin_state']="_PLUGIN_SETTING_RESERVED_ROOTS=frozenset({'security'})\ndef save_plugin_setting(plugin_id,segments,value):\n    return plugin_id,segments,value\n"
        initial['gateway_rpc']="def _plugins_settings(rid,params):\n    return params['key']\n";initial['mcp_discovery']="def restart_mcp_servers_with_new_inputs(names):\n    return names\n"
        initial['gateway_plugin_models']="class PluginSettingFieldType:\n    secret='secret'\nclass PluginSettingField:\n    value:object=None\n"
        old=checker.extract(initial);tracked=baseline(old);tracked['mobile_used_handlers']+=['plugin_settings:_FIELD_TYPES','plugin_settings:_VALUE_TYPES','plugin_settings:plugin_settings_fields','plugin_state:_PLUGIN_SETTING_RESERVED_ROOTS','plugin_state:save_plugin_setting','gateway_rpc:_plugins_settings','mcp_discovery:restart_mcp_servers_with_new_inputs','gateway_plugin_models:enum:PluginSettingFieldType'];tracked['mobile_used_request_fields'].append('gateway_plugin_models:PluginSettingField.value')
        for module,before,after in [('plugin_settings',"'secret':'secret'","'secret':'string'"),('plugin_settings','(int,float)','(int,float,bool)'),('plugin_settings','current.get(key)','current.get(key,default)'),('plugin_state',"{'security'}",'set()'),('plugin_state','return plugin_id,segments,value','return value'),('gateway_rpc',"params['key']","params['name']"),('mcp_discovery','return names','return []'),('gateway_plugin_models',"secret='secret'","secret='string'"),('gateway_plugin_models','value:object','value:str')]:
            changed=dict(initial);changed[module]=changed[module].replace(before,after);self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'],module)

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

    def test_recorded_api_actor_variants_preserve_baseline_and_reject_future_changes(self):
        tracked=json.loads((Path(__file__).resolve().parents[2]/'docs/contracts/hermes-baseline.json').read_text(encoding='utf-8'))
        self.assertEqual('f0d94882e89ab8d4098b5bb2f166adfb9ee0a0ab',tracked['upstream']['commit'])
        expected={'authority:respond_run': ['91ececa57860c5b4034976ce516f3521308ffb264d0d1a93f6ead1ceda6e151f', '4184a34230ffc7f0f80535fe18363db7d5b282a49f22046f1ecd5984c0bc3ca2'], 'authority:stop_run': ['71c001841698b9ef34ecfeb350a2d4382d508241f240430dddd476f7cab1645b', '96dd2e811391dc13582abe7de6386e772b55fd44d98ccf7ee73e437c616ca860'], 'authority:resolve_unknown_run': ['8fe7edb15f8bbb5bc55f1f3953d3ef91e94354939b35c8605570e010bfe6e045', 'f944fb77ca52397d51ec4f0d65d1dd6f3d79b550df3b46305abbe9d730e44c5d']}
        current={'commit':'e'*40,'contract':copy.deepcopy(tracked['upstream']['contract'])}
        # A complete synthetic current contract must include unrelated later introductions;
        # this test still isolates the three actor variants and preserves every original assertion.
        for key,entries in tracked['reviewed_handler_variants'].items():
            if tracked['upstream']['contract']['handlers'].get(key) is None:current['contract']['handlers'][key]=entries[-1]['sha256']
        for key,(before,after) in expected.items():
            self.assertEqual(before,tracked['upstream']['contract']['handlers'][key])
            evidence=tracked['reviewed_handler_variants'][key]
            self.assertEqual([after],[entry['sha256'] for entry in evidence])
            self.assertEqual(['07274ac65438a0b62bb9fd64bf284e58b2fd8fc6'],[entry['commit'] for entry in evidence])
            self.assertTrue(all(entry['reason'].strip() for entry in evidence))
            current['contract']['handlers'][key]=after
        self.assertFalse(checker.compare(tracked,current)['mobile_review_required'])
        for key in expected:
            changed=copy.deepcopy(current);changed['contract']['handlers'][key]='0'*64
            report=checker.compare(tracked,changed)
            self.assertTrue(report['mobile_review_required'])
            self.assertEqual({key},set(report['mobile_relevant_changes']['changed_used_handlers']))

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


class ProjectContractWatchTest(unittest.TestCase):
    def fixture(self):
        data=sources()
        data['project_contract']="""raise RuntimeError('Never execute project source')
class ProjectFolder(Result):
    path:str
    is_primary:bool=False
class ProjectInfo(Result):
    id:str
    created_at:int
    folders:list[ProjectFolder]
class ProjectsPayload(Result):
    projects:list[ProjectInfo]
    active_id:str|None=None
class ProjectResult(Result):
    project:ProjectInfo
class ProjectIdParams(ProfileParams):
    id:str
method('projects.list',params=ProfileParams,result=ProjectsPayload,doc='stored list')
method('projects.get',params=ProjectIdParams,result=ProjectResult,doc='stored detail')
"""
        data['project_methods']="""raise RuntimeError('Never execute project handlers')
_registry=HandlerRegistry()
method=_registry.method
def _projects_method(name):
    def decorate(fn):
        return _registry.profile_scoped(fn)
    return decorate
@_projects_method('projects.list')
def _(rid,params):
    return {'projects':list_projects(profile=params['profile']),'active_id':active()}
@_projects_method('projects.get')
def _(rid,params):
    return {'project':lookup(params['id'])}
def register(server):
    bind_module(globals(),server,skip=('_',))
"""
        data['project_db']="""raise RuntimeError('Never execute project DB code')
from hermes_constants import get_hermes_home
_ACTIVE_META_KEY='active_id'
def projects_db_path():
    return get_hermes_home() / 'projects.db'
def get_project(conn,selector):
    return by_id(selector) or by_slug(selector.lower())
def get_active_id(conn):
    return meta(conn,'active_id')
def _now():
    return int(time.time())
"""
        data['gateway_runtime']+="""
from . import methods_projects as _methods_projects, methods_tools as _methods_tools
for _m in (_methods_tools,_methods_projects):
    _m.register(sys.modules[__name__])
"""
        data['gateway_method_ctx']="""def rebind(fn,g):
    return copy_function(fn,g)
class HandlerRegistry:
    def profile_scoped(self,fn):
        fn._hermes_profile_scoped=True
        return fn
    def install(self,server):
        fn=server._profile_scoped(fn)
        return fn
"""
        return data

    def check_mutations(self,initial,handlers,fields,mutations):
        tracked=baseline(checker.extract(initial));tracked['mobile_used_handlers']+=handlers;tracked['mobile_used_request_fields']+=fields
        self.assertFalse(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(initial)})['mobile_review_required'])
        for source,before,after in mutations:
            with self.subTest(source=source,before=before):
                changed=copy.deepcopy(initial);self.assertIn(before,changed[source]);changed[source]=changed[source].replace(before,after)
                self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'])

    def test_project_list_keeps_its_identity_when_later_get_reuses_underscore(self):
        initial=self.fixture();old=checker.extract(initial)
        keys=['project_methods:rpc:projects.list','project_methods:rpc:projects.get']
        for key in keys:self.assertIn(key,old['handlers'])
        tracked=baseline(old);tracked['mobile_used_handlers']+=keys
        changed=copy.deepcopy(initial);changed['project_methods']=changed['project_methods'].replace("profile=params['profile']","profile='default'")
        report=checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})
        self.assertEqual({'project_methods:rpc:projects.list'},set(report['mobile_relevant_changes']['changed_used_handlers']))

    def test_project_profile_binding_and_deferred_registry_scope_are_watched(self):
        self.check_mutations(self.fixture(),['project_methods:_projects_method','gateway_method_ctx:rebind','gateway_method_ctx:HandlerRegistry.profile_scoped','gateway_method_ctx:HandlerRegistry.install'],[],[
            ('project_methods','return _registry.profile_scoped(fn)','return fn'),
            ('gateway_method_ctx','return copy_function(fn,g)','return fn'),
            ('gateway_method_ctx','fn._hermes_profile_scoped=True','fn._hermes_profile_scoped=False'),
            ('gateway_method_ctx','fn=server._profile_scoped(fn)','fn=fn')])

    def test_project_wire_fields_and_registered_result_types_are_watched(self):
        self.check_mutations(self.fixture(),['project_contract:method:projects.list'],['project_contract:ProjectInfo.created_at','project_contract:ProjectInfo.folders','project_contract:ProjectFolder.is_primary','project_contract:ProjectsPayload.active_id'],[
            ('project_contract','created_at:int','created_at:str'),
            ('project_contract','folders:list[ProjectFolder]','folders:list[str]'),
            ('project_contract','is_primary:bool=False','is_primary:str=False'),
            ('project_contract','active_id:str|None=None','active_id:int|None=None'),
            ('project_contract','result=ProjectsPayload','result=OtherPayload')])

    def test_project_id_slug_lookup_home_and_seconds_changes_request_review(self):
        self.check_mutations(self.fixture(),['project_db:get_project','project_db:projects_db_path','project_db:get_active_id','project_db:_now'],[],[
            ('project_db','by_id(selector) or by_slug(selector.lower())','by_slug(selector.lower())'),
            ('project_db',"get_hermes_home() / 'projects.db'","launch_home / 'projects.db'"),
            ('project_db',"meta(conn,'active_id')","meta(conn,'cwd')"),
            ('project_db','int(time.time())','int(time.time()*1000)')])

    def test_project_import_and_selected_module_registration_cannot_disappear(self):
        self.check_mutations(self.fixture(),['gateway_runtime:projects_registration_import','gateway_runtime:projects_registration_loop'],[],[
            ('gateway_runtime','from . import methods_projects','from other import methods_projects'),
            ('gateway_runtime','(_methods_tools,_methods_projects)','(_methods_tools,)'),
            ('gateway_runtime','_m.register(sys.modules[__name__])','_m.other(sys.modules[__name__])')])

    def test_project_registry_fingerprints_are_bound_to_the_actual_server_source(self):
        tracked=json.loads((Path(__file__).resolve().parents[2]/'docs/contracts/hermes-baseline.json').read_text(encoding='utf-8'))
        keys=[key for key in tracked['mobile_used_handlers'] if key.endswith((':projects_registration_import',':projects_registration_loop'))]
        self.assertEqual(2,len(keys))
        for key in keys:
            self.assertEqual('tui_gateway/server.py',checker.EXTRA_SOURCES[key.split(':',1)[0]])

    def test_project_source_is_parsed_without_execution_and_cannot_be_missing(self):
        initial=self.fixture();result=checker.extract(initial)
        self.assertIn('project_contract:ProjectInfo.created_at',result['request_fields'])
        for key in checker.PROJECT_CONTRACT_SOURCES:
            changed=copy.deepcopy(initial);del changed[key]
            with self.assertRaises(ValueError):checker.extract(changed)

    def mutation_fixture(self):
        data=self.fixture()
        data['project_contract']+="""
class ProjectsCreateParams(ProfileParams):
    name:str
    use:bool=False
class ProjectsUpdateParams(ProjectIdParams):
    description:str|None=None
class OptionalProjectResult(Result):
    project:ProjectInfo|None=None
class ActiveIdResult(Result):
    active_id:str|None=None
method('projects.create',params=ProjectsCreateParams,result=OptionalProjectResult)
method('projects.update',params=ProjectsUpdateParams,result=ProjectResult)
"""
        data['project_methods']+="""
def _register_project_mutator(suffix,fn_name,takes_path,kwargs_of):
    return require_project_then_call(fn_name,takes_path,kwargs_of)
_register_project_mutator('set_primary','set_primary',True,lambda p:{})
@_projects_method('projects.create')
def _(rid,params):
    pid=pdb.create_project(name=params['name'])
    record_feature_used('projects')
    return {'project':pdb.get_project(pid)}
"""
        data['project_db']+="""
def _normalize_path(path):
    return host_norm(path)
def set_primary(conn,id,path):
    if not attached(conn,id,path):
        return False
    return set_primary_locked(conn,id,path)
def delete_project(conn,id):
    return metadata_delete(conn,id)
"""
        return data

    def test_project_mutation_null_keep_clear_and_optional_creation_contracts_request_review(self):
        self.check_mutations(self.mutation_fixture(),['project_contract:schema:ProjectsUpdateParams','project_contract:method:projects.create'],['project_contract:ProjectsCreateParams.use','project_contract:ProjectsUpdateParams.description','project_contract:OptionalProjectResult.project'],[
            ('project_contract','use:bool=False','use:bool=True'),
            ('project_contract','description:str|None=None',"description:str=''") ,
            ('project_contract','project:ProjectInfo|None=None','project:ProjectInfo'),
            ('project_contract','ProjectsUpdateParams(ProjectIdParams)','ProjectsUpdateParams(ProfileParams)'),
            ('project_contract','result=OptionalProjectResult','result=OtherResult')])

    def test_project_dynamic_mutator_mapping_and_host_path_primary_effects_request_review(self):
        self.check_mutations(self.mutation_fixture(),['project_methods:_register_project_mutator','project_methods:mutator:set_primary','project_db:_normalize_path','project_db:set_primary'],[],[
            ('project_methods',"'set_primary','set_primary',True","'set_primary','set_primary',False"),
            ('project_methods','require_project_then_call','call_without_canonical_target'),
            ('project_db','host_norm(path)','android_norm(path)'),
            ('project_db','return False','return True')])

    def test_project_postwrite_creation_and_metadata_only_deletion_cannot_change_unnoticed(self):
        self.check_mutations(self.mutation_fixture(),['project_methods:rpc:projects.create','project_db:delete_project'],[],[
            ('project_methods',"record_feature_used('projects')","raise ValueError('after-write')"),
            ('project_methods',"pdb.create_project(name=params['name'])","pdb.create_and_activate(name=params['name'])"),
            ('project_db','metadata_delete(conn,id)','remove_files(conn,id)')])

if __name__=='__main__':unittest.main()


class KanbanContractWatchTests(unittest.TestCase):
    def fixture(self):
        data=sources()
        data['kanban_api']="raise RuntimeError('Do not execute plugin code')\nrouter=APIRouter()\nBOARD_COLUMNS=list(DEFAULT_WORKFLOW.keys())\n_read_board=coalesced_read(get_board)\n@router.get('/boards')\ndef list_boards(include_archived=False):\n    return shared_boards(include_archived)\n@router.get('/board')\nasync def get_board_endpoint(board=None):\n    return await _read_board(board=board or get_current_board())\n@router.get('/tasks/{task_id}')\ndef get_task(task_id,board=None):\n    return owned_detail(board,task_id)\n"
        data['kanban_db']="import os\ndef kanban_home():\n    return os.environ.get('HERMES_KANBAN_HOME') or get_default_hermes_root()\nclass Task:\n    id:str\n    body:str|None=None\nclass Run:\n    task_id:str\n    ended_at:int|None=None\n"
        data['kanban_boards']="def read_board_metadata(board):\n    return {'slug':board,'db_path':kanban_db_path(board)}\n"
        data['kanban_connect']="def connect(board=None):\n    guard_existing_db();migrate(board)\n    return open_db(board)\n"
        data['kanban_plugin_mount']="def _mount_one_plugin_api(app,plugin):\n    app.include_router(plugin.router,prefix='/api/plugins/'+plugin.name,dependencies=[Depends(_plugin_route_secret_scope)])\n"
        data['kanban_plugin_profiles']="def plugin_request_scope(scope):\n    return profile_scope(scope.query['profile'])\n"
        data['kanban_read_coalescing']="def coalesced_read(fn):\n    return keyed_by_home_and_arguments(fn)\n"
        data['kanban_workflow']="DEFAULT_WORKFLOW=Workflow(columns=('scheduled','running'))\n"
        return data
    def tracked(self):
        old=checker.extract(self.fixture());value=baseline(old)
        value['mobile_used_management_routes']+=['GET /api/plugins/kanban/boards','GET /api/plugins/kanban/board','GET /api/plugins/kanban/tasks/{task_id}']
        value['mobile_used_handlers']+=['kanban_api:list_boards','kanban_api:get_board_endpoint','kanban_api:get_task','kanban_api:binding:router','kanban_api:binding:BOARD_COLUMNS','kanban_api:binding:_read_board','kanban_db:kanban_home','kanban_db:schema:Task','kanban_db:schema:Run','kanban_boards:read_board_metadata','kanban_connect:connect','kanban_plugin_mount:_mount_one_plugin_api','kanban_plugin_profiles:plugin_request_scope','kanban_read_coalescing:coalesced_read','kanban_workflow:binding:DEFAULT_WORKFLOW']
        return value
    def test_plugin_routes_use_the_verified_mount_prefix_without_colliding_with_core_routes(self):
        old=checker.extract(self.fixture());self.assertIn('GET /api/plugins/kanban/boards',old['management_routes']);self.assertNotIn('GET /boards',old['management_routes'])
        data=self.fixture();data['kanban_api']=data['kanban_api'].replace("'/board'","'/renamed'")
        result=checker.compare(self.tracked(),{'commit':'b'*40,'contract':checker.extract(data)})
        self.assertEqual(['GET /api/plugins/kanban/board'],result['mobile_relevant_changes']['removed_used_management_routes'])
    def test_access_root_storage_coalescing_and_mount_changes_require_review(self):
        for module,before,after in [('kanban_api','owned_detail(board,task_id)','owned_detail(None,task_id)'),('kanban_api','board=board or get_current_board()','board=get_current_board()'),('kanban_api','APIRouter()','APIRouter(prefix="/new")'),('kanban_db',"HERMES_KANBAN_HOME","HERMES_HOME"),('kanban_boards',"'db_path':kanban_db_path(board)","'db_path':other_db()"),('kanban_connect','guard_existing_db();migrate(board)','dispatch_work();migrate(board)'),('kanban_plugin_mount','dependencies=[Depends(_plugin_route_secret_scope)]','dependencies=[]'),('kanban_plugin_profiles',"scope.query['profile']","None"),('kanban_read_coalescing','keyed_by_home_and_arguments(fn)','globally_cached(fn)'),('kanban_workflow',"'scheduled','running'","'scheduled','ready','running'")]:
            data=self.fixture();self.assertIn(before,data[module]);data[module]=data[module].replace(before,after)
            self.assertTrue(checker.compare(self.tracked(),{'commit':'b'*40,'contract':checker.extract(data)})['mobile_review_required'],module)
    def test_task_and_attempt_schema_changes_and_missing_sources_fail_closed(self):
        data=self.fixture();data['kanban_db']=data['kanban_db'].replace('ended_at:int|None=None','ended_at:str|None=None')
        result=checker.compare(self.tracked(),{'commit':'b'*40,'contract':checker.extract(data)})
        self.assertIn('kanban_db:schema:Run',result['mobile_relevant_changes']['changed_used_handlers'])
        data=self.fixture();data.pop('kanban_read_coalescing')
        with self.assertRaises(ValueError):checker.extract(data)
    def test_unknown_new_plugin_route_is_triage_information_without_silencing_used_changes(self):
        data=self.fixture();data['kanban_api']+="\n@router.get('/new-feature')\ndef new_feature():\n    return {}\n"
        result=checker.compare(self.tracked(),{'commit':'b'*40,'contract':checker.extract(data)})
        self.assertFalse(result['mobile_review_required']);self.assertIn('GET /api/plugins/kanban/new-feature',result['added_management_routes'])


class KanbanBoardWriteWatchTests(unittest.TestCase):
    def fixture(self):
        data=KanbanContractWatchTests().fixture()
        data['kanban_api']+="\nclass CreateBoardBody(BaseModel):\n    slug:str\n    project_id:str|None=None\n    switch:bool=False\nclass RenameBoardBody(BaseModel):\n    default_workdir:str|None=None\n    project_id:str|None=None\n@router.post('/boards')\ndef create_board_endpoint(payload:CreateBoardBody):\n    return create_board(payload.slug,switch=payload.switch)\n@router.patch('/boards/{slug}')\ndef rename_board(slug,payload:RenameBoardBody):\n    return write_board_metadata(slug,project=resolve(payload.project_id))\n@router.delete('/boards/{slug}')\ndef delete_board(slug,delete=False):\n    return remove_board(slug,archive=not delete)\n@router.post('/boards/{slug}/switch')\ndef switch_board(slug):\n    return set_current_board(slug)\n"
        data['kanban_boards']+="\ndef create_board(slug,switch=False):\n    write_board_metadata(slug,archived=False)\n    return init_db(slug)\ndef write_board_metadata(slug,project=None,archived=None):\n    return persist_metadata(slug,project,archived)\ndef remove_board(slug,archive=True):\n    clear_current_board()\n    return move_or_delete(slug,archive)\n"
        data['kanban_db']+="\ndef set_current_board(slug):\n    require_live(slug)\n    return persist_current(slug)\n"
        return data
    def tracked(self):
        value=baseline(checker.extract(self.fixture()))
        value['mobile_used_management_routes']+=['POST /api/plugins/kanban/boards','PATCH /api/plugins/kanban/boards/{slug}','DELETE /api/plugins/kanban/boards/{slug}','POST /api/plugins/kanban/boards/{slug}/switch']
        value['mobile_used_handlers']+=['kanban_api:schema:CreateBoardBody','kanban_api:schema:RenameBoardBody','kanban_api:create_board_endpoint','kanban_api:rename_board','kanban_api:delete_board','kanban_api:switch_board','kanban_boards:create_board','kanban_boards:write_board_metadata','kanban_boards:remove_board','kanban_db:set_current_board']
        value['mobile_used_request_fields']+=['kanban_api:CreateBoardBody.switch','kanban_api:RenameBoardBody.default_workdir']
        return value
    def test_each_used_write_route_removed_requires_review(self):
        for route in ["@router.post('/boards')","@router.patch('/boards/{slug}')","@router.delete('/boards/{slug}')","@router.post('/boards/{slug}/switch')"]:
            data=self.fixture();self.assertIn(route,data['kanban_api']);data['kanban_api']=data['kanban_api'].replace(route,route.replace('boards','renamed'))
            result=checker.compare(self.tracked(),{'commit':'b'*40,'contract':checker.extract(data)})
            self.assertTrue(result['mobile_review_required']);self.assertEqual(1,len(result['mobile_relevant_changes']['removed_used_management_routes']))
    def test_defaults_and_new_schema_fields_cannot_escape_whole_schema_watch(self):
        for before,after in [('switch:bool=False','switch:bool=True'),('default_workdir:str|None=None','default_workdir:str=""'),('slug:str','slug:str\n    overwrite:bool=True')]:
            data=self.fixture();data['kanban_api']=data['kanban_api'].replace(before,after)
            self.assertTrue(checker.compare(self.tracked(),{'commit':'b'*40,'contract':checker.extract(data)})['mobile_review_required'])
    def test_partial_effect_order_and_archive_direction_changes_require_review(self):
        for module,before,after in [('kanban_api','archive=not delete','archive=delete'),('kanban_boards','write_board_metadata(slug,archived=False)','write_board_metadata(slug,archived=True)'),('kanban_boards','clear_current_board()\n    return move_or_delete(slug,archive)','move_or_delete(slug,archive)\n    return clear_current_board()'),('kanban_db','require_live(slug)','pass')]:
            data=self.fixture();self.assertIn(before,data[module]);data[module]=data[module].replace(before,after)
            self.assertTrue(checker.compare(self.tracked(),{'commit':'b'*40,'contract':checker.extract(data)})['mobile_review_required'])
    def test_project_binding_resolution_cannot_be_replaced_by_unscoped_write(self):
        data=self.fixture();data['kanban_api']=data['kanban_api'].replace('project=resolve(payload.project_id)','project=payload.project_id')
        result=checker.compare(self.tracked(),{'commit':'b'*40,'contract':checker.extract(data)})
        self.assertIn('kanban_api:rename_board',result['mobile_relevant_changes']['changed_used_handlers'])
    def test_unrelated_new_write_is_triaged_without_accepting_used_drift(self):
        data=self.fixture();data['kanban_api']+="\n@router.post('/unused')\ndef unused():\n    return {}\n"
        result=checker.compare(self.tracked(),{'commit':'b'*40,'contract':checker.extract(data)})
        self.assertFalse(result['mobile_review_required']);self.assertIn('POST /api/plugins/kanban/unused',result['added_management_routes'])


class PluginUpdateLockWatchTests(unittest.TestCase):
    def fixture(self):
        data=sources()
        data['plugin_auto_update']="raise RuntimeError('Never execute upstream dependency')\nimport threading\nfrom pathlib import Path\n_LOCK_WAIT_SECONDS=1.0\n_lock_holders={}\n_lock_holders_guard=threading.Lock()\ndef _holder(key):\n    return _lock_holders.setdefault(key,threading.local())\ndef plugin_update_lock(target):\n    return bounded_lock(target,_LOCK_WAIT_SECONDS)\n"
        data['provider_auth']="try:\n    import fcntl\nexcept ImportError:\n    fcntl=None\ndef _file_lock(path,holder,timeout,message):\n    return kernel_lock(path,timeout)\n"
        data['plugin_update']="def _dashboard_update_locked(name,force=False,accept_capabilities=False):\n    if needs_consent(name) and not accept_capabilities:\n        return refuse()\n    if dangerous(name):\n        return blocked()\n    return publish(name,force=force)\n"
        return data
    def tracked(self):
        value=baseline(checker.extract(self.fixture()));value['mobile_used_handlers']+=['plugin_auto_update:binding:_LOCK_WAIT_SECONDS','plugin_auto_update:binding:_lock_holders_guard','plugin_auto_update:import:threading','plugin_auto_update:plugin_update_lock','provider_auth:_file_lock','provider_auth:lock_platform:fcntl','plugin_update:_dashboard_update_locked'];return value
    def test_lock_timeout_import_owner_backend_and_platform_changes_require_review(self):
        for module,before,after,key in [('plugin_auto_update','_LOCK_WAIT_SECONDS=1.0','_LOCK_WAIT_SECONDS=20.0','plugin_auto_update:binding:_LOCK_WAIT_SECONDS'),('plugin_auto_update','threading.Lock()','fake_lock()','plugin_auto_update:binding:_lock_holders_guard'),('plugin_auto_update','import threading','import other as threading','plugin_auto_update:import:threading'),('plugin_auto_update','bounded_lock(target,_LOCK_WAIT_SECONDS)','unbounded_lock(target)','plugin_auto_update:plugin_update_lock'),('provider_auth','kernel_lock(path,timeout)','open_without_lock(path)','provider_auth:_file_lock'),('provider_auth','fcntl=None','fcntl=unsafe_backend()','provider_auth:lock_platform:fcntl')]:
            data=self.fixture();self.assertIn(before,data[module]);data[module]=data[module].replace(before,after);result=checker.compare(self.tracked(),{'commit':'b'*40,'contract':checker.extract(data)})
            self.assertTrue(result['mobile_review_required']);self.assertIn(key,result['mobile_relevant_changes']['changed_used_handlers'])
    def test_extracted_manual_body_cannot_bypass_consent_scan_or_force_choice(self):
        for before,after in [('not accept_capabilities','False'),('return blocked()','return publish(name,force=True)'),('publish(name,force=force)','publish(name,force=True)')]:
            data=self.fixture();data['plugin_update']=data['plugin_update'].replace(before,after);result=checker.compare(self.tracked(),{'commit':'b'*40,'contract':checker.extract(data)})
            self.assertIn('plugin_update:_dashboard_update_locked',result['mobile_relevant_changes']['changed_used_handlers'])
        data=self.fixture();data.pop('plugin_auto_update')
        with self.assertRaises(ValueError):checker.extract(data)
    def introduced(self):
        data=self.fixture();current=checker.extract(data);old_data=dict(data);old_data['plugin_auto_update']='';value=baseline(checker.extract(old_data));key='plugin_auto_update:plugin_update_lock'
        value['mobile_used_handlers'].append(key);value['reviewed_handler_variants']={key:[{'sha256':current['handlers'][key],'commit':'c'*40,'reason':'Synthetic source introduction'}]}
        value['reviewed_handler_absences']={key:[{'commit':'d'*40,'reason':'Synthetic exact older stable source lacks the introduced helper'}]}
        return value,current,key
    def test_introduced_handler_removal_is_not_silently_accepted_as_the_original_absence(self):
        value,current,key=self.introduced();self.assertFalse(checker.compare(value,{'commit':'e'*40,'contract':current})['mobile_review_required']);missing={**current,'handlers':{k:v for k,v in current['handlers'].items() if k!=key}}
        report=checker.compare(value,{'commit':'e'*40,'contract':missing});self.assertTrue(report['mobile_review_required']);self.assertTrue(report['mobile_relevant_changes']['changed_used_handlers'][key]['required_introduced_handler_missing'])
        self.assertFalse(checker.compare(value,{'commit':'d'*40,'contract':missing})['mobile_review_required'])
        self.assertFalse(checker.compare(value,{'commit':value['upstream']['commit'],'contract':missing})['mobile_review_required'])
        self.assertTrue(checker.compare(value,{'commit':'f'*40,'contract':missing})['mobile_review_required'])
    def test_absence_exceptions_require_selected_introduced_handlers_and_exact_pinned_evidence(self):
        value,current,key=self.introduced()
        for replacement in [[],[{'commit':'main','reason':'review'}],[{'commit':'d'*40,'reason':''}],[{'commit':value['upstream']['commit'],'reason':'redundant baseline'}],['not evidence']]:
            bad=copy.deepcopy(value);bad['reviewed_handler_absences'][key]=replacement
            with self.assertRaises(ValueError):checker.compare(bad,{'commit':'e'*40,'contract':current})
        bad=copy.deepcopy(value);bad['reviewed_handler_absences']['unknown:handler']=bad['reviewed_handler_absences'].pop(key)
        with self.assertRaises(ValueError):checker.compare(bad,{'commit':'e'*40,'contract':current})
    def test_every_recorded_introduced_relay_or_lock_handler_requires_future_presence(self):
        value=json.loads((Path(__file__).resolve().parents[2]/'docs/contracts/hermes-baseline.json').read_text(encoding='utf-8'));current=copy.deepcopy(value['upstream']['contract'])
        introduced=[k for k in value['reviewed_handler_variants'] if current['handlers'].get(k) is None]
        self.assertGreaterEqual(len(introduced),70)
        for key in introduced:current['handlers'][key]=value['reviewed_handler_variants'][key][-1]['sha256']
        self.assertFalse(checker.compare(value,{'commit':'e'*40,'contract':current})['mobile_review_required'])
        for key in introduced:
            missing={**current,'handlers':{k:v for k,v in current['handlers'].items() if k!=key}}
            report=checker.compare(value,{'commit':'e'*40,'contract':missing})
            self.assertEqual({key},set(report['mobile_relevant_changes']['changed_used_handlers']));self.assertTrue(report['mobile_relevant_changes']['changed_used_handlers'][key]['required_introduced_handler_missing'])
