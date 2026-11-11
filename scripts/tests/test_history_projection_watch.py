"""Saved-message projection changes must require explicit source review."""
import copy
import importlib.util
import json
from pathlib import Path
import unittest
spec=importlib.util.spec_from_file_location('projection_checker',Path(__file__).resolve().parents[1]/'check_hermes_contract.py')
checker=importlib.util.module_from_spec(spec);spec.loader.exec_module(checker)
BASE=json.loads((Path(__file__).resolve().parents[2]/'docs/contracts/hermes-baseline.json').read_text(encoding='utf-8'))
KEYS=('api:_project_client_message','api:APIServerAdapter._message_response')
class HistoryProjectionWatchTest(unittest.TestCase):
    def current(self,pin=0):
        current=copy.deepcopy(BASE['upstream'])
        for key in KEYS:
            variant=BASE['reviewed_handler_variants'][key][pin]
            current['commit']=variant['commit'];current['contract']['handlers'][key]=variant['sha256'];current['contract']['source_hashes'].update(variant['required_sources'])
        return current
    def changed(self,current):return checker.compare(BASE,current)['mobile_relevant_changes']['changed_used_handlers']
    def test_original_baseline_remains_accepted(self):self.assertFalse(checker.compare(BASE,BASE['upstream'])['mobile_review_required'])
    def test_both_exact_reviewed_projection_contexts_are_accepted(self):
        for pin in (0,1):self.assertFalse(set(KEYS)&set(self.changed(self.current(pin))))
    def test_hidden_or_safe_field_ast_changes_require_review(self):
        for key in KEYS:
            current=self.current();current['contract']['handlers'][key]='0'*64
            self.assertIn(key,self.changed(current))
    def test_unchanged_ast_in_a_different_api_module_is_not_a_waiver(self):
        current=self.current();current['contract']['source_hashes']['api']='f'*64
        self.assertTrue(set(KEYS)<=set(self.changed(current)))
    def test_consumed_projection_disappearance_requires_review(self):
        for key in KEYS:
            current=self.current();current['contract']['handlers'].pop(key)
            self.assertIn(key,self.changed(current))
if __name__=='__main__':unittest.main()
