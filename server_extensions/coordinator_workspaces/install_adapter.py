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

FILES = ('__init__.py','coordinator.py','ssh_transport.py','native_adapter.py',
         'operator_policy.py','policy_file.py','attached.py','cron_history.py','cron_output.py')


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
    parser.add_argument('--apply',action='store_true')
    args=parser.parse_args()
    source=args.runtime_root/'gateway/session_controls.py'
    updates = [(source, args.expected_controls_sha256, prepare(source, args.expected_controls_sha256, patch_source))]
    if args.expected_scheduler_sha256 is not None:
        scheduler = args.runtime_root / 'cron/scheduler.py'
        updates.append((scheduler, args.expected_scheduler_sha256,
                        prepare(scheduler, args.expected_scheduler_sha256, patch_scheduler_output)))
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
