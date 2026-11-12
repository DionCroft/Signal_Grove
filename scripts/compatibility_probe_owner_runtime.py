"""Validate an isolated, exact-source Hermes owner runtime on native Windows.

Requires an existing interpreter/dependency site and a fresh E: output root.
No deployment, model call, tool turn, scheduler ticker or external network is started.
Exit 0 requires clean process-exit diagnostics; exit 2 retains a validated core
with a shutdown finding. Any other failure is not acceptance. See the owner
runtime contract for the deliberately narrower scope than the room matrix.
Optional --seeded-room-reads adds current-stable native owner visibility/paging
checks with synthetic stored rooms and management events; no user/model turn.
See docs/contracts/2026-11-12-native-owner-room-reads.md.
"""
from pathlib import Path
import argparse, asyncio, atexit, datetime, hashlib, ipaddress, json, logging, os, subprocess, sys, traceback, urllib.parse, weakref

if not __debug__ or not (sys.flags.isolated and sys.flags.no_site and sys.flags.dont_write_bytecode) or os.name!='nt':
    raise SystemExit('Use native Windows -I -S -B without -O; assertions and isolated imports are required.')

# Immutable review targets only: a new release is not automatically trusted.
REVIEWED_COMMITS = (
    '2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19',
    '3b421578588243f920886e6fe79a6faa87ea026e',
)
p = argparse.ArgumentParser()
p.add_argument('--source-commit', choices=REVIEWED_COMMITS, default=REVIEWED_COMMITS[0],
               help='Exact reviewed checkout; defaults to the original owner-runtime pin.')
p.add_argument('--source-root', type=Path, required=True)
p.add_argument('--dependency-site', type=Path, required=True)
p.add_argument('--work-root', type=Path, required=True)
p.add_argument('--seeded-room-reads',action='store_true',help='Add current-stable seeded native owner room visibility/history checks; retains nonzero shutdown findings.')
p.add_argument('--owned-child', action='store_true', help=argparse.SUPPRESS)
a = p.parse_args()
if a.seeded_room_reads and a.source_commit!='3b421578588243f920886e6fe79a6faa87ea026e':p.error('Seeded room reads require the exact current-stable execution target.')
source, site, root = a.source_root.resolve(), a.dependency_site.resolve(), a.work_root.resolve()
# Preserve the caller's short DLL-loader spelling; audit checks keep using the
# identical resolved, read-only site. No dependency or permission changes.
site_alias=a.dependency_site.absolute()
assert a.dependency_site.is_absolute() and site_alias.resolve()==site
COMMIT = a.source_commit
CORE_EXPECTED_STATE='PASS_REAL_OWNER_SEEDED_ROOM_READS' if a.seeded_room_reads else 'PASS_REAL_OWNER_BOOTSTRAP_EMPTY_READS'
assert os.name == 'nt' and sys.flags.isolated and sys.flags.no_site and sys.dont_write_bytecode
assert source.is_dir() and site.is_dir() and not root.exists()
assert source.drive.upper() == root.drive.upper() == 'E:'
assert not any(x.is_relative_to(y) for x, y in ((root, source), (source, root), (root, site), (site, root)))
def supervise():
    # One owned attempt: never recycle a root or retry uncertain work.
    receipt=root.with_suffix('.supervisor.json');log=root.with_suffix('.supervisor.log')
    assert not receipt.exists() and not log.exists()
    command=[sys.executable,'-I','-S','-B',str(Path(__file__).resolve()),'--owned-child',
             '--source-commit',COMMIT,'--source-root',str(source),'--dependency-site',str(site_alias),'--work-root',str(root)]
    if a.seeded_room_reads:command.append('--seeded-room-reads')
    child_env={k:v for k,v in os.environ.items() if k.upper() in ('SYSTEMROOT','WINDIR','PATH','PATHEXT')}
    child_env.update(TEMP=str(root.parent),TMP=str(root.parent))
    observation=dict(state='OWNED_ATTEMPT_STARTING',command=command,sourceCommit=COMMIT,harnessSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())
    def save():receipt.write_text(json.dumps(observation,indent=2)+'\n',encoding='utf-8')
    save()
    with log.open('xb') as stream:
        child=subprocess.Popen(command,env=child_env,stdin=subprocess.DEVNULL,stdout=stream,
                               stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
        observation.update(state='OWNED_ATTEMPT_RUNNING',pid=child.pid);save()
        try:code=child.wait(timeout=420)
        except subprocess.TimeoutExpired:
            observation.update(state='UNKNOWN_INSPECT_EXACT_PID_AND_ROOT_NO_REPLAY');save()
            return 3
    observation.update(state='TERMINAL_REVIEW_PENDING',exitCode=code);save()
    if code!=0:
        observation['state']='FAILED_CHILD_RETAINED';save();return 1
    core=json.loads((root/'report.json').read_text(encoding='utf-8'))
    final=json.loads((root/'shutdown-diagnostics.json').read_text(encoding='utf-8'))
    assert core['harnessSha256']==observation['harnessSha256']
    assert core['state']==CORE_EXPECTED_STATE and core['importedModules']
    if a.seeded_room_reads:assert len(core['seededRoomReads']['cases'])==20
    assert not core['denied'] and not final['denied']
    # After actual process exit, inspect its own stores through read-only SQLite.
    import sqlite3
    stores=[]
    for db_path in (root/'hermes-home/state.db',root/'hermes-home/profiles/field/state.db'):
        with sqlite3.connect('file:'+db_path.as_posix()+'?mode=ro',uri=True) as db:
            assert db.execute('PRAGMA quick_check').fetchall()==[('ok',)]
            tables={row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
            counts={name:db.execute('SELECT COUNT(*) FROM '+name).fetchone()[0]
                    for name in ('sessions','session_admissions','hosted_rooms') if name in tables}
            if a.seeded_room_reads:
                assert {'sessions','session_admissions','hosted_rooms'}<=counts.keys() and counts['sessions']==counts['session_admissions']==0 and counts['hosted_rooms']==6
                assert db.execute('SELECT COUNT(*) FROM hosted_room_events').fetchone()[0]==18
            else:
                assert {'sessions','session_admissions'}<=counts.keys() and not any(counts.values())
            stores.append(dict(profile='default' if db_path.parent.name=='hermes-home' else 'field',counts=counts,quickCheck='ok'))
    assert not (root/'hermes-home/gateway.pid').exists()
    observation.update(coreTransportAccepted=True,stores=stores,shutdownWarnings=len(final['warnings']),
        importedModules=len(core['importedModules']),sourceCommit=COMMIT,
        scope='Finite no-platform owner bootstrap/native identity/tickets/read refusals/owner loss only; no seeded room, outer forwarding, scheduler, model/tool, Android, Pi or deployed-fleet acceptance.')
    observation['seededRoomMode']=a.seeded_room_reads
    if a.seeded_room_reads:
        observation['seededRoomReads']=core['seededRoomReads']
        observation['scope']='Actual current-owner native pipe/SID/tickets and canonical socket seeded room reads in isolated profiles. Synthetic stored rooms and management-only events; no model/tool/user turn, Android/Pi/deployed acceptance. Shutdown finding remains nonzero.'
    clean=final['state']=='CLEAN_EXIT_OBSERVATION' and not final['warnings']
    observation['state']='PASS_FINITE_OWNER_RUNTIME' if clean else 'CORE_PASS_SHUTDOWN_FINDING_OPEN'
    save();print(json.dumps({k:observation[k] for k in ('state','importedModules','shutdownWarnings','sourceCommit','stores')}))
    return 0 if clean else 2

if not a.owned_child:
    sys.exit(supervise())

root.mkdir(parents=True, exist_ok=False)
user, temp, launch = root/'account', root/'temp', root/'hermes-home'
secondary = launch/'profiles'/'field'
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
os.chdir(root);sys.path[:0]=[str(source),str(site_alias)]
reads=[root,source,site,Path(sys.base_prefix).resolve(),Path(__file__).resolve()]
denied=[]; bound=weakref.WeakSet(); sockets=[]; version_probes=[]
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
record=dict(state='STARTING_FINITE_REAL_OWNER_PROBE',commit=COMMIT,pid=os.getpid(),phases=[],denied=denied,
    harnessSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())
shutdown_warnings=[]
class ShutdownDiagnostics(logging.Handler):
    def emit(self,entry):
        if 'closed while a' in entry.getMessage() and 'reopening' in entry.getMessage():
            shutdown_warnings.append(dict(message=entry.getMessage(),stack=traceback.format_stack()))
logging.getLogger().addHandler(ShutdownDiagnostics())
def observe_process_exit():
    (root/'shutdown-diagnostics.json').write_text(json.dumps(dict(warnings=shutdown_warnings,denied=denied,
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
            'dashboard':{'startup_orphan_sweep':False},'agent':{'environment_probe':False},'terminal':{'cwd':str(root)}})
    from gateway.run import GatewayRunner,_refresh_host_gateway_record
    from gateway.config import GatewayConfig
    from gateway import run_bootstrap as boot,run_runtime as runtime,run_api
    from gateway.runtime_ownership import process_ownership
    from gateway import host_rendezvous as hr,status
    from hermes_cli.gateway_runtime import discover_gateway_endpoint
    from tui_gateway.owner_forward import connect_owner
    from hermes_cli.gateway_client import GatewayRPCError,GatewayClient,_session_ticket,gateway_ws_target,GATEWAY_WS_PROTOCOL
    from gateway.owner_identity import host_owner_subject
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
        await api.app.ensure()
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
        if a.seeded_room_reads:
            import time
            from gateway import hosted_rooms as seeded_rooms
            from gateway.hosted_room_driver import list_tasks as seeded_tasks
            from gateway.session_identity import authenticated_subject
            from gateway.session_hosted_service import _OWNER as room_owner_prefix
            from gateway.run import _profile_runtime_scope
            from tui_gateway.contracts import registry as seeded_registry
            assert seeded_registry.STRICT is True
            room_cases=[];room_fixtures={};room_corpus={}
            def room_passed(name):
                assert name not in room_cases;room_cases.append(name)
            def room_snapshot(authority):
                with authority.db._read_ctx() as conn:
                    names=sorted(r[0] for r in conn.execute("SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'hosted_room%'").fetchall())
                    tables={}
                    for name in names:
                        assert name.isidentifier()
                        cur=conn.execute('SELECT * FROM "'+name+'"');columns=[d[0] for d in cur.description]
                        rows=[dict(zip(columns,tuple(row))) for row in cur.fetchall()]
                        tables[name]=sorted(rows,key=lambda row:json.dumps(row,sort_keys=True,separators=(',',':')))
                    owners=[list(r) for r in conn.execute('SELECT key,value FROM state_meta WHERE key LIKE ? ORDER BY key',(room_owner_prefix+'%',)).fetchall()]
                return dict(tables=tables,owners=owners)
            def room_digest(value):return hashlib.sha256(json.dumps(value,sort_keys=True,separators=(',',':')).encode()).hexdigest()
            owner=host_owner_subject();legacy_owner=authenticated_subject(dict(provider='basic',user_id='signal-grove-legacy-fixture'))
            from gateway.owner_identity import same_owner
            assert same_owner(legacy_owner,owner) and not same_owner('signal-grove-foreign-owner',owner)
            seed_time=time.time()
            for label,profile_home in [('default',launch),('field',secondary)]:
                authority=runner.session_authorities.require(profile_home);service=authority.hosted_room_service
                assert service is not None and Path(service.db_path).resolve()==profile_home/'state.db'
                order=[('sg-hidden-before','foreign'),('sg-owned-a','owner'),('sg-hidden-between','foreign'),('sg-owned-b','legacy'),('sg-'+label+'-only','owner'),('sg-hidden-after','foreign')]
                fixtures={}
                with _profile_runtime_scope(profile_home):
                    gateway_id=seeded_rooms.local_authority_gateway_id()
                    for index,(room_id,kind) in enumerate(order):
                        subject=owner if kind=='owner' else legacy_owner if kind=='legacy' else 'signal-grove-foreign-owner'
                        stamp=seed_time+(len(order)-index)*10
                        names=[label+' '+room_id+' revision '+str(n) for n in range(4)]
                        seeded_rooms.create_room(authority.db.db_path,room_id=room_id,name=names[0],members=[dict(profile=label,handle='hermes')],authority_gateway_id=gateway_id,now=stamp,
                            admit=lambda conn,s=subject,r=room_id:service.authorize_room(s,r,create=True,conn=conn))
                        # Management-only events cannot admit a user/model turn.
                        for seq in range(1,4):
                            result=seeded_rooms.rename_room(authority.db.db_path,room_id=room_id,event_id='sg-rename-'+str(seq),name=names[seq],now=stamp+seq)
                            assert result['event']['kind']=='room.renamed' and result['event']['seq']==seq
                        assert seeded_tasks(authority.db.db_path,room_id=room_id)==[]
                        fixtures[room_id]=dict(kind=kind,names=names,gateway_id=gateway_id)
                room_fixtures[label]=dict(authority=authority,fixtures=fixtures)
            before_stores={label:room_snapshot(data['authority']) for label,data in room_fixtures.items()}
            (root/'seeded-room-stores-before.json').write_text(json.dumps(before_stores,indent=2)+'\n',encoding='utf-8')
            phase('SIX_OWNED_LEGACY_AND_FOREIGN_ROOMS_PER_PROFILE_SEEDED_WITHOUT_USER_EVENTS')
            for label,profile_home in [('default',launch),('field',secondary)]:
                data=room_fixtures[label];authority=data['authority'];fixtures=data['fixtures'];eligible=['sg-owned-a','sg-owned-b','sg-'+label+'-only']
                other_label='field' if label=='default' else 'default';corpus=[]
                endpoint=(await asyncio.to_thread(discover_gateway_endpoint,profile_home,timeout=5)).endpoint
                assert endpoint is not None
                async with connect_owner(profile_home,endpoint) as client:
                    async def read_room(method,**params):
                        response=await client.rpc(method,_timeout=10,**params);corpus.append(dict(method=method,params=params,result=response));return response
                    async def refused_room(method,**params):
                        try:await client.rpc(method,_timeout=10,**params)
                        except GatewayRPCError as exc:assert str(exc) in ('permission_denied','profile_mismatch','invalid_params'),str(exc);return str(exc)
                        raise AssertionError('Unexpected room read accepted: '+method)
                    seen=[];offset=0
                    for index in range(3):
                        page=await read_room('groups.list',limit=1,offset=offset)
                        assert len(page['rooms'])==1 and page['rooms'][0]['room_id']==eligible[index]
                        room=page['rooms'][0];assert room['name']==fixtures[room['room_id']]['names'][3]
                        seen.append(room['room_id']);next_offset=page.get('next_offset')
                        if index<2:assert type(next_offset) is int and next_offset>offset;offset=next_offset
                        else:assert next_offset is None
                    assert seen==eligible and len(set(seen))==3
                    room_passed(label+'_native_owner_pages_skip_foreign_and_include_legacy_login_owner')
                    first=await read_room('groups.list',limit=2,offset=0)
                    assert [r['room_id'] for r in first['rooms']]==eligible[:2] and type(first.get('next_offset')) is int
                    tail=await read_room('groups.list',limit=2,offset=first['next_offset'])
                    assert [r['room_id'] for r in tail['rooms']]==eligible[2:] and tail.get('next_offset') is None
                    room_passed(label+'_larger_page_limit_keeps_actor_visible_continuation')
                    state=await read_room('groups.state',room_id='sg-owned-a')
                    assert state['room']['name']==fixtures['sg-owned-a']['names'][3] and state['room']['room_id']=='sg-owned-a'
                    room_passed(label+'_same_room_id_reads_only_selected_profile_state')
                    for method in ['groups.state','groups.log']:
                        assert await refused_room(method,room_id='sg-hidden-before')=='permission_denied'
                    room_passed(label+'_foreign_private_room_state_and_log_refused')
                    for method in ['groups.state','groups.log']:
                        assert await refused_room(method,room_id='sg-'+other_label+'-only')=='permission_denied'
                    room_passed(label+'_sibling_only_room_state_and_log_refused')
                    assert await refused_room('groups.list',limit=1,offset=0,profile=other_label)=='profile_mismatch'
                    room_passed(label+'_sibling_profile_selector_refused_with_data_present')
                    cursor=0;events=[]
                    for seq in range(1,4):
                        page=await read_room('groups.log',room_id='sg-owned-a',since_seq=cursor,limit=1)
                        assert len(page['events'])==1 and page['cursor']==seq and page['latest_seq']==3 and page['has_more'] is (seq<3)
                        assert page['authority']['gateway_id']==fixtures['sg-owned-a']['gateway_id']
                        event=page['events'][0];assert event['room_id']=='sg-owned-a' and event['seq']==seq and event['kind']=='room.renamed' and event['payload']=={'name':fixtures['sg-owned-a']['names'][seq]}
                        events.append(event['event_id']);cursor=page['cursor']
                    assert len(set(events))==3
                    tail=await read_room('groups.log',room_id='sg-owned-a',since_seq=cursor,limit=1)
                    assert tail['events']==[] and tail['cursor']==3 and tail['latest_seq']==3 and tail['has_more'] is False
                    room_passed(label+'_room_log_pages_are_exact_monotonic_and_tail_is_empty')
                    for params in [dict(limit=0,offset=0),dict(limit=1,offset=-1),dict(limit=1,offset=True)]:
                        assert await refused_room('groups.list',**params)=='invalid_params'
                    for cursor in [True,4]:assert await refused_room('groups.log',room_id='sg-owned-a',since_seq=cursor,limit=1)=='invalid_params'
                    room_passed(label+'_invalid_room_and_log_pagination_refused')
                assert room_snapshot(authority)==before_stores[label]
                room_passed(label+'_room_tables_and_ownership_rows_unchanged_by_reads')
                room_corpus[label]=corpus;phase('NATIVE_SEEDED_ROOM_READS_VERIFIED',profile=label,cases=9)
            for data in room_fixtures.values():
                authority=data['authority'];assert authority.db._read_all('SELECT * FROM session_admissions')==[]
                for room_id in data['fixtures']:assert seeded_tasks(authority.db.db_path,room_id=room_id)==[]
            room_passed('seeded_reads_schedule_no_room_tasks_or_session_admissions')
            after_stores={label:room_snapshot(data['authority']) for label,data in room_fixtures.items()};assert after_stores==before_stores
            room_passed('both_profile_stores_preserved_after_all_seeded_clients_close')
            assert len(room_cases)==len(set(room_cases))==20
            record['seededRoomReads']=dict(cases=room_cases,roomsPerProfile=6,eventsPerProfile=18,storeSha256={label:room_digest(value) for label,value in after_stores.items()},actualNativeOwnerTickets=True,syntheticRoomRowsAndManagementEvents=True,userEventsSeeded=0,modelToolTurnsOrAdmissions=0,ownerActorsInjected=False,dispatcherOrAuthPatched=False)
            (root/'seeded-room-stores-after.json').write_text(json.dumps(after_stores,indent=2)+'\n',encoding='utf-8')
            (root/'seeded-room-rpc.json').write_text(json.dumps(room_corpus,indent=2)+'\n',encoding='utf-8')
            phase('TWENTY_SEEDED_NATIVE_OWNER_ROOM_CHECKS_PASSED')

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
        for authority in runner.session_authorities:
            assert authority.db._read_all('SELECT * FROM session_admissions')==[]
        phase('ZERO_ADMISSIONS_CONFIRMED')
        record['state']=CORE_EXPECTED_STATE
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
    asyncio.run(asyncio.wait_for(run(),180))
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
    record.update(importedModules=imported,versionProbes=version_probes,socketEvents=sockets,
        scope='Finite empty no-platform owner runtime and negative-auth reads only; no phone, Pi, model, tool turn, outer forwarding or seeded private/shared/paged room acceptance. Final shutdown diagnostics must also be accepted.')
    if a.seeded_room_reads:record['scope']='Actual owner bootstrap/current-user DACL/SID/native tickets and canonical socket seeded room reads. Synthetic room rows/management events; no user/model/tool turn, Android/Pi/deployed acceptance. Complete process-exit diagnostics remain required.'
    record['dependencyAliasResolvesToCanonicalReadRoot']=site_alias.resolve()==site
    (root/'report.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(dict(state=record['state'],modules=len(imported),denials=len(denied))),flush=True)
if record['state']!=CORE_EXPECTED_STATE or denied:sys.exit(1)
