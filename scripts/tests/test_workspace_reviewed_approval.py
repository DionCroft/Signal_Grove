"""Portable checks for the fixed authenticated approval packet and write policy."""
from dataclasses import replace
import json
import unittest

from server_extensions.worker_workspaces.approval_envelope import ApprovalRefused, ReviewedWrite, sign, verify
from server_extensions.worker_workspaces.reviewed_operator import parse_write_policy
from server_extensions.worker_workspaces.workspace_provider import WorkspaceError


class ReviewedApprovalPacketTest(unittest.TestCase):
    key = bytes(range(32))
    approval = ReviewedWrite(
        authority="coordinator-fixture", scope_id="a" * 64, profile="fixture-profile", project="fixture",
        workspace=r"E:\fixture\work", branch="review/example", commit="b" * 40, worker="WINDOWS-WORKER",
        worker_instance="c81f9acd-bf13-4bc4-8f9b-5ee34f52df07", checkout_revision="c" * 64, access="write",
        task="保存便笺\nAdd a note.", execution_id="d" * 32, review_digest="e" * 64,
        write_policy_revision="f" * 64, issued_at=1000, expires_at=1050,
    )

    def test_round_trip_and_canonical_order(self):
        original = sign(self.approval, self.key)
        alternate = json.dumps(json.loads(original), indent=2).encode("utf-8")
        self.assertEqual(self.approval, verify(alternate, self.key, now=1000))
        self.assertEqual(original, sign(verify(alternate, self.key, now=1000), self.key))

    def test_full_length_unicode_task_is_supported(self):
        approval = replace(self.approval, task="🪴" * 8192)
        self.assertEqual(approval, verify(sign(approval, self.key), self.key, now=1000))

    def test_other_key_or_changed_review_is_denied(self):
        original = sign(self.approval, self.key)
        with self.assertRaisesRegex(ApprovalRefused, "DENIED"):
            verify(original, bytes(reversed(self.key)), now=1000)
        for field, value in vars(self.approval).items():
            with self.subTest(field=field):
                changed = json.loads(original)
                changed["approval"][field] = value + ("different" if type(value) is str else 1)
                with self.assertRaisesRegex(ApprovalRefused, "DENIED"):
                    verify(json.dumps(changed).encode("utf-8"), self.key, now=1000)

    def test_duplicate_fields_and_excessive_packet_fail_closed(self):
        original = sign(self.approval, self.key)
        for raw in (b'{"schema":1,' + original[1:], b"x" * 65537, b"\xff", b"[1]"):
            with self.assertRaisesRegex(ApprovalRefused, "INVALID"):
                verify(raw, self.key, now=1000)

    def test_expiry_and_future_issue_are_checked_with_distinct_integer_fields(self):
        original = sign(self.approval, self.key)
        for now in (994, 1050, 1051):
            with self.subTest(now=now), self.assertRaisesRegex(ApprovalRefused, "EXPIRED"):
                verify(original, self.key, now=now)
        for changes in ({"issued_at": True}, {"expires_at": 1181}, {"access": "read"},
                        {"task": "contains a control\u0000"}, {"commit": "short"},
                        {"worker_instance": "example"}, {"review_digest": "wrong"}):
            with self.subTest(changes=changes), self.assertRaisesRegex(ApprovalRefused, "INVALID"):
                sign(replace(self.approval, **changes), self.key)


class ProtectedWritePolicyFormatTest(unittest.TestCase):
    def setUp(self):
        self.grant = {"scope_id": "a" * 64, "project": "fixture", "profile": "fixture-profile",
                      "model_profile": "native-test", "native_scope": "b" * 64 + ":fixture"}
        self.packet = {"schema": 1, "authority": "coordinator-fixture", "device": "WINDOWS-WORKER",
                       "owner_sid": "S-1-5-21-1234-5678-9012-1001", "grants": [self.grant]}

    def test_policy_exact_bytes_and_exact_scope_profile(self):
        raw = json.dumps(self.packet).encode()
        policy = parse_write_policy(raw)
        self.assertNotEqual(policy.revision, parse_write_policy(raw + b"\n").revision)
        self.assertEqual(self.grant["model_profile"], policy.grant("a" * 64, "fixture-profile", "fixture").model_profile)
        with self.assertRaisesRegex(WorkspaceError, "DENIED"):
            policy.grant("a" * 64, "other", "fixture")

    def test_reject_unknown_duplicate_or_invalid_grants(self):
        for changes in ({"schema": True}, {"grants": [self.grant, self.grant]}, {"grants": {"scope": "a"}},
                        {"grants": [{**self.grant, "native_scope": "arbitrary"}]},
                        {"grants": [{**self.grant, "root": r"E:\unreviewed"}]}, {"device": "other"}):
            with self.subTest(changes=changes), self.assertRaisesRegex(WorkspaceError, "INVALID"):
                parse_write_policy(json.dumps({**self.packet, **changes}).encode())
