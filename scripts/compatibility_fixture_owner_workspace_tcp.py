"""Serve or probe one bounded actual Hermes owner in a disposable home.

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

def profile_android_rpc_allowed(method,params,*,profile,session_id,resume_cursor,resumed,replayed,pinged):
    """Closed read/resume surface, exact ACK cursor, one attachment per actual profile."""
    if not isinstance(params,dict):return False
    if method=='runtime.describe':return params=={}
    if profile not in ('default','field') or not isinstance(session_id,str) or not session_id:return False
    if method=='gateway.ping':return params=={} and not pinged
    if method=='session.list':return set(params)=={'scope','limit'} and params['scope']=='live' and type(params['limit']) is int and params['limit']==200
    if method=='session.resume':return not resumed and params=={'session_id':session_id,'omit_messages':True} and type(params.get('omit_messages')) is bool
    if method=='session.events.since':
        return (resumed and not replayed and isinstance(resume_cursor,dict) and set(resume_cursor)=={'last_sequence','replay_epoch'}
            and type(resume_cursor['last_sequence']) is int and 0<=resume_cursor['last_sequence']<2**63
            and isinstance(resume_cursor['replay_epoch'],str) and 0<len(resume_cursor['replay_epoch'])<=256
            and set(params)=={'session_id','last_sequence','replay_epoch'} and type(params['last_sequence']) is int
            and params=={'session_id':session_id,**resume_cursor})
    return False

def project_android_rpc_allowed(method,params,*,profile,projects):
    """Read-only fixture metadata and stored inventory. No resume/mutate/work/discovery."""
    if not isinstance(params,dict):return False
    if method=='runtime.describe':return params=={}
    if profile not in ('default','field') or not isinstance(projects,dict) or set(projects)!={'default','field'}:return False
    if not all(isinstance(value,str) and re.fullmatch(r'p_[0-9a-f]{8}',value) for value in projects.values()):return False
    if method=='session.list':return set(params)=={'scope','limit'} and params['scope']=='stored' and type(params['limit']) is int and params['limit']==200
    if params.get('profile')!=profile:return False
    if method=='projects.list':return set(params)=={'profile'}
    if method=='projects.get':return set(params)=={'profile','id'} and params['id'] in projects.values()
    if method=='projects.project_sessions':return set(params)=={'profile','project_id','session_limit'} and params['project_id']==projects[profile] and type(params['session_limit']) is int and params['session_limit']==500
    return False


def project_fixture_tables(conn,tables):
    """Lossless ordered rows for only the explicit fixture tables; no upstream code execution."""
    assert tables in (('sessions','messages'),('projects','project_folders','project_meta','discovered_repos'))
    result={}
    for table in tables:
        cursor=conn.execute('SELECT * FROM '+table+' ORDER BY rowid')
        result[table]=[dict(zip([c[0] for c in cursor.description],row)) for row in cursor.fetchall()]
    return result


def verify_project_fixture_store(conn,expected):
    assert conn.execute('PRAGMA quick_check').fetchall()==[('ok',)]
    counts={name:conn.execute('SELECT COUNT(*) FROM '+name).fetchone()[0] for name in ('sessions','messages','session_admissions','hosted_rooms','worker_executions','session_turn_leases','compression_locks')}
    assert counts==dict(sessions=7,messages=12,session_admissions=0,hosted_rooms=0,worker_executions=0,session_turn_leases=0,compression_locks=0)
    assert conn.execute('SELECT COUNT(*) FROM sessions WHERE ended_at IS NULL').fetchone()[0]==0
    assert conn.execute("SELECT COUNT(*) FROM state_meta WHERE key GLOB 'gateway.mutation.v1.*'").fetchone()[0]==0
    assert history_snapshot_digest(project_fixture_tables(conn,('sessions','messages')))==expected['historyDigest']
    return counts


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
p.add_argument('--android-api',type=int,choices=(26,37))
p.add_argument('--emit-host-signals',action='store_true',help='Emit three explicit public metadata signals through the real upstream producer on describe.')
effect=p.add_mutually_exclusive_group()
effect.add_argument('--drop-workspace-ack',action='store_true',help='Drop only the actual committed workspace ACK at the outer transport; never alter dispatch or authority.')
effect.add_argument('--workspace-refusal',choices=('turn-lease','compression-lock'),help='Hold a real fixture-local transcript lease/lock through the actual mutation refusal; no inference or fake response.')
effect.add_argument('--read-api-history',action='store_true',help='Read closed seeded conversations through the actual bearer API; no session adoption or forwarded mutation.')
effect.add_argument('--probe-profile-tickets',choices=('gateway','webapp'),help='Backend-only native-auth/profile-bound ticket and retirement proof; synthetic IdP/renderer, no Android or inference claim.')
effect.add_argument('--android-project-conversations',choices=('gateway','webapp'),help='Read-only actual Android project grouping/profile inventory; no adoption or worker action.')

effect.add_argument('--android-profile-tickets',choices=('gateway','webapp'),help='Actual Android native-auth/profile attachment, retirement and quiescent Room cache isolation; no work submission.')
p.add_argument('--owned-child', action='store_true', help=argparse.SUPPRESS)
a = p.parse_args()
if a.probe_profile_tickets and (a.android_api is not None or a.emit_host_signals):p.error('Backend profile probe does not accept Android API or controlled host signals.')
if not a.probe_profile_tickets and a.android_api is None:p.error('Android fixture modes require --android-api.')
if (a.android_profile_tickets or a.android_project_conversations) and a.emit_host_signals:p.error('Android profile mode does not emit controlled host signals.')
if a.read_api_history and a.emit_host_signals:p.error("History mode does not emit canonical host notifications.")
source, site, root = a.source_root.resolve(), a.dependency_site.resolve(), a.work_root.resolve()
COMMIT = a.source_commit
assert os.name == 'nt' and sys.flags.isolated and sys.flags.no_site and sys.dont_write_bytecode
assert source.is_dir() and site.is_dir() and not root.exists()
assert source.drive.upper() == root.drive.upper() == 'E:'
assert not any(x.is_relative_to(y) for x, y in ((root, source), (source, root), (root, site), (site, root)))
def history_snapshot_digest(snapshot):
    """Lossless typed encoding for SQLite BLOB fields; do not omit display identities."""
    def encode(value):
        if not isinstance(value,bytes):raise TypeError('Unsupported fixture snapshot type')
        return {'sqlite_blob_hex':value.hex()}
    return hashlib.sha256(json.dumps(snapshot,sort_keys=True,separators=(',',':'),ensure_ascii=True,default=encode).encode()).hexdigest()

def verify_profile_ticket_store(db, expected):
    """Read-only final evidence: path equality alone never substitutes for fences/receipts/work."""
    assert set(expected)=={'sessionId','cwd','revision','generation'}
    assert all(isinstance(expected[x],str) and expected[x] for x in ('sessionId','cwd'))
    assert all(type(expected[x]) is int and 0<=expected[x]<2**63 for x in ('revision','generation'))
    assert db.execute('PRAGMA quick_check').fetchall()==[('ok',)]
    counts={name:db.execute('SELECT COUNT(*) FROM '+name).fetchone()[0] for name in ('sessions','messages','session_admissions','hosted_rooms','worker_executions','session_turn_leases','compression_locks')}
    assert counts==dict(sessions=1,messages=0,session_admissions=0,hosted_rooms=0,worker_executions=0,session_turn_leases=0,compression_locks=0)
    assert db.execute('SELECT id,cwd,runtime_revision,runtime_generation FROM sessions').fetchall()==[(expected['sessionId'],expected['cwd'],expected['revision'],expected['generation'])]
    assert db.execute("SELECT COUNT(*) FROM state_meta WHERE key GLOB 'gateway.mutation.v1.*'").fetchone()[0]==0
    return counts


def supervise():
    # One owned attempt: never recycle a root or retry uncertain work.
    receipt=root.with_suffix('.supervisor.json');log=root.with_suffix('.supervisor.log')
    assert not receipt.exists() and not log.exists()
    command=[sys.executable,'-I','-S','-B',str(Path(__file__).resolve()),'--owned-child',
             '--source-commit',COMMIT,'--transport',a.transport,'--source-root',str(source),'--dependency-site',str(site),'--work-root',str(root)]
    if a.android_api is not None:command.extend(['--android-api',str(a.android_api)])
    if a.probe_profile_tickets:command.extend(['--probe-profile-tickets',a.probe_profile_tickets])
    if a.android_profile_tickets:command.extend(['--android-profile-tickets',a.android_profile_tickets])
    if a.android_project_conversations:command.extend(['--android-project-conversations',a.android_project_conversations])
    if a.emit_host_signals:command.append('--emit-host-signals')
    if a.drop_workspace_ack:command.append('--drop-workspace-ack')
    if a.workspace_refusal:command.extend(['--workspace-refusal',a.workspace_refusal])
    if a.read_api_history:command.append('--read-api-history')
    child_env={k:v for k,v in os.environ.items() if k.upper() in ('SYSTEMROOT','WINDIR','PATH','PATHEXT')}
    child_env.update(TEMP=str(root.parent),TMP=str(root.parent))
    observation=dict(state='OWNED_ATTEMPT_STARTING',command=command,sourceCommit=COMMIT,transport=a.transport,controlledHostSignals=a.emit_host_signals,dropWorkspaceAck=a.drop_workspace_ack,workspaceRefusal=a.workspace_refusal,readApiHistory=a.read_api_history,profileTicketProbe=a.probe_profile_tickets,androidProfileTickets=a.android_profile_tickets,androidProjectConversations=a.android_project_conversations,harnessSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())
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
    assert core['state']==('PASS_ANDROID_REAL_OWNER_PROJECT_CONVERSATIONS' if a.android_project_conversations else 'PASS_ANDROID_REAL_OWNER_PROFILE_RETIREMENT' if a.android_profile_tickets else 'PASS_ACTUAL_OWNER_PROFILE_TICKET_PROBE' if a.probe_profile_tickets else ('PASS_ANDROID_REAL_OWNER_CLOSED_HISTORY' if a.read_api_history else 'PASS_ANDROID_REAL_OWNER_WORKSPACE_TRANSPORT')) and core['importedModules']
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
            if a.android_project_conversations:
                profile='default' if db_path.parent.name=='hermes-home' else 'field'
                expected=core['projectConversations']['snapshots'][profile]
                verified=verify_project_fixture_store(db,expected);assert counts=={key:verified[key] for key in counts}
                with sqlite3.connect('file:'+(db_path.parent/'projects.db').as_posix()+'?mode=ro',uri=True) as projects:
                    assert projects.execute('PRAGMA quick_check').fetchall()==[('ok',)]
                    assert history_snapshot_digest(project_fixture_tables(projects,('projects','project_folders','project_meta','discovered_repos')))==expected['projectDigest']
            elif a.probe_profile_tickets or a.android_profile_tickets:
                profile='default' if db_path.parent.name=='hermes-home' else 'field'
                verified=verify_profile_ticket_store(db,core['profileTicketAndroid' if a.android_profile_tickets else 'profileTicketProbe']['snapshots'][profile])
                assert counts=={key:verified[key] for key in counts}
            elif a.read_api_history:
                profile='default' if db_path.parent.name=='hermes-home' else 'field'
                assert counts==dict(sessions=2,session_admissions=0,hosted_rooms=0)
                assert db.execute('SELECT COUNT(*) FROM worker_executions').fetchone()[0]==0
                assert db.execute('SELECT COUNT(*) FROM messages').fetchone()[0]==503
                assert db.execute('SELECT COUNT(*) FROM sessions WHERE ended_at IS NULL').fetchone()[0]==0
                assert db.execute("SELECT COUNT(*) FROM state_meta WHERE key GLOB 'gateway.mutation.v1.*'").fetchone()[0]==0
                assert db.execute('SELECT id FROM sessions ORDER BY id').fetchall()==[(profile+'-only-fixture',),('shared-closed-fixture',)]
                snap={'sessions':[dict(zip([x[1] for x in db.execute('PRAGMA table_info(sessions)')],row)) for row in db.execute('SELECT * FROM sessions ORDER BY id')],
                      'messages':[dict(zip([x[1] for x in db.execute('PRAGMA table_info(messages)')],row)) for row in db.execute('SELECT * FROM messages ORDER BY id')]}
                digest=history_snapshot_digest(snap)
                assert digest==core['historySnapshots'][profile]
            else:
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
        scope=('Actual Android read-only project conversation grouping and profile-bound stored inventory; synthetic IdP/renderer, no resume/work/worker/fleet or full lifecycle acceptance.' if a.android_project_conversations else 'Actual Android/native-auth/profile attachment/retirement and receipt/cursor Room proof; synthetic IdP and renderer placeholder, no inference, production or full lifecycle acceptance.' if a.android_profile_tickets else 'Actual owner native-auth/profile ticket/retirement backend proof; synthetic IdP and renderer placeholder, no Android, inference, production or full Webapp/lifecycle acceptance.' if a.probe_profile_tickets else ('Actual owner/bearer API/closed seeded history/Room read proof; no native login, inference, production or full lifecycle acceptance.' if a.read_api_history else 'One bounded Android client over prefixed real-owner HTTP/PKCE/ticket/canonical workspace and Room proof; synthetic IdP only; no production IdP, Pi, inference/tool/worker or complete lifecycle acceptance.')))
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


def history_fixture_http_allowed(method,path,query):
    """Only finite read-only API paths, profiles and exact pagination shapes reach the real adapter."""
    if method!='GET' or not isinstance(path,str) or not isinstance(query,bytes):return False
    match=re.fullmatch(r'/history/p/(default|field|not-served-fixture)/api/sessions(?:/(shared-closed-fixture|default-only-fixture|field-only-fixture)/messages)?',path)
    if match is None:return False
    try:
        pairs=urllib.parse.parse_qsl(query.decode('ascii'),keep_blank_values=True,strict_parsing=True)
    except (ValueError,UnicodeError):return False
    values=dict(pairs)
    if len(values)!=len(pairs):return False
    if match[2] is None:
        return set(values)=={'limit','offset'} and values['limit'] in ('1','50') and values['offset'] in ('0','1','2','50')
    return (set(values)=={'inline_images','limit','offset','order'} and values['inline_images']=='false'
            and values['limit']=='500' and values['offset'] in ('0','500') and values['order']=='oldest')

async def history_fixture_body(stream):
    """Read every chunk with a hard bound; one read(n) is not a complete network response."""
    body=bytearray()
    async for chunk in stream.iter_chunked(65536):
        if not isinstance(chunk,bytes) or len(chunk)>65536:raise RuntimeError('Malformed fixture response chunk')
        if len(body)+len(chunk)>2_000_000:raise RuntimeError('Oversized fixture response')
        body.extend(chunk)
    return bytes(body)

async def serve_closed_history_android(runner,keys):
    import socket,time,uvicorn
    from aiohttp import ClientSession
    from gateway.config import Platform
    from gateway.session_authorities import owner_scope
    api=runner.adapters[Platform.API_SERVER]
    inner_port=api._site._server.sockets[0].getsockname()[1]
    assert type(inner_port) is int and 1024<=inner_port<=65535
    homes=[('default',launch),('field',secondary)];snapshots={}
    def snapshot(db):
        with db._read_ctx() as conn:
            return {'sessions':[dict(row) for row in conn.execute('SELECT * FROM sessions ORDER BY id')],
                    'messages':[dict(row) for row in conn.execute('SELECT * FROM messages ORDER BY id')]}
    for name,home in homes:
        authority=runner.session_authorities.require(home);db=authority.db
        with owner_scope(authority):
            db.create_session('shared-closed-fixture',source='desktop')
            db.append_message('shared-closed-fixture','user',name+' saved user',timestamp=100)
            for index in range(2,501):db.append_message('shared-closed-fixture','assistant','private-hidden-fixture-'+name,display_kind='hidden',timestamp=100+index)
            db.append_message('shared-closed-fixture','assistant',name+' saved reply',timestamp=1000,reasoning_content='private-reasoning-fixture-'+name)
            db.end_session('shared-closed-fixture',end_reason='fixture_closed')
            sid=name+'-only-fixture';db.create_session(sid,source='desktop')
            db.append_message(sid,'user',name+' only user',timestamp=100)
            db.append_message(sid,'assistant',name+' only reply',timestamp=101)
            db.end_session(sid,end_reason='fixture_closed')
        snapshots[name]=history_snapshot_digest(snapshot(db))
    record['historySnapshots']=snapshots
    phase('CLOSED_NONEMPTY_HISTORY_SEEDED_ONLY_IN_TWO_DISPOSABLE_PROFILE_STORES')
    requests=[];rejected=[]
    async with ClientSession() as client:
        async def prefix(scope,receive,send):
            if scope['type']!='http':
                if scope['type']=='websocket':await send(dict(type='websocket.close',code=1008))
                return
            path=scope.get('path','');query=scope.get('query_string',b'');headers=scope.get('headers',[])
            allowed=history_fixture_http_allowed(scope['method'],path,query)
            auth=[value.decode('ascii') for key,value in headers if key.lower()==b'authorization']
            allowed=allowed and len(auth)==1 and auth[0] in ['Bearer '+keys[str(home)] for _,home in homes]
            allowed=allowed and not any(key.lower() in (b'cookie',b'origin',b'transfer-encoding') for key,value in headers)
            if not allowed:
                rejected.append('unreviewed-history-route-or-header');raise RuntimeError('Unreviewed history request')
            size=0
            while True:
                message=await receive()
                if message['type']=='http.disconnect':return
                assert message['type']=='http.request';size+=len(message.get('body',b''))
                if size:rejected.append('history-body');raise RuntimeError('History fixture GET has a body')
                if not message.get('more_body',False):break
            target='http://127.0.0.1:'+str(inner_port)+path[len('/history'):]+('?' + query.decode('ascii') if query else '')
            async with client.get(target,headers={'Authorization':auth[0],'Accept':'application/json'},allow_redirects=False) as response:
                body=await history_fixture_body(response.content)
                requests.append(dict(method='GET',path=path,query=query.decode('ascii'),status=response.status))
                await send(dict(type='http.response.start',status=response.status,headers=[(b'content-type',response.headers.get('Content-Type','').encode('ascii'))]))
                await send(dict(type='http.response.body',body=body))
        listener=socket.socket();listener.bind(('127.0.0.1',0));listener.listen(64);port=listener.getsockname()[1]
        server=uvicorn.Server(uvicorn.Config(prefix,host='127.0.0.1',port=0,loop='asyncio',http='h11',ws='websockets',lifespan='off',log_config=None,access_log=False,log_level='warning',timeout_graceful_shutdown=5))
        task=asyncio.create_task(server.serve(sockets=[listener]))
        try:
            deadline=time.monotonic()+15
            while not server.started and not task.done() and time.monotonic()<deadline:await asyncio.sleep(.05)
            assert server.started and not task.done()
            ready=dict(state='READY_ACTUAL_OWNER_CLOSED_HISTORY_ANDROID_FIXTURE',pid=os.getpid(),port=port,baseUrl=f'http://127.0.0.1:{port}/history/',sourceCommit=COMMIT,androidApi=a.android_api,historyKeys={name:keys[str(home)] for name,home in homes},harnessSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())
            pending=root/'ready.pending';pending.write_text(json.dumps(ready)+'\n',encoding='utf-8');pending.replace(root/'ready.json')
            phase('READY_FOR_READ_ONLY_ANDROID_BEARER_HISTORY')
            deadline=time.monotonic()+600
            while not (root/'android-result.json').is_file() and time.monotonic()<deadline:await asyncio.sleep(.1)
            assert (root/'android-result.json').is_file(),'Android did not publish its exact completed result; do not repeat it'
            proof=json.loads((root/'android-result.json').read_text());assert proof['instrumentationPassed'] is True and proof['distinctMethods']==1
            result=proof['result'];assert result['state']=='PASS_REAL_OWNER_API_CLOSED_HISTORY' and result['sourceCommit']==COMMIT and result['api']==a.android_api and result['backend']=='actual-api-owner'
            assert result['savedCredentialsUnchanged'] and not result['modelOrWorkerExecutionClaim'] and len(result['checks'])==7
            assert not rejected and len(requests)==12 and all(item['method']=='GET' for item in requests)
            assert sorted(item['status'] for item in requests)==[200]*10+[401,404]
            for name,home in homes:
                db=runner.session_authorities.require(home).db
                assert history_snapshot_digest(snapshot(db))==snapshots[name]
                assert db._read_all('SELECT * FROM session_admissions')==[]
            record.update(android=result,historySnapshots=snapshots,historyRequests=requests,historyRejected=rejected)
            phase('ANDROID_BEARER_CLOSED_HISTORY_AND_ROOM_ISOLATION_RECONCILED_WITH_UNCHANGED_HOST')
        finally:
            record['historySnapshotsAfterClient']={name:history_snapshot_digest(snapshot(runner.session_authorities.require(home).db)) for name,home in homes}
            record.update(historyRequests=requests,historyRejected=rejected)
            server.should_exit=True;await asyncio.wait_for(task,15);listener.close()

async def profile_ticket_probe(runner,api):
    import base64,secrets,uuid
    from aiohttp import ClientSession,CookieJar,DummyCookieJar
    from hermes_cli.gateway_client import GatewayClient,GatewayRPCError,_session_ticket,gateway_ws_target
    from tui_gateway.owner_forward import connect_owner
    from hermes_cli.gateway_runtime import discover_gateway_endpoint
    from websockets.asyncio.client import connect
    from websockets.exceptions import InvalidStatus,ConnectionClosed
    origin=api.api_origin.rstrip('/')
    assert origin.startswith('http://127.0.0.1:')
    sessions={}
    for name,home in [('default',launch),('field',secondary)]:
        found=await asyncio.to_thread(discover_gateway_endpoint,home,timeout=5)
        assert found.state=='ready' and found.endpoint is not None
        async with connect_owner(home,found.endpoint) as client:
            made=await client.rpc('session.create',_timeout=30,request_id='profile-ticket-create-'+name,source='gui',cwd=str(workspace_cwds[0]),cwd_explicit=True,model='fixture-no-inference',provider='custom',base_url='http://127.0.0.1:1/v1',api_key='fixture-no-inference',toolsets=[])
            sessions[name]=made
    phase('TWO_REAL_PROFILE_LOCAL_SESSIONS_CREATED_WITHOUT_INFERENCE')
    verifier=secrets.token_urlsafe(32);state=secrets.token_urlsafe(32);challenge=base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b'=').decode()
    callback='http://127.0.0.1:43127/callback'
    async with ClientSession(cookie_jar=CookieJar(unsafe=True)) as browser:
        async with browser.get(origin+'/auth/native/authorize',params=dict(code_challenge=challenge,code_challenge_method='S256',redirect_uri=callback,state=state),allow_redirects=False) as response:
            assert response.status==302
            location=urllib.parse.urljoin(origin+'/',response.headers['Location'])
            assert location.startswith(origin+'/auth/callback?')
        async with browser.get(location,allow_redirects=False) as response:
            assert response.status==302
            destination=response.headers['Location'];parsed=urllib.parse.urlsplit(destination);values=urllib.parse.parse_qs(parsed.query,strict_parsing=True)
            assert destination.startswith(callback+'?') and values['state']==[state] and set(values)=={'code','state'}
    async with ClientSession(cookie_jar=DummyCookieJar()) as client:
        async with client.post(origin+'/auth/native/token',json=dict(code=values['code'][0],code_verifier=verifier)) as response:
            assert response.status==200
            tokens=await response.json()
        headers={'Authorization':'Bearer '+tokens['access_token']}
        async with client.get(origin+'/api/auth/me',headers=headers) as response:
            assert response.status==200
            identity=await response.json();assert identity['provider']==tokens['provider'] and identity['user_id']==tokens['user_id']
        phase('REAL_NATIVE_PKCE_BEARER_IDENTITY_WITH_SYNTHETIC_IDP')
        async with client.post(origin+'/api/webapp/socket-ticket',params=dict(socket='gateway',profile='field'),json={}) as response:
            assert response.status==401
        phase('PROFILE_TICKET_MINT_REQUIRES_AUTHENTICATION')
        async def ticket(path,**query):
            async with client.post(origin+path,params=query,headers=headers,json={}) as response:
                data=await response.json()
                if response.status==404:return 404,None
                assert response.status==200 and isinstance(data['ticket'],str) and data['ttl_seconds']==30
                return 200,data['ticket']
        status,value=await ticket('/api/auth/ws-ticket');assert status==200
        async with connect(origin.replace('http://','ws://')+'/api/ws',subprotocols=['hermes-gateway-v1','hermes-gateway-ticket.'+value],proxy=None,open_timeout=10) as ws:
            assert ws.subprotocol=='hermes-gateway-v1'
            async with GatewayClient(ws) as rpc:
                info=await rpc.rpc('runtime.describe',_timeout=10);assert Path(info['profile_id']).resolve()==launch
                try:await rpc.rpc('runtime.describe',_timeout=10,profile='field')
                except GatewayRPCError as e:assert str(e)=='invalid_params'
                else:raise AssertionError('Expected exact reviewed describe profile refusal')
        phase('GENERIC_TICKET_BINDS_LAUNCH_AND_DESCRIBE_CANNOT_SELECT_SIBLING')
        code,value=await ticket('/api/webapp/socket-ticket',socket='gateway',profile='field')
        record['profileTicketProbe']=dict(surface=a.probe_profile_tickets,genericTicketProfile='default',scopedTicketStatus=code,defaultSession=sessions['default']['session_id'],fieldSession=sessions['field']['session_id'],syntheticRendererNotExercised=a.probe_profile_tickets=='webapp',snapshots={name:dict(sessionId=row['session_id'],cwd=str(workspace_cwds[0]),revision=row['revision'],generation=row['execution_generation']) for name,row in sessions.items()})
        if a.probe_profile_tickets=='gateway':
            assert code==404 and value is None
            phase('GATEWAY_SURFACE_PROFILE_TICKET_UNAVAILABLE_WITHOUT_FALLBACK');return
        assert code==200
        authority=runner.session_authorities.require(secondary)
        async with connect(origin.replace('http://','ws://')+'/api/ws',subprotocols=['hermes-gateway-v1','hermes-gateway-ticket.'+value],proxy=None,open_timeout=10) as ws:
            assert ws.subprotocol=='hermes-gateway-v1'
            async with GatewayClient(ws) as rpc:
                info=await rpc.rpc('runtime.describe',_timeout=10);assert Path(info['profile_id']).resolve()==secondary and info['authority_epoch']==authority.epoch
                listed=await rpc.rpc('session.list',_timeout=10,scope='live',limit=200);assert {x['session_id'] for x in listed['sessions']}=={sessions['field']['session_id']}
                try:await rpc.rpc('session.info',_timeout=10,session_id=sessions['default']['session_id'])
                except GatewayRPCError as e:assert str(e)=='not_found'
                else:raise AssertionError('Field socket reached default session')
                phase('REAL_WEBAPP_PROFILE_TICKET_BINDS_FIELD_WITHOUT_FOREIGN_HISTORY')
                try:
                    async with connect(origin.replace('http://','ws://')+'/api/ws',subprotocols=['hermes-gateway-v1','hermes-gateway-ticket.'+value],proxy=None,open_timeout=10):raise AssertionError('Scoped ticket replay opened a socket')
                except InvalidStatus as e:assert e.response.status_code==403
                record['profileTicketProbe']['scopedTicketReplayRefused']=True
                phase('SCOPED_WEBAPP_TICKET_CANNOT_BE_REPLAYED')
                await runner._unserve_profile('field',secondary)
                assert authority.retired and runner.session_authorities.for_home(secondary) is None
                row=sessions['field'];rid=str(uuid.uuid4())
                try:await rpc.rpc('session.mutate',_timeout=10,session_id=row['session_id'],request_id=rid,expected_revision=row['revision'],expected_generation=row['execution_generation'],operation='workspace',payload={'cwd':str(workspace_cwds[1])})
                except GatewayRPCError as e:
                    assert str(e)=='runtime_draining' and e.code==4001 and e.data.get('reason')=='runtime_draining' and authority.retired and authority.retiring and not runner._draining;record['profileTicketProbe']['retiredMutationRefusal']='runtime_draining'
                else:raise AssertionError('Retired profile accepted workspace mutation')
                record['profileTicketProbe']['mutationRequestId']=rid
                phase('GENUINE_PROFILE_UNSERVE_REFUSES_STALE_WORKSPACE_WITHOUT_REPLAY')
        code,value=await ticket('/api/webapp/socket-ticket',socket='gateway',profile='field')
        if code==200:
            try:
                async with connect(origin.replace('http://','ws://')+'/api/ws',subprotocols=['hermes-gateway-v1','hermes-gateway-ticket.'+value],proxy=None,open_timeout=10):raise AssertionError('Unserved profile ticket reached default')
            except InvalidStatus as e:assert e.response.status_code==403
        else:assert code==404
        record['profileTicketProbe']['afterUnserveTicketStatus']=code
        phase('UNSERVED_PROFILE_CANNOT_OPEN_DEFAULT_AS_FALLBACK')
        code,value=await ticket('/api/auth/ws-ticket');assert code==200
        async with connect(origin.replace('http://','ws://')+'/api/ws',subprotocols=['hermes-gateway-v1','hermes-gateway-ticket.'+value],proxy=None,open_timeout=10) as ws:
            async with GatewayClient(ws) as rpc:
                info=await rpc.rpc('runtime.describe',_timeout=10);assert Path(info['profile_id']).resolve()==launch
                current=await rpc.rpc('session.info',_timeout=10,session_id=sessions['default']['session_id']);assert current['cwd']==str(workspace_cwds[0])
        record['profileTicketProbe']['defaultStillServing']=True
        phase('DEFAULT_AUTHORITY_STILL_SERVING_AFTER_SIBLING_UNSERVE')

async def serve_profile_tickets_android(runner,api):
    """One real Android/native-auth/profile/cache case, with explicit host retirement."""
    import socket,time,uuid,uvicorn
    from tui_gateway.owner_forward import connect_owner
    from hermes_cli.gateway_runtime import discover_gateway_endpoint
    from gateway.run_api import GatewayRuntimeAPI
    surface=a.android_profile_tickets;case_id=str(uuid.uuid4());homes={'default':launch,'field':secondary};sessions={};snapshots={}
    for name,home in homes.items():
        found=await asyncio.to_thread(discover_gateway_endpoint,home,timeout=5)
        assert found.state=='ready' and found.endpoint is not None
        async with connect_owner(home,found.endpoint) as client:
            made=await client.rpc('session.create',_timeout=30,request_id='android-profile-create-'+name,source='gui',cwd=str(workspace_cwds[0]),cwd_explicit=True,model='fixture-no-inference',provider='custom',base_url='http://127.0.0.1:1/v1',api_key='fixture-no-inference',toolsets=[])
            sessions[name]=made['session_id']
        db=runner.session_authorities.require(home).db
        rows=db._read_all('SELECT id,cwd,runtime_revision,runtime_generation FROM sessions')
        assert len(rows)==1 and rows[0][0]==sessions[name]
        snapshots[name]=dict(zip(('sessionId','cwd','revision','generation'),rows[0]))
    real_app=api.server.config.loaded_app;assert isinstance(real_app,GatewayRuntimeAPI)
    requests=[];rpc_requests=[];rejected=[];retired=False;resumed=set();replayed=set();pinged=set()
    allowed={('GET','/auth/native/authorize'),('GET','/auth/callback'),('POST','/auth/native/token'),('POST','/auth/native/refresh'),('GET','/api/auth/me'),('GET','/api/profiles'),('POST','/api/auth/ws-ticket'),('POST','/api/webapp/socket-ticket')}
    async def prefix(scope,receive,send):
        if scope['type'] not in ('http','websocket'):return
        path=scope['path'];headers=list(scope['headers']);query=scope.get('query_string',b'')
        if not path.startswith('/dashboard/') or len(requests)>=512 or len(query)>8192:
            rejected.append('scope');raise RuntimeError('Unreviewed profile fixture scope')
        short=path[len('/dashboard'):]
        forwarded=dict(scope,path=short,raw_path=short.encode('ascii'),root_path='',headers=[(k,v) for k,v in headers if not k.lower().startswith(b'x-forwarded-')]+[(b'x-forwarded-prefix',b'/dashboard')])
        if scope['type']=='http':
            if scope['method']=='GET' and short=='/__probe/state' and not query:
                body=json.dumps(dict(caseId=case_id,state='retired' if retired else 'waiting')).encode()
                await send(dict(type='http.response.start',status=200,headers=[(b'content-type',b'application/json'),(b'content-length',str(len(body)).encode())]));await send(dict(type='http.response.body',body=body));return
            if (scope['method'],short) not in allowed:
                rejected.append('route');raise RuntimeError('Unreviewed profile fixture route')
            if short=='/api/webapp/socket-ticket' and urllib.parse.parse_qs(query.decode('ascii'),strict_parsing=True)!={'socket':['gateway'],'profile':['field']}:
                rejected.append('ticket-query');raise RuntimeError('Foreign profile ticket')
            entry=dict(method=scope['method'],path=short);requests.append(entry);received=0
            async def bounded():
                nonlocal received
                message=await receive();received+=len(message.get('body',b''));assert received<=32768
                return message
            async def observed(message):
                if message['type']=='http.response.start':entry['status']=message['status']
                await send(message)
            await real_app(forwarded,bounded,observed);return
        if short!='/api/ws' or query or any(k.lower() in (b'authorization',b'cookie',b'origin') for k,v in headers):
            rejected.append('upgrade');await send(dict(type='websocket.close',code=1008));return
        entry=dict(method='WS',path=short,rpc=[]);requests.append(entry);ids={};bound=None;resume_cursor=None
        async def bounded_ws():
            message=await receive()
            if message['type']!='websocket.receive':return message
            try:
                raw=message.get('text');assert isinstance(raw,str) and len(raw.encode())<=32768 and len(rpc_requests)<128
                frame=json.loads(raw);assert set(frame)=={'jsonrpc','id','method','params'} and frame['jsonrpc']=='2.0'
                rid=frame['id'];assert type(rid) in (str,int) and len(str(rid))<=128 and rid not in ids
                method,params=frame['method'],frame['params']
                assert profile_android_rpc_allowed(method,params,profile=bound,session_id=sessions.get(bound),resume_cursor=resume_cursor,resumed=bound in resumed,replayed=bound in replayed,pinged=bound in pinged)
                if method=='session.resume':resumed.add(bound)
                if method=='session.events.since':replayed.add(bound)
                if method=='gateway.ping':pinged.add(bound)
                ids[rid]=method;entry['rpc'].append(method);rpc_requests.append(dict(profile=bound,method=method))
            except BaseException:
                rejected.append('rpc');await send(dict(type='websocket.close',code=1008));raise
            return message
        async def observed_ws(message):
            nonlocal bound,resume_cursor
            if message['type']=='websocket.accept':entry['accepted']=True
            if message['type']=='websocket.close':entry['serverCloseCode']=message.get('code')
            if message['type']=='websocket.send' and isinstance(message.get('text'),str):
                frame=json.loads(message['text']);method=ids.get(frame.get('id'));result=frame.get('result')
                if method=='runtime.describe' and isinstance(result,dict):
                    names=[name for name,home in homes.items() if result.get('profile_id')==str(home)];assert len(names)==1
                    bound=names[0];entry['profile']=bound
                    assert result['instance_id']==runner.session_runtime_descriptor['instance_id']
                    assert result['authority_epoch']==runner.session_authorities.require(homes[bound]).epoch
                if method=='session.resume':
                    assert result['session_id']==sessions[bound] and result['message_count']==0 and result['running'] is False
                    assert result['info']['cwd']==str(workspace_cwds[0])
                    resume_cursor=dict(last_sequence=result['last_sequence'],replay_epoch=result['replay_epoch'])
            await send(message)
        await real_app(forwarded,bounded_ws,observed_ws)
    listener=socket.socket();listener.bind(('127.0.0.1',0));listener.listen(64);port=listener.getsockname()[1]
    server=uvicorn.Server(uvicorn.Config(prefix,host='127.0.0.1',port=0,loop='asyncio',http='h11',ws='websockets',lifespan='off',log_config=None,access_log=False,log_level='warning',timeout_graceful_shutdown=5))
    task=asyncio.create_task(server.serve(sockets=[listener]))
    try:
        deadline=time.monotonic()+15
        while not server.started and not task.done() and time.monotonic()<deadline:await asyncio.sleep(.05)
        assert server.started and not task.done()
        case=dict(caseId=case_id,surface=surface,sourceCommit=COMMIT,api=a.android_api,baseUrl=f'http://127.0.0.1:{port}/dashboard/',profileHomes={k:str(v) for k,v in homes.items()},sessions=sessions,workspace=str(workspace_cwds[0]))
        ready=dict(state='READY_ACTUAL_OWNER_PROFILE_ANDROID_FIXTURE',pid=os.getpid(),port=port,case=case,harnessSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())
        pending=root/'ready.pending';pending.write_text(json.dumps(ready)+'\n');pending.replace(root/'ready.json')
        phase('READY_FOR_ONE_ANDROID_PROFILE_RETIREMENT_CASE')
        deadline=time.monotonic()+600
        while not (root/'retire-request.json').exists() and not (root/'android-result.json').exists() and not task.done() and time.monotonic()<deadline:await asyncio.sleep(.05)
        assert (root/'retire-request.json').exists(),'No reviewed retirement request; preserve exact case'
        stage=json.loads((root/'retire-request.json').read_text())
        assert stage==dict(state='CACHE_CLOSED_FOR_PROFILE_RETIREMENT',caseId=case_id,surface=surface,api=a.android_api,sourceCommit=COMMIT,databaseName='owner-profile-'+case_id+'.db',sessions=sessions)
        expected={'default','field'} if surface=='webapp' else {'default'}
        assert resumed==replayed==pinged==expected
        authority=runner.session_authorities.require(secondary)
        await runner._unserve_profile('field',secondary)
        assert authority.retired and authority.retiring and not runner._draining
        assert runner.session_authorities.for_home(secondary) is None and runner.session_authorities.profile_ids()==frozenset([str(launch)])
        assert not runner.session_authorities.require(launch).retired
        retired=True;phase('ACTUAL_FIELD_AUTHORITY_RETIRED_AFTER_ANDROID_CACHE_CLOSE')
        while not (root/'android-result.json').exists() and not task.done() and time.monotonic()<deadline:await asyncio.sleep(.1)
        assert (root/'android-result.json').exists(),'Android result unknown; preserve exact case'
        proof=json.loads((root/'android-result.json').read_text());assert proof['instrumentationPassed'] is True and proof['distinctMethods']==1
        result=proof['result']
        assert result['state']=='PASS_ANDROID_REAL_OWNER_PROFILE_RETIREMENT' and result['backend']=='actual-owner'
        assert result['caseId']==case_id and result['surface']==surface and result['api']==a.android_api and result['sourceCommit']==COMMIT
        assert result['savedCredentialsUnchanged'] is True and result['modelOrWorkerExecutionClaim'] is False and result['recordCount']==len(expected)
        assert not rejected and resumed==replayed==pinged==expected
        assert sum(row['method']=='session.resume' for row in rpc_requests)==len(expected)
        assert sum(row['method']=='session.events.since' for row in rpc_requests)==len(expected)
        record.update(android=result,profileTicketAndroid=dict(surface=surface,snapshots=snapshots,caseId=case_id,requests=requests,rpcRequests=rpc_requests,rejected=rejected,retired=retired))
        phase('ANDROID_PROFILE_CACHE_RETIREMENT_RECONCILED_WITH_REAL_AUTHORITIES')
    finally:
        record['profileTicketAndroid']=dict(surface=surface,snapshots=snapshots,caseId=case_id,requests=requests,rpcRequests=rpc_requests,rejected=rejected,retired=retired)
        server.should_exit=True;await asyncio.wait_for(task,15);listener.close()

async def serve_project_conversations_android(runner,api):
    """Read the actual owner project/session stores; no native session adoption."""
    import socket,time,uuid,uvicorn
    from tui_gateway.owner_forward import connect_owner
    from hermes_cli.gateway_runtime import discover_gateway_endpoint
    from gateway.run_api import GatewayRuntimeAPI
    from gateway.session_authorities import owner_scope
    from hermes_cli import projects_db as pdb
    import sqlite3
    surface=a.android_project_conversations;case_id=str(uuid.uuid4());homes={'default':launch,'field':secondary};projects={};snapshots={};expected={}
    for index,(name,home) in enumerate(homes.items()):
        authority=runner.session_authorities.require(home);db=authority.db;cwd=workspace_cwds[index]
        marker=cwd/'fixture-read-only.txt';marker.write_text(name+' fixture project marker\n')
        with owner_scope(authority):
            for sid,source_kind,directory,parent,archived in [('shared-project-fixture','desktop',cwd,None,False),(name+'-project-fixture','cli',cwd,None,False),('outside-fixture','cli',workspace_cwds[1-index],None,False),('archived-fixture','cli',cwd,None,True),('cron-fixture','cron',cwd,None,False),('child-fixture','cli',cwd,'shared-project-fixture',False),('empty-fixture','cli',cwd,None,False)]:
                db.create_session(sid,source=source_kind)
                db._write_sql('UPDATE sessions SET cwd=?,title=?,archived=?,parent_session_id=? WHERE id=?',(str(directory),name+' '+sid,int(archived),parent,sid))
                if sid!='empty-fixture':
                    db.append_message(sid,'user',name+' private stored user',timestamp=100)
                    db.append_message(sid,'assistant',name+' private stored reply',timestamp=101,reasoning_content=name+' private reasoning sentinel')
                db.end_session(sid,end_reason='fixture_closed')
        with pdb.connect_closing(home/'projects.db') as conn:
            pid=pdb.create_project(conn,name=name+' fixture project',slug='fixture-project',primary_path=str(cwd))
            pdb.set_active(conn,pid)
            pdb.record_discovered_repos(conn,[(str(workspace_cwds[1-index]),name+' retained cache')],policy_key='fixture-prior-policy')
            projects[name]=pid;project_digest=history_snapshot_digest(project_fixture_tables(conn,('projects','project_folders','project_meta','discovered_repos')))
        with db._read_ctx() as conn:history_digest=history_snapshot_digest(project_fixture_tables(conn,('sessions','messages')))
        snapshots[name]=dict(historyDigest=history_digest,projectDigest=project_digest)
        expected[name]=dict(projectId=pid,name=name+' fixture project',directory=str(cwd),sessionIds=['shared-project-fixture',name+'-project-fixture'])
    def workspace_digest():return {p.relative_to(root/'workspace').as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in (root/'workspace').rglob('*') if p.is_file()}
    workspace_before=workspace_digest()
    phase('PROJECTS_AND_CLOSED_FILTER_FIXTURES_SEEDED_BEFORE_READ_ONLY_CLIENT')
    real_app=api.server.config.loaded_app;assert isinstance(real_app,GatewayRuntimeAPI)
    requests=[];rpc_requests=[];rejected=[]
    allowed={('GET','/auth/native/authorize'),('GET','/auth/callback'),('POST','/auth/native/token'),('POST','/auth/native/refresh'),('GET','/api/auth/me'),('GET','/api/profiles'),('POST','/api/auth/ws-ticket'),('POST','/api/webapp/socket-ticket')}
    async def prefix(scope,receive,send):
        if scope['type'] not in ('http','websocket'):return
        path=scope['path'];headers=list(scope['headers']);query=scope.get('query_string',b'')
        if not path.startswith('/dashboard/') or len(requests)>=512 or len(query)>8192:
            rejected.append('scope');raise RuntimeError('Unreviewed profile fixture scope')
        short=path[len('/dashboard'):]
        forwarded=dict(scope,path=short,raw_path=short.encode('ascii'),root_path='',headers=[(k,v) for k,v in headers if not k.lower().startswith(b'x-forwarded-')]+[(b'x-forwarded-prefix',b'/dashboard')])
        if scope['type']=='http':
            if (scope['method'],short) not in allowed:
                rejected.append('route');raise RuntimeError('Unreviewed profile fixture route')
            if short=='/api/webapp/socket-ticket' and urllib.parse.parse_qs(query.decode('ascii'),strict_parsing=True)!={'socket':['gateway'],'profile':['field']}:
                rejected.append('ticket-query');raise RuntimeError('Foreign profile ticket')
            entry=dict(method=scope['method'],path=short);requests.append(entry);received=0
            async def bounded():
                nonlocal received
                message=await receive();received+=len(message.get('body',b''));assert received<=32768
                return message
            async def observed(message):
                if message['type']=='http.response.start':entry['status']=message['status']
                await send(message)
            await real_app(forwarded,bounded,observed);return
        if short!='/api/ws' or query or any(k.lower() in (b'authorization',b'cookie',b'origin') for k,v in headers):
            rejected.append('upgrade');await send(dict(type='websocket.close',code=1008));return
        entry=dict(method='WS',path=short,rpc=[]);requests.append(entry);ids={};bound=None
        async def bounded_ws():
            message=await receive()
            if message['type']!='websocket.receive':return message
            try:
                raw=message.get('text');assert isinstance(raw,str) and len(raw.encode())<=32768 and len(rpc_requests)<128
                frame=json.loads(raw);assert set(frame)=={'jsonrpc','id','method','params'} and frame['jsonrpc']=='2.0'
                rid=frame['id'];assert type(rid) in (str,int) and len(str(rid))<=128 and rid not in ids
                method,params=frame['method'],frame['params']
                assert project_android_rpc_allowed(method,params,profile=bound,projects=projects)
                ids[rid]=method;entry['rpc'].append(method);rpc_requests.append(dict(profile=bound,method=method))
            except BaseException:
                rejected.append('rpc');await send(dict(type='websocket.close',code=1008));raise
            return message
        async def observed_ws(message):
            nonlocal bound
            if message['type']=='websocket.accept':entry['accepted']=True
            if message['type']=='websocket.close':entry['serverCloseCode']=message.get('code')
            if message['type']=='websocket.send' and isinstance(message.get('text'),str):
                frame=json.loads(message['text']);method=ids.get(frame.get('id'));result=frame.get('result')
                if method=='runtime.describe' and isinstance(result,dict):
                    names=[name for name,home in homes.items() if result.get('profile_id')==str(home)];assert len(names)==1
                    bound=names[0];entry['profile']=bound
                    assert result['instance_id']==runner.session_runtime_descriptor['instance_id']
                    assert result['authority_epoch']==runner.session_authorities.require(homes[bound]).epoch
            await send(message)
        await real_app(forwarded,bounded_ws,observed_ws)
    listener=socket.socket();listener.bind(('127.0.0.1',0));listener.listen(64);port=listener.getsockname()[1]
    server=uvicorn.Server(uvicorn.Config(prefix,host='127.0.0.1',port=0,loop='asyncio',http='h11',ws='websockets',lifespan='off',log_config=None,access_log=False,log_level='warning',timeout_graceful_shutdown=5))
    task=asyncio.create_task(server.serve(sockets=[listener]))
    try:
        deadline=time.monotonic()+15
        while not server.started and not task.done() and time.monotonic()<deadline:await asyncio.sleep(.05)
        assert server.started and not task.done()
        case=dict(caseId=case_id,surface=surface,sourceCommit=COMMIT,api=a.android_api,baseUrl=f'http://127.0.0.1:{port}/dashboard/',profileHomes={k:str(v) for k,v in homes.items()},projects=expected)
        ready=dict(state='READY_ACTUAL_OWNER_PROJECT_ANDROID_FIXTURE',pid=os.getpid(),port=port,case=case,harnessSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())
        pending=root/'ready.pending';pending.write_text(json.dumps(ready)+'\n');pending.replace(root/'ready.json')
        phase('READY_FOR_ONE_READ_ONLY_ANDROID_PROJECT_CASE')
        deadline=time.monotonic()+600
        while not (root/'android-result.json').exists() and not task.done() and time.monotonic()<deadline:await asyncio.sleep(.1)
        assert (root/'android-result.json').exists(),'Android project result unknown; preserve exact case'
        proof=json.loads((root/'android-result.json').read_text());assert proof['instrumentationPassed'] is True and proof['distinctMethods']==1
        result=proof['result'];assert result['state']=='PASS_ANDROID_PROJECT_CONVERSATIONS' and result['backend']=='actual-owner'
        assert result['caseId']==case_id and result['surface']==surface and result['sourceCommit']==COMMIT and result['api']==a.android_api
        assert result['savedCredentialsUnchanged'] and result['sessionAdoptionCount']==0 and result['workerExecutionCount']==0
        assert result['supportedProfiles']==(['default','field'] if surface=='webapp' else ['default'])
        assert not rejected and all(row['method'] in ('runtime.describe','session.list','projects.list','projects.get','projects.project_sessions') for row in rpc_requests)
        for name,home in homes.items():
            with runner.session_authorities.require(home).db._read_ctx() as conn:
                assert history_snapshot_digest(project_fixture_tables(conn,('sessions','messages')))==snapshots[name]['historyDigest']
            with pdb.connect_closing(home/'projects.db') as conn:
                assert history_snapshot_digest(project_fixture_tables(conn,('projects','project_folders','project_meta','discovered_repos')))==snapshots[name]['projectDigest']
        assert workspace_digest()==workspace_before
        record.update(android=result,projectConversations=dict(surface=surface,snapshots=snapshots,caseId=case_id,requests=requests,rpcRequests=rpc_requests,rejected=rejected,workspaceUnchanged=True))
        phase('ANDROID_PROJECT_GROUPING_AND_EXACT_PROFILE_READ_ONLY_EFFECTS_VERIFIED')
    finally:
        record.setdefault('projectConversations',dict(surface=surface,snapshots=snapshots,caseId=case_id,requests=requests,rpcRequests=rpc_requests,rejected=rejected,workspaceUnchanged=False))
        server.should_exit=True;await asyncio.wait_for(task,15);listener.close()

async def run():
    from hermes_cli.config import atomic_config_write
    import secrets
    fixture_keys={str(home):secrets.token_hex(32) for home in (launch,secondary)} if a.read_api_history else {}
    if (a.probe_profile_tickets or a.android_profile_tickets or a.android_project_conversations)=='webapp':
        dist=root/'synthetic-renderer';dist.mkdir();(dist/'index.html').write_text('<!doctype html><title>Fixture renderer: not exercised</title>',encoding='utf-8');os.environ['HERMES_WEBAPP_DIST']=str(dist)
    for profile_home in (launch,secondary):
        if a.read_api_history:(profile_home/'.env').write_text('API_SERVER_KEY='+fixture_keys[str(profile_home)]+'\n',encoding='utf-8')
        atomic_config_write(profile_home/'config.yaml',{'gateway':{'multiplex_profiles':True,'loop_watchdog':False},
            'dashboard':{'startup_orphan_sweep':False},'agent':{'environment_probe':False},'terminal':{'cwd':str(root)},'model_catalog':{'enabled':False}})
    from gateway.run import GatewayRunner,_refresh_host_gateway_record
    from gateway.config import GatewayConfig,Platform,PlatformConfig
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
    config=GatewayConfig(multiplex_profiles=True,loop_watchdog=False,platforms={Platform.API_SERVER:PlatformConfig(enabled=True,extra=dict(host="127.0.0.1",port=0,key=fixture_keys[str(launch)]))} if a.read_api_history else {})
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
        if (a.probe_profile_tickets or a.android_profile_tickets or a.android_project_conversations)=='webapp':
            from gateway.webapp_listener import WebappMount
            api=await run_api.start_gateway_api(runner,host='127.0.0.1',port=0,webapp=WebappMount(dist=root/'synthetic-renderer',home=launch))
            runner.session_api=api;runner.session_runtime_descriptor['api_origin']=api.api_origin
        else:
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
        if a.probe_profile_tickets:
            await profile_ticket_probe(runner,api)
            record['state']='PASS_ACTUAL_OWNER_PROFILE_TICKET_PROBE'
            return
        if a.android_project_conversations:
            await serve_project_conversations_android(runner,api)
            record['state']='PASS_ANDROID_REAL_OWNER_PROJECT_CONVERSATIONS'
            return
        if a.android_profile_tickets:
            await serve_profile_tickets_android(runner,api)
            record['state']='PASS_ANDROID_REAL_OWNER_PROFILE_RETIREMENT'
            return
        if a.read_api_history:
            await serve_closed_history_android(runner,fixture_keys)
            record['state']='PASS_ANDROID_REAL_OWNER_CLOSED_HISTORY'
            return
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
        scope=('Actual Android read-only project conversation grouping and profile-bound stored inventory; synthetic IdP/renderer, no resume/work/worker/fleet or full lifecycle acceptance.' if a.android_project_conversations else 'Actual Android/native-auth/profile attachment/retirement and receipt/cursor Room proof; synthetic IdP and renderer placeholder, no inference, production or full lifecycle acceptance.' if a.android_profile_tickets else 'Actual owner native-auth/profile ticket/retirement backend proof; synthetic IdP/renderer placeholder, no Android, model, worker, production or complete Webapp/lifecycle acceptance; shutdown diagnostics remain separate.' if a.probe_profile_tickets else ('Actual owner/bearer API/closed seeded history/Room reads. No native login, inference, production or complete lifecycle acceptance; final shutdown diagnostics remain separate.' if a.read_api_history else 'One bounded Android client over prefixed real-owner HTTP/PKCE/ticket/canonical workspace and Room proof; synthetic IdP only; no production IdP, Pi, inference/tool/worker or complete lifecycle acceptance. Final shutdown diagnostics must also be accepted.')))
    (root/'report.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(dict(state=record['state'],modules=len(imported),denials=len(denied))),flush=True)
if record['state']!=('PASS_ANDROID_REAL_OWNER_PROJECT_CONVERSATIONS' if a.android_project_conversations else 'PASS_ANDROID_REAL_OWNER_PROFILE_RETIREMENT' if a.android_profile_tickets else 'PASS_ACTUAL_OWNER_PROFILE_TICKET_PROBE' if a.probe_profile_tickets else ('PASS_ANDROID_REAL_OWNER_CLOSED_HISTORY' if a.read_api_history else 'PASS_ANDROID_REAL_OWNER_WORKSPACE_TRANSPORT')) or denied:sys.exit(1)
