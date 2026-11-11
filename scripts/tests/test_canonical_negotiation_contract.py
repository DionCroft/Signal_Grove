"""Consumed canonical negotiation contracts must retain strict types and exact source variants."""
import copy
import json
from pathlib import Path
import unittest
from test_hermes_contract import checker, sources


class CanonicalNegotiationContractTest(unittest.TestCase):
    def baseline(self):
        return json.loads((Path(__file__).resolve().parents[2]/'docs/contracts/hermes-baseline.json').read_text())

    def variant(self, key='canonical_controls:AuthorityConnection.dispatch'):
        value=self.baseline()
        entry=value['reviewed_handler_variants'][key][0]
        current={'commit':entry['commit'],'contract':copy.deepcopy(value['upstream']['contract'])}
        current['contract']['handlers'][key]=entry['sha256']
        current['contract']['source_hashes'].update(entry['required_sources'])
        return value,current,key,entry

    def test_historical_f0_contract_remains_self_consistent(self):
        value=self.baseline()
        current={'commit':value['upstream']['commit'],'contract':copy.deepcopy(value['upstream']['contract'])}
        self.assertFalse(checker.compare(value,current)['mobile_review_required'])
        self.assertEqual('f0d94882e89ab8d4098b5bb2f166adfb9ee0a0ab',value['upstream']['commit'])
        self.assertEqual(215,len(checker.SOURCES|checker.EXTRA_SOURCES))

    def test_guarded_dispatch_variant_needs_every_exact_source_dependency(self):
        value,current,key,entry=self.variant()
        self.assertNotIn(key,checker.compare(value,current)['mobile_relevant_changes']['changed_used_handlers'])
        for source in entry['required_sources']:
            for absent in (False,True):
                with self.subTest(source=source,absent=absent):
                    changed=copy.deepcopy(current)
                    if absent:changed['contract']['source_hashes'].pop(source)
                    else:changed['contract']['source_hashes'][source]='0'*64
                    self.assertIn(key,checker.compare(value,changed)['mobile_relevant_changes']['changed_used_handlers'])

    def test_unknown_dispatch_body_is_not_approved_by_known_module_names(self):
        value,current,key,entry=self.variant()
        current['contract']['handlers'][key]='0'*64
        self.assertIn(key,checker.compare(value,current)['mobile_relevant_changes']['changed_used_handlers'])

    def test_runtime_identity_types_and_descriptor_fields_remain_watched(self):
        value=self.baseline()
        for key in ('canonical_wire:RuntimeDescribeResult.instance_id','canonical_wire:RuntimeDescribeResult.profile_id',
                    'canonical_wire:RuntimeDescribeResult.authority_epoch','canonical_wire:RuntimeDescribeResult.capabilities',
                    'canonical_wire:RuntimeDescribeResult.session_create','canonical_wire:SessionCreateDescriptor.sources',
                    'canonical_wire:SessionCreateDescriptor.parameters'):
            with self.subTest(key=key):
                self.assertIn(key,value['mobile_used_request_fields'])
                current={'commit':value['upstream']['commit'],'contract':copy.deepcopy(value['upstream']['contract'])}
                current['contract']['request_fields'][key]='0'*64
                self.assertIn(key,checker.compare(value,current)['mobile_relevant_changes']['changed_used_request_fields'])

    def test_empty_params_schema_and_runtime_registration_are_selected_without_execution(self):
        data=sources()
        data['canonical_wire']="""raise RuntimeError('never execute public source')
class RuntimeDescribeParams(Params):
    pass
class RuntimeDescribeResult(Result):
    instance_id:str
class SessionCreateDescriptor(Result):
    sources:list[str]
canonical_method('runtime.describe',params=RuntimeDescribeParams,result=RuntimeDescribeResult,doc='fixture')
"""
        contract=checker.extract(data)
        for key in ('canonical_wire:schema:RuntimeDescribeParams','canonical_wire:schema:RuntimeDescribeResult',
                    'canonical_wire:schema:SessionCreateDescriptor','canonical_wire:registration:runtime.describe'):
            self.assertIn(key,contract['handlers'])
            self.assertIn(key,self.baseline()['mobile_used_handlers'])
        changed=copy.deepcopy(data);changed['canonical_wire']=data['canonical_wire'].replace('    pass','    profile:str')
        self.assertNotEqual(contract['handlers']['canonical_wire:schema:RuntimeDescribeParams'],
                            checker.extract(changed)['handlers']['canonical_wire:schema:RuntimeDescribeParams'])


if __name__=='__main__':
    unittest.main()
