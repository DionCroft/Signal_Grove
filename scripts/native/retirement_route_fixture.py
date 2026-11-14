"""Exercise unchanged SDK ASGI retirement routes in a fresh, fenced fixture home.

No server, network listener, lifespan, live login or service operation is started.
The staging controller must verify SDK/application/module hashes, a fresh private
home, write/network boundaries and unchanged live references before and after.
This is an in-process route contract check, not TCP-owner/provider acceptance.
"""
from pathlib import Path
import asyncio
import json
import os
import secrets
import sys


def run(output):
    assert sys.platform == 'linux' and output.is_absolute() and output.is_dir()
    assert not (output / 'client-journal.jsonl').exists() and not (output / 'result.json').exists()
    assert not any(n in sys.modules for n in ['hermes_cli.web_server', 'hermes_cli.backend_retirement'])
    from retirement_guard import Owner, Refused, RetirementGuard

    def observe_owner():
        ticks = Path('/proc/self/stat').read_text().rsplit(')', 1)[1].split()[19]
        boot = Path('/proc/sys/kernel/random/boot_id').read_text().strip()
        return Owner(os.getpid(), int(ticks), boot)

    owner = observe_owner()
    secret = secrets.token_urlsafe(32)
    os.environ['HERMES_DASHBOARD_SESSION_TOKEN'] = secret
    from hermes_cli.web_server import app
    from hermes_cli.backend_retirement import retirement
    import httpx
    assert getattr(app.state, 'auth_required', False) is False
    assert getattr(app.state, 'gateway_runner', None) is None
    assert retirement.active_count() == 0
    requests = []
    permits = []
    journal = output / 'client-journal.jsonl'
    journal.touch(mode=0o600, exist_ok=False)

    def record(event):
        with journal.open('a', encoding='utf8') as stream:
            stream.write(json.dumps(event) + '\n')
            stream.flush()
            os.fsync(stream.fileno())

    async def exercise():
        loop = asyncio.get_running_loop()
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app),
                                     base_url='http://127.0.0.1', follow_redirects=False,
                                     timeout=10.0, trust_env=False) as client:
            async def send(method, path, body, authenticated):
                headers = {'Authorization': 'Bearer ' + secret} if authenticated else {}
                response = await client.request(method, path, json=body, headers=headers)
                assert len(response.content) <= 65536
                data = response.json()
                if isinstance(data.get('token'), str):
                    permits.append(data['token'])
                requests.append(dict(method=method, path=path,
                                     action=body.get('action') if body else None,
                                     authenticated=authenticated, status=response.status_code))
                return response.status_code, data

            def request(method, path, body):
                return asyncio.run_coroutine_threadsafe(send(method, path, body, True), loop).result(timeout=15)

            def unauthenticated(method, path, body):
                return asyncio.run_coroutine_threadsafe(send(method, path, body, False), loop).result(timeout=15)

            def workflow():
                refused = RetirementGuard(owner, unauthenticated, observe_owner, record)
                try:
                    refused.inspect()
                except Refused:
                    pass
                else:
                    raise AssertionError('Unauthenticated SDK request must be refused')
                assert refused.state == 'refused' and len(requests) == 1 and requests[0]['status'] == 401

                busy = RetirementGuard(owner, request, observe_owner, record)
                assert busy.inspect() is True
                assert retirement.acquire() is True
                try:
                    assert busy.prepare() is None and busy.state == 'held'
                finally:
                    retirement.release()

                cancelled = RetirementGuard(owner, request, observe_owner, record)
                assert cancelled.inspect() is True
                permit = cancelled.prepare()
                assert cancelled.state == 'prepared' and retirement.acquire() is False
                cancelled.cancel(permit)
                assert cancelled.state == 'cancelled' and retirement.acquire() is True
                retirement.release()

                committed = RetirementGuard(owner, request, observe_owner, record)
                assert committed.inspect() is True
                permit = committed.prepare()
                assert committed.state == 'prepared' and retirement.acquire() is False
                committed.commit(permit)
                assert committed.state == 'committed' and retirement.acquire() is False
                count = len(requests)
                try:
                    committed.commit(permit)
                except Refused:
                    pass
                else:
                    raise AssertionError('Spent permit must not be replayed')
                assert len(requests) == count == 12 and retirement.active_count() == 0
            await asyncio.to_thread(workflow)

    try:
        asyncio.run(exercise())
        journal_text = journal.read_text(encoding='utf8')
        assert secret not in journal_text and all(token not in journal_text for token in permits)
        assert len(permits) == 2 and len(set(permits)) == 2
        assert [row['action'] for row in requests if row['method'] == 'POST'] == [
            'prepare', 'prepare', 'cancel', 'prepare', 'commit']
        result = dict(state='UNCHANGED_SDK_ASGI_RETIREMENT_GUARD_PASS', owner=dict(pid=owner.pid,
                      startTicks=owner.start_ticks, bootId=owner.boot_id), requests=requests,
                      authentication='isolated loopback deployment-token seam',
                      lifespanStarted=False, networkListenerStarted=False,
                      liveProviderSignInAccepted=False, nativeTcpPinAccepted=False,
                      cases=['unauthenticated-refused', 'real-admission-busy-holds',
                             'prepare-freezes-admission', 'cancel-reopens-admission',
                             'commit-closes-admission', 'spent-permit-not-replayed'],
                      journalContainsCredentials=False, liveActivation=False)
        text = json.dumps(result, indent=2) + '\n'
        assert secret not in text and all(token not in text for token in permits)
        with (output / 'result.json').open('x', encoding='utf8') as stream:
            stream.write(text)
            stream.flush()
            os.fsync(stream.fileno())
        return result
    finally:
        os.environ.pop('HERMES_DASHBOARD_SESSION_TOKEN', None)
        permits.clear()


if __name__ == '__main__':
    run(Path(sys.argv[1]))
