"""Attachment-scoped coordinator policy and worker lease regressions."""
from dataclasses import replace
import json
from pathlib import Path
import unittest

from server_extensions.coordinator_workspaces import coordinator as c
from server_extensions.coordinator_workspaces.attached import FollowedWorkspace
from server_extensions.coordinator_workspaces.operator_policy import PolicyDocument, build_operator_service

DEVICE = 'FIXTURE-WORKER'
INSTANCE = '11111111-2222-4333-8444-555555555555'
PROJECT = 'fixture-project'
SECOND = 'second-project'
SESSION = 'fixture-session'
OTHER_SESSION = 'other-session'
SUBJECT = 'uid:1000'
PROFILE = str(Path.cwd() / 'profile')
CWD = str(Path.cwd() / 'control-workspace')
WORKER_ROOT = r'E:\registered\project'
NATIVE_TOKEN = 'a' * 64
ENTRY_TOKEN = 'b' * 64


class AttachedPolicyTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.calls = []
        self.context = c.NativeReadContext(SUBJECT, PROFILE, 'transport', 'authority-instance')
        self.view = FollowedWorkspace(self.context, SESSION, 'subscription', CWD, 12, 5)
        self.policy = {
            'schema': 2,
            'authority': 'test-authority',
            'grants': [{'subject': SUBJECT, 'profile_id': PROFILE,
                        'device': DEVICE, 'project_ids': [PROJECT, SECOND]}],
            'attachments': [self.enrollment(SESSION), self.enrollment(OTHER_SESSION)],
        }
        self.worker_reported_root = WORKER_ROOT
        self.after_read = None
        self.generation = 1
        self.sequence = 0
        self.service = build_operator_service(
            self.read_policy, authority='test-authority', routes={DEVICE: self.route},
        )
        self.attached = self.service.attachments

    def enrollment(self, session_id, **changes):
        return {
            'subject': SUBJECT, 'profile_id': PROFILE, 'session_id': session_id,
            'coordinator_cwd': CWD, 'device': DEVICE, 'project_id': PROJECT,
            'worker_root': WORKER_ROOT, **changes,
        }

    def read_policy(self):
        return PolicyDocument(json.dumps(self.policy).encode('utf-8'), f'{self.generation:064x}')

    def follow(self):
        return self.view

    async def route(self, payload):
        self.calls.append(payload)
        scope = payload['scope_id']
        if payload['action'] == 'workspace_status':
            return dict(transport='status', scope_id=scope, device=DEVICE, state='ready',
                        worker_instance=INSTANCE, failure_code=None)
        frame = payload['frame']
        request = frame['payload']
        result = dict(schema=1, scope_id=scope, project_id=request['project_id'])
        operation = request['operation']
        if operation == 'prepare':
            result.update(root=self.worker_reported_root, binding_token=NATIVE_TOKEN,
                          expires_in_seconds=300, worker=dict(device=DEVICE, instance_id=INSTANCE))
        else:
            result.update(worker_instance=INSTANCE, relative_path=request['relative_path'], truncated=False)
            if operation == 'list':
                result.update(entries=[], excluded_entries=0)
            else:
                result.update(byte_size=1, preview_bytes=1, preview_sha256='c'*64, text='x', binary=False)
        if self.after_read is not None:
            self.after_read()
        return dict(transport='reply', reply=dict(schema=1, request_id=frame['request_id'],
                    worker_instance=INSTANCE, status='ok', result=result))

    def request(self, operation, **fields):
        self.sequence += 1
        return {'request_id': f'{self.sequence:032x}', 'worker_instance': INSTANCE,
                'operation': operation, **fields}

    async def prepare(self):
        return (await self.attached.read(self.follow, self.request('prepare')))['result']

    async def read_bound(self, token):
        return await self.attached.read(self.follow, self.request(
            'preview', binding_token=token, relative_path='note.txt', entry_token=ENTRY_TOKEN,
        ))

    async def assert_refused(self, operation, code, submitted=False):
        with self.assertRaises(c.ReadRefused) as error:
            await operation
        self.assertEqual((code, submitted), (error.exception.code, error.exception.request_submitted))

    async def test_v1_grants_stay_independent_and_attached_description_projects_one_root(self):
        self.assertEqual([PROJECT, SECOND],
                         (await self.service.describe(lambda: self.context, DEVICE))['project_ids'])
        status = await self.attached.describe(self.follow)
        self.assertEqual(([PROJECT], WORKER_ROOT, INSTANCE),
                         (status['project_ids'], status['root'], status['worker_instance']))

    async def test_schema_one_has_no_implicit_attachment(self):
        self.policy.pop('attachments')
        self.policy['schema'] = 1
        await self.assert_refused(self.attached.describe(self.follow), 'DENIED')
        self.assertEqual([], self.calls)

    async def test_unknown_subject_profile_or_session_cannot_reach_worker(self):
        for changed in (replace(self.context, subject='api'), replace(self.context, profile_id=PROFILE + '2')):
            self.view = replace(self.view, context=changed)
            await self.assert_refused(self.attached.describe(self.follow), 'DENIED')
        self.view = replace(self.view, context=self.context, session_id='unmapped')
        await self.assert_refused(self.attached.describe(self.follow), 'DENIED')
        self.assertEqual([], self.calls)

    async def test_invalid_enrollment_refuses_ungranted_project_duplicate_and_noncanonical_paths(self):
        for rows in (
            [self.enrollment(SESSION, project_id='ungranted')],
            [self.enrollment(SESSION), self.enrollment(SESSION)],
            [self.enrollment(SESSION, worker_root=r'E:\registered\..\outside')],
            [self.enrollment(SESSION, worker_root=r'\\?\E:\registered\project')],
            [self.enrollment(SESSION, coordinator_cwd='relative')],
        ):
            with self.subTest(rows=rows):
                self.policy['attachments'] = rows
                await self.assert_refused(self.attached.describe(self.follow), 'UNAVAILABLE')
        self.assertEqual([], self.calls)

    async def test_stale_cwd_and_client_device_root_project_claims_refused_before_route(self):
        original = self.view
        self.view = replace(self.view, cwd=CWD + '-different')
        await self.assert_refused(self.attached.describe(self.follow), 'CHANGED')
        self.view = original
        for key in ('device', 'root', 'project_id', 'profile_id', 'scope_id'):
            await self.assert_refused(
                self.attached.read(self.follow, self.request('prepare', **{key: 'untrusted'})), 'INVALID',
            )
        self.assertEqual([], self.calls)

    async def test_prepare_keeps_worker_token_internal_and_read_returns_verified_route(self):
        prepared = await self.prepare()
        self.assertEqual(WORKER_ROOT, prepared['root'])
        self.assertNotEqual(NATIVE_TOKEN, prepared['binding_token'])
        self.assertNotIn('scope_id', prepared)
        reply = await self.read_bound(prepared['binding_token'])
        self.assertEqual('x', reply['result']['text'])
        self.assertNotIn('scope_id', reply['result'])
        self.assertEqual(NATIVE_TOKEN, self.calls[-1]['frame']['payload']['binding_token'])
        await self.assert_refused(self.read_bound(NATIVE_TOKEN), 'CHANGED')
        self.assertEqual(2, len(self.calls))

    async def test_worker_cannot_redirect_prepare_to_a_different_root(self):
        self.worker_reported_root = WORKER_ROOT + '-wrong'
        await self.assert_refused(self.prepare(), 'CHANGED', submitted=True)
        self.assertEqual(1, len(self.calls))

    async def test_binding_cannot_cross_a_new_attachment_connection_generation_or_session(self):
        original = self.view
        token = (await self.prepare())['binding_token']
        for view in (replace(original, subscription='reopened'),
                     replace(original, context=replace(self.context, transport_id='new-transport')),
                     replace(original, execution_generation=6),
                     replace(original, session_id=OTHER_SESSION)):
            self.view = view
            await self.assert_refused(self.read_bound(token), 'CHANGED')
        self.assertEqual(1, len(self.calls))

    async def test_inflight_scope_change_discards_preview_and_reports_submission(self):
        token = (await self.prepare())['binding_token']
        self.after_read = lambda: setattr(self, 'view', replace(self.view, revision=13))
        await self.assert_refused(self.read_bound(token), 'CHANGED', submitted=True)
        self.assertEqual(2, len(self.calls))

    async def test_same_registration_replaced_on_disk_invalidates_inflight_reply_and_lease(self):
        token = (await self.prepare())['binding_token']
        self.after_read = lambda: setattr(self, 'generation', 2)
        await self.assert_refused(self.read_bound(token), 'CHANGED', submitted=True)
        await self.assert_refused(self.read_bound(token), 'CHANGED')
        self.assertEqual(2, len(self.calls))

    async def test_expired_bindings_are_refused_before_worker_admission(self):
        now = 0
        self.attached._clock = lambda: now
        token = (await self.prepare())['binding_token']
        now = 301
        await self.assert_refused(self.read_bound(token), 'EXPIRED')
        self.assertEqual(1, len(self.calls))
