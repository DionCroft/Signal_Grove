"""Strict project-occurrence decision contract and receipt addressing."""
import unittest

from server_extensions.coordinator_workspaces.coordinator import ReadRefused
from server_extensions.coordinator_workspaces.cron_project_decisions import decision_query, occurrence_query


class ProjectDecisionContractTest(unittest.TestCase):
    selector = {"expected_profile": "worker", "job_id": "job-01", "occurrence_id": "a" * 32}

    def test_same_original_occurrence_uses_one_execution_id(self):
        selected = occurrence_query(self.selector)
        for choice in ("approve", "defer", "deny"):
            for digest in ("b" * 64, "c" * 64):
                request = decision_query(self.selector | {"review_digest": digest, "decision": choice})
                self.assertEqual(request.occurrence.execution_id(), selected.execution_id())
        for field, other in (("expected_profile", "other"), ("job_id", "job-02"), ("occurrence_id", "b" * 32)):
            self.assertNotEqual(occurrence_query(self.selector | {field: other}).execution_id(), selected.execution_id())

    def test_rejects_transport_routing_or_unreviewed_decision(self):
        for changes in ({"profile": "worker"}, {"workspace": "/wrong"}, {"expected_profile": "all"},
                        {"occurrence_id": "-" * 32}, {"job_id": "../other"}):
            with self.assertRaises(ReadRefused):
                occurrence_query(self.selector | changes)
        good = self.selector | {"review_digest": "b" * 64, "decision": "approve"}
        for changes in ({"decision": "force"}, {"decision": []}, {"task": "new task"},
                        {"review_digest": "B" * 64}, {"worker_instance": "different"}):
            with self.assertRaises(ReadRefused):
                decision_query(good | changes)
