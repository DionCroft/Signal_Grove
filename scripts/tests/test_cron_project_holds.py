"""Portable policy, exact-occurrence and fail-closed schedule inbox checks."""
from datetime import datetime, timezone
from pathlib import Path
import json
import unittest
from unittest.mock import patch

from server_extensions.coordinator_workspaces import cron_project_holds as holds
from server_extensions.coordinator_workspaces.coordinator import ReadRefused
from server_extensions.coordinator_workspaces.operator_policy import PolicyDocument


class ProjectScheduleHoldsTest(unittest.TestCase):
    home = Path.cwd() / "fixture-schedule-home"
    due = "2026-11-21T09:00:00+01:00"
    now = datetime(2026, 11, 21, 9, tzinfo=timezone.utc)
    query = holds.ScheduleInboxQuery("default")
    rule = {"job_id": "held-job", "project_id": "fixture-project"}
    ordinary_job = {"id": "read-job", "prompt": "known task", "repeat": {"times": None, "completed": 0}}
    ordinary_rule = {"job_id": "read-job", "spec_digest": holds.automatic_spec_digest(ordinary_job)}

    def document(self, *, automatic=None, rules=None, **extra):
        return PolicyDocument(json.dumps({"schema": 1, "automatic": [self.ordinary_rule] if automatic is None else automatic,
                                           "project_writes": [self.rule] if rules is None else rules, **extra}).encode(),
                              "a" * 64)

    def job(self, **changes):
        return {"id": "held-job", "next_run_at": self.due, "enabled": True, "state": "scheduled", **changes}

    def inbox(self, records, *, doc=None):
        with patch.object(holds, "read_owner_document", return_value=doc or self.document()) as reader:
            with patch.object(holds, "_catalogue_records", return_value=(records, "stable-catalogue")) as catalogue:
                reply = holds.read_inbox(self.home, self.query, now=self.now)
            self.assertEqual(reader.call_count, 2)
            reader.assert_called_with(self.home / "cron", holds.POLICY_NAME)
            catalogue.assert_called_with(self.home)
            return reply

    def test_only_the_configured_profile_selector_is_accepted(self):
        self.assertEqual(holds.inbox_query({"expected_profile": "field"}).profile, "field")
        for bad in ("all", "", "FIELD", "../default", False):
            with self.assertRaises(ReadRefused):
                holds.inbox_query({"expected_profile": bad})
        with self.assertRaises(ReadRefused):
            holds.inbox_query({"expected_profile": "default", "profile": "field"})

    def test_unknown_registered_and_redirected_targets_cannot_enter_stock_execution(self):
        directory = self.home / "cron"
        with patch.object(holds, "read_owner_document", return_value=self.document()):
            observed = holds.automatic_jobs(self.home, directory, directory / "jobs.json")
            self.assertEqual(observed, {"read-job": self.ordinary_rule["spec_digest"]})
            self.assertTrue(holds.matches_automatic(observed, self.ordinary_job))
            for changes in ({"prompt": "changed"}, {"workdir": "/different"}, {"script": "different.py"}, {"extra_field": True}):
                self.assertFalse(holds.matches_automatic(observed, self.ordinary_job | changes))
            self.assertTrue(holds.matches_automatic(observed, self.ordinary_job | {
                "next_run_at": self.due, "execution_id": "a" * 32, "repeat": {"times": None, "completed": 2},
            }))
            self.assertFalse(holds.automatic_jobs(self.home, directory / "elsewhere", directory / "jobs.json"))
        with patch.object(holds, "read_owner_document", side_effect=ReadRefused("UNAVAILABLE")):
            self.assertFalse(holds.automatic_jobs(self.home, directory, directory / "jobs.json"))

    def test_malformed_duplicate_or_overlapping_policy_holds_every_job(self):
        cases = (
            self.document(automatic=[self.ordinary_rule, self.ordinary_rule]),
            self.document(automatic=[self.ordinary_rule | {"job_id": "held-job"}]),
            self.document(rules=[self.rule, self.rule]),
            self.document(rules=[self.rule | {"worker": "caller-supplied"}]),
            self.document(automatic=["unknown"]),
            self.document(automatic=[self.ordinary_rule | {"job_id": f"id-{i}"} for i in range(65)]),
            self.document(fallback="allow"),
            PolicyDocument(b'{"schema":true,"automatic":[],"project_writes":[]}', "a" * 64),
        )
        directory = self.home / "cron"
        for doc in cases:
            with self.subTest(doc=doc), patch.object(holds, "read_owner_document", return_value=doc):
                self.assertFalse(holds.automatic_jobs(self.home, directory, directory / "jobs.json"))
                with self.assertRaises(ReadRefused):
                    holds.read_inbox(self.home, self.query, now=self.now)

    def test_inbox_identity_uses_the_exact_due_instant_and_owning_home(self):
        job = self.job(prompt="private task", workdir="private workspace")
        payload = self.inbox({"held-job": job, "unmapped-job": self.job(id="unmapped-job")})
        expected = payload["entries"][0]
        self.assertEqual(payload["profile"], "default")
        self.assertEqual(set(expected), {"job_id", "project_id", "scheduled_instant", "occurrence_id"})
        self.assertEqual(expected["scheduled_instant"], "2026-11-21T08:00:00+00:00")
        self.assertEqual(expected["project_id"], "fixture-project")
        self.assertEqual(self.inbox({"held-job": self.job(next_run_at="2026-11-21T08:00:00Z")}), payload)
        self.assertNotEqual(self.inbox({"held-job": self.job(next_run_at="2026-11-21T08:01:00Z")}), payload)
        with patch.object(holds, "read_owner_document", return_value=self.document()), \
                patch.object(holds, "_catalogue_records", return_value=({"held-job": job}, "stable-catalogue")):
            elsewhere = holds.read_inbox(self.home / "profiles" / "field", self.query, now=self.now)
        self.assertNotEqual(elsewhere, payload)

    def test_future_paused_malformed_or_unsettled_jobs_are_not_reviewable(self):
        changes = (
            {"enabled": False}, {"state": "completed"}, {"paused_at": self.due},
            {"next_run_at": "2026-11-21T10:00:00+00:00"}, {"next_run_at": "2026-11-21T08:00:00"},
            {"next_run_at": "bad"}, {"pending_slot": {}}, {"fire_claim": {}}, {"run_claim": {}},
            {"manual_run_at": self.due}, {"manual_run_prompt": "not the scheduled task"},
        )
        for fields in changes:
            with self.subTest(fields=fields):
                self.assertEqual(self.inbox({"held-job": self.job(**fields)})["entries"], [])

    def test_changed_policy_or_job_catalogue_discards_the_read(self):
        def refusals(documents, versions):
            records = {"held-job": self.job()}
            with patch.object(holds, "read_owner_document", side_effect=documents), \
                    patch.object(holds, "_catalogue_records", side_effect=[(records, v) for v in versions]):
                with self.assertRaises(ReadRefused) as error:
                    holds.read_inbox(self.home, self.query, now=self.now)
            self.assertEqual(error.exception.code, "CHANGED")
        doc = self.document()
        refusals([doc, doc], ["before", "after"])
        refusals([doc, PolicyDocument(doc.data, "b" * 64)], ["unchanged"] * 2)
