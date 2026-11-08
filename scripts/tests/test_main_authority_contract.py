"""Current-main canonical compatibility requires complete source modes, never a moving-ref waiver."""
import copy
import json
from pathlib import Path
import unittest
from test_hermes_contract import checker, complete_replacement_fixture

PIN='21ae0e1238fdf466fa52a85a6ebf75d6899e49da'

class MainAuthorityContractTest(unittest.TestCase):
    def fixture(self):
        value=json.loads((Path(__file__).resolve().parents[2]/'docs/contracts/hermes-baseline.json').read_text())
        contract=copy.deepcopy(value['upstream']['contract'])
        for key,entries in value['reviewed_handler_variants'].items():
            if key not in contract['handlers']:contract['handlers'][key]=entries[-1]['sha256']
        complete_replacement_fixture(value,contract)
        reviewed={}
        for key,entries in value['reviewed_handler_variants'].items():
            found=[entry for entry in entries if entry['commit']==PIN]
            if found:
                self.assertEqual(1,len(found));reviewed[key]=found[0]
                contract['handlers'][key]=found[0]['sha256']
                contract['source_hashes'].update(found[0]['required_sources'])
        self.assertEqual(13,len({key:entry for key,entry in reviewed.items() if not key.startswith('hosted_room_')}))
        self.assertEqual({'canonical_event_stream:consumed_module','canonical_event_payloads:consumed_module'},
                         {key for key in reviewed if key.startswith(('canonical_event_stream:','canonical_event_payloads:'))})
        return value,{'commit':'e'*40,'contract':contract},reviewed

    def test_old_pin_remains_self_consistent_and_main_adds_no_blanket_commit_trust(self):
        value,current,reviewed=self.fixture()
        self.assertEqual('f0d94882e89ab8d4098b5bb2f166adfb9ee0a0ab',value['upstream']['commit'])
        self.assertEqual(205,len(checker.SOURCES|checker.EXTRA_SOURCES))
        self.assertFalse(checker.compare(value,{'commit':value['upstream']['commit'],'contract':copy.deepcopy(value['upstream']['contract'])})['mobile_review_required'])
        self.assertFalse(checker.compare(value,current)['mobile_review_required'])
        for entry in reviewed.values():
            self.assertTrue(entry['reason'].strip())
            self.assertTrue(entry['required_sources'])
        for key in reviewed:
            if (key.startswith('canonical_') or key=='gateway_ws:_authority_connection') and key not in {'canonical_event_stream:consumed_module','canonical_event_payloads:consumed_module'}:
                self.assertTrue(set(checker.MAIN_AUTHORITY_CONTRACT_SOURCES)<=set(reviewed[key]['required_sources']))

    def test_every_consumed_dependency_is_required_even_when_handler_ast_matches(self):
        value,current,reviewed=self.fixture()
        for key,entry in reviewed.items():
            for alias in entry['required_sources']:
                for missing in (True,False):
                    with self.subTest(handler=key,dependency=alias,missing=missing):
                        changed=copy.deepcopy(current)
                        if missing:changed['contract']['source_hashes'].pop(alias)
                        else:changed['contract']['source_hashes'][alias]='0'*64
                        self.assertIn(key,checker.compare(value,changed)['mobile_relevant_changes']['changed_used_handlers'])

    def test_reviewed_source_identities_cannot_authorize_unknown_or_missing_handlers(self):
        value,current,reviewed=self.fixture()
        for key in reviewed:
            for missing in (True,False):
                with self.subTest(handler=key,missing=missing):
                    changed=copy.deepcopy(current)
                    if missing:changed['contract']['handlers'].pop(key)
                    else:changed['contract']['handlers'][key]='0'*64
                    self.assertIn(key,checker.compare(value,changed)['mobile_relevant_changes']['changed_used_handlers'])

    def test_new_helpers_can_be_absent_only_at_the_proven_older_commits(self):
        expected={'f0d94882e89ab8d4098b5bb2f166adfb9ee0a0ab','b200f5bc60e31588ba2cb60e288029c4a54ba5fa','bf0203a794a07aac3ff764972ab9087baa4e4f4e','2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19'}
        for alias in ['canonical_stored_reads','canonical_picker','canonical_plugin_rpc','canonical_transcript_rows']:
            path,pins=checker.REVIEWED_SOURCE_ABSENCES[alias]
            self.assertEqual(expected,set(pins))
            for ref in expected:self.assertTrue(checker.reviewed_source_absence(alias,path,ref))
            for ref in [PIN,'e'*40,'main']:
                self.assertFalse(checker.reviewed_source_absence(alias,path,ref))
            self.assertFalse(checker.reviewed_source_absence(alias,path+'/other',next(iter(expected))))

if __name__=='__main__':unittest.main()
