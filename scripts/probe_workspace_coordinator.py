"""Actual native AuthorityConnection/SessionAuthority/closed-contract acceptance.

Use an immutable reviewed checkout, its existing dependency environment and a new
approved scratch directory. This builds only an in-memory source adapter. The
trusted constructor identities and synthetic worker replies do not certify HTTP
sign-in, deployed Pi policy, real worker content or Android. Windows asyncio's
own self-pipe is initialized before external network/child-process/write fences.
"""
from pathlib import Path
import asyncio, hashlib, importlib, json, os, sys, types

import argparse, subprocess
parser=argparse.ArgumentParser(description='Isolated actual Hermes native workspace-read contract/actor gate; no deployment or external network.')
parser.add_argument('--source-root',type=Path,required=True)
parser.add_argument('--work-root',type=Path,required=True)
args=parser.parse_args()
R=Path(__file__).resolve().parents[1]
S=args.source_root.resolve()
root=args.work_root.resolve()
assert not root.exists() and not root.is_relative_to(R), 'Use a fresh approved scratch root outside source'
env=os.environ.copy()
for key in ('GIT_DIR','GIT_WORK_TREE','GIT_INDEX_FILE'):env.pop(key,None)
def source_git(*args):return subprocess.check_output(['git','--no-optional-locks','-c','core.fsmonitor=false','-C',str(S),*args],env=env).decode('utf8')
assert source_git('rev-parse','HEAD').strip()=='3b421578588243f920886e6fe79a6faa87ea026e'
assert not source_git('status','--porcelain=v1','--untracked-files=all'), 'Preserve source drift for review'
root.mkdir()
home=root/'default'; field=home/'profiles'/'field'; field.mkdir(parents=True)
os.environ.update(HERMES_HOME=str(home),TEMP=str(root),TMP=str(root),PYTHONDONTWRITEBYTECODE='1',HERMES_TEST_ISOLATION='1')
sys.path[:0]=[str(S),str(R)]
loop=asyncio.new_event_loop()  # Windows asyncio's own socketpair, before the operation audit
violations=[]
def audit(event,args):
    if event in {'socket.connect','socket.bind','socket.sendto','socket.sendmsg','subprocess.Popen','os.system','os.exec','os.posix_spawn','os.kill','os.killpg'}:
        violations.append(event); raise PermissionError('Native read fixture denies execution/network')
    if event=='open' and not isinstance(args[0],int):
        path=Path(args[0]);mode=args[1];flags=args[2]
        writing=(isinstance(mode,str) and any(c in mode for c in 'wax+')) or (isinstance(flags,int) and flags & (os.O_WRONLY|os.O_RDWR|os.O_CREAT|os.O_TRUNC|os.O_APPEND))
        if writing and not path.resolve().is_relative_to(root.resolve()):
            violations.append('write-outside-fixture');raise PermissionError('Outside isolated write root')
    if event in {'os.mkdir','os.remove','os.rmdir','os.chmod','os.rename','os.link','os.symlink'}:
        paths=args[:2] if event in {'os.rename','os.link','os.symlink'} else args[:1]
        if any(not isinstance(p,(str,bytes,os.PathLike)) or not Path(os.fsdecode(p)).resolve().is_relative_to(root.resolve()) for p in paths):
            violations.append('mutation-outside-fixture');raise PermissionError('Outside isolated mutation root')
sys.addaudithook(audit)
from hermes_state import SessionDB
from hermes_state_runtime import begin_runtime_epoch
from gateway.session_authority import SessionAuthority
from gateway.session_authorities import SessionAuthorities
from gateway.owner_identity import host_owner_subject
from server_extensions.coordinator_workspaces.install_adapter import patch_source
package=types.ModuleType('gateway.signal_grove_workspaces');package.__path__=[str(R/'server_extensions/coordinator_workspaces')]
sys.modules[package.__name__]=package
c=importlib.import_module(package.__name__+'.coordinator')
adapter=importlib.import_module(package.__name__+'.native_adapter')
from tui_gateway.contracts import registry as contract_registry
assert contract_registry.STRICT is True
controls=types.ModuleType('gateway.session_controls'); controls.__file__=str(S/'gateway/session_controls.py'); controls.__package__='gateway'
sys.modules[controls.__name__]=controls
raw=(S/'gateway/session_controls.py').read_bytes();patched=patch_source(raw.decode('utf8'))
exec(compile(patched,controls.__file__,'exec'),controls.__dict__)
runner=types.SimpleNamespace(_draining=False,adapters={},_cached_agent_for=lambda route:None)
registry=runner.session_authorities=SessionAuthorities(home,multiplexed=True)
databases=[]
def authority(path,instance):
    db=SessionDB(db_path=path/'state.db'); databases.append(db)
    epoch=begin_runtime_epoch(db,instance_id=instance)
    obj=SessionAuthority(runner,profile_id=str(path),instance_id=instance,db=db,epoch=epoch)
    registry.add(path,obj,None if path==home else 'field');return obj
owner=authority(home,'fixture-owner'); secondary=authority(field,'fixture-field')
DEVICE='FIXTURE-WORKER';INSTANCE='11111111-2222-4333-8444-555555555555'
subject=host_owner_subject();calls=[];revoked=False;retire_in_route=False
def grant(context,device):
    if revoked or context.subject!=subject or context.profile_id!=str(home):raise c.ReadRefused('DENIED')
    return c.ReadGrant('e'*64,frozenset({'fixture-project'}))
async def route(payload):
    calls.append(payload)
    if retire_in_route:owner.retired=True
    scope=c.scope_for('fixture-authority',subject,str(home))
    if payload['action']=='workspace_status':return dict(transport='status',scope_id=scope,device=DEVICE,state='ready',worker_instance=INSTANCE,failure_code=None)
    f=payload['frame']; result=dict(schema=1,scope_id=scope,project_id='fixture-project',worker=dict(device=DEVICE,instance_id=INSTANCE),root='fixture-root',binding_token='b'*64,expires_in_seconds=300)
    return dict(transport='reply',reply=dict(schema=1,request_id=f['request_id'],worker_instance=INSTANCE,status='ok',result=result))
operator=types.ModuleType('gateway.signal_grove_workspace_operator')
operator.service=c.CoordinatorReadService('fixture-authority',{DEVICE:route},grant=grant)
sys.modules[operator.__name__]=operator
def connect(*,operator_gate=True,**changes):
    identity=dict(provider='basic',user_id='fixture-login',profile_id=str(home),instance_id=owner.instance_id,profile_scope='host')
    identity.update(changes)
    return controls.AuthorityConnection(owner,types.SimpleNamespace(),identity,operator=operator_gate)
def request(**changes):
    body=dict(schema=1,request_id='a'*32,worker_instance=INSTANCE,operation='prepare',project_id='fixture-project');body.update(changes);return body
async def call(connection,method,params):
    result=await connection.dispatch(dict(jsonrpc='2.0',id='fixture',method=method,params=params))
    assert result['id']=='fixture'
    if 'result' in result: adapter.ReadResult.model_validate(result['result'])
    return result
async def main():
    global retire_in_route
    outcomes=[];peer=connect()
    good=await call(peer,adapter.DESCRIBE,dict(device=DEVICE));assert good['result']['state']=='ok';outcomes.append('actual-owner-describe')
    good=await call(peer,adapter.READ,dict(device=DEVICE,request=request()));assert good['result']['payload']['status']=='ok';outcomes.append('actual-owner-prepare')
    count=len(calls)
    sys.modules.pop(operator.__name__)
    unavailable=await call(peer,adapter.DESCRIBE,dict(device=DEVICE))
    assert unavailable['result']==dict(schema=1,state='error',code='UNAVAILABLE',request_submitted=False)
    assert len(calls)==count;outcomes.append('absent-operator-policy-unavailable-without-route')
    sys.modules[operator.__name__]=operator
    for name,foreign in [('nonoperator',connect(operator_gate=False)),('api-subject',connect(provider='local',user_id='api')),
                         ('foreign-native-subject',connect(provider='local',user_id='sid:S-1-5-21-1-2-3-9999')),
                         ('wrong-instance',connect(instance_id='other-instance'))]:
        result=await call(foreign,adapter.DESCRIBE,dict(device=DEVICE));assert result['result']['code']=='DENIED',(name,result)
        assert len(calls)==count;outcomes.append(name+'-denied-before-route')
    result=await call(peer,adapter.DESCRIBE,dict(device=DEVICE,profile='field'));assert result['result']['code']=='DENIED';assert len(calls)==count;outcomes.append('actual-sibling-profile-denied-before-route')
    result=await call(peer,adapter.READ,dict(device=DEVICE,request=request(scope_id='f'*64)));assert result['result']['code']=='INVALID';assert len(calls)==count;outcomes.append('client-scope-refused')
    result=await call(peer,adapter.DESCRIBE,dict(device=DEVICE,subject=subject));assert 'error'in result and result['error']['data']['reason']=='invalid_params';assert len(calls)==count;outcomes.append('actual-contract-extra-claims-refused')
    owner.events.pop(peer.actor.transport_id)
    result=await call(peer,adapter.DESCRIBE,dict(device=DEVICE));assert result['result']['code']=='DENIED';assert len(calls)==count;outcomes.append('closed-transport-denied')
    peer=connect();retire_in_route=True
    result=await call(peer,adapter.READ,dict(device=DEVICE,request=request(request_id='d'*32)))
    assert result['result']==dict(schema=1,state='error',code='DENIED',request_submitted=True);outcomes.append('authority-retired-during-read-drops-contents')
    for db in databases:
        for table in ('sessions','messages','session_admissions','worker_executions'):
            assert db._read_one('SELECT COUNT(*) AS n FROM '+table)['n']==0,table
    assert not violations,violations
    result=dict(state='ACTUAL_NATIVE_AUTHORITY_DISPATCH_AND_CLOSED_CONTRACT_PASS',cases=len(outcomes),outcomes=outcomes,routeCalls=len(calls),sourceCommit='3b421578588243f920886e6fe79a6faa87ea026e',sourceControlsSha256=hashlib.sha256(raw).hexdigest(),patchedControlsSha256=hashlib.sha256(patched.encode()).hexdigest(),extensionFiles={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in (R/'server_extensions/coordinator_workspaces').glob('*.py')},noNetworkOrExecution=True,emptyAdmissionWorkerAndConversationTables=True,productionChanged=False,scope='Actual native AuthorityConnection/SessionAuthority/contract models with isolated SQLite, a bounded synthetic worker transport and trusted constructor identities. Not HTTP/PKCE, deployed Pi or Android acceptance.')
    (root/'report.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');print(json.dumps({k:v for k,v in result.items() if k not in {'outcomes','extensionFiles','scope'}}),flush=True)
try: loop.run_until_complete(main())
finally: loop.close()
