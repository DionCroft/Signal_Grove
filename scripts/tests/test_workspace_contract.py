"""Workspace writes watch exact source and wire fields without accepting runtime variants."""
import copy
import json
from pathlib import Path
import unittest
from test_hermes_contract import checker

class WorkspaceContractTest(unittest.TestCase):
    def baseline(self):
        return json.loads((Path(__file__).resolve().parents[2]/'docs/contracts/hermes-baseline.json').read_text())
    def test_f0_workspace_modules_are_bound_to_reviewed_git_blobs(self):
        b=self.baseline()
        manifest=json.loads((Path(__file__).resolve().parents[2]/'docs/contracts/workspace-source-review.json').read_text())
        for alias,path in checker.WORKSPACE_CONTRACT_SOURCES.items():
            row=next(x for x in manifest['files'] if x['version']=='f0' and x['path']==path)
            self.assertEqual(b['upstream']['commit'],row['commit'])
            self.assertEqual(row['sha256'],b['upstream']['contract']['source_hashes'][alias])
            self.assertIn(alias+':consumed_module',b['mobile_used_handlers'])
    def test_workspace_wire_fences_and_ack_identity_remain_watched(self):
        b=self.baseline()
        for cls,fields in [('SessionMutateParams',['session_id','request_id','expected_revision','operation','payload','expected_generation']),('SessionMutateResult',['session_id','operation','revision'])]:
            for field in fields:
                key='canonical_wire:'+cls+'.'+field
                self.assertIn(key,b['mobile_used_request_fields'])
                current={'commit':b['upstream']['commit'],'contract':copy.deepcopy(b['upstream']['contract'])}
                current['contract']['request_fields'][key]='0'*64
                self.assertIn(key,checker.compare(b,current)['mobile_relevant_changes']['changed_used_request_fields'])
    def test_changed_workspace_module_requires_review_without_a_variant_waiver(self):
        b=self.baseline()
        for alias in checker.WORKSPACE_CONTRACT_SOURCES:
            key=alias+':consumed_module'
            current={'commit':'0'*40,'contract':copy.deepcopy(b['upstream']['contract'])}
            current['contract']['handlers'][key]='0'*64
            self.assertIn(key,checker.compare(b,current)['mobile_relevant_changes']['changed_used_handlers'])
            self.assertNotIn(key,b.get('reviewed_handler_variants',{}))

if __name__=='__main__':unittest.main()
