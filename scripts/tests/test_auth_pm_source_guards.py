import copy
import hashlib
import json
from pathlib import Path
import unittest
import test_hermes_contract as fixtures
checker=fixtures.checker

PM_SOURCES={'pm_init':'pm/__init__.py','pm_extras':'pm/extras.py','pm_environments':'pm/environments.py','pm_environments_adopt':'pm/environments_adopt.py','pm_package':'pm/package.py','pm_paths':'pm/paths.py','pm_client':'pm/client.py'}
TARGETS=('auth_middleware:_GATE_PUBLIC_PREFIXES','auth_middleware:gated_auth_middleware','auth_native_run_api:GatewayRuntimeAPI._serve')
STABLE='3b421578588243f920886e6fe79a6faa87ea026e'

class PmStartupSourceGuardTests(unittest.TestCase):
    def fixture(self):
        key='auth_middleware:gated_auth_middleware'
        original={'source_hashes':{'auth_middleware':'a'*64},'handlers':{key:'b'*64}}
        after={'source_hashes':{'auth_middleware':'c'*64,**{n:'d'*64 for n in PM_SOURCES}},'handlers':{key:'e'*64}}
        baseline={'upstream':{'commit':'a'*40,'contract':original},'mobile_used_handlers':[key],
            'reviewed_handler_variants':{key:[{'sha256':'e'*64,'commit':STABLE,'reason':'Synthetic source-fence counterexample; not SDK acceptance.','required_sources':dict(after['source_hashes'])}]}}
        return baseline,original,after,key

    def test_pm_modules_are_required_static_inputs_and_never_executed(self):
        self.assertEqual(PM_SOURCES,checker.PM_STARTUP_CONTRACT_SOURCES)
        source=fixtures.sources()
        for n in PM_SOURCES:source[n]="raise RuntimeError('Upstream must never execute')\n"
        result=checker.extract(source)
        for n in PM_SOURCES:self.assertEqual(hashlib.sha256(source[n].encode()).hexdigest(),result['source_hashes'][n])
        for n in PM_SOURCES:
            changed=dict(source);changed.pop(n)
            with self.subTest(dependency=n),self.assertRaisesRegex(ValueError,'Missing required contract source'):checker.extract(changed)

    def test_exact_new_pm_context_selects_only_its_handler_variant(self):
        b,old,new,key=self.fixture();accepted,applied=checker.reviewed_handler_variants(b,{'contract':new})
        self.assertEqual({key:{'e'*64}},accepted);self.assertEqual({key},applied)
        self.assertEqual({'auth_middleware':'a'*64},old['source_hashes'])

    def test_each_pm_dependency_drift_or_absence_reopens_review(self):
        b,old,new,key=self.fixture()
        for n in PM_SOURCES:
            for mode in ('changed','missing'):
                changed=copy.deepcopy(new)
                if mode=='changed':changed['source_hashes'][n]='f'*64
                else:changed['source_hashes'].pop(n)
                with self.subTest(dependency=n,mode=mode):
                    accepted,applied=checker.reviewed_handler_variants(b,{'contract':changed})
                    self.assertEqual(set(),accepted[key]);self.assertNotIn(key,applied)

    def test_commit_label_and_same_parent_ast_cannot_waive_pm_drift(self):
        b,old,new,key=self.fixture();new['source_hashes']['pm_extras']='f'*64
        for commit in (STABLE,'a'*40,'f'*40):
            accepted,applied=checker.reviewed_handler_variants(b,{'commit':commit,'contract':new})
            self.assertEqual(set(),accepted[key]);self.assertNotIn(key,applied)

    def test_untracked_dependency_and_malformed_digest_refuse(self):
        b,old,new,key=self.fixture()
        for dependency,digest in (('untracked_runtime','f'*64),('pm_extras','not-a-hash')):
            changed=copy.deepcopy(b);changed['reviewed_handler_variants'][key][0]['required_sources'][dependency]=digest
            with self.subTest(dependency=dependency),self.assertRaises(ValueError):checker.reviewed_handler_variants(changed,{'contract':new})

    def test_historical_snapshot_does_not_acquire_new_pm_facts(self):
        b,old,new,key=self.fixture();before=copy.deepcopy(old)
        accepted,applied=checker.reviewed_handler_variants(b,{'contract':old})
        self.assertEqual(set(),accepted[key]);self.assertEqual(set(),applied);self.assertEqual(before,old)

    def test_three_stable_entries_keep_complete_eighteen_source_fences(self):
        b=json.loads((Path(__file__).resolve().parents[2]/'docs/contracts/hermes-baseline.json').read_text(encoding='utf-8'))
        self.assertEqual(222,len(b['upstream']['contract']['source_hashes']))
        self.assertEqual(set(PM_SOURCES)-{'pm_client'},set(checker.SOURCES|checker.EXTRA_SOURCES)-set(b['upstream']['contract']['source_hashes']))
        self.assertTrue(set(b['upstream']['contract']['source_hashes'])<=set(checker.SOURCES|checker.EXTRA_SOURCES))
        previous=copy.deepcopy(b);fences=[]
        for key in TARGETS:
            entry=previous['reviewed_handler_variants'][key].pop()
            self.assertEqual(STABLE,entry['commit']);self.assertEqual(18,len(entry['required_sources']))
            self.assertTrue(set(PM_SOURCES)<=set(entry['required_sources']));self.assertIn('ws_boundary',entry['required_sources'])
            self.assertIn('auth_middleware',entry['required_sources']);self.assertIn('web_bootstrap',entry['required_sources'])
            self.assertIn('auth_native_run_api',entry['required_sources']);fences.append(entry['required_sources'])
        self.assertEqual(fences[0],fences[1]);self.assertEqual(fences[1],fences[2])
        self.assertEqual('0065c4deac2fcee58b736b9aa81d6a42c67c074c0022f471c8c62a06e5111d99',hashlib.sha256(json.dumps(previous,sort_keys=True,separators=(',',':')).encode()).hexdigest())

if __name__=='__main__':unittest.main()
