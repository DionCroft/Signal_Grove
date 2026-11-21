"""Protected scheduler storage acceptance with a native-writer interface fixture."""

from contextlib import contextmanager
import fcntl
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from server_extensions.coordinator_workspaces import cron_edit
from server_extensions.coordinator_workspaces.coordinator import ReadRefused


JOB = "owned-timing-job"
OTHER = "peer-timing-job"


class NativeWriterFixture:
    class CronStoreLockUnavailable(RuntimeError):
        pass

    def __init__(self):
        self.saves = 0
        self.lose_reply = False
        self.store = None

    @contextmanager
    def use_cron_store(self, home):
        self.store = Path(home) / "cron"
        try:
            yield
        finally:
            self.store = None

    @contextmanager
    def _jobs_lock(self, *, require_cross_process=False):
        assert require_cross_process
        descriptor = os.open(self.store / ".jobs.lock", os.O_RDWR | os.O_CREAT, 0o600)
        try:
            try:
                fcntl.flock(descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError:
                raise self.CronStoreLockUnavailable() from None
            yield
        finally:
            os.close(descriptor)

    @staticmethod
    def parse_schedule(value):
        if value.count(" ") == 4:
            return {"kind":"cron", "expr":value, "display":value}
        raise ValueError("Unreviewed schedule fixture")

    def update_job(self, job_id, updates, *, finalize_review=None):
        document = self.store / "jobs.json"
        rows = json.loads(document.read_bytes())["jobs"]
        for index, job in enumerate(rows):
            if job["id"] != job_id:
                continue
            updated = dict(job, **updates)
            finalize_review(updated)
            rows[index] = updated
            replacement = document.with_suffix(".pending")
            replacement.write_text(json.dumps({"jobs":rows}), encoding="utf-8")
            replacement.chmod(0o600)
            replacement.replace(document)
            self.saves += 1
            if self.lose_reply:
                raise OSError("Readback interrupted")
            return updated
        return None


class CronEditPosixTest(unittest.TestCase):
    def setUp(self):
        parent = os.environ["SIGNAL_GROVE_POLICY_TEST_ROOT"]
        tmp = tempfile.TemporaryDirectory(prefix="timing-edit-", dir=parent)
        self.addCleanup(tmp.cleanup)
        self.home = Path(tmp.name) / "default"
        self.other = Path(tmp.name) / "secondary"
        self.initial = {
            "id":JOB, "enabled":False, "state":"paused", "name":"Fixture",
            "schedule":{"kind":"cron", "expr":"0 9 * * *"}, "timezone":"UTC",
            "prompt":"private fixture prompt", "next_run_at":"2035-01-01T09:00:00+00:00",
        }
        for home in (self.home,self.other):
            (home / "cron").mkdir(parents=True, mode=0o700)
            self.write(home, [dict(self.initial)])
        self.writer = NativeWriterFixture()
        boot = patch.object(cron_edit, "_native", return_value=self.writer)
        boot.start()
        self.addCleanup(boot.stop)

    @staticmethod
    def path(home):
        return home / "cron/jobs.json"

    def write(self, home, jobs):
        path = self.path(home)
        path.write_text(json.dumps({"jobs":jobs}), encoding="utf-8")
        path.chmod(0o600)

    def prepare(self, home=None):
        return cron_edit.prepare_edit(self.home if home is None else home, self.query())

    def query(self, action="prepare", **fields):
        return cron_edit.edit_query(dict(expected_profile="default", job_id=JOB, **fields), action)

    def request(self, request_number=1, version=None, **changes):
        return self.query("apply", request_id=f"{request_number:032x}",
                          expected_version=version or self.prepare()["version"], changes=changes)

    def submit(self, query, guard=lambda: None):
        return cron_edit.apply_edit(self.home, query, guard)

    def observe(self, query, home=None):
        return cron_edit.observe_edit(self.home if home is None else home, self.query(
            "observe", request_id=query.request_id))

    def refuse(self, code, call, submitted=False):
        with self.assertRaises(ReadRefused) as raised:
            call()
        self.assertEqual((code, submitted), (raised.exception.code, raised.exception.request_submitted))

    def test_exact_edit_changes_timing_and_one_atomic_receipt_only(self):
        before = self.path(self.other).read_bytes()
        review = self.prepare()
        self.assertNotIn("private fixture prompt", str(review))
        edit = self.request(schedule="0 11 * * *", timezone="Europe/London", grace_minutes=0)
        result = self.submit(edit)
        self.assertEqual(result, self.observe(edit))
        self.assertEqual("0 11 * * *", result["timing"]["schedule"]["expr"])
        self.assertEqual(1, self.writer.saves)
        updated = json.loads(self.path(self.home).read_bytes())["jobs"][0]
        self.assertEqual(("paused", False, None), (updated["state"],updated["enabled"],updated["next_run_at"]))
        self.assertEqual(before, self.path(self.other).read_bytes())
        self.refuse("NOT_FOUND", lambda: self.observe(edit, self.other))

    def test_file_change_and_replacement_with_equal_bytes_invalidate_the_review(self):
        accepted = self.request(schedule="0 11 * * *")
        original = self.path(self.home)
        replacement = original.with_suffix(".same")
        replacement.write_bytes(original.read_bytes())
        replacement.chmod(0o600)
        replacement.replace(original)
        self.refuse("CHANGED", lambda: self.submit(accepted))
        self.assertEqual(0, self.writer.saves)

    def test_lock_contention_and_precommit_owner_change_leave_job_untouched(self):
        edit = self.request(catch_up=False)
        before = self.path(self.home).read_bytes()
        lock = self.home / "cron/.jobs.lock"
        with lock.open("a+", encoding="utf-8") as held:
            lock.chmod(0o600)
            fcntl.flock(held, fcntl.LOCK_EX)
            self.refuse("BUSY", lambda: self.submit(edit))
        calls = []
        def verify():
            calls.append(1)
            if len(calls) == 2:
                raise ReadRefused("DENIED")
        self.refuse("DENIED", lambda: self.submit(edit, verify))
        self.assertEqual(before, self.path(self.home).read_bytes())
        self.assertEqual(0, self.writer.saves)

    def test_lost_reply_keeps_the_exact_receipt_and_same_request_never_writes_twice(self):
        edit = self.request(schedule="0 11 * * *", grace_minutes=0)
        self.writer.lose_reply = True
        self.refuse("UNAVAILABLE", lambda: self.submit(edit), submitted=True)
        result = self.observe(edit)
        self.writer.lose_reply = False
        repeat = self.request(version=edit.expected_version, grace_minutes=0, schedule="0 11 * * *")
        self.assertEqual(result, self.submit(repeat))
        conflicting = self.request(version=edit.expected_version, catch_up=True)
        self.refuse("DUPLICATE", lambda: self.submit(conflicting))
        self.assertEqual(1, self.writer.saves)

    def test_active_or_claimed_jobs_foreign_symlink_and_duplicate_ids_are_refused(self):
        for condition in ({"enabled":True}, {"fire_claim":{"by":"fixture"}}):
            self.write(self.home, [dict(self.initial, **condition)])
            self.refuse("BUSY", self.prepare)
        self.write(self.home, [dict(self.initial), dict(self.initial)])
        self.refuse("DUPLICATE", self.prepare)
        target = self.path(self.home)
        target.unlink()
        target.symlink_to(self.path(self.other))
        self.refuse("UNAVAILABLE", self.prepare)
        self.assertEqual(0, self.writer.saves)
