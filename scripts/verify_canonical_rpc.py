"""Verify the complete, unmodified pinned Hermes plugin-RPC file on Linux."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import xml.etree.ElementTree as ET

PIN = '3b421578588243f920886e6fe79a6faa87ea026e'
TEST_FILE = 'tests/gateway/test_session_plugin_rpc.py'
EXPECTED = frozenset({
    'test_plugin_method_is_gated_like_a_session_verb_and_leaves_with_its_plugin',
    'test_plugin_session_event_reaches_only_that_sessions_viewers',
    'test_owner_advertises_plugin_rpc_and_the_contract_declares_its_namespace',
    'test_emit_from_a_plugin_thread_reaches_its_own_profile_on_a_multiplexed_gateway',
    'test_plugin_frontend_gets_schema_checked_json_from_its_own_task_with_input_redacted[in_process]',
    'test_plugin_frontend_gets_schema_checked_json_from_its_own_task_with_input_redacted[host]',
})


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def canonical(data: object) -> bytes:
    return json.dumps(data, sort_keys=True, separators=(',', ':')).encode()


def positive_cases(raw: bytes) -> list[dict[str, str]]:
    if len(raw) > 2 * 1024 * 1024 or b'<!DOCTYPE' in raw.upper():
        raise ValueError('Invalid JUnit input')
    tree = ET.fromstring(raw)
    suites = [tree] if tree.tag == 'testsuite' else list(tree)
    if tree.tag not in ('testsuite', 'testsuites') or len(suites) != 1 or suites[0].tag != 'testsuite':
        raise ValueError('Expected exactly one canonical test suite')
    suite = suites[0]
    if {k: suite.get(k) for k in ('tests', 'failures', 'errors', 'skipped')} != dict(tests='6', failures='0', errors='0', skipped='0'):
        raise ValueError('All six cases must pass without skips or errors')
    rows = suite.findall('testcase')
    names = [row.get('name') for row in rows]
    if len(rows) != 6 or len(set(names)) != 6 or set(names) != EXPECTED:
        raise ValueError('Canonical case inventory mismatch')
    classes = {row.get('classname', '') for row in rows}
    if len(classes) != 1 or not next(iter(classes)).endswith('test_session_plugin_rpc'):
        raise ValueError('Unexpected canonical test class')
    if any(row.find(tag) is not None for row in rows for tag in ('failure', 'error', 'skipped')):
        raise ValueError('Non-positive canonical result')
    return sorted((dict(name=row.get('name'), classname=row.get('classname')) for row in rows), key=lambda row: row['name'])


def reviewed_entries(head: bytes, index: bytes) -> dict[bytes, tuple[bytes, bytes]]:
    expected = {}
    actual = {}
    for raw in head.split(b'\0'):
        if raw:
            header, name = raw.split(b'\t', 1)
            mode, kind, blob = header.split()
            if kind != b'blob' or name in expected:
                raise ValueError('Invalid pinned SDK tree')
            expected[name] = (mode, blob)
    for raw in index.split(b'\0'):
        if raw:
            header, name = raw.split(b'\t', 1)
            mode, blob, stage = header.split()
            if stage != b'0' or name in actual:
                raise ValueError('Unmerged or duplicate SDK index entry')
            actual[name] = (mode, blob)
    if not expected or actual != expected:
        raise ValueError('SDK index differs from the pinned HEAD tree')
    return expected


def source_snapshot(source: Path) -> dict[str, str]:
    def git(*args: str) -> bytes:
        return subprocess.check_output(['git', '--no-optional-locks', '-C', str(source), *args], stderr=subprocess.PIPE)
    if git('rev-parse', 'HEAD').decode().strip() != PIN:
        raise ValueError('Wrong pinned SDK revision')
    result = {}
    entries = reviewed_entries(git('ls-tree', '-r', '-z', 'HEAD'), git('ls-files', '--stage', '-z'))
    for name, (mode, blob) in entries.items():
        relative = name.decode('utf8')
        path = source / relative
        if mode not in (b'100644', b'100755') or path.is_symlink() or not path.resolve(strict=True).is_relative_to(source):
            raise ValueError('Unexpected SDK tree entry')
        data = path.read_bytes()
        if hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest().encode() != blob:
            raise ValueError('SDK working bytes differ from pinned Git blob: ' + relative)
        if os.name == 'posix' and bool(path.stat().st_mode & 0o111) != (mode == b'100755'):
            raise ValueError('SDK executable bit changed: ' + relative)
        result[relative] = sha(data)
    if len(result) != 23586 or TEST_FILE not in result:
        raise ValueError('Incomplete pinned SDK tree')
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('phase', choices=('before', 'after'))
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--evidence', type=Path, required=True)
    args = parser.parse_args()
    if platform.system() != 'Linux' or os.name != 'posix':
        raise ValueError('Canonical POSIX acceptance requires the actual Linux host')
    source = args.source.resolve(strict=True)
    evidence = args.evidence.resolve(strict=True)
    status = subprocess.check_output(['git', '--no-optional-locks', '-C', str(source), 'status', '--porcelain', '--untracked-files=no']).decode('utf8')
    # Keep filter/stat observations, but accept source only through exact
    # HEAD -> index -> working-byte/mode equality, independent of Git filters.
    with (evidence / (args.phase + '-git-status.json')).open('x', encoding='utf8') as stream:
        json.dump(dict(status=status, sourceCommit=PIN), stream, indent=2)
    snapshot = source_snapshot(source)
    map_path = evidence / 'source-map.json'
    before_path = evidence / 'before.json'
    if args.phase == 'before':
        for path, value in ((map_path, snapshot), (before_path, dict(sourceCommit=PIN, sourceFiles=len(snapshot), sourceMapSha256=sha(canonical(snapshot)), nativeSystem=platform.system(), nativeMachine=platform.machine(), canonicalRunner='scripts/run_tests.sh', testFile=TEST_FILE))):
            with path.open('x', encoding='utf8', newline='\n') as stream:
                stream.write(json.dumps(value, indent=2) + '\n')
        print('Pinned pristine SDK tree verified before PM setup')
        return
    before = json.loads(before_path.read_text())
    if json.loads(map_path.read_text()) != snapshot or before['sourceMapSha256'] != sha(canonical(snapshot)) or before['sourceCommit'] != PIN:
        raise ValueError('SDK source changed during PM setup or canonical tests')
    if (evidence / 'canonical-exit-code.txt').read_text().strip() != '0':
        raise ValueError('Canonical runner did not exit successfully')
    junit = (evidence / 'canonical.xml').read_bytes()
    cases = positive_cases(junit)
    runtime = json.loads((evidence / 'test-environment.json').read_text())
    if runtime['system'] != 'Linux' or runtime['machine'] != platform.machine() or runtime['role'] != 'supported-pm-side-test-environment':
        raise ValueError('Incorrect native test environment')
    report = dict(state='PRISTINE_LINUX_CANONICAL_PLUGIN_RPC_SIX_PASS', sourceCommit=PIN,
                  sourceFiles=len(snapshot), sourceMapSha256=sha(canonical(snapshot)),
                  canonicalRunner='scripts/run_tests.sh', fileRetries=0, testFile=TEST_FILE,
                  nativeSystem=platform.system(), nativeMachine=platform.machine(),
                  cases=cases, junitSha256=sha(junit), runtime=runtime,
                  canonicalLogSha256=sha((evidence / 'canonical.log').read_bytes()),
                  allTrackedSourceUnchanged=True, exactHeadIndexAndWorkingBytes=True, piActivationAccepted=False,
                  completeUpstreamLifecycleAccepted=False)
    with (evidence / 'result.json').open('x', encoding='utf8', newline='\n') as stream:
        stream.write(json.dumps(report, indent=2) + '\n')
    print(json.dumps({key: value for key, value in report.items() if key not in ('cases', 'runtime')}))


if __name__ == '__main__':
    main()
