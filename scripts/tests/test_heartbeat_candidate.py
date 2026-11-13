"""Counterexamples for isolated SDK candidate provenance and materialization."""
from pathlib import Path
import hashlib
import importlib.util
import json
import os
import stat
import tempfile
import types
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("heartbeat_candidate_policy", ROOT / "scripts/hermes_heartbeat_candidate.py")
candidate = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(candidate)


class HeartbeatCandidatePolicyTest(unittest.TestCase):
    def setUp(self):
        self.scratch = Path(tempfile.gettempdir()).resolve()
        if os.name == "nt" and self.scratch.drive.upper() != "E:":
            raise ValueError("Set TEMP/TMP to E: before running project tests")
        self.temp = tempfile.TemporaryDirectory(prefix="heartbeat-policy-", dir=self.scratch)
        self.root = Path(self.temp.name).resolve()
        self.base = self.root / "base"
        self.source = self.root / "source"
        self.base.mkdir()
        self.source.mkdir()
        self.raw = b"VALUE = 1\n"
        (self.source / "module.py").write_bytes(self.raw)
        self.tree = {"module.py": candidate.git_blob(self.raw)}

    def tearDown(self):
        if not self.root.is_relative_to(self.scratch) or self.root == self.scratch:
            raise AssertionError("Fixture cleanup escaped its verified scratch parent")
        self.temp.cleanup()

    def verify_fixture(self):
        with patch.object(candidate, "base_tree", return_value=self.tree), patch.object(candidate, "PATCH_FILES", {}):
            return candidate.verify_candidate(self.base, self.source)

    def test_git_blob_matches_known_empty_object(self):
        self.assertEqual(candidate.git_blob(b""), "e69de29bb2d1d6434b8b29ae775ad8c2e48c5391")

    def test_relative_source_names_preserve_one_spelling(self):
        for name in ("module.py", "tui_gateway/session_reaper.py", ".github/workflows/check.yml", "LICENSE"):
            self.assertEqual(candidate.relative_path(name).as_posix(), name)

    def test_unsafe_or_ambiguous_source_names_are_refused(self):
        for name in ("", ".", "..", "../outside", "/outside", "//host/share", "C:/outside", "E:relative",
                     "a\\b", "a//b", "a/./b", "a/../b", "a/", ".git/config", ".GIT/config",
                     "NUL.py", "nested/COM1.txt", "trailing.", "trailing ", "a:b", "a?b", "a*b", "a|b", "a\nb"):
            with self.subTest(name=name), self.assertRaises(ValueError):
                candidate.relative_path(name)

    def test_committed_patch_has_the_exact_reviewed_bytes(self):
        path = ROOT / "upstream_patches" / candidate.PATCH_NAME
        self.assertEqual(hashlib.sha256(candidate.patch_bytes(path)).hexdigest(), candidate.PATCH_SHA256)

    def test_altered_patch_is_refused_before_materialization(self):
        path = self.root / "changed.patch"
        path.write_bytes(b"not the reviewed source patch\n")
        with self.assertRaisesRegex(ValueError, "patch bytes"):
            candidate.patch_bytes(path)

    def test_unknown_base_pin_is_refused(self):
        with patch.object(candidate, "git", return_value=b"f" * 40 + b"\n") as mocked:
            with self.assertRaisesRegex(ValueError, "exact reviewed"):
                candidate.base_tree(self.base)
            self.assertEqual(mocked.call_count, 1)

    def test_dirty_base_is_refused_without_reading_its_tree(self):
        with patch.object(candidate, "git", side_effect=[candidate.BASE_COMMIT.encode() + b"\n", b" M changed.py\n"]) as mocked:
            with self.assertRaisesRegex(ValueError, "clean"):
                candidate.base_tree(self.base)
            self.assertEqual(mocked.call_count, 2)

    def test_wrong_patch_base_blob_is_refused(self):
        tree = b"100644 blob " + self.tree["module.py"].encode() + b"\tmodule.py\0"
        with patch.object(candidate, "git", side_effect=[candidate.BASE_COMMIT.encode() + b"\n", b"", tree]):
            with self.assertRaisesRegex(ValueError, "base-file identity"):
                candidate.base_tree(self.base)

    def test_exact_fixture_is_labelled_experimental(self):
        proof = self.verify_fixture()
        self.assertEqual(proof["files"], 1)
        self.assertFalse(proof["unmodified_upstream_accepted"])
        self.assertEqual(proof["source_sha256"]["module.py"], hashlib.sha256(self.raw).hexdigest())

    def test_missing_candidate_source_is_refused(self):
        (self.source / "module.py").unlink()
        with self.assertRaisesRegex(ValueError, "missing or additional"):
            self.verify_fixture()

    def test_additional_python_source_is_refused(self):
        (self.source / "shadow.py").write_bytes(b"raise RuntimeError('must never execute')\n")
        with self.assertRaisesRegex(ValueError, "missing or additional"):
            self.verify_fixture()

    def test_changed_undeclared_source_is_refused(self):
        (self.source / "module.py").write_bytes(b"VALUE = 2\n")
        with self.assertRaisesRegex(ValueError, "Unreviewed source change"):
            self.verify_fixture()

    def test_additional_empty_namespace_is_refused(self):
        (self.source / "shadow_namespace").mkdir()
        with self.assertRaisesRegex(ValueError, "empty source directories"):
            self.verify_fixture()

    def test_reparse_and_symlink_attributes_are_refused(self):
        for info in (types.SimpleNamespace(st_mode=stat.S_IFREG, st_file_attributes=0x400),
                     types.SimpleNamespace(st_mode=stat.S_IFLNK, st_file_attributes=0)):
            fake = types.SimpleNamespace(lstat=lambda value=info: value)
            with self.assertRaisesRegex(ValueError, "reparse"):
                candidate.ordinary(fake)

    def test_existing_output_is_not_replayed_or_overwritten(self):
        output = self.root / "existing"
        output.mkdir()
        sentinel = output / "receipt.txt"
        sentinel.write_bytes(b"unknown prior outcome")
        with patch.object(candidate, "base_tree") as mocked:
            with self.assertRaisesRegex(ValueError, "fresh separate"):
                candidate.prepare_candidate(self.base, output, self.root / "unused.patch")
            mocked.assert_not_called()
        self.assertEqual(sentinel.read_bytes(), b"unknown prior outcome")

    def test_malformed_blob_stream_retains_failed_preparation(self):
        output = self.root / "failed-attempt"
        with patch.object(candidate, "base_tree", return_value=self.tree), \
                patch.object(candidate, "patch_bytes", return_value=b"fixture patch"), \
                patch.object(candidate, "git", return_value=b"not-a-blob\n"):
            with self.assertRaisesRegex(ValueError, "batch header"):
                candidate.prepare_candidate(self.base, output, self.root / "unused.patch")
        receipt = json.loads((output / "preparation.json").read_text(encoding="utf-8"))
        self.assertEqual(receipt["state"], "FAILED_PREPARATION_RETAINED_NO_RETRY")
        self.assertFalse(receipt["base_modified"])
        self.assertFalse(receipt["sdk_executed"])
        self.assertEqual(list((output / "source").iterdir()), [])


if __name__ == "__main__":
    unittest.main()
