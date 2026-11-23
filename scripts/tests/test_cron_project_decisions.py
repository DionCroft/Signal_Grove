"""Strict project-occurrence decision contract and receipt addressing."""
import asyncio
from contextlib import ExitStack, nullcontext
from dataclasses import replace
from pathlib import Path
import sys
from types import ModuleType, SimpleNamespace
import unittest
from unittest.mock import Mock, patch

from server_extensions.coordinator_workspaces import cron_project_decisions as projects
from server_extensions.coordinator_workspaces.coordinator import NativeReadContext, ReadRefused
from server_extensions.coordinator_workspaces.cron_project_decisions import decision_query, occurrence_query
from server_extensions.coordinator_workspaces.cron_run_once_approval import RunOnceScope


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


class ProjectCheckoutReviewTest(unittest.TestCase):
    selector = {"expected_profile": "worker", "job_id": "fixture-job", "occurrence_id": "a" * 32}

    def setUp(self):
        self.home = Path.cwd() / "fixture-home"
        self.actor = NativeReadContext("owner", str(self.home), "connection", "gateway")
        self.selection = occurrence_query(self.selector)
        self.job = {"id": self.selector["job_id"]}
        self.scope = RunOnceScope("fixture", "workspace", "main", "worker", "worker-instance",
                                  "write", "a" * 64, "Write the fixture notes", commit="b" * 40)
        self.reservations = []
        self.dispatch = Mock(return_value="completed")
        self.service = projects.ProjectScheduleOperator(lambda *_: self.scope, self.dispatch)
        native = SimpleNamespace(CronStoreLockUnavailable=type("NativeLockUnavailable", (Exception,), {}),
                                 decide_reviewed_project_occurrence=self.reserve,
                                 settle_reviewed_project_occurrence=Mock())
        profiles = ModuleType("hermes_cli.profiles")
        profiles.profile_matches_home = lambda name, path: name == "worker" and path == self.home
        cron = ModuleType("cron")
        cron.executions = SimpleNamespace(mark_execution_running=Mock(return_value=True))
        stack = self.enterContext(ExitStack())
        for fixture in (
            patch.dict(sys.modules, {"hermes_cli.profiles": profiles, "cron": cron}),
            patch.object(projects, "_profile_home", side_effect=lambda *_: nullcontext()),
            patch.object(projects, "_native_jobs", return_value=native),
            patch.object(projects, "_selected", return_value=(
                self.job, "fixture", "2026-11-21T09:00:00Z", "policy", "catalogue")),
            patch.object(projects, "observe_occurrence", side_effect=self.observe),
        ):
            stack.enter_context(fixture)

    def reserve(self, job_id, *, occurrence_id, execution_id, instant, decision, authorize):
        self.assertEqual(self.job["id"], job_id)
        self.assertEqual(self.selection.occurrence_id, occurrence_id)
        self.assertEqual("approve", decision)
        self.assertTrue(authorize(self.job))
        self.reservations.append(execution_id)
        return dict(self.job, fire_claim={"by": "native-owner"})

    def observe(self, home, selection):
        self.assertEqual((self.home, self.selection), (home, selection))
        if selection.execution_id() not in self.reservations:
            raise ReadRefused("NOT_FOUND")
        return {}

    def verify(self):
        return self.actor

    def decide(self, review):
        request = decision_query(self.selector | {"review_digest": review["review_digest"], "decision": "approve"})
        return asyncio.run(self.service.decide(request, self.verify))

    def test_commit_drift_needs_new_review_and_original_occurrence_only_dispatches_once(self):
        original = self.service.prepare(self.selection, self.verify)
        self.assertEqual("b" * 40, original["scope"]["commit"])
        self.scope = replace(self.scope, commit="c" * 64)
        with self.assertRaises(ReadRefused) as drift:
            self.decide(original)
        self.assertEqual("CHANGED", drift.exception.code)
        self.assertFalse(drift.exception.request_submitted)
        self.assertEqual([], self.reservations)
        self.dispatch.assert_not_called()

        latest = self.service.prepare(self.selection, self.verify)
        self.assertEqual("c" * 64, latest["scope"]["commit"])
        acknowledged = self.decide(latest)
        self.assertEqual("claimed", acknowledged["state"])
        self.assertEqual(self.selection.execution_id(), acknowledged["execution_id"])
        self.assertEqual(self.scope, self.dispatch.call_args.args[0].scope)
        self.assertEqual(self.actor, self.dispatch.call_args.args[0].actor)
        with self.assertRaises(ReadRefused) as duplicate:
            self.decide(latest)
        self.assertEqual("DUPLICATE", duplicate.exception.code)
        self.assertEqual([self.selection.execution_id()], self.reservations)
        self.dispatch.assert_called_once()
