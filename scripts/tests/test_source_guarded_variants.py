"""Whole-source dependency fences around reviewed handler variants."""
import copy
import json
from pathlib import Path
import unittest

from test_hermes_contract import baseline, checker, sources


class SourceGuardedVariantTest(unittest.TestCase):
    key = 'mcp:set_mcp_server_enabled'

    def fixture(self):
        original = sources()
        changed = dict(original)
        changed['mcp'] = changed['mcp'].replace("return {'ok':True}", "return {'ok':False}")
        changed['env_config'] = "raise RuntimeError('Never execute upstream')\ndef helper():\n    return 'scoped'\n"
        current = checker.extract(changed)
        value = baseline(checker.extract(original))
        value['reviewed_handler_variants'] = {self.key: [{
            'sha256': current['handlers'][self.key], 'commit': 'b' * 40,
            'reason': 'Synthetic review binds the handler and its transitive module',
            'required_sources': {name: current['source_hashes'][name] for name in ('mcp', 'env_config')},
        }]}
        return value, original, changed

    def compare(self, value, data, commit='b' * 40):
        return checker.compare(value, {'commit': commit, 'contract': checker.extract(data)})

    def test_original_and_complete_guarded_variant_are_distinct_accepted_modes(self):
        value, original, changed = self.fixture()
        result = self.compare(value, original, 'a' * 40)
        self.assertFalse(result['mobile_review_required'])
        self.assertEqual([], result['reviewed_source_guarded_variants_applied'])
        for commit in ('b' * 40, 'c' * 40):
            result = self.compare(value, changed, commit)
            self.assertFalse(result['mobile_review_required'])
            self.assertEqual([self.key], result['reviewed_source_guarded_variants_applied'])

    def test_each_missing_or_changed_source_identity_requires_review(self):
        value, _, changed = self.fixture()
        for source in ('mcp', 'env_config'):
            for missing in (True, False):
                with self.subTest(source=source, missing=missing):
                    current = checker.extract(changed)
                    if missing:
                        current['source_hashes'].pop(source)
                    else:
                        current['source_hashes'][source] = '0' * 64
                    result = checker.compare(value, {'commit': 'b' * 40, 'contract': current})
                    self.assertTrue(result['mobile_review_required'])
                    self.assertIn(self.key, result['mobile_relevant_changes']['changed_used_handlers'])
                    self.assertEqual([], result['reviewed_source_guarded_variants_applied'])

    def test_same_handler_ast_with_different_own_or_helper_bytes_requires_review(self):
        value, _, changed = self.fixture()
        expected = checker.extract(changed)['handlers'][self.key]
        for source in ('mcp', 'env_config'):
            for suffix in ('\n# Different bytes, same AST.\n', "\ndef untracked_helper():\n    return 'changed scope'\n"):
                with self.subTest(source=source, suffix=suffix):
                    altered = dict(changed)
                    altered[source] += suffix
                    self.assertEqual(expected, checker.extract(altered)['handlers'][self.key])
                    result = self.compare(value, altered)
                    self.assertTrue(result['mobile_review_required'])
                    self.assertEqual([], result['reviewed_source_guarded_variants_applied'])

    def test_matching_modules_cannot_accept_changed_or_missing_watched_ast(self):
        value, _, changed = self.fixture()
        for missing in (True, False):
            current = checker.extract(changed)
            if missing:
                current['handlers'].pop(self.key)
            else:
                current['handlers'][self.key] = '0' * 64
            result = checker.compare(value, {'commit': 'b' * 40, 'contract': current})
            self.assertTrue(result['mobile_review_required'])
            self.assertEqual([], result['reviewed_source_guarded_variants_applied'])

    def test_same_ast_requires_one_complete_review_mode_without_mixing_sources(self):
        value, _, first = self.fixture()
        second = dict(first)
        second['mcp'] += '\n# Second reviewed source identity.\n'
        second['env_config'] += '\n# Second reviewed helper identity.\n'
        current = checker.extract(second)
        entry = copy.deepcopy(value['reviewed_handler_variants'][self.key][0])
        entry['commit'] = 'c' * 40
        entry['required_sources'] = {k: current['source_hashes'][k] for k in ('mcp', 'env_config')}
        self.assertEqual(entry['sha256'], current['handlers'][self.key])
        value['reviewed_handler_variants'][self.key].append(entry)
        for data in (first, second):
            self.assertFalse(self.compare(value, data)['mobile_review_required'])
        for source in ('mcp', 'env_config'):
            hybrid = dict(first)
            hybrid[source] = second[source]
            with self.subTest(source=source):
                result = self.compare(value, hybrid)
                self.assertTrue(result['mobile_review_required'])
                self.assertEqual([], result['reviewed_source_guarded_variants_applied'])

    def test_legacy_unguarded_evidence_retains_its_behavior(self):
        value, _, changed = self.fixture()
        value['reviewed_handler_variants'][self.key][0].pop('required_sources')
        changed['env_config'] += '\n# Not part of this synthetic historical review.\n'
        result = self.compare(value, changed)
        self.assertFalse(result['mobile_review_required'])
        self.assertEqual([], result['reviewed_source_guarded_variants_applied'])

    def test_unrelated_module_change_is_not_mislabeled_as_a_guard_failure(self):
        value, _, changed = self.fixture()
        changed['skills'] += '\n# Outside the two deliberately selected guards.\n'
        result = self.compare(value, changed)
        self.assertFalse(result['mobile_review_required'])
        self.assertEqual([self.key], result['reviewed_source_guarded_variants_applied'])
        self.assertIn('skills', result['changed_source_modules'])

    def test_malformed_empty_unknown_missing_own_and_oversized_guards_are_rejected(self):
        value, _, changed = self.fixture()
        identities = value['upstream']['contract']['source_hashes']
        cases = [None, [], {}, {'env_config': '0' * 64}, {'mcp': '*'}, {'mcp': None},
                 {'mcp': 'A' * 64}, {'mcp': '0' * 64, 'unknown_source': '0' * 64},
                 {'mcp': '0' * 64, 17: '0' * 64},
                 {'mcp': '0' * 64, **{k: '0' * 64 for k in list(identities)[:33]}}]
        for guards in cases:
            with self.subTest(guards=guards):
                invalid = copy.deepcopy(value)
                invalid['reviewed_handler_variants'][self.key][0]['required_sources'] = guards
                with self.assertRaises(ValueError):
                    self.compare(invalid, changed)

    def test_misspelled_or_unknown_entry_keys_cannot_silently_disable_guards(self):
        value, _, changed = self.fixture()
        for typo in ('required_source', 'require_sources', 'unknown_flag'):
            invalid = copy.deepcopy(value)
            entry = invalid['reviewed_handler_variants'][self.key][0]
            entry[typo] = entry.pop('required_sources')
            with self.subTest(typo=typo), self.assertRaises(ValueError):
                self.compare(invalid, changed)
        invalid = copy.deepcopy(value)
        invalid['reviewed_handler_variants'][self.key][0]['ignore_source_drift'] = True
        with self.assertRaises(ValueError):
            self.compare(invalid, changed)

    def test_yuanbao_review_requires_every_complete_module_and_preserves_f0(self):
        value = json.loads((Path(__file__).resolve().parents[2] / 'docs/contracts/hermes-baseline.json').read_text(encoding='utf-8'))
        self.assertEqual('f0d94882e89ab8d4098b5bb2f166adfb9ee0a0ab', value['upstream']['commit'])
        expected_sources = {
            'channel_catalog': 'dee21f5b9b5d38ba8e6d811648baf20fe16125870d0466d289848d504c6b2baa',
            'channel_config': '711847b5f740a5131bd2772bc8d737f94ab549faab8d8de18befe8202dbe7dd7',
            'env_config': 'f50d79e1ca5f051a78e51b162d0e88c27443acb3f6eaf1cd02074fad3b9aed5d',
            'plugin_legacy': '9e04fcd9a118c9a386c694ad72195e28253bfe2aac349cc6978d1cf3b6aa53da',
        }
        keys = {'channel_catalog:binding:_PLATFORM_ORDER', 'channel_config:schema:Platform',
                'env_config:_worker_config_snapshot', 'plugin_legacy:LEFT_CORE',
                'plugin_legacy:legacy_plugin_alias', 'plugin_legacy:legacy_plugin_keys'}
        reviewed = {key: entry for key, entries in value['reviewed_handler_variants'].items()
                    for entry in entries if entry['commit'] == '981791d9d41fd2efde27f563873ce8c48ef1dac6'}
        self.assertEqual(keys, set(reviewed))
        for key, entry in reviewed.items():
            self.assertEqual(expected_sources, entry['required_sources'], key)
            self.assertNotEqual(value['upstream']['contract']['handlers'][key], entry['sha256'])
            self.assertIn(key, value['mobile_used_handlers'])
            same_ast = [v for v in value['reviewed_handler_variants'][key] if v['sha256'] == entry['sha256']]
            self.assertEqual(3, len(same_ast))
            modes = {v['commit']: v for v in same_ast}
            self.assertEqual({'981791d9d41fd2efde27f563873ce8c48ef1dac6', 'd4e7c3434209861f5cc6da0649e2df1f11f53e73', '3b421578588243f920886e6fe79a6faa87ea026e'}, set(modes))
            next_sources = dict(expected_sources)
            next_sources['env_config'] = 'ac6677722a209d62a602fe6d3140abdd394502a9c2926447dc194f9cd2a35166'
            self.assertEqual(next_sources, modes['d4e7c3434209861f5cc6da0649e2df1f11f53e73']['required_sources'])
            self.assertTrue(all(set(modes[c]['required_sources']) == set(expected_sources) for c in ('981791d9d41fd2efde27f563873ce8c48ef1dac6', 'd4e7c3434209861f5cc6da0649e2df1f11f53e73')))
            current_sources=dict(expected_sources,env_config='bdd14e65866864bbb6afad73fa3b2f4ba69097e783ac3cda18ac374dca33b763',channel_registry='05103cf65cf74935afa5660b140f012f22255a0b764b6c99c6c680a7a0d7f261')
            self.assertEqual(current_sources,modes['3b421578588243f920886e6fe79a6faa87ea026e']['required_sources'])


if __name__ == '__main__':
    unittest.main()
