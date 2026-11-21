"""Exact, paused scheduler timing updates using the native writer and durable receipts."""

from contextlib import contextmanager
from copy import deepcopy
from dataclasses import dataclass
from datetime import datetime
import hashlib
from inspect import signature
from pathlib import Path
import re

from .coordinator import ReadRefused, decode, encode
from .cron_history import _MAX_CATALOG, _ledger_identity, execution_query
from .policy_file import open_owner_directory, read_owner_file

_RECEIPTS = "signal_grove_timing_receipts_v1"
_KEEP = 20
_HEX = re.compile(r"[a-f0-9]{64}")
_ID = re.compile(r"[a-f0-9]{32}")
_ZONE = re.compile(r"[A-Za-z0-9_+.-]+(?:/[A-Za-z0-9_+.-]+)*")
_FIELDS = {"schedule", "timezone", "catch_up", "grace_minutes"}


@dataclass(frozen=True)
class EditQuery:
    profile: str
    job_id: str
    request_id: str | None = None
    expected_version: str | None = None
    changes: dict | None = None
    review_digest: str | None = None


def edit_query(params, action):
    base = {"expected_profile", "job_id"}
    keys = {
        "prepare": base,
        "observe": base | {"request_id"},
        "review": base | {"expected_version", "changes"},
        "apply": base | {"request_id", "expected_version", "changes", "review_digest"},
    }
    if type(params) is not dict or set(params) != keys[action]:
        raise ReadRefused("INVALID")
    target = execution_query({key: params[key] for key in base})
    request_id = params.get("request_id")
    if action in {"observe", "apply"} and (type(request_id) is not str or not _ID.fullmatch(request_id)):
        raise ReadRefused("INVALID")
    if action in {"prepare", "observe"}:
        return EditQuery(target.profile, target.job_id, request_id)
    review_digest = params.get("review_digest")
    if action == "apply" and (type(review_digest) is not str or not _HEX.fullmatch(review_digest)):
        raise ReadRefused("INVALID")
    version, changes = params["expected_version"], params["changes"]
    if (type(version) is not str or not _HEX.fullmatch(version) or type(changes) is not dict
            or not changes or not changes.keys() <= _FIELDS):
        raise ReadRefused("INVALID")
    for name, value in changes.items():
        if name == "schedule":
            if not _text(value, 512) or value != value.strip():
                raise ReadRefused("INVALID")
        else:
            _validate_setting(name, value, "INVALID")
    return EditQuery(target.profile, target.job_id, request_id, version, dict(sorted(changes.items())), review_digest)


def _text(value, maximum):
    return type(value) is str and 1 <= len(value) <= maximum and all(32 <= ord(c) < 127 for c in value)


def _validate_setting(name, value, code):
    if value is None:
        return
    if name == "timezone":
        allowed = _text(value, 128) and _ZONE.fullmatch(value) and not {"..", "."}.intersection(value.split("/"))
    elif name == "catch_up":
        allowed = type(value) is bool
    else:
        allowed = type(value) is int and 0 <= value <= 1440
    if not allowed:
        raise ReadRefused(code)


def _catalogue(home, job_id):
    with open_owner_directory(Path(home) / "cron") as parent:
        if _ledger_identity(parent, "jobs.json") is None:
            raise ReadRefused("NOT_FOUND")
        _ledger_identity(parent, ".jobs.lock")
        raw, identity = read_owner_file(parent, "jobs.json", _MAX_CATALOG)
    jobs = decode(raw, maximum=_MAX_CATALOG).get("jobs")
    if type(jobs) is not list:
        raise ReadRefused("UNAVAILABLE")
    records = {}
    for job in jobs:
        if type(job) is not dict or type(job.get("id")) is not str:
            raise ReadRefused("UNAVAILABLE")
        key = job["id"]
        if key in records:
            raise ReadRefused("DUPLICATE")
        repeat = job.get("repeat")
        if type(repeat) is dict and "completed" in repeat and (
                type(repeat["completed"]) is not int or repeat["completed"] < 0):
            raise ReadRefused("UNAVAILABLE")
        records[key] = job
    if job_id not in records:
        raise ReadRefused("NOT_FOUND")
    version = hashlib.sha256(b"signal-grove-cron-catalogue-v1\0" + encode({"identity": identity}) + raw).hexdigest()
    return records[job_id], version


def _paused(job):
    if job.get("state") != "paused" or job.get("enabled") is not False or job.get("fire_claim"):
        raise ReadRefused("BUSY")


def _schedule(stored):
    if type(stored) is not dict:
        raise ReadRefused("UNAVAILABLE")
    kind = stored.get("kind")
    if kind == "cron" and _text(stored.get("expr"), 512):
        return {"kind": kind, "expr": stored["expr"]}
    if kind == "interval" and type(stored.get("minutes")) is int and stored["minutes"] > 0:
        return {"kind": kind, "minutes": stored["minutes"]}
    if kind == "once" and _text(stored.get("run_at"), 64):
        instant = datetime.fromisoformat(stored["run_at"])
        if instant.utcoffset() is not None:
            return {"kind": kind, "run_at": instant.isoformat()}
    raise ReadRefused("UNAVAILABLE")


def _timing(job):
    result = {"schedule": _schedule(job.get("schedule"))}
    for field in ("timezone", "catch_up", "grace_minutes"):
        value = job.get(field)
        _validate_setting(field, value, "UNAVAILABLE")
        result[field] = value
    return result


def _repeat(value):
    if value is None:
        return None
    if type(value) is not dict or "times" not in value or "completed" not in value:
        raise ReadRefused("UNAVAILABLE")
    times, completed = value["times"], value["completed"]
    if ((times is not None and (type(times) is not int or times < 1))
            or type(completed) is not int or completed < 0):
        raise ReadRefused("UNAVAILABLE")
    return {"times": times, "completed": completed}


def _effect(job):
    return {"timing": _timing(job), "repeat": _repeat(job.get("repeat"))}


def _receipts(job):
    records = job.get(_RECEIPTS, [])
    if type(records) is not list or len(records) > _KEEP:
        raise ReadRefused("UNAVAILABLE")
    seen = set()
    base = {"request_id", "fingerprint", "reviewed_version", "timing"}
    for record in records:
        if (type(record) is not dict or set(record) not in (base, base | {"review_digest", "repeat"})
                or type(record["request_id"]) is not str or not _ID.fullmatch(record["request_id"])
                or record["request_id"] in seen
                or type(record["fingerprint"]) is not str or not _HEX.fullmatch(record["fingerprint"])
                or type(record["reviewed_version"]) is not str or not _HEX.fullmatch(record["reviewed_version"])
                or type(record["timing"]) is not dict or _timing(record["timing"]) != record["timing"]):
            raise ReadRefused("UNAVAILABLE")
        if "review_digest" in record and (
                type(record["review_digest"]) is not str or not _HEX.fullmatch(record["review_digest"])
                or record["repeat"] != _repeat(record["repeat"])):
            raise ReadRefused("UNAVAILABLE")
        seen.add(record["request_id"])
    return records


def _result(query, record):
    result = {key: value for key, value in record.items() if key != "fingerprint"}
    return {"profile": query.profile, "job_id": query.job_id, **result}


def _native():
    from cron import jobs
    if ("require_cross_process" not in signature(jobs._jobs_lock).parameters
            or "finalize_review" not in signature(jobs.update_job).parameters
            or not callable(getattr(jobs, "_project_job_update", None))):
        raise ReadRefused("UNSUPPORTED")
    return jobs


def prepare_edit(home, query):
    _native()
    try:
        job, version = _catalogue(home, query.job_id)
        _paused(job)
        return {"profile": query.profile, "job_id": query.job_id, "version": version, **_effect(job)}
    except (OSError, TypeError, ValueError):
        raise ReadRefused("UNAVAILABLE") from None


def observe_edit(home, query):
    _native()
    try:
        job, _ = _catalogue(home, query.job_id)
        for record in _receipts(job):
            if record["request_id"] == query.request_id:
                return _result(query, record)
    except (OSError, TypeError, ValueError):
        raise ReadRefused("UNAVAILABLE") from None
    raise ReadRefused("NOT_FOUND")


def _reviewed_changes(jobs, changes):
    prepared = dict(changes)
    if "schedule" in prepared:
        text = prepared["schedule"]
        try:
            scheduled = jobs.parse_schedule(text)
            if scheduled.get("kind") == "once" and datetime.fromisoformat(text).utcoffset() is None:
                raise ValueError("One-time edit requires an explicit UTC offset.")
        except (TypeError, ValueError):
            raise ReadRefused("INVALID") from None
        prepared["schedule"] = scheduled
    return prepared


@contextmanager
def _profile_home(jobs, home):
    from hermes_constants import reset_hermes_home_override, set_hermes_home_override
    token = set_hermes_home_override(str(home))
    try:
        with jobs.use_cron_store(home):
            yield
    finally:
        reset_hermes_home_override(token)


def _profile_timezone():
    from hermes_time import get_timezone
    zone = get_timezone()
    return zone.key if zone is not None else None


def _plan(jobs, saved, version, query):
    if version != query.expected_version:
        raise ReadRefused("CHANGED")
    _paused(saved)
    updates = _reviewed_changes(jobs, query.changes)
    if "schedule" in updates or "timezone" in updates:
        updates["next_run_at"] = None
    projected = jobs._project_job_update(query.job_id, deepcopy(saved), deepcopy(updates))
    _paused(projected)
    review = {
        "profile": query.profile, "job_id": query.job_id, "version": version,
        "profile_timezone": _profile_timezone(), "before": _effect(saved), "after": _effect(projected),
    }
    digest = hashlib.sha256(b"signal-grove-cron-review-v1\0"
                            + encode({"review": review, "changes": query.changes})).hexdigest()
    return {**review, "review_digest": digest}, updates


def review_edit(home, query):
    jobs = _native()
    try:
        saved, version = _catalogue(home, query.job_id)
        with _profile_home(jobs, home):
            review, _ = _plan(jobs, saved, version, query)
        if _catalogue(home, query.job_id)[1] != version:
            raise ReadRefused("CHANGED")
        return review
    except ValueError:
        raise ReadRefused("INVALID") from None
    except (OSError, TypeError, RuntimeError):
        raise ReadRefused("UNAVAILABLE") from None


def apply_edit(home, query, verify_authority):
    jobs = _native()
    started = False
    try:
        key = hashlib.sha256(encode({"version": query.expected_version, "changes": query.changes,
                                     "review_digest": query.review_digest})).hexdigest()
        # Check the protected store before native locking can create directories.
        _catalogue(home, query.job_id)
        with _profile_home(jobs, home), jobs._jobs_lock(require_cross_process=True):
            verify_authority()
            saved, version = _catalogue(home, query.job_id)
            history = _receipts(saved)
            for record in history:
                if record["request_id"] == query.request_id:
                    if record["fingerprint"] != key:
                        raise ReadRefused("DUPLICATE")
                    return _result(query, record)
            plan, updates = _plan(jobs, saved, version, query)
            if plan["review_digest"] != query.review_digest:
                raise ReadRefused("CHANGED")
            expected = plan["after"]
            record = {"request_id": query.request_id, "fingerprint": key, "reviewed_version": version,
                      "review_digest": query.review_digest, **expected}

            def finalize(updated):
                nonlocal started
                verify_authority()
                if _catalogue(home, query.job_id)[1] != version:
                    raise ReadRefused("CHANGED")
                _paused(updated)
                if _effect(updated) != expected or _profile_timezone() != plan["profile_timezone"]:
                    raise ReadRefused("CHANGED")
                updated[_RECEIPTS] = (history + [record])[-_KEEP:]
                started = True

            result = jobs.update_job(query.job_id, updates, finalize_review=finalize)
            if result is None or not started:
                raise ReadRefused("NOT_FOUND")
            confirmed, _ = _catalogue(home, query.job_id)
            if _receipts(confirmed)[-1:] != [record] or _effect(confirmed) != expected:
                raise ReadRefused("CHANGED", request_submitted=True)
            _paused(confirmed)
            return _result(query, record)
    except jobs.CronStoreLockUnavailable:
        raise ReadRefused("BUSY") from None
    except ReadRefused as error:
        if started and not error.request_submitted:
            raise ReadRefused(error.code, request_submitted=True) from None
        raise
    except ValueError:
        raise ReadRefused("UNAVAILABLE" if started else "INVALID", request_submitted=started) from None
    except (OSError, TypeError, RuntimeError):
        raise ReadRefused("UNAVAILABLE", request_submitted=started) from None
