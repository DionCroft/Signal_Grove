"""Join native Hermes workspace attachment to a real Windows read-only pipe.

Run through probe_workspace_coordinator.py with its pinned source and I/O fence.
The trusted identity and two operator policies are disposable fixtures.
"""
import asyncio
import hashlib
import json
import os
import sys
import types
from pathlib import Path
from uuid import uuid4
from unittest.mock import patch

from agent import image_routing
from gateway.config import GatewayConfig
from gateway.owner_identity import host_owner_subject
from gateway.session import SessionStore
from gateway.session_authorities import SessionAuthorities
from gateway.session_authority import initialize_session_authority
from hermes_cli import version_info
from tui_gateway import git_probe

from server_extensions.worker_workspaces import broker_enrollment, host_integration
from server_extensions.worker_workspaces import local_pipe, operator_file, read_host


async def run(root, home, controls, adapter, coordinator, operator_policy):
    workspace = root / 'project'
    workspace.mkdir()
    previous = root / 'old-workspace'
    previous.mkdir()
    coordinator_workspace = root / 'coordinator-workspace'
    coordinator_workspace.mkdir()
    content = b'Fixture project evidence.\n'
    note = workspace / 'note.txt'
    note.write_bytes(content)
    digest = hashlib.sha256(content).hexdigest()
    (home / 'config.yaml').write_text(
        'model: {default: fixture-model, provider: custom, base_url: "http://127.0.0.1:9/v1", context_length: 8192}\n'
        'terminal: {backend: local}\n'
        'model_catalog: {enabled: false}\n'
        'agent: {image_input_mode: text}\n',
        encoding='utf-8',
    )

    owner = broker_enrollment.observe_owner()
    assert owner.active is True, 'Run in an active interactive Windows session'
    subject = host_owner_subject()
    authority_key = 'fixture-attached-authority'
    project_id = 'fixture-project'
    scope = coordinator.scope_for(authority_key, subject, str(home))

    host_root = root / 'host'
    host_root.mkdir()
    (host_root / 'state').mkdir()
    registry_file = host_root / 'registry.json'
    project = {'id': project_id, 'root': str(workspace), 'device': owner.device}
    registry_file.write_text(json.dumps({'projects': [project]}), encoding='utf-8')
    worker_policy = {
        'schema': 1, 'device': owner.device, 'owner_sid': owner.owner_sid,
        'grants': [{'scope_id': scope, 'project_ids': [project_id]}],
    }
    (host_root / 'workspace-read-policy.json').write_text(json.dumps(worker_policy), encoding='utf-8')

    def registered_project(ident, rows):
        assert ident == project_id and rows == (project,)
        return dict(rows[0])

    def write_projection(path, data):
        next_path = path.with_suffix('.next')
        next_path.write_text(json.dumps(data), encoding='utf-8')
        os.replace(next_path, path)

    worker = types.SimpleNamespace(
        DEVICE=owner.device, HOST_CONTEXT=True, REGISTRY=registry_file,
        RT=Path(controls.__file__).parent.parent, project=registered_project,
        atomic=write_projection,
    )
    policy_bytes = json.dumps({
        'schema': 1, 'authority': authority_key,
        'grants': [{'subject': subject, 'profile_id': str(home),
                    'device': owner.device, 'project_ids': [project_id]}],
    }).encode('utf-8')
    document = operator_policy.PolicyDocument(policy_bytes, hashlib.sha256(policy_bytes).hexdigest())

    operations = []
    after_route = None
    bound_checks = []

    async def route(payload):
        operations.append(payload['action'])
        reply = await asyncio.to_thread(host_integration.rpc, worker, payload)
        if after_route is not None:
            await after_route()
        return reply

    operator = types.ModuleType('gateway.signal_grove_workspace_operator')
    operator.service = operator_policy.build_operator_service(
        lambda: document, authority=authority_key, routes={owner.device: route},
    )
    # The enclosing probe already verified the checkout. Disable the runtime's
    # optional startup provenance Git lookup in this no-subprocess fixture.
    with patch.object(version_info, '_resolve_repo_dir', return_value=None):
        store = SessionStore(home / 'sessions', GatewayConfig())
    db = store._db
    assert db.db_path.parent.resolve() == home

    def unexpected_turn(*args, **kwargs):
        raise AssertionError('Fixture must never submit or execute a turn')

    runner = types.SimpleNamespace(
        session_store=store, _session_db=db, adapters={}, _draining=False,
        _cached_agent_for=lambda route: None,
        _evict_cached_agent=lambda route: None,
        _resolve_session_agent_runtime=lambda **kwargs: ('fixture-model', {}),
        _handle_message=unexpected_turn,
    )
    runner._adapter_for_source = lambda source: runner.adapters.get(source.platform)
    runner.session_authorities = SessionAuthorities(home, multiplexed=True)
    authority = await initialize_session_authority(
        runner, profile_id=str(home), instance_id='fixture-attached',
    )
    runner.session_authorities.add(home, authority)
    identity = {
        'provider': 'basic', 'user_id': 'fixture-login',
        'profile_id': str(home), 'profile_scope': 'host',
    }
    peer = controls.AuthorityConnection(authority, object(), identity, operator=True)
    non_operator = controls.AuthorityConnection(authority, object(), identity)
    second_owner = controls.AuthorityConnection(authority, object(), identity, operator=True)

    async def request(connection, method, **params):
        reply = await connection.dispatch({'jsonrpc': '2.0', 'id': 1, 'method': method, 'params': params})
        assert 'result' in reply, (method, reply)
        return reply['result']

    async def read(instance, number, operation, *, connection=peer, project=project_id, **fields):
        frame = {
            'schema': 1, 'request_id': f'{number:032x}', 'worker_instance': instance,
            'operation': operation, 'project_id': project, **fields,
        }
        return await request(connection, adapter.READ, device=owner.device, request=frame)

    def ok(reply):
        assert reply['state'] == 'ok', reply
        return reply['payload']

    def refusal(reply, code, submitted):
        assert reply == {'schema': 1, 'state': 'error', 'code': code, 'request_submitted': submitted}, reply

    runtime = (broker_enrollment, operator_file, read_host, local_pipe)
    try:
        with (
            patch.dict(sys.modules, {operator.__name__: operator}),
            patch.object(host_integration, 'HERE', host_root),
            patch.object(git_probe, 'run_git', return_value=''),  # Fixture folders have no Git metadata.
            patch.object(image_routing, '_lookup_supports_vision', return_value=False),
            patch.object(host_integration, '_runtime', return_value=runtime),
        ):
            host = host_integration.Host(worker)
            try:
                assert host.manager is not None, host.failure
                deadline = asyncio.get_running_loop().time() + 5
                while host.manager.statuses()[0].state == 'starting':
                    assert asyncio.get_running_loop().time() < deadline, 'Windows pipe did not become ready'
                    await asyncio.sleep(0.01)
                created = await request(
                    peer, 'session.create', request_id='create', source='cli', cwd=str(previous),
                    toolsets=['terminal'],
                )
                sid = created['session_id']
                attached = await request(peer, 'session.resume', session_id=sid)
                assert peer.subscriptions.get(sid)
                # A worker-only directory is absent from the Hermes host view.
                # A native workspace move must never treat its label as a local directory.
                missing = root / 'not-present-on-authority'
                assert not missing.exists()
                before_refusal = db.get_session(sid)
                rejected = await peer.dispatch({
                    'jsonrpc': '2.0', 'id': 2, 'method': 'session.mutate',
                    'params': {
                        'session_id': sid, 'request_id': 'worker-path',
                        'operation': 'workspace', 'payload': {'cwd': str(missing)},
                        'expected_revision': attached['revision'],
                        'expected_generation': attached['execution_generation'],
                    },
                })
                assert rejected.get('error', {}).get('data', {}).get('reason') == 'invalid_params', rejected
                assert db.get_session(sid) == before_refusal and not operations
                assert not missing.exists()
                await request(
                    peer, 'session.mutate', session_id=sid, request_id='attach', operation='workspace',
                    expected_revision=attached['revision'], expected_generation=attached['execution_generation'],
                    payload={'cwd': str(workspace)},
                )
                readback = await request(peer, 'session.info', session_id=sid)
                assert Path(readback['cwd']) == workspace, readback
                before = db.get_session(sid)
                subscription = peer.subscriptions[sid]

                host.tick()
                status = ok(await request(peer, adapter.DESCRIBE, device=owner.device))
                assert status['state'] == 'ready' and status['project_ids'] == [project_id]
                instance = status['worker_instance']
                prepared = ok(await read(instance, 1, 'prepare'))['result']
                assert Path(prepared['root']) == workspace == Path(readback['cwd']), prepared
                binding = prepared['binding_token']
                listing = ok(await read(
                    instance, 2, 'list', binding_token=binding, relative_path='', entry_token=None,
                ))['result']
                entry, = listing['entries']
                assert entry['relative_path'] == 'note.txt' and entry['kind'] == 'file'
                preview = ok(await read(
                    instance, 3, 'preview', binding_token=binding,
                    relative_path=entry['relative_path'], entry_token=entry['entry_token'],
                ))['result']
                assert preview['text'] == content.decode('utf-8')
                assert preview['preview_bytes'] == len(content) and preview['preview_sha256'] == digest
                assert preview['truncated'] is False

                calls = len(operations)
                refusal(await read(instance, 4, 'prepare', connection=non_operator), 'DENIED', False)
                refusal(await read(instance, 5, 'prepare', project='ungranted'), 'DENIED', False)
                assert len(operations) == calls
                refusal(await read(
                    instance, 6, 'preview', binding_token=binding,
                    relative_path=entry['relative_path'], entry_token='0' * 64,
                ), 'CHANGED', True)
                refusal(await read(
                    instance, 7, 'list', binding_token='0' * 64, relative_path='', entry_token=None,
                ), 'CHANGED', True)

                refusal(await read(str(uuid4()), 8, 'prepare'), 'CHANGED', False)

                assert operations == ['workspace_status'] + ['workspace_read'] * 6
                assert db.get_session(sid) == before and peer.subscriptions[sid] == subscription
                # The two paths deliberately differ, as they do on Pi and Windows.
                await request(
                    peer, 'session.mutate', session_id=sid, request_id='cross-host-attach',
                    operation='workspace', expected_revision=before['runtime_revision'],
                    expected_generation=before['runtime_generation'],
                    payload={'cwd': str(coordinator_workspace)},
                )
                cross_host = db.get_session(sid)
                assert cross_host['cwd'] == str(coordinator_workspace) != str(workspace)
                selection = {
                    'attached_session_id': sid,
                    'expected_revision': cross_host['runtime_revision'],
                    'expected_generation': cross_host['runtime_generation'],
                }
                before_calls = len(operations)
                refusal(await request(peer, adapter.DESCRIBE_ATTACHED, **selection), 'DENIED', False)

                policy = json.loads(document.data)
                policy['schema'] = 2
                policy['attachments'] = [{
                    'subject': subject, 'profile_id': str(home), 'session_id': sid,
                    'coordinator_cwd': str(coordinator_workspace), 'device': owner.device,
                    'project_id': project_id, 'worker_root': str(workspace),
                }]
                raw = json.dumps(policy).encode('utf-8')
                document = operator_policy.PolicyDocument(raw, hashlib.sha256(raw).hexdigest())

                refusal(await request(second_owner, adapter.DESCRIBE_ATTACHED, **selection), 'DENIED', False)
                refusal(await request(non_operator, adapter.DESCRIBE_ATTACHED, **selection), 'DENIED', False)
                unknown = {**selection, 'attached_session_id': 'not-a-known-session'}
                refusal(await request(peer, adapter.DESCRIBE_ATTACHED, **unknown), 'DENIED', False)
                assert 'not-a-known-session' not in authority.sessions
                old = {**selection, 'expected_generation': before['runtime_generation']}
                refusal(await request(peer, adapter.DESCRIBE_ATTACHED, **old), 'CHANGED', False)
                assert len(operations) == before_calls

                host.tick()
                attached_status = ok(await request(peer, adapter.DESCRIBE_ATTACHED, **selection))
                assert attached_status['worker_instance'] == instance
                assert attached_status['root'] == str(workspace)
                assert attached_status['project_ids'] == [project_id]

                async def bound_read(number, operation, **fields):
                    wire = dict(request_id=f'{number:032x}', worker_instance=instance,
                                operation=operation, **fields)
                    return await request(peer, adapter.READ_ATTACHED, **selection, request=wire)

                scoped = ok(await bound_read(9, 'prepare'))['result']
                assert scoped['root'] == str(workspace) and 'scope_id' not in scoped
                scoped_token = scoped['binding_token']
                assert scoped_token != binding
                refusal(await bound_read(
                    10, 'list', binding_token=binding, relative_path='', entry_token=None,
                ), 'CHANGED', False)
                entries = ok(await bound_read(
                    11, 'list', binding_token=scoped_token, relative_path='', entry_token=None,
                ))['result']
                entry, = entries['entries']
                preview_fields = {
                    'binding_token': scoped_token, 'relative_path': entry['relative_path'],
                    'entry_token': entry['entry_token'],
                }
                bound_preview = ok(await bound_read(12, 'preview', **preview_fields))['result']
                assert (bound_preview['text'], bound_preview['preview_sha256']) == (content.decode(), digest)
                assert 'scope_id' not in bound_preview
                assert db.get_session(sid) == cross_host and peer.subscriptions[sid] == subscription
                bound_checks.extend(['separate-authority-and-worker-paths', 'schema-one-has-no-attachment',
                                     'unfollowed-and-non-operator-denied', 'no-session-adoption',
                                     'stale-generation-refused', 'attached-prepare-list-preview',
                                     'raw-v1-token-denied', 'chat-and-subscription-preserved'])

                async def move_after_worker_read():
                    await request(
                        second_owner, 'session.mutate', session_id=sid, request_id='move-during-read',
                        operation='workspace', expected_revision=selection['expected_revision'],
                        expected_generation=selection['expected_generation'],
                        payload={'cwd': str(previous)},
                    )

                after_route = move_after_worker_read
                refusal(await bound_read(13, 'preview', **preview_fields), 'CHANGED', True)
                assert db.get_session(sid)['cwd'] == str(previous)
                after_route = None
                after_calls = len(operations)
                refusal(await bound_read(14, 'preview', **preview_fields), 'CHANGED', False)
                assert len(operations) == after_calls
                assert after_calls - before_calls == 5
                bound_checks.extend(['in-flight-move-discarded-reply', 'obsolete-lease-refused-before-route'])

                for table in ('messages', 'session_admissions', 'worker_executions'):
                    assert db._read_one('SELECT COUNT(*) AS n FROM ' + table)['n'] == 0, table
                assert list(workspace.iterdir()) == [note] and note.read_bytes() == content
                assert list(previous.iterdir()) == list(coordinator_workspace.iterdir()) == []
            finally:
                assert host.close(), 'Disposable Windows pipe did not close'
    finally:
        try:
            await second_owner.close()
            await non_operator.close()
            await peer.close()
        finally:
            store.close_all_db_handles()

    return {
        'state': 'ISOLATED_NATIVE_ATTACHMENT_AND_WINDOWS_PIPE_PASS',
        'checks': ['absent-authority-directory-refused', 'create-resume-move-readback', 'ready-worker', 'same-root-prepare', 'list',
                   'preview-bytes-and-hash', 'non-operator-denied', 'ungranted-project-denied',
                   'invalid-entry-token-denied', 'invalid-binding-denied', 'wrong-worker-instance-denied',
                   'state-and-files-preserved'],
        'attachedChecks': bound_checks,
        'previewBytes': len(content),
        'routeCalls': len(operations),
        'admissions': 0,
        'workerExecutions': 0,
        'scope': 'Disposable local authority and named pipe with injected owner login and registration. '
                 'The new attached methods also prove distinct coordinator and worker paths and reject a changed attachment.',
    }
