"""Real canonical stream helpers remain parsed-only and protected by complete reviewed vectors."""
import copy
import hashlib
import json
from pathlib import Path
import unittest
from test_hermes_contract import checker, sources

STREAM_ALIASES=('canonical_event_stream','canonical_replay_store','canonical_transport','canonical_event_payloads')
OLD_F0_CONTRACT_SHA256='3bf0ff78e20bc88da8ded497caea02b0e09752ce627f985f946f90fa7644355d'
OLD_MOBILE_SCOPE_SHA256='3d4ade32df8379b55912aadeeb2e9d0366a24abdd5a9bbc7347feb5193d0f0cd'

class CanonicalStreamContractTest(unittest.TestCase):
    def baseline(self):return json.loads((Path(__file__).resolve().parents[2]/'docs/contracts/hermes-baseline.json').read_text())
    def digest(self,value):return hashlib.sha256(json.dumps(value,sort_keys=True,separators=(',',':'),ensure_ascii=True).encode()).hexdigest()

    def test_every_original_f0_entry_and_mobile_scope_is_preserved(self):
        b=self.baseline();contract=copy.deepcopy(b['upstream']['contract'])
        for section in ('source_hashes','handlers','request_fields'):
            contract[section]={k:v for k,v in contract[section].items() if not (k in STREAM_ALIASES or any(k.startswith(a+':') for a in STREAM_ALIASES))}
        self.assertEqual(OLD_F0_CONTRACT_SHA256,self.digest(contract))
        scope={k:copy.deepcopy(v) for k,v in b.items() if k.startswith('mobile_used_')}
        scope['mobile_used_handlers']=[x for x in scope['mobile_used_handlers'] if x not in {a+':consumed_module' for a in STREAM_ALIASES}]
        self.assertEqual(OLD_MOBILE_SCOPE_SHA256,self.digest(scope))

    def test_expanded_baseline_compares_to_itself_and_watches_all_four_helpers(self):
        b=self.baseline();self.assertEqual(189,len(checker.SOURCES|checker.EXTRA_SOURCES))
        self.assertFalse(checker.compare(b,{'commit':b['upstream']['commit'],'contract':copy.deepcopy(b['upstream']['contract'])})['mobile_review_required'])
        for alias in STREAM_ALIASES:self.assertIn(alias+':consumed_module',b['mobile_used_handlers'])

    def test_helper_source_is_never_executed_and_body_changes_are_detected(self):
        for alias in STREAM_ALIASES:
            with self.subTest(alias=alias):
                data=sources();data[alias]="raise RuntimeError('never execute upstream')\ndef helper():\n    return 1\n"
                before=checker.extract(data);data[alias]=data[alias].replace('return 1','return 2');after=checker.extract(data)
                self.assertNotEqual(before['handlers'][alias+':consumed_module'],after['handlers'][alias+':consumed_module'])

    def test_one_complete_exact_source_vector_is_required(self):
        b=self.baseline()
        for alias in ('canonical_event_stream','canonical_event_payloads'):
            key=alias+':consumed_module'
            for entry in b['reviewed_handler_variants'][key]:
                current={'commit':entry['commit'],'contract':copy.deepcopy(b['upstream']['contract'])}
                current['contract']['handlers'][key]=entry['sha256'];current['contract']['source_hashes'].update(entry['required_sources'])
                self.assertNotIn(key,checker.compare(b,current)['mobile_relevant_changes']['changed_used_handlers'])
                self.assertEqual(7,len(entry['required_sources']))
                for source in entry['required_sources']:
                    for missing in (False,True):
                        bad=copy.deepcopy(current)
                        if missing:bad['contract']['source_hashes'].pop(source)
                        else:bad['contract']['source_hashes'][source]='0'*64
                        with self.subTest(alias=alias,source=source,missing=missing):
                            self.assertIn(key,checker.compare(b,bad)['mobile_relevant_changes']['changed_used_handlers'])

    def test_private_event_registration_and_payload_field_changes_are_visible(self):
        data=sources();alias='canonical_event_payloads'
        data[alias]="class StreamDeltaPayload:\n    text:str\nevent('reasoning.delta',StreamDeltaPayload)\n"
        original=checker.extract(data)['handlers'][alias+':consumed_module']
        for changed in (data[alias].replace('text:str','text:int'),data[alias].replace('reasoning.delta','unknown.delta')):
            data[alias]=changed;self.assertNotEqual(original,checker.extract(data)['handlers'][alias+':consumed_module'])

if __name__=='__main__':unittest.main()
