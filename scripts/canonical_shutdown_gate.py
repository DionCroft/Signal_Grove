"""Run and reconcile the exact pinned SDK shutdown cohort on actual Linux.

The SDK runner and tests are unmodified. Only the candidate's two reviewed
lifecycle files change. Generated caches are inventoried separately from source.
"""
from __future__ import annotations

import argparse
import importlib.util
import json
import math
import os
from pathlib import Path, PurePosixPath
import platform
import re
import stat
import subprocess
import sys
import time
import xml.etree.ElementTree as ET


def sibling(name):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(name + '.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


snapshot = sibling('hermes_snapshot_heartbeat')
pinned = snapshot.pinned
rpc = sibling('verify_canonical_rpc')
CASES = {'tests/tui_gateway/test_backend_heartbeat.py': [('tests.tui_gateway.test_backend_heartbeat.TestBackendHeartbeatRefresher',
                                                  'test_first_call_registers_row_in_db'),
                                                 ('tests.tui_gateway.test_backend_heartbeat.TestBackendHeartbeatRefresher',
                                                  'test_repeat_calls_are_noops'),
                                                 ('tests.tui_gateway.test_backend_heartbeat.TestBackendHeartbeatRefresher',
                                                  'test_refresher_failure_does_not_raise'),
                                                 ('tests.tui_gateway.test_backend_heartbeat.TestEntryAndWsWiring',
                                                  'test_entry_main_starts_heartbeat_refresher'),
                                                 ('tests.tui_gateway.test_backend_heartbeat.TestEntryAndWsWiring',
                                                  'test_handle_ws_starts_heartbeat_refresher')],
 'tests/tui_gateway/test_session_db_ownership_teardown.py': [('tests.tui_gateway.test_session_db_ownership_teardown',
                                                              'test_close_closes_a_dedicated_handle_it_owns'),
                                                             ('tests.tui_gateway.test_session_db_ownership_teardown',
                                                              'test_close_never_closes_a_shared_handle'),
                                                             ('tests.tui_gateway.test_session_db_ownership_teardown',
                                                              'test_close_without_ownership_attribute_does_not_close'),
                                                             ('tests.tui_gateway.test_session_db_ownership_teardown',
                                                              'test_close_is_idempotent_for_an_owned_handle'),
                                                             ('tests.tui_gateway.test_session_db_ownership_teardown',
                                                              'test_raising_close_is_swallowed_and_not_retried'),
                                                             ('tests.tui_gateway.test_session_db_ownership_teardown',
                                                              'test_close_still_ends_the_session_row_before_closing'),
                                                             ('tests.tui_gateway.test_session_db_ownership_teardown',
                                                              'test_lazy_recall_open_is_owned_by_the_agent'),
                                                             ('tests.tui_gateway.test_session_db_ownership_teardown',
                                                              'test_transfer_marks_the_agent_that_holds_the_handle'),
                                                             ('tests.tui_gateway.test_session_db_ownership_teardown',
                                                              'test_transfer_is_refused_when_the_agent_holds_a_different_handle'),
                                                             ('tests.tui_gateway.test_session_db_ownership_teardown',
                                                              'test_transfer_is_refused_for_missing_operands[None-db0]'),
                                                             ('tests.tui_gateway.test_session_db_ownership_teardown',
                                                              'test_transfer_is_refused_for_missing_operands[agent1-None]'),
                                                             ('tests.tui_gateway.test_session_db_ownership_teardown',
                                                              'test_transfer_is_refused_for_the_shared_launch_handle'),
                                                             ('tests.tui_gateway.test_session_db_ownership_teardown',
                                                              'test_deferred_build_closes_the_handle_when_the_build_fails'),
                                                             ('tests.tui_gateway.test_session_db_ownership_teardown',
                                                              'test_deferred_build_transfers_the_handle_on_success'),
                                                             ('tests.tui_gateway.test_session_db_ownership_teardown',
                                                              'test_deferred_build_uses_the_preserved_desktop_workspace_provenance'),
                                                             ('tests.tui_gateway.test_session_db_ownership_teardown',
                                                              'test_deferred_build_closes_the_handle_when_the_session_is_reaped_midbuild'),
                                                             ('tests.tui_gateway.test_session_db_ownership_teardown',
                                                              'test_deferred_build_never_opens_or_closes_for_the_launch_profile')],
 'tests/gateway/test_shutdown_executor_quiesce.py': [('tests.gateway.test_shutdown_executor_quiesce',
                                                      'test_running_executor_work_finishes_before_session_db_close'),
                                                     ('tests.gateway.test_shutdown_executor_quiesce',
                                                      'test_executor_refuses_new_work_before_session_db_close'),
                                                     ('tests.gateway.test_shutdown_executor_quiesce',
                                                      'test_stuck_worker_skips_the_session_db_close'),
                                                     ('tests.gateway.test_shutdown_executor_quiesce',
                                                      'test_live_writer_outside_the_executor_skips_the_session_db_close[cron]'),
                                                     ('tests.gateway.test_shutdown_executor_quiesce',
                                                      'test_live_writer_outside_the_executor_skips_the_session_db_close[api]'),
                                                     ('tests.gateway.test_shutdown_executor_quiesce',
                                                      'test_live_writer_outside_the_executor_skips_the_session_db_close[deferred]'),
                                                     ('tests.gateway.test_shutdown_executor_quiesce',
                                                      'test_cancelled_api_handler_worker_still_blocks_session_db_close'),
                                                     ('tests.gateway.test_shutdown_executor_quiesce',
                                                      'test_shutdown_executor_defaults_to_no_wait'),
                                                     ('tests.gateway.test_shutdown_executor_quiesce',
                                                      'test_shutdown_executor_reports_a_stuck_worker'),
                                                     ('tests.gateway.test_shutdown_executor_quiesce',
                                                      'test_default_executor_worker_is_seen_by_the_close_guard')],
 'tests/gateway/test_shutdown_teardown_profile_scope.py': [('tests.gateway.test_shutdown_teardown_profile_scope',
                                                            'test_shutdown_teardown_commits_memory_under_the_owning_profile'),
                                                           ('tests.gateway.test_shutdown_teardown_profile_scope',
                                                            'test_in_turn_teardown_keeps_the_callers_scope')],
 'tests/gateway/test_mcp_shutdown_profile_passes.py': [('tests.gateway.test_mcp_shutdown_profile_passes',
                                                        'test_every_pass_shares_the_callers_budget'),
                                                       ('tests.gateway.test_mcp_shutdown_profile_passes',
                                                        'test_teardown_thread_does_not_inherit_the_callers_home_override')],
 'tests/gateway/test_gateway_shutdown.py': [('tests.gateway.test_gateway_shutdown',
                                             'test_cancel_background_tasks_cancels_inflight_message_processing'),
                                            ('tests.gateway.test_gateway_shutdown',
                                             'test_cron_provider_stop_cannot_override_gateway_exit_code'),
                                            ('tests.gateway.test_gateway_shutdown',
                                             'test_gateway_stop_interrupts_running_agents_and_cancels_adapter_tasks'),
                                            ('tests.gateway.test_gateway_shutdown',
                                             'test_gateway_stop_settles_completion_batch_before_adapter_disconnect'),
                                            ('tests.gateway.test_gateway_shutdown',
                                             'test_planned_service_exit_issues_no_restart_of_its_own'),
                                            ('tests.gateway.test_gateway_shutdown',
                                             'test_post_interrupt_grace_only_shortens_unexpected_signal_shutdown[True-False-0.25]'),
                                            ('tests.gateway.test_gateway_shutdown',
                                             'test_post_interrupt_grace_only_shortens_unexpected_signal_shutdown[False-False-5.0]'),
                                            ('tests.gateway.test_gateway_shutdown',
                                             'test_post_interrupt_grace_only_shortens_unexpected_signal_shutdown[True-True-5.0]'),
                                            ('tests.gateway.test_gateway_shutdown',
                                             'test_in_chat_restart_skips_home_shutdown_even_with_active_session'),
                                            ('tests.gateway.test_gateway_shutdown',
                                             'test_gateway_stop_kills_tool_subprocesses_before_adapter_disconnect_on_timeout'),
                                            ('tests.gateway.test_gateway_shutdown',
                                             'test_signal_initiated_shutdown_persists_running_not_stopped'),
                                            ('tests.gateway.test_gateway_shutdown',
                                             'test_pid_exists_zombie_via_psutil_returns_false'),
                                            ('tests.gateway.test_gateway_shutdown',
                                             'test_shutdown_mcp_servers_nonblocking_keeps_loop_responsive'),
                                            ('tests.gateway.test_gateway_shutdown',
                                             'test_shutdown_mcp_servers_nonblocking_completes_fast_path')]}
VARIANTS = ('pristine', 'candidate')
BASE_SHA = snapshot.REVIEWED['pristine-stable3b'][1]
PYTEST_CACHE = frozenset({'.gitignore', 'README.md', 'CACHEDIR.TAG',
                          'v/cache/nodeids', 'v/cache/lastfailed', 'v/cache/stepwise'})


def write_new(path, data):
    with path.open('x', encoding='utf8', newline='\n') as stream:
        stream.write(json.dumps(data, indent=2, sort_keys=True) + '\n')


def read_json(path):
    return json.loads(path.read_text(encoding='utf8'), object_pairs_hook=snapshot.unique)


def require_linux():
    if platform.system() != 'Linux' or os.name != 'posix':
        raise ValueError('Native Linux required; no OS simulation')


def expected_map(base, variant):
    if variant not in VARIANTS or len(base) != 23586 or snapshot.digest(snapshot.canonical(base)) != BASE_SHA:
        raise ValueError('Unreviewed whole SDK source')
    result = dict(base)
    if variant == 'candidate':
        for name, row in pinned.PATCH_FILES.items():
            if result[name] != row['base_sha256']:
                raise ValueError('Unreviewed patch input')
            result[name] = row['patched_sha256']
    return result


def generated_path(name, sources, guard=None):
    """Allow only cache output names tied to reviewed source or known runners."""
    path = PurePosixPath(name)
    if name == 'test_durations.json':
        return True
    if name.startswith('.pytest_cache/'):
        return name.removeprefix('.pytest_cache/') in PYTEST_CACHE
    if guard and re.fullmatch('[a-f0-9]{16}', guard):
        if name in (f'.pytest-cache/gw-adapter-guard-{guard}',
                    f'.pytest-cache/.gw-adapter-guard-{guard}.lock'):
            return True
    if path.parent.name != '__pycache__':
        return False
    match = re.fullmatch(r'(.+)\.cpython-(312|314)(?:\.opt-[12]|-pytest-9\.1\.1)?\.pyc', path.name)
    return bool(match and (path.parent.parent / (match[1] + '.py')).as_posix() in sources)


def guard_fingerprint(source):
    # Same stat-only fingerprint as the unchanged SDK tests/gateway/conftest.py.
    # No cache is seeded: canonical pytest must create its own successful scan.
    import hashlib
    checksum = hashlib.sha256()
    for path in sorted((source / 'tests/gateway').rglob('test_*.py')):
        info = path.stat()
        checksum.update(f'{path.name}:{info.st_mtime_ns}:{info.st_size}'.encode())
    return checksum.hexdigest()[:16]


def inspect_source(source, expected, modes=None, *, generated=False, observations=None):
    """Verify every source byte/mode and reject new executable namespaces."""
    hashes, actual_modes, extras, directories, unexpected = {}, {}, {}, set(), {}
    guard = guard_fingerprint(source)
    total = 0
    for directory, dirs, files in os.walk(source, followlinks=False):
        root = Path(directory)
        pinned.ordinary(root)
        if root == source and '.git' in dirs:
            pinned.ordinary(root / '.git')
            dirs.remove('.git')
        for name in dirs:
            child = root / name
            pinned.ordinary(child)
            directories.add(child.relative_to(source).as_posix())
        for name in files:
            path = root / name
            pinned.ordinary(path)
            if not stat.S_ISREG(path.lstat().st_mode):
                raise ValueError('Nonregular SDK entry')
            rel = path.relative_to(source).as_posix()
            pinned.relative_path(rel)
            size = path.stat().st_size
            total += size
            if size > pinned.MAX_FILE_BYTES or total > 512 * 1024 * 1024:
                raise ValueError('SDK inventory byte bound exceeded')
            raw = path.read_bytes()
            mode = stat.S_IMODE(path.stat().st_mode)
            if rel in expected:
                hashes[rel] = snapshot.digest(raw)
                actual_modes[rel] = mode
            elif generated and generated_path(rel, expected, guard) and not mode & 0o111:
                if rel.startswith('.pytest-cache/'):
                    wanted = b'' if rel.endswith('.lock') else b'clean'
                    if raw != wanted:
                        raise ValueError('Adapter-guard output is not the exact successful result')
                if rel == 'test_durations.json':
                    durations = read_json(path)
                    if (type(durations) is not dict or not set(durations) <= set(CASES) or
                            any(type(t) not in (float, int) or not math.isfinite(t) or t < 0 for t in durations.values())):
                        raise ValueError('Unexpected canonical duration output')
                extras[rel] = dict(sha256=snapshot.digest(raw), bytes=size, mode=mode)
            else:
                unexpected[rel] = dict(sha256=snapshot.digest(raw), bytes=size, mode=mode)
    result = dict(source=hashes, modes=actual_modes, generated=extras, guardFingerprint=guard)
    if observations is not None:
        write_new(observations, dict(**result, unexpected=unexpected, directories=sorted(directories)))
    if unexpected:
        raise ValueError('Unexpected SDK source/output: ' + ', '.join(sorted(unexpected)[:20]))
    all_names = set(hashes) | set(extras)
    allowed_dirs = {p.as_posix() for n in all_names for p in PurePosixPath(n).parents if p.as_posix() != '.'}
    if directories != allowed_dirs or hashes != expected:
        raise ValueError('Missing/changed source or empty shadow namespace')
    if modes is not None and actual_modes != modes:
        raise ValueError('SDK source mode changed')
    names = sorted(n for n, mode in actual_modes.items() if mode & 0o111)
    if len(expected) == 23586 and (len(names), snapshot.digest(('\n'.join(names) + '\n').encode())) != snapshot.EXECUTABLES:
        raise ValueError('SDK executable identities changed')
    return result


def source_index(source):
    if pinned.git(source, 'rev-parse', 'HEAD').decode().strip() != rpc.PIN:
        raise ValueError('SDK HEAD changed')
    rpc.reviewed_entries(pinned.git(source, 'ls-tree', '-r', '-z', 'HEAD'),
                         pinned.git(source, 'ls-files', '--stage', '-z'))


def prepare(source, evidence, variant):
    require_linux()
    if evidence.exists():
        raise ValueError('Fresh evidence required; preserve uncertain/failed runs')
    base = rpc.source_snapshot(source)
    expected = expected_map(base, variant)
    before = inspect_source(source, base)
    evidence.mkdir(mode=0o700)
    write_new(evidence / 'intent.json', dict(state='PREPARATION_STARTED', variant=variant, sourceCommit=rpc.PIN))
    write_new(evidence / 'base-map.json', base)
    write_new(evidence / 'git-observations.json', dict(status=pinned.git(source, 'status', '--porcelain', '--untracked-files=no').decode('utf8')))
    if variant == 'candidate':
        patch = Path(__file__).resolve().parents[1] / 'upstream_patches' / pinned.PATCH_NAME
        raw = pinned.patch_bytes(patch)
        staged = evidence / pinned.PATCH_NAME
        staged.write_bytes(raw)
        pinned.git(source, 'apply', '--check', str(staged))
        pinned.git(source, 'apply', str(staged))
        for name in pinned.PATCH_FILES:
            (source / name).chmod(before['modes'][name])
    actual = inspect_source(source, expected, before['modes'])
    source_index(source)
    write_new(evidence / 'before.json', dict(variant=variant, sourceCommit=rpc.PIN,
              patchSha256=pinned.PATCH_SHA256 if variant == 'candidate' else None,
              sourceMapSha256=snapshot.digest(snapshot.canonical(expected)), **actual))


def command(source, evidence, index, test_file):
    return ['bash', str(source / 'scripts/run_tests.sh'), test_file, '-j', '1',
            '--file-retries', '0', '--fail-on-skip', '--file-timeout', '240', '--', '-v',
            '--junitxml=' + str(evidence / f'{index}.xml')]


def run(source, evidence, python):
    require_linux()
    before = read_json(evidence / 'before.json')
    expected = expected_map(read_json(evidence / 'base-map.json'), before['variant'])
    inspect_source(source, expected, before['modes'], generated=True)
    source_index(source)
    runtime_code = "import importlib.metadata,json,platform,sys; print(json.dumps(dict(system=platform.system(),machine=platform.machine(),python=sys.version,pytest=importlib.metadata.version('pytest'),executable=sys.executable,prefix=sys.prefix)))"
    runtime = json.loads(subprocess.check_output([str(python), '-I', '-B', '-c', runtime_code], text=True))
    if runtime['system'] != 'Linux' or runtime['machine'] != platform.machine() or runtime['pytest'] != '9.1.1':
        raise ValueError('Wrong native supported-PM environment')
    write_new(evidence / 'runtime.json', runtime)
    if (evidence / 'run.json').exists():
        raise ValueError('Never replay an existing run')
    record = dict(state='STARTED', fileRetries=0, canonicalRunner='scripts/run_tests.sh', runs=[])
    def save():
        (evidence / 'run.json').write_text(json.dumps(record, indent=2) + '\n', encoding='utf8')
    save()
    env = os.environ.copy()
    env['HERMES_PYTHON'] = str(python)
    for index, test_file in enumerate(CASES):
        argv = command(source, evidence, index, test_file)
        row = dict(file=test_file, argv=argv, state='LAUNCH_INTENT')
        record['runs'].append(row)
        save()
        with (evidence / f'{index}.log').open('xb') as log:
            child = subprocess.Popen(argv, cwd=source, env=env, stdin=subprocess.DEVNULL,
                                     stdout=log, stderr=subprocess.STDOUT)
            row.update(state='RUNNING', pid=child.pid, started=time.time())
            save()
            try:
                row['exitCode'] = child.wait(timeout=540)
            except subprocess.TimeoutExpired:
                row['state'] = 'UNKNOWN_PROCESS_PRESERVED_NO_REPLAY'
                record['state'] = row['state']
                save()
                raise
        row.update(state='TERMINAL', finished=time.time())
        save()
    record['state'] = 'ALL_FILES_TERMINAL'
    save()


def positive_cases(raw, test_file):
    if len(raw) > 2 * 1024 * 1024 or b'<!DOCTYPE' in raw.upper():
        raise ValueError('Invalid JUnit input')
    root = ET.fromstring(raw)
    suites = [root] if root.tag == 'testsuite' else list(root)
    if root.tag not in ('testsuite', 'testsuites') or len(suites) != 1 or suites[0].tag != 'testsuite':
        raise ValueError('Expected exactly one raw suite')
    suite = suites[0]
    expected = {tuple(pair) for pair in CASES[test_file]}
    if {k: suite.get(k) for k in ('tests', 'failures', 'errors', 'skipped')} != dict(tests=str(len(expected)), failures='0', errors='0', skipped='0'):
        raise ValueError('Non-positive or incomplete suite totals')
    rows = suite.findall('testcase')
    pairs = [(row.get('classname'), row.get('name')) for row in rows]
    if len(pairs) != len(expected) or set(pairs) != expected:
        raise ValueError('Exact case identities missing, duplicated or changed')
    if any(row.find(tag) is not None for row in rows for tag in ('failure', 'error', 'skipped')):
        raise ValueError('Hidden non-positive test outcome')
    return sorted(pairs)


def accepted_evidence(evidence):
    before = read_json(evidence / 'before.json')
    variant = before['variant']
    expected = expected_map(read_json(evidence / 'base-map.json'), variant)
    if (before['sourceCommit'] != rpc.PIN or before['source'] != expected or
            before['sourceMapSha256'] != snapshot.digest(snapshot.canonical(expected)) or
            before['patchSha256'] != (pinned.PATCH_SHA256 if variant == 'candidate' else None) or before['generated']):
        raise ValueError('Preparation evidence mismatch')
    after = read_json(evidence / 'after.json')
    if (after['source'] != expected or after['modes'] != before['modes'] or
            after['guardFingerprint'] != before['guardFingerprint']):
        raise ValueError('Source changed during PM/canonical execution')
    if any(not generated_path(name, expected, after['guardFingerprint']) for name in after['generated']):
        raise ValueError('Unexpected generated output in evidence')
    runtime = read_json(evidence / 'runtime.json')
    if runtime['system'] != 'Linux' or runtime['pytest'] != '9.1.1':
        raise ValueError('Not native supported-PM Linux evidence')
    rows = terminal_runs(read_json(evidence / 'run.json'))
    results = []
    for index, (test_file, row) in enumerate(zip(CASES, rows)):
        raw = (evidence / f'{index}.xml').read_bytes()
        log = (evidence / f'{index}.log').read_bytes()
        results.append(dict(file=test_file, cases=positive_cases(raw, test_file),
                            junitSha256=snapshot.digest(raw), logSha256=snapshot.digest(log)))
    return dict(variant=variant, sourceCommit=rpc.PIN, sourceMapSha256=before['sourceMapSha256'],
                caseCount=sum(len(r['cases']) for r in results), files=results,
                canonicalRunner='scripts/run_tests.sh', fileRetries=0, runtime=runtime,
                generatedFiles=len(after['generated']), piActivationAccepted=False,
                completeUpstreamLifecycleAccepted=False)


def terminal_runs(record):
    """Reject a green aggregate over failed, unknown, replayed or filtered runs."""
    rows = record['runs']
    if (record['state'] != 'ALL_FILES_TERMINAL' or record['fileRetries'] != 0 or
            record['canonicalRunner'] != 'scripts/run_tests.sh' or
            [r['file'] for r in rows] != list(CASES) or
            any(r['state'] != 'TERMINAL' or r['exitCode'] != 0 for r in rows)):
        raise ValueError('Canonical process failed, missing, retried or unknown')
    for index, (test_file, row) in enumerate(zip(CASES, rows)):
        argv = row['argv']
        # Artifact relocation changes the directory, never the file basename.
        wanted = [test_file, '-j', '1', '--file-retries', '0', '--fail-on-skip', '--file-timeout', '240', '--', '-v']
        if (len(argv) != 13 or argv[:1] != ['bash'] or not argv[1].endswith('/scripts/run_tests.sh') or
                argv[2:12] != wanted or not argv[12].endswith('/' + str(index) + '.xml') or not argv[12].startswith('--junitxml=')):
            raise ValueError('Canonical invocation changed')
    return rows


def verify(source, evidence):
    require_linux()
    before = read_json(evidence / 'before.json')
    expected = expected_map(read_json(evidence / 'base-map.json'), before['variant'])
    source_index(source)
    actual = inspect_source(source, expected, before['modes'], generated=True,
                            observations=evidence / 'observed-tree.json')
    write_new(evidence / 'after.json', actual)
    result = accepted_evidence(evidence)
    result['state'] = 'NATIVE_LINUX_CANONICAL_SHUTDOWN_FIFTY_PASS'
    write_new(evidence / 'result.json', result)
    print(json.dumps({k: v for k, v in result.items() if k not in ('files', 'runtime')}))


def compare(pristine, candidate, output):
    a, b = accepted_evidence(pristine), accepted_evidence(candidate)
    if a['variant'] != 'pristine' or b['variant'] != 'candidate' or a['caseCount'] != 50 or b['caseCount'] != 50:
        raise ValueError('Need the two distinct complete cohorts')
    if [r['cases'] for r in a['files']] != [r['cases'] for r in b['files']]:
        raise ValueError('Paired case identities differ')
    if read_json(pristine / 'before.json')['modes'] != read_json(candidate / 'before.json')['modes']:
        raise ValueError('Paired source modes differ')
    result = dict(state='PAIRED_LINUX_CANONICAL_SHUTDOWN_FIFTY_IDENTITIES_PASS',
                  sourceCommit=rpc.PIN, patchSha256=pinned.PATCH_SHA256,
                  pristine=a, candidate=b, exactSameFiftyCases=True,
                  piActivationAccepted=False, completeUpstreamLifecycleAccepted=False)
    write_new(output, result)
    print(result['state'])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('prepare', 'run', 'verify', 'compare'))
    parser.add_argument('--source', type=Path)
    parser.add_argument('--evidence', type=Path)
    parser.add_argument('--variant', choices=VARIANTS)
    parser.add_argument('--python', type=Path)
    parser.add_argument('--pristine', type=Path)
    parser.add_argument('--candidate', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    if args.action == 'compare':
        if not all((args.pristine, args.candidate, args.output)):
            parser.error('compare requires pristine, candidate and fresh output')
        compare(args.pristine.resolve(strict=True), args.candidate.resolve(strict=True), args.output)
        return
    if not args.source or not args.evidence:
        parser.error('source and evidence required')
    source, evidence = args.source.resolve(strict=True), args.evidence.resolve()
    if args.action == 'prepare':
        if args.variant is None:
            parser.error('prepare requires variant')
        prepare(source, evidence, args.variant)
    elif args.action == 'run':
        if not args.python:
            parser.error('run requires supported PM test interpreter')
        # A venv interpreter is usually a symlink. Resolving it would discard
        # the supported PM environment and select the base interpreter.
        if not args.python.is_file():
            parser.error('PM interpreter is missing')
        run(source, evidence, args.python.absolute())
    else:
        verify(source, evidence)


if __name__ == '__main__':
    main()
