"""Fail-closed coverage for source-bound retirement of local handler implementations."""
import copy
import json
from pathlib import Path
import unittest

from test_hermes_contract import baseline, checker, sources


class HandlerReplacementTest(unittest.TestCase):
    retired = 'signin_registry:_target'
    guards = ('signin_registry:binding:_registry', 'signin_registry:binding:_target',
              'signin_registry_engine:ProviderRegistry._target')

    def fixture(self):
        original = sources()
        original['signin_registry'] = "raise RuntimeError('Never execute upstream')\ndef _target(scope):\n    return scoped(scope)\n"
        original['signin_registry_engine'] = 'class ProviderRegistry:\n    def _target(self, scope):\n        return scoped(scope)\n'
        replaced = copy.deepcopy(original)
        replaced['signin_registry'] = "raise RuntimeError('Never execute upstream')\n_registry = ProviderRegistry()\n_target = _registry._target\n"
        current = checker.extract(replaced)
        tracked = baseline(checker.extract(original))
        tracked['mobile_used_handlers'] += [self.retired, *self.guards]
        tracked['reviewed_handler_variants'] = {
            key: [{'sha256': current['handlers'][key], 'commit': 'b' * 40,
                   'reason': 'Synthetic exact replacement binding'}]
            for key in self.guards if key.startswith('signin_registry:')
        }
        tracked['reviewed_handler_absences'] = {
            key: [{'commit': 'd' * 40, 'reason': 'Reviewed older implementation without this binding'}]
            for key in tracked['reviewed_handler_variants']
        }
        tracked['reviewed_handler_replacements'] = {self.retired: [{
            'commit': 'b' * 40,
            'reason': 'Synthetic source-bound local implementation replacement',
            'source_sha256': current['source_hashes']['signin_registry'],
            'replacements': {key: current['handlers'][key] for key in self.guards},
        }]}
        return tracked, original, replaced

    def compare(self, tracked, data, commit='b' * 40):
        return checker.compare(tracked, {'commit': commit, 'contract': checker.extract(data)})

    def test_original_and_complete_reviewed_replacement_are_accepted(self):
        tracked, original, replaced = self.fixture()
        self.assertFalse(self.compare(tracked, original, 'a' * 40)['mobile_review_required'])
        self.assertFalse(self.compare(tracked, original, 'd' * 40)['mobile_review_required'])
        for commit in ('b' * 40, 'e' * 40):
            result = self.compare(tracked, replaced, commit)
            self.assertFalse(result['mobile_review_required'])
            self.assertEqual([self.retired], result['reviewed_handler_replacements_applied'])
        self.assertTrue(self.compare(tracked, original, 'f' * 40)['mobile_review_required'])

    def test_changed_replacement_source_bytes_require_a_new_review(self):
        tracked, _, replaced = self.fixture()
        replaced['signin_registry'] += '# Source identity differs even with the same AST.\n'
        result = self.compare(tracked, replaced)
        self.assertTrue(result['mobile_review_required'])
        self.assertTrue(result['mobile_relevant_changes']['changed_used_handlers'][self.retired]['unreviewed_replacement_mode'])
        self.assertEqual([], result['reviewed_handler_replacements_applied'])

    def test_every_missing_or_changed_replacement_dependency_requires_review(self):
        tracked, _, replaced = self.fixture()
        for key in self.guards:
            for absent in (True, False):
                with self.subTest(key=key, absent=absent):
                    current = checker.extract(replaced)
                    if absent:
                        current['handlers'].pop(key)
                    else:
                        current['handlers'][key] = '0' * 64
                    result = checker.compare(tracked, {'commit': 'b' * 40, 'contract': current})
                    self.assertTrue(result['mobile_review_required'])
                    self.assertIn(self.retired, result['mobile_relevant_changes']['changed_used_handlers'])
                    self.assertEqual([], result['reviewed_handler_replacements_applied'])

    def test_missing_source_identity_cannot_authorize_retirement(self):
        tracked, _, replaced = self.fixture()
        current = checker.extract(replaced)
        current['source_hashes'].pop('signin_registry')
        result = checker.compare(tracked, {'commit': 'b' * 40, 'contract': current})
        self.assertTrue(result['mobile_review_required'])
        self.assertEqual([], result['reviewed_handler_replacements_applied'])

    def test_a_hybrid_cannot_reuse_the_original_function_fingerprint(self):
        tracked, original, replaced = self.fixture()
        replaced['signin_registry'] += original['signin_registry']
        current = checker.extract(replaced)
        self.assertEqual(tracked['upstream']['contract']['handlers'][self.retired], current['handlers'][self.retired])
        result = checker.compare(tracked, {'commit': 'b' * 40, 'contract': current})
        self.assertTrue(result['mobile_review_required'])
        self.assertTrue(result['mobile_relevant_changes']['changed_used_handlers'][self.retired]['unreviewed_replacement_mode'])

    def test_original_and_external_scope_changes_are_not_waived(self):
        tracked, original, replaced = self.fixture()
        original['signin_registry'] = original['signin_registry'].replace('scoped(scope)', 'global_provider()')
        self.assertTrue(self.compare(tracked, original, 'a' * 40)['mobile_review_required'])
        replaced['signin_registry_engine'] = replaced['signin_registry_engine'].replace('scoped(scope)', 'global_provider()')
        result = self.compare(tracked, replaced)
        self.assertTrue(result['mobile_review_required'])
        self.assertIn(self.retired, result['mobile_relevant_changes']['changed_used_handlers'])
        self.assertIn(self.guards[-1], result['mobile_relevant_changes']['changed_used_handlers'])

    def test_unconfigured_retirement_and_reintroduced_functions_are_rejected(self):
        tracked, _, replaced = self.fixture()
        unreviewed = copy.deepcopy(tracked)
        unreviewed.pop('reviewed_handler_replacements')
        self.assertTrue(self.compare(unreviewed, replaced)['mobile_review_required'])
        current = checker.extract(replaced)
        for digest in ('0' * 64, tracked['upstream']['contract']['handlers'][self.retired]):
            current['handlers'][self.retired] = digest
            result = checker.compare(tracked, {'commit': 'b' * 40, 'contract': current})
            self.assertTrue(result['mobile_review_required'])
            self.assertEqual([], result['reviewed_handler_replacements_applied'])

    def test_replacement_evidence_requires_pins_identity_and_watched_reviewed_guards(self):
        tracked, _, replaced = self.fixture()
        old_hash = tracked['upstream']['contract']['source_hashes']['signin_registry']
        mutations = [
            ('commit', 'main'), ('commit', 'a' * 40), ('commit', None),
            ('reason', ''), ('reason', None), ('source_sha256', '*'),
            ('source_sha256', old_hash), ('source_sha256', None),
            ('replacements', {}), ('replacements', []),
            ('replacements', {self.guards[0]: '0' * 64}),
            ('replacements', {self.retired: tracked['upstream']['contract']['handlers'][self.retired]}),
            ('replacements', {'unwatched:binding': '0' * 64}),
            ('replacements', {self.guards[-1]: tracked['upstream']['contract']['handlers'][self.guards[-1]]}),
        ]
        for field, value in mutations:
            with self.subTest(field=field, value=value):
                invalid = copy.deepcopy(tracked)
                invalid['reviewed_handler_replacements'][self.retired][0][field] = value
                with self.assertRaises(ValueError):
                    self.compare(invalid, replaced)
        for entries in ([], {}, ['invalid']):
            invalid = copy.deepcopy(tracked)
            invalid['reviewed_handler_replacements'][self.retired] = entries
            with self.assertRaises(ValueError):
                self.compare(invalid, replaced)
        invalid = copy.deepcopy(tracked)
        invalid['reviewed_handler_replacements']['unwatched:handler'] = invalid['reviewed_handler_replacements'].pop(self.retired)
        with self.assertRaises(ValueError):
            self.compare(invalid, replaced)
        invalid = copy.deepcopy(tracked)
        invalid['reviewed_handler_replacements'] = []
        with self.assertRaises(ValueError):
            self.compare(invalid, replaced)


    def test_real_baseline_retains_original_engine_and_selects_transitive_guards(self):
        value = json.loads((Path(__file__).resolve().parents[2] / 'docs/contracts/hermes-baseline.json').read_text(encoding='utf-8'))
        from test_hermes_contract import plugin_refusal_historical_projection
        value = plugin_refusal_historical_projection(value)
        self.assertEqual('f0d94882e89ab8d4098b5bb2f166adfb9ee0a0ab', value['upstream']['commit'])
        self.assertEqual('agent/provider_registry.py', checker.RUNTIME_GUARD_CONTRACT_SOURCES['signin_registry_engine'])
        self.assertEqual('c799942889fddcb69851f2872c30fe439b6903b337ea2bcbeab56245fc4a5b7a', value['upstream']['contract']['handlers']['signin_registry_engine:ProviderRegistry.__init__'])
        self.assertEqual({'signin_registry:_target', 'signin_registry:import:threading', 'signin_registry:snapshot_registration', 'signin_registry:restore_registration'}, set(value['reviewed_handler_replacements']))
        required = {'signin_registry_engine:' + key for key in (
            'ProviderRegistry.__init__', 'ProviderRegistry._target', 'ProviderRegistry._bump',
            'ProviderRegistry.snapshot_registration', 'ProviderRegistry.restore_registration',
            'exact_key', 'import:threading', 'import:hermes_home_key', 'import:normalize_scope')}
        self.assertTrue(required <= set(value['mobile_used_handlers']))
        for entries in value['reviewed_handler_replacements'].values():
            self.assertEqual(1, len(entries))
            self.assertEqual('e36d4e57359074d0f9e8ccc8981bca16b893ad7f', entries[0]['commit'])
            self.assertEqual('bdc657b049497103863c5c49c0ceb6d98789b3a699ffda22ea2509c043cfd492', entries[0]['source_sha256'])
            self.assertGreaterEqual(len(entries[0]['replacements']), 4)


if __name__ == '__main__':
    unittest.main()
