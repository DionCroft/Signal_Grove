"""Negative fixtures for reviewed upstream auth/import/recall/profile boundaries."""
import copy
import json
from pathlib import Path
import unittest

from test_hermes_contract import checker, sources, baseline


class RuntimeGuardContractTest(unittest.TestCase):
    def fixture(self):
        data=sources()
        data.update({
            'web_signin': "raise RuntimeError('never execute upstream')\ndef access_token_works_alone(request,token):\n    return token_works_alone(request,token) or _is_deployment_token(token)\ndef _is_deployment_token(token):\n    provider=get_provider('session-token')\n    return bool(token) and callable(provider.matches_token) and provider.matches_token(token)\n",
            'session_token_provider': "import hmac\nclass SessionTokenProvider:\n    name='session-token'\n    interactive_login=False\n    def matches_token(self,token):\n        return bool(token) and hmac.compare_digest(token.encode(),self._token.encode())\n    def verify_session(self,access_token):\n        return session() if self.matches_token(access_token) else None\n",
            'auth': "def auth_native_refresh(request,token):\n    return token_works_alone(request,token)\n",
            'auth_middleware': "_SESSION_TOKEN_HEADER='x-hermes-session-token'\n_SESSION_TOKEN_PROVIDER='session-token'\ndef _cookie_write_refusal(request):\n    if request.has_cookies and not cookie_origin_is_allowed(request):return refuse(403)\ndef _session_token_header_session(request):\n    token=request.headers.get(_SESSION_TOKEN_HEADER,'')\n    provider=get_provider(_SESSION_TOKEN_PROVIDER)\n    return provider.verify_session(token) if provider and access_token_works_alone(request,token) else None\ndef gated_auth_middleware(request):\n    if request.bearer:return verify(request.bearer)\n    if request.has_cookies:return checked_cookie(request)\n    return _session_token_header_session(request)\n",
            'auth_request_utils': "def cookie_origin_is_allowed(request):\n    return len(request.origins)==1 and same_origin(request.origins[0],trusted_host(request))\n",
            'plugin_isolation': "import sys\nISOLATION_HOST='host'\ndef import_user_code(path,name):\n    module=make_module(path)\n    sys.modules[name]=module\n    try:execute(module)\n    except Exception:\n        sys.modules.pop(name,None)\n        raise\n    return module\n",
            'kanban_plugin_mount': "def _mount_one_plugin_api(plugin):\n    if skip_reason(plugin):return False\n    if plugin.source=='project':return False\n    if isolation_mode()=='host':return mount_hosted(plugin)\n    return import_user_code(plugin.path,plugin.name)\n",
            'plugin_core': "def _refuse_recalled_entry(entry):\n    if entry is not None and entry[3]!='bundled':refuse_if_installed_removed(entry[0],entry[4])\ndef _set_plugin_enabled(name,enable):\n    if enable:_refuse_recalled_entry(_find_plugin_entry(name))\n    return activate(name,enable)\n",
            'plugin_install': "def _install_plugin_core(manifest,allow_removed=False):\n    if not allow_removed:raise_if_removed(manifest.name,manifest.repository)\n    install_dependencies(manifest)\n",
            'plugin_catalog_gate': "def installed_plugin_removal(record):\n    if record.get('allow_removed') is True:return None\n    return find_removed(record)\n",
            'plugin_update': "def _pull_plugin_update(record):\n    return _reclone_plugin_update(allow_removed=record.get('allow_removed') is True)\ndef _reclone_plugin_update(allow_removed=False):\n    return install(allow_removed=allow_removed)\n",
            'channel_served': "def served_profile_mirror_platforms(runtime,profile):\n    if not profile or profile=='default' or not isinstance(runtime,dict):return {}\n    return {k:v for k,v in runtime.items() if k=='relay' or v['state'] in {'connected','connecting','retrying'}}\n",
            'channels': "def _messaging_platform_payload(entry):\n    if entry.mirrored and not(entry.enabled and entry.configured):return {}\n    return entry.runtime\n",
            'channel_config': "SERVED_PROFILE_MIRROR_PLATFORMS=SHARED_LISTENER_MIRROR_PLATFORMS|{'relay'}\n",
        })
        tracked=baseline(checker.extract(data))
        keys=['web_signin:access_token_works_alone','web_signin:_is_deployment_token',
              'session_token_provider:SessionTokenProvider.matches_token','session_token_provider:SessionTokenProvider.verify_session',
              'session_token_provider:binding:SessionTokenProvider.name','session_token_provider:binding:SessionTokenProvider.interactive_login',
              'session_token_provider:import:hmac','auth:auth_native_refresh',
              'auth_middleware:_SESSION_TOKEN_HEADER','auth_middleware:_SESSION_TOKEN_PROVIDER',
              'auth_middleware:_cookie_write_refusal','auth_middleware:_session_token_header_session','auth_middleware:gated_auth_middleware',
              'auth_request_utils:cookie_origin_is_allowed','plugin_isolation:import:sys','plugin_isolation:binding:ISOLATION_HOST',
              'plugin_isolation:import_user_code','kanban_plugin_mount:_mount_one_plugin_api',
              'plugin_core:_refuse_recalled_entry','plugin_core:_set_plugin_enabled','plugin_install:_install_plugin_core',
              'plugin_catalog_gate:installed_plugin_removal','plugin_update:_pull_plugin_update','plugin_update:_reclone_plugin_update',
              'channel_served:served_profile_mirror_platforms','channels:_messaging_platform_payload','channel_config:binding:SERVED_PROFILE_MIRROR_PLATFORMS']
        for key in keys:self.assertIn(key,tracked['upstream']['contract']['handlers'])
        tracked['mobile_used_handlers']+=keys
        return data,tracked,keys

    def test_guard_mutations_require_review_without_executing_source(self):
        data,tracked,_=self.fixture()
        self.assertFalse(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(data)})['mobile_review_required'])
        mutations=[
            ('web_signin',"get_provider('session-token')","get_provider('other')"),
            ('web_signin','bool(token) and callable','True and callable'),
            ('session_token_provider','hmac.compare_digest(token.encode(),self._token.encode())','token==self._token'),
            ('session_token_provider','bool(token) and','True and'),
            ('session_token_provider',"name='session-token'","name='other'"),
            ('session_token_provider','interactive_login=False','interactive_login=True'),
            ('session_token_provider','import hmac','import unsafe as hmac'),
            ('session_token_provider','self.matches_token(access_token)','True'),
            ('auth','return token_works_alone','return access_token_works_alone'),
            ('auth_middleware',"'x-hermes-session-token'","'authorization'"),
            ('auth_middleware',"_SESSION_TOKEN_PROVIDER='session-token'","_SESSION_TOKEN_PROVIDER='other'"),
            ('auth_middleware','request.has_cookies and not cookie_origin_is_allowed(request)','False'),
            ('auth_middleware','if request.bearer:return verify(request.bearer)','if request.bearer:return _session_token_header_session(request)'),
            ('auth_middleware','if request.has_cookies:return checked_cookie(request)','if request.has_cookies:return _session_token_header_session(request)'),
            ('auth_request_utils','len(request.origins)==1','len(request.origins)>0'),
            ('auth_request_utils','trusted_host(request)','untrusted_forwarded_host(request)'),
            ('plugin_isolation','import sys','import unsafe as sys'),
            ('plugin_isolation',"ISOLATION_HOST='host'","ISOLATION_HOST='in-process'"),
            ('plugin_isolation','sys.modules.pop(name,None)','pass'),
            ('plugin_isolation','sys.modules[name]=module','pass'),
            ('kanban_plugin_mount',"plugin.source=='project'","False"),
            ('kanban_plugin_mount',"isolation_mode()=='host'","False"),
            ('kanban_plugin_mount','if skip_reason(plugin)','if False'),
            ('plugin_core','entry[0],entry[4]','entry[0],guess_directory(entry[0])'),
            ('plugin_core','_refuse_recalled_entry(_find_plugin_entry(name))','pass'),
            ('plugin_install','if not allow_removed:raise_if_removed(manifest.name,manifest.repository)\n    install_dependencies(manifest)',
             'install_dependencies(manifest)\n    if not allow_removed:raise_if_removed(manifest.name,manifest.repository)'),
            ('plugin_catalog_gate',"record.get('allow_removed') is True","bool(record.get('allow_removed'))"),
            ('plugin_update','allow_removed=False','allow_removed=True'),
            ('plugin_update',"record.get('allow_removed') is True","True"),
            ('channel_served',"profile=='default'","profile=='unused'"),
            ('channel_served',"{'connected','connecting','retrying'}","{'failed','connected'}"),
            ('channels','not(entry.enabled and entry.configured)','False'),
            ('channel_config',"|{'relay'}","|{'all'}"),
        ]
        for module,before,after in mutations:
            with self.subTest(module=module,change=before):
                self.assertIn(before,data[module]);changed=copy.deepcopy(data)
                changed[module]=changed[module].replace(before,after)
                report=checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})
                self.assertTrue(report['mobile_review_required'])
                self.assertTrue(report['mobile_relevant_changes']['changed_used_handlers'])

    def test_introduced_guard_absence_is_exact_commit_only(self):
        data,tracked,_=self.fixture();key='web_signin:access_token_works_alone'
        original=copy.deepcopy(data);original['web_signin']=''
        tracked=baseline(checker.extract(original));tracked['mobile_used_handlers'].append(key)
        digest=checker.extract(data)['handlers'][key]
        tracked['reviewed_handler_variants']={key:[{'sha256':digest,'commit':'b'*40,'reason':'Reviewed introduced helper'}]}
        tracked['reviewed_handler_absences']={key:[{'commit':'d'*40,'reason':'Inspected additional pre-introduction pin'}]}
        self.assertFalse(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(data)})['mobile_review_required'])
        self.assertFalse(checker.compare(tracked,{'commit':'a'*40,'contract':checker.extract(original)})['mobile_review_required'])
        self.assertFalse(checker.compare(tracked,{'commit':'d'*40,'contract':checker.extract(original)})['mobile_review_required'])
        self.assertTrue(checker.compare(tracked,{'commit':'c'*40,'contract':checker.extract(original)})['mobile_review_required'])
        data['web_signin']=data['web_signin'].replace('or _is_deployment_token(token)','or bool(token)')
        self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(data)})['mobile_review_required'])

    def test_real_baseline_selects_the_direct_guard_dependencies(self):
        real=json.loads((Path(__file__).resolve().parents[2]/'docs/contracts/hermes-baseline.json').read_text(encoding='utf-8'))
        _,_,keys=self.fixture()
        self.assertTrue(set(keys)<=set(real['mobile_used_handlers']))
        for key in ('session_token_loader:resolve_session_token','signin_registry:get_provider',
                    'plugin_catalog_loader:cached_removed_entries','plugin_catalog_loader:_stale_live_cache',
                    'kanban_plugin_mount:_mount_hosted_plugin_api','kanban_roots:normalize_scope'):
            self.assertIn(key,real['mobile_used_handlers'])
        self.assertEqual(len(checker.RUNTIME_GUARD_CONTRACT_SOURCES),10)
        self.assertEqual(checker.RUNTIME_GUARD_CONTRACT_SOURCES['gateway_server_requests'],'tui_gateway/server_requests.py')
        self.assertEqual(checker.RUNTIME_GUARD_CONTRACT_SOURCES['signin_registry_engine'],'agent/provider_registry.py')
        self.assertEqual(real['upstream']['commit'],'f0d94882e89ab8d4098b5bb2f166adfb9ee0a0ab')


if __name__=='__main__':unittest.main()
