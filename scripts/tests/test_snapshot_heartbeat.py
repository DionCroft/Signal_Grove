"""Counterexamples for snapshot-bound lifecycle source preparation; no SDK runs."""
from contextlib import ExitStack
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest import mock

spec = importlib.util.spec_from_file_location('snapshot_heartbeat', Path(__file__).resolve().parents[1] / 'hermes_snapshot_heartbeat.py')
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


class SnapshotHeartbeatPolicyTests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory(prefix='sg-snapshot-heartbeat-')
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        self.base = self.root / 'base'
        self.base.mkdir()
        self.output = self.root / 'derived'
        self.evidence = self.root / 'evidence'
        self.map = self.root / 'source-map.json'
        self.patch = self.root / 'delta.patch'
        self.kind = 'maintained-stable3b-read-v1'
        before = {'gateway/run_shutdown.py': b'before gateway\n', 'tui_gateway/session_reaper.py': b'before reaper\n', 'maintained.py': b'custom unchanged bytes\n', 'install-stamp.json': b'{}\n'}
        self.after = {'gateway/run_shutdown.py': b'after gateway\n', 'tui_gateway/session_reaper.py': b'after reaper\n'}
        self.expected = {name: m.digest(raw) for name, raw in before.items()}
        for name, raw in before.items():
            path = self.base / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(raw)
            if os.name == 'posix':
                path.chmod(0o644)
        patch = b''
        for name in self.after:
            patch += ('--- a/' + name + '\n+++ b/' + name + '\n@@ -1 +1 @@\n').encode() + b'-' + before[name] + b'+' + self.after[name]
        self.patch.write_bytes(patch)
        self.map.write_text(json.dumps(self.expected), encoding='utf8')
        stack = ExitStack()
        self.addCleanup(stack.close)
        stack.enter_context(mock.patch.dict(m.REVIEWED, {self.kind: (len(self.expected), m.digest(m.canonical(self.expected)))}, clear=True))
        stack.enter_context(mock.patch.object(m, 'EXECUTABLES', (0, m.digest(b'\n'))))
        stack.enter_context(mock.patch.dict(m.pinned.PATCH_FILES, {name: dict(base_sha256=self.expected[name], patched_sha256=m.digest(raw)) for name, raw in self.after.items()}, clear=True))
        stack.enter_context(mock.patch.object(m.pinned, 'PATCH_SHA256', m.digest(patch)))

    def prepare(self):
        return m.prepare(self.base, self.output, self.evidence, self.map, self.kind, self.patch)

    def verify(self):
        return m.verify(self.base, self.output, self.map, self.kind)

    def test_exact_two_deltas_preserve_every_custom_byte(self):
        original_umask = os.umask(0o077) if os.name == 'posix' else None
        try:
            record = self.prepare()
        finally:
            if original_umask is not None:
                os.umask(original_umask)
        proof = self.verify()
        self.assertEqual(record['state'], 'EXACT_SNAPSHOT_PLUS_TWO_LIFECYCLE_CHANGES_VERIFIED')
        self.assertEqual(proof['changed_files'], sorted(self.after))
        self.assertEqual((self.output / 'maintained.py').read_bytes(), (self.base / 'maintained.py').read_bytes())
        self.assertEqual((self.output / 'install-stamp.json').read_bytes(), b'{}\n')
        self.assertFalse(proof['sdk_executed'] or proof['deployed'] or proof['unmodified_upstream_accepted'])

    def test_wrong_whole_map_digest_refuses_before_writes(self):
        wrong = dict(self.expected, unrelated='0' * 64)
        self.map.write_text(json.dumps(wrong), encoding='utf8')
        with self.assertRaises(ValueError):
            self.prepare()
        self.assertFalse(self.output.exists() or self.evidence.exists())

    def test_duplicate_json_key_cannot_satisfy_review(self):
        raw = self.map.read_text(encoding='utf8')
        self.map.write_text(raw[:-1] + ', "maintained.py": "' + self.expected['maintained.py'] + '"}', encoding='utf8')
        with self.assertRaises(ValueError):
            m.reviewed_map(self.map, self.kind)

    def test_hash_valid_unsafe_review_names_are_still_refused(self):
        for bad in ('../escape.py', 'gateway/../../escape.py', 'NUL.py', 'shadow\\module.py'):
            value = dict(self.expected, **{bad: '0' * 64})
            with mock.patch.dict(m.REVIEWED, {self.kind: (len(value), m.digest(m.canonical(value)))}, clear=True):
                self.map.write_text(json.dumps(value), encoding='utf8')
                with self.assertRaises(ValueError):
                    m.reviewed_map(self.map, self.kind)

    def test_original_custom_drift_is_not_overwritten(self):
        path = self.base / 'maintained.py'
        path.write_bytes(b'new owner work\n')
        with self.assertRaises(ValueError):
            self.prepare()
        self.assertEqual(path.read_bytes(), b'new owner work\n')
        self.assertFalse(self.output.exists() or self.evidence.exists())

    def test_empty_shadow_namespace_refuses_before_copy(self):
        (self.base / 'shadow').mkdir()
        with self.assertRaises(ValueError):
            self.prepare()
        self.assertFalse(self.output.exists())

    def test_existing_output_and_receipt_are_never_reused(self):
        self.output.mkdir()
        sentinel = self.output / 'uncertain.bin'
        sentinel.write_bytes(b'preserve')
        with self.assertRaises(ValueError):
            self.prepare()
        self.assertEqual(sentinel.read_bytes(), b'preserve')
        self.assertFalse(self.evidence.exists())

    def test_third_file_change_cannot_hide_behind_two_file_manifest(self):
        self.prepare()
        (self.output / 'maintained.py').write_bytes(b'unauthorized delta\n')
        with self.assertRaises(ValueError):
            self.verify()

    def test_modified_patch_is_rejected_before_source_creation(self):
        self.patch.write_bytes(self.patch.read_bytes() + b'\n')
        with self.assertRaises(ValueError):
            self.prepare()
        self.assertFalse(self.output.exists() or self.evidence.exists())

    def test_nested_source_cannot_change_relative_runtime_context(self):
        nested = self.root / 'container'
        nested.mkdir()
        with self.assertRaises(ValueError):
            m.prepare(self.base, nested / 'derived', self.evidence, self.map, self.kind, self.patch)
        self.assertFalse(self.evidence.exists())

    def test_extra_derived_source_file_is_not_ignored(self):
        self.prepare()
        (self.output / '__init__.py').write_bytes(b'new shadow namespace\n')
        with self.assertRaises(ValueError):
            self.verify()

    def test_patch_output_and_base_are_rechecked(self):
        self.prepare()
        (self.output / 'gateway/run_shutdown.py').write_bytes(b'wrong correction\n')
        with self.assertRaises(ValueError):
            self.verify()
        (self.output / 'gateway/run_shutdown.py').write_bytes(self.after['gateway/run_shutdown.py'])
        (self.base / 'maintained.py').write_bytes(b'owner changed base after copy\n')
        with self.assertRaises(ValueError):
            self.verify()


if __name__ == '__main__':
    unittest.main()
