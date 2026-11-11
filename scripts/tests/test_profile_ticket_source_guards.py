"""Consumed profile ticket modules must retain exact coherent dependency contexts."""
import copy
import json
from pathlib import Path
import unittest
from test_hermes_contract import baseline,checker,sources,profile_ticket_historical_projection

class ProfileTicketSourceGuardsTest(unittest.TestCase):
    aliases=('profile_socket_routes','profile_socket_surface','profile_socket_kinds')
    def fixture(self):
        data=sources()
        data[self.aliases[0]]="raise RuntimeError('never execute upstream')\n@router.post('/api/webapp/socket-ticket')\ndef issue_socket_ticket(profile=None):\n    return bind(profile)\n"
        data[self.aliases[1]]="raise RuntimeError('never execute upstream')\nclass SurfacePolicy:\n    gateway_ticket=True\n"
        data[self.aliases[2]]="raise RuntimeError('never execute upstream')\nGATEWAY_SOCKET='gateway'\n"
        value=baseline(checker.extract(data))
        for alias in self.aliases:
            key=alias+':consumed_module';value['mobile_used_handlers'].append(key);value.setdefault('baseline_handler_source_guards',{})[key]=list(self.aliases)
        value['mobile_used_management_routes'].append('POST /api/webapp/socket-ticket')
        return value,data
    def compare(self,value,data):return checker.compare(value,dict(commit='b'*40,contract=checker.extract(data)))
    def variants(self,value,data):
        changed=copy.deepcopy(data)
        for alias in self.aliases:changed[alias]+="\n# Explicit reviewed source context.\n"
        contract=checker.extract(changed)
        for alias in self.aliases:
            key=alias+':consumed_module';value.setdefault('reviewed_handler_variants',{})[key]=[dict(sha256=contract['handlers'][key],commit='b'*40,reason='Coherent synthetic context; never execute upstream.',required_sources={a:contract['source_hashes'][a] for a in self.aliases})]
        return changed
    def test_original_and_explicit_complete_contexts_are_accepted(self):
        value,data=self.fixture();self.assertFalse(self.compare(value,data)['mobile_review_required']);changed=self.variants(value,data);self.assertFalse(self.compare(value,changed)['mobile_review_required'])
    def test_route_policy_and_socket_kind_changes_each_require_review(self):
        value,data=self.fixture()
        for alias,before,after in [(self.aliases[0],'bind(profile)','bind(None)'),(self.aliases[1],'gateway_ticket=True','gateway_ticket=False'),(self.aliases[2],"'gateway'","'host-terminal'")]:
            with self.subTest(alias=alias):
                changed={**data,alias:data[alias].replace(before,after)};self.assertIn(alias+':consumed_module',self.compare(value,changed)['mobile_relevant_changes']['changed_used_handlers'])
    def test_byte_only_change_cannot_hide_behind_identical_module_ast(self):
        value,data=self.fixture();changed={**data,self.aliases[1]:data[self.aliases[1]]+'\n# Unreviewed.\n'};self.assertTrue(self.compare(value,changed)['mobile_review_required'])
    def test_mixed_original_and_reviewed_contexts_are_refused(self):
        value,data=self.fixture();changed=self.variants(value,data);changed[self.aliases[2]]=data[self.aliases[2]];self.assertTrue(self.compare(value,changed)['mobile_review_required'])
    def test_missing_dependency_guard_is_not_treated_as_a_complete_review(self):
        value,data=self.fixture();changed=self.variants(value,data);value['reviewed_handler_variants'][self.aliases[0]+':consumed_module'][0]['required_sources'].pop(self.aliases[2])
        with self.assertRaises(ValueError):self.compare(value,changed)
    def test_missing_module_or_route_decorator_requires_review(self):
        value,data=self.fixture()
        for text in ('',data[self.aliases[0]].replace("@router.post('/api/webapp/socket-ticket')\n",'')):
            with self.subTest(text=text):
                result=self.compare(value,{**data,self.aliases[0]:text});self.assertTrue(result['mobile_review_required']);self.assertIn('POST /api/webapp/socket-ticket',result['mobile_relevant_changes']['removed_used_management_routes'])

class ProductionProfileTicketModesTest(unittest.TestCase):
    def value(self):return json.loads((Path(__file__).resolve().parents[2]/'docs/contracts/hermes-baseline.json').read_text())
    def test_three_direct_sources_and_two_complete_execution_pin_modes_are_explicit(self):
        value=self.value();self.assertEqual({'profile_socket_routes','profile_socket_surface','profile_socket_kinds'},set(checker.PROFILE_TICKET_CONTRACT_SOURCES))
        pins={'2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19','3b421578588243f920886e6fe79a6faa87ea026e'}
        for alias in checker.PROFILE_TICKET_CONTRACT_SOURCES:
            key=alias+':consumed_module';self.assertEqual(pins,{entry['commit'] for entry in value['reviewed_handler_variants'][key]});self.assertEqual(11,len(value['baseline_handler_source_guards'][key]))
    def test_every_real_mode_dependency_is_required_without_claiming_whole_pin_compatibility(self):
        value=self.value();keys={a+':consumed_module' for a in checker.PROFILE_TICKET_CONTRACT_SOURCES}
        for pin in ('2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19','3b421578588243f920886e6fe79a6faa87ea026e'):
            contract=copy.deepcopy(value['upstream']['contract'])
            for key in keys:
                entry=next(e for e in value['reviewed_handler_variants'][key] if e['commit']==pin);contract['handlers'][key]=entry['sha256'];contract['source_hashes'].update(entry['required_sources'])
            current=dict(commit=pin,contract=contract);changed=checker.compare(value,current)['mobile_relevant_changes']['changed_used_handlers'];self.assertFalse(keys & set(changed))
            for dependency in entry['required_sources']:
                with self.subTest(pin=pin,dependency=dependency):
                    bad=copy.deepcopy(current);bad['contract']['source_hashes'][dependency]='0'*64;findings=checker.compare(value,bad)['mobile_relevant_changes']['changed_used_handlers'];self.assertTrue(keys <= set(findings))
    def test_historical_projection_preserves_every_unrelated_value_and_does_not_mutate_production(self):
        value=self.value();before=copy.deepcopy(value);old=profile_ticket_historical_projection(value);self.assertEqual(before,value)
        aliases=set(checker.PROFILE_TICKET_CONTRACT_SOURCES)
        for section in ('source_hashes','handlers','request_fields'):
            for key,digest in value['upstream']['contract'][section].items():
                if key.split(':',1)[0] not in aliases:self.assertEqual(digest,old['upstream']['contract'][section][key])
        for section in ('baseline_handler_source_guards','reviewed_handler_variants','reviewed_handler_absences'):
            for key,entry in value.get(section,{}).items():
                if key.split(':',1)[0] not in aliases:self.assertEqual(entry,old.get(section,{})[key])

if __name__=='__main__':unittest.main()