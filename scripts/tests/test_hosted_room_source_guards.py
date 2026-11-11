"""Room read contracts preserve the original189-source baseline and exact dependency modes."""
import copy
import hashlib
import json
from pathlib import Path
import unittest
from test_hermes_contract import checker, sources, profile_ticket_historical_projection

# Exclude only explicitly added workspace and history watches from historical projections.
HISTORY_WATCHES={'api:_project_client_message','api:APIServerAdapter._message_response'}
WORKSPACE_FIELDS={ 'canonical_wire:SessionMutateParams.'+x for x in ('session_id','request_id','expected_revision','operation','payload','expected_generation')} | {'canonical_wire:SessionMutateResult.'+x for x in ('session_id','operation','revision')}

GOLDEN={'contract': '8b6e0d1272378e190b5d10e29f9aec251de5d024df8e3c8e01da444e8dbad6bb', 'scope': 'cbcd74b483f54d89068575e278dc5fb55a4fab94ff1aa7f0c1e00ca7370eca3b', 'variants': '3e817ad24ab57a0a4697cb73bed7f5caae4f050fd9275161084c92f15aa18c41', 'absences': 'f7d5c2b4f5aac2951f82f4d505aeaf463c7da0b9721f68464af94192f46126ed'}
class HostedRoomSourceGuardsTest(unittest.TestCase):
    def baseline(self):return profile_ticket_historical_projection(json.loads((Path(__file__).resolve().parents[2]/'docs/contracts/hermes-baseline.json').read_text()))
    def digest(self,value):return hashlib.sha256(json.dumps(value,sort_keys=True,separators=(',',':'),ensure_ascii=True).encode()).hexdigest()
    def test_all_original189_entries_mobile_scope_and_review_modes_are_exact(self):
        value=self.baseline();aliases=checker.HOSTED_ROOM_CONTRACT_SOURCES;contract=copy.deepcopy(value['upstream']['contract'])
        for field in ['source_hashes','handlers','request_fields']:
            contract[field]={k:v for k,v in contract[field].items() if k not in HISTORY_WATCHES and k not in (aliases|checker.WORKSPACE_CONTRACT_SOURCES) and not any(k.startswith(a+':') for a in (aliases|checker.WORKSPACE_CONTRACT_SOURCES))}
        scope={k:copy.deepcopy(v) for k,v in value.items() if k.startswith('mobile_used_')};scope['mobile_used_handlers']=[key for key in scope['mobile_used_handlers'] if key not in HISTORY_WATCHES and not key.startswith(('hosted_room_','canonical_workspace_'))]
        scope['mobile_used_request_fields']=[x for x in scope['mobile_used_request_fields'] if x not in WORKSPACE_FIELDS]
        variants={k:v for k,v in value['reviewed_handler_variants'].items() if k not in HISTORY_WATCHES and not k.startswith('hosted_room_')}
        absences={k:v for k,v in value.get('reviewed_handler_absences',{}).items() if k not in HISTORY_WATCHES and not k.startswith('hosted_room_')}
        self.assertEqual(GOLDEN,dict(contract=self.digest(contract),scope=self.digest(scope),variants=self.digest(variants),absences=self.digest(absences)))
        self.assertEqual(218,len(checker.SOURCES|checker.EXTRA_SOURCES));self.assertEqual(16,len(aliases))
        self.assertFalse(checker.compare(value,dict(commit=value['upstream']['commit'],contract=copy.deepcopy(value['upstream']['contract'])))['mobile_review_required'])
    def test_original_and_reviewed_room_modes_require_every_dependency(self):
        value=self.baseline();guards={k:v for k,v in value['baseline_handler_source_guards'].items() if k not in HISTORY_WATCHES};self.assertEqual(14,len(guards))
        key='hosted_room_store:consumed_module';required=set(checker.HOSTED_ROOM_CONTRACT_SOURCES)|{'gateway_ws','canonical_controls','auth_native_http'}
        self.assertEqual(required,set(guards[key]))
        for target,entries in value['reviewed_handler_variants'].items():
            if target.startswith('hosted_room_'):
                for entry in entries:self.assertEqual(required,set(entry['required_sources']))
        modes=[dict(commit=value['upstream']['commit'],contract=copy.deepcopy(value['upstream']['contract']))]
        for entry in value['reviewed_handler_variants'][key]:
            current=dict(commit=entry['commit'],contract=copy.deepcopy(value['upstream']['contract']));current['contract']['handlers'][key]=entry['sha256'];current['contract']['source_hashes'].update(entry['required_sources']);modes.append(current)
        for current in modes:
            self.assertNotIn(key,checker.compare(value,current)['mobile_relevant_changes']['changed_used_handlers'])
            for source in required:
                for missing in (False,True):
                    bad=copy.deepcopy(current)
                    if missing:bad['contract']['source_hashes'].pop(source)
                    else:bad['contract']['source_hashes'][source]='0'*64
                    with self.subTest(commit=current['commit'],source=source,missing=missing):self.assertIn(key,checker.compare(value,bad)['mobile_relevant_changes']['changed_used_handlers'])
    def test_missing_room_sources_are_only_exact_tree_proven_historical_absences(self):
        expected={'hosted_room_peers':{'f0d94882e89ab8d4098b5bb2f166adfb9ee0a0ab','2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19','bf0203a794a07aac3ff764972ab9087baa4e4f4e'},'hosted_room_context':{'f0d94882e89ab8d4098b5bb2f166adfb9ee0a0ab','bf0203a794a07aac3ff764972ab9087baa4e4f4e'},'hosted_room_forward':{'bf0203a794a07aac3ff764972ab9087baa4e4f4e'},'hosted_room_gateway_import':{'bf0203a794a07aac3ff764972ab9087baa4e4f4e'}}
        for alias,pins in expected.items():
            path=checker.HOSTED_ROOM_CONTRACT_SOURCES[alias];self.assertEqual((path,frozenset(pins)),checker.REVIEWED_SOURCE_ABSENCES[alias])
            self.assertFalse(checker.reviewed_source_absence(alias,path,'e'*40));self.assertFalse(checker.reviewed_source_absence(alias,'wrong.py',next(iter(pins))))
    def test_room_permission_and_wire_binding_drift_is_parsed_without_execution(self):
        data=sources();alias='hosted_room_controls';data[alias]="raise RuntimeError('never execute upstream')\nGROUP_METHODS={'groups.list':'session:read'}\n"
        before=checker.extract(data)['handlers'][alias+':consumed_module'];data[alias]=data[alias].replace("'session:read'","'session:control'")
        self.assertNotEqual(before,checker.extract(data)['handlers'][alias+':consumed_module'])

if __name__=='__main__':unittest.main()
