"""Disposable native worker and in-memory coordinator for the Android owner fixture."""
import asyncio
from contextlib import AsyncExitStack
import hashlib
import importlib
import json
import sys
import types

from server_extensions.worker_workspaces import broker_enrollment, local_pipe, read_host


AUTHORITY = 'fixture-android-worker-authority'
PROJECT = 'fixture-android-worker'
CONTENT = b'Android owner project fixture.\n'


class AndroidWorkerFixture:
    def __init__(self, source, extensions):
        package = types.ModuleType('gateway.signal_grove_workspaces')
        package.__path__ = [str(extensions)]
        sys.modules[package.__name__] = package
        self.coordinator = importlib.import_module(package.__name__ + '.coordinator')
        self.adapter = importlib.import_module(package.__name__ + '.native_adapter')
        self.policy = importlib.import_module(package.__name__ + '.operator_policy')
        installer = importlib.import_module(package.__name__ + '.install_adapter')
        controls = types.ModuleType('gateway.session_controls')
        assert controls.__name__ not in sys.modules
        controls.__file__ = str(source / 'gateway/session_controls.py')
        controls.__package__ = 'gateway'
        raw = (source / 'gateway/session_controls.py').read_bytes()
        patched = installer.patch_source(raw.decode('utf-8'))
        exec(compile(patched, controls.__file__, 'exec'), controls.__dict__)
        sys.modules[controls.__name__] = controls
        self.hashes = {
            'original': hashlib.sha256(raw).hexdigest(),
            'adapted': hashlib.sha256(patched.encode()).hexdigest(),
        }
        self.host = self.operator = None
        self.observer = AsyncExitStack()
        self.calls = []
        self.owner = broker_enrollment.observe_owner()
        assert self.owner.active is True, 'The disposable Windows broker needs an active interactive owner'

    async def keep_session_live(self, profile, endpoint, snapshot):
        from tui_gateway.owner_forward import connect_owner

        # Keep the fixture chat live while the emulator installs its APKs.
        # Android still has to attach explicitly on its own authenticated socket.
        viewer = await self.observer.enter_async_context(connect_owner(profile, endpoint))
        result = await viewer.rpc(
            'session.resume', _timeout=15, session_id=snapshot['sessionId'], omit_messages=True,
        )
        assert result['session_id'] == snapshot['sessionId']
        assert result['info']['cwd'] == snapshot['cwd']
        assert (result['revision'], result['execution_generation']) == (
            snapshot['revision'], snapshot['generation'],
        )

    async def open(self, worker_root, profile, snapshot):
        from gateway.owner_identity import host_owner_subject

        self.root = worker_root
        self.root.mkdir()
        note = self.root / 'note.txt'
        note.write_bytes(CONTENT)
        self.subject = host_owner_subject()
        self.scope = self.coordinator.scope_for(AUTHORITY, self.subject, str(profile))
        device = self.owner.device
        row = {'id': PROJECT, 'root': str(worker_root), 'device': device}
        policy = json.dumps({
            'schema': 1, 'device': device, 'owner_sid': self.owner.owner_sid,
            'grants': [{'scope_id': self.scope, 'project_ids': [PROJECT]}],
        }).encode()
        registry = broker_enrollment.RegistrySnapshot(
            hashlib.sha256(json.dumps(row).encode()).hexdigest(), (row,),
        )

        def project(project_id, rows):
            assert project_id == PROJECT and rows == (row,)
            return dict(row)

        session = broker_enrollment.EnrolledReadSession(
            scope_id=self.scope, read_policy=lambda: policy,
            read_registry=lambda: registry, validate_project=project,
        )
        self.host = read_host.ReadHost([session]).start()
        deadline = asyncio.get_running_loop().time() + 5
        while self.host.statuses()[0].state == 'starting':
            assert asyncio.get_running_loop().time() < deadline
            await asyncio.sleep(0.01)
        status = self.host.statuses()[0]
        assert status.state == 'ready'
        self.endpoint = status.endpoint
        scope = {'subject': self.subject, 'profile_id': str(profile), 'device': device}
        operator_data = json.dumps({
            'schema': 2, 'authority': AUTHORITY,
            'grants': [{**scope, 'project_ids': [PROJECT]}],
            'attachments': [{**scope, 'session_id': snapshot['sessionId'], 'project_id': PROJECT,
                             'coordinator_cwd': snapshot['cwd'], 'worker_root': str(worker_root)}],
        }).encode()
        document = self.policy.PolicyDocument(operator_data, hashlib.sha256(operator_data).hexdigest())
        self.operator = types.ModuleType('gateway.signal_grove_workspace_operator')
        self.operator.service = self.policy.build_operator_service(
            lambda: document, authority=AUTHORITY, routes={device: self.route},
        )
        sys.modules[self.operator.__name__] = self.operator

    async def route(self, payload):
        assert payload['scope_id'] == self.scope
        device = self.owner.device
        if payload['action'] == 'workspace_status':
            assert set(payload) == {'action', 'scope_id'}
            self.calls.append('describe')
            status = self.host.statuses()[0]
            assert status.state == 'ready' and status.endpoint == self.endpoint
            return dict(transport='status', scope_id=self.scope, device=device, state='ready',
                        worker_instance=self.endpoint.worker_instance, failure_code=None)
        assert payload['action'] == 'workspace_read' and set(payload) == {'action', 'scope_id', 'frame'}
        frame = payload['frame']
        assert frame['worker_instance'] == self.endpoint.worker_instance
        assert frame['payload']['project_id'] == PROJECT
        operation = frame['payload']['operation']
        self.calls.append(operation)
        data = await asyncio.to_thread(local_pipe.call_once, self.endpoint, json.dumps(frame).encode(), timeout=10)
        return dict(transport='reply', reply=json.loads(data))

    async def close(self):
        try:
            if self.operator is not None:
                sys.modules.pop(self.operator.__name__)
            if self.host is not None:
                self.host.close()
                assert self.host.wait_closed(timeout=8)
        finally:
            await self.observer.aclose()
