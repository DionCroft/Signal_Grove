"""Check the reviewed coordinator client over its real subprocess framing."""
from dataclasses import replace
import sys
import unittest

from server_extensions.coordinator_workspaces.coordinator import NativeReadContext, ReadRefused, scope_for
from server_extensions.coordinator_workspaces.reviewed_ssh import ReviewedWorkerSsh, reviewed_ssh_argv
from server_extensions.coordinator_workspaces.reviewed_worker import OriginalWorkerReceipt
from server_extensions.worker_workspaces.approval_envelope import ReviewedWrite, sign


WORKER = "FIXTURE-WORKER"
INITIAL = "a8858069-2113-43cb-bc54-e19d71149932"
CURRENT = "0b4ac557-5529-431f-b498-c7f34b899d36"
AUTHORITY = "fixture-coordinator"
ACTOR = NativeReadContext("fixture-owner", "/fixture/profile", "transport", "instance")
SCOPE = scope_for(AUTHORITY, ACTOR.subject, ACTOR.profile_id)
ID = "d" * 32
KEY = bytes(range(32))
HELPER = r'''
from hashlib import sha256
import json, sys
content = sys.stdin.buffer.read()
packet = json.loads(content)
payload = packet["payload"]
assert set(packet) == {"action", "scope_id", "worker_instance", "payload"}
assert packet["action"] == "workspace_reviewed" and packet["scope_id"] == payload["scope_id"]
operation = payload["operation"]
identity = None
if operation == "reviewed_prepare":
    assert packet["worker_instance"] is None
    instance = "a8858069-2113-43cb-bc54-e19d71149932"
    result = dict(authority="fixture-coordinator", scope_id=payload["scope_id"], profile=payload["profile"],
                  project=payload["project_id"], workspace="fixture-root", branch="fixture-branch",
                  commit="a"*40, worker="FIXTURE-WORKER", worker_instance=instance,
                  checkout_revision="c"*64, write_policy_revision="f"*64)
elif operation == "reviewed_submit":
    assert len(content) > 32 * 1024
    canonical = json.dumps(payload["approval"], ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()
    assert payload["approval_sha256"] == sha256(canonical).hexdigest()
    proof = payload["approval"]["approval"]
    assert payload["project_id"] == proof["project"] and packet["worker_instance"] == proof["worker_instance"]
    assert proof["task"] == "\U0001f332" * 8192
    instance = proof["worker_instance"]
    identity = proof["execution_id"]
    if sys.argv[1] == "lost":
        sys.exit(41)
    result = {"id": identity, "state": "ready"}
else:
    assert operation == "reviewed_observe" and packet["worker_instance"] is None
    assert payload["receipt_worker_instance"] == "a8858069-2113-43cb-bc54-e19d71149932"
    instance = "0b4ac557-5529-431f-b498-c7f34b899d36"
    result = {"id": "e"*32 if sys.argv[1] == "foreign" else payload["execution_id"], "state": "completed"}
    if "approval_sha256" in payload and sys.argv[1] != "legacy-reply":
        result["approval_sha256"] = "e"*64 if sys.argv[1] == "foreign-approval" else payload["approval_sha256"]
print(json.dumps({"ok": True, "result": {"transport": "reply", "reply": {
    "schema": 1, "request_id": "b"*32, "worker_instance": instance, "status": "ok", "result": result,
}}}))
'''


class ReviewedSshTest(unittest.TestCase):
    def client(self, mode="pass", **kwargs):
        return ReviewedWorkerSsh(AUTHORITY, WORKER, (sys.executable, "-B", "-c", "exec(" + repr(HELPER) + ")", mode), **kwargs)

    def approved(self):
        return ReviewedWrite(
            AUTHORITY, SCOPE, "default", "fixture", "fixture-root", "fixture-branch", "a" * 40,
            WORKER, INITIAL, "c" * 64, "write", "🌲" * 8192, ID, "b" * 64, "f" * 64, 1200, 1230,
        )

    def original(self, **changes):
        return replace(OriginalWorkerReceipt(ID, SCOPE, "default", "fixture", WORKER, INITIAL), **changes)

    def test_enrolled_command_uses_strict_host_keys_and_no_interactive_session(self):
        from pathlib import Path

        root = Path(sys.executable).parent
        argv = reviewed_ssh_argv(root / "ssh", root / "fixture-config", "enrolled-device", "fixed approved command")
        self.assertIn("-oStrictHostKeyChecking=yes", argv)
        self.assertIn("-oUpdateHostKeys=no", argv)
        self.assertIn("-T", argv)
        self.assertIn("-oBatchMode=yes", argv)
        self.assertIn("-oClearAllForwardings=yes", argv)
        self.assertEqual(("enrolled-device", "fixed approved command"), argv[-2:])
        with self.assertRaises(ReadRefused):
            reviewed_ssh_argv(root / "ssh", root / "fixture-config", "-F", "command")

    def test_prepare_derives_scope_from_native_actor_and_reviews_exact_checkout(self):
        reviewed = self.client().prepare(ACTOR, "default", "fixture", "Fixture task", policy_revision="e" * 64)
        self.assertEqual((INITIAL, "a" * 40, "c" * 64, "f" * 64),
                         (reviewed.worker_instance, reviewed.commit, reviewed.checkout_revision,
                          reviewed.write_policy_revision))

    def test_full_utf8_approval_and_separate_original_observation(self):
        client = self.client()
        self.assertEqual({"id": ID, "state": "ready"}, client.submit(sign(self.approved(), KEY)))
        self.assertEqual({"id": ID, "state": "completed"}, client.observe(self.original()))

    def test_lost_submission_marks_uncertain_and_original_stays_observable(self):
        client = self.client("lost")
        with self.assertRaises(ReadRefused) as lost:
            client.submit(sign(self.approved(), KEY))
        self.assertTrue(lost.exception.request_submitted)
        self.assertEqual({"id": ID, "state": "completed"}, client.observe(self.original()))
        self.assertEqual({"id": ID, "state": "completed"}, client.project_route("/fixture", "key").observe(self.original()))

    def test_exact_receipt_requires_worker_to_echo_the_checked_signed_digest(self):
        digest = "f" * 64
        receipt = self.original(approval_sha256=digest)
        self.assertEqual({"id": ID, "state": "completed", "approval_sha256": digest},
                         self.client().observe(receipt))
        for mode in ("foreign-approval", "legacy-reply"):
            with self.subTest(mode=mode), self.assertRaisesRegex(ReadRefused, "CHANGED"):
                self.client(mode).observe(receipt)

    def test_observer_refuses_foreign_worker_or_original_id(self):
        with self.assertRaisesRegex(ReadRefused, "DENIED"):
            self.client().observe(self.original(worker="OTHER"))
        with self.assertRaisesRegex(ReadRefused, "CHANGED"):
            self.client("foreign").observe(self.original())
