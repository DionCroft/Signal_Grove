"""An exact, short-lived review of one protected scheduled project occurrence."""

from dataclasses import dataclass
from datetime import datetime, timezone
import hashlib
from pathlib import Path
import re
import sqlite3
from threading import Lock
from time import monotonic
from uuid import uuid4

from .coordinator import NativeReadContext, ReadRefused, encode
from .cron_edit import _catalogue, _profile_home
from .cron_history import execution_query, read_project_receipt
from .cron_project_holds import _due_instant, _occurrence_id, _read_policy
from .cron_run_once_approval import MAX_REVIEWS, REVIEW_SECONDS, RunOnceScope, _verified_context
from .cron_run_once_operator import run_reserved_decision

PROJECT_SOURCE = "signal-grove-project-occurrence-v1"
_SELECTOR = {"expected_profile", "job_id", "occurrence_id"}
_DECIDE = _SELECTOR | {"review_digest", "decision"}
_DIGEST = re.compile(r"[a-f0-9]{64}")


@dataclass(frozen=True)
class ProjectOccurrence:
    profile: str
    job_id: str
    occurrence_id: str

    def execution_id(self):
        identity = encode([self.profile, self.job_id, self.occurrence_id])
        return hashlib.sha256(b"signal-grove-project-execution-v1\0" + identity).hexdigest()[:32]

    def fields(self):
        return {"profile": self.profile, "job_id": self.job_id, "occurrence_id": self.occurrence_id}


@dataclass(frozen=True)
class ProjectDecision:
    occurrence: ProjectOccurrence
    digest: str
    decision: str


@dataclass(frozen=True, repr=False)
class ApprovedOccurrence:
    occurrence: ProjectOccurrence
    scope: RunOnceScope
    scheduled_instant: str
    snapshot: dict
    actor: NativeReadContext
    review_digest: str
    expires_at: float


@dataclass(frozen=True, repr=False)
class _Review:
    selection: ProjectOccurrence
    scope: RunOnceScope
    scheduled_instant: str
    actor: NativeReadContext
    policy_version: str
    catalogue_version: str
    expires_at: float


def occurrence_query(params):
    if type(params) is not dict or set(params) != _SELECTOR:
        raise ReadRefused("INVALID")
    query = execution_query({"expected_profile": params["expected_profile"],
                             "job_id": params["job_id"], "execution_id": params["occurrence_id"]})
    return ProjectOccurrence(query.profile, query.job_id, query.execution_id)


def decision_query(params):
    if type(params) is not dict or set(params) != _DECIDE:
        raise ReadRefused("INVALID")
    digest, decision = params["review_digest"], params["decision"]
    if type(digest) is not str or not _DIGEST.fullmatch(digest) or decision not in ("approve", "defer", "deny"):
        raise ReadRefused("INVALID")
    selection = occurrence_query({key: params[key] for key in _SELECTOR})
    return ProjectDecision(selection, digest, decision)


def observe_occurrence(home, selection):
    query = execution_query({"expected_profile": selection.profile, "job_id": selection.job_id,
                             "execution_id": selection.execution_id()})
    receipt = read_project_receipt(home, query, selection.occurrence_id, PROJECT_SOURCE)
    return {**selection.fields(), **receipt}


def _native_jobs():
    from cron import jobs
    if not callable(getattr(jobs, "decide_reviewed_project_occurrence", None)):
        raise ReadRefused("UNSUPPORTED")
    return jobs


def _selected(home, selection):
    policy = _read_policy(home)
    project = policy.project_writes.get(selection.job_id)
    if project is None:
        raise ReadRefused("DENIED")
    job, version = _catalogue(home, selection.job_id)
    instant = _due_instant(job, datetime.now(timezone.utc))
    if instant is None or _occurrence_id(selection.profile, home, selection.job_id, instant) != selection.occurrence_id:
        raise ReadRefused("CHANGED")
    return job, project, instant, policy.revision, version


def _unreserved(home, selection):
    try:
        observe_occurrence(home, selection)
    except ReadRefused as error:
        if error.code != "NOT_FOUND":
            raise
    else:
        raise ReadRefused("DUPLICATE", request_submitted=True)


class ProjectScheduleOperator:
    """Local owner policy supplies the current route and a scoped worker dispatcher."""

    def __init__(self, resolve_scope, dispatch):
        if not callable(resolve_scope) or not callable(dispatch):
            raise TypeError("Project schedules require a scope resolver and dispatcher")
        self._resolve_scope = resolve_scope
        self._dispatch = dispatch
        self._reviews = {}
        self._lock = Lock()
        self._tasks = set()

    def _scope(self, actor, project, job):
        try:
            scope = self._resolve_scope(actor, project, job)
        except ReadRefused:
            raise
        except Exception:
            raise ReadRefused("UNAVAILABLE") from None
        if type(scope) is not RunOnceScope or scope.project != project or scope.access != "write":
            raise ReadRefused("DENIED")
        scope.values()
        return scope

    def prepare(self, selection, verify):
        actor = _verified_context(verify, selection.profile)
        jobs = _native_jobs()
        home = Path(actor.profile_id)
        with _profile_home(jobs, home):
            _unreserved(home, selection)
            chosen = _selected(home, selection)
            job, project, instant, policy_version, catalogue_version = chosen
            scope = self._scope(actor, project, job)
            if _selected(home, selection) != chosen:
                raise ReadRefused("CHANGED")
        _verified_context(verify, selection.profile, actor)
        with self._lock:
            now = monotonic()
            self._reviews = {key: review for key, review in self._reviews.items() if review.expires_at > now}
            if len(self._reviews) >= MAX_REVIEWS:
                raise ReadRefused("BUSY")
            fields = [*selection.fields().values(), instant, policy_version, catalogue_version, *scope.values(), uuid4().hex]
            digest = hashlib.sha256(b"signal-grove-project-review-v1\0" + encode(fields, maximum=128 * 1024)).hexdigest()
            self._reviews[digest] = _Review(selection, scope, instant, actor, policy_version, catalogue_version,
                                             now + REVIEW_SECONDS)
        return {**selection.fields(), "scheduled_instant": instant, "review_digest": digest,
                "expires_in_seconds": REVIEW_SECONDS, "scope": scope.review_payload()}

    def _pending(self, request, actor):
        review = self._reviews.get(request.digest)
        if review is None or monotonic() >= review.expires_at:
            raise ReadRefused("EXPIRED")
        if review.actor != actor or review.selection != request.occurrence:
            raise ReadRefused("DENIED")
        return review

    async def decide(self, request, verify):
        if request.decision == "defer":
            actor = _verified_context(verify, request.occurrence.profile)
            with self._lock:
                self._pending(request, actor)
                self._reviews.pop(request.digest)
            return {**request.occurrence.fields(), "state": "deferred"}

        return await run_reserved_decision(self._tasks, lambda publish: self._submit(request, verify, publish))

    def _submit(self, request, verify, publish):
        selection = request.occurrence
        actor = _verified_context(verify, selection.profile)
        home = Path(actor.profile_id)
        jobs = _native_jobs()
        from cron import executions

        with _profile_home(jobs, home):
            _unreserved(home, selection)
        with self._lock:
            review = self._pending(request, actor)
        submitted = False

        def authorize(stored):
            nonlocal submitted
            _verified_context(verify, selection.profile, actor)
            chosen = _selected(home, selection)
            job, project, instant, policy_version, catalogue_version = chosen
            if (stored != job or (instant, policy_version, catalogue_version) !=
                    (review.scheduled_instant, review.policy_version, review.catalogue_version)):
                raise ReadRefused("CHANGED")
            if request.decision == "approve" and self._scope(actor, project, job) != review.scope:
                raise ReadRefused("CHANGED")
            if _selected(home, selection) != chosen:
                raise ReadRefused("CHANGED")
            _verified_context(verify, selection.profile, actor)
            with self._lock:
                self._pending(request, actor)
                self._reviews.pop(request.digest)
                submitted = True
            return True

        try:
            with _profile_home(jobs, home):
                _unreserved(home, selection)
                snapshot = jobs.decide_reviewed_project_occurrence(
                    selection.job_id, occurrence_id=selection.occurrence_id, execution_id=selection.execution_id(),
                    instant=review.scheduled_instant, decision=request.decision, authorize=authorize)
                if type(snapshot) is not dict or snapshot.get("id") != selection.job_id or not submitted:
                    _unreserved(home, selection)
                    raise ReadRefused("CHANGED")
                claimed = request.decision == "approve"
                publish({**selection.fields(), "scheduled_instant": review.scheduled_instant,
                         "execution_id": selection.execution_id(), "state": "claimed" if claimed else "denied"})
                if claimed and executions.mark_execution_running(selection.execution_id()) is not None:
                    run = ApprovedOccurrence(selection, review.scope, review.scheduled_instant, snapshot, actor,
                                             request.digest, review.expires_at)
                    try:
                        status = self._dispatch(run)
                    except Exception:
                        status = None
                    if type(status) is str and status in ("completed", "failed"):
                        jobs.settle_reviewed_project_occurrence(selection.job_id, snapshot["fire_claim"]["by"],
                                                               selection.execution_id(), status)
                    else:
                        executions.mark_project_execution_unknown(selection.execution_id())
        except jobs.CronStoreLockUnavailable:
            raise ReadRefused("BUSY", request_submitted=submitted) from None
        except sqlite3.IntegrityError:
            raise ReadRefused("DUPLICATE", request_submitted=True) from None
        except ReadRefused as error:
            raise ReadRefused(error.code, request_submitted=submitted or error.request_submitted) from None
        except (OSError, ValueError, RuntimeError, TypeError, sqlite3.Error):
            raise ReadRefused("UNAVAILABLE", request_submitted=submitted) from None
