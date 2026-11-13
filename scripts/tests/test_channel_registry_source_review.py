"""Exact current channel registry context; original modes and other findings remain guarded."""
import copy
import json
from pathlib import Path
import unittest
from test_hermes_contract import checker, complete_replacement_fixture, profile_ticket_historical_projection, project_sessions_historical_projection

COMMIT='3b421578588243f920886e6fe79a6faa87ea026e'
GUARDS={'channel_catalog': 'dee21f5b9b5d38ba8e6d811648baf20fe16125870d0466d289848d504c6b2baa',
 'channel_config': '711847b5f740a5131bd2772bc8d737f94ab549faab8d8de18befe8202dbe7dd7',
 'channel_registry': '05103cf65cf74935afa5660b140f012f22255a0b764b6c99c6c680a7a0d7f261',
 'env_config': 'bdd14e65866864bbb6afad73fa3b2f4ba69097e783ac3cda18ac374dca33b763',
 'plugin_legacy': '9e04fcd9a118c9a386c694ad72195e28253bfe2aac349cc6978d1cf3b6aa53da'}
HANDLERS={'channel_catalog:binding:_PLATFORM_ORDER': '63dbf1f2276f0f75c55b39bc5dd3aa1ef313fd012d9048c4b9c1263503816196',
 'channel_config:schema:Platform': '403dec3b589fa496cddb44a12f96e68e54d89adcee285ea69efad552f22cf39c',
 'channel_registry:schema:PlatformEntry': 'd3d2d041558be760053d8ea0daf3b971deeaadc283556687eb2a0959519395b1',
 'env_config:_worker_config_snapshot': '3e194951f7a604bd3d9658ceb8cf00c9260c5bdedca90e3e3f2ce64a82d7e6d5',
 'plugin_legacy:LEFT_CORE': '55a7d0ed11c93c1957137b060a359f256be16f53c182fe34ed3a3d7845aa9bf2',
 'plugin_legacy:legacy_plugin_alias': 'f919efd87703b3bd268ff84a9231a4b123eec59c1e232087c894ed970e398240',
 'plugin_legacy:legacy_plugin_keys': 'c35bc9263c6cb04729f9761f398422c357a8d2b0347e0e5a4bb20d1d657c302e'}

class ChannelRegistrySourceReviewTest(unittest.TestCase):
    def fixture(self):
        baseline=json.loads((Path(__file__).resolve().parents[2]/'docs/contracts/hermes-baseline.json').read_text(encoding='utf8'))
        # Use the established complete synthetic historical authority mode. The
        # actual stable source snapshot is re-compared separately, retaining 40
        # unrelated findings; this fixture isolates the channel guard behavior.
        baseline=profile_ticket_historical_projection(project_sessions_historical_projection(baseline))
        contract=copy.deepcopy(baseline['upstream']['contract'])
        for key,entries in baseline['reviewed_handler_variants'].items():
            if key not in contract['handlers']:contract['handlers'][key]=entries[-1]['sha256']
        complete_replacement_fixture(baseline,contract)
        contract['source_hashes'].update(GUARDS)
        contract['handlers'].update(HANDLERS)
        return baseline,dict(commit=COMMIT,contract=contract)

    def assert_only_unreviewed_registry_context(self,report):
        # The seven reviewed variants do not approve the unchanged parent bodies
        # in a different full registry module. Keep those two new findings open.
        self.assertTrue(report['mobile_review_required'])
        findings=report['mobile_relevant_changes']['changed_used_handlers']
        self.assertEqual({'channel_registry:PlatformRegistry._resolve','channel_registry:PlatformRegistry.configured_entries'},set(findings))
        self.assertTrue(all(value['original_source_context_changed'] and value['before']==value['after'] for value in findings.values()))

    def test_exact_seven_variants_require_all_five_reviewed_complete_sources(self):
        baseline,current=self.fixture()
        entries={key:entry for key,values in baseline['reviewed_handler_variants'].items() if key in HANDLERS for entry in values if entry['commit']==COMMIT}
        self.assertEqual(set(HANDLERS),set(entries))
        for key,entry in entries.items():
            self.assertEqual(HANDLERS[key],entry['sha256'])
            self.assertEqual(GUARDS,entry['required_sources'])
            self.assertIn('2026-11-12-channel-registry-context-review.md',entry['reason'])
        report=checker.compare(baseline,current)
        self.assert_only_unreviewed_registry_context(report)
        self.assertTrue(set(HANDLERS)<=set(report['reviewed_source_guarded_variants_applied']))

    def test_each_missing_or_changed_dependency_reopens_all_seven_findings(self):
        baseline,current=self.fixture()
        for source in GUARDS:
            for missing in (True,False):
                altered=copy.deepcopy(current)
                if missing:altered['contract']['source_hashes'].pop(source)
                else:altered['contract']['source_hashes'][source]='0'*64
                with self.subTest(source=source,missing=missing):
                    report=checker.compare(baseline,altered)
                    self.assertTrue(report['mobile_review_required'])
                    self.assertTrue(set(HANDLERS)<=set(report['mobile_relevant_changes']['changed_used_handlers']))

    def test_same_review_commit_cannot_waive_unknown_or_missing_handler_body(self):
        baseline,current=self.fixture()
        for key in HANDLERS:
            for missing in (True,False):
                altered=copy.deepcopy(current)
                if missing:altered['contract']['handlers'].pop(key)
                else:altered['contract']['handlers'][key]='0'*64
                with self.subTest(handler=key,missing=missing):
                    report=checker.compare(baseline,altered)
                    self.assertTrue(report['mobile_review_required'])
                    self.assertIn(key,report['mobile_relevant_changes']['changed_used_handlers'])

    def test_commit_label_is_provenance_and_not_authorization(self):
        baseline,current=self.fixture()
        current['commit']='c'*40
        foreign_label=checker.compare(baseline,current)
        self.assert_only_unreviewed_registry_context(foreign_label)
        current['commit']=COMMIT
        self.assertEqual(foreign_label['mobile_relevant_changes'],checker.compare(baseline,current)['mobile_relevant_changes'])
        current['contract']['source_hashes']['env_config']='c'*64
        report=checker.compare(baseline,current)
        self.assertTrue(report['mobile_review_required'])
        self.assertTrue(set(HANDLERS)<=set(report['mobile_relevant_changes']['changed_used_handlers']))

    def test_unrelated_canonical_drift_stays_open_with_complete_channel_review(self):
        baseline,current=self.fixture()
        key='canonical_controls:AuthorityConnection.dispatch'
        self.assertIn(key,baseline['mobile_used_handlers'])
        current['contract']['handlers'][key]='0'*64
        report=checker.compare(baseline,current)
        self.assertTrue(report['mobile_review_required'])
        self.assertIn(key,report['mobile_relevant_changes']['changed_used_handlers'])
        self.assertTrue(set(HANDLERS)<=set(report['reviewed_source_guarded_variants_applied']))

    def test_original_f0_and_exact_historical_modes_are_retained(self):
        baseline,current=self.fixture()
        self.assertEqual('f0d94882e89ab8d4098b5bb2f166adfb9ee0a0ab',baseline['upstream']['commit'])
        old=dict(commit=baseline['upstream']['commit'],contract=copy.deepcopy(baseline['upstream']['contract']))
        self.assertFalse(checker.compare(baseline,old)['mobile_review_required'])
        for key in set(HANDLERS)-{'channel_registry:schema:PlatformEntry'}:
            values=baseline['reviewed_handler_variants'][key]
            historical={v['commit']:v for v in values if v['commit'] in {'981791d9d41fd2efde27f563873ce8c48ef1dac6','d4e7c3434209861f5cc6da0649e2df1f11f53e73'}}
            self.assertEqual(2,len(historical))
            self.assertTrue(all(set(v['required_sources'])==set(GUARDS)-{'channel_registry'} for v in historical.values()))

if __name__=='__main__':unittest.main()
