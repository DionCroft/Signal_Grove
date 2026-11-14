"""Full isolated SDK listener/provider/lifespan check; never use a real home.

The reviewed staging controller supplies an immutable SDK source, selected PM
Python and a fresh private fixture directory. This is not a deployment command.
Only generated fixture credentials and an ephemeral loopback listener are used.
No SDK function is patched; profiling retains fixed lifecycle/source metadata.
"""
from pathlib import Path
import base64
import hashlib
import http.cookies
import importlib.util
import json
import os
import re
import secrets
import signal
import socket
import subprocess
import sys
import threading
import time
import traceback
import urllib.parse

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
from retirement_guard import Owner, Refused, RetirementGuard
from native_retirement_owner import LinuxSocketOwner, read_process_owner
from pinned_retirement_http import PinnedRetirementHttp, _ExistingConnection


def child_environment(home):
    h = str(Path(home).resolve())
    return dict(PATH='/usr/bin:/bin', LANG='C.UTF-8', HOME=h + '/account',
                XDG_CONFIG_HOME=h + '/config', XDG_CACHE_HOME=h + '/cache',
                XDG_STATE_HOME=h + '/state', XDG_DATA_HOME=h + '/data',
                TMPDIR=h + '/scratch', TMP=h + '/scratch', TEMP=h + '/scratch',
                HF_HOME=h + '/cache/huggingface', HERMES_HOME=h + '/hermes-home',
                HERMES_SERVE_HEADLESS='1', PYTHONDONTWRITEBYTECODE='1', PYTHONUNBUFFERED='1')


def callback_code(target, expected_uri, expected_state):
    try:
        return _callback_code(target, expected_uri, expected_state)
    except (ValueError, TypeError, AttributeError):
        raise ValueError('Native callback is invalid or mismatched') from None


def _callback_code(target, expected_uri, expected_state):
    if type(target) is not str or len(target) > 4096:
        raise ValueError('Invalid bounded native callback')
    actual, expected = urllib.parse.urlsplit(target), urllib.parse.urlsplit(expected_uri)
    if (expected.scheme != 'http' or expected.hostname != '127.0.0.1'
            or expected.port is None or not 0 < expected.port <= 65535
            or expected.netloc != '127.0.0.1:' + str(expected.port)
            or expected.query or expected.fragment):
        raise ValueError('An exact owner-local callback is required')
    if ((actual.scheme, actual.netloc, actual.path) != (expected.scheme, expected.netloc, expected.path)
            or actual.fragment or actual.username is not None or actual.password is not None):
        raise ValueError('Native callback authority differs')
    values = urllib.parse.parse_qs(actual.query, strict_parsing=True)
    if (set(values) != {'code', 'state'} or values['state'] != [expected_state]
            or len(values['code']) != 1 or not re.fullmatch(r'[A-Za-z0-9_-]{16,256}', values['code'][0])):
        raise ValueError('Native callback state or code is invalid')
    return values['code'][0]


def server_argv():
    return ['hermes', 'serve', '--host', '127.0.0.1', '--port', '0', '--isolated', '--no-open']


def fixture_bind_allowed(family, kind, address):
    return kind == socket.SOCK_STREAM and ((family == socket.AF_INET and address == ('127.0.0.1', 0))
                                          or (family == socket.AF_INET6 and address == ('::1', 0)))


def server(home, source):
    assert sys.platform == 'linux' and sys.flags.isolated and sys.dont_write_bytecode
    home, source = Path(home).resolve(), Path(source).resolve()
    assert home.is_dir() and (home / 'fixture-marker.json').is_file()
    assert os.environ['HERMES_HOME'] == str(home / 'hermes-home') and os.environ['HOME'] == str(home / 'account')
    os.umask(0o077)
    os.chdir(home)
    refusals = []
    trace = home / 'lifecycle.jsonl'
    trace.touch(mode=0o600, exist_ok=False)

    def inside(value, root):
        return isinstance(value, (str, bytes, os.PathLike)) and Path(os.fsdecode(value)).resolve().is_relative_to(root)

    def denied(event, target=None):
        if len(refusals) < 64:
            refusals.append(event)
            (home / 'fence-refusals.json').write_text(json.dumps(refusals) + '\n', encoding='utf8')
            # Do not load source lines while reporting a denied read: linecache
            # would itself open this fixture and recursively invoke the fence.
            callers = []
            frame = sys._getframe(1)
            try:
                for _ in range(6):
                    if frame is None:
                        break
                    callers.append(dict(file=frame.f_code.co_filename, line=frame.f_lineno,
                                        function=frame.f_code.co_name))
                    frame = frame.f_back
            finally:
                del frame
            detail = dict(event=event, target=str(target)[:1024], callers=callers)
            with (home / 'fence-details.jsonl').open('a', encoding='utf8') as stream:
                stream.write(json.dumps(detail) + '\n')
        raise PermissionError('Full SDK fixture boundary refused an operation')

    python_root = Path(sys.base_prefix).resolve()
    venv = Path(sys.prefix).resolve()
    allowed_roots = [home, source, python_root, venv, Path('/usr'), Path('/lib'), Path('/lib64'), Path('/sys'), Path('/etc/ssl/certs')]
    allowed_files = {'/etc/passwd', '/etc/group', '/etc/nsswitch.conf', '/etc/localtime',
                     '/etc/os-release', '/etc/ld.so.cache', '/etc/hosts', '/etc/resolv.conf',
                     '/etc/mime.types', '/etc/protocols', '/etc/services', '/etc/host.conf', '/etc/gai.conf',
                     '/dev/urandom', '/dev/null'}
    # Exact reviewed package metadata in the approved generation, never a
    # general ancestor read. Resolved symlinks outside these names still refuse.
    allowed_files.update({str(source.parent / 'manifest.json'), str(source.parent / 'tools/facts.json')})

    def audit(event, args):
        if event == 'open' and not isinstance(args[0], int):
            mode, flags = args[1:3]
            write = (isinstance(mode, str) and any(c in mode for c in 'wax+')) or (isinstance(flags, int) and flags & (os.O_WRONLY | os.O_RDWR | os.O_CREAT | os.O_TRUNC | os.O_APPEND))
            path = Path(os.fsdecode(args[0])).resolve()
            if write and not inside(path, home):
                denied(event + ':outside-write', path)
            if not write:
                proc = path.is_relative_to('/proc') and (path.name != 'environ' or path.parent.name == str(os.getpid()))
                if not (proc or str(path) in allowed_files or any(path.is_relative_to(root) for root in allowed_roots)):
                    denied(event + ':outside-read', path)
        if event in ('os.mkdir', 'os.remove', 'os.rmdir', 'os.chmod', 'os.rename', 'os.link', 'os.symlink'):
            if event in ('os.rename', 'os.link'):
                values = [(args[0], args[2]), (args[1], args[3])]
            elif event in ('os.mkdir', 'os.chmod', 'os.symlink'):
                values = [(args[1] if event == 'os.symlink' else args[0], args[2])]
            else:
                values = [(args[0], args[1])]
            resolved = []
            for value, descriptor in values:
                if isinstance(value, int):
                    value = os.readlink('/proc/self/fd/' + str(value))
                elif not Path(value).is_absolute() and descriptor not in (-1, None):
                    value = Path(os.readlink('/proc/self/fd/' + str(descriptor))) / value
                resolved.append(value)
            if any(not inside(value, home) for value in resolved):
                denied(event + ':outside-mutation', resolved)
        if event == 'socket.bind' and not fixture_bind_allowed(args[0].family, args[0].type, args[1]):
            denied(event + ':foreign-listener', args[1])
        if event in ('socket.connect', 'subprocess.Popen', 'os.system'):
            denied(event, args[1] if len(args) > 1 else None)
        if event == 'os.kill':
            marker = home / 'stop-authorized.json'
            if (args != (os.getpid(), signal.SIGTERM) or not marker.is_file()
                    or json.loads(marker.read_text())['owner']['pid'] != os.getpid()):
                denied(event, args)

    sys.addaudithook(audit)
    sys.path.insert(0, str(source))
    assert sys.getprofile() is None
    watched = {
        ('hermes_cli/web_server.py', '_on_server_started'),
        ('hermes_cli/web_server_app.py', 'standalone_lifespan'),
        ('hermes_cli/web_server_app.py', '_unlink_pty_markers'),
        ('hermes_cli/local_runtime/bootstrap.py', 'shutdown_local_runtime'),
        ('hermes_cli/backend_retirement.py', 'commit'),
    }
    count = 0

    def source_locations(phase):
        modules = {}
        for name, module in list(sys.modules.items()):
            file = getattr(module, '__file__', None)
            if file:
                path = Path(file).resolve()
                if path.is_relative_to(source):
                    modules[name] = path.relative_to(source).as_posix()
        (home / ('source-' + phase + '.json')).write_text(json.dumps(modules, sort_keys=True) + '\n', encoding='utf8')

    def profile(frame, event, arg):
        nonlocal count
        if event not in ('call', 'return'):
            return
        file = frame.f_code.co_filename
        if not file.startswith(str(source) + '/'):
            return
        key = (file[len(str(source)) + 1:], frame.f_code.co_name)
        if key not in watched:
            return
        count += 1
        assert count <= 64
        with trace.open('a', encoding='utf8') as stream:
            stream.write(json.dumps(dict(event=event, module=key[0], function=key[1], monotonic=time.monotonic())) + '\n')
            stream.flush()
            os.fsync(stream.fileno())
        if event == 'return' and key[1] == 'commit':
            source_locations('commit')
        if event == 'return' and key[1] == 'shutdown_local_runtime':
            source_locations('shutdown')

    sys.setprofile(profile)
    from hermes_cli.main import main
    sys.argv = server_argv()
    main()
    assert not refusals


def run(home, source, python):
    assert sys.platform == 'linux' and sys.flags.isolated and sys.dont_write_bytecode
    home, source = Path(home).resolve(), Path(source).resolve()
    assert not home.exists() and source.is_dir() and Path(python).is_file()
    home.mkdir(mode=0o700)
    for name in ['account', 'config', 'cache', 'state', 'data', 'scratch', 'hermes-home']:
        (home / name).mkdir(mode=0o700)
    os.umask(0o077)
    password, signing = secrets.token_urlsafe(32), secrets.token_urlsafe(32)
    salt = secrets.token_bytes(16)
    digest = hashlib.scrypt(password.encode(), salt=salt, n=2**14, r=8, p=1, dklen=32, maxmem=0)
    password_hash = 'scrypt$16384$8$1$' + base64.b64encode(salt).decode() + '$' + base64.b64encode(digest).decode()
    config = dict(model=dict(provider='custom', default='fixture-no-inference', base_url='http://127.0.0.1:9/v1', api_key='fixture-no-inference'),
                  fallback_model=[], local_runtime=dict(enabled=False),
                  dashboard=dict(public_url='http://signal-grove-fixture.invalid',
                                 basic_auth=dict(username='fixture-operator', password_hash=password_hash, secret=signing)))
    (home / 'hermes-home/config.yaml').write_text(json.dumps(config, indent=2) + '\n', encoding='utf8')
    (home / 'fixture-marker.json').write_text(json.dumps(dict(schema=1, sdkSource=str(source), mode='isolated-provider-lifespan')) + '\n', encoding='utf8')
    record = dict(state='FULL_SDK_CHILD_START_INTENT', liveEndpointUsed=False, liveChanges=False, requests=[], started=time.time())

    def save():
        with (home / 'operation.json').open('w', encoding='utf8') as stream:
            stream.write(json.dumps(record, indent=2) + '\n')
            stream.flush()
            os.fsync(stream.fileno())

    save()
    stdout_path, stderr_path = home / 'server.stdout', home / 'server.stderr'
    with stdout_path.open('xb') as out, stderr_path.open('xb') as err, Path(os.devnull).open('rb') as empty_input:
        proc = subprocess.Popen([str(python), '-I', '-B', str(Path(__file__).resolve()), '--server', str(home), str(source)],
                                cwd=home, env=child_environment(home), stdin=empty_input, stdout=out, stderr=err)
    owner = read_process_owner(proc.pid)
    assert type(owner) is Owner
    record.update(state='FULL_SDK_CHILD_STARTING', childOwner=dict(pid=owner.pid, start_ticks=owner.start_ticks, boot_id=owner.boot_id))
    save()
    sock = None
    adapter = None
    secrets_seen = [password, signing]
    failure = None
    try:
        deadline = time.monotonic() + 60
        port = None
        while time.monotonic() < deadline and proc.poll() is None:
            match = re.findall(r'^HERMES_BACKEND_READY port=([0-9]+)$', stdout_path.read_text(encoding='utf8'), re.M)
            if match:
                assert len(match) == 1
                port = int(match[0])
                break
            time.sleep(.1)
        assert port is not None and 0 < port <= 65535 and read_process_owner(proc.pid) == owner
        record.update(state='FULL_SDK_PROVIDER_FLOW_RUNNING', port=port)
        save()
        sock = socket.create_connection(('127.0.0.1', port), timeout=5)
        native = LinuxSocketOwner(owner)
        deadline = time.monotonic() + 2
        while native(sock) is None and time.monotonic() < deadline:
            time.sleep(.01)
        assert native(sock) == owner
        connection = _ExistingConnection('127.0.0.1', port, timeout=5)
        connection.sock = sock

        def exchange(method, route, body=None, headers=None):
            assert native(sock) == owner and connection.sock is sock
            timer = threading.Timer(5, lambda: sock.shutdown(socket.SHUT_RDWR))
            timer.daemon = True
            timer.start()
            try:
                connection.request(method, route, body=None if body is None else json.dumps(body).encode(),
                                   headers={'Content-Type': 'application/json', 'Accept': 'application/json', **(headers or {})})
                response = connection.getresponse()
                assert response.version == 11 and not response.will_close and not response.chunked
                lengths = response.headers.get_all('Content-Length', [])
                assert len(lengths) == 1 and lengths[0].isdigit() and int(lengths[0]) <= 65536
                raw = response.read(65537)
                assert len(raw) == int(lengths[0]) and native(sock) == owner
                status, response_headers = response.status, list(response.getheaders())
                response.close()
                record['requests'].append(dict(method=method, route=route.split('?', 1)[0], status=status, authenticated='Authorization' in (headers or {})))
                save()
                return status, response_headers, json.loads(raw) if raw else None
            finally:
                timer.cancel()
                timer.join()

        status, _, health = exchange('GET', '/api/health')
        assert status == 200 and health['auth_required'] is True
        assert exchange('GET', '/api/host/identity')[0] == 401
        verifier, state = secrets.token_urlsafe(48), secrets.token_urlsafe(24)
        challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip('=')
        secrets_seen += [verifier]
        with socket.socket() as callback:
            callback.bind(('127.0.0.1', 0))
            callback.listen(1)
            uri = 'http://127.0.0.1:' + str(callback.getsockname()[1]) + '/complete'
            route = '/auth/native/authorize?' + urllib.parse.urlencode(dict(provider='basic', code_challenge=challenge, code_challenge_method='S256', redirect_uri=uri, state=state))
            status, headers, _ = exchange('GET', route)
            assert status == 302
            cookies = http.cookies.SimpleCookie()
            for key, value in headers:
                if key.lower() == 'set-cookie':
                    cookies.load(value)
            assert set(cookies) == {'hermes_session_pkce'}
            cookie = 'hermes_session_pkce=' + cookies['hermes_session_pkce'].coded_value
            status, headers, login = exchange('POST', '/auth/password-login', dict(provider='basic', username='fixture-operator', password=password), {'Cookie': cookie})
            assert status == 200 and login['ok'] is True
            code = callback_code(login['next'], uri, state)
        secrets_seen.append(code)
        status, _, token = exchange('POST', '/auth/native/token', dict(code=code, code_verifier=verifier))
        assert status == 200 and token['provider'] == 'basic' and token['user_id'] == 'fixture-operator' and token['token_type'] == 'Bearer'
        secrets_seen += [token['access_token'], token['refresh_token']]
        assert exchange('POST', '/auth/native/token', dict(code=code, code_verifier=verifier))[0] == 400
        status, _, renewed = exchange('POST', '/auth/native/refresh', dict(provider='basic', refresh_token=token['refresh_token']))
        assert status == 200 and renewed['provider'] == 'basic' and renewed['access_token'] != token['access_token']
        secrets_seen += [renewed['access_token'], renewed['refresh_token']]
        status, _, me = exchange('GET', '/api/auth/me', headers={'Authorization': 'Bearer ' + renewed['access_token']})
        assert status == 200 and me['provider'] == 'basic' and me['user_id'] == 'fixture-operator'
        # Transfer this same fully consumed socket, never connect a successor.
        connection.sock = None
        adapter = PinnedRetirementHttp(owner, sock, renewed['access_token'], native)
        events = []
        with (home / 'retirement-journal.jsonl').open('x', encoding='utf8') as journal:
            def journal_event(event):
                encoded = json.dumps(event, sort_keys=True)
                assert not any(value in encoded for value in secrets_seen)
                journal.write(encoded + '\n')
                journal.flush()
                os.fsync(journal.fileno())
                events.append(event)
            guard = RetirementGuard(owner, adapter, lambda: native(sock), journal_event)
            assert guard.inspect() is True
            permit = guard.prepare()
            assert permit is not None
            guard.commit(permit)
            assert guard.state == 'committed'
            try:
                guard.commit(permit)
                raise AssertionError('Spent SDK permit reached transport')
            except Refused:
                pass
        record.update(state='FULL_SDK_PROVIDER_AND_NATIVE_RETIREMENT_PASS_SHUTDOWN_PENDING', provider='basic',
                      normalPkceLogin=True, oneTimeCodeReplayRefused=True, nativeRefresh=True,
                      nativeOwnerVerified=True, oneSocketAcrossLoginAndRetirement=True,
                      retirementState=guard.state, retirementJournalRows=len(events), retirementExchanges=4)
        save()
    except BaseException as exc:
        failure = type(exc).__name__
        record.update(state='FULL_SDK_FIXTURE_FAILURE_RETAINED_SHUTDOWN_PENDING', errorType=failure,
                      errorFrames=[dict(file=Path(x.filename).name, line=x.lineno, function=x.name) for x in traceback.extract_tb(exc.__traceback__)[-8:]])
        save()
    finally:
        if adapter is not None:
            adapter.close()
        elif sock is not None:
            sock.close()
        if proc.poll() is None:
            assert read_process_owner(proc.pid) == owner
            (home / 'stop-authorized.json').write_text(json.dumps(dict(owner=record['childOwner'], reason='committed' if failure is None else 'owned-fixture-failure')) + '\n', encoding='utf8')
            os.kill(proc.pid, signal.SIGTERM)
            record['sigtermSentOnce'] = True
            save()
            try:
                proc.wait(timeout=12)
            except subprocess.TimeoutExpired:
                record['state'] = 'OWNED_SDK_SHUTDOWN_UNKNOWN_INSPECT_NO_REPLAY'
                save()
                raise
        record['serverExitCode'] = proc.returncode
        record['childEnded'] = read_process_owner(proc.pid) != owner
        save()
    logs = stdout_path.read_text(encoding='utf8') + stderr_path.read_text(encoding='utf8')
    record['credentialValuesAbsentFromLogs'] = not any(value in logs for value in secrets_seen)
    record['fenceRefusals'] = json.loads((home / 'fence-refusals.json').read_text()) if (home / 'fence-refusals.json').exists() else []
    record['pmReadinessWarning'] = 'install out of sync' in logs
    record['state'] = 'FULL_SDK_FIXTURE_TERMINAL_ACCEPTANCE_REVIEW'
    save()
    assert failure is None and proc.returncode == -signal.SIGTERM and record['childEnded']
    if record['fenceRefusals'] or not record['credentialValuesAbsentFromLogs'] or record['pmReadinessWarning']:
        record['state'] = 'FULL_SDK_FIXTURE_TERMINAL_HELD'
        record['holdReasons'] = (["fence_refusal"] if record['fenceRefusals'] else []) + (["credential_in_log"] if not record['credentialValuesAbsentFromLogs'] else []) + (["pm_readiness_warning"] if record['pmReadinessWarning'] else [])
        save()
        raise AssertionError('Full SDK fixture remains held by its recorded acceptance reasons')
    trace = [json.loads(line) for line in (home / 'lifecycle.jsonl').read_text().splitlines()]
    assert sum(x['function'] == 'standalone_lifespan' and x['event'] == 'call' for x in trace) >= 2
    assert any(x['function'] == '_unlink_pty_markers' and x['event'] == 'return' for x in trace)
    assert any(x['function'] == 'shutdown_local_runtime' and x['event'] == 'return' for x in trace)
    last_cleanup = max(i for i,x in enumerate(trace) if x['function'] == 'shutdown_local_runtime' and x['event'] == 'return')
    assert any(x['function'] == 'standalone_lifespan' and x['event'] == 'return' for x in trace[last_cleanup + 1:])
    assert not any(text in logs for text in ['ProgrammingError', 'Cannot operate on a closed database', 'Exception ignored in:', 'Traceback (most recent call last):'])
    record.update(state='FULL_ISOLATED_SDK_PROVIDER_NATIVE_TCP_LIFESPAN_PASS', lifecycleEvents=len(trace),
                  sourceCommitProof=(home / 'source-commit.json').is_file(), sourceShutdownProof=(home / 'source-shutdown.json').is_file(),
                  sdkFunctionsPatched=False, liveUserProviderSessionAccepted=False, finished=time.time())
    assert record['sourceCommitProof'] and record['sourceShutdownProof']
    save()
    return record


if __name__ == '__main__':
    assert len(sys.argv) == 4 and sys.argv[1] == '--server'
    server(Path(sys.argv[2]), Path(sys.argv[3]))
