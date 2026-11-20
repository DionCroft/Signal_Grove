#!/usr/bin/env python3
# Run the exact public persistence cohort on the supported PM Linux runtime.
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


def read_cases(raw: bytes) -> list[tuple[str, str]]:
    if len(raw) > 2_000_000 or b'<!DOCTYPE' in raw.upper():
        raise ValueError('Invalid persistence JUnit report')
    root = ET.fromstring(raw)
    suites = [root] if root.tag == 'testsuite' else list(root)
    if root.tag not in ('testsuite', 'testsuites') or len(suites) != 1 or suites[0].tag != 'testsuite':
        raise ValueError('Expected one persistence suite')
    suite = suites[0]
    totals = {name: suite.get(name) for name in ('tests', 'failures', 'errors', 'skipped')}
    if totals != dict(tests=str(CASE_COUNT), failures='0', errors='0', skipped='0'):
        raise ValueError('Incomplete or non-positive persistence suite')
    rows = suite.findall('testcase')
    pairs = [(row.get('classname'), row.get('name')) for row in rows]
    if (len(pairs) != CASE_COUNT or any(not all(pair) for pair in pairs)
            or len(set(pairs)) != CASE_COUNT):
        raise ValueError('Invalid persistence case identities')
    if any(row.find(tag) is not None for row in rows for tag in ('failure', 'error', 'skipped')):
        raise ValueError('Unexpected hidden persistence case outcome')
    return sorted(pairs)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--evidence', type=Path, required=True)
    parser.add_argument('--python', type=Path, required=True)
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
    test_hashes = {name: expected[name] for name in TESTS}
    gate.inspect_source(source, expected, before['modes'], generated=True)
    gate.source_index(source, pin)
    gate.write_new(evidence / 'persistence-intent.json',
                   dict(sourceCommit=pin, files=test_hashes, caseIdentitySha256=CASE_SHA256))
    account, temp = evidence / 'account', evidence / 'temp'
    for folder in (account, temp):
        folder.mkdir(mode=0o700)
    env = {k: os.environ[k] for k in ('PATH', 'LANG', 'LC_ALL') if k in os.environ}
    env.update(HOME=str(account), HERMES_HOME=str(account / 'hermes'), TMPDIR=str(temp),
               XDG_CACHE_HOME=str(account / 'cache'), HERMES_DISABLE_LAZY_INSTALLS='1',
               HERMES_TEST_ISOLATION='1', PYTEST_DISABLE_PLUGIN_AUTOLOAD='1',
               PYTHONDONTWRITEBYTECODE='1')
    argv = [str(args.python.absolute()), '-I', '-B', '-c', PYTEST_DRIVER,
            str(source), str(evidence), *TESTS]
    with (evidence / 'persistence.log').open('xb') as log:
        child = subprocess.Popen(argv, cwd=evidence, env=env, stdin=subprocess.DEVNULL,
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
    cases = read_cases(xml)
    digest = gate.snapshot.digest(json.dumps(cases, separators=(',', ':')).encode())
    if digest != CASE_SHA256:
        raise ValueError('Persistence case identities differ from the reviewed cohort')
    gate.write_new(evidence / 'persistence-result.json',
                   dict(sourceCommit=pin, caseCount=len(cases), caseIdentitySha256=digest,
                        junitSha256=gate.snapshot.digest(xml), testFiles=test_hashes))
    print(f'Verified {len(cases)} unchanged persistence cases at {pin}.')


if __name__ == '__main__':
    main()
