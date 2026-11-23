"""Bounded authentication of reviewed write approvals between enrolled owners."""
from dataclasses import asdict, dataclass
from hashlib import sha256
import hmac
import json
import re


MAX_APPROVAL_BYTES = 64 * 1024
MAX_APPROVAL_SECONDS = 180
_DOMAIN = b"signal-grove-reviewed-write-v1\0"
_HEX = re.compile(r"[a-f0-9]{64}")
_ID = re.compile(r"[a-f0-9]{32}")
_COMMIT = re.compile(r"(?:[a-f0-9]{40}|[a-f0-9]{64})")
_DEVICE = re.compile(r"[A-Z0-9][A-Z0-9_-]{0,62}")
_PROJECT = re.compile(r"[A-Za-z0-9][A-Za-z0-9_-]{0,79}")
_INSTANCE = re.compile(r"[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}")


class ApprovalRefused(ValueError):
    pass


def _unique(pairs):
    values = {}
    for key, value in pairs:
        if key in values:
            raise ApprovalRefused("INVALID")
        values[key] = value
    return values


def _read(raw, maximum):
    if type(raw) is not bytes or not 1 <= len(raw) <= maximum:
        raise ApprovalRefused("INVALID")
    try:
        result = json.loads(raw.decode("utf-8"), object_pairs_hook=_unique)
    except (UnicodeError, ValueError, RecursionError):
        raise ApprovalRefused("INVALID") from None
    if type(result) is not dict:
        raise ApprovalRefused("INVALID")
    return result


def _json(value):
    try:
        raw = json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(",", ":"), allow_nan=False).encode("utf-8")
    except (TypeError, ValueError, RecursionError):
        raise ApprovalRefused("INVALID") from None
    if len(raw) > MAX_APPROVAL_BYTES:
        raise ApprovalRefused("INVALID")
    return raw


def _text(value, maximum, *, multiline=False):
    return (type(value) is str and 1 <= len(value) <= maximum
            and not any((ord(c) < 32 and (not multiline or c not in "\n\t"))
                        or ord(c) == 127 or 0xd800 <= ord(c) <= 0xdfff for c in value))


def _matches(pattern, value):
    return type(value) is str and pattern.fullmatch(value) is not None


@dataclass(frozen=True, repr=False)
class ReviewedWrite:
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
    access: str
    task: str
    execution_id: str
    review_digest: str
    write_policy_revision: str
    issued_at: int
    expires_at: int

    def validate(self):
        if (not _text(self.authority, 256) or not _text(self.profile, 256)
                or not _matches(_HEX, self.scope_id) or not _matches(_PROJECT, self.project)
                or not _text(self.workspace, 4096) or not _text(self.branch, 1024)
                or not _matches(_COMMIT, self.commit) or not _matches(_DEVICE, self.worker)
                or not _matches(_INSTANCE, self.worker_instance) or not _matches(_HEX, self.checkout_revision)
                or type(self.access) is not str or self.access != "write"
                or not _text(self.task, 8192, multiline=True) or not _matches(_ID, self.execution_id)
                or not _matches(_HEX, self.review_digest) or not _matches(_HEX, self.write_policy_revision)
                or type(self.issued_at) is not int or type(self.expires_at) is not int
                or not 0 < self.issued_at < self.expires_at < 2**63
                or self.expires_at - self.issued_at > MAX_APPROVAL_SECONDS):
            raise ApprovalRefused("INVALID")

    def require_current(self, now):
        if self.issued_at > now + 5 or self.expires_at <= now:
            raise ApprovalRefused("EXPIRED")


def _signature(key, tagged):
    if type(key) is not bytes or len(key) != 32:
        raise ApprovalRefused("UNAVAILABLE")
    return hmac.new(key, _DOMAIN + _json(tagged), sha256).hexdigest()


def sign(approval, key):
    if type(approval) is not ReviewedWrite:
        raise ApprovalRefused("INVALID")
    approval.validate()
    tagged = {"schema": 1, "approval": asdict(approval)}
    return _json({**tagged, "mac": _signature(key, tagged)})


def verify(raw, key, *, now):
    signed = _read(raw, MAX_APPROVAL_BYTES)
    if set(signed) != {"schema", "approval", "mac"} or type(signed["schema"]) is not int or signed["schema"] != 1:
        raise ApprovalRefused("INVALID")
    mac = signed.pop("mac")
    if not _matches(_HEX, mac) or not hmac.compare_digest(mac, _signature(key, signed)):
        raise ApprovalRefused("DENIED")
    fields = signed["approval"]
    if type(fields) is not dict:
        raise ApprovalRefused("INVALID")
    try:
        approval = ReviewedWrite(**fields)
    except TypeError:
        raise ApprovalRefused("INVALID") from None
    approval.validate()
    approval.require_current(now)
    return approval
