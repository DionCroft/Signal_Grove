"""In-process checkout gates for a protected, approved worker queue entry."""
from contextlib import ExitStack, contextmanager
from dataclasses import dataclass
import math
from pathlib import Path
import re
from threading import Lock, get_ident
from time import monotonic

from .reviewed_checkout import ReviewedCheckout, hold_reviewed_checkout
from .workspace_provider import WindowsFiles, WorkspaceError, _normal


@dataclass(frozen=True, repr=False)
class ApprovedProjectWrite:
    execution_id: str
    profile: str
    approval_digest: str
    checkout: ReviewedCheckout
    model_profile: str
    native_scope: str
    task: str
    expires_at: float
    access: str = "write"

    def queue_payload(self):
        registration = self.checkout.registration
        return {
            "action": "reviewed_start",
            "id": self.execution_id,
            "project": registration.project_id,
            "profile": self.model_profile,
            "scope": self.native_scope,
            "prompt": self.task,
            "binding": {"id": registration.project_id, "root": self.checkout.root,
                        "device": registration.device},
        }


class ReviewedWorkerAdmission:
    """A broker-owned one-use gate. Resolve the write grant from protected policy.

    The approval is local owner state, not a queue payload or a read grant.
    Keep the same admission object in the owning broker through final launch.
    Reservation and outcome recovery remain in the worker's original queue.
    """

    def __init__(self, provider, approval, *, check_write_grant, git_executable, clock=monotonic):
        if (type(approval) is not ApprovedProjectWrite
                or not isinstance(approval.checkout, ReviewedCheckout)
                or not isinstance(approval.execution_id, str)
                or not re.fullmatch(r"[a-f0-9]{32}", approval.execution_id)
                or not isinstance(approval.approval_digest, str)
                or not re.fullmatch(r"[a-f0-9]{64}", approval.approval_digest)
                or not isinstance(approval.profile, str) or not approval.profile.strip()
                or not isinstance(approval.model_profile, str) or not approval.model_profile
                or not isinstance(approval.native_scope, str)
                or not re.fullmatch(r"[a-f0-9]{64}:[a-zA-Z0-9_-]{1,80}", approval.native_scope)
                or not isinstance(approval.task, str) or not 1 <= len(approval.task) <= 16000
                or type(approval.expires_at) not in (int, float) or not math.isfinite(approval.expires_at)
                or approval.access != "write"
                or not callable(check_write_grant)):
            raise WorkspaceError("INVALID")
        self._provider = provider
        self._approval = approval
        self._check_grant = check_write_grant
        self._git = git_executable
        self._clock = clock
        self._state = "ready"
        self._lock = Lock()
        self._launch_owner = None
        self.launch_attempted = False

    def _authorized(self):
        if self._clock() >= self._approval.expires_at:
            raise WorkspaceError("EXPIRED")
        if self._check_grant(self._approval) is not True:
            raise WorkspaceError("DENIED")

    @contextmanager
    def _hold(self, data, required, next_state):
        with self._lock:
            if self._state != required:
                raise WorkspaceError("DENIED")
            # Any failed transition requires a new authorized review.
            self._state = "spent"
            if type(data) is not dict or data != self._approval.queue_payload():
                raise WorkspaceError("CHANGED")
            self._authorized()
            with hold_reviewed_checkout(self._provider, self._approval.checkout, git_executable=self._git) as checkout:
                self._authorized()
                if next_state == "spent":
                    self._launch_owner = get_ident()
                try:
                    yield checkout
                finally:
                    self._launch_owner = None
            self._state = next_state

    def claim(self, data):
        """Hold checkout and grant through the original durable queue claim."""
        return self._hold(data, "ready", "claimed")

    def launch(self, data):
        """Recheck the same checkout and grant through the actual child creation."""
        return self._hold(data, "claimed", "spent")



    @contextmanager
    def job_directory(self, checkout):
        """Create the fixed job directory one level at a time under native handles."""
        if checkout != self._approval.checkout or self._launch_owner != get_ident():
            raise WorkspaceError("DENIED")
        current = Path(checkout.root)
        native = WindowsFiles()
        with ExitStack() as handles:
            for part in (".agentic", "dispatcher", "jobs", self._approval.execution_id):
                current = current / part
                current.mkdir(exist_ok=part != self._approval.execution_id)
                _, meta = handles.enter_context(native.opened(current, directory=True))
                if _normal(meta.path) != _normal(str(current)):
                    raise WorkspaceError("CHANGED")
            yield current
