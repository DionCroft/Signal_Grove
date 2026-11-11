"""Project conversation navigation must retain exact coherent metadata/profile source contexts."""
import copy,json,unittest
from pathlib import Path
from test_hermes_contract import checker,sources,baseline,project_sessions_historical_projection

class ProjectSessionSourceTest(unittest.TestCase):
    aliases=('project_session_config','project_session_methods','project_session_tree','project_session_wire')
    def fixture(self):
        data=sources()
        for alias in self.aliases:data[alias]="raise RuntimeError('never execute upstream')\nimport owner_scope\nLIMIT=500\ndef project_sessions(profile=None):\n    return read(profile,include_discovered=False)\n"
        value=baseline(checker.extract(data))
        for alias in self.aliases:
            key=alias+':consumed_module';value['mobile_used_handlers'].append(key);value.setdefault('baseline_handler_source_guards',{})[key]=list(self.aliases)
        return value,data
    def compare(self,value,data):return checker.compare(value,dict(commit='b'*40,contract=checker.extract(data)))
    def test_complete_original_context_is_accepted(self):
        value,data=self.fixture();self.assertFalse(self.compare(value,data)['mobile_review_required'])
    def test_each_body_import_and_discovery_policy_change_requires_review(self):
        value,data=self.fixture()
        for alias in self.aliases:
            for before,after in [('read(profile,','read(None,'),('include_discovered=False','include_discovered=True'),('import owner_scope','import other_scope'),('LIMIT=500','LIMIT=5000')]:
                with self.subTest(alias=alias,before=before):self.assertTrue(self.compare(value,{**data,alias:data[alias].replace(before,after)})['mobile_review_required'])
    def test_missing_module_and_byte_only_drift_are_refused(self):
        value,data=self.fixture()
        for alias in self.aliases:
            for text in ('',data[alias]+'\n# Unreviewed bytes.\n'):
                with self.subTest(alias=alias,text=text):self.assertTrue(self.compare(value,{**data,alias:text})['mobile_review_required'])
    def test_complete_review_is_accepted_but_mixed_context_is_refused(self):
        value,data=self.fixture();changed={**data,self.aliases[0]:data[self.aliases[0]]+'\n# Explicit review.\n'};contract=checker.extract(changed)
        for alias in self.aliases:
            key=alias+':consumed_module';value.setdefault('reviewed_handler_variants',{})[key]=[dict(sha256=contract['handlers'][key],commit='b'*40,reason='Synthetic coherent read contract only.',required_sources={a:contract['source_hashes'][a] for a in self.aliases})]
        self.assertFalse(self.compare(value,changed)['mobile_review_required'])
        for alias in self.aliases[1:]:
            bad={**changed,alias:changed[alias]+'\n# Mixed mode.\n'};self.assertTrue(self.compare(value,bad)['mobile_review_required'])
    def test_incomplete_review_cannot_remove_a_required_dependency(self):
        value,data=self.fixture();key=self.aliases[0]+':consumed_module';contract=checker.extract(data)
        value['reviewed_handler_variants']={key:[dict(sha256=contract['handlers'][key],commit='b'*40,reason='Incomplete synthetic review.',required_sources={self.aliases[0]:contract['source_hashes'][self.aliases[0]]})]}
        with self.assertRaises(ValueError):self.compare(value,data)
    def production(self):return json.loads((Path(__file__).resolve().parents[2]/'docs/contracts/hermes-baseline.json').read_text())
    def test_production_modes_bind_every_metadata_and_auth_dependency(self):
        value=self.production();self.assertEqual(set(self.aliases),set(checker.PROJECT_SESSION_CONTRACT_SOURCES));self.assertEqual(222,len(checker.SOURCES|checker.EXTRA_SOURCES))
        keys={a+':consumed_module' for a in self.aliases}
        for pin in ['2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19','3b421578588243f920886e6fe79a6faa87ea026e']:
            contract=copy.deepcopy(value['upstream']['contract'])
            for key in keys:
                entry=next(e for e in value['reviewed_handler_variants'][key] if e['commit']==pin);self.assertEqual(19,len(entry['required_sources']));contract['handlers'][key]=entry['sha256'];contract['source_hashes'].update(entry['required_sources'])
            current=dict(commit=pin,contract=contract);self.assertFalse(keys & set(checker.compare(value,current)['mobile_relevant_changes']['changed_used_handlers']))
            for dep in entry['required_sources']:
                with self.subTest(pin=pin,dependency=dep):
                    bad=copy.deepcopy(current);bad['contract']['source_hashes'][dep]='0'*64;self.assertTrue(keys <= set(checker.compare(value,bad)['mobile_relevant_changes']['changed_used_handlers']))
    def test_historical_projection_changes_only_four_additive_sources(self):
        value=self.production();before=copy.deepcopy(value);old=project_sessions_historical_projection(value);self.assertEqual(before,value)
        for section in ['source_hashes','handlers','request_fields']:
            self.assertEqual({k:v for k,v in value['upstream']['contract'][section].items() if k.split(':',1)[0] not in self.aliases},old['upstream']['contract'][section])
        self.assertEqual([k for k in value['mobile_used_handlers'] if k.split(':',1)[0] not in self.aliases],old['mobile_used_handlers'])
        self.assertEqual(value['known_open_mobile_findings'],old['known_open_mobile_findings'])

if __name__=='__main__':unittest.main()
