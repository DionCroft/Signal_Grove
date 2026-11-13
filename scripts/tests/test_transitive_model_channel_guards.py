"""Changed helpers must reopen review even when their watched caller is identical."""
import copy
import json
from pathlib import Path
import unittest
from test_hermes_contract import checker


class TransitiveModelChannelGuardTest(unittest.TestCase):
    def fixture(self):
        baseline=json.loads((Path(__file__).resolve().parents[2]/'docs/contracts/hermes-baseline.json').read_text(encoding='utf8'))
        current=dict(commit=baseline['upstream']['commit'],contract=copy.deepcopy(baseline['upstream']['contract']))
        return baseline,current

    def targets(self):
        # These are the consumed callers under review, independent of whether
        # a future policy edit accidentally removes one of their source guards.
        callers=('channel_env_loader:_apply_external_secret_sources',
                 'channel_env_loader:_publish_staged_env',
                 'channel_registry:PlatformRegistry._resolve',
                 'channel_registry:PlatformRegistry.configured_entries',
                 'model_catalog:_lap_bare_custom_row',
                 'model_catalog:_lap_custom_provider_rows',
                 'model_catalog:_lap_user_provider_rows',
                 'model_catalog:_save_discovered_models_to_config',
                 'pool_routes:add_credential_pool_entry')
        return {key:[key.split(':',1)[0]] for key in callers}

    def test_helper_drift_reopens_review_while_parent_body_remains_original(self):
        baseline,current=self.fixture();targets=self.targets()
        for key,sources in targets.items():
            for source in sources:
                altered=copy.deepcopy(current);altered['contract']['source_hashes'][source]='0'*64
                with self.subTest(handler=key,dependency=source):
                    self.assertEqual(baseline['upstream']['contract']['handlers'][key],altered['contract']['handlers'][key])
                    report=checker.compare(baseline,altered);finding=report['mobile_relevant_changes']['changed_used_handlers'][key]
                    self.assertTrue(report['mobile_review_required']);self.assertTrue(finding['original_source_context_changed']);self.assertEqual(finding['before'],finding['after'])

    def test_missing_source_cannot_be_accepted_from_an_unchanged_parent_fingerprint(self):
        baseline,current=self.fixture()
        for key,sources in self.targets().items():
            for source in sources:
                altered=copy.deepcopy(current);altered['contract']['source_hashes'].pop(source)
                with self.subTest(handler=key,dependency=source):
                    report=checker.compare(baseline,altered);self.assertTrue(report['mobile_review_required']);self.assertIn(key,report['mobile_relevant_changes']['changed_used_handlers'])

    def test_original_context_remains_valid_but_changed_parent_is_still_reviewed(self):
        baseline,current=self.fixture();self.assertFalse(checker.compare(baseline,current)['mobile_review_required'])
        for key in self.targets():
            altered=copy.deepcopy(current);altered['contract']['handlers'][key]='0'*64
            with self.subTest(handler=key):
                report=checker.compare(baseline,altered);self.assertTrue(report['mobile_review_required']);self.assertIn(key,report['mobile_relevant_changes']['changed_used_handlers'])


if __name__=='__main__':unittest.main()
