"""Exercise pinned real Hermes native auth and profile history through its ASGI app.

Windows probe; use an existing Hermes interpreter with -I -S -B, no installation.
Starts the actual dashboard lifespan with upstream synthetic HMAC auth and two
fresh fixture homes. No Android browser, deployed Pi, real IdP or inference claim.
Exact read-only metadata subprocess attempts remain blocked and are recorded.
Python audit hooks bound this fixture but are not an OS security sandbox.
See docs/contracts/2026-11-07-native-auth-history-matrix.md.
"""
from pathlib import Path
import argparse
import hashlib
import ipaddress
import json
import os
import subprocess
import sys
import traceback
import urllib.parse
import weakref

REVISIONS = {'stable-2bd378e': {'commit': '2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19',
                    'blobs': {'hermes_cli/web_server.py': '22ac93e69cae5a8c2a0e045cc9944f8416107e18',
                              'hermes_cli/dashboard_auth/__init__.py': 'f52f683894b4272e85565f8b2778b4b77b170b73',
                              'hermes_cli/dashboard_auth/base.py': 'b8e71461adf30b4814634cb3fbcfdf9b49488e33',
                              'hermes_cli/dashboard_auth/routes.py': '97dfc55f91130da8924ff497b4560d9d5305b00e',
                              'hermes_cli/dashboard_auth/native_flow.py': 'f9483c8bea04c046dab89907108b2a42132bd960',
                              'hermes_cli/dashboard_auth/middleware.py': '5dc4c382ef9d93557c5eb022a9fa5f0737bdbc6a',
                              'hermes_cli/dashboard_auth/cookies.py': 'd6e5bb541594277a99d7e8f3d71fd43b68b717bc',
                              'hermes_cli/dashboard_auth/refresh_singleflight.py': '6d94bd44201237dd56075cd0a1c50e1c12c9428a',
                              'hermes_cli/dashboard_auth/request_utils.py': 'aca6ad79247b6e66fc4dae99de19da1911c96543',
                              'hermes_cli/dashboard_auth/prefix.py': 'c8b86dc8b8e55415c4c7069d7c5993d025c627bb',
                              'hermes_cli/dashboard_auth/public_paths.py': 'a4793ab7e815b584869231b501806070f0485324',
                              'hermes_cli/dashboard_auth/registry.py': 'ae0d377f8a308e4a9550f99817cf59c277e8876a',
                              'tests/hermes_cli/conftest_dashboard_auth.py': 'f06ec93f722b26530b69d81c8fed0edf870a1599',
                              'hermes_cli/version_info.py': '258d67b4df3bb18bedd7c77e2bd539c73043baa1',
                              'hermes_cli/source_check.py': '6d2f267d5fa59f982c142f2abd8c0711b5a2e29c',
                              'hermes_cli/_subprocess_compat.py': 'a2f985af6197eb80ba55b8f45d3c4fea8e3c4eff',
                              'hermes_cli/web_routers/sessions.py': 'df2fe8084547a3c0cfc87ff8e6cc1dd5c496902e',
                              'hermes_cli/web_routers/profiles.py': 'dedfdcb871aa06b621310321301a658871ded80c',
                              'hermes_cli/web_server_sessions.py': '650ba6470402f763314aab7145fcbc8afd8a8a8f',
                              'hermes_cli/web_server_cron.py': 'b777f511a8786d8c7ea788bc93276d58546f6b48',
                              'hermes_cli/profiles.py': '622077d545b3dd322ba9e967a3e8620b0b18e278',
                              'hermes_state.py': '6a7ae615405ee714c0a87f83835408e36bd645ea',
                              'hermes_state_sessions.py': 'f3fc4c2854367bc1a61786406090b71bdccf28f6'}},
 'prior-bf0203a': {'commit': 'bf0203a794a07aac3ff764972ab9087baa4e4f4e',
                   'blobs': {'hermes_cli/web_server.py': '59700ba32c525efcc2b4039401f1b031b6c675c6',
                             'hermes_cli/dashboard_auth/__init__.py': '05941a68bdd3bcdcca49a0fe0eeee9e0018cb840',
                             'hermes_cli/dashboard_auth/base.py': '9e8c7a6b0c9d7b939b92c44cc8a92f497b0bcc86',
                             'hermes_cli/dashboard_auth/routes.py': 'be029a317fb45edfa9d4f66f16c33dff856e7c48',
                             'hermes_cli/dashboard_auth/native_flow.py': 'f9483c8bea04c046dab89907108b2a42132bd960',
                             'hermes_cli/dashboard_auth/middleware.py': '2f360140b2e94df6457d33ba1d9d977463d545ef',
                             'hermes_cli/dashboard_auth/cookies.py': '1a52c8b16db105a514a36b3b38ee14d9c23232de',
                             'hermes_cli/dashboard_auth/refresh_singleflight.py': '6d94bd44201237dd56075cd0a1c50e1c12c9428a',
                             'hermes_cli/dashboard_auth/request_utils.py': '2eca3d145c8cba33e974604e3308a0caa0339922',
                             'hermes_cli/dashboard_auth/prefix.py': '1e61b5a72d5fef542532544890e0b4bd748fac74',
                             'hermes_cli/dashboard_auth/public_paths.py': 'a4793ab7e815b584869231b501806070f0485324',
                             'hermes_cli/dashboard_auth/registry.py': '18687691f8b79e551db1565b613e60fcda8afd35',
                             'tests/hermes_cli/conftest_dashboard_auth.py': 'f06ec93f722b26530b69d81c8fed0edf870a1599',
                             'hermes_cli/version_info.py': 'a4b3b33b9fe7341a7ed6e927afaf24bb391d9988',
                             'hermes_cli/source_check.py': '5a67a576988ccaf49e1b0192de12606232a86a37',
                             'hermes_cli/_subprocess_compat.py': '8ec065c638016983089e6b174c89951e9e2e93a3',
                             'hermes_cli/web_routers/sessions.py': 'd6a5cb947a14855c260754307bcf7698fe520da2',
                             'hermes_cli/web_routers/profiles.py': '8c60f23c4c52117558ebcccf941c27ce86ca3803',
                             'hermes_cli/web_server_sessions.py': '5d2d82cb97003fc87116dd115a821538a845194d',
                             'hermes_cli/web_server_cron.py': '761693b5d8e9a9e64606da49784be7879bfdeb42',
                             'hermes_cli/profiles.py': '08b7eb1057f21f91175dc19074a98c24bd515def',
                             'hermes_state.py': '833f4b738f4698034b7da22276fbcf7bfd3f9768',
                             'hermes_state_sessions.py': 'a072ea5eec5bb9c74491052676e004c16eb2451a'}}}
if not __debug__ or not (sys.flags.isolated and sys.flags.no_site and sys.flags.dont_write_bytecode):
    raise SystemExit('Use -I -S -B without -O; assertions and isolated imports are required.')
if os.name != 'nt':
    raise SystemExit('This bounded probe currently supports Windows only; no cross-host acceptance is claimed.')
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source-root', type=Path, required=True)
parser.add_argument('--work-root', type=Path, required=True)
parser.add_argument('--dependency-site', type=Path, required=True)
parser.add_argument('--revision', choices=tuple(REVISIONS), default='stable-2bd378e')
a = parser.parse_args()
SOURCE_COMMIT = REVISIONS[a.revision]['commit']
PINNED_BLOBS = REVISIONS[a.revision]['blobs']
source, root, site = a.source_root.resolve(), a.work_root.resolve(), a.dependency_site.resolve()
assert source.is_dir() and site.is_dir() and not root.exists(), 'Use existing dependencies and a fresh output root.'
assert source.drive.upper() == root.drive.upper() == 'E:', 'Source and temporary output must stay on E:.'
assert not source.is_relative_to(root) and not root.is_relative_to(source)
assert not root.is_relative_to(site) and not site.is_relative_to(root)
for name, expected in PINNED_BLOBS.items():
    path = source / name
    assert not path.is_symlink() and path.resolve().is_relative_to(source)
    raw = path.read_bytes()
    actual = hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest()
    assert actual == expected, 'Pinned module differs: ' + name
root.mkdir(parents=True, exist_ok=False)
user, temp = root / 'account', root / 'temp'
for path in (user, user / 'AppData/Local', user / 'AppData/Roaming', temp, root / 'hermes-home', root / 'runtime'):
    path.mkdir(parents=True, exist_ok=True)
# Retain only OS loader variables and Git's executable search path for two local
# read-only identity checks. No production Hermes environment or credentials.
git_env = {k: v for k, v in os.environ.items() if k.upper() in ('SYSTEMROOT', 'WINDIR', 'PATH', 'PATHEXT')}
git_env.update(TEMP=str(temp), TMP=str(temp), HOME=str(user), USERPROFILE=str(user),
               GIT_OPTIONAL_LOCKS='0', GIT_NO_LAZY_FETCH='1', GIT_CONFIG_NOSYSTEM='1',
               GIT_CONFIG_GLOBAL=os.devnull, GIT_TERMINAL_PROMPT='0')
def git_read(*args):
    result = subprocess.run(['git', '--no-optional-locks', '-c', 'core.fsmonitor=false',
                             '-C', str(source), *args], env=git_env, capture_output=True,
                            text=True, check=True, timeout=30)
    return result.stdout.strip()
assert git_read('rev-parse', 'HEAD') == SOURCE_COMMIT, 'Wrong source commit.'
assert not git_read('status', '--porcelain', '--untracked-files=all'), 'Source must be clean.'
system = {k: v for k, v in os.environ.items() if k.upper() in ('SYSTEMROOT', 'WINDIR')}
os.environ.clear()
os.environ.update(system)
os.environ.update(TEMP=str(temp), TMP=str(temp), TMPDIR=str(temp), HOME=str(user), USERPROFILE=str(user),
                  APPDATA=str(user / 'AppData/Roaming'), LOCALAPPDATA=str(user / 'AppData/Local'),
                  HERMES_HOME=str(root / 'hermes-home'), HERMES_RUNTIME_DIR=str(root / 'runtime'),
                  HERMES_DISABLE_LAZY_INSTALLS='1', PYTHONDONTWRITEBYTECODE='1', NO_PROXY='127.0.0.1,localhost')
os.environ['HERMES_TEST_ISOLATION'] = '1'
os.environ.update(GIT_CONFIG_NOSYSTEM='1',GIT_CONFIG_GLOBAL=str(root/'empty-git-config'),GIT_OPTIONAL_LOCKS='0',GIT_NO_LAZY_FETCH='1',GIT_TERMINAL_PROMPT='0')
(root/'empty-git-config').write_text('',encoding='utf-8')
os.chdir(root)
sys.dont_write_bytecode = True
sys.path[:0] = [str(source), str(site)]
read_roots = [root, source, site, Path(sys.base_prefix).resolve(), Path(__file__).resolve()]
denied, denial_details, loaded, report = [], [], [], {}

def inside(path, roots):
    return any((path.is_relative_to(x) for x in roots))

def filepath(value):
    return Path(os.fsdecode(value)).resolve() if isinstance(value, (str, bytes, os.PathLike)) else None

def refuse(event, target=None):
    denied.append(event)
    frames = []
    frame = sys._getframe(1)
    for _ in range(12):
        if frame is None:
            break
        file = Path(frame.f_code.co_filename)
        frames.append(dict(module=frame.f_globals.get('__name__', 'unknown'), function=frame.f_code.co_name, line=frame.f_lineno, file=file.relative_to(source).as_posix() if file.is_absolute() and file.is_relative_to(source) else file.name))
        frame = frame.f_back
    if len(denial_details) < 64:
        denial_details.append(dict(event=event, targetName=target.name if target is not None else None, targetPathSha256=hashlib.sha256(str(target).encode()).hexdigest() if target is not None else None, frames=frames))
    raise PermissionError('Native auth fixture boundary: ' + event)

expected_blocked_metadata_probes = []
VERSION_PROBES = {
    ('git', 'rev-parse', 'HEAD'),
    ('git', 'branch', '--show-current'),
    ('git', 'log', '-1', '--format=%ct', 'HEAD'),
    ('git', '--no-optional-locks', 'status', '--porcelain', '-uno'),
    ('git', 'tag', '--merged', 'HEAD', '--list', 'v[0-9]*'),
    ('git', 'describe', '--tags', '--long', '--match', 'v2[0-9][0-9][0-9].*', 'HEAD'),
    ('git', 'rev-parse', '--short=7', 'HEAD'),
}
def audit(event, args):
    if event == 'subprocess.Popen':
        argv, cwd = args[1], args[2]
        frame = sys._getframe(1)
        from_version_info = False
        for _ in range(10):
            if frame is None:
                break
            if frame.f_globals.get('__name__') == 'hermes_cli.version_info' and frame.f_code.co_name in ('_run_git', '_git_version_info'):
                from_version_info = True
            frame = frame.f_back
        version_probe = next((probe for probe in VERSION_PROBES if isinstance(argv, str) and argv == subprocess.list2cmdline(list(probe))), None)
        if isinstance(argv, (list, tuple)) and tuple(argv) in VERSION_PROBES:
            version_probe = tuple(argv)
        if version_probe is not None and cwd is not None and Path(cwd).resolve() == source and from_version_info:
            expected_blocked_metadata_probes.append(list(version_probe))
            raise PermissionError('Version metadata subprocess intentionally blocked in fixture')

        probes={('git','config',scope,'-z','--get-all','safe.directory') for scope in ('--system','--global')}
        source_probes={('git','rev-parse','HEAD'),('git','rev-parse','--abbrev-ref','HEAD'),('git','remote','get-url','origin'),('git','status','--porcelain')}
        words=next((p for p in probes|source_probes if argv==subprocess.list2cmdline(list(p)) or isinstance(argv,(list,tuple)) and tuple(argv)==p),None)
        expected_caller=False
        frame=sys._getframe(1)
        for _ in range(12):
            if frame is None:break
            module=frame.f_globals.get('__name__');function=frame.f_code.co_name
            if words in probes and module=='hermes_cli._subprocess_compat' and function=='_user_safe_directories' and cwd is None:expected_caller=True
            if words in source_probes and module=='hermes_cli.source_check' and function=='_git_run' and cwd is not None and Path(cwd).resolve()==source:expected_caller=True
            frame=frame.f_back
        if words is not None and expected_caller:
            expected_blocked_metadata_probes.append(list(words))
            raise PermissionError('Source metadata subprocess intentionally blocked in fixture')

    if event in ('subprocess.Popen', 'os.system', 'os.kill', 'os.killpg', 'os.exec', 'os.posix_spawn', 'sqlite3.enable_load_extension', 'sqlite3.load_extension', 'socket.sendto', 'socket.sendmsg', 'socket.gethostbyname', 'socket.gethostbyaddr'):
        refuse(event)
    if event == 'socket.getaddrinfo':
        try:
            allowed = ipaddress.ip_address(args[0]).is_loopback
        except ValueError:
            allowed = args[0] == 'localhost'
        if not allowed:
            refuse('nonloopback-resolution')
    if event in ('socket.bind', 'socket.connect'):
        address = args[1]
        if not isinstance(address, tuple) or len(address) < 2:
            refuse('unowned-socket-address')
        try:
            loopback = ipaddress.ip_address(address[0]).is_loopback
        except ValueError:
            loopback = False
        if not loopback:
            refuse('nonloopback-socket')
        if event == 'socket.bind':
            if address[1] != 0:
                refuse('fixed-port-bind')
            bound_sockets.add(args[0])
        else:
            ports = []
            for sock in tuple(bound_sockets):
                try:
                    ports.append(sock.getsockname()[1])
                except OSError:
                    pass
            if address[1] not in ports or address[1] == 0:
                refuse('connect-to-unowned-local-port')
        owned_socket_events.append(event)
    if event == 'open':
        path = filepath(args[0])
        mode = args[1] or ''
        flags = args[2] or 0
        writing = any((x in mode for x in 'wax+')) or bool(flags & (os.O_WRONLY | os.O_RDWR | os.O_CREAT | os.O_TRUNC | os.O_APPEND))
        if not writing and str(args[0]) in missing_os_probes:
            expected = missing_os_probes[str(args[0])]
            if path == expected and path.resolve() == expected and (not path.exists()):
                if len(missing_os_probe_reads) < 128:
                    missing_os_probe_reads.append(str(args[0]))
                return
        if path and str(args[0]).lower() != os.devnull.lower() and (not inside(path, [root] if writing else read_roots)):
            refuse('file-write' if writing else 'file-read', path)
    if event == 'os.chdir' and (not Path(args[0]).resolve().is_relative_to(root)):
        refuse('chdir-outside-fixture')
    if event in ('os.mkdir', 'os.remove', 'os.rmdir', 'os.rename', 'os.symlink', 'os.link'):
        values = args[:2] if event in ('os.rename', 'os.symlink', 'os.link') else args[:1]
        if any(((path := filepath(v)) is not None and (not path.is_relative_to(root)) for v in values)):
            refuse(event)
    if event == 'sqlite3.connect' and str(args[0]) != ':memory:':
        value = str(args[0])
        if value.startswith('file:'):
            uri = urllib.parse.urlsplit(value)
            if uri.netloc not in ('', 'localhost'):
                refuse('sqlite-uri-authority')
            value = urllib.parse.unquote(uri.path)
            if value.startswith('/') and len(value) > 2 and (value[2] == ':'):
                value = value[1:]
        if not Path(value).resolve().is_relative_to(root):
            refuse('sqlite-outside-fixture')
missing_os_probes = {}
assert os.name == 'nt'
for name in ('/proc/1/cgroup', '/proc/self/mountinfo', '/proc/version'):
    target = Path(root.drive + name.replace('/', '\\'))
    assert target.resolve() == target and (not target.exists())
    missing_os_probes[name] = target
missing_manifest = source.parent / 'manifest.json'
assert missing_manifest.resolve() == missing_manifest and (not missing_manifest.exists())
missing_os_probes[str(missing_manifest)] = missing_manifest
missing_os_probe_reads = []
bound_sockets = weakref.WeakSet()
owned_socket_events = []
cases = []
sys.addaudithook(audit)

try:
    import base64,time,urllib.parse
    import hermes_constants as hc
    assert hc.get_hermes_home().resolve()==root/'hermes-home'

    from hermes_state import SessionDB
    homes={'default':root/'hermes-home','worker':root/'hermes-home/profiles/worker'}
    original={}
    for profile,home in homes.items():
        home.mkdir(parents=True,exist_ok=True);(home/'SOUL.md').write_text('Synthetic '+profile+' identity only.',encoding='utf-8')
        scope_token=hc.set_hermes_home_override(str(home))
        try:
            db=SessionDB(db_path=home/'state.db')
            try:
                for sid in ('shared-fixture',profile+'-only-fixture'):
                    db.create_session(sid,source='cli')
                    db.append_message(sid,'user',profile+' private user fixture')
                    db.append_message(sid,'assistant',profile+' private reply fixture')
                original[profile]={sid:(db.get_session(sid),db.get_messages(sid)) for sid in ('shared-fixture',profile+'-only-fixture')}
            finally:db.close()
        finally:hc.reset_hermes_home_override(scope_token)

    from hermes_cli import web_server
    from hermes_cli.dashboard_auth import clear_providers,register_provider,native_flow
    from tests.hermes_cli.conftest_dashboard_auth import StubAuthProvider
    from fastapi.testclient import TestClient
    clear_providers();register_provider(StubAuthProvider());native_flow._reset_for_tests()
    web_server.app.state.bound_host='fixture.invalid';web_server.app.state.bound_port=443;web_server.app.state.auth_required=True
    with TestClient(web_server.app,base_url='https://fixture.invalid',follow_redirects=False,client=('198.51.100.7',50000)) as client:
        # This pilot deliberately starts the actual lifespan, not a copied router.
        reply=client.get('/api/auth/me');assert reply.status_code==401;cases.append('missing_bearer_refused')

        def walk():
            verifier=base64.urlsafe_b64encode(os.urandom(32)).rstrip(b'=').decode()
            challenge=base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b'=').decode()
            reply=client.get('/auth/native/authorize',params=dict(provider='stub',code_challenge=challenge,code_challenge_method='S256',redirect_uri='http://127.0.0.1:53999/callback',state='native-fixture-state'))
            assert reply.status_code==302
            parts=urllib.parse.urlsplit(reply.headers['location'])
            reply=client.get(parts.path+'?'+parts.query);assert reply.status_code==302
            assert 'hermes_session_at' not in reply.headers.get('set-cookie','')
            query=urllib.parse.parse_qs(urllib.parse.urlsplit(reply.headers['location']).query);assert query['state']==['native-fixture-state']
            client.cookies.clear()
            return verifier,challenge,query['code'][0]
        verifier,challenge,code=walk();cases.extend(['real_native_authorize','native_callback_without_access_cookie'])
        reply=client.post('/auth/native/token',json=dict(code=code,code_verifier=verifier));assert reply.status_code==200
        tokens=reply.json();assert tokens['provider']=='stub' and tokens['user_id']=='stub-user-1';assert 'set-cookie' not in reply.headers
        cases.append('real_pkce_exchange')
        reply=client.get('/api/auth/me',headers={'Authorization':'Bearer '+tokens['access_token']});assert reply.status_code==200 and reply.json()['user_id']=='stub-user-1'
        cases.append('cookieless_bearer_identity')
        reply=client.post('/auth/native/refresh',json=dict(refresh_token=tokens['refresh_token'],provider='stub'));assert reply.status_code==200 and reply.json()['user_id']=='stub-user-1'
        cases.append('real_refresh_same_identity')

        # Every protected read uses a fresh explicit bearer header, never browser cookies.
        headers={'Authorization':'Bearer '+tokens['access_token']}
        reply=client.get('/api/profiles',headers=headers);assert reply.status_code==200
        profiles=reply.json()['profiles'];assert {'default','worker'}<={p['name'] for p in profiles};cases.append('real_profile_inventory')
        for profile in ('default','worker'):
            reply=client.get('/api/sessions',params={'profile':profile,'limit':20},headers=headers);assert reply.status_code==200
            rows=reply.json()['sessions'];assert {r['id'] for r in rows}=={'shared-fixture',profile+'-only-fixture'}
            assert all(row['profile']==profile for row in rows);cases.append(profile+'_scoped_history_inventory')
            reply=client.get('/api/sessions/shared-fixture/messages',params={'profile':profile,'limit':20},headers=headers);assert reply.status_code==200
            data=reply.json();assert data['profile']==profile and data['session_id']=='shared-fixture'
            assert [row['content'] for row in data['messages']]==[profile+' private user fixture',profile+' private reply fixture'];cases.append(profile+'_same_id_transcript_isolated')
            other='worker' if profile=='default' else 'default'
            reply=client.get('/api/sessions/'+other+'-only-fixture/messages',params={'profile':profile,'limit':20},headers=headers);assert reply.status_code==404
            cases.append(profile+'_foreign_only_id_refused')
        absent=root/'hermes-home/profiles/missing-fixture';assert not absent.exists()
        reply=client.get('/api/sessions',params={'profile':'missing-fixture'},headers=headers);assert reply.status_code==404 and not absent.exists();cases.append('missing_profile_not_created')
        reply=client.get('/api/auth/me',headers={'Authorization':'Bearer invalid-fixture-token'});assert reply.status_code==401 and 'user_id' not in reply.json();cases.append('invalid_bearer_refused_without_identity')
        params=dict(provider='stub',code_challenge=challenge,code_challenge_method='S256',redirect_uri='https://outside.example/callback',state='fixture')
        reply=client.get('/auth/native/authorize',params=params);assert reply.status_code==400;cases.append('nonloopback_redirect_refused_before_navigation')
        params.update(redirect_uri='http://127.0.0.1:53999/callback',code_challenge_method='plain')
        reply=client.get('/auth/native/authorize',params=params);assert reply.status_code==400;cases.append('plain_pkce_refused')
        reply=client.post('/auth/native/token',json=dict(code=code,code_verifier=verifier));assert reply.status_code==400;replay_error=reply.json();cases.append('used_code_replay_refused')
        good,_,wrong_code=walk()
        reply=client.post('/auth/native/token',json=dict(code=wrong_code,code_verifier='wrong-fixture-verifier'));assert reply.status_code==400 and reply.json()==replay_error
        reply=client.post('/auth/native/token',json=dict(code=wrong_code,code_verifier=good));assert reply.status_code==400 and reply.json()==replay_error;cases.append('wrong_verifier_consumes_code_without_oracle')
        reply=client.post('/auth/native/refresh',json=dict(refresh_token='invalid-fixture-refresh',provider='stub'));assert reply.status_code==401 and reply.json()['error']=='session_expired';cases.append('invalid_refresh_requires_signin')
    for profile,home in homes.items():
        scope_token=hc.set_hermes_home_override(str(home))
        try:
            db=SessionDB(db_path=home/'state.db',read_only=True)
            try:after={sid:(db.get_session(sid),db.get_messages(sid)) for sid in ('shared-fixture',profile+'-only-fixture')}
            finally:db.close()
        finally:hc.reset_hermes_home_override(scope_token)
        assert after==original[profile]
    cases.append('browsing_preserves_exact_seeded_sessions_and_messages')

    assert not denied,denied
    report.update(state='PASS_REAL_DASHBOARD_NATIVE_AUTH_AND_SCOPED_HISTORY')
except BaseException as error:
    report.update(state='FAILED_INSPECT_EXACT_PILOT',errorType=type(error).__name__)
    (root/'traceback-private.txt').write_text(traceback.format_exc(),encoding='utf-8')
finally:
    modules={}
    for name,module in list(sys.modules.items()):
        value=getattr(module,'__file__',None)
        if value:
            path=Path(value).resolve()
            if path.is_relative_to(source) and path.is_file():modules[path.relative_to(source).as_posix()]=hashlib.sha256(path.read_bytes()).hexdigest()
    report.update(revision=a.revision,sourceCommit=SOURCE_COMMIT,cases=cases,projectModules=modules,deniedEvents=denied,denialDetails=denial_details,expectedBlockedMetadataProbes=expected_blocked_metadata_probes,missingOsProbeReads=missing_os_probe_reads,ownedSocketEvents=owned_socket_events,scope='Actual dashboard ASGI app/lifespan/auth gate, native broker and profile history stores with upstream synthetic HMAC provider; isolated fixture only. Not native Android, deployed Pi, real external IdP or inference acceptance. Audit hooks are not an OS sandbox.')
    (root/'result.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(dict(state=report['state'],cases=cases,modules=len(modules),deniedEvents=denied,errorType=report.get('errorType'))))
    if report['state'].startswith('FAILED'):raise SystemExit(1)
