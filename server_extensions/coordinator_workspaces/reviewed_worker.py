"""Carry a consumed native project decision to the enrolled Windows owner."""
from collections.abc import Callable
from dataclasses import dataclass
from hashlib import sha256
import math
from pathlib import Path
from time import monotonic, time

from .coordinator import ReadRefused, _DEVICE, _identity, scope_for
from .cron_project_decisions import ApprovedOccurrence, observe_occurrence
from .policy_file import open_owner_directory, read_owner_file

if __package__ == "gateway.signal_grove_workspaces":
    from .approval_envelope import ApprovalRefused, MAX_APPROVAL_SECONDS, ReviewedWrite, sign
else:
    from ..worker_workspaces.approval_envelope import ApprovalRefused, MAX_APPROVAL_SECONDS, ReviewedWrite, sign



@dataclass(frozen=True, repr=False)
class OriginalWorkerReceipt:
    execution_id: str
    scope_id: str
    profile: str
    project: str
    worker: str
    worker_instance: str
    approval_sha256: str | None = None


@dataclass(frozen=True, repr=False)
class ProjectWorkerRoute:
    key_directory: Path
    key_name: str
    submit: Callable[[bytes], object]
    observe: Callable[[OriginalWorkerReceipt], dict | None]



def _require_native_claim(run):
    """Verify the current durable decision as well as the original native claim."""
    selection = run.occurrence
    execution = selection.execution_id()
    saved = run.snapshot
    claim = saved.get("fire_claim")
    if (saved.get("id") != selection.job_id or saved.get("execution_id") != execution
            or saved.get("_scheduled_instant") != run.scheduled_instant or type(claim) is not dict
            or claim.get("signal_grove_project_occurrence") != execution or not _identity(claim.get("by"))):
        raise ReadRefused("DENIED")
    current = observe_occurrence(Path(run.actor.profile_id), selection)
    record = current["execution"]
    if (current["decision"] != "approve" or current["outcome"] is not None
            or current["execution_id"] != execution or current["scheduled_instant"] != run.scheduled_instant
            or type(record) is not dict or record.get("status") != "running"
            or record.get("execution_id") != execution):
        raise ReadRefused("CHANGED")


class WorkerProjectDispatch:
    """Submit one consumed decision, then only observe the original worker receipt.

    Operator configuration supplies the existing authenticated route and a
    protected per-worker key. Submission is called once, including on timeout.
    The observer can wait for native settlement using read-only requests. A
    missing or foreign receipt remains unknown in the scheduler.
    """

    def __init__(self, authority, routes, *, clock=monotonic, wall_clock=time):
        if (not _identity(authority) or len(authority) > 256 or type(routes) is not dict
                or not 1 <= len(routes) <= 8
                or any(type(device) is not str or not _DEVICE.fullmatch(device)
                       or type(route) is not ProjectWorkerRoute
                       or not callable(route.submit) or not callable(route.observe)
                       for device, route in routes.items())):
            raise ReadRefused("INVALID")
        self._authority = authority
        self._routes = dict(routes)
        self._clock = clock
        self._wall_clock = wall_clock

    def __call__(self, run):
        if type(run) is not ApprovedOccurrence or type(run.snapshot) is not dict:
            raise ReadRefused("DENIED")
        scope = run.scope
        scope.values()
        route = self._routes.get(scope.worker)
        if (route is None or scope.project is None or scope.access != "write"
                or scope.checkout_revision is None or scope.write_policy_revision is None):
            raise ReadRefused("DENIED")
        _require_native_claim(run)
        with open_owner_directory(route.key_directory) as parent:
            key, _ = read_owner_file(parent, route.key_name, 32)
        if len(key) != 32:
            raise ReadRefused("UNAVAILABLE")
        _require_native_claim(run)
        seconds = min(MAX_APPROVAL_SECONDS, math.floor(run.expires_at - self._clock()))
        if seconds < 1:
            raise ReadRefused("EXPIRED")
        issued = math.floor(self._wall_clock())
        scope_id = scope_for(self._authority, run.actor.subject, run.actor.profile_id)
        packet = ReviewedWrite(
            authority=self._authority, scope_id=scope_id, profile=run.occurrence.profile,
            project=scope.project, workspace=scope.workspace, branch=scope.branch, commit=scope.commit,
            worker=scope.worker, worker_instance=scope.worker_instance, checkout_revision=scope.checkout_revision,
            access=scope.access, task=scope.task, execution_id=run.occurrence.execution_id(),
            review_digest=run.review_digest, write_policy_revision=scope.write_policy_revision,
            issued_at=issued, expires_at=issued + seconds,
        )
        try:
            signed = sign(packet, key)
        except ApprovalRefused:
            raise ReadRefused("DENIED") from None
        if self._clock() >= run.expires_at:
            raise ReadRefused("EXPIRED")
        selector = OriginalWorkerReceipt(
            packet.execution_id, packet.scope_id, packet.profile, packet.project, packet.worker, packet.worker_instance,
            sha256(signed).hexdigest(),
        )
        try:
            route.submit(signed)
        except Exception:
            # A lost reply may follow durable worker admission.
            pass
        try:
            result = route.observe(selector)
        except Exception:
            return None
        if (type(result) is dict and set(result) == {"id", "state", "approval_sha256"}
                and result["id"] == selector.execution_id and result["approval_sha256"] == selector.approval_sha256
                and type(result["state"]) is str
                and result["state"] in ("completed", "failed")):
            return result["state"]
        return None
