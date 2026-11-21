"""Closed selectors and redacted failure behavior for the scoped scheduler reader."""

import ast
import hashlib
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

from server_extensions.coordinator_workspaces.cron_edit import edit_query
from server_extensions.coordinator_workspaces.cron_history import execution_query, read_executions
from server_extensions.coordinator_workspaces.coordinator import ReadRefused
from server_extensions.coordinator_workspaces.cron_output import bind_output, output_query, read_output
from server_extensions.coordinator_workspaces.install_adapter import patch_scheduler_output, prepare


class CronHistoryQueryTest(unittest.TestCase):
    def test_closed_selector_accepts_list_or_one_exact_receipt(self):
        request = {'expected_profile': 'field_1', 'job_id': '65ddee99bbfa'}
        page = execution_query(request)
        self.assertEqual(('field_1', '65ddee99bbfa', 20, None),
                         (page.profile, page.job_id, page.limit, page.execution_id))
        self.assertEqual(5, execution_query(dict(request, limit=5)).limit)
        exact = execution_query(dict(request, execution_id='a' * 32))
        self.assertEqual(('a' * 32, 1), (exact.execution_id, exact.limit))

    def test_caller_paths_scope_claims_missing_profiles_and_unbounded_limits_are_rejected(self):
        base = {'expected_profile': 'field', 'job_id': 'a1b2c3d4e5f6'}
        invalid = [
            {'job_id': 'a1b2c3d4e5f6'}, dict(base, expected_profile='all'), dict(base, expected_profile='../field'),
            dict(base, job_id='unknown'), dict(base, job_id='bad/other'), dict(base, home='/other'),
            dict(base, transport_id='other'), dict(base, profile='other'), dict(base, limit=True), dict(base, limit=21),
            dict(base, execution_id='a' * 32, limit=1), dict(base, execution_id='A' * 32),
            dict(base, execution_id=None), dict(base, limit=None),
        ]
        for value in invalid:
            with self.subTest(value=value), self.assertRaises(ReadRefused) as raised:
                execution_query(value)
            self.assertEqual('INVALID', raised.exception.code)

    def test_unsupported_host_refuses_before_inspecting_profile_paths(self):
        query = execution_query({'expected_profile': 'default', 'job_id': 'a1b2c3d4e5f6'})
        with patch.object(sys, 'platform', 'win32'), self.assertRaises(ReadRefused) as raised:
            read_executions(None, query)
        self.assertEqual('UNSUPPORTED', raised.exception.code)


    def test_output_rpc_only_accepts_exact_selector(self):
        params = dict(expected_profile="field", job_id="calendar-job", execution_id="a" * 32)
        self.assertEqual("a" * 32, output_query(params).execution_id)
        for invalid in (dict(params, profile="default"), dict(params, path="2026.md"),
                        dict(params, limit=1), dict(params, execution_id=None),
                        {key: value for key, value in params.items() if key != "execution_id"}):
            with self.assertRaises(ReadRefused) as raised:
                output_query(invalid)
            self.assertEqual("INVALID", raised.exception.code)

    def test_output_never_reads_or_writes_paths_on_an_unsupported_host(self):
        query = output_query(dict(expected_profile="field", job_id="calendar-job", execution_id="a" * 32))
        with patch.object(sys, "platform", "win32"):
            with self.assertRaises(ReadRefused) as raised:
                read_output(None, query)
            self.assertEqual("UNSUPPORTED", raised.exception.code)
            self.assertFalse(bind_output("calendar-job", "a" * 32, None, "fixture"))

    def test_scheduler_patch_runs_inside_output_fence_and_preserves_the_unmodified_writer(self):
        source = """def finish(job, fence, verbose, output):
    with fence.side_effect_fence() as owns_output:
        if not owns_output:
            raise RuntimeError()
        output_file = (
            None if self_removal_delivery_allowed(job["id"])
            else save_job_output(job["id"], output))
    if verbose and output_file is not None:
        return output_file
"""
        patched = patch_scheduler_output(source)
        node = ast.parse(patched).body[0]
        self.assertIsInstance(node.body[0], ast.With)
        self.assertEqual(ast.unparse(node.body[0].body[1]),
                         ast.unparse(ast.parse(source).body[0].body[0].body[1]))
        self.assertIn("output_file is not None and job.get", ast.unparse(node.body[0].body[2]))
        self.assertIn("bind_output(job['id'], job['execution_id'], output_file, output)",
                      ast.unparse(node.body[0].body[2]))
        with self.assertRaises(ValueError):
            patch_scheduler_output(patched)
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "scheduler.py"
            data = source.replace("\n", "\r\n").encode("utf-8")
            path.write_bytes(data)
            prepared = prepare(path, hashlib.sha256(data).hexdigest(), patch_scheduler_output)
            self.assertEqual(patched.replace("\n", "\r\n").encode("utf-8"), prepared)
            self.assertEqual(data, path.read_bytes())
            with self.assertRaises(ValueError):
                prepare(path, "0" * 64, patch_scheduler_output)


    def test_schedule_edit_accepts_only_exact_scope_review_and_timing_changes(self):
        target = dict(expected_profile="worker", job_id="calendar-job")
        apply = dict(target, request_id="1"*32, expected_version="a"*64, review_digest="b"*64,
                     changes={"timezone":"Europe/London", "schedule":"0 9 * * *", "grace_minutes":0})
        valid = edit_query(apply, "apply")
        self.assertEqual(("worker", "calendar-job", "1"*32, "a"*64),
                         (valid.profile, valid.job_id, valid.request_id, valid.expected_version))
        self.assertEqual(["grace_minutes", "schedule", "timezone"], list(valid.changes))
        self.assertEqual("b"*64, valid.review_digest)
        review = edit_query({key: value for key, value in apply.items() if key not in {"request_id", "review_digest"}}, "review")
        self.assertEqual(valid.changes, review.changes)
        self.assertIsNone(review.review_digest)
        for missing in ("review_digest", "expected_version"):
            with self.assertRaises(ReadRefused) as error:
                edit_query({key: value for key, value in apply.items() if key != missing}, "apply")
            self.assertEqual("INVALID", error.exception.code)
        self.assertEqual(valid.request_id, edit_query(dict(target, request_id=valid.request_id), "observe").request_id)
        self.assertIsNone(edit_query(target, "prepare").request_id)
        for invalid in (dict(apply, profile="default"), dict(apply, expected_version="wrong"),
                        dict(apply, request_id="path/claim"), dict(apply, changes={}),
                        dict(apply, changes={"enabled":True}), dict(apply, changes={"prompt":"private"}),
                        dict(apply, changes={"grace_minutes":True}), dict(apply, changes={"catch_up":"yes"}),
                        dict(apply, changes={"timezone":"../../other"}), dict(apply, changes={"timezone":"../UTC"}),
                        dict(apply, changes={"schedule":"0 9 * * *\n"})):
            with self.assertRaises(ReadRefused) as error:
                edit_query(invalid, "apply")
            self.assertEqual("INVALID", error.exception.code)
