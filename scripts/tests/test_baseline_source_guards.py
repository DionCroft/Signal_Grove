"""Original source contexts need explicit dependency guards as well as reviewed variants."""
import copy
import unittest
from test_hermes_contract import baseline, checker, sources

class BaselineSourceGuardsTest(unittest.TestCase):
    key='mcp:set_mcp_server_enabled'
    def fixture(self):
        original=sources();changed=dict(original)
        changed['mcp']=changed['mcp'].replace("return {'ok':True}","return {'ok':False}")
        changed['env_config']="raise RuntimeError('never execute upstream')\ndef helper():\n    return 'reviewed'\n"
        contract=checker.extract(changed);value=baseline(checker.extract(original))
        value['baseline_handler_source_guards']={self.key:['mcp','env_config']}
        value['reviewed_handler_variants']={self.key:[dict(sha256=contract['handlers'][self.key],commit='b'*40,
            reason='Synthetic full original/variant dependency modes',required_sources={n:contract['source_hashes'][n] for n in ['mcp','env_config']})]}
        return value,original,changed
    def compare(self,value,data):return checker.compare(value,dict(commit='b'*40,contract=checker.extract(data)))
    def test_original_and_reviewed_complete_source_modes_remain_valid(self):
        value,original,changed=self.fixture()
        self.assertFalse(self.compare(value,original)['mobile_review_required'])
        self.assertFalse(self.compare(value,changed)['mobile_review_required'])
    def test_baseline_ast_cannot_hide_changed_helper_source(self):
        value,original,_=self.fixture();original['env_config']+="\ndef a_new_helper():\n    return 'changed'\n"
        self.assertIn(self.key,self.compare(value,original)['mobile_relevant_changes']['changed_used_handlers'])
    def test_baseline_ast_cannot_hide_changed_own_source_bytes(self):
        value,original,_=self.fixture();original['mcp']+='\n# Changed bytes; same original AST.\n'
        self.assertIn(self.key,self.compare(value,original)['mobile_relevant_changes']['changed_used_handlers'])
    def test_incomplete_variant_cannot_bypass_original_dependency_inventory(self):
        value,_,changed=self.fixture();value['reviewed_handler_variants'][self.key][0]['required_sources'].pop('env_config')
        with self.assertRaises(ValueError):self.compare(value,changed)
    def test_malformed_original_mode_guards_are_not_silently_ignored(self):
        value,original,_=self.fixture();value['baseline_handler_source_guards'][self.key]=[]
        with self.assertRaises(ValueError):self.compare(value,original)

if __name__=='__main__':unittest.main()
