"""Protected schedule classification and a read-only inbox of held occurrences."""

from dataclasses import dataclass
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
from types import MappingProxyType
from typing import Mapping

from .coordinator import ReadRefused, _PROJECT, decode, encode
from .cron_edit import _catalogue_records
from .operator_policy import MAX_POLICY
from .policy_file import read_owner_document

POLICY_NAME = "signal-grove-schedule-policy.json"
_MAX_JOBS = 64
_JOB = re.compile(r"[A-Za-z0-9][A-Za-z0-9_.:-]{0,255}")
_PROFILE = re.compile(r"[a-z0-9][a-z0-9_-]{0,63}")
_DIGEST = re.compile(r"[a-f0-9]{64}")
_MAX_SPEC_BYTES = 128 * 1024
# Native bookkeeping changes after a fire. All execution settings and
# unfamiliar job fields participate in the automatic approval digest.
_RUNTIME_FIELDS = frozenset({
    "state", "enabled", "paused_at", "paused_reason", "created_at", "next_run_at",
    "last_run_at", "last_status", "last_error", "last_delivery_error",
    "last_delivery_unverified", "last_delivery_queued", "last_failure", "failure_streak",
    "last_dispatch", "monitor_state", "pending_slot", "run_claim", "fire_claim",
    "execution_id", "_scheduled_instant",
})


@dataclass(frozen=True, repr=False)
class SchedulePolicy:
    automatic: Mapping[str, str]
    project_writes: Mapping[str, str]
    revision: str


@dataclass(frozen=True)
class ScheduleInboxQuery:
    profile: str


def _job_id(value):
    return type(value) is str and value != "unknown" and _JOB.fullmatch(value) is not None


def inbox_query(params):
    if type(params) is not dict or set(params) != {"expected_profile"}:
        raise ReadRefused("INVALID")
    profile = params["expected_profile"]
    if type(profile) is not str or profile == "all" or not _PROFILE.fullmatch(profile):
        raise ReadRefused("INVALID")
    return ScheduleInboxQuery(profile)


def _read_policy(home):
    document = read_owner_document(Path(home) / "cron", POLICY_NAME)
    data = decode(document.data, MAX_POLICY)
    if (set(data) != {"schema", "automatic", "project_writes"}
            or type(data["schema"]) is not int or data["schema"] != 1
            or type(data["automatic"]) is not list or type(data["project_writes"]) is not list
            or len(data["automatic"]) + len(data["project_writes"]) > _MAX_JOBS):
        raise ReadRefused("UNAVAILABLE")
    automatic = {}
    for item in data["automatic"]:
        if type(item) is not dict or set(item) != {"job_id", "spec_digest"}:
            raise ReadRefused("UNAVAILABLE")
        job, digest = item["job_id"], item["spec_digest"]
        if (not _job_id(job) or job in automatic or type(digest) is not str or not _DIGEST.fullmatch(digest)):
            raise ReadRefused("UNAVAILABLE")
        automatic[job] = digest
    writes = {}
    for item in data["project_writes"]:
        if type(item) is not dict or set(item) != {"job_id", "project_id"}:
            raise ReadRefused("UNAVAILABLE")
        job, project = item["job_id"], item["project_id"]
        if (not _job_id(job) or job in writes or job in automatic
                or type(project) is not str or not _PROJECT.fullmatch(project)):
            raise ReadRefused("UNAVAILABLE")
        writes[job] = project
    return SchedulePolicy(MappingProxyType(automatic), MappingProxyType(writes), document.version)


def automatic_jobs(home, cron_dir, jobs_file):
    """For native execution. A missing, invalid or redirected policy holds every job."""
    directory = Path(home) / "cron"
    if Path(cron_dir) != directory or Path(jobs_file) != directory / "jobs.json":
        return {}
    try:
        return _read_policy(home).automatic
    except ReadRefused:
        return {}


def automatic_spec_digest(job):
    """Bind the frozen native job definition without its execution counters."""
    if type(job) is not dict or not _job_id(job.get("id")) or any(type(key) is not str for key in job):
        raise ReadRefused("INVALID")
    spec = {key: value for key, value in job.items() if key not in _RUNTIME_FIELDS}
    if type(spec.get("repeat")) is dict:
        spec["repeat"] = {key: value for key, value in spec["repeat"].items() if key != "completed"}
    try:
        encoded = json.dumps(spec, ensure_ascii=True, allow_nan=False, sort_keys=True,
                             separators=(",", ":")).encode("ascii")
    except (TypeError, ValueError, RecursionError):
        raise ReadRefused("INVALID") from None
    if len(encoded) > _MAX_SPEC_BYTES:
        raise ReadRefused("TOO_LARGE")
    return hashlib.sha256(b"signal-grove-ordinary-schedule-v1\0" + encoded).hexdigest()


def matches_automatic(automatic, job):
    if type(job) is not dict or not _job_id(job.get("id")) or job["id"] not in automatic:
        return False
    try:
        return automatic[job["id"]] == automatic_spec_digest(job)
    except ReadRefused:
        return False


def _due_instant(job, now):
    if (job.get("enabled") is not True or job.get("state") != "scheduled"
            or job.get("paused_at")
            or any(job.get(key) is not None for key in (
                "fire_claim", "run_claim", "pending_slot",
                "manual_run_at", "manual_run_restore_at", "manual_run_prompt"))):
        return None
    instant = job.get("next_run_at")
    if type(instant) is not str or not 1 <= len(instant) <= 64:
        return None
    try:
        parsed = datetime.fromisoformat(instant)
        if parsed.utcoffset() is None or parsed > now:
            return None
        return parsed.astimezone(timezone.utc).isoformat()
    except (ValueError, OverflowError):
        return None


def _occurrence_id(profile, home, job, instant):
    # This addresses a stored occurrence. A future decision must also bind
    # the exact catalogue, current policy revision and verified worker scope.
    marker = encode([profile, str(home), job, instant])
    return hashlib.sha256(b"signal-grove-scheduled-occurrence-v1\0" + marker).hexdigest()[:32]


def read_inbox(home, query, *, now=None):
    home = Path(home)
    policy = _read_policy(home)
    records, version = _catalogue_records(home)
    now = now if now is not None else datetime.now(timezone.utc)
    entries = []
    for job_id, project in policy.project_writes.items():
        instant = _due_instant(records.get(job_id, {}), now)
        if instant is not None:
            entries.append({"job_id": job_id, "project_id": project, "scheduled_instant": instant,
                            "occurrence_id": _occurrence_id(query.profile, home, job_id, instant)})
    if _catalogue_records(home)[1] != version or _read_policy(home).revision != policy.revision:
        raise ReadRefused("CHANGED")
    entries.sort(key=lambda item: (item["scheduled_instant"], item["job_id"]))
    return {"profile": query.profile, "entries": entries}
