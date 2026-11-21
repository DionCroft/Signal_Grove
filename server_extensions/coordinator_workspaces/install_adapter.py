"""Guarded optional extension of existing native authority handlers; preview only.

This prepares source, not operator policy, credentials, listeners or restarts.
Deployment requires the owner's existing reviewed recovery/idle procedure.
"""
import argparse
import ast
import hashlib
import os
from pathlib import Path
import stat
import textwrap

FILES = ('__init__.py','coordinator.py','ssh_transport.py','native_adapter.py',
         'operator_policy.py','policy_file.py','attached.py','cron_history.py','cron_output.py','cron_edit.py',
         'cron_run_once.py')


def patch_source(source):
    edits = (
        ('from hermes_state_runtime import RuntimeStoreError\n',
         'from hermes_state_runtime import RuntimeStoreError\n'
         'from gateway.signal_grove_workspaces.native_adapter import handlers as worker_workspace_handlers\n'),
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
    parser.add_argument('--expected-scheduler-sha256', help='Also bind owned output in this reviewed runtime generation.')
    parser.add_argument('--expected-cron-jobs-sha256', help='Also stage the strict paused scheduler edit hook.')
    parser.add_argument('--expected-cron-executions-sha256',
                        help='Also stage the reviewed native manual execution ID hook.')
    parser.add_argument('--apply',action='store_true')
    args=parser.parse_args()
    source=args.runtime_root/'gateway/session_controls.py'
    updates = [(source, args.expected_controls_sha256, prepare(source, args.expected_controls_sha256, patch_source))]
    if args.expected_scheduler_sha256 is not None:
        scheduler = args.runtime_root / 'cron/scheduler.py'
        updates.append((scheduler, args.expected_scheduler_sha256,
                        prepare(scheduler, args.expected_scheduler_sha256, patch_scheduler_output)))
    if args.expected_cron_jobs_sha256 is not None:
        jobs = args.runtime_root / 'cron/jobs.py'
        updates.append((jobs, args.expected_cron_jobs_sha256,
                        prepare(jobs, args.expected_cron_jobs_sha256, patch_cron_edit)))
    if args.expected_cron_executions_sha256 is not None:
        executions = args.runtime_root / 'cron/executions.py'
        expected = args.expected_cron_executions_sha256
        updates.append((executions, expected, prepare(executions, expected, patch_cron_execution_reservation)))
    modules={name:Path(__file__).with_name(name).read_bytes() for name in FILES}
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
