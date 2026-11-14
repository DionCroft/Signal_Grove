"""Behavior of dormant explicit operator grants with synthetic trusted observations."""
from dataclasses import replace
import json
from pathlib import Path
import unittest
from server_extensions.coordinator_workspaces import coordinator as c
from server_extensions.coordinator_workspaces.operator_policy import (MAX_POLICY, PolicyDocument, build_operator_service)

DEVICE='FIXTURE-WORKER'
INSTANCE='11111111-2222-4333-8444-555555555555'
AUTHORITY='fixture-authority'
PROFILE=str(Path.cwd()/'fixture-profile')
CONTEXT=c.NativeReadContext('uid:1000',PROFILE,'fixture-transport','fixture-instance')


class OperatorPolicyTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.calls=[];self.edit=None;self.version=0;self.failure=None
        self.policy=dict(schema=1,authority=AUTHORITY,grants=[self.rule()])
        self.service=self.build()

    def rule(self, **changes):
        return dict(dict(subject=CONTEXT.subject,profile_id=PROFILE,device=DEVICE,
                         project_ids=['fixture-project']),**changes)

    def observe(self):
        if self.failure:raise OSError('private-observer-error')
        return PolicyDocument(json.dumps(self.policy).encode(),format(self.version,'064x'))

    def build(self, reader=None, routes=None):
        return build_operator_service(reader or self.observe,authority=AUTHORITY,
                                      routes=routes if routes is not None else {DEVICE:self.route})

    async def route(self,payload):
        self.calls.append(payload)
        if self.edit:self.edit()
        scope=payload['scope_id']
        if payload['action']=='workspace_status':
            return dict(transport='status',scope_id=scope,device=DEVICE,state='ready',worker_instance=INSTANCE,failure_code=None)
        frame=payload['frame']
        result=dict(schema=1,scope_id=scope,project_id='fixture-project',worker=dict(device=DEVICE,instance_id=INSTANCE))
        return dict(transport='reply',reply=dict(schema=1,request_id=frame['request_id'],worker_instance=INSTANCE,status='ok',result=result))

    def request(self):
        return dict(schema=1,request_id='a'*32,worker_instance=INSTANCE,operation='prepare',project_id='fixture-project')

    async def test_only_explicit_owner_profile_device_and_projects_reach_the_fixed_route(self):
        result=await self.service.describe(lambda:CONTEXT,DEVICE)
        self.assertEqual(['fixture-project'],result['project_ids']);self.assertEqual(['prepare','list','preview'],result['operations'])
        self.assertEqual({'action','scope_id'},set(self.calls[0]))
        for context,device in [(replace(CONTEXT,subject='api'),DEVICE),
                               (replace(CONTEXT,profile_id=PROFILE+'-other'),DEVICE),
                               (CONTEXT,'OTHER-WORKER')]:
            with self.subTest(context=context,device=device):
                with self.assertRaises(c.ReadRefused) as error:await self.service.describe(lambda:context,device)
                self.assertEqual('DENIED',error.exception.code);self.assertFalse(error.exception.request_submitted)
        self.assertEqual(1,len(self.calls))

    async def test_closed_policy_schema_rejects_invalid_or_ambiguous_enrollment(self):
        invalid=[dict(self.policy,schema=True),dict(self.policy,authority='foreign'),dict(self.policy,command='private-command'),
                 dict(self.policy,grants=[self.rule(),self.rule()]),dict(self.policy,grants=[self.rule(root='private-root')]),
                 dict(self.policy,grants=[self.rule(project_ids=[])]),dict(self.policy,grants=[self.rule(project_ids=['x','x'])]),
                 dict(self.policy,grants=[self.rule(project_ids=['../root'])]),dict(self.policy,grants=[self.rule(profile_id='relative')]),
                 dict(self.policy,grants=[self.rule(subject=True)]),dict(self.policy,grants=[self.rule(device='OTHER-WORKER')]),
                 dict(self.policy,grants=[self.rule(project_ids=['p'+str(i) for i in range(33)])])]
        raws=[json.dumps(p).encode() for p in invalid]+[b'{"schema":1,"schema":1}',b'[]',b'{"schema":NaN}',b'\xff',b'x'*(MAX_POLICY+1)]
        for raw in raws:
            with self.subTest(bytes=len(raw)):
                with self.assertRaises(c.ReadRefused) as error:self.build(lambda:PolicyDocument(raw,'a'*64))
                self.assertEqual('UNAVAILABLE',error.exception.code)
        self.assertEqual([],self.calls)

    async def test_empty_grants_never_advertise_ready_worker_access(self):
        self.policy['grants']=[]
        service=self.build()
        with self.assertRaises(c.ReadRefused) as error:await service.describe(lambda:CONTEXT,DEVICE)
        self.assertEqual('DENIED',error.exception.code);self.assertEqual([],self.calls)

    async def test_fresh_protected_edits_take_effect_but_cannot_change_authority_or_route(self):
        routes={DEVICE:self.route};service=self.build(routes=routes);routes.clear()
        await service.describe(lambda:CONTEXT,DEVICE)
        self.policy['grants']=[]
        with self.assertRaises(c.ReadRefused):await service.describe(lambda:CONTEXT,DEVICE)
        self.policy['grants']=[self.rule(project_ids=['explicit-new-project'])];self.version+=1
        self.assertEqual(['explicit-new-project'],(await service.describe(lambda:CONTEXT,DEVICE))['project_ids'])
        self.policy['authority']='foreign'
        with self.assertRaises(c.ReadRefused):await service.describe(lambda:CONTEXT,DEVICE)
        self.assertEqual(2,len(self.calls))

    async def test_revocation_during_read_discards_reply_and_preserves_submitted_state(self):
        self.edit=lambda:self.policy.update(grants=[])
        with self.assertRaises(c.ReadRefused) as error:await self.service.read(lambda:CONTEXT,DEVICE,self.request())
        self.assertEqual('DENIED',error.exception.code);self.assertTrue(error.exception.request_submitted)
        with self.assertRaises(c.ReadRefused):await self.service.describe(lambda:CONTEXT,DEVICE)
        self.assertEqual(1,len(self.calls))

    async def test_same_policy_bytes_with_a_new_file_generation_discard_inflight_reply(self):
        self.edit=lambda:setattr(self,'version',self.version+1)
        with self.assertRaises(c.ReadRefused) as error:await self.service.read(lambda:CONTEXT,DEVICE,self.request())
        self.assertEqual('CHANGED',error.exception.code);self.assertTrue(error.exception.request_submitted)
        self.assertEqual(1,len(self.calls))

    async def test_observer_failure_or_malformed_observation_is_private_and_unavailable(self):
        self.failure=True
        with self.assertRaises(c.ReadRefused) as error:await self.service.describe(lambda:CONTEXT,DEVICE)
        self.assertEqual('UNAVAILABLE',str(error.exception));self.assertNotIn('private',repr(error.exception))
        for value in (None,b'raw',PolicyDocument(b'', 'a'*64),PolicyDocument(b'{}','bad')):
            with self.assertRaises(c.ReadRefused):self.build(lambda:value)
        self.assertEqual('PolicyDocument(redacted)',repr(PolicyDocument(b'private-policy','b'*64)))
        self.assertEqual([],self.calls)

    async def test_request_identity_command_and_ungranted_project_claims_cannot_override_policy(self):
        for field in ('subject','profile_id','authority','root','command','scope_id'):
            request=self.request();request[field]='private-claim'
            with self.assertRaises(c.ReadRefused) as error:await self.service.read(lambda:CONTEXT,DEVICE,request)
            self.assertEqual('INVALID',error.exception.code)
        request=self.request();request['project_id']='ungranted'
        with self.assertRaises(c.ReadRefused) as error:await self.service.read(lambda:CONTEXT,DEVICE,request)
        self.assertEqual('DENIED',error.exception.code);self.assertEqual([],self.calls)
