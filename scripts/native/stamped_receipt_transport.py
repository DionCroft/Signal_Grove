"""Read a receipt from the stamped-digest writer over an authenticated gateway upgrade."""

from argparse import ArgumentParser
import asyncio
from contextlib import contextmanager
from http.server import ThreadingHTTPServer
import copy
import json
import os
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


def verify_source(path, commit, count, digest):
    inputs = source_snapshot(path, commit=commit, file_count=count)
    if sha(canonical(inputs)) != digest:
        raise ValueError('SDK input map differs from the reviewed source')
    return inputs


def saved_admission(home):
    with sqlite3.connect((home / 'state.db').as_uri() + '?mode=ro', uri=True) as db:
        rows = db.execute(
            'SELECT admission_id, target_session_id, principal_id, request_id, payload_json, '
            'payload_digest, intent, status, outcome, generation FROM session_admissions'
        ).fetchall()
        assert db.execute('PRAGMA integrity_check').fetchone() == ('ok',)
        assert not db.execute('PRAGMA foreign_key_check').fetchall()
    row, = rows
    assert row[3] == REQUEST and row[7:9] == ('terminal', 'completed')
    return row


def run(legacy, current, evidence):
    sys.path.insert(0, str(current))
    from persistence.authority.vocabulary import admission_fingerprint
    from tests.gateway.fixtures.local_recovery_probe import Model, child_env, daemon, rpc, websocket

    home, user = evidence / 'owned-profile', evidence / 'owned-account'
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
    def gateway(root, phase):
        env = child_env()
        env.update(HOME=str(user), USERPROFILE=str(user), HERMES_HOME=str(home), PYTHONPATH=str(root),
                   OPENAI_API_KEY='loopback-only', OPENAI_BASE_URL=endpoint, PYTHONDONTWRITEBYTECODE='1',
                   HERMES_DISABLE_LAZY_INSTALLS='1', PYTHONUNBUFFERED='1')
        try:
            with daemon(root, home, env, barrier=False) as (proc, descriptor):
                assert descriptor['api_origin'].startswith('http://127.0.0.1:')
                yield descriptor
                proc.send_signal(signal.SIGINT)
                assert proc.wait(timeout=55) == 0
        finally:
            log = home / 'restart.log'
            if log.is_file():
                log.rename(evidence / f'gateway-stamp-{phase}.log')

    async def result(ws, method, **params):
        reply = await rpc(ws, method, **params)
        assert 'result' in reply, (method, reply.get('error', {}).get('message'))
        return reply['result']

    async def stamp(ws, version):
        await result(ws, 'client.capabilities', client={'name': LABEL, 'kind': 'mobile', 'version': version})

    async def legacy_turn(descriptor):
        async with websocket(home, descriptor, proxy=None) as ws:
            await stamp(ws, '1.0')
            opened = await result(ws, 'session.create', request_id='stamped-fixture-session',
                                  source='tui', cwd=str(home), toolsets=[])
            session = opened['session_id']
            sent = await result(ws, 'prompt.submit', session_id=session, input_id=REQUEST, text=TEXT)
            async with asyncio.timeout(65):
                while True:
                    receipt = await result(ws, 'prompt.receipt', session_id=session,
                                           admission_id=sent['admission_id'])
                    if receipt['status'] == 'terminal':
                        assert receipt['outcome'] == 'completed'
                        return session, receipt
                    await asyncio.sleep(0.05)

    async def recover(descriptor, session, original):
        for version in ('2.0', '2.1'):
            async with websocket(home, descriptor, proxy=None) as ws:
                await stamp(ws, version)
                restored = await result(ws, 'session.resume', session_id=session)
                assert 'RECOVERY_ACK_' + TEXT in json.dumps(restored['messages'])
                receipt = await result(ws, 'prompt.receipt', session_id=session, admission_id=original['admission_id'])
                assert all(receipt[key] == original[key] for key in ('admission_id', 'status', 'outcome', 'sequence'))

    try:
        with gateway(legacy, 'legacy') as first:
            session, original = asyncio.run(legacy_turn(first))
        before = saved_admission(home)
        payload = json.loads(before[4])
        assert payload['surface_v1']['client'] == {'name': LABEL, 'kind': 'mobile', 'version': '1.0'}
        unlabelled = copy.deepcopy(payload)
        unlabelled['surface_v1'].pop('client')
        original_digest = admission_fingerprint(canonical_target=session, payload={'input': payload, 'intent': before[6]})
        different_digest = admission_fingerprint(canonical_target=session, payload={'input': unlabelled, 'intent': before[6]})
        assert before[0:2] == (original['admission_id'], session)
        assert before[5] == original_digest and original_digest != different_digest
        assert len(peer.requests) == 1
        with gateway(current, 'current') as second:
            assert second['instance_id'] != first['instance_id']
            assert second['authority_epoch'] > first['authority_epoch']
            asyncio.run(recover(second, session, original))
        assert saved_admission(home) == before and len(peer.requests) == 1
    finally:
        peer.shutdown()
        peer.server_close()
        thread.join(timeout=5)
    return {'oldStampWasPartOfDigest': True, 'authenticatedNewConnections': 2,
            'newInferenceCalls': 0, 'retainedAdmissions': 1, 'originalOutcome': 'completed'}


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
    report = run(old, new, evidence)
    verify_source(old, *LEGACY)
    verify_source(new, *current)
    report.update(legacyCommit=LEGACY[0], currentCommit=args.current_commit, sourceTreesVerified=True)
    with (evidence / 'gateway-stamp-result.json').open('x', encoding='utf-8') as stream:
        json.dump(report, stream, indent=2, sort_keys=True)
    print(json.dumps(report, sort_keys=True))


if __name__ == '__main__':
    main()
