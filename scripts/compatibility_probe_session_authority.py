"""Exercise pinned real Hermes authority, canonical dispatch and attach-first replay fixtures.

Windows component probe; run with an existing Hermes interpreter using -I -S -B.
No dependency installation, site hooks, gateway startup, inference or owner data.
Python audit hooks bound this probe but do not constitute an OS security sandbox.
See docs/contracts/2026-11-07-canonical-authority-component.md for scope and invocation.
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
                    'blobs': {'gateway/session_authority.py': '052252b70edc38b71b6a8679f91660d6009893aa',
                              'gateway/owner_identity.py': 'bffdf405527e53f3bd1b608c88d609b1dab34803',
                              'gateway/session_local_migration.py': '82a647465420180829beeb081a6271bcdc48272f',
                              'gateway/session_worker_reservation.py': '421bb89109330bd5eaf8f7ac29d77dcda1ce5554',
                              'tests/gateway/test_local_authority_transitions.py': '8e0e58485a882c7ee90bb204e7eded9ee81f6da8',
                              'tests/gateway/test_canonical_wire_contracts.py': '78364e99862de463129325a67c8ae6e0880a0a62',
                              'gateway/session_controls.py': '3e4a89ad890cacf9645b648369d3daa682c1e114',
                              'gateway/session_events.py': '5aa8636025d83497b23dcad7cc24bfd0c9ca911d',
                              'tui_gateway/contracts/canonical_sessions.py': '642364a17f0ec8a12c66c285d76064996b604f10',
                              'hermes_state_runtime.py': '49207f5508db3255cd90433a229c518f11cfd0f7'}},
 'prior-bf0203a': {'commit': 'bf0203a794a07aac3ff764972ab9087baa4e4f4e',
                   'blobs': {'gateway/session_authority.py': '3bc6c596c77c495c1cf318495a84319c211dd523',
                             'gateway/session_controls.py': '65887575dec12b3ddb9f859ebe56156264ea120e',
                             'gateway/session_events.py': 'f669acfb44e3acc17058e67981b4cf8569478451',
                             'gateway/session_local_migration.py': '484be549e64250e509bbaabe80b6ef9cc499c18e',
                             'tui_gateway/contracts/canonical_sessions.py': 'db394b039bca3a75a9ed4e06e0fcc7a121c3ae33',
                             'hermes_state_runtime.py': '516e3ea46152226e320dc0b6236e6af0b2a00ccb'}}}
if not __debug__ or not (sys.flags.isolated and sys.flags.no_site and sys.flags.dont_write_bytecode):
    raise SystemExit('Use -I -S -B without -O; assertions and isolated imports are required.')
if os.name != 'nt':
    raise SystemExit('This bounded probe currently supports Windows only; no cross-host acceptance is claimed.')
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source-root', type=Path, required=True)
parser.add_argument('--work-root', type=Path, required=True)
parser.add_argument('--dependency-site', type=Path, required=True)
parser.add_argument('--revision', choices=tuple(REVISIONS), default='stable-2bd378e')
parser.add_argument('--flow', choices=('authority-wire', 'attach-first'), default='authority-wire')
a = parser.parse_args()
if a.flow == 'authority-wire' and a.revision != 'stable-2bd378e':
    parser.error('The full authority lifecycle flow requires the stable revision; use attach-first for the prior source.')
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
    raise PermissionError('Canonical fixture boundary: ' + event)

expected_blocked_version_probes = []
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
        if a.revision == 'prior-bf0203a' and version_probe is not None and cwd is not None and Path(cwd).resolve() == source and from_version_info:
            expected_blocked_version_probes.append(list(version_probe))
            raise PermissionError('Version metadata subprocess intentionally blocked in fixture')
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
    import hermes_constants as hc
    assert hc.get_hermes_home().resolve() == root / 'hermes-home' and hc.get_default_hermes_root().resolve() == root / 'hermes-home'
    from hermes_state import SessionDB
    from hermes_state_runtime import begin_runtime_epoch, RuntimeStoreError
    from gateway.session_authority import SessionAuthority, LiveSession
    from gateway.session_controls import AuthorityConnection
    if a.revision == 'prior-bf0203a':
        from gateway.session_contract import Principal as SessionActor, SessionRef
    else:
        from gateway.session_contract import SessionActor, SessionRef
    from gateway.config import Platform
    from hermes_state_runtime import admit_session_input, claim_session_input, recover_session_inputs, get_session_admission
    from types import SimpleNamespace
    import asyncio

    class Sink:

        def __init__(self):
            import threading
            self.frames = []
            self.changed = threading.Condition()

        def write(self, frame):
            with self.changed:
                self.frames.append(frame)
                self.changed.notify_all()
            return True

        def wait_for_count(self, count, timeout):
            with self.changed:
                return self.changed.wait_for(lambda: len(self.frames) >= count, timeout)

        def close(self):
            pass

    def passed(name):
        assert name not in cases
        cases.append(name)

    def refused_call(function, reason):
        try:
            function()
        except RuntimeStoreError as error:
            assert error.reason == reason, (error.reason, reason)
            return error
        raise AssertionError('Expected refusal: ' + reason)

    async def refused_await(coroutine, reason):
        try:
            await coroutine
        except RuntimeStoreError as error:
            assert error.reason == reason, (error.reason, reason)
            return
        raise AssertionError('Expected refusal: ' + reason)

    async def wire_behavior(authority, db):
        from tui_gateway.contracts.events import StatusUpdatePayload
        from tui_gateway.contracts import registry
        assert registry.STRICT is True, "Official strict result validation must be active."
        sinks = [Sink(), Sink(), Sink()]
        identity = {'provider': 'local', 'user_id': 'wire-viewer', 'capabilities': ['session:read', 'session:operator']}
        first = AuthorityConnection(authority, sinks[0], identity)
        second = AuthorityConnection(authority, sinks[1], identity)
        third = AuthorityConnection(authority, sinks[2], identity)
        empty = AuthorityConnection(authority, Sink(), dict(identity, user_id=''))
        peers = [first, second, third, empty]
        authority.sessions['private'] = LiveSession(SimpleNamespace(platform=Platform.LOCAL, user_id='other-owner'), 'fixture-private')
        serial = 0
        async def rpc(peer, method, params):
            nonlocal serial
            serial += 1
            frame = {'jsonrpc': '2.0', 'id': 'wire-' + str(serial), 'method': method, 'params': params}
            response = await peer.dispatch(frame)
            assert response['jsonrpc'] == '2.0' and response['id'] == frame['id']
            return response
        def success(response):
            assert 'error' not in response and 'result' in response, response
            return response['result']
        def refusal(response, reason, code=4001):
            assert response['error']['code'] == code and response['error']['data']['reason'] == reason, response
            return response['error']
        async def drain_notices():
            tasks = [task for peer in peers for task in tuple(getattr(peer, '_notice_tasks', ()))]
            if tasks:
                await asyncio.gather(*tasks)
        try:
            assert success(await rpc(first, 'ping', {})) == {'pong': True}
            passed('wire_ping_echoes_request_id')
            refusal(await rpc(first, 'ping', ['bad']), 'invalid_params')
            passed('wire_non_mapping_params_refused')
            refusal(await rpc(first, 'fixture.unknown', {}), 'unknown_method', -32601)
            passed('wire_unknown_method_has_protocol_error')
            refusal(await rpc(first, 'ping', {'profile': 'other'}), 'profile_mismatch')
            passed('wire_foreign_profile_refused')
            missing = refusal(await rpc(first, 'session.mutate', {'session_id': 's'}), 'invalid_params')
            assert missing['data']['fields']
            passed('wire_missing_mutation_fields_refused_before_handler')
            mutation = {'session_id': 's', 'request_id': 'fixture-wire-rename', 'expected_revision': 0, 'operation': 'rename', 'payload': {'title': 'Never written'}}
            extra = refusal(await rpc(first, 'session.mutate', dict(mutation, actor='forged')), 'invalid_params')
            assert extra['data']['fields']
            passed('wire_unknown_mutation_fields_refused_before_handler')
            refusal(await rpc(empty, 'ping', {}), 'permission_denied')
            passed('wire_missing_user_revokes_supplied_capabilities')
            description = success(await rpc(first, 'runtime.describe', {}))
            assert description['instance_id'] == authority.instance_id and description['profile_id'] == authority.profile_id and description['authority_epoch'] == authority.epoch
            assert 'durable-admission-v1' in description['capabilities'] and 'session:control' not in first.actor.capabilities and 'session:operator' not in first.actor.capabilities
            passed('wire_runtime_features_do_not_grant_actor_rights')
            refusal(await rpc(first, 'session.mutate', mutation), 'permission_denied')
            passed('wire_read_only_viewer_cannot_rename')
            refusal(await rpc(first, 'session.info', {'session_id': 'private'}), 'permission_denied')
            passed('wire_foreign_local_history_owner_refused')
            snapshot = success(await rpc(first, 'session.resume', {'session_id': 's'}))
            await drain_notices()
            assert snapshot['session_id'] == snapshot['stored_session_id'] == 's' and snapshot['authority_epoch'] == authority.epoch and snapshot['execution_generation'] == 1 and snapshot['revision'] == 0
            assert [(row['role'], row['content']) for row in snapshot['messages']] == [('user', 'fixture user'), ('assistant', 'fixture assistant')]
            assert snapshot['message_count'] == 2 and snapshot['info']['desktop_protocol'] == 'hermes-gateway-v1'
            subscription = snapshot['subscription_id']
            passed('wire_resume_preserves_history_and_binds_snapshot')
            again = success(await rpc(first, 'session.resume', {'session_id': 's'}))
            await drain_notices()
            assert again['subscription_id'] == subscription and len(authority.sessions['s'].subscribers) == 1
            passed('wire_repeated_resume_reuses_own_subscription')
            bad_epoch = success(await rpc(first, 'session.events.since', {'session_id': 's', 'replay_epoch': 'foreign-epoch', 'last_sequence': 0}))
            assert bad_epoch['snapshot_required'] is True and bad_epoch['events'] == []
            passed('wire_foreign_replay_epoch_requires_snapshot')
            replay_epoch = snapshot['replay_epoch']
            empty_replay = success(await rpc(first, 'session.events.since', {'session_id': 's', 'replay_epoch': replay_epoch, 'last_sequence': 0}))
            assert empty_replay['snapshot_required'] is False and empty_replay['count'] == 0 and empty_replay['events'] == []
            passed('wire_matching_empty_replay_does_not_require_snapshot')
            refusal(await rpc(first, 'session.events.since', {'session_id': 's', 'replay_epoch': replay_epoch, 'last_sequence': True}), 'invalid_params')
            passed('wire_boolean_replay_cursor_refused')
            detached = success(await rpc(second, 'session.detach', {'session_id': 's', 'subscription_id': subscription}))
            assert detached['detached'] is False and subscription in authority.sessions['s'].subscribers
            passed('wire_foreign_detach_does_not_remove_membership')
            detached = success(await rpc(first, 'session.detach', {'session_id': 's', 'subscription_id': subscription}))
            assert detached['detached'] is True and not authority.sessions['s'].subscribers
            passed('wire_own_detach_removes_only_own_subscription')
            stream = authority.sessions['s'].event_stream
            def publish(text):
                payload = {'kind': 'status', 'text': text}
                StatusUpdatePayload.model_validate(payload)
                return stream.publish('s', payload, event_type='status.update')
            assert publish('fixture event one') is False
            replay = success(await rpc(third, 'session.events.since', {'session_id': 's', 'replay_epoch': replay_epoch, 'last_sequence': 0}))
            assert replay['snapshot_required'] is False and replay['count'] == 1 and replay['events'][0]['session_id'] == 's'
            assert replay['events'][0]['payload']['text'] == 'fixture event one' and replay['events'][0]['seq'] == 1
            passed('wire_replay_uses_public_session_and_exact_cursor')
            assert publish('fixture event two') is False
            resumed = success(await rpc(third, 'session.resume', {'session_id': 's'}))
            await drain_notices()
            assert resumed['subscription_id'] != subscription and resumed['last_sequence'] == 2
            passed('wire_reconnected_transport_gets_new_subscription')
            assert len(sinks[2].frames) == 1 and sinks[2].frames[0]['params']['seq'] == 2 and sinks[2].frames[0]['params']['payload']['text'] == 'fixture event two'
            passed('wire_resume_catches_gap_without_replaying_answered_event')
            from hermes_state_runtime import list_session_admissions
            admissions = list_session_admissions(db, session_id='s', pending_only=False)
            assert len(admissions) == 1 and admissions[0]['status'] == 'unknown' and admissions[0]['request_id'] == 'fixture-request'
            assert [(row['role'], row['content']) for row in db.get_messages('s')] == [('user', 'fixture user'), ('assistant', 'fixture assistant')]
            passed('wire_reconnect_does_not_replay_unknown_admission')
            await third.close()
            assert not authority.sessions['s'].subscribers and third.actor.transport_id not in authority.events
            passed('wire_close_releases_subscription_and_transport')
        finally:
            await drain_notices()
            for peer in peers:
                await peer.close()

    async def attach_first_wire_behavior(authority, db):
        from tui_gateway.contracts.events import StatusUpdatePayload
        from tui_gateway.contracts import registry
        assert registry.STRICT is True
        sinks = [Sink(), Sink()]
        identity = {'provider': 'local', 'user_id': 'wire-viewer', 'capabilities': ['session:read']}
        peers = [AuthorityConnection(authority, sink, identity) for sink in sinks]
        serial = 0

        async def rpc(peer, method, params):
            nonlocal serial
            serial += 1
            request = {'jsonrpc': '2.0', 'id': str(serial), 'method': method, 'params': params}
            reply = await peer.dispatch(request)
            assert reply['id'] == request['id'] and reply.get('jsonrpc') == '2.0' and ('error' not in reply), reply
            return reply['result']

        async def resume(peer):
            snapshot = await rpc(peer, 'session.resume', {'session_id': 's'})
            tasks = list(getattr(peer, '_notice_tasks', ()))
            if tasks:
                await asyncio.gather(*tasks)
            return snapshot
        stream = authority.sessions['s'].event_stream

        def publish(index):
            payload = {'kind': 'status', 'text': 'fixture event ' + str(index)}
            StatusUpdatePayload.model_validate(payload)
            return stream.publish('s', payload, event_type='status.update')
        try:
            first = await resume(peers[0])
            assert first['session_id'] == first['stored_session_id'] == 's' and first['authority_epoch'] == authority.epoch and (first['execution_generation'] == 1) and (first['revision'] == 0)
            assert first['last_sequence'] == 0 and first['message_count'] == 2
            epoch = first['replay_epoch']
            passed('attach_first_snapshot_binds_session_epoch_revision_generation')
            assert publish(1) is True
            assert await asyncio.to_thread(sinks[0].wait_for_count, 1, 3), 'Actual fanout did not deliver event1 within3 seconds'
            assert [f['params']['seq'] for f in sinks[0].frames] == [1]
            passed('attached_viewer_receives_initial_live_event')
            await peers[0].close()
            assert publish(2) is False and len(sinks[0].frames) == 1
            passed('detached_event_is_not_mistaken_for_live_delivery')
            second = await resume(peers[1])
            assert second['subscription_id'] != first['subscription_id'] and second['replay_epoch'] == epoch and (second['last_sequence'] == 2)
            passed('fresh_transport_attaches_before_requesting_replay')
            assert publish(3) is True
            assert await asyncio.to_thread(sinks[1].wait_for_count, 1, 3), 'Actual fanout did not deliver event3 within3 seconds'
            assert [f['params']['seq'] for f in sinks[1].frames] == [3]
            passed('post_attach_pre_replay_event_is_buffered_live')
            replay = await rpc(peers[1], 'session.events.since', {'session_id': 's', 'replay_epoch': epoch, 'last_sequence': 1})
            assert replay['snapshot_required'] is False and [f['seq'] for f in replay['events']] == [2, 3]
            assert all((f['session_id'] == 's' and f['replay_epoch'] == epoch for f in replay['events']))
            passed('replay_after_attach_recovers_detached_and_racing_events')
            assert publish(4) is True
            assert await asyncio.to_thread(sinks[1].wait_for_count, 2, 3), 'Actual fanout did not deliver event4 within3 seconds'
            merged = {}
            for frame in replay['events'] + [f['params'] for f in sinks[1].frames]:
                assert frame['session_id'] == 's' and frame['replay_epoch'] == epoch
                sequence = frame['seq']
                assert sequence not in merged or merged[sequence] == frame
                merged[sequence] = frame
            assert sorted(merged) == [2, 3, 4] and [merged[n]['payload']['text'] for n in sorted(merged)] == ['fixture event 2', 'fixture event 3', 'fixture event 4']
            passed('replay_and_live_frames_merge_contiguously_without_duplicates')
            tail = await rpc(peers[1], 'session.events.since', {'session_id': 's', 'replay_epoch': epoch, 'last_sequence': 3})
            assert tail['snapshot_required'] is False and [f['seq'] for f in tail['events']] == [4]
            passed('advanced_cursor_replays_only_new_tail')
            from hermes_state_runtime import list_session_admissions
            admissions = list_session_admissions(db, session_id='s', pending_only=False)
            assert len(admissions) == 1 and admissions[0]['status'] == 'unknown'
            assert [(row['role'], row['content']) for row in db.get_messages('s')] == [('user', 'fixture user'), ('assistant', 'fixture assistant')]
            passed('attach_first_flow_never_replays_unknown_admission')
            await peers[1].close()
            assert not authority.sessions['s'].subscribers and all((peer.actor.transport_id not in authority.events for peer in peers))
            passed('attach_first_teardown_releases_own_registration')
            (root / 'attach-first-observation.json').write_text(json.dumps(dict(source=SOURCE_COMMIT, reconnectCursor=1, replaySequences=[f['seq'] for f in replay['events']], liveSequences=[f['params']['seq'] for f in sinks[1].frames], mergedSequences=sorted(merged), unknownAdmissionPreserved=True, strictWireSchemas=True, expectedBlockedVersionProbes=len(expected_blocked_version_probes)), indent=2) + '\n')
        finally:
            for peer in peers:
                await peer.close()

    async def attach_first_fixture():
        db = SessionDB(db_path=root / 'hermes-home/authority.sqlite')
        try:
            db.create_session('s', source='test')
            db.append_message('s', 'user', 'fixture user')
            db.append_message('s', 'assistant', 'fixture assistant')
            db.create_session('private', source='test')
            epoch = begin_runtime_epoch(db, instance_id='fixture-instance')
            admission = admit_session_input(db, epoch=epoch, principal_id='fixture-owner', session_id='s', request_id='fixture-request', payload={'text': 'fixture admission only'})
            claim = claim_session_input(db, epoch=epoch, session_id='s')
            assert claim['admission_id'] == admission['admission_id'] and claim['status'] == 'started'
            passed('fixture_real_admission_claimed_without_producer')
            next_epoch = begin_runtime_epoch(db, instance_id='replacement-instance')
            assert recover_session_inputs(db, epoch=next_epoch) == 1
            assert get_session_admission(db, admission_id=admission['admission_id'])['status'] == 'unknown'
            passed('fixture_claim_recovers_unknown_in_new_epoch')
            runner = SimpleNamespace(_draining=False, _cached_agent_for=lambda route: None, adapters={})
            authority = SessionAuthority(runner, profile_id='fixture', instance_id='replacement-instance', db=db, epoch=next_epoch)
            authority.sessions['s'] = LiveSession(None, 'fixture-route')
            await attach_first_wire_behavior(authority, db)
            assert [(row['role'], row['content']) for row in db.get_messages('s')] == [('user', 'fixture user'), ('assistant', 'fixture assistant')]
            passed('fixture_transcript_unchanged_after_wire_checks')
        finally:
            db.close()

    async def behavior():
        db = SessionDB(db_path=root / 'hermes-home/authority.sqlite')
        connections = []
        try:
            db.create_session('s', source='test')
            db.append_message('s', 'user', 'fixture user')
            db.append_message('s', 'assistant', 'fixture assistant')
            history = lambda: [(row['role'], row['content']) for row in db.get_messages('s')]
            assert history() == [('user', 'fixture user'), ('assistant', 'fixture assistant')]
            passed('real_sqlite_transcript_preserved')
            epoch = begin_runtime_epoch(db, instance_id='fixture-instance')
            runner = SimpleNamespace(_draining=False, _cached_agent_for=lambda route: None, adapters={})
            authority = SessionAuthority(runner, profile_id='fixture', instance_id='fixture-instance', db=db, epoch=epoch)
            authority.sessions['s'] = LiveSession(None, 'fixture-route')
            actor = SessionActor('fixture-owner', 'fixture', frozenset({'session:read', 'session:submit'}), 'viewer-one')
            ref = SessionRef('fixture', 's')
            handle = await authority.resolve(actor, ref)
            assert handle.ref == ref and handle.instance_id == 'fixture-instance' and (handle.authority_epoch == epoch) and (handle.revision == 0) and (handle.execution_generation == 0) and (handle.execution_state == 'idle')
            passed('read_handle_binds_instance_profile_epoch_revision_generation')
            await refused_await(authority.resolve(SessionActor(actor.subject, 'other', actor.capabilities, 'other'), ref), 'profile_mismatch')
            passed('foreign_actor_profile_refused')
            await refused_await(authority.resolve(actor, SessionRef('other', 's')), 'profile_mismatch')
            passed('foreign_reference_profile_refused')
            await refused_await(authority.resolve(SessionActor(actor.subject, 'fixture', frozenset(), 'no-cap'), ref), 'permission_denied')
            passed('missing_read_capability_refused')
            await refused_await(authority.resolve(actor, SessionRef('fixture', 'missing')), 'not_found')
            assert db.get_session('missing') is None
            passed('unknown_session_does_not_create_a_row')
            db.create_session('private', source='test')
            authority.sessions['private'] = LiveSession(SimpleNamespace(platform=Platform.LOCAL, user_id='other-owner'), 'fixture-private')
            await refused_await(authority.resolve(actor, SessionRef('fixture', 'private')), 'permission_denied')
            passed('foreign_local_owner_refused')
            own = SessionActor('other-owner', 'fixture', actor.capabilities, 'owner-viewer')
            assert (await authority.resolve(own, SessionRef('fixture', 'private'))).ref.session_id == 'private'
            passed('matching_local_owner_can_read')
            identity = {'provider': 'local', 'user_id': 'fixture-native', 'capabilities': ['session:read', 'session:operator']}
            unverified = AuthorityConnection(authority, Sink(), identity)
            connections.append(unverified)
            assert 'session:operator' not in unverified.actor.capabilities
            await refused_await(authority.resolve(unverified.actor, SessionRef('fixture', 'private')), 'permission_denied')
            passed('identity_data_cannot_grant_operator')
            verified = AuthorityConnection(authority, Sink(), identity, operator=True)
            connections.append(verified)
            assert 'session:operator' in verified.actor.capabilities and (await authority.resolve(verified.actor, SessionRef('fixture', 'private'))).ref.session_id == 'private'
            passed('explicit_trusted_constructor_operator_can_read')
            authority.retired = True
            try:
                await refused_await(authority.resolve(actor, ref), 'runtime_retired')
            finally:
                authority.retired = False
            passed('retired_authority_refused')
            sink1 = Sink()
            sink2 = Sink()
            authority.events[actor.transport_id] = sink1
            authority.pending_closes['s'] = 'fixture pending close'
            timer = asyncio.get_running_loop().call_later(3600, lambda: None)
            authority.orphan_closes['s'] = timer
            first = await authority.attach(actor, ref)
            assert [(row['role'], row['content']) for row in first.history] == history() and timer.cancelled() and ('s' not in authority.pending_closes) and ('s' not in authority.orphan_closes)
            passed('attach_preserves_history_and_cancels_pending_close')
            again = await authority.attach(actor, ref)
            assert again.subscription_id == first.subscription_id and len(authority.sessions['s'].subscribers) == 1
            passed('repeated_attach_reuses_own_subscription')
            actor2 = SessionActor(actor.subject, 'fixture', actor.capabilities, 'viewer-two')
            authority.events[actor2.transport_id] = sink2
            second = await authority.attach(actor2, ref)
            assert second.subscription_id != first.subscription_id and len(authority.sessions['s'].subscribers) == 2
            passed('another_transport_gets_distinct_subscription')
            await refused_await(authority.detach(actor2, first.subscription_id), 'permission_denied')
            assert len(authority.sessions['s'].subscribers) == 2
            passed('foreign_subscription_detach_refused')
            await authority.detach(actor, first.subscription_id)
            assert len(authority.sessions['s'].subscribers) == 1 and authority.sessions['s'].event_stream.fanout.contains(sink2) and (not authority.sessions['s'].event_stream.fanout.contains(sink1))
            passed('own_detach_preserves_other_viewer')
            await authority.detach(actor2, second.subscription_id)
            assert not authority.sessions['s'].subscribers and (not authority.sessions['s'].event_stream.fanout.contains(sink2))
            passed('last_plain_detach_removes_own_fanout')
            authority.authorize(actor, ref, 'session:submit')
            one = admit_session_input(db, epoch=epoch, principal_id=actor.subject, session_id='s', request_id='fixture-request', payload={'text': 'fixture admission only'})
            two = admit_session_input(db, epoch=epoch, principal_id=actor.subject, session_id='s', request_id='fixture-request', payload={'text': 'fixture admission only'})
            assert one['admission_id'] == two['admission_id'] and one['status'] == two['status'] == 'queued'
            passed('exact_queued_replay_reuses_receipt')
            error = refused_call(lambda: admit_session_input(db, epoch=epoch, principal_id=actor.subject, session_id='s', request_id='fixture-request', payload={'text': 'changed'}), 'admission_conflict')
            assert error.field == 'request_id'
            passed('changed_payload_under_same_key_refused')
            claimed = claim_session_input(db, epoch=epoch, session_id='s')
            assert claimed['admission_id'] == one['admission_id'] and claimed['status'] == 'started'
            authority.retired = True
            next_epoch = begin_runtime_epoch(db, instance_id='replacement-instance')
            assert recover_session_inputs(db, epoch=next_epoch) == 1
            recovered = get_session_admission(db, admission_id=one['admission_id'])
            assert recovered['status'] == 'unknown' and recovered['generation'] == claimed['generation']
            passed('claimed_work_recovers_unknown_without_replay')
            refused_call(lambda: admit_session_input(db, epoch=epoch, principal_id=actor.subject, session_id='s', request_id='stale', payload={'text': 'never admitted'}), 'stale_epoch')
            passed('stale_epoch_cannot_admit')
            replacement = SessionAuthority(runner, profile_id='fixture', instance_id='replacement-instance', db=db, epoch=next_epoch)
            replacement.sessions['s'] = LiveSession(None, 'fixture-route')
            new_handle = await replacement.resolve(actor, ref)
            assert new_handle.execution_state == 'unknown' and new_handle.authority_epoch == next_epoch and (new_handle.execution_generation == claimed['generation'])
            passed('replacement_handle_exposes_unknown_generation')
            refused_call(lambda: claim_session_input(db, epoch=next_epoch, session_id='s'), 'unknown_execution')
            assert get_session_admission(db, admission_id=one['admission_id'])['status'] == 'unknown' and history() == [('user', 'fixture user'), ('assistant', 'fixture assistant')]
            passed('unknown_execution_blocks_another_claim')
            await wire_behavior(replacement, db)
            for connection in connections:
                await connection.close()
            assert all((connection.actor.transport_id not in authority.events for connection in connections))
            passed('connection_close_releases_transport_registration')
        finally:
            db.close()
    asyncio.run(behavior() if a.flow == 'authority-wire' else attach_first_fixture())
    expected_cases = 46 if a.flow == 'authority-wire' else 13
    assert len(cases) == len(set(cases)) == expected_cases, cases
    for name, module in sorted(sys.modules.items()):
        file = getattr(module, '__file__', None)
        if file:
            path = Path(file).resolve()
            if path.is_relative_to(source) and path.is_file():
                loaded.append(dict(module=name, path=path.relative_to(source).as_posix(), sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
    for name in ('hermes_state', 'gateway.session_authority', 'gateway.session_controls'):
        assert Path(sys.modules[name].__file__).resolve().is_relative_to(source)
    assert not denied
    assert len(expected_blocked_version_probes) <= 16
    report = dict(state='PASS_BOUNDED_REAL_AUTHORITY_WIRE_BEHAVIOR' if a.flow == 'authority-wire' else 'PASS_BOUNDED_CANONICAL_ATTACH_FIRST_FLOW', source=str(source), sourceCommit=SOURCE_COMMIT, revision=a.revision, flow=a.flow, python=sys.version.split()[0], projectModules=loaded, deniedEvents=denied, expectedMissingProbes=missing_os_probe_reads, codeLoadedFromPinnedCheckout=True, sessionOperationsExecuted=True, canonicalDispatcherExecuted=True, strictWireContractValidation=True, wireCases=22 if a.flow == 'authority-wire' else 10, expectedBlockedVersionProbes=expected_blocked_version_probes, cases=cases, ownedSocketEvents=owned_socket_events, scope='Actual canonical dispatcher with trusted synthetic actors and in-memory transports; isolated existing dependencies/Python audit hooks. Not HTTP/WebSocket authentication, restore/adoption, inference, worker or deployed acceptance; not an OS sandbox.')
except BaseException:
    report = dict(state='BEHAVIOR_FAILED_PRESERVED', source=str(source), deniedEvents=denied, denialDetails=denial_details, expectedBlockedVersionProbes=expected_blocked_version_probes, traceback=traceback.format_exc(), passedBeforeFailure=cases, projectModules=loaded)
    raise
finally:
    (root / 'result.json').write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(dict(state=report['state'], python=report['python'], modules=len(loaded), denied=denied, sessionOperations=True, cases=len(cases))))
