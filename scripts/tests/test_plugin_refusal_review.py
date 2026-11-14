"""The exact plugin review preserves the entire prior metadata and refuses drift."""
import copy
import hashlib
import json
from pathlib import Path
import unittest
from test_hermes_contract import checker, plugin_refusal_historical_projection

OLD='gateway_rpc:_source_moved_data'
NEW='gateway_rpc:_refusal_data'
CALLER='gateway_rpc:_plugins_install'
COMMIT='3b421578588243f920886e6fe79a6faa87ea026e'
CONTEXTS={'gateway_rpc','plugin_core','plugin_install','plugin_catalog_gate',
          'plugin_catalog_loader','plugin_transaction','plugin_isolation',
          'plugin_legacy','plugin_auto_update','plugin_git'}


class PluginRefusalReviewTest(unittest.TestCase):
    def value(self):
        return json.loads((Path(__file__).resolve().parents[2]/'docs/contracts/hermes-baseline.json').read_text(encoding='utf8'))

    def test_additive_review_preserves_the_entire_original_metadata_vector(self):
        value=self.value();before=plugin_refusal_historical_projection(value)
        digest=hashlib.sha256(json.dumps(before,sort_keys=True,separators=(',',':'),ensure_ascii=True).encode()).hexdigest()
        self.assertEqual('635d2dec0c793d2379c332ab954c0eabf44706574430f1d6f48b89153502f9f2',digest)
        self.assertEqual(before['upstream'],value['upstream'])
        self.assertEqual(before['mobile_used_handlers']+[NEW],value['mobile_used_handlers'])
        self.assertNotIn(NEW,value['upstream']['contract']['handlers'])
        for key in (NEW,CALLER):
            entries=value['reviewed_handler_variants'][key]
            self.assertEqual(1,len(entries));self.assertEqual(COMMIT,entries[0]['commit'])
            self.assertEqual(CONTEXTS,set(entries[0]['required_sources']))

    def test_real_review_modes_require_every_module_and_reject_hybrid_or_missing_replacements(self):
        value=self.value();old=copy.deepcopy(value['upstream']['contract'])
        # Isolate this one feature's watches. Full real snapshots are compared by
        # the compatibility command; this is test data, not runtime evidence.
        value['mobile_used_handlers']=[OLD,NEW,CALLER]
        value['reviewed_handler_variants']={k:value['reviewed_handler_variants'][k] for k in (NEW,CALLER)}
        value['reviewed_handler_replacements']={OLD:value['reviewed_handler_replacements'][OLD]}
        value['reviewed_handler_absences']={NEW:value['reviewed_handler_absences'][NEW]}
        value['baseline_handler_source_guards']={}
        current=copy.deepcopy(old);current['handlers'].pop(OLD)
        for key,entries in value['reviewed_handler_variants'].items():
            current['handlers'][key]=entries[0]['sha256']
            current['source_hashes'].update(entries[0]['required_sources'])
        for commit in (value['upstream']['commit'],'2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19'):
            self.assertFalse(checker.compare(value,{'commit':commit,'contract':old})['mobile_review_required'])
        self.assertFalse(checker.compare(value,{'commit':COMMIT,'contract':current})['mobile_review_required'])
        for key in CONTEXTS:
            changed=copy.deepcopy(current);changed['source_hashes'][key]='0'*64
            with self.subTest(context=key):
                result=checker.compare(value,{'commit':COMMIT,'contract':changed})
                self.assertTrue(result['mobile_review_required'])
                self.assertTrue({OLD,NEW,CALLER} <= set(result['mobile_relevant_changes']['changed_used_handlers']))
                self.assertNotIn(OLD,result['reviewed_handler_replacements_applied'])
        missing=copy.deepcopy(current);missing['handlers'].pop(NEW)
        result=checker.compare(value,{'commit':COMMIT,'contract':missing})
        self.assertIn(NEW,result['mobile_relevant_changes']['changed_used_handlers'])
        hybrid=copy.deepcopy(current);hybrid['handlers'][OLD]=old['handlers'][OLD]
        result=checker.compare(value,{'commit':COMMIT,'contract':hybrid})
        self.assertIn(OLD,result['mobile_relevant_changes']['changed_used_handlers'])
