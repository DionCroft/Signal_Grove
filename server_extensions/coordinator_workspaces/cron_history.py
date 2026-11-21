"""Owner/profile-bound reads of Hermes' durable scheduler ledger on Linux."""

from contextlib import closing, contextmanager
from dataclasses import dataclass
from datetime import datetime
import os
from pathlib import Path
import re
import sqlite3
import stat
import sys
import time

from .coordinator import ReadRefused, decode
from .policy_file import open_owner_directory, read_owner_file

_MAX_CATALOG = 4 * 1024 * 1024
_MAX_ATTEMPTS = 20
_PROFILE = re.compile(r'[a-z0-9][a-z0-9_-]{0,63}')
_JOB_ID = re.compile(r'[A-Za-z0-9][A-Za-z0-9_.:-]{0,255}')
_EXECUTION_ID = re.compile(r'[a-f0-9]{32}')
_OUTCOME_CODE = re.compile(r'[a-z][a-z0-9_]{0,47}')
_STATUSES = {'claimed', 'running', 'completed', 'failed', 'unknown', 'cancelled', 'missed'}
_LEDGER = 'executions.db'
_SELECT = """
    SELECT id, status, claimed_at, started_at, finished_at, scheduled_instant,
           delivery_outcome,
           CASE WHEN error IS NULL OR error = '' THEN 0 ELSE 1 END AS reported_error
      FROM executions WHERE job_id = ?
"""


@dataclass(frozen=True)
class ExecutionQuery:
    profile: str
    job_id: str
    limit: int
    execution_id: str | None


def execution_query(params):
    required = {'expected_profile', 'job_id'}
    if type(params) is not dict or not required <= params.keys() or params.keys() - (required | {'limit', 'execution_id'}):
        raise ReadRefused('INVALID')
    profile, job_id = params['expected_profile'], params['job_id']
    if type(profile) is not str or profile == 'all' or not _PROFILE.fullmatch(profile):
        raise ReadRefused('INVALID')
    if type(job_id) is not str or job_id == 'unknown' or not _JOB_ID.fullmatch(job_id):
        raise ReadRefused('INVALID')
    if 'execution_id' in params:
        exact = params['execution_id']
        if 'limit' in params or type(exact) is not str or not _EXECUTION_ID.fullmatch(exact):
            raise ReadRefused('INVALID')
        return ExecutionQuery(profile, job_id, 1, exact)
    limit = params.get('limit', _MAX_ATTEMPTS)
    if type(limit) is not int or not 1 <= limit <= _MAX_ATTEMPTS:
        raise ReadRefused('INVALID')
    return ExecutionQuery(profile, job_id, limit, None)


def _catalog(parent, job_id):
    try:
        os.stat('jobs.json', dir_fd=parent, follow_symlinks=False)
    except FileNotFoundError:
        raise ReadRefused('NOT_FOUND') from None
    raw, identity = read_owner_file(parent, 'jobs.json', _MAX_CATALOG)
    document = decode(raw, maximum=_MAX_CATALOG)
    jobs = document.get('jobs')
    if type(jobs) is not list or any(type(job) is not dict for job in jobs):
        raise ReadRefused('UNAVAILABLE')
    count = sum(job.get('id') == job_id for job in jobs)
    if count == 0:
        raise ReadRefused('NOT_FOUND')
    if count != 1:
        raise ReadRefused('DUPLICATE')
    return raw, identity


def _disk_identity(value):
    return value.st_dev, value.st_ino, value.st_uid, value.st_gid, value.st_mode, value.st_nlink


def _ledger_identity(parent, name):
    try:
        value = os.stat(name, dir_fd=parent, follow_symlinks=False)
    except FileNotFoundError:
        return None
    if (not stat.S_ISREG(value.st_mode) or value.st_ino <= 0 or value.st_nlink != 1
            or value.st_uid not in (0, os.geteuid()) or value.st_mode & 0o022):
        raise ReadRefused('UNAVAILABLE')
    return _disk_identity(value)


def _checked_timestamp(value, optional=False):
    if value is None and optional:
        return None
    if type(value) is not str or not 1 <= len(value) <= 64:
        raise ReadRefused('UNAVAILABLE')
    parsed = datetime.fromisoformat(value)
    if parsed.utcoffset() is None:
        raise ReadRefused('UNAVAILABLE')
    return parsed.isoformat()


def _project(row):
    execution_id, status = row['id'], row['status']
    if type(execution_id) is not str or not _EXECUTION_ID.fullmatch(execution_id) or status not in _STATUSES:
        raise ReadRefused('UNAVAILABLE')
    delivery = row['delivery_outcome']
    if delivery is not None and (type(delivery) is not str or not _OUTCOME_CODE.fullmatch(delivery)):
        raise ReadRefused('UNAVAILABLE')
    return {
        'execution_id': execution_id,
        'status': status,
        'claimed_at': _checked_timestamp(row['claimed_at']),
        'started_at': _checked_timestamp(row['started_at'], optional=True),
        'finished_at': _checked_timestamp(row['finished_at'], optional=True),
        'scheduled_instant': _checked_timestamp(row['scheduled_instant'], optional=True),
        'delivery_outcome': delivery,
        'reported_error': row['reported_error'] == 1,
    }


@contextmanager
def _open_read_ledger(parent):
    identity = _ledger_identity(parent, _LEDGER)
    sidecars = {name: _ledger_identity(parent, name) for name in (_LEDGER + '-wal', _LEDGER + '-shm')}
    if identity is None:
        if any(value is not None for value in sidecars.values()):
            raise ReadRefused('UNAVAILABLE')
        yield None
        if _ledger_identity(parent, _LEDGER) is not None:
            raise ReadRefused('CHANGED')
        return
    # The descriptor anchors the database and its adjacent WAL.
    pinned = Path(f'/proc/self/fd/{parent}') / _LEDGER
    if _disk_identity(pinned.stat()) != identity:
        raise ReadRefused('CHANGED')
    end = time.monotonic() + 0.25
    with closing(sqlite3.connect(pinned.as_uri() + '?mode=ro', uri=True, timeout=0.1)) as db:
        db.row_factory = sqlite3.Row
        db.execute('PRAGMA query_only=ON')
        db.execute('PRAGMA trusted_schema=OFF')
        db.set_progress_handler(lambda: int(time.monotonic() >= end), 500)
        yield db
    if _ledger_identity(parent, _LEDGER) != identity:
        raise ReadRefused('CHANGED')
    for name, previous in sidecars.items():
        if previous is not None and _ledger_identity(parent, name) != previous:
            raise ReadRefused('CHANGED')


def _read_ledger(parent, query, *, required_source=None):
    statement = _SELECT
    args = [query.job_id]
    if query.execution_id is not None:
        statement += ' AND id = ?'
        args.append(query.execution_id)
    if required_source is not None:
        statement += ' AND source = ?'
        args.append(required_source)
    statement += ' ORDER BY julianday(claimed_at) DESC, claimed_at DESC, id DESC LIMIT ?'
    args.append(query.limit + 1)
    with _open_read_ledger(parent) as db:
        if db is None:
            return 'absent', [], False
        rows = db.execute(statement, args).fetchall()
    return 'available', [_project(row) for row in rows[:query.limit]], len(rows) > query.limit


def read_executions(home, query, *, required_source=None):
    """Use only the native authority's current home and the exact canonical job ID."""
    if sys.platform != 'linux':
        raise ReadRefused('UNSUPPORTED')
    if required_source is not None and query.execution_id is None:
        raise ReadRefused('INVALID')
    try:
        root = Path(home) / 'cron'
        with open_owner_directory(root) as parent:
            # A saved one-shot can remove itself after completion. Its exact
            # execution ID and job ID still bind the receipt in this profile.
            original = _catalog(parent, query.job_id) if query.execution_id is None else None
            ledger, attempts, limited = _read_ledger(parent, query, required_source=required_source)
            with open_owner_directory(root) as current:
                if _disk_identity(os.fstat(parent)) != _disk_identity(os.fstat(current)):
                    raise ReadRefused('CHANGED')
                if original is not None and _catalog(current, query.job_id) != original:
                    raise ReadRefused('CHANGED')
                if ledger == 'absent' and _ledger_identity(current, _LEDGER) is not None:
                    raise ReadRefused('CHANGED')
        if query.execution_id is not None and not attempts:
            raise ReadRefused('NOT_FOUND')
        return {'profile': query.profile, 'job_id': query.job_id, 'ledger': ledger,
                'attempts': attempts, 'limited': limited}
    except (OSError, ValueError, sqlite3.Error, TypeError):
        raise ReadRefused('UNAVAILABLE') from None


def read_project_receipt(home, query, occurrence_id, required_source):
    """Read the durable decision and its optional native execution in one SQLite snapshot."""
    if sys.platform != 'linux':
        raise ReadRefused('UNSUPPORTED')
    if query.execution_id is None or not _EXECUTION_ID.fullmatch(occurrence_id):
        raise ReadRefused('INVALID')
    root = Path(home) / 'cron'
    try:
        decision = execution = None
        with open_owner_directory(root) as parent:
            with _open_read_ledger(parent) as db:
                if db is not None:
                    db.execute('BEGIN')
                    table = db.execute(
                        "SELECT 1 FROM sqlite_master WHERE type='table' AND name='signal_grove_project_decisions'"
                    ).fetchone()
                    if table is not None:
                        decision = db.execute(
                            "SELECT decision, scheduled_instant, outcome FROM signal_grove_project_decisions "
                            "WHERE job_id=? AND occurrence_id=? AND execution_id=?",
                            (query.job_id, occurrence_id, query.execution_id),
                        ).fetchone()
                        if decision is not None:
                            row = db.execute(_SELECT + " AND id=? AND source=?",
                                             (query.job_id, query.execution_id, required_source)).fetchone()
                            if row is not None:
                                execution = _project(row)
            with open_owner_directory(root) as current:
                if _disk_identity(os.fstat(parent)) != _disk_identity(os.fstat(current)):
                    raise ReadRefused('CHANGED')
        if decision is None:
            raise ReadRefused('NOT_FOUND')
        selected = _checked_timestamp(decision['scheduled_instant'])
        choice, outcome = decision['decision'], decision['outcome']
        if (choice not in ('approve', 'deny')
                or (choice == 'deny' and outcome != 'cancelled')
                or (choice == 'approve' and outcome not in (None, 'completed', 'failed', 'cancelled', 'unknown'))
                or execution is not None and (execution['scheduled_instant'] != selected
                    or outcome != (None if execution['status'] in ('claimed', 'running') else execution['status']))):
            raise ReadRefused('CHANGED')
        return {'decision': choice, 'scheduled_instant': selected,
                'execution_id': query.execution_id, 'outcome': outcome, 'execution': execution}
    except (OSError, ValueError, sqlite3.Error, TypeError):
        raise ReadRefused('UNAVAILABLE') from None
