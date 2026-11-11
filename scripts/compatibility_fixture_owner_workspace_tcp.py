"""Serve one bounded actual Hermes owner to an explicitly selected Android test.

Immutable source, existing dependencies and a fresh E: root only. The actual
native owner keeps one disposable live session open. Android uses a local test
IdP through real prefixed HTTP/PKCE/tickets/authority; exactly one reviewed host
workspace mutation is allowed. Optional outer-only ACK loss proves preserved
uncertainty; the host checks its exact receipt independently. No inference, tools, worker, production credential
or server changes. Exit 2 retains a core proof with a shutdown finding, not full
lifecycle, production IdP, physical-device or deployed-fleet acceptance.
"""
from pathlib import Path
import re
import argparse, asyncio, atexit, datetime, hashlib, ipaddress, json, logging, os, subprocess, sys, traceback, urllib.parse, weakref

def workspace_fixture_rpc_allowed(method, params, *, session_id, directory, revision, generation, mutation_claimed):
    """Closed disposable-session scope. Never forward work submission or another resource."""
    if not isinstance(params,dict):return False
    if method in ('runtime.describe','gateway.ping'):return params=={}
    if method=='session.list':return set(params)=={'scope','limit'} and params['scope'] in ('stored','live') and type(params['limit']) is int and params['limit']==200
    if method=='session.resume':return params=={'session_id':session_id,'omit_messages':True} and type(params.get('omit_messages')) is bool
    if method=='session.events.since':
        return (set(params)=={'session_id','last_sequence','replay_epoch'} and params['session_id']==session_id
            and type(params['last_sequence']) is int and 0<=params['last_sequence']<2**63
            and isinstance(params['replay_epoch'],str) and 0<len(params['replay_epoch'])<=256)
    if method=='session.mutate':
        return (not mutation_claimed and set(params)=={'session_id','request_id','expected_revision','expected_generation','operation','payload'}
            and params['session_id']==session_id and params['operation']=='workspace' and params['payload']=={'cwd':directory}
            and type(params['expected_revision']) is int and params['expected_revision']==revision
            and type(params['expected_generation']) is int and params['expected_generation']==generation
            and isinstance(params['request_id'],str) and re.fullmatch(r'[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}',params['request_id']) is not None)
    return False

def catalog_fixture_request_blocked(url,data,headers,method,callers):
    """Refuse only named public read-only metadata before DNS; never produce a catalog."""
    manifest={
        'https://hermes-agent.nousresearch.com/docs/api/model-catalog.json',
        'https://raw.githubusercontent.com/NousResearch/hermes-agent/main/website/static/api/model-catalog.json'}
    providers={
        'https://web-assets.nousresearch.com/dynamic/portal.nousresearch.com/api/nous/recommended-models',
        'https://portal.nousresearch.com/api/nous/recommended-models',
        'https://openrouter.ai/api/v1/models'}
    return (method=='GET' and data is None and isinstance(headers,dict)
        and not {'authorization','cookie'} & {str(key).lower() for key in headers}
        and ((url in manifest and ('hermes_cli.model_catalog','_fetch_manifest') in callers)
            or (url in providers and ('hermes_cli.models','_urlopen_model_catalog_request') in callers)))

# Immutable review targets only: a new release is not automatically trusted.
REVIEWED_COMMITS = (
    '2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19',
    '3b421578588243f920886e6fe79a6faa87ea026e',
)
p = argparse.ArgumentParser()
p.add_argument('--source-commit', choices=REVIEWED_COMMITS, default=REVIEWED_COMMITS[0],
               help='Exact reviewed checkout; defaults to the original owner-runtime pin.')
p.add_argument('--transport', choices=('http',), default='http',
               help='HTTP uses a local synthetic IdP through actual Hermes PKCE/refresh/WS-ticket routes.')
p.add_argument('--source-root', type=Path, required=True)
p.add_argument('--dependency-site', type=Path, required=True)
p.add_argument('--work-root', type=Path, required=True)
p.add_argument('--android-api',type=int,choices=(26,37),required=True)
p.add_argument('--emit-host-signals',action='store_true',help='Emit three explicit public metadata signals through the real upstream producer on describe.')
effect=p.add_mutually_exclusive_group()
effect.add_argument('--drop-workspace-ack',action='store_true',help='Drop only the actual committed workspace ACK at the outer transport; never alter dispatch or authority.')
effect.add_argument('--workspace-refusal',choices=('turn-lease','compression-lock'),help='Hold a real fixture-local transcript lease/lock through the actual mutation refusal; no inference or fake response.')
p.add_argument('--owned-child', action='store_true', help=argparse.SUPPRESS)
a = p.parse_args()
source, site, root = a.source_root.resolve(), a.dependency_site.resolve(), a.work_root.resolve()
COMMIT = a.source_commit
assert os.name == 'nt' and sys.flags.isolated and sys.flags.no_site and sys.dont_write_bytecode
assert source.is_dir() and site.is_dir() and not root.exists()
assert source.drive.upper() == root.drive.upper() == 'E:'
assert not any(x.is_relative_to(y) for x, y in ((root, source), (source, root), (root, site), (site, root)))
def supervise():
    # One owned attempt: never recycle a root or retry uncertain work.
    receipt=root.with_suffix('.supervisor.json');log=root.with_suffix('.supervisor.log')
    assert not receipt.exists() and not log.exists()
    command=[sys.executable,'-I','-S','-B',str(Path(__file__).resolve()),'--owned-child',
             '--source-commit',COMMIT,'--transport',a.transport,'--android-api',str(a.android_api),'--source-root',str(source),'--dependency-site',str(site),'--work-root',str(root)]
    if a.emit_host_signals:command.append('--emit-host-signals')
    if a.drop_workspace_ack:command.append('--drop-workspace-ack')
    if a.workspace_refusal:command.extend(['--workspace-refusal',a.workspace_refusal])
    child_env={k:v for k,v in os.environ.items() if k.upper() in ('SYSTEMROOT','WINDIR','PATH','PATHEXT')}
    child_env.update(TEMP=str(root.parent),TMP=str(root.parent))
    observation=dict(state='OWNED_ATTEMPT_STARTING',command=command,sourceCommit=COMMIT,transport=a.transport,controlledHostSignals=a.emit_host_signals,dropWorkspaceAck=a.drop_workspace_ack,workspaceRefusal=a.workspace_refusal,harnessSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())
    def save():receipt.write_text(json.dumps(observation,indent=2)+'\n',encoding='utf-8')
    save()
    with log.open('xb') as stream:
        child=subprocess.Popen(command,env=child_env,stdin=subprocess.DEVNULL,stdout=stream,
                               stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
        observation.update(state='OWNED_ATTEMPT_RUNNING',pid=child.pid);save()
        try:code=child.wait(timeout=1200)
        except subprocess.TimeoutExpired:
            observation.update(state='UNKNOWN_INSPECT_EXACT_PID_AND_ROOT_NO_REPLAY');save()
            return 3
    observation.update(state='TERMINAL_REVIEW_PENDING',exitCode=code);save()
    if code!=0:
        observation['state']='FAILED_CHILD_RETAINED';save();return 1
    core=json.loads((root/'report.json').read_text(encoding='utf-8'))
    final=json.loads((root/'shutdown-diagnostics.json').read_text(encoding='utf-8'))
    assert core['harnessSha256']==observation['harnessSha256']
    assert core['state']=='PASS_ANDROID_REAL_OWNER_WORKSPACE_TRANSPORT' and core['importedModules']
    assert not core['denied'] and not final['denied']
    # After actual process exit, verify the one exact fixture session and mutation receipt through read-only SQLite.
    import sqlite3
    stores=[]
    for db_path in (root/'hermes-home/state.db',root/'hermes-home/profiles/field/state.db'):
        with sqlite3.connect('file:'+db_path.as_posix()+'?mode=ro',uri=True) as db:
            assert db.execute('PRAGMA quick_check').fetchall()==[('ok',)]
            tables={row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
            counts={name:db.execute('SELECT COUNT(*) FROM '+name).fetchone()[0]
                    for name in ('sessions','session_admissions','hosted_rooms') if name in tables}
            assert {'sessions','session_admissions'}<=counts.keys()
            expected = core['workspace'] if db_path.parent.name=='hermes-home' else None
            assert counts==dict(sessions=int(expected is not None),session_admissions=0,hosted_rooms=0)
            assert db.execute('SELECT COUNT(*) FROM messages').fetchone()[0]==0
            if expected:
                rows=db.execute('SELECT id,cwd,runtime_revision,runtime_generation FROM sessions').fetchall()
                assert rows==[(expected['sessionId'],expected['cwd'],expected['revision'],expected['generation'])]
                receipts=db.execute("SELECT value FROM state_meta WHERE key GLOB 'gateway.mutation.v1.*'").fetchall()
                assert len(receipts)==(0 if a.workspace_refusal else 1)
                if not a.workspace_refusal:assert json.loads(receipts[0][0])['result']==expected['ack']
            for table in ('session_turn_leases','compression_locks'):
                assert db.execute('SELECT COUNT(*) FROM '+table).fetchone()[0]==0
            stores.append(dict(profile='default' if db_path.parent.name=='hermes-home' else 'field',counts=counts,quickCheck='ok'))
    assert not (root/'hermes-home/gateway.pid').exists()
    observation.update(coreTransportAccepted=True,stores=stores,shutdownWarnings=len(final['warnings']),
        importedModules=len(core['importedModules']),sourceCommit=COMMIT,
        scope='One bounded Android client over prefixed real-owner HTTP/PKCE/ticket/canonical workspace and Room proof; synthetic IdP only; no production IdP, Pi, inference/tool/worker or complete lifecycle acceptance.')
    clean=final['state']=='CLEAN_EXIT_OBSERVATION' and not final['warnings']
    observation['state']='PASS_FINITE_OWNER_RUNTIME' if clean else 'CORE_PASS_SHUTDOWN_FINDING_OPEN'
    save();print(json.dumps({k:observation[k] for k in ('state','transport','importedModules','shutdownWarnings','sourceCommit','stores')}))
    return 0 if clean else 2

if not a.owned_child:
    sys.exit(supervise())

root.mkdir(parents=True, exist_ok=False)
user, temp, launch = root/'account', root/'temp', root/'hermes-home'
secondary = launch/'profiles'/'field'
workspace_cwds=[root/'workspace'/'project-a',root/'workspace'/'project-b']
for directory in workspace_cwds:directory.mkdir(parents=True,exist_ok=False)
workspace_probes=[]
workspace_git={subprocess.list2cmdline(['git','-C',spelling,*tail])
    for directory in workspace_cwds for spelling in [str(directory),directory.as_posix()]
    for tail in [('branch','--show-current'),('rev-parse','--short','HEAD'),('rev-parse','--show-toplevel'),('rev-parse','--path-format=absolute','--git-common-dir')]}
for path in (user/'AppData/Local', user/'AppData/Roaming', temp, launch, secondary, root/'runtime', root/'locks'):
    path.mkdir(parents=True, exist_ok=True)
git_env = {k:v for k,v in os.environ.items() if k.upper() in ('SYSTEMROOT','WINDIR','PATH','PATHEXT')}
git_env.update(TEMP=str(temp), TMP=str(temp), GIT_CONFIG_NOSYSTEM='1', GIT_CONFIG_GLOBAL=os.devnull,
               GIT_OPTIONAL_LOCKS='0', GIT_NO_LAZY_FETCH='1', GIT_TERMINAL_PROMPT='0')
def git(*args):
    q=subprocess.run(['git','--no-optional-locks','-c','safe.directory='+str(source),'-c','core.fsmonitor=false','-C',str(source),*args],
                     env=git_env,capture_output=True,check=True,timeout=45)
    return q.stdout
assert git('rev-parse','HEAD').decode().strip()==COMMIT
assert not git('status','--porcelain','--untracked-files=all').strip()
tracked={}
for line in git('ls-files','--stage','-z').split(b'\0'):
    if line:
        entry,name=line.split(b'\t',1);mode,oid,stage=entry.decode().split()
        if mode=='100644':tracked[name.decode()]=oid
assert 'gateway/run_runtime.py' in tracked
before={name:hashlib.sha256((source/name).read_bytes()).hexdigest() for name in tracked if name.endswith('.py')}
system={k:v for k,v in os.environ.items() if k.upper() in ('SYSTEMROOT','WINDIR')}
os.environ.clear();os.environ.update(system)
os.environ.update(TEMP=str(temp),TMP=str(temp),TMPDIR=str(temp),USERPROFILE=str(user),HOME=str(user),
    APPDATA=str(user/'AppData/Roaming'),LOCALAPPDATA=str(user/'AppData/Local'),HERMES_HOME=str(launch),
    HERMES_RUNTIME_DIR=str(root/'runtime'),HERMES_GATEWAY_LOCK_DIR=str(root/'locks'),
    HERMES_DISABLE_LAZY_INSTALLS='1',HERMES_TEST_ISOLATION='1',HERMES_STARTUP_WARMUP_TIMEOUT='0',
    PYTHONDONTWRITEBYTECODE='1',NO_PROXY='127.0.0.1,localhost',
    GIT_CONFIG_NOSYSTEM='1',GIT_CONFIG_GLOBAL=os.devnull,GIT_OPTIONAL_LOCKS='0',GIT_NO_LAZY_FETCH='1',GIT_TERMINAL_PROMPT='0')
os.chdir(root);sys.path[:0]=[str(source),str(site)]
reads=[root,source,site,Path(sys.base_prefix).resolve(),Path(__file__).resolve()]
denied=[]; bound=weakref.WeakSet(); sockets=[]; version_probes=[]; blocked_catalog_reads=[]
def inside(value, roots):return any(value.is_relative_to(x) for x in roots)
def path_of(value):return Path(os.fsdecode(value)).resolve() if isinstance(value,(str,bytes,os.PathLike)) else None
def refuse(event,target=None):
    detail=dict(event=event,targetName=target.name if target else None)
    frame=sys._getframe(1);frames=[]
    for _ in range(18):
        if frame is None:break
        frames.append(dict(module=frame.f_globals.get('__name__'),function=frame.f_code.co_name,line=frame.f_lineno))
        frame=frame.f_back
    detail['frames']=frames
    denied.append(detail);raise PermissionError('Owner fixture boundary: '+event)
missing={}
for name in ('/proc/1/cgroup','/proc/1/stat','/proc/self/mountinfo','/proc/version','/proc/stat','/proc/self/status','/proc/meminfo','/proc/sys/kernel/random/boot_id',f'/proc/{os.getpid()}/stat',f'/proc/{os.getpid()}/cmdline'):
    target=Path(root.drive+name.replace('/','\\'));assert not target.exists();missing[name]=target;missing[str(Path(name))]=target
manifest=source.parent/'manifest.json';assert not manifest.exists();missing[str(manifest)]=manifest
def audit(event,args):
    if event=='urllib.Request':
        callers=[];frame=sys._getframe(1)
        for _ in range(20):
            if frame is None:break
            callers.append((frame.f_globals.get('__name__'),frame.f_code.co_name));frame=frame.f_back
        if catalog_fixture_request_blocked(*args,callers):
            blocked_catalog_reads.append(dict(url=args[0],method=args[3],reason='public_metadata_intentionally_unavailable_before_dns'))
            raise PermissionError('Public model catalogs are outside this offline workspace fixture')
    if event=='socket.getaddrinfo':
        try:local=ipaddress.ip_address(args[0]).is_loopback
        except ValueError:local=args[0]=='localhost'
        if not local:refuse('nonloopback-resolution')
    if event=='subprocess.Popen':
        argv=args[1];cwd=args[2]
        allowed={('git','rev-parse','HEAD'),('git','branch','--show-current'),('git','log','-1','--format=%ct','HEAD'),
            ('git','--no-optional-locks','status','--porcelain','-uno'),('git','tag','--merged','HEAD','--list','v[0-9]*'),
            ('git','describe','--tags','--long','--match','v2[0-9][0-9][0-9].*','HEAD'),('git','rev-parse','--short=7','HEAD')}
        frames=[];frame=sys._getframe(1)
        for _ in range(12):
            if frame is None:break
            frames.append(frame.f_globals.get('__name__'));frame=frame.f_back
        probe=next((x for x in allowed if argv==subprocess.list2cmdline(list(x)) or isinstance(argv,(list,tuple)) and tuple(argv)==x),None)
        if probe and cwd and Path(cwd).resolve()==source and 'hermes_cli.version_info' in frames:
            version_probes.append(list(probe));raise PermissionError('Version metadata subprocess intentionally blocked in fixture')
        safe_probes={('git','config',scope,'-z','--get-all','safe.directory') for scope in ('--system','--global')}
        source_probes={('git','rev-parse','HEAD'),('git','rev-parse','--abbrev-ref','HEAD'),('git','remote','get-url','origin'),('git','status','--porcelain')}
        probe=next((x for x in safe_probes|source_probes if argv==subprocess.list2cmdline(list(x)) or isinstance(argv,(list,tuple)) and tuple(argv)==x),None)
        if probe and ((probe in safe_probes and cwd is None and 'hermes_cli._subprocess_compat' in frames)
                or (probe in source_probes and cwd and Path(cwd).resolve()==source and 'hermes_cli.source_check' in frames)):
            version_probes.append(list(probe));raise PermissionError('Source metadata subprocess intentionally blocked in fixture')
        command=subprocess.list2cmdline(argv) if isinstance(argv,(list,tuple)) else argv
        if command in workspace_git and 'tui_gateway.git_probe' in frames:
            workspace_probes.append(command);return
        platform_frame=sys._getframe(1);platform_ver=False
        for _ in range(12):
            if platform_frame is None:break
            if (platform_frame.f_globals.get('__name__'),platform_frame.f_code.co_name)==('platform','_syscmd_ver'):platform_ver=True;break
            platform_frame=platform_frame.f_back
        command=argv if isinstance(argv,str) else subprocess.list2cmdline(argv)
        cmd=str(Path(os.environ['SYSTEMROOT'])/'System32'/'cmd.exe')
        platform_commands={(cmd+' /c "'+tail+'"').lower() for tail in ('ver','command /c ver','cmd /c ver')}
        if platform_ver and command.lower() in platform_commands:
            version_probes.append(dict(kind='platform_ver_intentionally_blocked',commandSha256=hashlib.sha256(command.encode()).hexdigest()))
            raise PermissionError('Exact platform version metadata subprocess intentionally blocked')
        refuse(event)
    if event.startswith(('os.exec','os.spawn')) or event in ('os.system','os.startfile','os.kill','os.killpg','sqlite3.enable_load_extension','sqlite3.load_extension','socket.sendto','socket.sendmsg','winreg.CreateKey','winreg.SetValue','winreg.DeleteKey','winreg.DeleteValue'):
        refuse(event)
    if event in ('socket.bind','socket.connect'):
        address=args[1]
        if not isinstance(address,tuple) or len(address)<2:refuse(event)
        try:assert ipaddress.ip_address(address[0]).is_loopback
        except (ValueError,AssertionError):refuse('nonloopback-socket')
        if event=='socket.bind':
            if address[1]!=0:refuse('fixed-port-bind')
            bound.add(args[0])
        else:
            ports=[]
            for sock in tuple(bound):
                try:ports.append(sock.getsockname()[1])
                except OSError:pass
            if address[1] not in ports or address[1]==0:refuse('connect-to-unowned-port')
        sockets.append(event)
    if event=='open':
        target=path_of(args[0]);mode=args[1] or '';flags=args[2] or 0
        write=any(c in mode for c in 'wax+') or bool(flags&(os.O_WRONLY|os.O_RDWR|os.O_CREAT|os.O_TRUNC|os.O_APPEND))
        if not write and str(args[0]) in missing and target==missing[str(args[0])] and not target.exists():return
        if target and str(args[0]).lower()!=os.devnull.lower() and not inside(target,[root] if write else reads):refuse('file-write' if write else 'file-read',target)
    if event=='os.chdir' and not Path(args[0]).resolve().is_relative_to(root):refuse(event)
    if event in ('os.mkdir','os.remove','os.rmdir','os.rename','os.symlink','os.link'):
        for value in args[:2] if event in ('os.rename','os.symlink','os.link') else args[:1]:
            target=path_of(value)
            if target and not target.is_relative_to(root):refuse(event,target)
    if event=='sqlite3.connect' and str(args[0])!=':memory:':
        value=str(args[0])
        if value.startswith('file:'):
            u=urllib.parse.urlsplit(value)
            if u.netloc not in ('','localhost'):refuse('sqlite-authority')
            value=urllib.parse.unquote(u.path)
            if value.startswith('/') and len(value)>2 and value[2]==':':value=value[1:]
        if not Path(value).resolve().is_relative_to(root):refuse('sqlite-outside-fixture')
sys.addaudithook(audit)
record=dict(state='STARTING_FINITE_REAL_OWNER_PROBE',commit=COMMIT,transport=a.transport,pid=os.getpid(),phases=[],denied=denied,
    harnessSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())
shutdown_warnings=[]
class ShutdownDiagnostics(logging.Handler):
    def emit(self,entry):
        if 'closed while a' in entry.getMessage() and 'reopening' in entry.getMessage():
            shutdown_warnings.append(dict(message=entry.getMessage(),stack=traceback.format_stack()))
logging.getLogger().addHandler(ShutdownDiagnostics())
def observe_process_exit():
    (root/'shutdown-diagnostics.json').write_text(json.dumps(dict(warnings=shutdown_warnings,denied=denied,blockedCatalogReads=blocked_catalog_reads,
        state='CLEAN_EXIT_OBSERVATION' if not shutdown_warnings and not denied else 'FOLLOW_UP_REQUIRED'),indent=2)+'\n',encoding='utf-8')
atexit.register(observe_process_exit)
def phase(name,**details):
    record['phases'].append(dict(name=name,at=datetime.datetime.now(datetime.timezone.utc).isoformat(),**details))
    (root/'attempt.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
    print(name,flush=True)

async def run():
    from hermes_cli.config import atomic_config_write
    for profile_home in (launch,secondary):
        atomic_config_write(profile_home/'config.yaml',{'gateway':{'multiplex_profiles':True,'loop_watchdog':False},
            'dashboard':{'startup_orphan_sweep':False},'agent':{'environment_probe':False},'terminal':{'cwd':str(root)},'model_catalog':{'enabled':False}})
    from gateway.run import GatewayRunner,_refresh_host_gateway_record
    from gateway.config import GatewayConfig
    from gateway import run_bootstrap as boot,run_runtime as runtime,run_api
    from gateway.runtime_ownership import process_ownership
    from gateway import host_rendezvous as hr,status
    from hermes_cli.gateway_runtime import discover_gateway_endpoint
    from tui_gateway.owner_forward import connect_owner
    from hermes_cli.gateway_client import GatewayRPCError,GatewayClient,_session_ticket,gateway_ws_target,GATEWAY_WS_PROTOCOL
    from gateway.owner_identity import host_owner_subject
    from gateway.session_contract import SessionRef
    from websockets.asyncio.client import connect
    from websockets.exceptions import InvalidStatus
    assert hr.host_state_dir().resolve()==root/'locks'
    profiles=[('field',secondary)]
    assert boot._start_gateway_reserve_profiles(profiles)
    assert all(process_ownership.owns(value) for value in (launch,secondary))
    assert boot._start_gateway_claim_pid_file()
    config=GatewayConfig(multiplex_profiles=True,loop_watchdog=False)
    config._runtime_profile_homes=tuple(profiles)
    runner=None;control=None;api=None
    try:
        runner=GatewayRunner(config)
        phase('OFFICIAL_RUNNER_CONSTRUCTED')
        await runtime.initialize_gateway_runtime(runner)
        assert len(runner.session_authorities.served_profiles())==2
        assert runner.session_runtime_descriptor['state']=='starting'
        phase('OFFICIAL_MULTIPLEX_AUTHORITIES_INITIALIZED')
        control=await boot._start_gateway_start_control_socket(runner)
        assert control is not None
        _refresh_host_gateway_record(runner)
        phase('NATIVE_CONTROL_STARTED')
        await runtime.start_gateway_runtime_api(runner);api=runner.session_api
        assert not api.task.done()
        web=await api.app.ensure()
        if a.transport=='http':
            from hermes_cli.dashboard_auth import clear_providers,register_provider,native_flow
            from tests.hermes_cli.conftest_dashboard_auth import StubAuthProvider
            clear_providers();register_provider(StubAuthProvider());native_flow._reset_for_tests()
            web.app.state.auth_required=True
        phase('ACTUAL_DASHBOARD_LIFESPAN_STARTED')
        assert await runner.start()
        assert runner._running and not runner._draining
        runtime.publish_gateway_runtime_ready(runner)
        phase('OFFICIAL_RUNNER_STARTED_READY_PUBLISHED')
        for profile_home in (launch,secondary):
            discovery=await asyncio.to_thread(discover_gateway_endpoint,profile_home,timeout=5)
            assert discovery.state=='ready' and discovery.endpoint is not None,discovery.state
            async with connect_owner(profile_home,discovery.endpoint) as client:
                result=await client.rpc('runtime.describe',_timeout=10)
                assert isinstance(result,dict) and Path(result['profile_id']).resolve()==profile_home
                assert result['instance_id']==runner.session_runtime_descriptor['instance_id']
                assert result['authority_epoch']==runner.session_authorities.require(profile_home).epoch
                phase('NATIVE_TICKET_WS_DESCRIPTION_READ',profile='default' if profile_home==launch else 'field',keys=sorted(result))
                groups=await client.rpc('groups.list',_timeout=10,limit=1,offset=0)
                assert groups['rooms']==[] and groups.get('next_offset') is None
                phase('CANONICAL_EMPTY_ROOM_READ',profile='default' if profile_home==launch else 'field')
                try:
                    await client.rpc('groups.list',_timeout=10,limit=1,offset=0,profile='field' if profile_home==launch else 'default')
                except GatewayRPCError as exc:
                    assert str(exc)=='profile_mismatch'
                else:raise AssertionError('A profile-bound socket reached its sibling')
                phase('FOREIGN_PROFILE_READ_REFUSED',profile='default' if profile_home==launch else 'field')
        endpoint=(await asyncio.to_thread(discover_gateway_endpoint,launch,timeout=5)).endpoint
        ticket=await asyncio.to_thread(_session_ticket,launch,endpoint)
        with runner.session_ticket_store._lock:
            grant=runner.session_ticket_store._entries[hashlib.sha256(ticket.encode()).hexdigest()][1]
            assert grant['subject']==host_owner_subject() and grant['subject'].startswith('sid:')
            assert grant['profile_id']==str(launch) and grant['scope']=='profile'
        phase('ACTUAL_NATIVE_SID_TICKET_BINDING_VERIFIED')
        url,protocols=gateway_ws_target(endpoint,ticket)
        async with connect(url,subprotocols=protocols,open_timeout=10,proxy=None) as ws:
            assert ws.subprotocol==GATEWAY_WS_PROTOCOL
            async with GatewayClient(ws) as client:
                assert Path((await client.rpc('runtime.describe',_timeout=10))['profile_id']).resolve()==launch
        try:
            async with connect(url,subprotocols=protocols,open_timeout=10,proxy=None):
                raise AssertionError('A one-use ticket was accepted twice')
        except InvalidStatus as exc:assert exc.response.status_code==403
        phase('REPLAYED_TICKET_UPGRADE_REFUSED')
        import socket,time,uvicorn
        start,target=workspace_cwds
        endpoint=(await asyncio.to_thread(discover_gateway_endpoint,launch,timeout=5)).endpoint
        async with connect_owner(launch,endpoint) as creator:
            created=await creator.rpc('session.create',_timeout=30,request_id='workspace-create',source='gui',
                cwd=str(start),cwd_explicit=True,model='fixture-no-inference',provider='custom',
                base_url='http://127.0.0.1:1/v1',api_key='fixture-no-inference',toolsets=[])
            sid=created['session_id'];revision=created['revision'];generation=created['execution_generation']
            assert sid.startswith('local-') and created['info']['cwd']==str(start)
            phase('ACTUAL_NATIVE_OWNER_CREATED_ONE_DISPOSABLE_SESSION')
            real_app=api.server.config.loaded_app
            assert isinstance(real_app,run_api.GatewayRuntimeAPI)
            requests=[];rpc_requests=[];rejected=[];host_emissions=[];mutation_request=None;mutation_ack=None;mutation_refusal=None
            fixture_db=runner.session_authorities.require(launch).db
            guard_acquired=False;guard_released=False;holder='fixture-workspace-refusal'
            async def release_guard():
                nonlocal guard_acquired,guard_released
                if guard_acquired:
                    release=fixture_db.release_session_turn_lease if a.workspace_refusal=='turn-lease' else fixture_db.release_compression_lock
                    await asyncio.to_thread(release,sid,holder)
                    guard_acquired=False;guard_released=True
            allowed={('GET','/auth/native/authorize'),('GET','/auth/callback'),('POST','/auth/native/token'),
                ('POST','/auth/native/refresh'),('GET','/api/auth/me'),('GET','/api/profiles'),('POST','/api/auth/ws-ticket')}
            async def prefix(scope,receive,send):
                nonlocal mutation_request,mutation_ack
                if scope['type'] not in ('http','websocket'):return
                path=scope['path'];headers=list(scope['headers'])
                if not path.startswith('/dashboard/') or len(requests)>=128 or len(scope.get('query_string',b''))>8192:
                    rejected.append('http-scope');raise RuntimeError('Unreviewed fixture request')
                short=path[len('/dashboard'):]
                forwarded=dict(scope,path=short,raw_path=short.encode('ascii'),root_path='',
                    headers=[(k,v) for k,v in headers if not k.lower().startswith(b'x-forwarded-')]+[(b'x-forwarded-prefix',b'/dashboard')])
                if scope['type']=='http':
                    if (scope['method'],short) not in allowed:
                        rejected.append('http-route');raise RuntimeError('Unreviewed fixture route')
                    entry=dict(method=scope['method'],path=short);requests.append(entry);received=0
                    async def bounded():
                        nonlocal received
                        message=await receive();received+=len(message.get('body',b''))
                        if received>32768:rejected.append('http-body');raise RuntimeError('Oversized fixture request')
                        return message
                    async def observed(message):
                        if message['type']=='http.response.start':entry['status']=message['status']
                        await send(message)
                    await real_app(forwarded,bounded,observed);return
                if short!='/api/ws' or scope.get('query_string') or any(k.lower() in (b'authorization',b'cookie',b'origin') for k,v in headers):
                    rejected.append('ws-upgrade');await send(dict(type='websocket.close',code=1008));return
                entry=dict(method='WS',path=short,rpc=[],outbound=[]);requests.append(entry);ids={};resumes=0;ack_dropped=False
                async def bounded_ws():
                    nonlocal mutation_request,resumes,guard_acquired
                    message=await receive()
                    if message['type']!='websocket.receive':
                        if message['type']=='websocket.disconnect':entry['clientCloseCode']=message.get('code')
                        return message
                    raw=message.get('text')
                    try:
                        assert isinstance(raw,str) and len(raw.encode('utf-8'))<=32768 and len(rpc_requests)<64
                        frame=json.loads(raw);assert isinstance(frame,dict) and set(frame)=={'jsonrpc','id','method','params'} and frame['jsonrpc']=='2.0'
                        rid=frame['id'];assert type(rid) in (str,int) and len(str(rid))<=128 and rid not in ids
                        method,params=frame['method'],frame['params']
                        assert workspace_fixture_rpc_allowed(method,params,session_id=sid,directory=str(target),revision=revision,generation=generation,mutation_claimed=mutation_request is not None)
                        if method=='session.mutate':
                            assert resumes==0
                            mutation_request=dict(requestId=params['request_id'],revision=revision,generation=generation)
                            if a.workspace_refusal:
                                assert not guard_acquired and not guard_released
                                acquire=fixture_db.try_acquire_session_turn_lease if a.workspace_refusal=='turn-lease' else fixture_db.try_acquire_compression_lock
                                assert await asyncio.to_thread(acquire,sid,holder,ttl_seconds=1200)
                                guard_acquired=True
                                phase('ACTUAL_TRANSCRIPT_GUARD_ACQUIRED_FOR_ONE_REVIEWED_MUTATION',guard=a.workspace_refusal)
                        if method=='session.resume':resumes+=1
                        if method=='runtime.describe' and a.emit_host_signals:
                            # Controlled public metadata stimulus through the real upstream producer.
                            # No readiness, identity, prompt or transcript response is fabricated.
                            from tui_gateway.server import _broadcast_global_event
                            for signal in ('cron.changed','projects.changed','skills.changed'):
                                _broadcast_global_event(signal,{})
                                host_emissions.append(dict(type=signal,trigger='runtime.describe',scope='surface-global'))
                        ids[rid]=method;entry['rpc'].append(method);rpc_requests.append(method)
                    except BaseException:
                        rejected.append('ws-rpc');await send(dict(type='websocket.close',code=1008));raise
                    return message
                async def observed_ws(message):
                    nonlocal mutation_ack,mutation_refusal,ack_dropped
                    if ack_dropped:return
                    if message['type']=='websocket.accept':entry['accepted']=True
                    if message['type']=='websocket.close':entry['serverCloseCode']=message.get('code')
                    if message['type']=='websocket.send' and isinstance(message.get('text'),str):
                        frame=json.loads(message['text'])
                        params=frame.get('params')
                        meta=dict(frameKeys=sorted(frame),id=frame.get('id'))
                        if isinstance(params,dict):
                            meta.update(type=params.get('type'),paramKeys=sorted(params),sessionIdEmpty=params.get('session_id')=='',
                                payloadKeys=sorted(params['payload']) if isinstance(params.get('payload'),dict) else None)
                        if isinstance(frame.get('result'),dict):meta['resultKeys']=sorted(frame['result'])
                        if len(entry['outbound'])<64:entry['outbound'].append(meta)
                        if ids.get(frame.get('id'))=='session.mutate':
                            assert mutation_ack is None and mutation_refusal is None
                            if a.workspace_refusal:
                                error=frame['error'];assert 'result' not in frame
                                assert error['code']==4001 and error['message']=='session_busy' and error['data']['reason']=='session_busy'
                                expected_field='compression' if a.workspace_refusal=='compression-lock' else None
                                assert error['data'].get('field')==expected_field and guard_acquired
                                mutation_refusal=dict(code=4001,reason='session_busy',field=expected_field,guard=a.workspace_refusal)
                                entry['workspaceGuardRefused']=True
                                # Release only our owned guard after the actual refusal, before the
                                # phone sees it. A cleared host must not cause an automatic retry.
                                await release_guard()
                            else:
                                assert 'error' not in frame
                                ack=frame['result'];assert ack['session_id']==sid and ack['operation']=='workspace' and ack['cwd']==str(target)
                                assert ack['revision']==revision+1 and ack['execution_generation']==generation+1
                                mutation_ack=ack
                                if a.drop_workspace_ack:
                                    ack_dropped=True;entry['workspaceAckWithheld']=True
                                    await send(dict(type='websocket.close',code=1000))
                                    return
                    await send(message)
                await real_app(forwarded,bounded_ws,observed_ws)
            listener=socket.socket();listener.bind(('127.0.0.1',0));listener.listen(64);port=listener.getsockname()[1]
            proxy_server=uvicorn.Server(uvicorn.Config(prefix,host='127.0.0.1',port=0,loop='asyncio',http='h11',ws='websockets',lifespan='off',
                log_config=None,access_log=False,log_level='warning',timeout_graceful_shutdown=5))
            proxy_task=asyncio.create_task(proxy_server.serve(sockets=[listener]))
            try:
                deadline=time.monotonic()+15
                while not proxy_server.started and not proxy_task.done() and time.monotonic()<deadline:await asyncio.sleep(.05)
                assert proxy_server.started and not proxy_task.done()
                ready=dict(state='READY_ACTUAL_OWNER_WORKSPACE_ANDROID_FIXTURE',pid=os.getpid(),port=port,baseUrl=f'http://127.0.0.1:{port}/dashboard/',
                    sourceCommit=COMMIT,androidApi=a.android_api,controlledHostSignals=a.emit_host_signals,dropWorkspaceAck=a.drop_workspace_ack,workspaceRefusal=a.workspace_refusal,profileHome=str(launch),sessionId=sid,beforeDirectory=str(start),afterDirectory=str(target),
                    revision=revision,generation=generation,harnessSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())
                pending=root/'ready.pending';pending.write_text(json.dumps(ready)+'\n',encoding='utf-8');pending.replace(root/'ready.json')
                phase('READY_FOR_ONE_CONTROLLED_ANDROID_WORKSPACE_MUTATION')
                deadline=time.monotonic()+600
                while not (root/'android-result.json').exists() and not proxy_task.done() and time.monotonic()<deadline:await asyncio.sleep(.1)
                assert (root/'android-result.json').exists(),'Android outcome unknown; preserve exact fixture and inspect before retry'
                proof=json.loads((root/'android-result.json').read_text(encoding='utf-8'))
                assert proof['instrumentationPassed'] is True and proof['distinctMethods']==1
                result=proof['result']
                expected_state='PASS_OWNER_WORKSPACE_ANDROID_REFUSAL' if a.workspace_refusal else ('PASS_OWNER_WORKSPACE_ANDROID_UNKNOWN_RECOVERY' if a.drop_workspace_ack else 'PASS_OWNER_WORKSPACE_ANDROID_ROUNDTRIP')
                assert result['state']==expected_state and result['backend']=='actual-owner'
                assert result['sourceCommit']==COMMIT and result['api']==a.android_api and result['savedCredentialsUnchanged'] is True
                assert result['modelOrWorkerExecutionClaim'] is False and result['inventoryScope']=='live' and len(result['checks'])==(10 if a.workspace_refusal else (9 if a.drop_workspace_ack else 8))
                assert mutation_request is not None and result['requestId']==mutation_request['requestId']
                assert (mutation_refusal is not None and mutation_ack is None) if a.workspace_refusal else mutation_ack is not None
                assert result['beforeDirectorySha256']==hashlib.sha256(str(start).encode()).hexdigest()
                assert result['afterDirectorySha256']==hashlib.sha256(str(target).encode()).hexdigest()
                assert not rejected and rpc_requests.count('session.mutate')==1 and rpc_requests.count('session.resume')==(1 if a.drop_workspace_ack or a.workspace_refusal else 2)
                assert rpc_requests.count('session.events.since')==(1 if a.drop_workspace_ack or a.workspace_refusal else 2)
                if a.drop_workspace_ack or a.workspace_refusal:
                    assert result['receiptState']=='unknown' and result['localReviewApplied'] is False
                    assert sum(bool(x.get('workspaceAckWithheld')) for x in requests)==int(a.drop_workspace_ack)
                if a.workspace_refusal:
                    assert result['expectedRefusal']==a.workspace_refusal and guard_released and not guard_acquired
                    assert sum(bool(x.get('workspaceGuardRefused')) for x in requests)==1
                record.update(android=result,requests=requests,rpcRequests=rpc_requests,rejected=rejected,hostSignalStimuli=host_emissions,
                    workspace=dict(sessionId=sid,cwd=str(start if a.workspace_refusal else target),revision=revision if a.workspace_refusal else mutation_ack['revision'],generation=generation if a.workspace_refusal else mutation_ack['execution_generation'],ack=mutation_ack,refusal=mutation_refusal))
                phase('ANDROID_REFUSAL_UNKNOWN_BARRIER_RECONCILED_WITH_UNCHANGED_HOST' if a.workspace_refusal else ('ANDROID_UNKNOWN_ROOM_BARRIER_RECONCILED_WITH_SERVER_COMMIT' if a.drop_workspace_ack else 'ANDROID_AUTH_ROOM_WORKSPACE_FRESH_READBACK_RECONCILED_WITH_REAL_AUTHORITY'))
            finally:
                await release_guard()
                record.update(requests=requests,rpcRequests=rpc_requests,rejected=rejected,hostSignalStimuli=host_emissions,mutationRequest=mutation_request,mutationAcknowledgement=mutation_ack,mutationRefusal=mutation_refusal,guardReleased=guard_released)
                proxy_server.should_exit=True
                await asyncio.wait_for(proxy_task,15)
                listener.close()
        for authority in runner.session_authorities:
            assert authority.db._read_all('SELECT * FROM session_admissions')==[]
        phase('ZERO_ADMISSIONS_CONFIRMED')
        record['state']='PASS_ANDROID_REAL_OWNER_WORKSPACE_TRANSPORT'
    finally:
        if runner is not None:
            await runner.stop()
        if control is not None:
            await control.stop()
        process_ownership.close();status.remove_pid_file();status.release_gateway_runtime_lock();hr.release_host_lock(hr.ROLE_GATEWAY)
        discovery=await asyncio.to_thread(discover_gateway_endpoint,launch,timeout=5)
        assert discovery.state!='ready' and discovery.endpoint is None
        assert not process_ownership.homes and api.task.done()
        phase('OWNER_LOSS_HAS_NO_READY_FALLBACK')
        phase('OWNED_RUNTIME_TEARDOWN_FINISHED')

try:
    asyncio.run(asyncio.wait_for(run(),900))
except BaseException as exc:
    record.update(state='FAILED_REAL_OWNER_PROBE_RETAINED',errorType=type(exc).__name__)
    (root/'error.txt').write_text(traceback.format_exc(),encoding='utf-8')
    traceback.print_exc()
finally:
    if denied:record['state']='FAILED_BOUNDARY_DENIALS_RETAINED'
    imported=[]
    for name,module in list(sys.modules.items()):
        value=getattr(module,'__file__',None)
        if value:
            path=Path(value).resolve()
            if path.is_relative_to(source) and path.suffix=='.py':
                rel=path.relative_to(source).as_posix();raw=path.read_bytes()
                oid=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()
                assert oid==tracked[rel],rel
                assert hashlib.sha256(raw).hexdigest()==before[rel],rel
                imported.append(dict(module=name,path=rel,gitBlob=oid,sha256=before[rel]))
    record.update(importedModules=imported,versionProbes=version_probes,socketEvents=sockets,workspaceProbes=workspace_probes,blockedCatalogReads=blocked_catalog_reads,
        scope='One bounded Android client over prefixed real-owner HTTP/PKCE/ticket/canonical workspace and Room proof; synthetic IdP only; no production IdP, Pi, inference/tool/worker or complete lifecycle acceptance. Final shutdown diagnostics must also be accepted.')
    (root/'report.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(dict(state=record['state'],modules=len(imported),denials=len(denied))),flush=True)
if record['state']!='PASS_ANDROID_REAL_OWNER_WORKSPACE_TRANSPORT' or denied:sys.exit(1)
