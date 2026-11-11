"""Auth-boundary drift must retain exact producer and callback dependencies."""
import copy
import json
from pathlib import Path
import unittest
from unittest.mock import patch

from test_hermes_contract import checker, sources, baseline, complete_replacement_fixture, profile_ticket_historical_projection, project_sessions_historical_projection


class NativeAuthBoundaryContractTest(unittest.TestCase):
    def real(self):
        return profile_ticket_historical_projection(project_sessions_historical_projection(json.loads((Path(__file__).resolve().parents[2]/'docs/contracts/hermes-baseline.json').read_text(encoding='utf-8'))))

    def current(self, value):
        contract=copy.deepcopy(value['upstream']['contract'])
        for key,entries in value['reviewed_handler_variants'].items():
            if key not in contract['handlers']:
                contract['handlers'][key]=entries[-1]['sha256']
        complete_replacement_fixture(value,contract)
        for key in ('auth_middleware:_GATE_PUBLIC_PREFIXES','auth_middleware:gated_auth_middleware',
                    'auth_native_run_api:GatewayRuntimeAPI._serve'):
            entry=value['reviewed_handler_variants'][key][-1]
            contract['handlers'][key]=entry['sha256']
            contract['source_hashes'].update(entry['required_sources'])
        return {'commit':'071d31ffc69493ca1d7d368c163ce0b303d83387','contract':contract}

    def test_every_changed_boundary_refuses_missing_or_changed_complete_dependency(self):
        value=self.real();current=self.current(value)
        self.assertFalse(checker.compare(value,current)['mobile_review_required'])
        for key in ('auth_middleware:_GATE_PUBLIC_PREFIXES','auth_middleware:gated_auth_middleware',
                    'auth_native_run_api:GatewayRuntimeAPI._serve'):
            entry=value['reviewed_handler_variants'][key][-1]
            for source in entry['required_sources']:
                for missing in (False,True):
                    with self.subTest(handler=key,dependency=source,missing=missing):
                        changed=copy.deepcopy(current)
                        if missing:changed['contract']['source_hashes'].pop(source)
                        else:changed['contract']['source_hashes'][source]='0'*64
                        report=checker.compare(value,changed)
                        self.assertTrue(report['mobile_review_required'])
                        self.assertIn(key,report['mobile_relevant_changes']['changed_used_handlers'])

    def test_unknown_handler_body_is_refused_even_with_reviewed_module_identities(self):
        value=self.real();current=self.current(value)
        for key in ('auth_middleware:_GATE_PUBLIC_PREFIXES','auth_middleware:gated_auth_middleware',
                    'auth_native_run_api:GatewayRuntimeAPI._serve'):
            with self.subTest(handler=key):
                changed=copy.deepcopy(current);changed['contract']['handlers'][key]='0'*64
                report=checker.compare(value,changed)
                self.assertTrue(report['mobile_review_required'])
                self.assertIn(key,report['mobile_relevant_changes']['changed_used_handlers'])

    def test_commit_label_alone_never_authorizes_different_source(self):
        value=self.real();current=self.current(value)
        current['commit']='c'*40
        self.assertFalse(checker.compare(value,current)['mobile_review_required'])
        current['contract']['source_hashes']['saved_gateway_oauth']='0'*64
        self.assertTrue(checker.compare(value,current)['mobile_review_required'])

    def test_known_absent_files_require_a_complete_tree_and_an_exact_older_pin(self):
        saved={key:path for key,path in checker.AUTH_BOUNDARY_CONTRACT_SOURCES.items() if key.startswith('saved_gateway_')}
        wanted=set((checker.SOURCES|checker.EXTRA_SOURCES).values())
        omitted=set(saved.values())|{checker.EXTRA_SOURCES['plugin_auto_update']}
        rows=[{'path':path,'type':'blob','mode':'100644','sha':'a'*40,'size':12} for path in sorted(wanted-omitted)]
        for commit in ('f0d94882e89ab8d4098b5bb2f166adfb9ee0a0ab','b200f5bc60e31588ba2cb60e288029c4a54ba5fa'):
            with self.subTest(commit=commit),patch.object(checker,'github',return_value={'sha':commit,'truncated':False,'tree':rows}):
                result=checker.source_identities(commit)
                self.assertTrue(all(path not in result for path in saved.values()))
        with patch.object(checker,'github',return_value={'sha':'c'*40,'truncated':False,'tree':rows}):
            with self.assertRaises(ValueError):checker.source_identities('c'*40)
        with patch.object(checker,'github',return_value={'sha':'a'*40,'truncated':True,'tree':rows}):
            with self.assertRaises(ValueError):checker.source_identities('f0d94882e89ab8d4098b5bb2f166adfb9ee0a0ab')
        for key,path in saved.items():
            self.assertFalse(checker.reviewed_source_absence(key,path+'/wrong','f0d94882e89ab8d4098b5bb2f166adfb9ee0a0ab'))

    def test_native_profile_purpose_peer_and_one_use_guards_cannot_drift_silently(self):
        data=sources()
        data['auth_native_http']="raise RuntimeError('never import upstream')\nHEADER='x-hermes-gateway-ticket'\ndef _native_store(request):\n    if request.origin or request.peer not in ('127.0.0.1','::1'):return None\n    return request.store\ndef _own_profile(value,home):\n    if value!=home:raise PermissionError()\nasync def authenticate_native_http(request):\n    grant=request.store.redeem(request.ticket,purpose='native-http')\n    _own_profile(request.profile,grant['profile_id'])\n    request.state.native_http_principal=grant\n"
        data['auth_native_bootstrap']="_PURPOSE_CAPABILITIES={'native-http':frozenset({'http:owner'})}\nclass TicketStore:\n    TTL_SECONDS=30\n    def redeem(self,ticket,purpose):\n        grant=self.entries[ticket]\n        if grant['purpose']!=purpose:raise PermissionError()\n        del self.entries[ticket]\n        return grant\n"
        old=checker.extract(data);tracked=baseline(old)
        keys=['auth_native_http:binding:HEADER','auth_native_http:_native_store','auth_native_http:_own_profile',
              'auth_native_http:authenticate_native_http','auth_native_bootstrap:binding:_PURPOSE_CAPABILITIES',
              'auth_native_bootstrap:binding:TicketStore.TTL_SECONDS','auth_native_bootstrap:TicketStore.redeem']
        tracked['mobile_used_handlers']+=keys
        self.assertFalse(checker.compare(tracked,{'commit':'b'*40,'contract':old})['mobile_review_required'])
        mutations=[('auth_native_http',"'x-hermes-gateway-ticket'","'authorization'"),
                   ('auth_native_http',"request.origin or request.peer not in ('127.0.0.1','::1')",'False'),
                   ('auth_native_http','value!=home','False'),
                   ('auth_native_http',"purpose='native-http'","purpose='interactive'"),
                   ('auth_native_bootstrap',"frozenset({'http:owner'})","frozenset({'session:submit'})"),
                   ('auth_native_bootstrap','TTL_SECONDS=30','TTL_SECONDS=3600'),
                   ('auth_native_bootstrap','del self.entries[ticket]','pass')]
        for name,before,after in mutations:
            with self.subTest(source=name,change=before):
                changed=dict(data);self.assertIn(before,changed[name]);changed[name]=changed[name].replace(before,after)
                self.assertTrue(checker.compare(tracked,{'commit':'b'*40,'contract':checker.extract(changed)})['mobile_review_required'])

    def test_original_baseline_keeps_its_identity_and_adds_verified_producer_projection(self):
        value=self.real()
        self.assertEqual('f0d94882e89ab8d4098b5bb2f166adfb9ee0a0ab',value['upstream']['commit'])
        self.assertFalse(checker.compare(value,{'commit':value['upstream']['commit'],'contract':copy.deepcopy(value['upstream']['contract'])})['mobile_review_required'])
        for key in ('web_bootstrap:_token_auth_seam','auth_native_http:authenticate_native_http',
                    'auth_native_http:_native_store','auth_native_bootstrap:TicketStore.redeem',
                    'auth_native_run_api:GatewayRuntimeAPI._serve'):
            self.assertIn(key,value['mobile_used_handlers'])
            self.assertIn(key,value['upstream']['contract']['handlers'])
        self.assertEqual('608abee247b8e1c6f8641b619d9eb6bddd053a85ee3bd758b31f769b075713b8',value['upstream']['contract']['source_hashes']['auth_native_http'])
        self.assertEqual(7,len(checker.AUTH_BOUNDARY_CONTRACT_SOURCES))
        for key in ('auth_middleware:_GATE_PUBLIC_PREFIXES','auth_middleware:gated_auth_middleware'):
            self.assertTrue(set(checker.AUTH_BOUNDARY_CONTRACT_SOURCES)<=set(value['reviewed_handler_variants'][key][-1]['required_sources']))


if __name__=='__main__':unittest.main()
