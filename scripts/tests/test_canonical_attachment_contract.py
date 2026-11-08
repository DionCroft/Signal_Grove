"""Session attachment consumes exact reviewed source, not a moving branch waiver."""
import copy
import json
from pathlib import Path
import unittest
from test_hermes_contract import checker, sources

class CanonicalAttachmentContractTest(unittest.TestCase):
    def baseline(self):
        return json.loads((Path(__file__).resolve().parents[2]/'docs/contracts/hermes-baseline.json').read_text())

    def test_f0_baseline_still_compares_to_itself(self):
        b=self.baseline()
        self.assertFalse(checker.compare(b,{'commit':b['upstream']['commit'],'contract':copy.deepcopy(b['upstream']['contract'])})['mobile_review_required'])
        self.assertEqual(205,len(checker.SOURCES|checker.EXTRA_SOURCES))

    def test_each_consumed_module_is_parsed_without_execution_and_all_code_is_watched(self):
        for alias in checker.CANONICAL_ATTACHMENT_CONTRACT_SOURCES:
            with self.subTest(alias=alias):
                data=sources()
                data[alias]="raise RuntimeError('never execute upstream')\ndef helper():\n    return 'original'\n"
                original=checker.extract(data)
                data[alias]=data[alias].replace("'original'","'changed'")
                changed=checker.extract(data)
                key=alias+':consumed_module'
                self.assertNotEqual(original['handlers'][key],changed['handlers'][key])
                self.assertIn(key,self.baseline()['mobile_used_handlers'])

    def test_resume_review_requires_every_exact_dependency(self):
        b=self.baseline();key='canonical_controls:AuthorityConnection.resume'
        entry=b['reviewed_handler_variants'][key][0]
        current={'commit':entry['commit'],'contract':copy.deepcopy(b['upstream']['contract'])}
        current['contract']['handlers'][key]=entry['sha256']
        current['contract']['source_hashes'].update(entry['required_sources'])
        self.assertNotIn(key,checker.compare(b,current)['mobile_relevant_changes']['changed_used_handlers'])
        for source in entry['required_sources']:
            for missing in (False,True):
                with self.subTest(source=source,missing=missing):
                    bad=copy.deepcopy(current)
                    if missing:bad['contract']['source_hashes'].pop(source)
                    else:bad['contract']['source_hashes'][source]='0'*64
                    self.assertIn(key,checker.compare(b,bad)['mobile_relevant_changes']['changed_used_handlers'])

    def test_missing_owner_module_is_only_a_proven_old_pin_absence(self):
        path,pins=checker.REVIEWED_SOURCE_ABSENCES['canonical_owner']
        self.assertEqual('gateway/owner_identity.py',path)
        self.assertEqual(frozenset({'bf0203a794a07aac3ff764972ab9087baa4e4f4e'}),pins)
        b=self.baseline()
        current={'commit':'0'*40,'contract':copy.deepcopy(b['upstream']['contract'])}
        current['contract']['handlers'].pop('canonical_owner:consumed_module')
        self.assertIn('canonical_owner:consumed_module',checker.compare(b,current)['mobile_relevant_changes']['changed_used_handlers'])

    def test_snapshot_replay_identity_and_review_fields_stay_watched(self):
        b=self.baseline()
        for key in ('CanonicalResumeParams.omit_messages','CanonicalListParams.scope','CanonicalSnapshot.stored_session_id',
                    'CanonicalSnapshot.subscription_id','CanonicalSnapshot.authority_epoch','CanonicalSnapshot.revision',
                    'CanonicalSnapshot.execution_generation','CanonicalSnapshot.last_sequence','CanonicalEventsParams.replay_epoch',
                    'CanonicalEventsResult.snapshot_required','CanonicalReplayEvent.session_id'):
            key='canonical_sessions:'+key
            with self.subTest(key=key):
                self.assertIn(key,b['mobile_used_request_fields'])
                current={'commit':b['upstream']['commit'],'contract':copy.deepcopy(b['upstream']['contract'])}
                current['contract']['request_fields'][key]='0'*64
                self.assertIn(key,checker.compare(b,current)['mobile_relevant_changes']['changed_used_request_fields'])

if __name__=='__main__':unittest.main()
