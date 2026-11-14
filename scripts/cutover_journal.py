"""Exclusive durable cutover evidence; this component never executes a cutover.

The future driver owns approval, reviewed plans, process/work fences and effect
verification. A hash records evidence identity; it is not proof or authorization.
Unknown/unfinished operations forbid a new operation. There is intentionally no
resume/replay/repair/reset API. Parent ancestry is a separate deployment gate;
the supplied private directory and every named inode are checked before writes.
"""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import re
import stat
import threading


MAX_BYTES = 1024 * 1024
MAX_RECORDS = 512
FORWARD_STEPS = frozenset({'preflight', 'backup_pre', 'pm_old', 'pm_new',
    'gateway_drain', 'serve_retirement', 'stop_old', 'install_reviewed',
    'start_new', 'verify_new', 'backup_post', 'stop_gateway', 'stop_serve',
    'secure_modes', 'publish_reviewed', 'install_policy', 'bind_gateway',
    'bind_serve', 'bind_cli', 'reload_units', 'start_gateway', 'start_serve'})
RECOVERY_STEPS = frozenset({'recovery_preflight', 'recovery_backup',
    'rollback_gateway_drain', 'rollback_serve_retirement',
    'rollback_stop_gateway', 'rollback_stop_serve', 'rollback_policy',
    'rollback_runtime', 'rollback_reload', 'rollback_start_gateway',
    'rollback_start_serve', 'verify_rollback', 'recovery_backup_post'})
STEPS = FORWARD_STEPS | RECOVERY_STEPS
HASH = re.compile(r'[a-f0-9]{64}')
OPERATION = re.compile(r'[a-f0-9]{32}')


class Refused(RuntimeError):
    """No journal authority established; no action may follow."""


class Unknown(RuntimeError):
    """Preserve and inspect the existing operation; never replay it."""


def _unique(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('Duplicate journal key')
        result[key] = value
    return result


def inspect_bytes(data):
    """Validate a bounded closed-schema history without writing or dispatching.

    Returned state describes journal records, not actual external effects.
    Inventory/history callers must reconcile effects against native evidence.
    """
    if type(data) is not bytes or len(data) > MAX_BYTES or (data and not data.endswith(b'\n')):
        raise Unknown('Journal bytes are incomplete or outside bounds')
    lines = data.split(b'\n')[:-1] if data else []
    if len(lines) > MAX_RECORDS or any(not line or len(line) > 1024 for line in lines):
        raise Unknown('Journal record bounds are invalid')
    records = []
    state, operation, plan, pending = 'empty', None, None, None
    mode, root_operation, root_plan, recovery_origin = None, None, None, None
    authorization, root_authorization = None, None
    prefix = hashlib.sha256()
    seen, steps = set(), set()
    try:
        for seq, line in enumerate(lines, 1):
            row = json.loads(line.decode('utf8'), object_pairs_hook=_unique)
            if type(row) is not dict:
                raise ValueError()
            kind = row.get('kind')
            keys = {'schema', 'seq', 'operation', 'plan_sha256', 'kind'}
            if kind in ('intent', 'verified'):
                keys.add('step')
            if kind in ('verified', 'complete'):
                keys.add('evidence_sha256')
            if kind == 'recovery_begin':
                keys.update(('original_operation', 'original_plan_sha256', 'history_sha256', 'evidence_sha256'))
            if kind in ('begin', 'recovery_begin') and 'authorization_sha256' in row:
                keys.add('authorization_sha256')
            if (set(row) != keys or type(row['schema']) is not int or row['schema'] != 1
                    or type(row['seq']) is not int or row['seq'] != seq
                    or type(row['operation']) is not str or not OPERATION.fullmatch(row['operation'])
                    or type(row['plan_sha256']) is not str or not HASH.fullmatch(row['plan_sha256'])):
                raise ValueError()
            if 'evidence_sha256' in row and (type(row['evidence_sha256']) is not str
                    or not HASH.fullmatch(row['evidence_sha256'])):
                raise ValueError()
            if 'authorization_sha256' in row and (type(row['authorization_sha256']) is not str
                    or not HASH.fullmatch(row['authorization_sha256'])):
                raise ValueError()
            if kind == 'begin':
                if state not in ('empty', 'complete') or row['operation'] in seen:
                    raise ValueError()
                operation, plan = row['operation'], row['plan_sha256']
                seen.add(operation); steps = set(); state = 'active'; pending = None
                mode = 'forward'; root_operation, root_plan = operation, plan
                recovery_origin = None
                authorization = row.get('authorization_sha256'); root_authorization = authorization
            elif kind == 'recovery_begin':
                if (state not in ('active', 'pending', 'unknown') or row['operation'] in seen
                        or row['original_operation'] != operation or row['original_plan_sha256'] != plan
                        or row['history_sha256'] != prefix.hexdigest()):
                    raise ValueError()
                recovery_origin = dict(operation=operation, plan_sha256=plan, history_sha256=prefix.hexdigest())
                operation, plan = row['operation'], row['plan_sha256']
                seen.add(operation); steps = set(); state = 'active'; pending = None; mode = 'recovery'
                authorization = row.get('authorization_sha256')
            else:
                if row['operation'] != operation or row['plan_sha256'] != plan:
                    raise ValueError()
                if kind == 'intent':
                    step = row['step']
                    allowed = RECOVERY_STEPS if mode == 'recovery' else FORWARD_STEPS
                    if state != 'active' or type(step) is not str or step not in allowed or step in steps:
                        raise ValueError()
                    steps.add(step); pending = step; state = 'pending'
                elif kind == 'verified':
                    if state != 'pending' or row['step'] != pending:
                        raise ValueError()
                    pending = None; state = 'active'
                elif kind == 'unknown':
                    if state not in ('active', 'pending'):
                        raise ValueError()
                    state = 'unknown'
                elif kind == 'complete':
                    if state != 'active' or not steps:
                        raise ValueError()
                    state = 'complete'
                else:
                    raise ValueError()
            records.append(row)
            prefix.update(line + b'\n')
    except (ValueError, TypeError, KeyError, UnicodeError, RecursionError):
        raise Unknown('Journal schema/order is unproven; preserve it without repair') from None
    return dict(state=state, operation=operation, plan_sha256=plan,
                pending=pending, mode=mode, root_operation=root_operation, root_plan_sha256=root_plan,
                authorization_sha256=authorization, root_authorization_sha256=root_authorization,
                recovery_origin=recovery_origin, records=records,
                recovery_required=state not in ('empty', 'complete'))


class CutoverJournal:
    """POSIX-only flock + append/fsync under one owner-private directory.

    Opening/closing does not grant any external effect. Call `intent` durably
    before an action and `verified` only after actual effect verification. Any
    error/interrupt poisons this instance. Reopening exposes unfinished state
    but never resumes it. Only explicit fresh begin after a complete history is
    accepted. No files, facts, databases or lock names are deleted or replaced.
    """

    def __init__(self, directory, *, expected_identity):
        if os.name != 'posix':
            raise Refused('Native POSIX journal is required')
        import fcntl
        self._fcntl = fcntl
        self._fds = []
        self._closed = False
        self._poisoned = False
        self._owns_operation = False
        self._owner_pid = os.getpid()
        self._dispatch_claims = set()
        self._claim_lock = threading.Lock()
        if (type(expected_identity) is not tuple or len(expected_identity) != 2
                or any(type(n) is not int for n in expected_identity)
                or expected_identity[0] < 0 or expected_identity[1] <= 0):
            raise Refused('A previously observed native directory identity is required')
        self._directory = Path(directory)
        if (not self._directory.is_absolute() or self._directory.resolve() != self._directory
                or '..' in self._directory.parts):
            raise Refused('One normalized native directory is required')
        try:
            self._root = os.open(self._directory, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW | os.O_CLOEXEC)
            self._fds.append(self._root)
            self._root_identity = self._check(self._root, directory=True)
            if self._root_identity != expected_identity:
                raise Refused('Selected journal directory identity changed')
            self._lock = self._open('cutover.lock')
            try:
                fcntl.flock(self._lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError:
                raise Refused('Another cutover journal owner is active') from None
            self._journal = self._open('journal.jsonl')
            self._check_names()
            os.fsync(self._root)
            self._data = os.pread(self._journal, MAX_BYTES + 1, 0)
            self._view = inspect_bytes(self._data)
        except BaseException as exc:
            self.close()
            if isinstance(exc, (Refused, Unknown)) or not isinstance(exc, Exception):
                raise
            raise Refused('Private journal could not be opened safely') from None

    @staticmethod
    def _check(fd, *, directory=False):
        info = os.fstat(fd)
        mode = 0o700 if directory else 0o600
        if (info.st_uid != os.geteuid() or stat.S_IMODE(info.st_mode) != mode
                or not (stat.S_ISDIR(info.st_mode) if directory else stat.S_ISREG(info.st_mode))
                or (not directory and info.st_nlink != 1)):
            raise Refused('Journal inode ownership, type, links or mode is unsafe')
        return info.st_dev, info.st_ino

    def _open(self, name):
        fd = os.open(name, os.O_RDWR | os.O_CREAT | os.O_APPEND | os.O_NOFOLLOW | os.O_CLOEXEC,
                     0o600, dir_fd=self._root)
        self._fds.append(fd)
        self._check(fd)
        return fd

    def _check_names(self):
        current = os.stat(self._directory, follow_symlinks=False)
        if ((current.st_dev, current.st_ino) != self._root_identity
                or self._check(self._root, directory=True) != self._root_identity):
            raise Unknown('Private journal directory identity changed')
        for name, fd in (('cutover.lock', self._lock), ('journal.jsonl', self._journal)):
            info = os.stat(name, dir_fd=self._root, follow_symlinks=False)
            if (info.st_dev, info.st_ino) != self._check(fd):
                raise Unknown('Private journal name identity changed')

    @property
    def view(self):
        # Reparse to give callers a detached copy with no mutable internal state.
        result = inspect_bytes(self._data)
        result['durability_uncertain'] = self._poisoned
        return result

    def _append(self, kind, **fields):
        if self._closed or self._poisoned or (kind not in ('begin', 'recovery_begin') and not self._owns_operation):
            raise Unknown('Journal instance is closed or needs recovery')
        try:
            row = dict(schema=1, seq=len(self._view['records']) + 1,
                       operation=self._view['operation'], plan_sha256=self._view['plan_sha256'], kind=kind)
            row.update(fields)
            encoded = (json.dumps(row, sort_keys=True, separators=(',', ':')) + '\n').encode('utf8')
            candidate = self._data + encoded
            view = inspect_bytes(candidate)
            self._check_names()
            if os.pread(self._journal, MAX_BYTES + 1, 0) != self._data:
                raise Unknown('Journal content changed outside its exclusive owner')
            remaining = memoryview(encoded)
            while remaining:
                count = os.write(self._journal, remaining)
                if type(count) is not int or count <= 0 or count > len(remaining):
                    raise OSError('Invalid journal write count')
                remaining = remaining[count:]
            os.fsync(self._journal)
            self._check_names()
        except BaseException as exc:
            self._poisoned = True
            if not isinstance(exc, Exception):
                raise
            raise Unknown('Journal durability or identity is unproven; preserve without replay') from None
        self._data, self._view = candidate, view
        if kind in ('begin', 'recovery_begin'):
            self._owns_operation = True
        elif kind in ('unknown', 'complete'):
            self._owns_operation = False

    def begin(self, operation, plan_sha256, *, authorization_sha256=None):
        if self._view['recovery_required']:
            raise Unknown('An unfinished cutover must be reconciled before new work')
        # Begin uses a new operation/plan in the record, never mutates prior rows.
        fields = {} if authorization_sha256 is None else dict(authorization_sha256=authorization_sha256)
        self._append('begin', operation=operation, plan_sha256=plan_sha256, **fields)

    def intent(self, step):
        self._append('intent', step=step)

    @property
    def history_sha256(self):
        return hashlib.sha256(self._data).hexdigest()

    def assert_current(self):
        """Recheck the held names/content; a snapshot alone grants no dispatch."""
        if self._closed or self._poisoned:
            raise Unknown('Journal instance is closed or needs recovery')
        try:
            self._check_names()
            if os.pread(self._journal, MAX_BYTES + 1, 0) != self._data:
                raise Unknown('Journal content changed')
        except BaseException as exc:
            self._poisoned = True
            if not isinstance(exc, Exception):
                raise
            raise Unknown('Journal ownership or history is unproven') from None

    def claim_dispatch(self, operation, step, authorization_sha256):
        """Spend one pending intent, only on its original live journal owner.

        The durable intent exists before this in-memory claim. Opening unfinished
        bytes never owns that operation; a replacement adapter sharing this exact
        live journal cannot reclaim the step either. A crash/closed/reopened/forked
        owner permits inspection or separately approved recovery, never replay.
        Claim before any native effect; failure after claiming does not release it.
        This method does not issue approval or verify a service/work preimage.
        """
        if not self._claim_lock.acquire(False):
            raise Refused('Another native dispatch claim is active')
        try:
            self.assert_current()
            if (os.getpid()!=self._owner_pid or not self._owns_operation
                    or self._view['state']!='pending' or operation!=self._view['operation']
                    or step!=self._view['pending'] or type(authorization_sha256) is not str
                    or not HASH.fullmatch(authorization_sha256)
                    or authorization_sha256!=self._view['authorization_sha256']):
                raise Refused('Only the original live owner may claim its exact authorized pending intent')
            key=(operation,step)
            if key in self._dispatch_claims:
                raise Refused('This native dispatch intent has already been spent')
            self._dispatch_claims.add(key)
        finally:
            self._claim_lock.release()

    def begin_recovery(self, operation, plan_sha256, *, original_operation,
                       original_plan_sha256, history_sha256, evidence_sha256, authorization_sha256=None):
        """Record a separate explicitly reviewed recovery, never resume old steps.

        The coordinator must verify fresh native effects and matching approval
        before calling. This ledger does not validate or manufacture approval.
        """
        fields = {} if authorization_sha256 is None else dict(authorization_sha256=authorization_sha256)
        self._append('recovery_begin', operation=operation, plan_sha256=plan_sha256,
                     original_operation=original_operation, original_plan_sha256=original_plan_sha256,
                     history_sha256=history_sha256, evidence_sha256=evidence_sha256, **fields)

    def verified(self, step, evidence_sha256):
        self._append('verified', step=step, evidence_sha256=evidence_sha256)

    def unknown(self):
        self._append('unknown')

    def complete(self, evidence_sha256):
        self._append('complete', evidence_sha256=evidence_sha256)

    def close(self):
        self._closed = True
        for fd in reversed(self._fds):
            os.close(fd)
        self._fds.clear()

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.close()
