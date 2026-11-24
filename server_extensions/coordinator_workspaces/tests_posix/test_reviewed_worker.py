"""Protected POSIX signing and original worker receipt fixtures."""
from dataclasses import replace
from hashlib import sha256
import os
import unittest
from unittest.mock import patch

from server_extensions.coordinator_workspaces import reviewed_worker as bridge
from server_extensions.coordinator_workspaces.coordinator import NativeReadContext, ReadRefused, scope_for
from server_extensions.coordinator_workspaces.cron_project_decisions import ApprovedOccurrence, ProjectOccurrence
from server_extensions.coordinator_workspaces.cron_run_once_approval import RunOnceScope
from server_extensions.coordinator_workspaces.tests_posix import test_cron_project_decisions as receipts
from server_extensions.worker_workspaces.approval_envelope import verify


class ReviewedWorkerPosixTest(unittest.TestCase):
    def setUp(self):
        fixture = receipts.ProjectDecisionReceiptPosixTest()
        self.addCleanup(fixture.doCleanups)
        fixture.setUp()
        self.selection = ProjectOccurrence("default", "fixture-job", fixture.occurrence_id)
        ident = self.selection.execution_id()
        fixture.execution_id = ident
        self.db = fixture.fixture()
        self.db.execute("UPDATE signal_grove_project_decisions SET outcome=NULL")
        self.db.execute("UPDATE executions SET status='running', finished_at=NULL")
        self.db.commit()
        self.key = os.urandom(32)
        self.key_path = fixture.home / "review-key.bin"
        self.key_path.write_bytes(self.key)
        self.key_path.chmod(0o600)
        scope = RunOnceScope(
            "project", r"E:\fixture\repo", "review/job", "WIN-FIXTURE", "a8858069-2113-43cb-bc54-e19d71149932",
            "write", "e" * 64, "Write a private fixture note.", "f" * 40, "a" * 64, "b" * 64,
        )
        actor = NativeReadContext("fixture-owner", str(fixture.home), "transport", "instance")
        self.run_record = ApprovedOccurrence(
            self.selection, scope, fixture.instant,
            {"id": self.selection.job_id, "execution_id": ident, "_scheduled_instant": fixture.instant,
             "fire_claim": {"by": "local-native-owner", "signal_grove_project_occurrence": ident}},
            actor, "c" * 64, 130.0,
        )
        self.sent = []
        self.lookups = []
        self.status = "completed"
        self.result_id = ident
        self.reply_lost = False
        self.original_digest = None
        self.legacy_receipt = False
        route = bridge.ProjectWorkerRoute(fixture.home, self.key_path.name, self.submit, self.observe)
        self.dispatch = bridge.WorkerProjectDispatch(
            "fixture-coordinator", {scope.worker: route}, clock=lambda: 100.0, wall_clock=lambda: 1200.25,
        )

    def submit(self, raw):
        self.sent.append(verify(raw, self.key, now=1200))
        if self.original_digest is None:
            self.original_digest = sha256(raw).hexdigest()
        if self.reply_lost:
            raise ConnectionError("submission accepted but response lost")
        return {"id": self.result_id, "state": "completed"}

    def observe(self, selector):
        self.lookups.append(selector)
        receipt = {"id": self.result_id, "state": self.status}
        if not self.legacy_receipt:
            receipt["approval_sha256"] = self.original_digest
        return receipt

    def test_consumed_review_signs_exact_scope_and_original_execution(self):
        self.assertEqual("completed", self.dispatch(self.run_record))
        self.assertEqual(1, len(self.sent))
        self.assertEqual(1, len(self.lookups))
        packet, lookup = self.sent[0], self.lookups[0]
        scope, actor = self.run_record.scope, self.run_record.actor
        self.assertEqual("fixture-coordinator", packet.authority)
        self.assertEqual(scope_for(packet.authority, actor.subject, actor.profile_id), packet.scope_id)
        self.assertEqual("default", packet.profile)
        self.assertEqual(self.selection.execution_id(), packet.execution_id)
        self.assertEqual(self.run_record.review_digest, packet.review_digest)
        self.assertEqual((1200, 1230), (packet.issued_at, packet.expires_at))
        for field in ("project", "workspace", "branch", "commit", "worker", "worker_instance",
                      "checkout_revision", "write_policy_revision", "access", "task"):
            self.assertEqual(getattr(scope, field), getattr(packet, field), field)
        self.assertEqual(packet.execution_id, lookup.execution_id)
        self.assertEqual(packet.scope_id, lookup.scope_id)
        self.assertEqual(packet.profile, lookup.profile)
        self.assertEqual(packet.project, lookup.project)
        self.assertEqual(packet.worker, lookup.worker)
        self.assertEqual(packet.worker_instance, lookup.worker_instance)
        self.assertEqual(self.original_digest, lookup.approval_sha256)

    def test_lost_submit_reply_recovers_original_receipt_once(self):
        self.reply_lost = True
        self.assertEqual("completed", self.dispatch(self.run_record))
        self.assertEqual(1, len(self.sent))
        self.assertEqual([self.selection.execution_id()], [ref.execution_id for ref in self.lookups])

    def test_foreign_receipt_remains_unknown(self):
        self.result_id = "d" * 32
        self.assertIsNone(self.dispatch(self.run_record))
        self.assertEqual(1, len(self.sent))

    def test_same_execution_id_from_another_approval_cannot_settle_lost_submit(self):
        self.reply_lost = True
        self.original_digest = "e" * 64
        self.assertIsNone(self.dispatch(self.run_record))
        self.assertEqual(1, len(self.sent))
        self.assertEqual(1, len(self.lookups))
        self.assertNotEqual(self.original_digest, self.lookups[0].approval_sha256)

    def test_legacy_receipt_lacks_evidence_for_new_signed_dispatch(self):
        self.legacy_receipt = True
        self.assertIsNone(self.dispatch(self.run_record))
        self.assertEqual(1, len(self.sent))

    def test_unsettled_receipt_remains_unknown(self):
        self.status = "running"
        self.assertIsNone(self.dispatch(self.run_record))
        self.assertEqual(1, len(self.sent))

    def test_denied_or_other_source_never_signs(self):
        self.db.execute("UPDATE executions SET source='other'")
        self.db.commit()
        with self.assertRaisesRegex(ReadRefused, "CHANGED"):
            self.dispatch(self.run_record)
        self.db.execute("UPDATE signal_grove_project_decisions SET decision='deny', outcome='cancelled'")
        self.db.commit()
        with self.assertRaisesRegex(ReadRefused, "CHANGED"):
            self.dispatch(self.run_record)
        self.assertEqual([], self.sent)
        self.assertEqual([], self.lookups)

    def test_changed_claim_checkout_and_expired_review_refuse_before_submission(self):
        snapshot = self.run_record.snapshot | {"fire_claim": {}}
        scope = replace(self.run_record.scope, checkout_revision=None, write_policy_revision=None)
        for changed, code in ((replace(self.run_record, snapshot=snapshot), "DENIED"),
                              (replace(self.run_record, scope=scope), "DENIED"),
                              (replace(self.run_record, expires_at=100.8), "EXPIRED")):
            with self.subTest(code=code), self.assertRaisesRegex(ReadRefused, code):
                self.dispatch(changed)
        self.assertEqual([], self.sent)

    def test_loose_key_modes_and_short_keys_refuse(self):
        self.key_path.chmod(0o644)
        with self.assertRaisesRegex(ReadRefused, "UNAVAILABLE"):
            self.dispatch(self.run_record)
        self.key_path.chmod(0o600)
        self.key_path.write_bytes(b"short")
        with self.assertRaisesRegex(ReadRefused, "UNAVAILABLE"):
            self.dispatch(self.run_record)
        self.assertEqual([], self.sent)

    def test_claim_identity_rechecked_after_reading_signing_key(self):
        real_read = bridge.read_owner_file

        def changed(*args):
            data = real_read(*args)
            self.db.execute("UPDATE executions SET status='unknown'")
            self.db.execute("UPDATE signal_grove_project_decisions SET outcome='unknown'")
            self.db.commit()
            return data

        with patch.object(bridge, "read_owner_file", side_effect=changed):
            with self.assertRaisesRegex(ReadRefused, "CHANGED"):
                self.dispatch(self.run_record)
        self.assertEqual([], self.sent)
