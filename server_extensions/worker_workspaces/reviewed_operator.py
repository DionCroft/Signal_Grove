"""Inside the owner broker, admit an authenticated original review to its native queue."""
from dataclasses import dataclass
from hashlib import sha256
import json
import re
from threading import Lock
from time import monotonic, time

from .approval_envelope import (
    ApprovalRefused, _DEVICE, _HEX, _ID, _INSTANCE, _PROJECT, _json, _matches, _read, _text, sign, verify,
)
from .broker_enrollment import OwnerObservation, observe_owner
from .protected_operator_file import read_protected_file
from .reviewed_checkout import hold_reviewed_checkout, observe_checkout
from .reviewed_dispatch import ApprovedProjectWrite, ReviewedWorkerAdmission
from .workspace_provider import WorkspaceError, WorkspaceProvider


_MAX_POLICY_BYTES = 32 * 1024
_MAX_ADMISSIONS = 64
_SID = re.compile(r"S-1-(?:[0-9]+-){1,15}[0-9]+")
_NATIVE_SCOPE = re.compile(r"[a-f0-9]{64}:[a-zA-Z0-9_-]{1,80}")
_GRANT_FIELDS = {"scope_id", "profile", "project", "model_profile", "native_scope"}
_APPROVAL_TABLE = "signal_grove_reviewed_queue"
_RECEIPT_FIELDS = ("authority", "scope_id", "profile", "project", "worker_instance")
_QUEUE_STATES = frozenset({"ready", "claimed", "running", "completed", "failed", "cancelled", "unknown"})


@dataclass(frozen=True, repr=False)
class WriteGrant:
    scope_id: str
    profile: str
    project: str
    model_profile: str
    native_scope: str

    def selector(self):
        return self.scope_id, self.profile, self.project


@dataclass(frozen=True, repr=False)
class WritePolicy:
    authority: str
    device: str
    owner_sid: str
    revision: str
    grants: tuple[WriteGrant, ...]

    def grant(self, scope_id, profile, project):
        for grant in self.grants:
            if grant.selector() == (scope_id, profile, project):
                return grant
        raise WorkspaceError("DENIED")


def parse_write_policy(raw):
    try:
        value = _read(raw, _MAX_POLICY_BYTES)
    except ApprovalRefused:
        raise WorkspaceError("INVALID") from None
    if (set(value) != {"schema", "authority", "device", "owner_sid", "grants"}
            or type(value["schema"]) is not int or value["schema"] != 1
            or not _text(value["authority"], 256) or not _matches(_DEVICE, value["device"])
            or not _matches(_SID, value["owner_sid"])
            or type(value["grants"]) is not list or len(value["grants"]) > 64):
        raise WorkspaceError("INVALID")
    grants = {}
    for fields in value["grants"]:
        if (type(fields) is not dict or set(fields) != _GRANT_FIELDS
                or not _matches(_HEX, fields["scope_id"]) or not _text(fields["profile"], 256)
                or not _matches(_PROJECT, fields["project"]) or not _text(fields["model_profile"], 128)
                or not _matches(_NATIVE_SCOPE, fields["native_scope"])):
            raise WorkspaceError("INVALID")
        grant = WriteGrant(**fields)
        if grant.selector() in grants:
            raise WorkspaceError("INVALID")
        grants[grant.selector()] = grant
    return WritePolicy(value["authority"], value["device"], value["owner_sid"],
                       sha256(raw).hexdigest(), tuple(grants.values()))


def _checkout_revision(checkout):
    binding = checkout.registration
    return sha256(_json([
        "signal-grove-worker-checkout-v1", binding.project_id, binding.device, binding.scope_id,
        binding.registry_revision, checkout.worker_instance, checkout.root, checkout.root_identity,
        checkout.git_identity, checkout.branch, checkout.commit,
    ])).hexdigest()


@dataclass(frozen=True, repr=False)
class WriteReview:
    authority: str
    scope_id: str
    profile: str
    project: str
    workspace: str
    branch: str
    commit: str
    worker: str
    worker_instance: str
    checkout_revision: str
    write_policy_revision: str


class ReviewedWorkerOperator:
    """Trusted broker configuration and the native queue's in-process lookup."""

    def __init__(self, *, policy_path, key_path, provider_for_scope, queue_db, git_executable,
                 read_queue=None, owner_observer=observe_owner, wall_clock=time, clock=monotonic):
        self._policy_path = policy_path
        self._key_path = key_path
        self._provider_for_scope = provider_for_scope
        self._queue_db = queue_db
        self._read_queue = read_queue
        self._git = git_executable
        self._observe_owner = owner_observer
        self._wall_clock = wall_clock
        self._clock = clock
        self._owner = self._observe_owner()
        if type(self._owner) is not OwnerObservation or not self._owner.active:
            raise WorkspaceError("DENIED")
        self._admissions = {}
        self._lock = Lock()

    def _credentials(self):
        owner = self._observe_owner()
        if owner != self._owner:
            raise WorkspaceError("DENIED")
        policy = parse_write_policy(read_protected_file(
            self._policy_path, owner_sid=owner.owner_sid, maximum=_MAX_POLICY_BYTES,
        ))
        if policy.device != owner.device or policy.owner_sid != owner.owner_sid:
            raise WorkspaceError("DENIED")
        key = read_protected_file(self._key_path, owner_sid=owner.owner_sid, maximum=32)
        if len(key) != 32 or self._observe_owner() != owner:
            raise WorkspaceError("DENIED")
        return policy, key

    def _observe(self, scope_id, project):
        provider = self._provider_for_scope(scope_id)
        if not isinstance(provider, WorkspaceProvider) or provider.scope != scope_id:
            raise WorkspaceError("DENIED")
        checkout = observe_checkout(provider, project, git_executable=self._git)
        if checkout.registration.scope_id != scope_id or checkout.registration.device != self._owner.device:
            raise WorkspaceError("DENIED")
        return provider, checkout

    def prepare(self, scope_id, profile, project):
        """Capture the signed fields from the authenticated owner route before review."""
        policy, key = self._credentials()
        policy.grant(scope_id, profile, project)
        _, checkout = self._observe(scope_id, project)
        current, current_key = self._credentials()
        if (current.revision, current_key) != (policy.revision, key):
            raise WorkspaceError("CHANGED")
        return WriteReview(
            policy.authority, scope_id, profile, project, checkout.root, checkout.branch, checkout.commit,
            policy.device, checkout.worker_instance, _checkout_revision(checkout), policy.revision,
        )

    @staticmethod
    def _grant(policy, approval):
        if approval.authority != policy.authority or approval.worker != policy.device:
            raise WorkspaceError("DENIED")
        if approval.write_policy_revision != policy.revision:
            raise WorkspaceError("CHANGED")
        return policy.grant(approval.scope_id, approval.profile, approval.project)

    def _confirm(self, approval, grant, key_digest, provider):
        policy, key = self._credentials()
        if (self._grant(policy, approval) != grant or sha256(key).digest() != key_digest
                or self._provider_for_scope(approval.scope_id) is not provider):
            raise WorkspaceError("CHANGED")
        try:
            approval.require_current(self._wall_clock())
        except ApprovalRefused as error:
            raise WorkspaceError(str(error)) from None
        return True

    @staticmethod
    def _existing(db, ident, digest):
        row = db.execute("SELECT state FROM requests WHERE id=?", (ident,)).fetchone()
        if row is None:
            return None
        table = db.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",
                           (_APPROVAL_TABLE,)).fetchone()
        previous = db.execute(
            "SELECT approval_sha256 FROM signal_grove_reviewed_queue WHERE id=?", (ident,),
        ).fetchone() if table else None
        if previous != (digest,):
            raise WorkspaceError("CHANGED")
        return {"id": ident, "state": row[0]}

    @staticmethod
    def _prepare_receipt_table(db):
        db.execute(
            "CREATE TABLE IF NOT EXISTS signal_grove_reviewed_queue "
            "(id TEXT PRIMARY KEY, approval_sha256 TEXT NOT NULL, authority TEXT, scope_id TEXT, "
            "profile TEXT, project TEXT, worker_instance TEXT)",
        )
        # An earlier staged candidate stored only the replay fence. Such rows
        # retain that fence but lack the evidence for an authenticated receipt.
        columns = {row[1] for row in db.execute("PRAGMA table_info(signal_grove_reviewed_queue)")}
        for name in _RECEIPT_FIELDS:
            if name not in columns:
                db.execute(f"ALTER TABLE signal_grove_reviewed_queue ADD COLUMN {name} TEXT")

    def accept(self, raw, *, expected_route=None):
        """Authenticate a consumed scheduler review; return the same receipt on retry."""
        policy, key = self._credentials()
        try:
            approval = verify(raw, key, now=self._wall_clock())
        except ApprovalRefused as error:
            raise WorkspaceError(str(error)) from None
        route = (approval.scope_id, approval.profile, approval.project, approval.worker_instance)
        if expected_route is not None and route != expected_route:
            raise WorkspaceError("DENIED")
        deadline = self._clock() + (approval.expires_at - self._wall_clock())
        grant = self._grant(policy, approval)
        ident = approval.execution_id
        key_digest = sha256(key).digest()
        digest = sha256(sign(approval, key)).hexdigest()
        with self._queue_db() as db:
            existing = self._existing(db, ident, digest)
            if existing is not None:
                return existing
        provider, checkout = self._observe(approval.scope_id, approval.project)
        if (checkout.root, checkout.branch, checkout.commit, checkout.worker_instance,
                _checkout_revision(checkout)) != (approval.workspace, approval.branch, approval.commit,
                                                   approval.worker_instance, approval.checkout_revision):
            raise WorkspaceError("CHANGED")
        intent = ApprovedProjectWrite(
            ident, approval.profile, approval.review_digest, checkout, grant.model_profile,
            grant.native_scope, approval.task, deadline,
        )
        def current(_):
            return self._confirm(approval, grant, key_digest, provider)

        admission = ReviewedWorkerAdmission(
            provider, intent, check_write_grant=current, git_executable=self._git, clock=self._clock,
        )
        with self._queue_db() as db:
            db.execute("BEGIN IMMEDIATE")
            # The native claimant already holds this database lock when it calls lookup.
            with self._lock:
                current(intent)
                existing = self._existing(db, ident, digest)
                if existing is not None:
                    return existing
                with hold_reviewed_checkout(provider, checkout, git_executable=self._git):
                    current(intent)
                    now = self._clock()
                    if deadline <= now:
                        raise WorkspaceError("EXPIRED")
                    self._admissions = {
                        entry_id: slot for entry_id, slot in self._admissions.items() if slot[0] > now
                    }
                    if len(self._admissions) >= _MAX_ADMISSIONS:
                        raise WorkspaceError("BUSY")
                    self._prepare_receipt_table(db)
                    received = self._wall_clock()
                    db.execute("INSERT INTO requests VALUES(?,?,?,?,?)",
                               (ident, json.dumps(intent.queue_payload(), sort_keys=True), "ready", received, received))
                    db.execute(
                        "INSERT INTO signal_grove_reviewed_queue "
                        "(id,approval_sha256,authority,scope_id,profile,project,worker_instance) VALUES(?,?,?,?,?,?,?)",
                        (ident, digest, approval.authority, *route),
                    )
                    db.commit()
                    self._admissions[ident] = deadline, admission
        return {"id": ident, "state": "ready"}

    def observe(self, scope_id, profile, project, execution_id, worker_instance):
        """Read the exact durable scope on the original row, including after restart."""
        if self._read_queue is None:
            raise WorkspaceError("UNAVAILABLE")
        if not _matches(_ID, execution_id) or not _matches(_INSTANCE, worker_instance):
            raise WorkspaceError("INVALID")
        policy, key = self._credentials()
        policy.grant(scope_id, profile, project)
        with self._read_queue() as db:
            columns = {row[1] for row in db.execute("PRAGMA table_info(signal_grove_reviewed_queue)")}
            if not set(_RECEIPT_FIELDS) <= columns:
                raise WorkspaceError("NOT_FOUND")
            row = db.execute(
                "SELECT q.state FROM requests q JOIN signal_grove_reviewed_queue r ON q.id=r.id "
                "WHERE r.id=? AND r.authority=? AND r.scope_id=? AND r.profile=? "
                "AND r.project=? AND r.worker_instance=?",
                (execution_id, policy.authority, scope_id, profile, project, worker_instance),
            ).fetchone()
        current, current_key = self._credentials()
        if (current.revision, current_key) != (policy.revision, key):
            raise WorkspaceError("CHANGED")
        if row is None:
            raise WorkspaceError("NOT_FOUND")
        if row[0] not in _QUEUE_STATES:
            raise WorkspaceError("CHANGED")
        return {"id": execution_id, "state": row[0]}

    def lookup(self, ident):
        if not _matches(_ID, ident):
            raise WorkspaceError("INVALID")
        with self._lock:
            slot = self._admissions.get(ident)
            if slot is None or slot[0] <= self._clock():
                raise WorkspaceError("DENIED")
            return slot[1]
