"""Render a review-only Pi runtime packet; never install, run or activate it.

Inputs are deployment paths, not credentials. Source, tools, PM facts, ownership,
backup, work-drain and approval gates are separate prerequisites. This renderer
cannot apply permissions, create a grant, touch a service or restore state.
"""
from __future__ import annotations

import argparse
import json
from pathlib import PurePosixPath
import re

LAYOUT_KEYS = frozenset({'account_home', 'fleet_root', 'generation', 'source_name'})
SAFE_PATH = re.compile(r'/[A-Za-z0-9_./-]{1,2047}')
SAFE_NAME = re.compile(r'[A-Za-z0-9][A-Za-z0-9_.-]{0,95}')
COMMAND_DIRECTORY = 'commands-signal-grove-v1'
DROPIN = '100-signal-grove.conf'


def absolute(value):
    if type(value) is not str or not SAFE_PATH.fullmatch(value):
        raise ValueError('A literal absolute POSIX deployment path is required')
    path = PurePosixPath(value)
    if (str(path) != value or value.startswith('//') or '..' in path.parts
            or '.' in path.parts or len(path.parts) < 3):
        raise ValueError('Ambiguous or overly broad deployment path')
    return path


def component(value):
    if type(value) is not str or not SAFE_NAME.fullmatch(value) or value in ('.', '..'):
        raise ValueError('One ordinary path component is required')
    return value


def render(layout):
    if type(layout) is not dict or set(layout) != LAYOUT_KEYS:
        raise ValueError('Closed deployment layout required')
    account = absolute(layout['account_home'])
    fleet = absolute(layout['fleet_root'])
    if account.parent != PurePosixPath('/home') or not fleet.is_relative_to(account) or fleet == account:
        raise ValueError('A dedicated fleet root under the named Linux account is required')
    generation = component(layout['generation'])
    source_name = component(layout['source_name'])
    home = fleet / 'hermes'
    runtime = home / 'runtime-generations' / generation
    source = runtime / source_name
    commands = runtime / COMMAND_DIRECTORY
    launcher = commands / 'hermes'
    client = commands / 'hermes-client'
    serve = commands / 'hermes-serve'
    service_env = fleet / 'scripts/hermes-service.env'
    scratch = home / 'cache/scratch'
    units = account / '.config/systemd/user'
    gateway = f'''[Service]
ExecStart=
ExecStart={launcher} gateway run
ExecStop=
ExecStop=-{launcher} --run-module gateway.systemd_stop_mark
ExecStopPost=
ExecStopPost=-{launcher} --run-module gateway.cgroup_cleanup
WorkingDirectory={source}
Environment="HERMES_HOME={home}"
Environment="HERMES_RUNTIME_DIR="
Environment="PYTHONPATH="
Environment="HERMES_DISABLE_LAZY_INSTALLS=1"
Environment="TMPDIR={scratch}"
'''
    serve_unit = f'''[Service]
ExecStart=
ExecStart={serve}
ExecStop=
ExecStopPost=
WorkingDirectory={source}
Environment="HERMES_HOME={home}"
Environment="HERMES_RUNTIME_DIR="
Environment="PYTHONPATH="
Environment="HERMES_DISABLE_LAZY_INSTALLS=1"
Environment="TMPDIR={scratch}"
'''
    cli = f'''#!/bin/sh
set -eu
: "${{HERMES_HOME:={home}}}"
export HERMES_HOME
export HERMES_DISABLE_LAZY_INSTALLS=1
export PYTHONPATH={source}
export TMPDIR={scratch}
exec {launcher} "$@"
'''
    server = f'''#!/bin/bash
set -euo pipefail
# Existing private environment; no credential values are part of this packet.
source {service_env}
if [[ "${{HERMES_HOME:-}}" != '{home}' ]]; then
  echo 'Unexpected Hermes service home' >&2
  exit 78
fi
export HERMES_HOME
export HERMES_DISABLE_LAZY_INSTALLS=1
export PYTHONDONTWRITEBYTECODE=1
export PYTHONPATH={source}
export TMPDIR={scratch}
unset HERMES_RUNTIME_DIR
cd {source}
if [[ -z "${{HERMES_DASHBOARD_HOST:-}}" ]]; then
  HERMES_DASHBOARD_HOST="$(tailscale ip -4 | head -n1)"
fi
exec {launcher} serve --host "$HERMES_DASHBOARD_HOST" --port "${{HERMES_DASHBOARD_PORT:-9119}}"
'''
    artifacts = {
        f'scripts/systemd/{generation}/hermes-gateway.service.conf': gateway,
        f'scripts/systemd/{generation}/hermes-serve.service.conf': serve_unit,
        f'scripts/bin/{generation}/hermes-client': cli,
        f'scripts/bin/{generation}/hermes-serve': server,
    }
    links = {str(units / (name + '.d') / DROPIN): str(fleet / key)
             for name, key in (("hermes-gateway.service", f'scripts/systemd/{generation}/hermes-gateway.service.conf'),
                               ("hermes-serve.service", f'scripts/systemd/{generation}/hermes-serve.service.conf'))}
    return dict(schema=1, state='REVIEW_ONLY_NOT_AUTHORIZED_OR_ACTIVATED', layout=dict(layout),
                canonical_home=str(home), source=str(source), runtime=str(runtime),
                command_directory=str(commands), sdk_launcher=str(launcher),
                deployed_wrappers={str(client): cli, str(serve): server},
                source_artifacts=artifacts, proposed_dropin_links=links,
                proposed_user_command_link={str(account / '.local/bin/hermes'): str(client)},
                permission_paths_for_observation=sorted({str(parent) for path in
                    [*map(PurePosixPath, links), client, serve, launcher, account / '.local/bin/hermes',
                     *[fleet / key for key in artifacts], home / 'signal-grove-control/workspace-read-policy.json']
                    for parent in path.parents}),
                required_gates=['exact-source-and-runtime-inventory', 'exact-existing-units-and-links',
                    'protected-config-and-credential-conservation', 'supported-old-stop-launcher-pm',
                    'supported-canonical-home-pm',
                    'profile-and-operator-policy-review', 'explicit-named-permission-approval',
                    'fresh-encrypted-backup-and-supplement-verification', 'gateway-drain-and-zero-work',
                    'serve-retirement-prepare-and-commit', 'explicit-live-cutover-approval',
                    'old-unit-stop-before-reload', 'new-start-health-auth-history-and-graph',
                    'final-encrypted-backup'],
                rollback=dict(runtime_only=True, restore_live_databases=False, replay_work=False,
                    preserve_old_units_and_dropins=True, preserve_old_source_and_pm=True,
                    require_exact_created_link_targets=True, require_observed_current_process_identity=True,
                    review_permission_rollback_after_policy_retirement=True),
                effects=dict(installs=False, services=False, permissions=False, policy=False, credentials=False))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--layout', required=True, help='Path to a private closed layout JSON')
    args = parser.parse_args()
    from pathlib import Path
    data = Path(args.layout).read_bytes()
    if len(data) > 16 * 1024:
        parser.error('Layout is too large')
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError('Duplicate layout key')
            result[key] = value
        return result
    print(json.dumps(render(json.loads(data, object_pairs_hook=unique)), indent=2))


if __name__ == '__main__':
    main()
