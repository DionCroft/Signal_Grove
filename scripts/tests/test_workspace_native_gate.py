import importlib.util
import io
from pathlib import Path
import unittest

spec=importlib.util.spec_from_file_location('workspace_native_gate',Path(__file__).resolve().parents[1]/'run_workspace_provider_tests.py')
gate=importlib.util.module_from_spec(spec);spec.loader.exec_module(gate)


class NativeGatePolicyTest(unittest.TestCase):
    def run_case(self,kind):
        class Case(unittest.TestCase):
            def runTest(self):
                if kind=='skip':self.skipTest('synthetic skip must not become native acceptance')
                if kind in ('fail','expected'):self.fail('synthetic failure')
        if kind in ('expected','unexpected'):Case.runTest=unittest.expectedFailure(Case.runTest)
        case=Case();result=unittest.TextTestRunner(stream=io.StringIO(),verbosity=0,resultclass=gate.RecordedResult).run(unittest.TestSuite([case]))
        return case.id(),result
    def test_genuine_distinct_positive_case_with_unchanged_source_is_accepted(self):
        name,result=self.run_case('pass');self.assertTrue(gate.acceptance([name],result,{'source':'hash'},{'source':'hash'}))
    def test_skipped_failed_expected_failure_and_unexpected_success_are_refused(self):
        for kind in ['skip','fail','expected','unexpected']:
            name,result=self.run_case(kind)
            with self.subTest(kind=kind):self.assertFalse(gate.acceptance([name],result,{},{}))
    def test_empty_partial_duplicate_and_foreign_positive_lists_are_refused(self):
        name,result=self.run_case('pass')
        for expected in [[],[name,name],[name,'missing'],['foreign']]:
            with self.subTest(expected=expected):self.assertFalse(gate.acceptance(expected,result,{},{}))
        result.positive.append(name);self.assertFalse(gate.acceptance([name],result,{},{}))
    def test_source_drift_invalidates_an_otherwise_genuine_native_result(self):
        name,result=self.run_case('pass');self.assertFalse(gate.acceptance([name],result,{'source':'before'},{'source':'after'}))


if __name__=='__main__':unittest.main()
