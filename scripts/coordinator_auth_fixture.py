"""Opt-in fixture for actual Hermes HTTP/operator tickets; no production policy.

Only the two reviewed session-control anchors are adapted in memory. Login,
ticket issuance/redemption, TCP WebSocket, authority and dispatcher stay actual
Hermes. The downstream worker is a bounded synthetic route, never the owner host.
"""
from pathlib import Path
import hashlib,importlib,json,sys,types


class CoordinatorHttpFixture:
    DEVICE='FIXTURE-WORKER'
    PROJECT='fixture-project'
    INSTANCE='11111111-2222-4333-8444-555555555555'
    def __init__(self,source,extensions,home):
        self.home=home;self.runner=None;self.calls=[];self.outcomes=[];self.contexts=[];self.revision='1'*64;self.revoked=False;self.change_next=False;self.seen=set();self.sequence=0
        assert 'gateway.session_controls' not in sys.modules,'Install the explicit source adapter before the official runner imports controls'
        package=types.ModuleType('gateway.signal_grove_workspaces');package.__path__=[str(extensions)];sys.modules[package.__name__]=package
        self.c=importlib.import_module(package.__name__+'.coordinator')
        self.adapter=importlib.import_module(package.__name__+'.native_adapter')
        installer=importlib.import_module(package.__name__+'.install_adapter')
        raw=(source/'gateway/session_controls.py').read_bytes();patched=installer.patch_source(raw.decode('utf8'))
        controls=types.ModuleType('gateway.session_controls');controls.__file__=str(source/'gateway/session_controls.py');controls.__package__='gateway';sys.modules[controls.__name__]=controls
        exec(compile(patched,controls.__file__,'exec'),controls.__dict__)
        from gateway.owner_identity import host_owner_subject
        self.subject=host_owner_subject();self.authority_id='fixture-authority';self.scope=self.c.scope_for(self.authority_id,self.subject,str(home))
        self.operator=types.ModuleType('gateway.signal_grove_workspace_operator')
        self.operator.service=self.c.CoordinatorReadService(self.authority_id,{self.DEVICE:self.route},grant=self.grant)
        sys.modules[self.operator.__name__]=self.operator
        self.binding=dict(state='EXPLICIT_IN_MEMORY_SOURCE_ADAPTER',originalControlsSha256=hashlib.sha256(raw).hexdigest(),adaptedControlsSha256=hashlib.sha256(patched.encode()).hexdigest(),extensionFiles={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in extensions.glob('*.py')},workerTransport='synthetic-no-network-no-files')
    def grant(self,context,device):
        from gateway.owner_identity import host_owner_subject
        same=context.subject==host_owner_subject();profile=context.profile_id==str(self.home)
        self.contexts.append(dict(subjectIsActualHostOwner=same,defaultProfile=profile,instanceMatchesActualRunner=context.authority_instance==self.runner.session_runtime_descriptor['instance_id'],transportRegistered=context.transport_id in self.runner.session_authorities.require(Path(context.profile_id)).events))
        if self.revoked or not same or not profile or device!=self.DEVICE:raise self.c.ReadRefused('DENIED')
        assert self.contexts[-1]['instanceMatchesActualRunner'] and self.contexts[-1]['transportRegistered']
        return self.c.ReadGrant(self.revision,frozenset({self.PROJECT}))
    async def route(self,payload):
        self.calls.append(payload['action']);assert payload['scope_id']==self.scope
        if payload['action']=='workspace_status':return dict(transport='status',scope_id=self.scope,device=self.DEVICE,state='ready',worker_instance=self.INSTANCE,failure_code=None)
        frame=payload['frame'];request=frame['payload'];operation=request['operation']
        if frame['request_id'] in self.seen:return dict(transport='reply',reply=dict(schema=1,request_id=frame['request_id'],worker_instance=self.INSTANCE,status='error',code='DUPLICATE'))
        self.seen.add(frame['request_id']);common=dict(schema=1,scope_id=self.scope,project_id=self.PROJECT)
        if operation=='prepare':value=dict(**common,worker=dict(device=self.DEVICE,instance_id=self.INSTANCE),root='fixture-root',binding_token='b'*64,expires_in_seconds=300)
        elif operation=='list':value=dict(**common,worker_instance=self.INSTANCE,relative_path='',entries=[dict(relative_path='note.txt',name='note.txt',kind='file',entry_token='c'*64)],incomplete=False)
        elif operation=='preview':
            text='DO_NOT_DELIVER_CHANGED_POLICY' if self.change_next else 'Fixture preview only'
            value=dict(**common,worker_instance=self.INSTANCE,relative_path='note.txt',text=text,binary=False,preview_bytes=len(text.encode()),preview_sha256=hashlib.sha256(text.encode()).hexdigest())
        else:raise AssertionError('Unreviewed fixture operation')
        if self.change_next:self.revision='2'*64;self.change_next=False
        return dict(transport='reply',reply=dict(schema=1,request_id=frame['request_id'],worker_instance=self.INSTANCE,status='ok',result=value))
    def request(self,operation='prepare',**changes):
        self.sequence+=1
        result=dict(schema=1,request_id=f'{self.sequence:032x}',worker_instance=self.INSTANCE,operation=operation,project_id=self.PROJECT)
        if operation!='prepare':result.update(binding_token='b'*64,relative_path='' if operation=='list' else 'note.txt',entry_token=None if operation=='list' else 'c'*64)
        result.update(changes);return result
    def transport_metadata(self):
        from gateway.owner_identity import principal_subject
        authority=self.runner.session_authorities.require(self.home);result=[]
        for transport in authority.events.values():
            identity=getattr(transport,'auth_identity',None) or {}
            result.append(dict(ownerSubject=principal_subject(identity)==self.subject,profilePresent='profile_id' in identity,instancePresent='instance_id' in identity,instanceMatches=identity.get('instance_id')==authority.instance_id,nativeBootstrap=identity.get('native_bootstrap') is True,hostScoped=identity.get('profile_scope')=='host'))
        return result
    def completed(self,label):self.outcomes.append(label)
    async def exercise_http(self,client):
        from hermes_cli.gateway_client import GatewayRPCError
        async def describe(**extra):return await client.rpc(self.adapter.DESCRIBE,_timeout=10,device=self.DEVICE,**extra)
        async def read(request,**extra):return await client.rpc(self.adapter.READ,_timeout=10,device=self.DEVICE,request=request,**extra)
        def refused(result,code,submitted=False):assert result==dict(schema=1,state='error',code=code,request_submitted=submitted),result
        result=await describe();assert result.get('state')=='ok',dict(result=result,actualTransportMetadata=self.transport_metadata())
        assert result['payload']['operations']==['prepare','list','preview'] and result['payload']['project_ids']==[self.PROJECT];self.completed('actual-http-pkce-owner-describe')
        prepared=self.request();result=await read(prepared);assert result['state']=='ok' and result['payload']['result']['binding_token']=='b'*64;self.completed('actual-http-owner-prepare')
        result=await read(self.request('list'));assert result['payload']['result']['entries'][0]['relative_path']=='note.txt';self.completed('actual-http-owner-list')
        result=await read(self.request('preview'));assert result['payload']['result']['text']=='Fixture preview only';self.completed('actual-http-owner-preview')
        refused(await read(prepared),'DUPLICATE',True);self.completed('actual-http-completed-read-id-refused-without-replay')
        count=len(self.calls)
        refused(await describe(profile='field'),'DENIED');assert len(self.calls)==count;self.completed('actual-host-scoped-http-sibling-without-grant-denied')
        refused(await read(self.request(project_id='foreign-project')),'DENIED');assert len(self.calls)==count;self.completed('actual-http-foreign-project-denied-before-route')
        refused(await read(self.request(scope_id='f'*64)),'INVALID');assert len(self.calls)==count;self.completed('actual-http-inner-scope-claim-refused')
        try:await describe(subject=self.subject)
        except GatewayRPCError as exc:assert str(exc)=='invalid_params',str(exc)
        else:raise AssertionError('Outer identity claim accepted')
        assert len(self.calls)==count;self.completed('actual-http-contract-outer-identity-claim-refused')
        sys.modules.pop(self.operator.__name__)
        try:refused(await describe(),'UNAVAILABLE')
        finally:sys.modules[self.operator.__name__]=self.operator
        assert len(self.calls)==count;self.completed('actual-http-missing-operator-unavailable-before-route')
        self.change_next=True;result=await read(self.request('preview'));refused(result,'CHANGED',True);assert 'DO_NOT_DELIVER' not in json.dumps(result) and len(self.calls)==count+1;self.completed('actual-http-inflight-grant-change-drops-reply-preserves-submitted')
        self.revoked=True;count=len(self.calls);refused(await describe(),'DENIED');assert len(self.calls)==count;self.completed('actual-http-revoked-policy-denied-before-route')
    async def exercise_native_denial(self,client,label):
        count=len(self.calls);result=await client.rpc(self.adapter.DESCRIBE,_timeout=10,device=self.DEVICE)
        assert result==dict(schema=1,state='error',code='DENIED',request_submitted=False),result
        assert len(self.calls)==count;self.completed(label)
    def report(self):
        return dict(state='ACTUAL_HTTP_COORDINATOR_OPERATOR_BINDING_PASS',cases=len(self.outcomes),outcomes=self.outcomes,routeCalls=len(self.calls),contextObservations=self.contexts,sourceAdapter=self.binding,scope='Actual synthetic-IdP PKCE/refresh, issued HTTP/native tickets, TCP WebSocket, official runner/authority/dispatcher, with the declared in-memory two-anchor extension and synthetic worker. Not production IdP/Pi policy, live worker bytes, Android/TLS or clean upstream lifecycle acceptance.')
