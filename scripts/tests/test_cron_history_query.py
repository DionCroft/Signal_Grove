"""Closed selectors and redacted failure behavior for the scoped scheduler reader."""

import sys
import unittest

from server_extensions.coordinator_workspaces.cron_history import execution_query, read_executions
from server_extensions.coordinator_workspaces.coordinator import ReadRefused


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

    @unittest.skipIf(sys.platform == 'linux', 'Non-Linux refusal applies to the other platforms')
    def test_unsupported_host_refuses_before_inspecting_profile_paths(self):
        query = execution_query({'expected_profile': 'default', 'job_id': 'a1b2c3d4e5f6'})
        with self.assertRaises(ReadRefused) as raised:
            read_executions(None, query)
        self.assertEqual('UNSUPPORTED', raised.exception.code)
