"""Guarded optional extension of existing native authority handlers; preview only.

This prepares source, not operator policy, credentials, listeners or restarts.
Deployment requires the owner's existing reviewed recovery/idle procedure.
"""
import argparse
import ast
from functools import partial
import hashlib
import os
from pathlib import Path
import stat
import textwrap

WORKSPACE_FILES = ('__init__.py', 'coordinator.py', 'ssh_transport.py', 'native_workspace_adapter.py',
                   'operator_policy.py', 'policy_file.py', 'attached.py')
FILES = WORKSPACE_FILES + ('native_adapter.py', 'cron_history.py', 'cron_output.py', 'cron_edit.py',
                           'cron_run_once.py', 'cron_run_once_approval.py', 'cron_run_once_operator.py',
                           'cron_project_holds.py', 'cron_project_decisions.py', 'reviewed_worker.py',
                           'reviewed_ssh.py')


def patch_source(source, *, workspace_reads_only=False):
    known_imports = (
        'from hermes_state_runtime import RuntimeStoreError\n',
        'from persistence.core.errors import STORE_FAILURES, AuthorityRefusal, PersistenceError\n',
    )
    anchors = [line for line in known_imports if source.count(line) == 1]
    if len(anchors) != 1 or sum(source.count(line) for line in known_imports) != 1:
        raise ValueError('Native runtime differs from reviewed anchors; no change.')
    imported = anchors[0]
    adapter = 'native_workspace_adapter' if workspace_reads_only else 'native_adapter'
    edits = (
        (imported, imported +
         f'from gateway.signal_grove_workspaces.{adapter} import handlers as worker_workspace_handlers\n'),
        ('        handlers.update(stored_read_handlers(self))\n        return handlers\n',
         '        handlers.update(stored_read_handlers(self))\n'
         '        handlers.update(worker_workspace_handlers(self))\n        return handlers\n'))
    for before, after in edits:
        if source.count(before) != 1: raise ValueError('Native runtime differs from reviewed anchors; no change.')
        source = source.replace(before, after)
    ast.parse(source)
    return source


def patch_scheduler_output(source):
    anchor = '''            else save_job_output(job["id"], output))
    if verbose and output_file is not None:
'''
    if source.count(anchor) != 1:
        raise ValueError('Scheduler source differs from the reviewed output fence.')
    updated = source.replace(anchor, '''            else save_job_output(job["id"], output))
        if output_file is not None and job.get("execution_id"):
            from gateway.signal_grove_workspaces.cron_output import bind_output
            bind_output(job["id"], job["execution_id"], output_file, output)
    if verbose and output_file is not None:
''')
    ast.parse(updated)
    return updated


def patch_cron_edit(source):
    start = source.index("@contextlib.contextmanager\ndef _jobs_lock():")
    end = source.index("@contextlib.contextmanager\ndef _fire_job_lock", start)
    section = source[start:end]
    lock_edits = (
        ("@contextlib.contextmanager\ndef _jobs_lock():",
         "class CronStoreLockUnavailable(RuntimeError):\n    pass\n\n\n"
         "@contextlib.contextmanager\ndef _jobs_lock(*, require_cross_process=False):"),
        ("    if depth:\n        _jobs_lock_state.depth",
         "    if depth:\n        if require_cross_process:\n            raise CronStoreLockUnavailable()\n"
         "        _jobs_lock_state.depth"),
        ("        lock_fd = None\n        try:", "        lock_fd = None\n        acquired = None\n        try:"),
        ("                if _acquire_flock(lock_fd, _JOBS_LOCK_TIMEOUT_SECONDS) is False:\n                    logger.error(",
         "                acquired = _acquire_flock(lock_fd, _JOBS_LOCK_TIMEOUT_SECONDS)\n"
         "                if acquired is False and not require_cross_process:\n                    logger.error("),
        ("            except OSError as e:\n", "            except OSError as e:\n"
         "                if require_cross_process:\n                    acquired = None\n"
         "                    if lock_fd is not None:\n                        lock_fd.close()\n                        lock_fd = None\n"
         "                    raise CronStoreLockUnavailable() from None\n"),
        ("            try:\n                yield\n", "            try:\n"
         "                if require_cross_process and acquired is not True:\n                    raise CronStoreLockUnavailable()\n"
         "                yield\n"),
    )
    for before, after in lock_edits:
        if section.count(before) != 1:
            raise ValueError('Native lock differs from the reviewed schedule edit anchors.')
        section = section.replace(before, after)
    source = source[:start] + section + source[end:]
    start = source.index("def update_job(job_id: str, updates: dict[str, Any])")
    end = source.index("\n\ndef pause_job", start)
    section = source[start:end]
    apply_start = "    def apply(jobs, i, job):\n"
    write_start = "        jobs[i] = updated\n        save_jobs(jobs)"
    if section.count(apply_start) != 1 or section.count(write_start) != 1:
        raise ValueError('Native schedule writer differs from the reviewed edit anchors.')
    projection_start = section.index(apply_start) + len(apply_start)
    projection_end = section.index(write_start, projection_start)
    projection = textwrap.dedent(section[projection_start:projection_end]).rstrip()
    if (not projection.startswith("_rederive_repeat_for_schedule_change(job, updates)\n")
            or not projection.endswith("_reject_terminal_activation(job, updated, job_id)")):
        raise ValueError('Native schedule projection differs from the reviewed edit anchors.')
    section = (section[:projection_start]
               + "        updated = _project_job_update(job_id, job, updates)\n"
               + "        if finalize_review is not None:\n            finalize_review(updated)\n"
               + section[projection_end:])
    section = section.replace("def update_job(job_id: str, updates: dict[str, Any])",
                              "def update_job(job_id: str, updates: dict[str, Any], *, finalize_review=None)")
    section = ("def _project_job_update(job_id, job, updates):\n"
               + textwrap.indent(projection, "    ") + "\n    return updated\n\n\n" + section)
    result = source[:start] + section + source[end:]
    ast.parse(result)
    return result


def patch_cron_reviewed_run_once(source):
    """Stage the reviewed claim using the existing native job and fire locks."""
    source = patch_cron_edit(source)
    patches = (
        ('def mark_job_run(\n', """def _reviewed_run_once_id(job):
    claim = job.get("fire_claim")
    return claim.get("signal_grove_run_once") if isinstance(claim, dict) else None


def mark_job_run(
"""),
        ('        extra = extra_fire.is_extra(job)  # read before the outcome clears the claim\n'
         '        held = extra_fire.held_markers(job) if extra else {}\n',
         """        reviewed_run = _reviewed_run_once_id(job)
        if reviewed_run is not None and (expected_fire_owner is None
                or execution_id not in (None, reviewed_run)):
            return False
        extra = extra_fire.is_extra(job)  # read before the outcome clears the claim
        held = extra_fire.held_markers(job) if extra or reviewed_run is not None else {}
        if reviewed_run is not None:
            held.update({key: job[key] for key in ("pending_slot", "run_claim") if key in job})
"""),
        ('        if extra:  # a manual run: repeat budget and pending occurrence untouched\n',
         """        if reviewed_run is not None:
            # The manual result must not spend repeat, move the scheduled
            # occurrence or set automatic retry/quota scheduling for the job.
            save_jobs(jobs)
            return True
        if extra:  # a manual run: repeat budget and pending occurrence untouched
"""),
        ('        if extra_fire.is_extra(job):  # a manual run leaves the scheduled fire\'s budget alone\n',
         '        if _reviewed_run_once_id(job) is not None or extra_fire.is_extra(job):  # manual budget\n'),
        ('    now = scan.now\n    # Cross-process guard: another process\'s live one-shot run_claim',
         '''    now = scan.now
    if _reviewed_run_once_id(job) is not None and _claim_is_live(
            job["fire_claim"], now, FIRE_CLAIM_TTL_SECONDS):
        return False
    # Cross-process guard: another process's live one-shot run_claim'''),
        ('def heartbeat_fire_claim(job_id: str, *, expected_owner: str) -> bool:\n',
         """def claim_reviewed_run_once(job_id: str, *, execution_id: str, authorize):
    \"\"\"Claim one manual execution after checking its approval under the native fences.\"\"\"
    from cron import executions

    if (type(job_id) is not str or job_id == "unknown"
            or re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.:-]{0,255}", job_id) is None
            or type(execution_id) is not str or re.fullmatch(r"[a-f0-9]{32}", execution_id) is None
            or not callable(authorize)):
        raise ValueError("Invalid reviewed manual claim.")

    def verify_store():
        directory = get_hermes_home().resolve() / "cron"
        store = _current_cron_store()
        ledger = directory / "executions.db"
        if (executions.EXECUTIONS_FILE is not None or store.cron_dir.resolve() != directory
                or store.jobs_file.resolve() != directory / "jobs.json" or ledger.resolve() != ledger):
            raise ValueError("Reviewed job and execution stores have different homes.")

    verify_store()

    def apply(jobs, _index, job):
        paused = job.get("state") == "paused" and job.get("enabled") is False
        if (is_terminal_job(job) or not (paused or is_job_runnable(job))
                or job.get("fire_claim") is not None or job.get("run_claim") is not None
                or any(job.get(key) is not None
                       for key in ("manual_run_at", "manual_run_restore_at", "manual_run_prompt"))):
            return False
        if authorize(copy.deepcopy(job)) is not True:
            return False
        verify_store()
        now = _hermes_now()
        owner = f"{_machine_id()}:{uuid.uuid4().hex}"
        # Reserve under the protected locks before changing the job. A failed
        # job save leaves an original receipt to reconcile, rather than replay.
        executions.create_execution(job_id, source="signal-grove-run-once-v1", reviewed_id=execution_id)
        job["fire_claim"] = {"at": now.isoformat(), "by": owner, "signal_grove_run_once": execution_id}
        save_jobs(jobs)
        return dict(copy.deepcopy(job), _scheduled_instant=None, execution_id=execution_id)

    def locked():
        with _jobs_lock(require_cross_process=True):
            # Observe the exact record without stock load_jobs repairing state
            # before approval. Keep the native save's pre-read merge stamp.
            stamp = _jobs_file_stamp(_current_cron_store().jobs_file)
            rows = _peek_jobs_unlocked()
            if rows is None or any(type(row) is not dict for row in rows):
                raise ValueError("Invalid reviewed manual job store.")
            matches = [(index, row) for index, row in enumerate(rows) if row.get("id") == job_id]
            if len(matches) > 1:
                raise ValueError("Ambiguous reviewed manual job.")
            if not matches:
                return False
            _record_load_stamp(stamp)
            index, job = matches[0]
            return apply(rows, index, job)

    return _under_fire_fence(job_id, locked)


def heartbeat_fire_claim(job_id: str, *, expected_owner: str) -> bool:
"""),
    )
    for before, after in patches:
        if source.count(before) != 1:
            raise ValueError("Native jobs source differs from the reviewed manual claim anchors.")
        source = source.replace(before, after)
    ast.parse(source)
    return source


def _guard_entry(source, function, before, inserted):
    """Add a guard inside one exact reviewed function, before a known statement."""
    matches = [node for node in ast.parse(source).body if isinstance(node, ast.FunctionDef) and node.name == function]
    if len(matches) != 1:
        raise ValueError(f"Expected one reviewed function for {function}.")
    node = matches[0]
    lines = source.splitlines(keepends=True)
    section = "".join(lines[node.lineno - 1:node.end_lineno])
    if section.count(before) != 1 or inserted in section:
        raise ValueError(f"Native source differs from the reviewed gate for {function}.")
    lines[node.lineno - 1:node.end_lineno] = [section.replace(before, inserted + before)]
    return "".join(lines)


_PROJECT_JOB_DECISIONS = """def _with_project_decision_job(job_id, apply):
    from cron import executions

    if type(job_id) is not str or re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.:-]{0,255}", job_id) is None or job_id == "unknown":
        raise ValueError("Invalid reviewed project job.")
    directory = get_hermes_home().resolve() / "cron"
    store = _current_cron_store()
    ledger = directory / "executions.db"
    if (executions.EXECUTIONS_FILE is not None or store.cron_dir.resolve() != directory
            or store.jobs_file.resolve() != directory / "jobs.json" or ledger.resolve() != ledger):
        raise ValueError("Reviewed project job and execution stores have different homes.")

    def locked():
        with _jobs_lock(require_cross_process=True):
            stamp = _jobs_file_stamp(store.jobs_file)
            rows = _peek_jobs_unlocked()
            if rows is None or any(type(job) is not dict for job in rows):
                raise ValueError("Invalid reviewed project job store.")
            selected = [job for job in rows if job.get("id") == job_id]
            if len(selected) != 1:
                return False
            _record_load_stamp(stamp)
            return apply(rows, selected[0])

    return _under_fire_fence(job_id, locked)


def decide_reviewed_project_occurrence(job_id, *, occurrence_id, execution_id, instant, decision, authorize):
    from cron import executions
    from cron.occurrences import scheduled_instant

    def apply(rows, job):
        now = _hermes_now()
        if (job.get("enabled") is not True or job.get("state") != "scheduled" or _has_pause_marker(job)
                or any(job.get(key) is not None for key in ("run_claim", "fire_claim", "pending_slot",
                       "manual_run_at", "manual_run_restore_at", "manual_run_prompt"))
                or type(instant) is not str or scheduled_instant(instant) != instant
                or scheduled_instant(job.get("next_run_at")) != instant or datetime.fromisoformat(instant) > now):
            return False
        if decision not in ("approve", "deny") or authorize(copy.deepcopy(job)) is not True:
            return False

        advanced = None
        if decision == "deny" and job["schedule"].get("kind") in ("cron", "interval"):
            advanced = compute_next_run(job["schedule"], now.isoformat(), timezone=job.get("timezone"))
            if advanced is None or datetime.fromisoformat(scheduled_instant(advanced)) <= now:
                raise ValueError("Could not safely advance a denied schedule.")

        executions.record_project_decision(job_id, occurrence_id=occurrence_id, execution_id=execution_id,
                                           instant=instant, decision=decision)
        if decision == "approve":
            job["fire_claim"] = {"at": now.isoformat(), "by": f"{_machine_id()}:{uuid.uuid4().hex}",
                                 "signal_grove_project_occurrence": execution_id}
        elif advanced is not None:
            job["next_run_at"] = advanced
        else:
            job.update(enabled=False, state="paused", next_run_at=None, paused_at=now.isoformat(),
                       paused_reason="Scheduled occurrence denied.")
        save_jobs(rows)
        return dict(copy.deepcopy(job), _scheduled_instant=instant, execution_id=execution_id)

    return _with_project_decision_job(job_id, apply)


def settle_reviewed_project_occurrence(job_id, owner, execution_id, status):
    from cron import executions
    from cron.occurrences import scheduled_instant

    if status not in ("completed", "failed"):
        return False

    def apply(rows, job):
        claim = job.get("fire_claim")
        if (type(claim) is not dict or claim.get("by") != owner or
                claim.get("signal_grove_project_occurrence") != execution_id):
            return False
        decision = executions.project_decision_record(execution_id)
        if (decision is None or decision["job_id"] != job_id or decision["decision"] != "approve"
                or decision["scheduled_instant"] != scheduled_instant(job.get("next_run_at"))):
            return False
        detail = None if status == "completed" else "Reviewed project worker reported failure."
        if decision["outcome"] is None:
            executions.finish_execution(execution_id, success=status == "completed", error=detail)
            decision = executions.project_decision_record(execution_id)
        if decision is None or decision["outcome"] != status:
            return False
        now = _hermes_now().isoformat()
        _record_run_outcome(job, status == "completed", detail, None, None, now)
        _advance_after_run(job, now)
        save_jobs(rows)
        return True

    return _with_project_decision_job(job_id, apply)


"""


def patch_cron_project_jobs(source):
    source = patch_cron_reviewed_run_once(source)
    anchor = "def _project_job_update(job_id, job, updates):\n"
    if source.count(anchor) != 1:
        raise ValueError("Native schedule projection differs from the reviewed project gate.")
    source = source.replace(anchor, """def _project_cron_automatic_jobs():
    from gateway.signal_grove_workspaces.cron_project_holds import automatic_jobs

    store = _current_cron_store()
    return automatic_jobs(get_hermes_home(), store.cron_dir, store.jobs_file)


def _ordinary_cron_allowed(selected, *, automatic=None, stored=None):
    from gateway.signal_grove_workspaces.cron_project_holds import matches_automatic

    policy = _project_cron_automatic_jobs() if automatic is None else automatic
    job_id = selected.get("id") if type(selected) is dict else selected
    if type(job_id) is not str or job_id not in policy:
        return False
    if stored is None:
        rows = _peek_jobs_unlocked()
        if rows is None or any(type(row) is not dict for row in rows):
            return False
        matches = [row for row in rows if row.get("id") == job_id]
        if len(matches) != 1:
            return False
        stored = matches[0]
    return (matches_automatic(policy, stored)
            and (type(selected) is not dict or matches_automatic(policy, selected)))


""" + anchor + """    if any(updates.get(key) is not None for key in ("manual_run_at", "manual_run_restore_at", "manual_run_prompt")):
        raise ValueError("This job requires scoped scheduler approval.")
""")
    source = _guard_entry(source, "_get_due_jobs_locked", "    raw_jobs = load_jobs()\n",
                         """    automatic = _project_cron_automatic_jobs()
    if not automatic:
        return []
""")
    source = _guard_entry(source, "_get_due_jobs_locked", "            if _evaluate_due_job(job, scan, run_claim_ttl):\n",
                         """            if not _ordinary_cron_allowed(job, automatic=automatic, stored=scan.find(job.get("id"))):
                continue
""")
    for function, statement in (
        ("claim_job_for_fire", "        if is_terminal_job(job) and not _is_recoverable_error_job(job):\n"),
        ("claim_reviewed_run_once", '        paused = job.get("state") == "paused" and job.get("enabled") is False\n'),
        ("claim_dispatch", '        repeat = job.get("repeat") or {}\n'),
    ):
        source = _guard_entry(source, function, statement, """        if not _ordinary_cron_allowed(job_id, stored=job):
            return False
""")
    source = _guard_entry(source, "claim_dispatch", "    claimed = _with_job(job_id, apply, missing=_MISSING)\n",
                         """    if not _ordinary_cron_allowed(job_id):
        return False
""")
    source = _guard_entry(source, "mark_job_run", "        if execution_id is not None and execution_id in job.get(",
                         """        claim = job.get("fire_claim")
        if isinstance(claim, dict) and "signal_grove_project_occurrence" in claim:
            return False
""")
    anchor = "def heartbeat_fire_claim(job_id: str, *, expected_owner: str) -> bool:\n"
    if source.count(anchor) != 1:
        raise ValueError("Native jobs source differs from the reviewed project claim anchor.")
    source = source.replace(anchor, _PROJECT_JOB_DECISIONS + anchor)
    ast.parse(source)
    return source


def patch_cron_project_scheduler(source):
    source = patch_scheduler_output(source)
    source = _guard_entry(source, "run_one_job",
                         "    # Every gateway path (built-in scheduler, external providers, and direct\n",
                         """    from cron.jobs import _ordinary_cron_allowed

    if extra_prompt is not None or not _ordinary_cron_allowed(job):
        return False

""")
    source = _guard_entry(source, "run_job", "    from gateway.session_cron import current_execution\n",
                         """    from cron.jobs import _ordinary_cron_allowed

    if extra_prompt is not None or not _ordinary_cron_allowed(job):
        return False, "", "", "This job requires scoped scheduler approval."

""")
    ast.parse(source)
    return source


def patch_cron_project_provider(source):
    # CronScheduler.claim_fire reserves a row before its ordinary job CAS.
    # Refuse held jobs before reservation, then the normal gate checks again under the lock.
    imported = "        from cron.jobs import claim_job_for_fire\n"
    reservation = "        execution = create_execution(job_id, source=self.name)\n"
    profile_gate = '''        from hermes_cli.profiles_run_elsewhere import run_elsewhere_for_home
        from hermes_constants import get_hermes_home

        if run_elsewhere_for_home(get_hermes_home()) is not None:
            return None
'''
    anchors = [imported + ownership + "\n" for ownership in ("", profile_gate)]
    matches = [(anchor, source.count(anchor + reservation)) for anchor in anchors]
    if sum(count for _, count in matches) != 1:
        raise ValueError("Native provider differs from the reviewed project gate.")
    anchor = next(anchor for anchor, count in matches if count)
    gate = '''        from cron.jobs import _ordinary_cron_allowed
        if not _ordinary_cron_allowed(job_id):
            return None
'''
    updated = source.replace(anchor + reservation, anchor + gate + reservation)
    ast.parse(updated)
    return updated


def patch_cron_execution_reservation(source):
    start = source.index('def create_execution(\n')
    end = source.index('\ndef set_execution_occurrence(', start)
    section = source[start:end]
    signature = '    job_id: str, *, source: str, scheduled_instant: Optional[str] = None,\n'
    identity = '    execution_id = uuid.uuid4().hex\n'
    if section.count(signature) != 1 or section.count(identity) != 1:
        raise ValueError('Native execution admission differs from reviewed anchors.')
    section = section.replace(signature, signature + '    reviewed_id: Optional[str] = None,\n')
    section = section.replace(identity, '''    if source == "signal-grove-run-once-v1" or reviewed_id is not None:
        if (source != "signal-grove-run-once-v1" or scheduled_instant is not None
                or type(reviewed_id) is not str or len(reviewed_id) != 32
                or any(char not in "0123456789abcdef" for char in reviewed_id)):
            raise ValueError("Malformed reviewed manual execution.")
    execution_id = reviewed_id if reviewed_id is not None else uuid.uuid4().hex
''')
    updated = source[:start] + section + source[end:]
    ast.parse(updated)
    return updated


def patch_cron_project_executions(source):
    source = patch_cron_execution_reservation(source)
    opening = "    _widen_status_check(conn)\n"
    if source.count(opening) != 1:
        raise ValueError("Native ledger schema differs from the reviewed project decision anchor.")
    source = source.replace(opening, opening + """    conn.execute('''
        CREATE TABLE IF NOT EXISTS signal_grove_project_decisions (
            execution_id TEXT PRIMARY KEY, occurrence_id TEXT NOT NULL UNIQUE,
            job_id TEXT NOT NULL, scheduled_instant TEXT NOT NULL,
            decision TEXT NOT NULL CHECK(decision IN ('approve','deny')),
            outcome TEXT CHECK(outcome IN ('completed','failed','cancelled','unknown')),
            UNIQUE(job_id, scheduled_instant)
        )
    ''')
    conn.execute('''
        CREATE TRIGGER IF NOT EXISTS signal_grove_project_outcome
        AFTER UPDATE OF status ON executions
        WHEN NEW.source = 'signal-grove-project-occurrence-v1'
            AND NEW.status IN ('completed','failed','cancelled','unknown')
        BEGIN
            UPDATE signal_grove_project_decisions SET outcome=NEW.status WHERE execution_id=NEW.id;
        END
    ''')
""")
    anchor = "def set_execution_occurrence(execution_id: str, instant: Optional[str]) -> None:\n"
    if source.count(anchor) != 1:
        raise ValueError("Native executions source differs from the reviewed project reservation anchor.")
    source = source.replace(anchor, """def project_decision_record(execution_id):
    with _transaction() as conn:
        row = conn.execute("SELECT * FROM signal_grove_project_decisions WHERE execution_id=?",
                           (execution_id,)).fetchone()
        return dict(row) if row is not None else None


def mark_project_execution_unknown(execution_id):
    with _transaction() as conn:
        cursor = conn.execute(
            "UPDATE executions SET status='unknown', finished_at=?, error=?, handoff_pending=0, handoff_started_at=NULL "
            "WHERE id=? AND source='signal-grove-project-occurrence-v1' AND status IN ('claimed','running') "
            "AND process_id=? AND pid=?",
            (_hermes_now().isoformat(), "Scoped worker outcome was not verified.", execution_id, _PROCESS_ID, os.getpid()),
        )
        record = _fetch(conn, execution_id) if cursor.rowcount == 1 else None
        if record is not None:
            _prune_unlocked(conn)
    if record is not None:
        _emit_execution_state(record)


def record_project_decision(job_id, *, occurrence_id, execution_id, instant, decision):
    from cron.occurrences import scheduled_instant

    if (type(job_id) is not str or not job_id
            or any(type(value) is not str or len(value) != 32 or any(char not in "0123456789abcdef" for char in value)
                   for value in (occurrence_id, execution_id))
            or type(instant) is not str or scheduled_instant(instant) != instant or decision not in ("approve", "deny")):
        raise ValueError("Invalid reviewed project decision.")
    now = _hermes_now().isoformat()
    denied = decision == "deny"
    status = "cancelled" if denied else "claimed"
    pid = os.getpid()
    with _transaction() as conn:
        conn.execute(
            "INSERT INTO signal_grove_project_decisions "
            "(execution_id, occurrence_id, job_id, scheduled_instant, decision, outcome) VALUES (?, ?, ?, ?, ?, ?)",
            (execution_id, occurrence_id, job_id, instant, decision, status if denied else None),
        )
        conn.execute(
            "INSERT INTO executions (id, job_id, source, process_id, pid, process_started_at, "
            "status, claimed_at, scheduled_instant, finished_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (execution_id, job_id, "signal-grove-project-occurrence-v1", _PROCESS_ID, pid,
             _process_start_time(pid), status, now, instant, now if denied else None),
        )
        record = _fetch(conn, execution_id)
        if denied:
            _prune_unlocked(conn)
    _emit_execution_state(record)
    return record


""" + anchor)
    ast.parse(source)
    return source


def prepare(path, expected_hash, patch):
    raw = path.read_bytes()
    if hashlib.sha256(raw).hexdigest() != expected_hash:
        raise ValueError('Native source hash changed.')
    newline = '\r\n' if b'\r\n' in raw else '\n'
    if newline == '\r\n' and raw.count(b'\r\n') != raw.count(b'\n'):
        raise ValueError('Mixed source newlines require review.')
    return patch(raw.decode('utf8').replace('\r\n', '\n')).replace('\n', newline).encode('utf8')


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--runtime-root',type=Path,required=True)
    parser.add_argument('--expected-controls-sha256',required=True)
    parser.add_argument('--workspace-reads-only', action='store_true',
                        help='Stage only the four registered workspace-read methods.')
    parser.add_argument('--expected-scheduler-sha256', help='Also bind owned output in this reviewed runtime generation.')
    parser.add_argument('--expected-cron-jobs-sha256', help='Also stage the strict paused scheduler edit hook.')
    parser.add_argument('--expected-cron-executions-sha256',
                        help='Also stage the reviewed native manual execution ID hook.')
    parser.add_argument('--hold-project-schedules', action='store_true',
                        help='Require protected schedule policy across reviewed stock dispatch paths.')
    parser.add_argument('--expected-cron-provider-sha256',
                        help='Required native provider hash when holding project schedules.')
    parser.add_argument('--apply',action='store_true')
    args=parser.parse_args()
    if args.workspace_reads_only and any((args.expected_scheduler_sha256, args.expected_cron_jobs_sha256,
            args.expected_cron_executions_sha256, args.expected_cron_provider_sha256,
            args.hold_project_schedules)):
        parser.error('Workspace reads only cannot stage scheduling hooks.')
    if args.hold_project_schedules and not all((args.expected_scheduler_sha256, args.expected_cron_jobs_sha256,
            args.expected_cron_executions_sha256, args.expected_cron_provider_sha256)):
        parser.error('Holding project schedules requires verified scheduler, jobs, executions and provider hashes.')
    if args.expected_cron_provider_sha256 and not args.hold_project_schedules:
        parser.error('The provider hook requires --hold-project-schedules.')
    source=args.runtime_root/'gateway/session_controls.py'
    controls_patch = partial(patch_source, workspace_reads_only=args.workspace_reads_only)
    updates = [(source, args.expected_controls_sha256, prepare(source, args.expected_controls_sha256, controls_patch))]
    if args.expected_scheduler_sha256 is not None:
        scheduler = args.runtime_root / 'cron/scheduler.py'
        scheduler_patch = patch_cron_project_scheduler if args.hold_project_schedules else patch_scheduler_output
        updates.append((scheduler, args.expected_scheduler_sha256,
                        prepare(scheduler, args.expected_scheduler_sha256, scheduler_patch)))
    if args.expected_cron_jobs_sha256 is not None:
        jobs = args.runtime_root / 'cron/jobs.py'
        jobs_patch = (patch_cron_reviewed_run_once if args.expected_cron_executions_sha256 is not None
                      else patch_cron_edit)
        if args.hold_project_schedules:
            jobs_patch = patch_cron_project_jobs
        updates.append((jobs, args.expected_cron_jobs_sha256,
                        prepare(jobs, args.expected_cron_jobs_sha256, jobs_patch)))
    if args.expected_cron_executions_sha256 is not None:
        executions = args.runtime_root / 'cron/executions.py'
        expected = args.expected_cron_executions_sha256
        execution_patch = patch_cron_project_executions if args.hold_project_schedules else patch_cron_execution_reservation
        updates.append((executions, expected, prepare(executions, expected, execution_patch)))
    if args.hold_project_schedules:
        provider = args.runtime_root / 'cron/scheduler_provider.py'
        expected = args.expected_cron_provider_sha256
        updates.append((provider, expected, prepare(provider, expected, patch_cron_project_provider)))
    files = WORKSPACE_FILES if args.workspace_reads_only else FILES
    modules = {name: Path(__file__).with_name(name).read_bytes() for name in files}
    if not args.workspace_reads_only:
        shared_approval = Path(__file__).resolve().parents[1] / 'worker_workspaces' / 'approval_envelope.py'
        modules['approval_envelope.py'] = shared_approval.read_bytes()
    for value in modules.values(): ast.parse(value)
    target=source.parent/'signal_grove_workspaces'
    if target.exists() or target.is_symlink(): raise ValueError('Preserve existing extension; review first.')
    if args.apply:
        target.mkdir()
        # Full module set first. Existing in-memory authority remains unchanged.
        for name,value in modules.items():
            with (target/name).open('xb') as out: out.write(value);out.flush();os.fsync(out.fileno())
        staged = []
        for path, expected, updated in updates:
            temporary = path.with_name(f'.{path.stem}.signalgrove.tmp')
            with temporary.open('xb') as out:
                out.write(updated); out.flush(); os.fsync(out.fileno())
            temporary.chmod(stat.S_IMODE(path.stat().st_mode))
            staged.append((path, expected, temporary))
        for path, expected, temporary in staged:
            if hashlib.sha256(path.read_bytes()).hexdigest() != expected:
                raise ValueError('Native source changed during staging; preserve candidate for review.')
        for path, _, temporary in staged:
            os.replace(temporary, path)
    print('Applied' if args.apply else 'Preview',
          *[(path.stem.removeprefix('session_') + '_sha256=' + hashlib.sha256(updated).hexdigest())
            for path, _, updated in updates],
          'module_hashes='+','.join(name+':'+hashlib.sha256(value).hexdigest() for name,value in modules.items()))


if __name__=='__main__': main()
