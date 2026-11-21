"""Exact and one-use Run once approval across dispatch and receipt failure boundaries."""

import asyncio
from concurrent.futures import ThreadPoolExecutor
from contextlib import ExitStack, nullcontext
from copy import deepcopy
from dataclasses import replace
from pathlib import Path
import sys
from threading import Event, Lock
from types import ModuleType
import unittest
from unittest.mock import patch

from server_extensions.coordinator_workspaces import cron_run_once_approval as approval
from server_extensions.coordinator_workspaces import cron_run_once_operator as operator_module
from server_extensions.coordinator_workspaces.cron_run_once_operator import (
    RunOnceOperator, confirm_query, prepare_query,
)
from server_extensions.coordinator_workspaces.coordinator import NativeReadContext, ReadRefused


class NativeFixture:
    class CronStoreLockUnavailable(RuntimeError):
        pass

    def __init__(self, read):
        self.read = read
        self.lock = Lock()
        self.locked = False
        self.receipts = []
        self.fail_after_reservation = False
        self.after_reservation = lambda: None

    def claim_reviewed_run_once(self, job_id, *, execution_id, authorize):
        with self.lock:
            self.locked = True
            try:
                original = self.read()
                if job_id != original["id"] or authorize(original) is not True:
                    return False
                self.receipts.append(execution_id)
                self.after_reservation()
                if self.fail_after_reservation:
                    raise OSError("Synthetic job-store failure")
                return dict(original, execution_id=execution_id, _scheduled_instant=None)
            finally:
                self.locked = False


class RunOnceApprovalTest(unittest.TestCase):
    def setUp(self):
        self.home = Path.cwd() / "fixture-home"
        self.actor = NativeReadContext("owner", str(self.home), "connection", "gateway")
        self.job = {"id": "exact-job", "prompt": "Read the approved status file"}
        self.version = "a" * 64
        self.native = NativeFixture(lambda: deepcopy(self.job))
        self.scope = approval.RunOnceScope("site", "verified-workspace", "main", "worker",
                                           "worker-instance", "read", "b" * 64, self.job["prompt"])
        self.scope_checks = []
        self.clock = 100.0
        self.service = approval.RunOnceApprovals(self.resolve)
        profiles = ModuleType("hermes_cli.profiles")
        profiles.profile_matches_home = lambda name, path: name == "profile-a" and path == self.home
        stack = self.enterContext(ExitStack())
        for fixture in (
            patch.dict(sys.modules, {"hermes_cli.profiles": profiles}),
            patch.object(approval, "_native_jobs", return_value=self.native),
            patch.object(approval, "_profile_home", side_effect=lambda *_: nullcontext()),
            patch.object(approval, "_catalogue", side_effect=self.catalogue),
            patch.object(approval, "monotonic", side_effect=lambda: self.clock),
            patch.object(operator_module, "monotonic", side_effect=lambda: self.clock),
        ):
            stack.enter_context(fixture)

    def catalogue(self, home, job_id):
        self.assertEqual((self.home, self.job["id"]), (home, job_id))
        return deepcopy(self.job), self.version

    def resolve(self, actor, job):
        self.assertEqual((self.actor, self.job), (actor, job))
        self.scope_checks.append(self.native.locked)
        return self.scope

    def verify(self):
        return self.actor

    def prepare(self):
        return self.service.prepare("profile-a", "exact-job", self.verify)

    def admit(self, review):
        return self.service.admit(review.receipt, review.digest, self.verify)

    def refuse(self, code, operation, submitted=False):
        with self.assertRaises(ReadRefused) as failure:
            operation()
        self.assertEqual((code, submitted), (failure.exception.code, failure.exception.request_submitted))

    def test_exact_approval_runs_scope_check_inside_claim_and_reuses_the_receipt(self):
        review = self.prepare()
        self.assertEqual(self.scope, review.scope)
        self.assertEqual([], self.native.receipts)
        self.assertEqual([False], self.scope_checks)
        claim = self.admit(review)
        expected_id = review.receipt.execution_id()
        self.assertEqual((review.receipt, self.scope, expected_id),
                         (claim.receipt, claim.scope, claim.snapshot["execution_id"]))
        self.assertEqual([False, True], self.scope_checks)
        self.refuse("DUPLICATE", lambda: self.admit(review), submitted=True)
        self.assertEqual([expected_id], self.native.receipts)

    def test_changed_catalogue_job_scope_or_review_digest_requires_new_review(self):
        original = self.scope
        for field, modified in (("project", "other"), ("workspace", "other"), ("branch", "other"),
                                ("worker", "other"), ("worker_instance", "other"), ("access", "write"),
                                ("policy_revision", "c" * 64), ("task", "Different task")):
            with self.subTest(field=field):
                self.scope = original
                review = self.prepare()
                self.scope = replace(original, **{field: modified})
                self.refuse("CHANGED", lambda: self.admit(review))
        self.scope = original
        review = self.prepare()
        self.refuse("CHANGED", lambda: self.service.admit(review.receipt, "c" * 64, self.verify))
        self.version = "e" * 64
        self.refuse("CHANGED", lambda: self.admit(review))
        self.assertEqual([], self.native.receipts)

    def test_foreign_transport_owner_and_authority_refuse_before_admission(self):
        review = self.prepare()
        for change in ({"subject": "other"}, {"transport_id": "other"},
                       {"authority_instance": "other"}, {"profile_id": str(self.home / "other")}):
            context = replace(self.actor, **change)
            self.refuse("DENIED", lambda: self.service.admit(review.receipt, review.digest, lambda: context))
        foreign_receipt = replace(review.receipt, profile="profile-b")
        self.refuse("DENIED", lambda: self.service.admit(foreign_receipt, review.digest, self.verify))
        self.assertEqual([False], self.scope_checks)
        self.assertEqual([], self.native.receipts)

    def test_deferred_denied_and_expired_reviews_cannot_dispatch(self):
        for decision in ("defer", "deny"):
            review = self.prepare()
            self.service.dismiss(review.receipt, review.digest, self.verify, decision)
            self.refuse("DUPLICATE", lambda: self.admit(review))
        review = self.prepare()
        self.clock += approval.REVIEW_SECONDS
        self.refuse("EXPIRED", lambda: self.admit(review))
        self.assertEqual([], self.native.receipts)

    def test_post_reservation_failure_remains_submitted_and_cannot_be_replayed(self):
        review = self.prepare()
        self.native.fail_after_reservation = True
        self.refuse("UNAVAILABLE", lambda: self.admit(review), submitted=True)
        self.refuse("DUPLICATE", lambda: self.admit(review), submitted=True)
        self.assertEqual([review.receipt.execution_id()], self.native.receipts)

    def test_policy_resolution_unavailable_stays_redacted(self):
        with patch.object(self.service, "_resolve_scope", side_effect=OSError("Private worker detail")):
            with self.assertRaises(ReadRefused) as error:
                self.prepare()
            self.assertEqual("UNAVAILABLE", str(error.exception))
        self.scope = None
        self.refuse("DENIED", self.prepare)
        self.assertEqual([], self.native.receipts)

    def test_context_lost_after_reservation_is_uncertain_and_keeps_its_original_id(self):
        review = self.prepare()

        def verify():
            if self.native.receipts:
                return replace(self.actor, transport_id="disconnected")
            return self.actor

        self.refuse("DENIED", lambda: self.service.admit(review.receipt, review.digest, verify),
                    submitted=True)
        self.assertEqual([review.receipt.execution_id()], self.native.receipts)
        self.refuse("DUPLICATE", lambda: self.admit(review), submitted=True)

    def test_competing_confirmations_reserve_only_once(self):
        review = self.prepare()

        def attempt():
            try:
                return self.admit(review)
            except ReadRefused as error:
                return error

        with ThreadPoolExecutor(max_workers=2) as scheduler:
            results = tuple(scheduler.map(lambda _: attempt(), range(2)))
        self.assertEqual(1, sum(isinstance(outcome, approval.ApprovedRunOnce) for outcome in results))
        denied = next(outcome for outcome in results if isinstance(outcome, ReadRefused))
        self.assertEqual(("DUPLICATE", True), (denied.code, denied.request_submitted))
        self.assertEqual([review.receipt.execution_id()], self.native.receipts)

    def test_closed_gateway_selectors_and_retired_review(self):
        selected = {"expected_profile": "profile-a", "job_id": "exact-job"}
        operator = RunOnceOperator(self.resolve, lambda run: self.fail("Dismissed review dispatched"))
        prepared = operator.prepare(prepare_query(selected), self.verify)
        self.assertEqual("verified-workspace", prepared["scope"]["workspace"])
        self.assertEqual("Read the approved status file", prepared["scope"]["task"])
        self.assertGreater(prepared["expires_in_seconds"], 0)
        self.assertNotIn("actor", prepared)
        inputs = selected | {key: prepared[key] for key in ("request_id", "review_digest")}
        self.refuse("INVALID", lambda: prepare_query(selected | {"profile": "profile-b"}))
        for field in ("profile", "session_id", "workspace", "dispatch"):
            self.refuse("INVALID", lambda: confirm_query(inputs | {field: "other"}))
        self.refuse("INVALID", lambda: confirm_query(inputs | {"review_digest": "wrong"}))
        request = confirm_query(inputs | {"decision": "defer"}, dismiss=True)
        self.assertEqual("defer", operator.dismiss(request, self.verify)["decision"])
        self.refuse("DUPLICATE", lambda: asyncio.run(operator.confirm(confirm_query(inputs), self.verify)))
        self.assertEqual([], self.native.receipts)

    def test_confirm_returns_claimed_while_native_dispatch_is_still_running(self):
        active, finish = Event(), Event()
        routed = []

        def dispatch(run):
            routed.append(run)
            active.set()
            if not finish.wait(5):
                raise RuntimeError("Fixture timed out")

        async def exercise():
            operator = RunOnceOperator(self.resolve, dispatch)
            selected = {"expected_profile": "profile-a", "job_id": "exact-job"}
            prepared = operator.prepare(prepare_query(selected), self.verify)
            request = confirm_query(selected | {key: prepared[key] for key in ("request_id", "review_digest")})
            try:
                accepted = await asyncio.wait_for(operator.confirm(request, self.verify), timeout=5)
                self.assertTrue(await asyncio.to_thread(active.wait, 5))
                self.assertEqual({"profile": "profile-a", "job_id": "exact-job",
                                  "request_id": request.receipt.request_id,
                                  "execution_id": request.receipt.execution_id(), "state": "claimed"}, accepted)
                self.assertEqual(1, len(operator._tasks))
                self.assertEqual((self.scope, self.actor), (routed[0].scope, routed[0].actor))
                with self.assertRaises(ReadRefused) as duplicate:
                    await operator.confirm(request, self.verify)
                self.assertEqual(("DUPLICATE", True),
                                 (duplicate.exception.code, duplicate.exception.request_submitted))
            finally:
                finish.set()
                await asyncio.gather(*operator._tasks)

        asyncio.run(exercise())
        self.assertEqual(1, len(routed))
        self.assertEqual([routed[0].receipt.execution_id()], self.native.receipts)

    def test_disconnect_after_reservation_keeps_exact_dispatched_work(self):
        stored, release = Event(), Event()
        routed = []

        def block_after_store():
            stored.set()
            if not release.wait(5):
                raise RuntimeError("Fixture timed out")

        self.native.after_reservation = block_after_store

        async def exercise():
            operator = RunOnceOperator(self.resolve, routed.append)
            selected = {"expected_profile": "profile-a", "job_id": "exact-job"}
            prepared = operator.prepare(prepare_query(selected), self.verify)
            request = confirm_query(selected | {key: prepared[key] for key in ("request_id", "review_digest")})

            def verify():
                if release.is_set():
                    raise ReadRefused("DENIED")
                return self.actor

            caller = asyncio.create_task(operator.confirm(request, verify))
            try:
                self.assertTrue(await asyncio.to_thread(stored.wait, 5))
                caller.cancel()
                with self.assertRaises(asyncio.CancelledError):
                    await caller
                self.assertEqual(1, len(operator._tasks))
                self.assertEqual([], routed)
            finally:
                release.set()
                await asyncio.gather(*operator._tasks)
            self.assertEqual([request.receipt], [run.receipt for run in routed])
            self.assertEqual([request.receipt.execution_id()], self.native.receipts)

        asyncio.run(exercise())
