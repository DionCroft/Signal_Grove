"""Counterexamples for native shutdown evidence and post-run source integrity."""
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location('shutdown_gate', Path(__file__).resolve().parents[1] / 'canonical_shutdown_gate.py')
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


class ShutdownEvidenceTests(unittest.TestCase):
    def suite(self, test_file=None):
        test_file = test_file or next(iter(gate.CASES))
        root = ET.Element('testsuites')
        suite = ET.SubElement(root, 'testsuite', tests=str(len(gate.CASES[test_file])), failures='0', errors='0', skipped='0')
        for classname, name in gate.CASES[test_file]:
            ET.SubElement(suite, 'testcase', classname=classname, name=name)
        return root, suite, test_file

    def reject(self, root, test_file):
        with self.assertRaises(ValueError):
            gate.positive_cases(ET.tostring(root), test_file)

    def test_all_six_complete_inventories_have_fifty_positive_identities(self):
        rows = [gate.positive_cases(ET.tostring(self.suite(f)[0]), f) for f in gate.CASES]
        self.assertEqual([len(r) for r in rows], [5, 17, 10, 2, 2, 14])
        self.assertEqual(sum(map(len, rows)), 50)

    def test_green_totals_cannot_hide_failed_or_skipped_cases(self):
        for tag in ('failure', 'error', 'skipped'):
            with self.subTest(tag=tag):
                root, suite, f = self.suite()
                ET.SubElement(suite[0], tag)
                self.reject(root, f)

    def test_duplicate_cannot_replace_missing_case(self):
        root, suite, f = self.suite()
        suite[-1].attrib.update(suite[0].attrib)
        self.reject(root, f)

    def test_wrong_class_cannot_masquerade_as_same_named_case(self):
        root, suite, f = self.suite()
        suite[0].set('classname', 'unrelated')
        self.reject(root, f)

    def test_partial_suite_and_changed_counters_are_rejected(self):
        root, suite, f = self.suite()
        suite.set('tests', '4')
        self.reject(root, f)
        root, suite, f = self.suite()
        suite.remove(suite[-1])
        self.reject(root, f)

    def test_extra_suite_is_not_laundered_into_a_positive(self):
        root, _, f = self.suite()
        ET.SubElement(root, 'testsuite', tests='0')
        self.reject(root, f)

    def test_entities_are_rejected(self):
        with self.assertRaises(ValueError):
            gate.positive_cases(b'<!DOCTYPE x [<!ENTITY x "y">]><testsuites/>', next(iter(gate.CASES)))

    def record(self):
        rows = []
        for i, name in enumerate(gate.CASES):
            rows.append(dict(file=name, state='TERMINAL', exitCode=0,
                 argv=['bash', '/sdk/scripts/run_tests.sh', name, '-j', '1',
                       '--file-retries', '0', '--fail-on-skip', '--file-timeout', '240',
                       '--', '-v', f'--junitxml=/evidence/{i}.xml']))
        return dict(state='ALL_FILES_TERMINAL', fileRetries=0,
                    canonicalRunner='scripts/run_tests.sh', runs=rows)

    def test_fresh_terminal_records_are_accepted_after_artifact_relocation(self):
        self.assertEqual(len(gate.terminal_runs(self.record())), 6)

    def test_unknown_failed_or_duplicated_process_is_not_a_positive(self):
        for field, value in (('state', 'UNKNOWN_PROCESS_PRESERVED_NO_REPLAY'), ('exitCode', 2)):
            record = self.record()
            record['runs'][0][field] = value
            with self.assertRaises(ValueError):
                gate.terminal_runs(record)
        record = self.record()
        record['runs'][-1] = record['runs'][0]
        with self.assertRaises(ValueError):
            gate.terminal_runs(record)

    def test_filter_retry_or_overwritten_junit_invocation_is_rejected(self):
        for index, value in ((6, '1'), (7, '-k'), (12, '--junitxml=/evidence/shared.xml')):
            record = self.record()
            record['runs'][0]['argv'][index] = value
            with self.assertRaises(ValueError):
                gate.terminal_runs(record)

    def fixture(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        root = Path(temp.name)
        (root / 'pkg').mkdir()
        (root / 'pkg/task.py').write_bytes(b'pass\n')
        expected = {'pkg/task.py': gate.snapshot.digest(b'pass\n')}
        before = gate.inspect_source(root, expected)
        return root, expected, before['modes']

    def test_source_mutation_is_rejected_even_with_valid_cache(self):
        root, expected, modes = self.fixture()
        (root / 'pkg/task.py').write_bytes(b'changed\n')
        with self.assertRaises(ValueError):
            gate.inspect_source(root, expected, modes, generated=True)

    def test_importable_extra_source_cannot_be_called_generated(self):
        root, expected, modes = self.fixture()
        (root / 'pkg/shadow.py').write_bytes(b'pass\n')
        with self.assertRaises(ValueError):
            gate.inspect_source(root, expected, modes, generated=True)

    def test_cache_for_unknown_source_or_wrong_runtime_is_rejected(self):
        root, expected, modes = self.fixture()
        cache = root / 'pkg/__pycache__'
        cache.mkdir()
        for name in ('shadow.cpython-314.pyc', 'task.cpython-999.pyc'):
            path = cache / name
            path.write_bytes(b'cache output')
            with self.assertRaises(ValueError):
                gate.inspect_source(root, expected, modes, generated=True)
            path.unlink()

    def test_source_link_and_empty_shadow_directory_are_rejected(self):
        root, expected, modes = self.fixture()
        (root / 'shadow').mkdir()
        with self.assertRaises(ValueError):
            gate.inspect_source(root, expected, modes, generated=True)
        if os.name == 'posix':
            (root / 'shadow').rmdir()
            (root / 'shadow').symlink_to(root / 'pkg', target_is_directory=True)
            with self.assertRaises(ValueError):
                gate.inspect_source(root, expected, modes, generated=True)

    def test_duration_cache_cannot_import_unselected_case_history(self):
        root, expected, modes = self.fixture()
        for value in ({'unreviewed.py': 0.2}, {next(iter(gate.CASES)): float('inf')}):
            (root / 'test_durations.json').write_text(json.dumps(value), encoding='utf8')
            with self.assertRaises(ValueError):
                gate.inspect_source(root, expected, modes, generated=True)

    def test_generated_outputs_are_recorded_without_weakening_fresh_source_gate(self):
        root, expected, modes = self.fixture()
        path = root / 'pkg/__pycache__/task.cpython-314.pyc'
        path.parent.mkdir()
        path.write_bytes(b'recorded cache output')
        with self.assertRaises(ValueError):
            gate.inspect_source(root, expected, modes)
        after = gate.inspect_source(root, expected, modes, generated=True)
        self.assertEqual(after['source'], expected)
        self.assertEqual(after['generated']['pkg/__pycache__/task.cpython-314.pyc']['sha256'], gate.snapshot.digest(path.read_bytes()))


if __name__ == '__main__':
    unittest.main()
