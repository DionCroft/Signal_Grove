"""Refuse incomplete or contradictory canonical acceptance evidence."""
import importlib.util
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location('canonical_rpc', Path(__file__).resolve().parents[1] / 'verify_canonical_rpc.py')
verifier = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verifier)


class CanonicalRpcEvidenceTests(unittest.TestCase):
    def suite(self):
        root = ET.Element('testsuites')
        suite = ET.SubElement(root, 'testsuite', tests='6', failures='0', errors='0', skipped='0')
        for name in sorted(verifier.EXPECTED):
            ET.SubElement(suite, 'testcase', name=name, classname='tests.gateway.test_session_plugin_rpc')
        return root, suite

    def rejected(self, root):
        with self.assertRaises(ValueError):
            verifier.positive_cases(ET.tostring(root))

    def test_complete_positive_inventory_includes_real_host_case(self):
        root, _ = self.suite()
        self.assertEqual({row['name'] for row in verifier.positive_cases(ET.tostring(root))}, verifier.EXPECTED)

    def test_posix_skip_is_not_whole_file_acceptance(self):
        root, suite = self.suite()
        suite.set('skipped', '1')
        ET.SubElement(suite[-1], 'skipped')
        self.rejected(root)

    def test_duplicate_or_missing_case_cannot_fill_total(self):
        root, suite = self.suite()
        suite[-1].set('name', suite[0].get('name'))
        self.rejected(root)

    def test_green_totals_cannot_hide_failed_case(self):
        root, suite = self.suite()
        ET.SubElement(suite[0], 'failure', message='preserved failure')
        self.rejected(root)

    def test_unexpected_suite_or_class_is_refused(self):
        root, suite = self.suite()
        suite[0].set('classname', 'unrelated.test')
        self.rejected(root)
        root, suite = self.suite()
        ET.SubElement(root, 'testsuite')
        self.rejected(root)

    def test_xml_entities_are_refused(self):
        with self.assertRaises(ValueError):
            verifier.positive_cases(b'<!DOCTYPE x [<!ENTITY x "content">]><testsuites/>')

    def test_index_must_match_exact_head_blob_and_mode(self):
        head = b'100644 blob ' + b'1' * 40 + b'\tmodule.py\0'
        index = b'100644 ' + b'1' * 40 + b' 0\tmodule.py\0'
        self.assertEqual(verifier.reviewed_entries(head, index)[b'module.py'], (b'100644', b'1' * 40))
        for changed in (index.replace(b'1', b'2'), index.replace(b'100644', b'100755'), b''):
            with self.assertRaises(ValueError):
                verifier.reviewed_entries(head, changed)

    def test_duplicate_and_unmerged_index_cannot_supply_source_proof(self):
        head = b'100644 blob ' + b'1' * 40 + b'\tmodule.py\0'
        index = b'100644 ' + b'1' * 40 + b' 0\tmodule.py\0'
        for changed in (index + index, index.replace(b' 0\t', b' 2\t')):
            with self.assertRaises(ValueError):
                verifier.reviewed_entries(head, changed)


if __name__ == '__main__':
    unittest.main()
