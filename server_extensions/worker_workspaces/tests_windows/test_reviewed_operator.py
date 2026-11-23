"""Native Windows checks for the owner policy and reviewed queue admission."""
from base64 import b64encode
from contextlib import contextmanager
from dataclasses import asdict, replace
import json
import os
import sqlite3
import subprocess
from threading import Barrier, Thread
import unittest

from server_extensions.worker_workspaces.approval_envelope import ReviewedWrite, sign
from server_extensions.worker_workspaces.broker_enrollment import OwnerObservation
from server_extensions.worker_workspaces.local_pipe import _Windows
from server_extensions.worker_workspaces.protected_operator_file import read_protected_file
from server_extensions.worker_workspaces.reviewed_operator import ReviewedWorkerOperator, parse_write_policy
from server_extensions.worker_workspaces.workspace_provider import WorkspaceError
from server_extensions.worker_workspaces.tests_windows import test_reviewed_checkout as checkout_fixture


class ReviewedOperatorTest(unittest.TestCase):
    def setUp(self):
        self.fixture = checkout_fixture.ReviewedCheckoutTest()
        self.addCleanup(self.fixture.doCleanups)
        self.fixture.setUp()
        sid, started, session = _Windows().process_identity(os.getpid())
        self.owner = OwnerObservation(self.fixture.provider.device, sid, os.getpid(), started, session, True)
        self.policy_path = self.fixture.base / "write-policy.json"
        self.key_path = self.fixture.base / "write-key.bin"
        self.db_path = self.fixture.base / "queue.sqlite3"
        self.key = os.urandom(32)
        self.policy = {
            "schema": 1, "authority": "test-coordinator", "device": self.owner.device, "owner_sid": sid,
            "grants": [{"scope_id": self.fixture.provider.scope, "profile": "test-profile", "project": "fixture",
                        "model_profile": "approved-model", "native_scope": "a" * 64 + ":fixture"}],
        }
        self.policy_path.write_bytes(json.dumps(self.policy).encode("ascii"))
        self.key_path.write_bytes(self.key)
        self.protect(self.policy_path)
        self.protect(self.key_path)
        self.wall = 1_800_000_000
        self.monotonic = 100.0
        self.operator = self.make_operator()
        review = self.operator.prepare(self.fixture.provider.scope, "test-profile", "fixture")
        self.approval = ReviewedWrite(
            **asdict(review), access="write", task="Write an inert fixture note.",
            execution_id="d" * 32, review_digest="b" * 64, issued_at=self.wall, expires_at=self.wall + 30,
        )

    def protect(self, path):
        command = """
        $ErrorActionPreference = 'Stop'
        $security = Get-Acl -LiteralPath $env:SG_FIXTURE_ACL_FILE
        $sddl = 'D:P(A;;FA;;;' + $env:SG_FIXTURE_OWNER + ')'
        $security.SetSecurityDescriptorSddlForm($sddl, [Security.AccessControl.AccessControlSections]::Access)
        Set-Acl -LiteralPath $env:SG_FIXTURE_ACL_FILE -AclObject $security
        """
        encoded = b64encode(command.encode("utf-16-le")).decode("ascii")
        result = subprocess.run(
            ["pwsh.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded],
            env={**os.environ, "SG_FIXTURE_OWNER": self.owner.owner_sid, "SG_FIXTURE_ACL_FILE": str(path)},
            capture_output=True, timeout=10,
        )
        error = result.stderr.decode(errors="replace").replace(self.owner.owner_sid, "<owner SID>")
        self.assertEqual(0, result.returncode, error.replace(str(path), "<fixture>"))

    def acl(self, path, *args):
        subprocess.run(["icacls.exe", str(path), *args], check=True, capture_output=True, timeout=10)

    def make_operator(self):
        return ReviewedWorkerOperator(
            policy_path=str(self.policy_path), key_path=str(self.key_path), queue_db=self.queue_db,
            provider_for_scope=lambda scope: self.fixture.provider if scope == self.fixture.provider.scope else None,
            git_executable=self.fixture.git, owner_observer=lambda: self.owner, wall_clock=lambda: self.wall,
            clock=lambda: self.monotonic,
        )

    @contextmanager
    def queue_db(self):
        db = sqlite3.connect(self.db_path, timeout=10)
        try:
            db.execute("CREATE TABLE IF NOT EXISTS requests(id TEXT PRIMARY KEY,payload TEXT,state TEXT,created REAL,lease REAL)")
            db.commit()
            with db:
                yield db
        finally:
            db.close()

    def rows(self):
        with self.queue_db() as db:
            return db.execute("SELECT id,payload,state FROM requests ORDER BY id").fetchall()

    def packet(self, **changes):
        return sign(replace(self.approval, **changes), self.key)

    def read_key(self, path):
        return read_protected_file(str(path), owner_sid=self.owner.owner_sid, maximum=32)

    def test_native_acl_denies_inherited_or_extra_reader(self):
        self.assertEqual(self.key, self.read_key(self.key_path))
        subprocess.run(["icacls.exe", str(self.key_path), "/grant", "*S-1-1-0:(R)"],
                       check=True, capture_output=True, timeout=10)
        with self.assertRaisesRegex(WorkspaceError, "DENIED"):
            self.read_key(self.key_path)
        inherited = self.fixture.base / "inherited.bin"
        inherited.write_bytes(self.key)
        with self.assertRaisesRegex(WorkspaceError, "DENIED"):
            self.read_key(inherited)
        with self.assertRaisesRegex(WorkspaceError, "DENIED"):
            self.operator.accept(self.packet())
        self.assertFalse(self.rows())

    def test_native_acl_denies_linked_key(self):
        alias = self.fixture.base / "alias.bin"
        os.link(self.key_path, alias)
        with self.assertRaisesRegex(WorkspaceError, "DENIED"):
            self.read_key(self.key_path)
        with self.assertRaisesRegex(WorkspaceError, "DENIED"):
            self.read_key(alias)

    def test_prepare_refuses_a_scope_without_a_separate_write_grant(self):
        self.policy["grants"] = []
        self.policy_path.write_bytes(json.dumps(self.policy).encode("ascii"))
        self.assertEqual((), parse_write_policy(self.policy_path.read_bytes()).grants)
        with self.assertRaisesRegex(WorkspaceError, "DENIED"):
            self.operator.prepare(self.fixture.provider.scope, "test-profile", "fixture")
        self.assertFalse(self.rows())

    def test_signed_wrong_scope_or_checkout_never_creates_a_queue_row(self):
        changes = [
            {"authority": "different"}, {"scope_id": "f" * 64}, {"profile": "other"}, {"project": "other"},
            {"workspace": self.approval.workspace + "\\different"}, {"branch": "other"},
            {"commit": "f" * 40}, {"worker": "OTHER"}, {"worker_instance": "2a13e07a-3739-4a7c-a891-86a431033315"},
            {"checkout_revision": "f" * 64}, {"write_policy_revision": "f" * 64},
        ]
        for fields in changes:
            with self.subTest(field=next(iter(fields))):
                with self.assertRaisesRegex(WorkspaceError, "CHANGED|DENIED"):
                    self.operator.accept(self.packet(**fields))
        self.assertFalse(self.rows())

    def test_registry_change_invalidates_the_same_branch_and_commit(self):
        self.fixture.registration = replace(self.fixture.registration, registry_revision="e" * 64)
        with self.assertRaisesRegex(WorkspaceError, "CHANGED"):
            self.operator.accept(self.packet())
        self.assertFalse(self.rows())

    def test_native_reservation_is_idempotent_and_one_use(self):
        original = self.packet()
        first = self.operator.accept(original)
        self.assertEqual({"id": self.approval.execution_id, "state": "ready"}, first)
        self.assertEqual(first, self.operator.accept(json.dumps(json.loads(original), indent=2).encode()))
        with self.assertRaisesRegex(WorkspaceError, "CHANGED"):
            self.operator.accept(self.packet(task="Different approved task"))
        ident, raw, _ = self.rows()[0]
        native = json.loads(raw)
        self.assertEqual((self.approval.task, "approved-model"), (native["prompt"], native["profile"]))
        self.assertEqual({"action", "id", "project", "profile", "scope", "prompt", "binding"}, set(native))
        gate = self.operator.lookup(ident)
        with gate.claim(native) as current:
            self.assertEqual(self.approval.commit, current.commit)
        with gate.launch(native):
            pass
        with self.assertRaisesRegex(WorkspaceError, "DENIED"):
            with gate.launch(native):
                self.fail("Review launched twice")

    def test_concurrent_retry_observes_one_original_row(self):
        both = Barrier(2)
        results, errors = [], []
        packet = self.packet()

        def accept():
            try:
                both.wait(5)
                results.append(self.operator.accept(packet))
            except Exception as error:
                errors.append(error)

        threads = [Thread(target=accept, daemon=True) for _ in range(2)]
        for thread in threads:
            thread.start()
        for thread in threads:
            thread.join(15)
            self.assertFalse(thread.is_alive())
        self.assertEqual([], errors)
        self.assertEqual([{"id": self.approval.execution_id, "state": "ready"}] * 2, results)
        self.assertEqual(1, len(self.rows()))

    def test_existing_legacy_id_cannot_be_rebound(self):
        with self.queue_db() as db:
            db.execute("INSERT INTO requests VALUES(?,?,?,?,?)",
                       (self.approval.execution_id, "{}", "awaiting_ack", self.wall, 0))
        with self.assertRaisesRegex(WorkspaceError, "CHANGED"):
            self.operator.accept(self.packet())
        self.assertEqual([(self.approval.execution_id, "{}", "awaiting_ack")], self.rows())

    def test_restart_observes_original_unknown_claim_after_branch_drift(self):
        packet = self.packet()
        ident = self.operator.accept(packet)["id"]
        with self.queue_db() as db:
            db.execute("UPDATE requests SET state='unknown' WHERE id=?", (ident,))
        self.fixture.git_call("commit", "--allow-empty", "--message=after uncertain claim")
        fresh = self.make_operator()
        self.assertEqual({"id": ident, "state": "unknown"}, fresh.accept(packet))
        with self.assertRaisesRegex(WorkspaceError, "DENIED"):
            fresh.lookup(ident)
        self.assertEqual(1, len(self.rows()))

    def test_policy_revocation_and_key_rotation_stop_unlaunched_work(self):
        ident = self.operator.accept(self.packet())["id"]
        native = json.loads(self.rows()[0][1])
        gate = self.operator.lookup(ident)
        with gate.claim(native):
            pass
        self.key_path.write_bytes(os.urandom(32))
        with self.assertRaisesRegex(WorkspaceError, "CHANGED"):
            with gate.launch(native):
                self.fail("A rotated signing key no longer grants the write")
        self.key_path.write_bytes(self.key)
        packet = self.packet(execution_id="e" * 32)
        self.assertEqual("ready", self.operator.accept(packet)["state"])
        self.policy["grants"] = []
        self.policy_path.write_bytes(json.dumps(self.policy).encode("ascii"))
        with self.assertRaisesRegex(WorkspaceError, "CHANGED"):
            with self.operator.lookup("e" * 32).claim(json.loads(self.rows()[1][1])):
                self.fail("A revoked policy granted the claim")

    def test_disconnected_owner_or_expired_approval_stops_queue_admission(self):
        original = self.owner
        self.owner = replace(original, active=False)
        with self.assertRaisesRegex(WorkspaceError, "DENIED"):
            self.operator.accept(self.packet())
        self.owner = original
        ident = self.operator.accept(self.packet())["id"]
        gate = self.operator.lookup(ident)
        native = json.loads(self.rows()[0][1])
        self.wall += 30
        with self.assertRaisesRegex(WorkspaceError, "EXPIRED"):
            with gate.claim(native):
                self.fail("Expired approval reached claim")
        self.monotonic += 30
        with self.assertRaisesRegex(WorkspaceError, "DENIED"):
            self.operator.lookup(ident)
