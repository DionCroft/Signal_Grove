"""Recover stamped receipts over authenticated transports across an exact gateway upgrade."""

from argparse import ArgumentParser
import asyncio
from contextlib import closing, contextmanager
from http.server import ThreadingHTTPServer
import copy
import json
from pathlib import Path
import signal
import sqlite3
import sys
import threading

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from canonical_shutdown_gate import REVIEWED_PINS
from verify_canonical_rpc import canonical, sha, source_snapshot


LEGACY = (
    '0357a14b76e4f604d2fa6b9c3a973f9a76f7968d', 27532,
    '57917366fd38eb2424e559e28303b4f5ecae6a9cfc65c0de90619ab3b8835d85',
)
CURRENT = {
    commit for commit in REVIEWED_PINS if commit.startswith(('8fc0374', '5725d1d'))
}
LABEL = 'signal-grove-receipt-fixture'
TEXT = 'STAMPED_RECEIPT_CHECK'
REQUEST = 'stamped-digest-request'
FOLLOWER = 'stamped-follower-request'


def verify_source(path, commit, count, digest):
    inputs = source_snapshot(path, commit=commit, file_count=count)
    if sha(canonical(inputs)) != digest:
        raise ValueError('SDK input map differs from the reviewed source')
    return inputs


def saved_admissions(home):
    with closing(sqlite3.connect((home / 'state.db').as_uri() + '?mode=ro', uri=True)) as db:
        db.row_factory = sqlite3.Row
        rows = db.execute(
            'SELECT admission_id, target_session_id, principal_id, request_id, payload_json, '
            'payload_digest, intent, status, outcome, generation FROM session_admissions'
        ).fetchall()
        assert tuple(db.execute('PRAGMA integrity_check').fetchone()) == ('ok',)
        assert not db.execute('PRAGMA foreign_key_check').fetchall()
    admissions = {row['request_id']: dict(row) for row in rows}
    assert len(admissions) == len(rows)
    return admissions


def run(legacy, current, evidence, *, interrupt=False):
    sys.path.insert(0, str(current))
    from persistence.authority.vocabulary import admission_fingerprint
    from tests.gateway.fixtures.local_recovery_probe import Model, child_env, daemon, rpc, websocket

    case = 'uncertain' if interrupt else 'completed'
    log_prefix = 'gateway-uncertain-stamp' if interrupt else 'gateway-stamp'
    home, user = evidence / f'owned-profile-{case}', evidence / f'owned-account-{case}'
    home.mkdir(mode=0o700)
    user.mkdir(mode=0o700)
    peer = ThreadingHTTPServer(('127.0.0.1', 0), Model)
    peer.requests = []
    peer.blocked, peer.release = threading.Event(), threading.Event()
    thread = threading.Thread(target=peer.serve_forever, daemon=True)
    thread.start()
    endpoint = f'http://127.0.0.1:{peer.server_port}/v1'
    cfg = {
        'gateway': {'multiplex_profiles': False},
        'model': {'provider': 'custom', 'default': 'signal-grove-fixture', 'base_url': endpoint},
        'model_catalog': {'enabled': False},
        'plugins': {'auto_update_check_hours': 0},
        'auxiliary': {'title_generation': {'enabled': False}},
        'platform_toolsets': {'cli': []},
    }
    (home / 'config.yaml').write_text(json.dumps(cfg), encoding='utf-8')

    @contextmanager
    def gateway(root, phase, *, crash=False):
        env = child_env()
        env.update(HOME=str(user), USERPROFILE=str(user), HERMES_HOME=str(home), PYTHONPATH=str(root),
                   OPENAI_API_KEY='loopback-only', OPENAI_BASE_URL=endpoint, PYTHONDONTWRITEBYTECODE='1',
                   HERMES_DISABLE_LAZY_INSTALLS='1', PYTHONUNBUFFERED='1')
        try:
            with daemon(root, home, env, barrier=False) as (proc, descriptor):
                assert descriptor['api_origin'].startswith('http://127.0.0.1:')
                yield proc, descriptor
                if crash:
                    assert proc.returncode == -signal.SIGKILL
                else:
                    proc.send_signal(signal.SIGINT)
                    assert proc.wait(timeout=55) == 0
        finally:
            log = home / 'restart.log'
            if log.is_file():
                log.rename(evidence / f'{log_prefix}-{phase}.log')

    async def result(ws, method, **params):
        reply = await rpc(ws, method, **params)
        assert 'result' in reply, (method, reply.get('error', {}).get('message'))
        return reply['result']

    async def stamp(ws, version):
        await result(ws, 'client.capabilities', client={'name': LABEL, 'kind': 'mobile', 'version': version})

    async def receipt(ws, session, admission):
        return await result(ws, 'prompt.receipt', session_id=session, admission_id=admission)

    async def legacy_turn(proc, descriptor):
        async with websocket(home, descriptor, proxy=None) as ws:
            await stamp(ws, '1.0')
            opened = await result(ws, 'session.create', request_id='stamped-fixture-session',
                                  source='tui', cwd=str(home), toolsets=[])
            session = opened['session_id']
            sent = await result(ws, 'prompt.submit', session_id=session, input_id=REQUEST,
                                text='BLOCK_STARTED' if interrupt else TEXT)
            if interrupt:
                assert await asyncio.to_thread(peer.blocked.wait, 25)
                active = await receipt(ws, session, sent['admission_id'])
                assert active['status'] == 'started' and active['outcome'] is None
                follower = await result(ws, 'prompt.submit', session_id=session, input_id=FOLLOWER,
                                        text='NEVER_REPLAY')
                assert follower['status'] == 'queued'
                assert not peer.release.is_set() and len(peer.requests) == 1
                proc.kill()
                assert await asyncio.to_thread(proc.wait, 10) == -signal.SIGKILL
                return session, {REQUEST: active, FOLLOWER: follower}
            async with asyncio.timeout(65):
                while True:
                    completed = await receipt(ws, session, sent['admission_id'])
                    if completed['status'] == 'terminal':
                        assert completed['outcome'] == 'completed'
                        return session, {REQUEST: completed}
                    await asyncio.sleep(0.05)

    async def recover(descriptor, session, original, expected):
        for version in ('2.0', '2.1'):
            async with websocket(home, descriptor, proxy=None) as ws:
                await stamp(ws, version)
                restored = await result(ws, 'session.resume', session_id=session)
                transcript = json.dumps(restored['messages'])
                if interrupt:
                    assert 'RECOVERY_ACK_' not in transcript
                    pending = {row['admission_id']: row['status'] for row in restored['pending']}
                    assert pending == {item['admission_id']: expected[key]['status']
                                       for key, item in original.items()}
                else:
                    assert 'RECOVERY_ACK_' + TEXT in transcript
                for key, item in original.items():
                    recovered = await receipt(ws, session, item['admission_id'])
                    assert recovered['admission_id'] == item['admission_id']
                    assert recovered['sequence'] == item['sequence']
                    assert recovered['status'] == expected[key]['status']
                    assert recovered['outcome'] == expected[key]['outcome']

    try:
        with gateway(legacy, 'legacy', crash=interrupt) as (proc, first):
            session, original = asyncio.run(legacy_turn(proc, first))
        before = saved_admissions(home)
        assert before.keys() == original.keys()
        for key, row in before.items():
            payload = json.loads(row['payload_json'])
            assert payload['surface_v1']['client'] == {'name': LABEL, 'kind': 'mobile', 'version': '1.0'}
            unlabelled = copy.deepcopy(payload)
            unlabelled['surface_v1'].pop('client')
            original_digest = admission_fingerprint(canonical_target=session, payload={'input': payload, 'intent': row['intent']})
            different_digest = admission_fingerprint(canonical_target=session, payload={'input': unlabelled, 'intent': row['intent']})
            assert row['admission_id'] == original[key]['admission_id'] and row['target_session_id'] == session
            assert (row['status'], row['outcome']) == (original[key]['status'], original[key]['outcome'])
            assert row['payload_digest'] == original_digest and original_digest != different_digest
        expected = copy.deepcopy(before)
        if interrupt:
            expected[REQUEST]['status'] = 'unknown'
        peer.release.set()
        assert len(peer.requests) == 1
        with gateway(current, 'current') as (_, second):
            assert second['instance_id'] != first['instance_id']
            assert second['authority_epoch'] > first['authority_epoch']
            asyncio.run(recover(second, session, original, expected))
        assert saved_admissions(home) == expected and len(peer.requests) == 1
    finally:
        peer.release.set()
        peer.shutdown()
        peer.server_close()
        thread.join(timeout=5)
    report = {'oldStampWasPartOfDigest': True, 'authenticatedNewConnections': 2,
              'newInferenceCalls': 0, 'retainedAdmissions': len(expected)}
    if interrupt:
        return report | {'legacyOwnerKilledWhileRunning': True,
                         'retainedUnknownStatus': expected[REQUEST]['status'],
                         'unresolvedOutcome': expected[REQUEST]['outcome'],
                         'retainedQueueStatus': expected[FOLLOWER]['status']}
    return report | {'originalOutcome': 'completed'}


def main():
    parser = ArgumentParser(description=__doc__)
    parser.add_argument('--legacy', type=Path, required=True)
    parser.add_argument('--current', type=Path, required=True)
    parser.add_argument('--current-commit', choices=sorted(CURRENT), required=True)
    parser.add_argument('--evidence', type=Path, required=True)
    args = parser.parse_args()
    if sys.platform != 'linux':
        raise ValueError('A native Linux gateway is required')
    old, new, evidence = args.legacy.resolve(strict=True), args.current.resolve(strict=True), args.evidence.resolve()
    if old == new or any(evidence.is_relative_to(path) for path in (old, new)):
        raise ValueError('Use independent SDK roots and a fresh evidence directory')
    evidence.mkdir(mode=0o700)
    pin = REVIEWED_PINS[args.current_commit]
    current = (args.current_commit, pin.file_count, pin.source_digest)
    earlier, later = verify_source(old, *LEGACY), verify_source(new, *current)
    assert all(earlier[name] == later[name] for name in
               ('uv.lock', 'pyproject.toml', 'tests/gateway/fixtures/local_recovery_probe.py'))
    completed = run(old, new, evidence)
    uncertain = run(old, new, evidence, interrupt=True)
    verify_source(old, *LEGACY)
    verify_source(new, *current)
    for name, report in (('gateway-stamp-result.json', completed), ('gateway-uncertain-stamp-result.json', uncertain)):
        report.update(legacyCommit=LEGACY[0], currentCommit=args.current_commit, sourceTreesVerified=True)
        with (evidence / name).open('x', encoding='utf-8') as stream:
            json.dump(report, stream, indent=2, sort_keys=True)
        print(json.dumps(report, sort_keys=True))


if __name__ == '__main__':
    main()
