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

FILES = ('__init__.py','coordinator.py','ssh_transport.py','native_adapter.py')


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


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--runtime-root',type=Path,required=True)
    parser.add_argument('--expected-controls-sha256',required=True)
    parser.add_argument('--apply',action='store_true')
    args=parser.parse_args()
    source=args.runtime_root/'gateway/session_controls.py'
    raw=source.read_bytes()
    if hashlib.sha256(raw).hexdigest()!=args.expected_controls_sha256: raise ValueError('Native source hash changed.')
    newline='\r\n' if b'\r\n' in raw else '\n'
    if newline=='\r\n' and raw.count(b'\r\n')!=raw.count(b'\n'): raise ValueError('Mixed source newlines require review.')
    updated=patch_source(raw.decode('utf8').replace('\r\n','\n')).replace('\n',newline).encode('utf8')
    modules={name:Path(__file__).with_name(name).read_bytes() for name in FILES}
    for value in modules.values(): ast.parse(value)
    target=source.parent/'signal_grove_workspaces'
    if target.exists() or target.is_symlink(): raise ValueError('Preserve existing extension; review first.')
    if args.apply:
        target.mkdir()
        # Full module set first. Existing in-memory authority remains unchanged.
        for name,value in modules.items():
            with (target/name).open('xb') as out: out.write(value);out.flush();os.fsync(out.fileno())
        temporary=source.with_name('.session_controls.signalgrove.tmp')
        with temporary.open('xb') as out: out.write(updated);out.flush();os.fsync(out.fileno())
        temporary.chmod(stat.S_IMODE(source.stat().st_mode))
        if hashlib.sha256(source.read_bytes()).hexdigest()!=args.expected_controls_sha256:
            raise ValueError('Native source changed during staging; preserve candidate for review.')
        os.replace(temporary,source)
    print('Applied' if args.apply else 'Preview','controls_sha256='+hashlib.sha256(updated).hexdigest(),
          'module_hashes='+','.join(name+':'+hashlib.sha256(value).hexdigest() for name,value in modules.items()))


if __name__=='__main__': main()
