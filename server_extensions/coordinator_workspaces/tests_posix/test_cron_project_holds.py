"""Real Linux tests of the protected policy and held occurrence snapshot."""
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import tempfile
import unittest

from server_extensions.coordinator_workspaces import cron_project_holds as holds
from server_extensions.coordinator_workspaces.coordinator import ReadRefused


class ProjectScheduleHoldsPosixTest(unittest.TestCase):
    def setUp(self):
        scratch = tempfile.TemporaryDirectory(dir=os.environ["SIGNAL_GROVE_POLICY_TEST_ROOT"])
        self.addCleanup(scratch.cleanup)
        self.home = Path(scratch.name)
        self.cron = self.home / "cron"
        self.cron.mkdir(mode=0o700)
        self.jobs = self.cron / "jobs.json"
        self.policy = self.cron / holds.POLICY_NAME
        self.write(self.policy, {"schema": 1, "automatic": [{"job_id": "read-job", "spec_digest": "a" * 64}],
                                 "project_writes": [{"job_id": "write-job", "project_id": "fixture-project"}]})
        self.write(self.jobs, {"jobs": [{"id": "write-job", "state": "scheduled", "enabled": True,
                                       "next_run_at": "2026-11-21T09:00:00+00:00", "prompt": "private text"}]})

    @staticmethod
    def write(path, data):
        descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(descriptor, "w", encoding="utf-8") as out:
            json.dump(data, out)

    def test_exact_held_occurrence_requires_an_anchored_owner_policy_and_catalogue(self):
        before = self.jobs.read_bytes(), self.policy.read_bytes()
        self.assertEqual(holds.automatic_jobs(self.home, self.cron, self.jobs), {"read-job": "a" * 64})
        actual = holds.read_inbox(self.home, holds.ScheduleInboxQuery("default"),
                                  now=datetime(2026, 11, 21, 10, tzinfo=timezone.utc))
        self.assertEqual([item["job_id"] for item in actual["entries"]], ["write-job"])
        self.assertEqual(actual["entries"][0]["project_id"], "fixture-project")
        self.assertNotIn("private text", json.dumps(actual))
        self.assertEqual((self.jobs.read_bytes(), self.policy.read_bytes()), before)
        self.assertFalse((self.cron / "executions.db").exists())

    def test_missing_broad_mode_and_symlink_policy_are_held(self):
        self.policy.chmod(0o644)
        self.assertFalse(holds.automatic_jobs(self.home, self.cron, self.jobs))
        with self.assertRaises(ReadRefused):
            holds.read_inbox(self.home, holds.ScheduleInboxQuery("default"))
        saved = self.cron / "saved-policy"
        self.policy.chmod(0o600)
        self.policy.rename(saved)
        self.assertFalse(holds.automatic_jobs(self.home, self.cron, self.jobs))
        self.policy.symlink_to(saved)
        self.assertFalse(holds.automatic_jobs(self.home, self.cron, self.jobs))
        with self.assertRaises(ReadRefused):
            holds.read_inbox(self.home, holds.ScheduleInboxQuery("default"))
