import asyncio
from dataclasses import replace
import json
import sys
import unittest

from server_extensions.coordinator_workspaces import coordinator as c
from server_extensions.coordinator_workspaces.ssh_transport import SingleCallSsh

DEVICE='FIXTURE-WORKER'
INSTANCE='11111111-2222-4333-8444-555555555555'
CONTEXT=c.NativeReadContext('uid:1000','/fixture/hermes','transport-A','authority-A')


class CoordinatorPolicyTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.context=CONTEXT; self.grant=c.ReadGrant('e'*64,frozenset({'fixture-project'}))
        self.calls=[]; self.edit=None; self.reply=None
        self.scope=c.scope_for('fixture-authority',CONTEXT.subject,CONTEXT.profile_id)
        self.service=c.CoordinatorReadService('fixture-authority',{DEVICE:self.route},grant=self.lookup)

    def lookup(self, context, device):
        if context.subject != CONTEXT.subject or context.profile_id != CONTEXT.profile_id: raise c.ReadRefused('DENIED')
        return self.grant

    def auth(self): return self.context

    async def route(self,payload):
        self.calls.append(payload)
        if self.edit: self.edit()
        if self.reply is not None: return self.reply
        if payload['action']=='workspace_status':
            return dict(transport='status',scope_id=self.scope,device=DEVICE,state='ready',worker_instance=INSTANCE,failure_code=None)
        f=payload['frame']; p=f['payload']
        result=dict(schema=1,scope_id=self.scope,project_id='fixture-project')
        if p['operation']=='prepare': result.update(worker=dict(device=DEVICE,instance_id=INSTANCE),root='fixture-root',binding_token='b'*64,expires_in_seconds=300)
        else: result.update(worker_instance=INSTANCE,relative_path=p['relative_path'],text='fixture content')
        return dict(transport='reply',reply=dict(schema=1,request_id=f['request_id'],worker_instance=INSTANCE,status='ok',result=result))

    def request(self,operation='prepare'):
        value=dict(schema=1,request_id='a'*32,worker_instance=INSTANCE,operation=operation,project_id='fixture-project')
        if operation!='prepare': value.update(binding_token='b'*64,relative_path='readme.txt',entry_token='c'*64)
        return value

    async def refused(self,awaitable,code,submitted=False):
        with self.assertRaises(c.ReadRefused) as caught: await awaitable
        self.assertEqual((code,submitted),(caught.exception.code,caught.exception.request_submitted))

    async def test_describe_checks_real_route_and_returns_only_ready_operations(self):
        result=await self.service.describe(self.auth,DEVICE)
        self.assertEqual(['prepare','list','preview'],result['operations'])
        self.assertEqual(['fixture-project'],result['project_ids'])
        self.assertNotIn('scope_id',result)
        self.assertEqual([dict(action='workspace_status',scope_id=self.scope)],self.calls)

    async def test_prepare_list_preview_derive_scope_and_preserve_request_identity(self):
        for operation in ('prepare','list','preview'):
            result=await self.service.read(self.auth,DEVICE,self.request(operation))
            self.assertEqual('a'*32,result['request_id'])
            self.assertEqual(self.scope,self.calls[-1]['scope_id'])
            self.assertEqual(self.scope,self.calls[-1]['frame']['payload']['scope_id'])
        self.assertEqual(3,len(self.calls))

    async def test_client_cannot_choose_authority_subject_scope_profile_root_or_route(self):
        for key in ('scope_id','subject','profile','authority','root','command','host'):
            request=self.request();request[key]='untrusted'
            await self.refused(self.service.read(self.auth,DEVICE,request),'INVALID')
        await self.refused(self.service.describe(self.auth,'OTHER'),'DENIED')
        self.assertFalse(self.calls)

    async def test_foreign_actor_profile_and_ungranted_project_refuse_before_ssh(self):
        for change in (dict(subject='api'),dict(subject='uid:2000'),dict(profile_id='/fixture/other')):
            self.context=replace(CONTEXT,**change)
            await self.refused(self.service.read(self.auth,DEVICE,self.request()),'DENIED')
        self.context=CONTEXT;request=self.request();request['project_id']='foreign-project'
        await self.refused(self.service.read(self.auth,DEVICE,request),'DENIED')
        self.assertFalse(self.calls)

    async def test_actor_profile_transport_authority_or_policy_change_drops_contents(self):
        for field,value in [('subject','uid:2000'),('profile_id','/fixture/other'),('transport_id','transport-B'),('authority_instance','authority-B')]:
            self.context=CONTEXT;self.edit=lambda f=field,v=value:setattr(self,'context',replace(CONTEXT,**{f:v}))
            await self.refused(self.service.read(self.auth,DEVICE,self.request()),'DENIED' if field in ('subject','profile_id') else 'CHANGED',True)
        self.context=CONTEXT;self.edit=lambda:setattr(self,'grant',replace(self.grant,revision='f'*64))
        await self.refused(self.service.read(self.auth,DEVICE,self.request()),'CHANGED',True)

    async def test_lost_reply_and_unknown_transport_never_retry(self):
        async def lost(payload): self.calls.append(payload);raise c.ReadRefused('UNAVAILABLE',request_submitted=True)
        service=c.CoordinatorReadService('fixture-authority',{DEVICE:lost},grant=self.lookup)
        await self.refused(service.read(self.auth,DEVICE,self.request()),'UNAVAILABLE',True)
        self.assertEqual(1,len(self.calls))

    async def test_worker_refusals_keep_exact_submitted_state_and_never_exception_detail(self):
        for submitted in (True,False):
            self.reply=dict(transport='error',code='BUSY',request_submitted=submitted)
            await self.refused(self.service.read(self.auth,DEVICE,self.request()),'BUSY',submitted)
        self.reply=dict(transport='error',code='UNSUPPORTED',request_submitted=False)
        await self.refused(self.service.read(self.auth,DEVICE,self.request()),'UNSUPPORTED',False)
        self.reply=dict(transport='error',code='private error details',request_submitted=False)
        await self.refused(self.service.read(self.auth,DEVICE,self.request()),'UNAVAILABLE',True)

    async def test_changed_or_malformed_inner_reply_is_never_displayed(self):
        normal=await self.route(dict(action='workspace_read',frame=dict(request_id='a'*32,payload={'operation':'prepare'})))
        import copy
        for key,value in [('request_id','d'*32),('worker_instance','wrong'),('schema',True)]:
            self.reply=copy.deepcopy(normal);self.reply['reply'][key]=value
            await self.refused(self.service.read(self.auth,DEVICE,self.request()),'CHANGED',True)
        for key,value in [('scope_id','f'*64),('project_id','other')]:
            self.reply=copy.deepcopy(normal);self.reply['reply']['result'][key]=value
            await self.refused(self.service.read(self.auth,DEVICE,self.request()),'CHANGED',True)

    async def test_status_never_reuses_held_endpoint_or_nonready_operations(self):
        self.reply=dict(transport='status',scope_id=self.scope,device=DEVICE,state='held',worker_instance=INSTANCE,failure_code='DENIED')
        await self.refused(self.service.describe(self.auth,DEVICE),'UNAVAILABLE')
        self.reply['worker_instance']=None
        result=await self.service.describe(self.auth,DEVICE)
        self.assertEqual([],result['operations'])

    async def test_closed_requests_and_oversized_reply_refuse_without_retry(self):
        for value in (True,[],None,{},dict(self.request(),schema=True),dict(self.request(),operation=[])):
            await self.refused(self.service.read(self.auth,DEVICE,value),'INVALID')
        self.assertFalse(self.calls)
        self.reply={'transport':'reply','reply':{'large':'x'*(c.MAX_REPLY+1)}}
        await self.refused(self.service.read(self.auth,DEVICE,self.request()),'TOO_LARGE',True)
        self.assertEqual(1,len(self.calls))

    async def test_four_inflight_limit_refuses_without_queued_fifth_read(self):
        entered=asyncio.Event(); release=asyncio.Event(); count=0
        async def waiting(payload):
            nonlocal count
            count+=1
            if count==4:entered.set()
            await release.wait();return await self.route(payload)
        service=c.CoordinatorReadService('fixture-authority',{DEVICE:waiting},grant=self.lookup)
        tasks=[asyncio.create_task(service.read(self.auth,DEVICE,self.request())) for _ in range(4)]
        await asyncio.wait_for(entered.wait(),2)
        await self.refused(service.read(self.auth,DEVICE,self.request()),'BUSY')
        release.set();await asyncio.gather(*tasks)
        self.assertEqual(4,count)

    def test_scope_matches_native_windows_domain_and_redacts_identity(self):
        raw=json.dumps(['signal-grove-worker-read-v1','fixture-authority',CONTEXT.subject,CONTEXT.profile_id],ensure_ascii=True,separators=(',',':')).encode()
        import hashlib
        self.assertEqual(hashlib.sha256(raw).hexdigest(),self.scope)
        self.assertNotIn(CONTEXT.profile_id,repr(CONTEXT));self.assertNotIn(self.scope,repr(self.service))
        for data in (b'{"a":1,"a":2}',b'{"a":NaN}',b'[]',b'\xff'):
            with self.assertRaises(c.ReadRefused):c.decode(data)


class SshTransportTest(unittest.IsolatedAsyncioTestCase):
    def payload(self, action):
        value=dict(action=action,scope_id='e'*64)
        if action=='workspace_read':value['frame']={}
        return value

    async def test_transport_rejects_execution_actions_and_extra_fields_before_spawn(self):
        from unittest.mock import AsyncMock, patch
        with patch('asyncio.create_subprocess_exec',new_callable=AsyncMock) as spawn:
            for payload in ({'action':'start'},dict(self.payload('workspace_read'),command='untrusted')):
                with self.assertRaises(c.ReadRefused) as caught:
                    await SingleCallSsh((sys.executable,'-B','-c','pass'))(payload)
                self.assertFalse(caught.exception.request_submitted)
            spawn.assert_not_called()

    async def test_real_child_handles_reply_above_legacy_64_kib_without_disk(self):
        code="import sys,json;data=json.load(sys.stdin);print(json.dumps({'ok':True,'result':{'received':data['action'],'text':'中'*40000}}))"
        transport=SingleCallSsh((sys.executable,'-B','-c',code))
        result=await transport(self.payload('workspace_status'))
        self.assertEqual(40000,len(result['text']));self.assertEqual('workspace_status',result['received'])
        self.assertNotIn(code,repr(transport))

    async def test_nonzero_and_malformed_children_are_submitted_and_not_retried(self):
        for code in ("import sys;sys.stdin.read();print('bad json')", "import sys;sys.stdin.read();sys.exit(7)"):
            with self.assertRaises(c.ReadRefused) as caught:
                await SingleCallSsh((sys.executable,'-B','-c',code))(self.payload('workspace_read'))
            self.assertTrue(caught.exception.request_submitted)

    async def test_stdout_and_stderr_limits_kill_only_child_and_keep_uncertainty(self):
        for output,limit in [('stdout',c.MAX_REPLY),('stderr',8192)]:
            code=f"import sys;sys.stdin.read();sys.{output}.write('x'*{limit+1});sys.{output}.flush()"
            with self.assertRaises(c.ReadRefused) as caught:
                await SingleCallSsh((sys.executable,'-B','-c',code))(self.payload('workspace_read'))
            self.assertEqual('TOO_LARGE',caught.exception.code);self.assertTrue(caught.exception.request_submitted)

    async def test_exact_existing_worker_envelope_required_without_error_details(self):
        envelopes=[{'ok':False,'error_type':'PrivateFailure'}, {'ok':True,'result':{} ,'extra':'unexpected'},
                   {'ok':1,'result':{}}, {'ok':True,'result':[]}, {'transport':'status'}]
        for envelope in envelopes:
            code="import sys,json;sys.stdin.read();print("+repr(json.dumps(envelope))+")"
            with self.assertRaises(c.ReadRefused) as caught:
                await SingleCallSsh((sys.executable,'-B','-c',code))(self.payload('workspace_read'))
            self.assertEqual('UNAVAILABLE',caught.exception.code)
            self.assertTrue(caught.exception.request_submitted)
            self.assertNotIn('PrivateFailure',str(caught.exception))

    async def test_timeout_does_not_assert_remote_work_stopped(self):
        code="import sys,time;sys.stdin.read();time.sleep(3)"
        with self.assertRaises(c.ReadRefused) as caught:
            await SingleCallSsh((sys.executable,'-B','-c',code),timeout=.2)(self.payload('workspace_read'))
        self.assertTrue(caught.exception.request_submitted)

    async def test_failed_spawn_and_oversized_input_have_no_submission(self):
        for transport,payload in [(SingleCallSsh(('missing-signal-grove-executable','fixed')),self.payload('workspace_read')),
                                  (SingleCallSsh((sys.executable,'-B','-c','pass')),dict(self.payload('workspace_read'),frame={'large':'x'*c.MAX_REQUEST}))]:
            with self.assertRaises(c.ReadRefused) as caught:await transport(payload)
            self.assertFalse(caught.exception.request_submitted)

    async def test_cancelled_caller_gets_no_result_or_replay(self):
        transport=SingleCallSsh((sys.executable,'-B','-c','import sys,time;sys.stdin.read();time.sleep(3)'))
        task=asyncio.create_task(transport(self.payload('workspace_read')))
        await asyncio.sleep(.15);task.cancel()
        with self.assertRaises(asyncio.CancelledError):await task


class InstallerPolicyTest(unittest.TestCase):
    def test_source_anchor_drift_and_second_application_refuse(self):
        from server_extensions.coordinator_workspaces.install_adapter import patch_source
        source='from hermes_state_runtime import RuntimeStoreError\n\nclass C:\n    def handlers(self):\n        handlers = {}\n        handlers.update(stored_read_handlers(self))\n        return handlers\n'
        result=patch_source(source)
        self.assertIn('handlers.update(worker_workspace_handlers(self))',result)
        for changed in (result,source.replace('return handlers','return dict(handlers)'),source+source):
            with self.assertRaises(ValueError):patch_source(changed)
