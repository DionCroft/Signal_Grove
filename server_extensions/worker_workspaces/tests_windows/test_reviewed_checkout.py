"""Disposable Git checkouts behind the actual Windows workspace handles."""
from dataclasses import replace
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from server_extensions.worker_workspaces.reviewed_checkout import hold_reviewed_checkout, observe_checkout
from server_extensions.worker_workspaces.workspace_provider import RegisteredProject, WorkspaceError, WorkspaceProvider


class ReviewedCheckoutTest(unittest.TestCase):
    def setUp(self):
        parent = Path(os.environ["TEMP"]).resolve(strict=True)
        self.assertNotEqual("c:", parent.drive.lower())
        directory = tempfile.TemporaryDirectory(prefix="reviewed-checkout-", dir=parent)
        self.addCleanup(directory.cleanup)
        self.base = Path(directory.name)
        self.assertTrue(self.base.resolve().is_relative_to(parent))
        self.root = self.base / "project checkout"
        self.root.mkdir()
        located_git = shutil.which("git.exe")
        self.assertIsNotNone(located_git, "Native Git executable is required")
        self.git = Path(located_git).resolve(strict=True)
        self.fixture_env = {
            **os.environ, "GIT_CONFIG_NOSYSTEM": "1", "GIT_CONFIG_GLOBAL": os.devnull,
            "GIT_AUTHOR_NAME": "Fixture", "GIT_AUTHOR_EMAIL": "fixture@example.invalid",
            "GIT_COMMITTER_NAME": "Fixture", "GIT_COMMITTER_EMAIL": "fixture@example.invalid",
        }
        self.git_call("init", "--initial-branch=review/feature")
        self.git_call("commit", "--allow-empty", "--message=fixture")
        scope = "a" * 64
        self.provider = WorkspaceProvider(lambda _, __: self.registration, scope_id=scope)
        self.registration = RegisteredProject("fixture", self.provider.device, str(self.root), "b" * 64, scope)

    def git_call(self, *arguments, check=True):
        return subprocess.run(
            [str(self.git), *arguments], cwd=self.root, env=self.fixture_env,
            capture_output=True, check=check, timeout=10,
        )

    def observe(self):
        return observe_checkout(self.provider, "fixture", git_executable=self.git)

    def hold(self, expected, *, provider=None):
        return hold_reviewed_checkout(provider or self.provider, expected, git_executable=self.git)

    def test_reviewed_checkout_keeps_the_same_head_open_at_admission(self):
        expected = self.observe()
        self.assertEqual(expected.branch, "review/feature")
        self.assertEqual(expected.commit, self.git_call("rev-parse", "HEAD").stdout.decode().strip())
        self.assertEqual(expected.worker_instance, self.provider.instance)
        with self.hold(expected) as current:
            self.assertEqual(current, expected)
            attempt = self.git_call("symbolic-ref", "HEAD", "refs/heads/other", check=False)
            self.assertNotEqual(attempt.returncode, 0)
        self.assertEqual(self.observe(), expected)

    def assert_branch_reference_held(self):
        expected = self.observe()
        new_tip = self.git_call(
            "commit-tree", "HEAD^{tree}", "-p", expected.commit, "-m", "new fixture tip",
        ).stdout.decode().strip()
        ref = "refs/heads/review/feature"
        with self.assertRaisesRegex(RuntimeError, "fixture admission failed"):
            with self.hold(expected):
                attempt = self.git_call("update-ref", ref, new_tip, expected.commit, check=False)
                self.assertNotEqual(attempt.returncode, 0)
                self.assertEqual(self.git_call("rev-parse", "HEAD").stdout.decode().strip(), expected.commit)
                raise RuntimeError("fixture admission failed")
        self.assertEqual(self.observe(), expected)
        self.git_call("update-ref", ref, new_tip, expected.commit)
        self.assertEqual(self.observe().commit, new_tip)

    def test_loose_branch_tip_is_held_and_released_on_admission_failure(self):
        self.assert_branch_reference_held()

    def test_packed_branch_tip_is_held_and_released_on_admission_failure(self):
        self.git_call("pack-refs", "--all", "--prune")
        self.assertFalse((self.root / ".git/refs/heads/review/feature").exists())
        self.assert_branch_reference_held()

    def test_verify_only_transaction_excludes_checkout_hooks(self):
        expected = self.observe()
        marker = self.root / "hook.marker"
        hook = self.root / ".git/hooks/reference-transaction"
        hook.write_bytes(b'#!/bin/sh\nif test "$1" = prepared; then echo active > hook.marker; fi\n')
        packet = f"start\nverify refs/heads/review/feature {expected.commit}\nprepare\nabort\n".encode()
        subprocess.run(
            [str(self.git), "update-ref", "--stdin"], cwd=self.root, env=self.fixture_env,
            input=packet, capture_output=True, timeout=10, check=True,
        )
        self.assertTrue(marker.exists())
        marker.unlink()
        with self.hold(expected):
            self.assertFalse(marker.exists())
        self.assertFalse(marker.exists())
        self.assertEqual(self.observe(), expected)

    def test_branch_alias_cannot_hold_a_different_reference_at_the_same_commit(self):
        self.git_call("branch", "other")
        self.git_call("symbolic-ref", "refs/heads/review/feature", "refs/heads/other")
        with self.assertRaisesRegex(WorkspaceError, "UNSUPPORTED"):
            self.observe()

    def test_tip_change_between_observation_and_prepare_refuses_admission(self):
        from server_extensions.worker_workspaces import reviewed_checkout

        expected = self.observe()
        real_hold = reviewed_checkout._hold_branch_ref

        def race(*args):
            self.git_call("commit", "--allow-empty", "--message=changed between check and hold")
            return real_hold(*args)

        # HEAD is held against replacement, but this commit may still advance
        # the ref until the verify-only transaction acquires its own lock.
        with patch.object(reviewed_checkout, "_hold_branch_ref", side_effect=race):
            with self.assertRaisesRegex(WorkspaceError, "CHANGED"), self.hold(expected):
                self.fail("A stale reviewed commit reached admission")

    def test_branch_switch_at_same_commit_and_changed_tip_both_refuse(self):
        expected = self.observe()
        self.git_call("switch", "-c", "other")
        with self.assertRaisesRegex(WorkspaceError, "CHANGED"), self.hold(expected):
            self.fail("Different branch cannot reach admission")
        self.git_call("switch", "review/feature")
        self.git_call("commit", "--allow-empty", "--message=changed-tip")
        with self.assertRaisesRegex(WorkspaceError, "CHANGED"), self.hold(expected):
            self.fail("Changed commit cannot reach admission")

    def test_renamed_and_replaced_root_cannot_inherit_review(self):
        expected = self.observe()
        previous = self.base / "old-root"
        self.root.rename(previous)
        shutil.copytree(previous, self.root)
        self.assertEqual(self.observe().commit, expected.commit)
        with self.assertRaisesRegex(WorkspaceError, "CHANGED"), self.hold(expected):
            self.fail("Replacement path cannot inherit the earlier root's review")

    def test_restart_and_registration_revision_cannot_inherit_review(self):
        expected = self.observe()
        replacement = WorkspaceProvider(self.provider.lookup, scope_id=self.provider.scope)
        with self.assertRaisesRegex(WorkspaceError, "CHANGED"), self.hold(expected, provider=replacement):
            self.fail("Replacement worker cannot inherit review")
        self.registration = replace(self.registration, registry_revision="c" * 64)
        with self.assertRaisesRegex(WorkspaceError, "CHANGED"), self.hold(expected):
            self.fail("New policy or registration cannot inherit review")
        self.provider.retire()
        with self.assertRaisesRegex(WorkspaceError, "UNAVAILABLE"), self.hold(expected):
            self.fail("Retired worker cannot admit")

    def test_rechecks_registration_after_native_git_reads(self):
        enrolled = self.registration
        updated = replace(enrolled, registry_revision="c" * 64)
        observations = iter([enrolled, updated])
        with patch.object(self.provider, "lookup", side_effect=lambda *_: next(observations)):
            with self.assertRaisesRegex(WorkspaceError, "CHANGED"):
                self.observe()

    def test_inherited_git_redirection_cannot_change_the_registered_root(self):
        expected = self.observe()
        with patch.dict(os.environ, {
            "GIT_DIR": str(self.base / "foreign"),
            "GIT_WORK_TREE": str(self.base),
            "GIT_CONFIG_COUNT": "1",
            "GIT_CONFIG_KEY_0": "core.worktree",
            "GIT_CONFIG_VALUE_0": str(self.base),
        }):
            self.assertEqual(self.observe(), expected)

    def test_redirected_git_common_directory_cannot_inherit_a_review(self):
        expected = self.observe()
        common = self.base / "other-git-metadata"
        shutil.copytree(self.root / ".git", common)
        (self.root / ".git/commondir").write_text(common.as_posix() + "\n", encoding="utf-8")
        with self.assertRaisesRegex(WorkspaceError, "CHANGED"), self.hold(expected):
            self.fail("Redirected Git metadata cannot reach admission")

    def test_repository_worktree_redirect_is_refused(self):
        self.git_call("config", "core.worktree", str(self.base))
        with self.assertRaisesRegex(WorkspaceError, "CHANGED"):
            self.observe()

    def test_detached_and_linked_checkout_are_not_admitted(self):
        linked = self.base / "linked-checkout"
        self.git_call("worktree", "add", "-b", "linked", str(linked))
        self.registration = replace(self.registration, root=str(linked))
        with self.assertRaises(WorkspaceError):
            self.observe()
        self.registration = replace(self.registration, root=str(self.root))
        self.git_call("switch", "--detach")
        with self.assertRaisesRegex(WorkspaceError, "UNSUPPORTED"):
            self.observe()
