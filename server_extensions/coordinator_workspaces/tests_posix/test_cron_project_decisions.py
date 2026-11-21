"""Real Linux original occurrence receipt reads with protected native SQLite."""
from pathlib import Path
import os
import sqlite3
import tempfile
import unittest

from server_extensions.coordinator_workspaces.coordinator import ReadRefused
from server_extensions.coordinator_workspaces.cron_history import execution_query, read_project_receipt
from server_extensions.coordinator_workspaces.cron_project_decisions import PROJECT_SOURCE


class ProjectDecisionReceiptPosixTest(unittest.TestCase):
    def setUp(self):
        scratch = tempfile.TemporaryDirectory(dir=os.environ["SIGNAL_GROVE_POLICY_TEST_ROOT"])
        self.addCleanup(scratch.cleanup)
        self.home = Path(scratch.name)
        self.cron = self.home / "cron"
        self.cron.mkdir(mode=0o700)
        self.database = self.cron / "executions.db"
        self.occurrence_id = "a" * 32
        self.execution_id = "b" * 32
        self.instant = "2026-11-21T09:00:00+00:00"
        self.query = execution_query({"expected_profile": "default", "job_id": "fixture-job",
                                      "execution_id": self.execution_id})

    def fixture(self, *, denied=False):
        fd = os.open(self.database, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        os.close(fd)
        db = sqlite3.connect(self.database)
        self.addCleanup(db.close)
        db.execute("PRAGMA journal_mode=WAL")
        db.executescript(
            "CREATE TABLE signal_grove_project_decisions "
            "(execution_id, occurrence_id, job_id, scheduled_instant, decision, outcome);"
            "CREATE TABLE executions (id, job_id, source, status, claimed_at, started_at, finished_at,"
            "scheduled_instant, delivery_outcome, error);"
        )
        status = "cancelled" if denied else "completed"
        decision = "deny" if denied else "approve"
        db.execute("INSERT INTO signal_grove_project_decisions VALUES (?, ?, ?, ?, ?, ?)",
                   (self.execution_id, self.occurrence_id, "fixture-job", self.instant, decision, status))
        db.execute("INSERT INTO executions VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                   (self.execution_id, "fixture-job", PROJECT_SOURCE, status, self.instant, None, self.instant,
                    self.instant, None, "private error" if denied else None))
        db.commit()
        return db

    def read(self):
        return read_project_receipt(self.home, self.query, self.occurrence_id, PROJECT_SOURCE)

    def test_reserved_decision_remains_addressable_after_execution_pruning(self):
        db = self.fixture()
        before = self.read()
        self.assertEqual(before["decision"], "approve")
        self.assertEqual(before["execution"]["status"], "completed")
        self.assertEqual(before["outcome"], "completed")
        db.execute("DELETE FROM executions")
        db.commit()
        after = self.read()
        self.assertEqual(after, before | {"execution": None})

    def test_rejects_foreign_scope_absent_or_unsafe_native_ledger(self):
        with self.assertRaises(ReadRefused) as missing:
            self.read()
        self.assertEqual(missing.exception.code, "NOT_FOUND")
        self.assertFalse(self.database.exists())
        self.fixture(denied=True)
        self.assertNotIn("private error", str(self.read()))
        self.assertEqual(self.read()["execution"]["status"], "cancelled")
        with self.assertRaises(ReadRefused) as foreign:
            read_project_receipt(self.home, self.query, "c" * 32, PROJECT_SOURCE)
        self.assertEqual(foreign.exception.code, "NOT_FOUND")
        self.database.chmod(0o666)
        with self.assertRaises(ReadRefused):
            self.read()
