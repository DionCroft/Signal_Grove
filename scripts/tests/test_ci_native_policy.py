import importlib.util
import io
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('ci_native_policy',Path(__file__).resolve().parents[1]/'ci_native_policy.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)


class NativePolicyCoverageTests(unittest.TestCase):
    def run_cases(self,*tests):
        suite=unittest.TestSuite(tests);planned=m.case_ids(suite)
        result=unittest.TextTestRunner(stream=io.StringIO(),resultclass=m.ObservedResult).run(suite)
        return m.result_record(planned,result)

    def test_only_exact_positive_method_union_can_pass(self):
        class Fixture(unittest.TestCase):
            def test_one(self):pass
            def test_two(self):pass
        report=self.run_cases(Fixture('test_one'),Fixture('test_two'))
        self.assertTrue(report['exact_positive_coverage'])
        self.assertEqual(len(report['successful']),2)

    def test_skip_failure_error_and_expected_failure_cannot_become_acceptance(self):
        class Fixture(unittest.TestCase):
            @unittest.skip('Prepared fixture missing')
            def test_skip(self):pass
            def test_fail(self):self.fail('preserved failure')
            def test_error(self):raise ValueError('preserved error')
            @unittest.expectedFailure
            def test_expected(self):self.fail('still not accepted')
        for name in ('test_skip','test_fail','test_error','test_expected'):
            with self.subTest(case=name):self.assertFalse(self.run_cases(Fixture(name))['exact_positive_coverage'])

    def test_duplicate_cases_and_empty_inventory_never_pass(self):
        class Fixture(unittest.TestCase):
            def test_one(self):pass
        self.assertFalse(self.run_cases()['exact_positive_coverage'])
        self.assertFalse(self.run_cases(Fixture('test_one'),Fixture('test_one'))['exact_positive_coverage'])

    def test_missing_positive_or_finish_event_never_passes(self):
        class Fixture(unittest.TestCase):
            def test_one(self):pass
        case=Fixture('test_one');result=m.ObservedResult(unittest.runner._WritelnDecorator(io.StringIO()),True,1)
        result.startTest(case);result.addSuccess(case)
        self.assertFalse(m.result_record([case.id()],result)['exact_positive_coverage'])
        result.stopTest(case);result.successes=[]
        self.assertFalse(m.result_record([case.id()],result)['exact_positive_coverage'])

    def test_failing_subtest_is_not_a_completed_positive_case(self):
        class Fixture(unittest.TestCase):
            def test_one(self):
                with self.subTest(stage='backup'):self.fail('original failure')
        report=self.run_cases(Fixture('test_one'))
        self.assertFalse(report['exact_positive_coverage']);self.assertEqual(len(report['failures']),1)

    def test_unit_has_finite_lifetime_and_clears_inherited_credentials(self):
        with patch.dict(m.os.environ,{'GITHUB_TOKEN':'not passed','HERMES_API_KEY':'not passed'}):
            command=m.unit_command(Path('/fixture/tools/python/bin/python'),Path('/fixture/scripts/ci_native_policy.py'),Path('/fixture/owned'),'a'*32,False,1000)
        self.assertIn('--property=RuntimeMaxSec=900',command)
        self.assertIn('--property=KillMode=control-group',command)
        self.assertIn('--property=Restart=no',command)
        self.assertIn('/usr/bin/env',command);self.assertIn('-i',command)
        self.assertFalse(any('GITHUB_TOKEN' in row or 'HERMES_API_KEY' in row for row in command))
        self.assertNotIn('--native-only',command)


if __name__=='__main__':unittest.main()
