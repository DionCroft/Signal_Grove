import copy
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import Mock,patch

ROOT=Path(__file__).resolve().parents[1]
def module(name,path):
    spec=importlib.util.spec_from_file_location(name,path);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m
coverage=module('coverage_cases',ROOT/'android_device_coverage.py')
device=module('device_coverage_host',ROOT/'ci_android_device.py')


class AndroidCoverageTest(unittest.TestCase):
    source='a'*40
    apks=dict(app='b'*64,test='c'*64)
    def output(self,names):
        lines=[]
        for index,name in enumerate(names,1):
            for code in (1,0):
                lines.extend(['INSTRUMENTATION_STATUS: class=org.hermesmobile.app.FixtureTest','INSTRUMENTATION_STATUS: test='+name,'INSTRUMENTATION_STATUS: numtests='+str(len(names)),'INSTRUMENTATION_STATUS: current='+str(index),'INSTRUMENTATION_STATUS_CODE: '+str(code)])
        return '\n'.join([*lines,'OK ('+str(len(names))+' tests)','INSTRUMENTATION_CODE: -1',''])
    def partition(self,api,index,names,all_names=('one','two')):
        inventory=self.output(all_names);native=self.output(names)
        report=coverage.partition_record(api=api,index=index,source=self.source,apks=self.apks.copy(),external=device.external_model_gate_manifest(),inventory_output=inventory,native_output=native)
        return report,inventory,native
    def records(self):return [self.partition(api,index,[('one','two')[index]]) for api in (26,37) for index in (0,1)]
    def aggregate(self,records):return coverage.aggregate_partitions(records,self.source,device.external_model_gate_manifest())
    def test_complete_native_matrix_requires_disjoint_exact_unions_and_retains_external_not_run(self):
        result=self.aggregate(self.records());self.assertEqual('standard_suite_complete',result['status']);self.assertEqual(2,result['apis']['26']['executed_cases']);self.assertFalse(result['external_model_acceptance']);self.assertEqual('not_run',result['external_model_gates']['status'])
    def test_inventory_only_never_claims_test_bodies_ran(self):
        result=coverage.inventory_record(self.output(['one','two']),0);self.assertEqual('inventory_only',result['status']);self.assertIs(False,result['native_acceptance']);self.assertIs(False,result['test_bodies_executed']);self.assertNotIn('success_criteria_met',result)
    def test_partial_skipped_failed_duplicate_or_forged_status_streams_never_supply_case_proof(self):
        base=self.output(['one','two'])
        for text in ['',base.replace('CODE: -1','CODE: 0'),base+base,base.replace('STATUS_CODE: 0','STATUS_CODE: -3',1),base.replace('current=2','current=1'),base.replace('test=two','test=one'),base.replace('numtests=2','numtests=3'),base.replace('STATUS_CODE: 1','STATUS_CODE: 2',1),base.replace('OK (2 tests)','OK (1 tests)'),base.replace('STATUS: test=one','STATUS: class=org.hermesmobile.app.FixtureTest\nINSTRUMENTATION_STATUS: test=one',1),'OK (2 tests)\nINSTRUMENTATION_CODE: -1\n']:
            with self.subTest(text=text[:100]),self.assertRaises(ValueError):coverage.case_records(text,0)
        for code in [None,1,False]:
            with self.subTest(code=code),self.assertRaises(ValueError):coverage.case_records(base,code)
    def test_crlf_case_evidence_parses_without_rewriting_its_hash_binding(self):
        text=self.output(['one']).replace('\n','\r\n');self.assertEqual(1,len(coverage.case_records(text,0)))
        row=self.partition(26,0,['one']);report,inventory,native=row
        with self.assertRaises(ValueError):coverage.verify_partition(report,inventory.replace('\n','\r\n'),native,self.source,device.external_model_gate_manifest())
    def test_omission_overlap_missing_api_and_duplicate_partition_cannot_be_full_coverage(self):
        for rows in [self.records()[:3],[self.partition(api,index,['one']) for api in (26,37) for index in (0,1)],[self.partition(api,index,['one'] if index==0 else ['two'],('one','two','three')) for api in (26,37) for index in (0,1)],[self.records()[0],*self.records()[0:3]]]:
            with self.subTest(rows=len(rows)),self.assertRaises(ValueError):self.aggregate(rows)
    def test_independent_full_inventories_must_agree_before_union(self):
        rows=self.records();rows[1]=self.partition(26,1,['two'],('one','two','three'))
        with self.assertRaises(ValueError):self.aggregate(rows)
    def test_sdk_specific_inventory_can_differ_between_apis_but_each_api_is_complete(self):
        rows=self.records()[:2]+[self.partition(37,index,['three'] if index==0 else ['four'],('three','four')) for index in (0,1)];self.assertEqual(2,self.aggregate(rows)['apis']['37']['executed_cases'])
    def test_stale_source_different_apk_unknown_shard_changed_exclusions_and_false_codes_refuse(self):
        for key,value in [('source_revision','d'*40),('api',True),('shard_index',2),('shard_count',True),('status','inventory_only'),('apk_sha256',dict(app='d'*64,test='c'*64)),('external_model_gates',{}),('command_returncodes',dict(inventory=0,native=False))]:
            rows=self.records();report=rows[0][0];report[key]=value
            with self.subTest(key=key),self.assertRaises(ValueError):self.aggregate(rows)
    def test_raw_native_evidence_and_case_projection_are_both_mandatory(self):
        report,inventory,native=self.partition(26,0,['one'])
        for replacement in [native.replace('test=one','test=changed'),native+'truncated']:
            with self.assertRaises(ValueError):coverage.verify_partition(report,inventory,replacement,self.source,device.external_model_gate_manifest())
        changed=copy.deepcopy(report);changed['executed_cases']=copy.deepcopy(changed['inventory_cases'])
        with self.assertRaises(ValueError):coverage.verify_partition(changed,inventory,native,self.source,device.external_model_gate_manifest())
    def test_duplicate_json_fields_are_not_silently_reinterpreted(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'report.json';path.write_text('{"schema":1,"schema":2}')
            with self.assertRaises(ValueError):coverage.load_unique_json(path)
    def test_shard_command_retains_the_same_deadline_and_exclusions_and_runs_once(self):
        for index in (0,1):
            with self.subTest(index=index),tempfile.TemporaryDirectory() as directory:
                native=Mock(return_value=Mock(returncode=0,stdout=self.output(['one'])));self.assertEqual(1,device.run_instrumentation(native,Path(directory)/'instrumentation.log',shard_index=index));native.assert_called_once()
                args=native.call_args.args;self.assertEqual(('numShards','2','-e','shardIndex',str(index)),args[args.index('numShards'):args.index('numShards')+5]);self.assertEqual(6000,native.call_args.kwargs['timeout']);self.assertEqual(','.join(device.EXTERNAL_MODEL_GATES),args[args.index('notClass')+1])
        for index in [-1,2,True,'0']:
            with self.subTest(index=index),tempfile.TemporaryDirectory() as directory:
                native=Mock()
                with self.assertRaises(ValueError):device.run_instrumentation(native,Path(directory)/'out.log',shard_index=index)
                native.assert_not_called()
    def test_inventory_is_one_explicit_log_only_call_and_never_a_reusable_pass(self):
        with tempfile.TemporaryDirectory() as directory,patch.dict(sys.modules,{'android_device_coverage':coverage}):
            work=Path(directory);native=Mock(return_value=Mock(returncode=0,stdout=self.output(['one','two'])));result=device.read_standard_inventory(native,work);native.assert_called_once();self.assertEqual('inventory_only',result['status']);args=native.call_args.args;self.assertEqual('true',args[args.index('log')+1]);self.assertNotIn('numShards',args)
            with self.assertRaises(ValueError):device.read_standard_inventory(native,work)
            native.assert_called_once()
    def test_inventory_timeout_retains_its_original_partial_log_without_native_execution(self):
        with tempfile.TemporaryDirectory() as directory,patch.dict(sys.modules,{'android_device_coverage':coverage}):
            work=Path(directory);native=Mock(side_effect=subprocess.TimeoutExpired('inventory',120,output=b'partial'))
            with self.assertRaises(subprocess.TimeoutExpired):device.read_standard_inventory(native,work)
            self.assertEqual('partial',(work/'inventory.log').read_text());self.assertEqual('inventory_failed_or_unknown',json.loads((work/'inventory-result.json').read_text())['status']);native.assert_called_once()
    def test_installed_package_bytes_must_match_both_workflow_artifacts(self):
        with tempfile.TemporaryDirectory() as directory:
            app=Path(directory)/'app.apk';test=Path(directory)/'test.apk';app.write_bytes(b'app');test.write_bytes(b'test')
            import hashlib
            results=[]
            for kind,path in [('app',app),('test',test)]:results.extend([Mock(stdout='package:/data/app/~~fixture/'+kind+'/base.apk\n'),Mock(stdout=hashlib.sha256(path.read_bytes()).hexdigest()+'  /data/app/base.apk\n')])
            native=Mock(side_effect=results);self.assertEqual({'app','test'},set(device.verified_installed_apks(native,app,test)));self.assertEqual(4,native.call_count)
            native=Mock(side_effect=[Mock(stdout='package:/data/app/app/base.apk\n'),Mock(stdout='0'*64+' bad\n')])
            with self.assertRaises(ValueError):device.verified_installed_apks(native,app,test)
    def test_workflow_has_all_four_partitions_and_a_required_union_gate(self):
        text=(ROOT.parent/'.github/workflows/android-ci.yml').read_text();self.assertIn('api: [26, 37]',text);self.assertIn('shard: [0, 1]',text);self.assertIn('--shard-index "$DEVICE_SHARD"',text);self.assertIn('device-coverage:',text);self.assertIn('needs: device\n    if: always()',text);self.assertIn('scripts/verify_android_device_coverage.py',text);self.assertIn('timeout-minutes: 115',text)
        self.assertEqual(5,len(device.EXTERNAL_MODEL_GATES));self.assertEqual(11,sum(len(x['methods']) for x in device.EXTERNAL_MODEL_GATES.values()))


if __name__=='__main__':unittest.main()
