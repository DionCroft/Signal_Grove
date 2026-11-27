#!/usr/bin/env python3
# Run exact public persistence and profile receipt cohorts on the supported PM Linux runtime.
from __future__ import annotations

import argparse
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

TESTS = (
    'tests/persistence_sqlite/authority/test_binding_lineage.py',
    'tests/persistence_sqlite/authority/test_runtime_import_mutations.py',
    'tests/persistence_sqlite/authority/test_runtime_destructive_mutations.py',
    'tests/persistence_sqlite/authority/test_runtime_mutation_actions.py',
    'tests/persistence_sqlite/authority/test_runtime_mutations.py',
    'tests/persistence_sqlite/authority/test_runtime_mutation_retired_import.py',
    'tests/persistence_sqlite/authority/test_runtime_retirement.py',
    'tests/persistence_sqlite/authority/test_runtime_rewind_mutations.py',
    'tests/persistence_sqlite/authority/test_terminal_retirement.py',
    'tests/gateway/test_handoff_local_receipt.py',
    'tests/tui_gateway/test_desktop_session_workspace_persistence.py',
)

CASE_COUNT = 103
CASE_SHA256 = 'd5f4c2a842a12738a340e4f77932d7c385856ef44345748e1865de588861cb94'
PROFILE_RECEIPT_TESTS = (
    'tests/gateway/test_authority_approval_projection.py',
    'tests/gateway/test_api_compressed_receipt_history.py',
    'tests/gateway/test_native_http_auth.py',
    'tests/gateway/test_unknown_receipt_boundaries.py',
    'tests/cron/test_canonical_receipt_bookkeeping.py',
    'tests/cron/test_live_firer_receipt_recovery.py',
    'tests/gateway/test_cron_canonical_receipt_regression.py',
)
PROFILE_RECEIPT_CASE_COUNT = 19
PROFILE_RECEIPT_CASE_SHA256 = '5f45b0d507cc06a41be40fee7b522350636b6fd411138036842105fc3221e5d7'
GATEWAY_RECOVERY_TESTS = (
    'tests/gateway/test_authority_events.py',
    'tests/gateway/test_authority_reconnect_gap.py',
    'tests/gateway/test_completion_admission.py',
)


def gateway_recovery_cohort(pin: str) -> tuple[tuple[str, ...], int, str]:
    if pin == '8fc0374447313e276f6b9a2764bfc750190c966a':
        return GATEWAY_RECOVERY_TESTS, 5, 'e1490b46fb8b7a96ffecab48b59d060ad7e0653d5f2cb3ed47aea9986b1472c1'
    if pin in (
        '1d15aad1dfa8e9e5fcbd2082cad5666a0907a07d',
        '337903eeffd696cf73936a3a00af08b43feb2028',
        '9bba163bc1d090852eccc86802eddc454df47d1b',
        'de2b0590f87b2131b51557b3f30aefeec421be2c',
        '646c982ea7354261b355874781999b8a292a6013',
    ):
        return (
            (*GATEWAY_RECOVERY_TESTS, 'tests/gateway/test_held_route_late_platform.py'),
            20,
            '511d130298081c21a9edb9a9089c2e15287b45beba0a91e8650322b228189743',
        )
    raise ValueError('Gateway recovery inventory has not been reviewed for this pin')

PYTEST_DRIVER = '''
import importlib.metadata, json, platform, sys
from pathlib import Path

source, evidence, *tests = sys.argv[1:]
runtime = dict(system=platform.system(), machine=platform.machine(),
               python=sys.version, pytest=importlib.metadata.version('pytest'))
with (Path(evidence) / 'persistence-runtime.json').open('x', encoding='utf8') as f:
    json.dump(runtime, f)
if runtime['system'] != 'Linux' or runtime['pytest'] != '9.1.1':
    raise SystemExit('Unexpected native PM runtime')
sys.path.insert(0, source)
import pytest
raise SystemExit(pytest.main([
    '-p', 'pytest_asyncio.plugin', '-p', 'no:cacheprovider', '-q',
    '--basetemp', str(Path(evidence) / 'pytest-temp'),
    '--junitxml', str(Path(evidence) / 'persistence.xml'),
    *[str(Path(source) / name) for name in tests],
]))
'''


def read_cases(raw: bytes, count: int = CASE_COUNT) -> list[tuple[str, str]]:
    if len(raw) > 2_000_000 or b'<!DOCTYPE' in raw.upper():
        raise ValueError('Invalid persistence JUnit report')
    root = ET.fromstring(raw)
    suites = [root] if root.tag == 'testsuite' else list(root)
    if root.tag not in ('testsuite', 'testsuites') or len(suites) != 1 or suites[0].tag != 'testsuite':
        raise ValueError('Expected one persistence suite')
    suite = suites[0]
    totals = {name: suite.get(name) for name in ('tests', 'failures', 'errors', 'skipped')}
    if totals != dict(tests=str(count), failures='0', errors='0', skipped='0'):
        raise ValueError('Incomplete or non-positive persistence suite')
    rows = suite.findall('testcase')
    pairs = [(row.get('classname'), row.get('name')) for row in rows]
    if (len(pairs) != count or any(not all(pair) for pair in pairs)
            or len(set(pairs)) != count):
        raise ValueError('Invalid persistence case identities')
    if any(row.find(tag) is not None for row in rows for tag in ('failure', 'error', 'skipped')):
        raise ValueError('Unexpected hidden persistence case outcome')
    return sorted(pairs)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--evidence', type=Path, required=True)
    parser.add_argument('--python', type=Path, required=True)
    cohort = parser.add_mutually_exclusive_group()
    cohort.add_argument('--profile-receipts', action='store_true',
                        help='Run the exact native authentication, approval, history and scheduler receipts cohort')
    cohort.add_argument('--gateway-recovery', action='store_true',
                        help='Run the pinned admission and event replay cohort, including current main held routing')
    args = parser.parse_args()
    source, evidence = args.source.resolve(strict=True), args.evidence.resolve(strict=True)
    spec = importlib.util.spec_from_file_location(
        'canonical_shutdown_gate', Path(__file__).with_name('canonical_shutdown_gate.py'))
    gate = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(gate)
    gate.require_linux()
    before = gate.read_json(evidence / 'before.json')
    pin = before['sourceCommit']
    if before['variant'] != 'pristine' or pin == gate.rpc.PIN:
        raise ValueError('Expected the pristine public persistence layout')
    expected = gate.expected_map(gate.read_json(evidence / 'base-map.json'), 'pristine', pin)
    if args.gateway_recovery:
        tests, count, case_hash = gateway_recovery_cohort(pin)
    elif args.profile_receipts:
        tests, count, case_hash = PROFILE_RECEIPT_TESTS, PROFILE_RECEIPT_CASE_COUNT, PROFILE_RECEIPT_CASE_SHA256
    else:
        tests, count, case_hash = TESTS, CASE_COUNT, CASE_SHA256
    test_hashes = {name: expected[name] for name in tests}
    gate.inspect_source(source, expected, before['modes'], generated=True)
    gate.source_index(source, pin)
    gate.write_new(evidence / 'persistence-intent.json',
                   dict(sourceCommit=pin, files=test_hashes, caseIdentitySha256=case_hash))
    account, temp = evidence / 'account', evidence / 'temp'
    for folder in (account, temp):
        folder.mkdir(mode=0o700)
    env = {k: os.environ[k] for k in ('PATH', 'LANG', 'LC_ALL') if k in os.environ}
    env.update(HOME=str(account), TMPDIR=str(temp),
               XDG_CACHE_HOME=str(account / 'cache'), HERMES_DISABLE_LAZY_INSTALLS='1',
               HERMES_TEST_ISOLATION='1', PYTEST_DISABLE_PLUGIN_AUTOLOAD='1',
               PYTHONDONTWRITEBYTECODE='1')
    # Let upstream create its collection-time sandbox for GatewayRunner's module paths.
    # A pre-set custom HERMES_HOME is deliberately guarded as an operator home.
    if not args.gateway_recovery:
        env['HERMES_HOME'] = str(account / 'hermes')
    argv = [str(args.python.absolute()), '-I', '-B', '-c', PYTEST_DRIVER,
            str(source), str(evidence), *tests]
    # The upstream foreign-firer test imports the SDK from a child Python process.
    working_directory = source if args.profile_receipts else evidence
    with (evidence / 'persistence.log').open('xb') as log:
        child = subprocess.Popen(argv, cwd=working_directory, env=env, stdin=subprocess.DEVNULL,
                                 stdout=log, stderr=subprocess.STDOUT)
        try:
            return_code = child.wait(timeout=600)
        except subprocess.TimeoutExpired:
            gate.write_new(evidence / 'persistence-terminal.json',
                           dict(state='UNKNOWN_PROCESS_PRESERVED_NO_REPLAY', pid=child.pid))
            raise
    gate.write_new(evidence / 'persistence-terminal.json',
                   dict(state='TERMINAL', returnCode=return_code))
    gate.inspect_source(source, expected, before['modes'], generated=True)
    gate.source_index(source, pin)
    if return_code:
        raise SystemExit(return_code)
    xml = (evidence / 'persistence.xml').read_bytes()
    cases = read_cases(xml, count)
    digest = gate.snapshot.digest(json.dumps(cases, separators=(',', ':')).encode())
    if digest != case_hash:
        raise ValueError('Persistence case identities differ from the reviewed cohort')
    gate.write_new(evidence / 'persistence-result.json',
                   dict(sourceCommit=pin, caseCount=len(cases), caseIdentitySha256=digest,
                        junitSha256=gate.snapshot.digest(xml), testFiles=test_hashes))
    print(f'Verified {len(cases)} unchanged cases at {pin}.')


if __name__ == '__main__':
    main()
