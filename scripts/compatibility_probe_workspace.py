"""Bounded real Hermes workspace mutation component probe on native Windows.

Uses existing dependencies, an immutable clean source checkout and a fresh E: root.
Trusted synthetic actors/runner callback exercise actual dispatch, SQLite and policy;
this is not HTTP/native authentication, a tool/model turn or deployed-fleet acceptance.
Python audit hooks bound access but are not an OS security sandbox. One supervised
attempt preserves failed/unknown outcomes and never restarts or replays them.

Fixture construction helpers adapted from NousResearch/hermes-agent at the pinned
commit. Copyright (c) 2025 Nous Research; MIT license reproduced in
../docs/licenses/hermes-workspace-fixture-MIT.txt.
"""
from pathlib import Path
import argparse
import atexit
import shutil
import hashlib
import ipaddress
import json
import os
import subprocess
import sys
import traceback
import urllib.parse
import weakref

SOURCE_COMMIT='2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19'
PINNED_BLOBS = {'gateway/session_mutations.py': '6e6dbffbe8feaf1482cb28265597c9b5ca06ae87', 'gateway/session_mutation_policy.py': '626c92aa4f4f3dc0f341be9920d303ba6e5e1682', 'gateway/session_contract.py': 'accd1d9e2109e34c19263b90d730fad400128e6c', 'gateway/session_local.py': '9b78e982ea040eabb8dcade4286259515c71bc24', 'hermes_state_mutations.py': 'c809fca4a43b8e6f5187a472b40b602cdadb5dbe', 'hermes_state_mutation_prepared.py': '5638ed213f7c00d53da282c34800dca84ac5fcdc', 'hermes_state_mutation_guards.py': '46ed9bdceb66495449ade1b0ce273bb36fb955db', 'hermes_state_mutation_binding.py': '265c33fe16cfda436351f989746f21aee1e85457', 'hermes_state_mutation_retirement.py': '0e3afdb7d3fa450963e86e8fb3bb20edca2a47d0', 'hermes_state_runtime.py': '49207f5508db3255cd90433a229c518f11cfd0f7'}
parser=argparse.ArgumentParser()
parser.add_argument('--source-root',type=Path,required=True)
parser.add_argument('--dependency-site',type=Path,required=True)
parser.add_argument('--work-root',type=Path,required=True)
parser.add_argument('--owned-child',action='store_true',help=argparse.SUPPRESS)
a=parser.parse_args()
assert os.name=='nt' and sys.flags.isolated and sys.flags.no_site and sys.dont_write_bytecode
if not a.owned_child:
    source,site,root=a.source_root.resolve(),a.dependency_site.resolve(),a.work_root.resolve()
    assert source.is_dir() and site.is_dir() and not root.exists() and root.drive.upper()=='E:'
    assert not any(x.is_relative_to(y) for x,y in ((root,source),(source,root),(root,site),(site,root)))
    receipt=root.with_suffix('.supervisor.json');log=root.with_suffix('.supervisor.log')
    assert not receipt.exists() and not log.exists()
    command=[sys.executable,'-I','-S','-B',str(Path(__file__).resolve()),'--owned-child',
        '--source-root',str(source),'--dependency-site',str(site),'--work-root',str(root)]
    environment={k:v for k,v in os.environ.items() if k.upper() in ('SYSTEMROOT','WINDIR','PATH','PATHEXT')}
    environment.update(TEMP=str(root.parent),TMP=str(root.parent))
    observation=dict(state='STARTING_OWNED_COMPONENT',command=command,harnessSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())
    def save():receipt.write_text(json.dumps(observation,indent=2)+'\n',encoding='utf-8')
    save()
    with log.open('xb') as stream:
        child=subprocess.Popen(command,env=environment,stdin=subprocess.DEVNULL,stdout=stream,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
        observation.update(state='OWNED_COMPONENT_RUNNING',pid=child.pid);save()
        try:code=child.wait(timeout=420)
        except subprocess.TimeoutExpired:
            observation['state']='UNKNOWN_INSPECT_EXACT_PID_AND_ROOT_NO_REPLAY';save();raise SystemExit(3)
    observation.update(state='TERMINAL_REVIEW_PENDING',exitCode=code);save()
    if code!=0:
        observation['state']='FAILED_CHILD_PRESERVED';save();raise SystemExit(1)
    result=json.loads((root/'workspace-result.json').read_text())
    final=json.loads((root/'exit-boundary.json').read_text())
    assert result['state']=='PASS_REAL_WORKSPACE_MUTATION_COMPONENT' and len(result['cases'])==11
    assert not result['deniedEvents'] and not final['deniedEvents'] and result['modules']
    import sqlite3
    checks=[]
    for path in (root/'hermes-home').glob('*.db'):
        with sqlite3.connect('file:'+path.as_posix()+'?mode=ro',uri=True) as db:
            assert db.execute('PRAGMA quick_check').fetchall()==[('ok',)]
            tables={row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
            if 'session_admissions' in tables:
                statuses=db.execute('SELECT status,COUNT(*) FROM session_admissions GROUP BY status').fetchall()
                assert statuses==[('terminal',1)],statuses
                checks.append(dict(database=path.name,admissions=statuses,quickCheck='ok'))
    assert checks
    observation.update(state='PASS_REAL_WORKSPACE_COMPONENT_PROCESS_EXIT',cases=11,sourceModules=len(result['modules']),deniedEvents=[],stores=checks,sourceCommit=SOURCE_COMMIT)
    save();print(json.dumps(observation));raise SystemExit(0)
source, root, site = a.source_root.resolve(), a.work_root.resolve(), a.dependency_site.resolve()
assert source.is_dir() and site.is_dir() and not root.exists(), 'Use existing dependencies and a fresh output root.'
assert source.drive.upper() == root.drive.upper() == 'E:', 'Source and temporary output must stay on E:.'
assert not source.is_relative_to(root) and not root.is_relative_to(source)
assert not root.is_relative_to(site) and not site.is_relative_to(root)
for name, expected in PINNED_BLOBS.items():
    path = source / name
    assert not path.is_symlink() and path.resolve().is_relative_to(source)
    raw = path.read_bytes()
    actual = hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest()
    assert actual == expected, 'Pinned module differs: ' + name
root.mkdir(parents=True, exist_ok=False)
user, temp = root / 'account', root / 'temp'
for path in (user, user / 'AppData/Local', user / 'AppData/Roaming', temp, root / 'hermes-home', root / 'runtime'):
    path.mkdir(parents=True, exist_ok=True)
# Retain only OS loader variables and Git's executable search path for two local
# read-only identity checks. No production Hermes environment or credentials.
git_env = {k: v for k, v in os.environ.items() if k.upper() in ('SYSTEMROOT', 'WINDIR', 'PATH', 'PATHEXT')}
git_env.update(TEMP=str(temp), TMP=str(temp), HOME=str(user), USERPROFILE=str(user),
               GIT_OPTIONAL_LOCKS='0', GIT_NO_LAZY_FETCH='1', GIT_CONFIG_NOSYSTEM='1',
               GIT_CONFIG_GLOBAL=os.devnull, GIT_TERMINAL_PROMPT='0')
def git_read(*args):
    result = subprocess.run(['git', '--no-optional-locks', '-c', 'core.fsmonitor=false',
                             '-C', str(source), *args], env=git_env, capture_output=True,
                            text=True, check=True, timeout=30)
    return result.stdout.strip()
assert git_read('rev-parse', 'HEAD') == SOURCE_COMMIT, 'Wrong source commit.'
assert not git_read('status', '--porcelain', '--untracked-files=all'), 'Source must be clean.'
source_blobs={line.split('\t',1)[1]:line.split('\t',1)[0].split()[2] for line in git_read('ls-tree','-r','HEAD').splitlines() if '\t' in line}
git_path=os.environ.get('PATH','')
git_executables=['git']+[str(Path(value).resolve()) for value in [shutil.which('git')] if value]
system = {k: v for k, v in os.environ.items() if k.upper() in ('SYSTEMROOT', 'WINDIR')}
os.environ.clear()
os.environ.update(system)
os.environ.update(TEMP=str(temp), TMP=str(temp), TMPDIR=str(temp), HOME=str(user), USERPROFILE=str(user),
                  APPDATA=str(user / 'AppData/Roaming'), LOCALAPPDATA=str(user / 'AppData/Local'),
                  HERMES_HOME=str(root / 'hermes-home'), HERMES_RUNTIME_DIR=str(root / 'runtime'),
                  HERMES_DISABLE_LAZY_INSTALLS='1', PYTHONDONTWRITEBYTECODE='1', NO_PROXY='127.0.0.1,localhost')
os.environ['HERMES_TEST_ISOLATION'] = '1'
os.environ.update(PATH=git_path,GIT_CONFIG_NOSYSTEM='1',GIT_CONFIG_GLOBAL=os.devnull,GIT_TERMINAL_PROMPT='0',GIT_CEILING_DIRECTORIES=str(root))
sys.pycache_prefix=str(root/'pycache')
os.chdir(root)
sys.dont_write_bytecode = True
sys.path[:0] = [str(source), str(site)]
read_roots = [root, source, site, Path(sys.base_prefix).resolve(), Path(__file__).resolve()]
denied, denial_details, loaded, report = [], [], [], {}

def inside(path, roots):
    return any((path.is_relative_to(x) for x in roots))

def filepath(value):
    return Path(os.fsdecode(value)).resolve() if isinstance(value, (str, bytes, os.PathLike)) else None

def refuse(event, target=None):
    denied.append(event)
    frames = []
    frame = sys._getframe(1)
    for _ in range(12):
        if frame is None:
            break
        file = Path(frame.f_code.co_filename)
        frames.append(dict(module=frame.f_globals.get('__name__', 'unknown'), function=frame.f_code.co_name, line=frame.f_lineno, file=file.relative_to(source).as_posix() if file.is_absolute() and file.is_relative_to(source) else file.name))
        frame = frame.f_back
    if len(denial_details) < 64:
        denial_details.append(dict(event=event, targetName=target.name if target is not None else None, targetPathSha256=hashlib.sha256(str(target).encode()).hexdigest() if target is not None else None, frames=frames))
    raise PermissionError('Canonical fixture boundary: ' + event)

expected_blocked_version_probes = []
VERSION_PROBES = {
    ('git', 'rev-parse', 'HEAD'),
    ('git', 'branch', '--show-current'),
    ('git', 'log', '-1', '--format=%ct', 'HEAD'),
    ('git', '--no-optional-locks', 'status', '--porcelain', '-uno'),
    ('git', 'tag', '--merged', 'HEAD', '--list', 'v[0-9]*'),
    ('git', 'describe', '--tags', '--long', '--match', 'v2[0-9][0-9][0-9].*', 'HEAD'),
    ('git', 'rev-parse', '--short=7', 'HEAD'),
}
workspace_probes=[]
workspace_cwds=[root/'workspace'/'project-a',root/'workspace'/'project-b']
workspace_git={subprocess.list2cmdline(['git','-C',spelling,*tail])
    for directory in workspace_cwds for spelling in [str(directory),directory.as_posix()]
    for tail in [('branch','--show-current'),('rev-parse','--short','HEAD'),('rev-parse','--show-toplevel'),('rev-parse','--path-format=absolute','--git-common-dir')]}
def audit(event, args):
    if event == 'subprocess.Popen':
        argv, cwd = args[1], args[2]
        command=subprocess.list2cmdline(argv) if isinstance(argv,(list,tuple)) else argv
        metadata_frame=sys._getframe(1);metadata_function=None
        for _ in range(12):
            if metadata_frame is None:break
            module=metadata_frame.f_globals.get('__name__')
            if (module,metadata_frame.f_code.co_name) in {('hermes_cli.source_check','_git_run'),('hermes_cli._subprocess_compat','_user_safe_directories')}:
                metadata_function=(module,metadata_frame.f_code.co_name);break
            metadata_frame=metadata_frame.f_back
        # These exact harmless probes stay blocked, including their environment/config access.
        metadata_tails=[['config','--system','-z','--get-all','safe.directory'],['config','--global','-z','--get-all','safe.directory'],
            ['rev-parse','HEAD'],['rev-parse','--abbrev-ref','HEAD'],['remote','get-url','origin'],['status','--porcelain']]
        metadata_commands={subprocess.list2cmdline([exe,*tail]).lower() for exe in git_executables for tail in metadata_tails}
        if metadata_function is not None and command.lower() in metadata_commands:
            expected_blocked_version_probes.append(dict(module=metadata_function[0],command=command))
            raise PermissionError('Exact metadata probe intentionally blocked in fixture')
        if command in workspace_git:
            workspace_probes.append(command)
            return
        frame = sys._getframe(1)
        from_version_info = False
        for _ in range(10):
            if frame is None:
                break
            if frame.f_globals.get('__name__') == 'hermes_cli.version_info' and frame.f_code.co_name in ('_run_git', '_git_version_info'):
                from_version_info = True
            frame = frame.f_back
        version_probe = next((probe for probe in VERSION_PROBES if isinstance(argv, str) and argv == subprocess.list2cmdline(list(probe))), None)
        if isinstance(argv, (list, tuple)) and tuple(argv) in VERSION_PROBES:
            version_probe = tuple(argv)
        if version_probe is not None and cwd is not None and Path(cwd).resolve() == source and from_version_info:
            expected_blocked_version_probes.append(list(version_probe))
            raise PermissionError('Version metadata subprocess intentionally blocked in fixture')
    if event in ('subprocess.Popen', 'os.system', 'os.kill', 'os.killpg', 'os.exec', 'os.posix_spawn', 'sqlite3.enable_load_extension', 'sqlite3.load_extension', 'socket.sendto', 'socket.sendmsg', 'socket.gethostbyname', 'socket.gethostbyaddr'):
        refuse(event)
    if event == 'socket.getaddrinfo':
        try:
            allowed = ipaddress.ip_address(args[0]).is_loopback
        except ValueError:
            allowed = args[0] == 'localhost'
        if not allowed:
            refuse('nonloopback-resolution')
    if event in ('socket.bind', 'socket.connect'):
        address = args[1]
        if not isinstance(address, tuple) or len(address) < 2:
            refuse('unowned-socket-address')
        try:
            loopback = ipaddress.ip_address(address[0]).is_loopback
        except ValueError:
            loopback = False
        if not loopback:
            refuse('nonloopback-socket')
        if event == 'socket.bind':
            if address[1] != 0:
                refuse('fixed-port-bind')
            bound_sockets.add(args[0])
        else:
            ports = []
            for sock in tuple(bound_sockets):
                try:
                    ports.append(sock.getsockname()[1])
                except OSError:
                    pass
            if address[1] not in ports or address[1] == 0:
                refuse('connect-to-unowned-local-port')
        owned_socket_events.append(event)
    if event == 'open':
        path = filepath(args[0])
        mode = args[1] or ''
        flags = args[2] or 0
        writing = any((x in mode for x in 'wax+')) or bool(flags & (os.O_WRONLY | os.O_RDWR | os.O_CREAT | os.O_TRUNC | os.O_APPEND))
        if not writing and path in missing_os_probes.values():
            expected = next(value for value in missing_os_probes.values() if value==path)
            if path == expected and path.resolve() == expected and (not path.exists()):
                if len(missing_os_probe_reads) < 128:
                    missing_os_probe_reads.append(str(args[0]))
                return
        if path and str(args[0]).lower() != os.devnull.lower() and (not inside(path, [root] if writing else read_roots)):
            refuse('file-write' if writing else 'file-read', path)
    if event == 'os.chdir' and (not Path(args[0]).resolve().is_relative_to(root)):
        refuse('chdir-outside-fixture')
    if event in ('os.mkdir', 'os.remove', 'os.rmdir', 'os.rename', 'os.symlink', 'os.link'):
        values = args[:2] if event in ('os.rename', 'os.symlink', 'os.link') else args[:1]
        if any(((path := filepath(v)) is not None and (not path.is_relative_to(root)) for v in values)):
            refuse(event)
    if event == 'sqlite3.connect' and str(args[0]) != ':memory:':
        value = str(args[0])
        if value.startswith('file:'):
            uri = urllib.parse.urlsplit(value)
            if uri.netloc not in ('', 'localhost'):
                refuse('sqlite-uri-authority')
            value = urllib.parse.unquote(uri.path)
            if value.startswith('/') and len(value) > 2 and (value[2] == ':'):
                value = value[1:]
        if not Path(value).resolve().is_relative_to(root):
            refuse('sqlite-outside-fixture')
missing_os_probes = {}
assert os.name == 'nt'
for name in ('/proc/1/cgroup', '/proc/self/mountinfo', '/proc/version'):
    target = Path(root.drive + name.replace('/', '\\'))
    assert target.resolve() == target and (not target.exists())
    missing_os_probes[name] = target
missing_manifest = source.parent / 'manifest.json'
assert missing_manifest.resolve() == missing_manifest and (not missing_manifest.exists())
missing_os_probes[str(missing_manifest)] = missing_manifest
missing_os_probe_reads = []
bound_sockets = weakref.WeakSet()
owned_socket_events = []
cases = []
for fixture_pid in (os.getpid(),os.getppid()):
    name=f'/proc/{fixture_pid}/stat';target=Path(root.drive+name.replace('/','\\'))
    assert target.resolve()==target and not target.exists()
    missing_os_probes[name]=target
def observe_exit_boundary():
    (root/'exit-boundary.json').write_text(json.dumps(dict(deniedEvents=denied,denialDetails=denial_details,expectedBlockedMetadata=expected_blocked_version_probes),indent=2)+'\n')
atexit.register(observe_exit_boundary)
sys.addaudithook(audit)

try:
    import asyncio
    from types import SimpleNamespace
    class FixtureOverrides:
        def __init__(self):self.values=[]
        def setattr(self,target,name,value):self.values.append((target,name,getattr(target,name)));setattr(target,name,value)
        def undo(self):
            for target,name,value in reversed(self.values):setattr(target,name,value)
    from gateway.session_contract import Submission
    from gateway.session_controls import AuthorityConnection
    from gateway.session_policy import policy_for_source
    from hermes_state_local import local_receipt
    import hermes_state_runtime as rt
    def _owner(tmp_path, monkeypatch, handle):
        from gateway import run
        from gateway.config import GatewayConfig
        from gateway.session import SessionStore
        from gateway.session_authority import SessionAuthority
        from gateway.session_controls import AuthorityConnection
        from gateway.session_local import create_local_session
        import hermes_state_runtime as rt
        monkeypatch.setattr(run, '_load_gateway_config', lambda *a: {})
        store = SessionStore(tmp_path / 'sessions', GatewayConfig())
        db = store._db
        runner = SimpleNamespace(session_store=store, _session_db=db, adapters={}, _draining=False,
                                 _evict_cached_agent=lambda route: None, _cached_agent_for=lambda route: None,
                                 _set_session_reasoning_override=lambda route, value: None,
                                 _handle_message=handle, _resolve_session_agent_runtime=lambda **k: ('frozen', {}))
        runner._adapter_for_source = lambda source: runner.adapters.get(source.platform)
        authority = SessionAuthority(runner, profile_id='owned', instance_id='owner', db=db,
                                     epoch=rt.begin_runtime_epoch(db, instance_id='owner'))
        owner = AuthorityConnection(authority, object(), {'user_id': 'human'})
        start = tmp_path / 'project-a'
        start.mkdir()
        ref = create_local_session(authority, owner.actor, dict(request_id='m', source='gui', cwd=str(start),
                                                                model='frozen', toolsets=[], reasoning='low'))
        return store, db, authority, owner, ref

    def _mutation(db, ref, request_id, operation, payload):
        snap = db.get_session(ref.session_id)
        return {'id': request_id, 'method': 'session.mutate', 'params': {
            'session_id': ref.session_id, 'request_id': request_id, 'operation': operation, 'payload': payload,
            'expected_revision': snap['runtime_revision'], 'expected_generation': snap['runtime_generation']}}
    async def behavior():
        patch=FixtureOverrides();case=root/'workspace';case.mkdir()
        entered,release=asyncio.Event(),asyncio.Event()
        async def bounded_turn(event):
            entered.set();await release.wait();return 'fixture-only'
        store,db,authority,owner,ref=_owner(case,patch,bounded_turn)
        target=case/'project-b';target.mkdir()
        reader=AuthorityConnection(authority,object(),{'user_id':'human','capabilities':['session:read']})
        def passed(name):
            assert name not in cases;cases.append(name)
        def reason(response,expected):
            assert response['error']['data']['reason']==expected,response
        try:
            original=db.get_session(ref.session_id)
            denied=await reader.dispatch(_mutation(db,ref,'read-only','workspace',{'cwd':str(target)}))
            reason(denied,'permission_denied');passed('trusted_read_only_actor_cannot_change_workspace')
            queued=await authority.submit(owner.actor,Submission('bounded-fixture-turn',ref,{'text':'fixture only'},'queue'))
            await asyncio.wait_for(entered.wait(),10)
            reason(await owner.dispatch(_mutation(db,ref,'busy','workspace',{'cwd':str(target)})),'session_busy')
            assert db.get_session(ref.session_id)['cwd']==original['cwd'];passed('active_admission_blocks_without_changing_directory')
            release.set()
            async def async_wait_until(predicate,what):
                deadline=asyncio.get_running_loop().time()+10
                while not predicate():
                    assert asyncio.get_running_loop().time()<deadline,what
                    await asyncio.sleep(0.02)
            await async_wait_until(lambda:rt.get_session_admission(db,admission_id=queued.admission_id)['status']=='terminal',what='bounded callback settlement')
            before=db.get_session(ref.session_id)
            command=_mutation(db,ref,'reviewed-exact-workspace','workspace',{'cwd':str(target)})
            response=await owner.dispatch(command);assert 'result' in response,response
            ack=response['result'];after=db.get_session(ref.session_id)
            assert ack['session_id']==ref.session_id and ack['operation']=='workspace'
            assert ack['revision']==before['runtime_revision']+1 and ack['execution_generation']==before['runtime_generation']+1
            passed('real_workspace_ack_advances_both_exact_fences_once')
            saved=local_receipt(db,ref.session_id)['policy']
            assert saved['cwd']==json.loads(saved['terminal_json'])['TERMINAL_CWD']==after['cwd']==str(target)
            assert policy_for_source(authority.runner,authority.sessions[ref.session_id].source).cwd==str(target)
            passed('stored_policy_terminal_directory_live_policy_and_row_agree')
            info=await owner.dispatch({'jsonrpc':'2.0','id':'info','method':'session.info','params':{'session_id':ref.session_id}})
            assert info['result']['cwd']==str(target);passed('session_info_reports_actual_new_directory')
            repeated=await owner.dispatch(command);assert repeated['result']==ack
            assert db.get_session(ref.session_id)['runtime_revision']==after['runtime_revision'];passed('server_exact_receipt_replay_has_no_second_mutation')
            conflict={**command,'params':{**command['params'],'payload':{'cwd':str(case/'project-a')}}}
            reason(await owner.dispatch(conflict),'admission_conflict');passed('same_request_id_with_changed_directory_is_refused')
            stale={**command,'id':'stale','params':{**command['params'],'request_id':'stale-revision'}}
            reason(await owner.dispatch(stale),'revision_conflict');passed('stale_revision_cannot_change_workspace')
            stale_generation=_mutation(db,ref,'stale-generation','workspace',{'cwd':str(case/'project-a')})
            stale_generation['params']['expected_generation']=before['runtime_generation']
            reason(await owner.dispatch(stale_generation),'stale_generation');passed('stale_generation_cannot_change_workspace')
            reason(await owner.dispatch(_mutation(db,ref,'missing','workspace',{'cwd':str(case/'missing')})),'invalid_params')
            assert db.get_session(ref.session_id)['cwd']==str(target);passed('missing_host_directory_is_refused_without_policy_change')
            assert db.get_session(ref.session_id)['system_prompt']==original['system_prompt'];passed('workspace_does_not_rewrite_the_system_prompt')
        finally:
            release.set();await reader.close();await owner.close();store.close_all_db_handles();patch.undo()
    asyncio.run(behavior())
    for name,module in sorted(sys.modules.items()):
        file=getattr(module,'__file__',None)
        if file:
            path=Path(file).resolve()
            if path.is_relative_to(source) and path.is_file():
                data=path.read_bytes();relative=path.relative_to(source).as_posix()
                assert hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()==source_blobs[relative],relative
                loaded.append(dict(module=name,path=relative,sha256=hashlib.sha256(data).hexdigest()))
    assert not denied and len(cases)==11
    report=dict(state='PASS_REAL_WORKSPACE_MUTATION_COMPONENT',sourceCommit=SOURCE_COMMIT,cases=cases,modules=loaded,gitProbes=workspace_probes,expectedBlockedMetadata=expected_blocked_version_probes,missingProbes=missing_os_probe_reads,deniedEvents=denied,scope='Actual authority/dispatcher/SQLite/frozen policy with trusted synthetic actors and a bounded callback. No HTTP/native-auth, inference, tool, worker, Pi, model/default or deployed-fleet acceptance.')
except BaseException:
    report=dict(state='FAILED_COMPONENT_PRESERVED',passedBeforeFailure=cases,deniedEvents=denied,denialDetails=denial_details,traceback=traceback.format_exc())
    raise
finally:
    (root/'workspace-result.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(dict(state=report['state'],cases=len(cases),sourceModules=len(loaded),deniedEvents=denied)),flush=True)
