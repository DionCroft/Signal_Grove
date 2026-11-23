"""A short-lived, exact native Run once review and one-use admission."""

from dataclasses import dataclass
import hashlib
from pathlib import Path
import re
import sqlite3
from threading import Lock
from time import monotonic
from uuid import uuid4

from .coordinator import NativeReadContext, ReadRefused, _identity, encode
from .cron_edit import _catalogue, _profile_home
from .cron_history import execution_query
from .cron_run_once import RunOnceReceipt

REVIEW_SECONDS = 180
MAX_REVIEWS = 64
_DIGEST = re.compile(r"[a-f0-9]{64}")
_COMMIT = re.compile(r"(?:[a-f0-9]{40}|[a-f0-9]{64})")


@dataclass(frozen=True, repr=False)
class RunOnceScope:
    """An operator-verified route and exact user-visible task scope."""

    project: str | None
    workspace: str
    branch: str | None
    worker: str
    worker_instance: str
    access: str
    policy_revision: str
    task: str
    commit: str | None = None

    def values(self):
        identity = (self.workspace, self.worker, self.worker_instance)
        optional = (self.project, self.branch)
        if (not all(_identity(value) for value in identity)
                or any(value is not None and not _identity(value) for value in optional)
                or type(self.access) is not str or self.access not in ("read", "write")
                or (self.commit is not None
                    and (type(self.commit) is not str or not _COMMIT.fullmatch(self.commit)))
                or (self.project is not None and self.access == "write"
                    and (self.branch is None or self.commit is None))
                or type(self.policy_revision) is not str or not _DIGEST.fullmatch(self.policy_revision)
                or type(self.task) is not str or not 1 <= len(self.task) <= 8192
                or any(ord(char) < 32 and char not in "\n\t" or ord(char) == 127
                       or 0xd800 <= ord(char) <= 0xdfff for char in self.task)):
            raise ReadRefused("DENIED")
        return [*identity, *optional, self.access, self.policy_revision, self.task, self.commit]

    def review_payload(self):
        payload = {"project": self.project, "workspace": self.workspace, "branch": self.branch,
                   "worker": self.worker, "worker_instance": self.worker_instance,
                   "access": self.access, "task": self.task}
        if self.commit is not None:
            payload["commit"] = self.commit
        return payload


@dataclass(frozen=True, repr=False)
class RunOnceReview:
    receipt: RunOnceReceipt
    digest: str
    scope: RunOnceScope
    expires_at: float
    actor: NativeReadContext
    catalogue_version: str


@dataclass(frozen=True, repr=False)
class ApprovedRunOnce:
    receipt: RunOnceReceipt
    scope: RunOnceScope
    snapshot: dict
    actor: NativeReadContext


@dataclass(repr=False)
class _Slot:
    review: RunOnceReview
    state: str = "pending"


def _verified_context(verify, profile, expected=None):
    from hermes_cli.profiles import profile_matches_home

    try:
        context = verify()
    except ReadRefused:
        raise
    except Exception:
        raise ReadRefused("DENIED") from None
    if (type(context) is not NativeReadContext
            or not all(_identity(value) for value in (context.subject, context.profile_id,
                                                     context.transport_id, context.authority_instance))
            or not Path(context.profile_id).is_absolute()
            or not profile_matches_home(profile, Path(context.profile_id))
            or (expected is not None and expected != context)):
        raise ReadRefused("DENIED")
    return context


def _native_jobs():
    from cron import jobs
    if not callable(getattr(jobs, "claim_reviewed_run_once", None)):
        raise ReadRefused("UNSUPPORTED")
    return jobs


class RunOnceApprovals:
    """One service owned by a native authority, with a verified route resolver.

    The resolver receives the current native job and context. It checks the
    exact worker, workspace and task grant each time. It must use bounded local
    snapshots since the final check runs inside the scheduler's locks.
    """

    def __init__(self, resolve_scope):
        self._resolve_scope = resolve_scope
        self._slots = {}
        self._lock = Lock()

    def _scope(self, actor, job):
        try:
            scope = self._resolve_scope(actor, job)
        except ReadRefused:
            raise
        except Exception:
            raise ReadRefused("UNAVAILABLE") from None
        if type(scope) is not RunOnceScope:
            raise ReadRefused("DENIED")
        scope.values()
        return scope

    def prepare(self, profile, job_id, verify):
        query = execution_query({"expected_profile": profile, "job_id": job_id})
        actor = _verified_context(verify, query.profile)
        jobs = _native_jobs()
        home = Path(actor.profile_id)
        with _profile_home(jobs, home):
            job, version = _catalogue(home, query.job_id)
            scope = self._scope(actor, job)
            if _catalogue(home, query.job_id)[1] != version:
                raise ReadRefused("CHANGED")
        _verified_context(verify, query.profile, actor)

        with self._lock:
            now = monotonic()
            self._slots = {key: slot for key, slot in self._slots.items() if slot.review.expires_at > now}
            if len(self._slots) >= MAX_REVIEWS:
                raise ReadRefused("BUSY")
            request_id = uuid4().hex
            if request_id in self._slots:
                raise ReadRefused("BUSY")
            receipt = RunOnceReceipt(query.profile, query.job_id, request_id)
            digest = hashlib.sha256(b"signal-grove-cron-once-review-v1\0" +
                                     encode([receipt.profile, receipt.job_id, request_id, version,
                                             *scope.values()], maximum=64 * 1024)).hexdigest()
            review = RunOnceReview(receipt, digest, scope, now + REVIEW_SECONDS, actor, version)
            self._slots[request_id] = _Slot(review)
            return review

    def _pending(self, receipt, digest, actor):
        slot = self._slots.get(receipt.request_id)
        if slot is None:
            raise ReadRefused("EXPIRED")
        review = slot.review
        if review.receipt != receipt or review.actor != actor:
            raise ReadRefused("DENIED")
        if type(digest) is not str or digest != review.digest:
            raise ReadRefused("CHANGED")
        if slot.state != "pending":
            raise ReadRefused("DUPLICATE", request_submitted=slot.state == "consumed")
        if monotonic() >= review.expires_at:
            raise ReadRefused("EXPIRED")
        return slot

    def dismiss(self, receipt, digest, verify, decision):
        if decision not in ("defer", "deny"):
            raise ReadRefused("INVALID")
        actor = _verified_context(verify, receipt.profile)
        with self._lock:
            self._pending(receipt, digest, actor).state = decision

    def admit(self, receipt, digest, verify, *, on_reserved=None):
        actor = _verified_context(verify, receipt.profile)
        with self._lock:
            slot = self._pending(receipt, digest, actor)
        review = slot.review
        home = Path(actor.profile_id)
        jobs = _native_jobs()
        submitted = False

        def authorize(job):
            nonlocal submitted
            _verified_context(verify, receipt.profile, actor)
            saved, version = _catalogue(home, receipt.job_id)
            if (job != saved or version != review.catalogue_version
                    or self._scope(actor, job) != review.scope
                    or _catalogue(home, receipt.job_id)[1] != version):
                raise ReadRefused("CHANGED")
            _verified_context(verify, receipt.profile, actor)
            with self._lock:
                self._pending(receipt, digest, actor).state = "consumed"
                submitted = True
            return True

        try:
            with _profile_home(jobs, home):
                snapshot = jobs.claim_reviewed_run_once(receipt.job_id,
                    execution_id=receipt.execution_id(), authorize=authorize)
            if snapshot is False:
                with self._lock:
                    self._pending(receipt, digest, actor)
                raise ReadRefused("BUSY")
            if (not submitted or type(snapshot) is not dict or snapshot.get("id") != receipt.job_id
                    or snapshot.get("execution_id") != receipt.execution_id()
                    or snapshot.get("_scheduled_instant") is not None):
                raise ReadRefused("UNAVAILABLE")
            approved = ApprovedRunOnce(receipt, review.scope, snapshot, actor)
            if on_reserved is not None:
                with _profile_home(jobs, home):
                    on_reserved(approved)
            _verified_context(verify, receipt.profile, actor)
            return approved
        except jobs.CronStoreLockUnavailable:
            raise ReadRefused("BUSY", request_submitted=submitted) from None
        except sqlite3.IntegrityError:
            raise ReadRefused("DUPLICATE", request_submitted=submitted) from None
        except ReadRefused as error:
            if submitted and not error.request_submitted:
                raise ReadRefused(error.code, request_submitted=True) from None
            raise
        except (OSError, ValueError, RuntimeError, TypeError, sqlite3.Error):
            raise ReadRefused("UNAVAILABLE", request_submitted=submitted) from None
