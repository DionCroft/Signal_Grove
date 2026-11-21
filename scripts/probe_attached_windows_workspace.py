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

    async def route(payload):
        operations.append(payload['action'])
        return await asyncio.to_thread(host_integration.rpc, worker, payload)

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
                for table in ('messages', 'session_admissions', 'worker_executions'):
                    assert db._read_one('SELECT COUNT(*) AS n FROM ' + table)['n'] == 0, table
                assert list(workspace.iterdir()) == [note] and note.read_bytes() == content
                assert list(previous.iterdir()) == []
            finally:
                assert host.close(), 'Disposable Windows pipe did not close'
    finally:
        try:
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
        'previewBytes': len(content),
        'routeCalls': len(operations),
        'admissions': 0,
        'workerExecutions': 0,
        'scope': 'Disposable local authority and named pipe with injected owner login and registration. '
                 'The fixture compares roots. Workspace reads v1 do not enforce a session attachment.',
    }
