"""Fresh-home actual SDK GatewayRunner/admission/drain lifecycle qualification.

This calls the public gateway.run.start_gateway facade without SDK patches.
The controller supplies reviewed source/PM Python and a separate finite native
unit. All data/ledger identities are generated. It is not a cutover executor or
proof of all-profile, worker, transport, inference or production acceptance.
"""
from pathlib import Path
import asyncio
import base64
import hashlib
import json
import os
import re
import signal
import shutil
import socket
import stat
import subprocess
import sys
import threading
import time

SCRIPTS=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(SCRIPTS))
from native_gateway_control import NativeGatewayControl
from native_retirement_owner import read_process_owner


def write_new(path,value):
    with Path(path).open('x',encoding='utf-8') as out:
        json.dump(value,out,indent=2);out.write('\n');out.flush();os.fsync(out.fileno())


def fixture_environment(root,runtime):
    root,runtime=Path(root),Path(runtime)
    if not root.is_absolute() or not runtime.is_absolute():raise ValueError('Literal private fixture paths required')
    return dict(PATH='/usr/bin:/bin',LANG='C.UTF-8',HOME=str(root/'account'),HERMES_HOME=str(root/'hermes-home'),
        XDG_CONFIG_HOME=str(root/'config'),XDG_CACHE_HOME=str(root/'cache'),XDG_STATE_HOME=str(root/'state'),XDG_DATA_HOME=str(root/'data'),
        XDG_RUNTIME_DIR=str(runtime.parent),TMPDIR=str(root/'scratch'),TMP=str(root/'scratch'),TEMP=str(root/'scratch'),
        DBUS_SESSION_BUS_ADDRESS='unix:path='+str(runtime.parent/'bus'),
        HF_HOME=str(root/'cache/huggingface'),HERMES_GATEWAY_LOCK_DIR=str(root/'locks'),
        PYTHONDONTWRITEBYTECODE='1',PYTHONUNBUFFERED='1',GATEWAY_MULTIPLEX_PROFILES='false')


def fixture_bind_allowed(family,kind,address,runtime):
    if kind!=socket.SOCK_STREAM:return False
    unix=getattr(socket,'AF_UNIX',None)
    if unix is not None and family==unix:return fixture_unix_bind_allowed(address,runtime)
    return (family==socket.AF_INET and address==('127.0.0.1',0)) or (family==socket.AF_INET6 and address==('::1',0))


def fixture_unix_bind_allowed(address,runtime):
    return isinstance(address,str) and Path(address).is_absolute() and Path(address).resolve().is_relative_to(runtime)


def fixture_signal_probe_allowed(pid,signum,own_pid):
    return type(pid) is int and type(signum) is int and pid==own_pid and own_pid>1 and signum==0


def fixture_process_allowed(executable,argv,cwd,env,unit,diagnostic):
    if cwd is not None or env is not None or type(argv) is not list:return False
    if executable in ('systemctl','/usr/bin/systemctl'):
        return argv in (['systemctl','--user','show',unit,'--property=TimeoutStopUSec'],['systemctl','show',unit,'--property=TimeoutStopUSec'])
    return (bool(diagnostic) and executable==diagnostic[0] and argv==list(diagnostic))


def system_metadata():
    names=[Path('/etc/ssh/sshd_config'),*sorted(Path('/etc/ssh/sshd_config.d').glob('*.conf')),
        Path('/usr/bin/systemctl'),Path('/usr/bin/timeout'),Path('/usr/bin/bash')]
    result={}
    for p in names:
        if not p.exists():
            if p!=Path('/etc/ssh/sshd_config'):raise ValueError('Required native observation tool is missing')
            result[str(p)]=None;continue
        s=p.lstat()
        if p.resolve()!=p or not stat.S_ISREG(s.st_mode) or s.st_uid!=0 or s.st_mode&0o022 or s.st_size>4*1024*1024:
            raise ValueError('Exact protected native observation input required')
        try:content_sha=hashlib.sha256(p.read_bytes()).hexdigest()
        except PermissionError:
            if not p.is_relative_to('/etc/ssh'):raise
            content_sha=None
        result[str(p)]=dict(identity=[s.st_dev,s.st_ino,s.st_uid,s.st_mode,s.st_size,s.st_mtime_ns,s.st_ctime_ns],sha256=content_sha,kernel_read_denied=content_sha is None)
    return result


def duration_seconds(raw):
    if type(raw) is not str:raise ValueError('Exact native duration required')
    tokens=re.findall(r'([0-9]+)(min|s)',raw)
    if not tokens or ''.join(n+u for n,u in tokens)!=raw.replace(' ',''):raise ValueError('Unknown native duration')
    return sum(int(n)*(60 if u=='min' else 1) for n,u in tokens)


def checked_instantiation_epoch(boot,pid1_start):
    if (type(boot) is not str or not re.fullmatch('[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}',boot)
        or type(pid1_start) is not str or not re.fullmatch('0|[1-9][0-9]*',pid1_start) or int(pid1_start)>=2**64):
        raise ValueError('Exact native boot UUID and unsigned PID1 start ticks required')
    return boot+':'+pid1_start


def native_instantiation_epoch():
    return checked_instantiation_epoch(Path('/proc/sys/kernel/random/boot_id').read_text().strip(),Path('/proc/1/stat').read_text().rsplit(')',1)[1].split()[19])


def drain_is_settled(project,operation):
    if (type(project) is not dict or project.get('fence_owner')!=operation
        or type(project.get('fence_epoch')) is not int or project['fence_epoch']!=1
        or project.get('admission_frozen') is not True or type(project.get('busy')) is not bool
        or project.get('lifecycle') not in ('running','draining')
        or project.get('coverage')!='REGISTERED_WORK_ONLY' or project.get('fleet_ready_to_switch') is not False):
        raise RuntimeError('Original SDK admission freeze is unproven')
    return project['lifecycle']=='draining' and project['busy'] is False


def checked_observer_mode(mode):
    if type(mode) is not str or mode not in ('profile','none'):
        raise ValueError('An explicit reviewed observer mode is required')
    return mode


def run_gateway_facade(start_gateway,observer,mode):
    mode=checked_observer_mode(mode)
    if sys.getprofile() is not None or threading.getprofile() is not None:
        raise ValueError('The fixture cannot adopt or replace an existing profiler')
    if mode=='profile':
        sys.setprofile(observer);threading.setprofile(observer)
    try:
        result=asyncio.run(start_gateway())
        expected=observer if mode=='profile' else None
        if sys.getprofile() is not expected or threading.getprofile() is not expected:
            raise RuntimeError('SDK lifecycle observer state changed')
        return result
    finally:
        if mode=='profile':
            sys.setprofile(None);threading.setprofile(None)


def fixture_configuration():
    return dict(model=dict(provider='custom',default='fixture-no-inference',base_url='http://127.0.0.1:9/v1',api_key='fixture-no-inference'),
        fallback_model=[],local_runtime=dict(enabled=False),mcp_servers={},
        agent=dict(environment_probe=False,gateway_startup_warmup_timeout=0),
        project_control=dict(enabled=True,enrollment_mode='all'),gateway=dict(multiplex_profiles=False),
        memory=dict(memory_enabled=False,user_profile_enabled=False))


def checked_context(root,source):
    root,source=Path(root).resolve(),Path(source).resolve()
    if (sys.platform!='linux' or not sys.flags.isolated or not sys.dont_write_bytecode or os.geteuid()==0
        or not (root/'fixture-marker.json').is_file() or root.is_symlink() or source.is_symlink()):
        raise ValueError('Fresh private isolated SDK gateway fixture required')
    marker=json.loads((root/'fixture-marker.json').read_text(encoding='utf-8'))
    operation=marker['operation'];runtime=Path(marker['runtime']);checked_observer_mode(marker['observer_mode'])
    if (not re.fullmatch('[a-f0-9]{32}',operation) or marker['source']!=str(source)
        or marker['scope']!='isolated-normal-gateway' or marker['unit']!='signal-grove-acceptance-'+operation+'-gateway.service'
        or runtime!=Path('/run/user')/str(os.geteuid())/('hermes-gw-'+hashlib.sha256(os.path.normcase(str(root/'hermes-home')).encode()).hexdigest()[:16])
        or os.environ.get('HERMES_HOME')!=str(root/'hermes-home') or os.environ.get('XDG_RUNTIME_DIR')!=str(runtime.parent)
        or os.environ.get('HERMES_GATEWAY_LOCK_DIR')!=str(root/'locks')):
        raise ValueError('Exact generated home/runtime/operation boundary required')
    return root,source,runtime,marker


def install_boundary(root,source,runtime,marker):
    """Cooperative Python audit fence; source/native conservation is independent."""
    refusals=[]
    allowed=[root,source,SCRIPTS,Path(sys.prefix).resolve(),Path(sys.base_prefix).resolve(),Path('/usr'),Path('/lib'),Path('/lib64'),Path('/sys'),Path('/etc/ssl/certs')]
    exact={'/etc/passwd','/etc/group','/etc/nsswitch.conf','/etc/localtime','/etc/os-release','/etc/ld.so.cache','/etc/hosts','/etc/resolv.conf',
        '/etc/mime.types','/etc/protocols','/etc/services','/etc/host.conf','/etc/gai.conf','/dev/null','/dev/urandom',str(source.parent/'manifest.json'),str(source.parent/'tools/facts.json')}
    if system_metadata()!=marker['read_only_system_inputs']:raise ValueError('Reviewed native observation inputs changed')
    exact.update(marker['read_only_system_inputs']);allowed_processes={}
    def inside(value,base):return isinstance(value,(str,bytes,os.PathLike)) and Path(os.fsdecode(value)).resolve().is_relative_to(base)
    def deny(event,target=None):
        if len(refusals)<64:
            frame=sys._getframe(1);callers=[]
            try:
                for _ in range(6):
                    if frame is None:break
                    callers.append(dict(file=frame.f_code.co_filename,line=frame.f_lineno,function=frame.f_code.co_name));frame=frame.f_back
            finally:del frame
            refusals.append(dict(event=event,target=str(target)[:1024],callers=callers))
            (root/'fence-refusals.json').write_text(json.dumps(refusals,indent=2)+'\n',encoding='utf-8')
        raise PermissionError('Isolated actual-gateway fixture boundary refused an operation')
    def audit(event,args):
        if event=='open' and not isinstance(args[0],int):
            p=Path(os.fsdecode(args[0])).resolve();mode,flags=args[1:3]
            write=(isinstance(mode,str) and any(c in mode for c in 'wax+')) or (isinstance(flags,int) and flags&(os.O_WRONLY|os.O_RDWR|os.O_CREAT|os.O_TRUNC|os.O_APPEND))
            if write and p!=Path('/dev/null') and not inside(p,root) and not inside(p,runtime):deny('outside-write',p)
            if not write and not (str(p) in exact or p.is_relative_to('/proc') and (p.name!='environ' or p.parent.name==str(os.getpid())) or p.is_relative_to(runtime) or any(p.is_relative_to(r) for r in allowed)):deny('outside-read',p)
        if event=='sqlite3.connect':
            value=str(args[0])
            if value!=':memory:':
                from urllib.parse import unquote,urlsplit
                p=Path(unquote(urlsplit(value).path) if value.startswith('file:') else value)
                if not inside(p,root):deny('outside-sqlite',p)
        if event in ('os.mkdir','os.remove','os.rmdir','os.chmod','os.rename','os.link','os.symlink'):
            if event in ('os.rename','os.link'):values=[(args[0],args[2]),(args[1],args[3])]
            elif event in ('os.mkdir','os.chmod','os.symlink'):values=[(args[1] if event=='os.symlink' else args[0],args[2])]
            else:values=[(args[0],args[1])]
            for value,descriptor in values:
                if isinstance(value,int):value=os.readlink('/proc/self/fd/'+str(value))
                elif not Path(value).is_absolute() and descriptor not in (-1,None):value=Path(os.readlink('/proc/self/fd/'+str(descriptor)))/value
                if not inside(value,root) and not inside(value,runtime):deny('outside-mutation',value)
        if event=='socket.bind' and not fixture_bind_allowed(args[0].family,args[0].type,args[1],runtime):deny('foreign-listener',args[1])
        if event=='os.kill' and fixture_signal_probe_allowed(args[0],args[1],os.getpid()):return
        if event=='subprocess.Popen' and fixture_process_allowed(*args,marker['unit'],allowed_processes.get('diagnostic')):return
        if event in ('socket.connect','subprocess.Popen','os.system','os.kill'):deny(event,args[1] if len(args)>1 else args[0])
    sys.addaudithook(audit)
    return refusals,allowed_processes


def close_environment(root,runtime):
    expected=fixture_environment(root,runtime)
    for name in tuple(os.environ):
        if name not in expected and name not in ('INVOCATION_ID','JOURNAL_STREAM'):os.environ.pop(name)
    os.environ.update(expected)


def source_origins(source):
    return sorted({Path(m.__file__).resolve().relative_to(source).as_posix() for m in tuple(sys.modules.values())
        if getattr(m,'__file__',None) and Path(m.__file__).resolve().is_relative_to(source)})


def ledger_phase(root,source,phase):
    root,source,runtime,marker=checked_context(root,source);close_environment(root,runtime);os.umask(0o077);os.chdir(root)
    refusals,_=install_boundary(root,source,runtime,marker);sys.path.insert(0,str(source))
    from agent.project_control_store import Ledger
    path=root/'hermes-home/project-control.db';session='gateway-fixture-session'
    if phase=='seed':
        if path.exists():raise ValueError('Fresh generated ledger required')
        ledger=Ledger(path)
        try:
            ledger.register('gateway-fixture','revision-1');ledger.authorize_revision('gateway-fixture','revision-1','revision-2')
            ledger.bind_session(session,'gateway-fixture','revision-2');receipt=ledger.admit_turn(session);ledger.finish_turn(receipt)
            fence=ledger.fence_status();assert fence==dict(epoch=0,owner=None,frozen=False,active_turns=0,active_tools=0,processes_awaiting_reconciliation=0,unverified_operations=0)
            from gateway.restart import DEFAULT_GATEWAY_RESTART_DRAIN_TIMEOUT,DEFAULT_GATEWAY_CRON_DRAIN_TIMEOUT,resolve_systemd_timeout_stop_sec
            stop_seconds=resolve_systemd_timeout_stop_sec(DEFAULT_GATEWAY_RESTART_DRAIN_TIMEOUT,DEFAULT_GATEWAY_CRON_DRAIN_TIMEOUT)
            if type(stop_seconds) is not int or not 10<=stop_seconds<=900:raise ValueError('Review the supported SDK stop budget')
            report=dict(phase=phase,pid=os.getpid(),fence=fence,generated_turn_completed=True,stop_seconds=stop_seconds)
        finally:ledger.close()
    elif phase=='freeze':
        if not path.is_file() or path.is_symlink():raise ValueError('Existing generated ledger required')
        ledger=Ledger(path)
        try:
            assert ledger.fence_status()==dict(epoch=0,owner=None,frozen=False,active_turns=0,active_tools=0,processes_awaiting_reconciliation=0,unverified_operations=0)
            fence=ledger.freeze(marker['operation']);assert fence==dict(epoch=1,owner=marker['operation'],frozen=True,active_turns=0,active_tools=0,processes_awaiting_reconciliation=0,unverified_operations=0)
            denied=[]
            for kind,call in (('turn',lambda:ledger.admit_turn(session)),('tool',lambda:ledger.admit_tool(session=session))):
                try:call()
                except RuntimeError as error:
                    if str(error)!='New work is frozen by the mode coordinator':raise
                    denied.append(kind)
                else:raise AssertionError('Native SDK admission did not refuse frozen work')
            assert ledger.fence_status()==fence
        finally:ledger.close()
        from gateway.drain_control import current_instantiation_epoch,write_drain_request
        request=root/'hermes-home/.drain_request.json'
        if request.exists() or request.is_symlink():raise ValueError('Never adopt or refresh an existing fixture drain')
        epoch=current_instantiation_epoch()
        if epoch!=native_instantiation_epoch():raise ValueError('Strong native boot/PID1 epoch required')
        payload=write_drain_request(principal=marker['operation'],suppress_notification=True,home=root/'hermes-home')
        assert payload['epoch']==epoch==native_instantiation_epoch() and json.loads(request.read_text())==payload
        report=dict(phase=phase,pid=os.getpid(),fence=fence,refused=denied,marker=payload,sdk_marker_writer=True,production_exclusive_marker_accepted=False)
    else:raise ValueError('Unknown finite ledger phase')
    if refusals:raise AssertionError('SDK ledger phase had a boundary refusal')
    report.update(sdk_module_origins=source_origins(source),sdk_functions_patched=False,live_changes=False)
    write_new(root/(phase+'-result.json'),report)


def serve(root,source):
    root,source,runtime,marker=checked_context(root,source);close_environment(root,runtime);os.umask(0o077);os.chdir(root)
    cgroup=Path('/proc/self/cgroup').read_text().splitlines()
    if len(cgroup)!=1 or cgroup[0].split(':',2)[:2]!=['0',''] or Path(cgroup[0].split(':',2)[2]).name!=marker['unit']:
        raise ValueError('Gateway requires its own exact finite cgroup')
    refusals,allowed_processes=install_boundary(root,source,runtime,marker);sys.path.insert(0,str(source));trace=root/'lifecycle.jsonl'
    from gateway.shutdown_forensics import _async_diagnostic_script
    if shutil.which('timeout')!='/usr/bin/timeout':raise ValueError('Reviewed bounded diagnostic tool required')
    allowed_processes['diagnostic']=('/usr/bin/timeout','5','bash','-c',_async_diagnostic_script('SIGINT',os.getpid()))
    trace.touch(mode=0o600,exist_ok=False);count=0;diagnostic_count=0
    watched={('gateway/run_startup.py','_start_spawn_background_watchers'),('gateway/run_shutdown.py','_enter_external_drain'),
        ('gateway/run_runtime.py','publish_gateway_runtime_ready'),('gateway/run.py','_resolve_gateway_exit_verdict')}
    def profile(frame,event,arg):
        nonlocal count,diagnostic_count
        if event not in ('call','return') or not frame.f_code.co_filename.startswith(str(source)+'/'):return
        key=(frame.f_code.co_filename[len(str(source))+1:],frame.f_code.co_name)
        if key==('gateway/run_activity_status.py','snapshot') and event=='return' and type(arg) is dict and arg.get('busy') is True:
            diagnostic_count+=1
            if diagnostic_count<=8:
                from native.sdk_work_diagnostics import observe_work
                observation=observe_work(frame.f_locals['runner'],source,arg)
                encoded=json.dumps(observation)
                if len(encoded.encode())>512*1024:raise RuntimeError('Bounded work diagnostics exceeded their limit')
                with (root/'work-diagnostics.private.jsonl').open('a',encoding='utf-8') as out:
                    out.write(encoded+'\n');out.flush();os.fsync(out.fileno())
            return
        if key==('gateway/control_socket.py','handle_request_line') and event=='return' and type(arg) is bytes:
            request=frame.f_locals.get('request',{})
            if request.get('verb') in ('identify','status') and re.fullmatch('[a-f0-9]{32}',str(request.get('id',''))) and len(arg)<=512*1024:
                with (root/'wire-replies.private.jsonl').open('a',encoding='utf-8') as out:
                    out.write(json.dumps(dict(id=request['id'],verb=request['verb'],monotonic=time.monotonic(),bytes=len(arg),sha256=hashlib.sha256(arg).hexdigest(),raw=base64.b64encode(arg).decode()))+'\n');out.flush();os.fsync(out.fileno())
            return
        if key not in watched:return
        count+=1
        if count>512:raise RuntimeError('Finite lifecycle trace exceeded its bound')
        with trace.open('a',encoding='utf-8') as out:out.write(json.dumps(dict(event=event,module=key[0],function=key[1],monotonic=time.monotonic()))+'\n');out.flush();os.fsync(out.fileno())
        if key[1]=='publish_gateway_runtime_ready' and event=='return':write_new(root/'ready-source.json',source_origins(source))
    from gateway.run import start_gateway
    result=run_gateway_facade(start_gateway,profile,marker['observer_mode'])
    if result is not True or refusals:raise AssertionError('Normal SDK gateway lifecycle did not complete cleanly')
    write_new(root/'gateway-terminal.json',dict(pid=os.getpid(),result=result,cgroup=cgroup[0],
        sdk_module_origins=source_origins(source),fence_refusals=refusals,observer_mode=marker['observer_mode'],
        profiles_unset_at_entry_and_exit=marker['observer_mode']=='none',normal_gateway_facade=True,cli_main=False,sdk_functions_patched=False,live_changes=False))


def unit_snapshot(unit,owner=None,argv=None,stop_seconds=None):
    if not re.fullmatch(r'signal-grove-acceptance-[a-f0-9]{32}-gateway\.service',unit):raise ValueError('Exact owned gateway unit required')
    p=subprocess.run(['systemctl','--user','show',unit,'--property=LoadState,ActiveState,SubState,MainPID,ControlPID,NRestarts,ControlGroup,Type,KillMode,KillSignal,Restart,RuntimeMaxUSec,TimeoutStopUSec,Result,ExecMainCode,ExecMainStatus'],capture_output=True,timeout=8)
    values=dict(line.split('=',1) for line in p.stdout.decode().splitlines() if '=' in line)
    if values.get('LoadState')=='not-found':return values
    if values.get('NRestarts')!='0' or values.get('ControlPID')!='0':raise RuntimeError('Fixture unit changed or restarted')
    if owner is None and values.get('ActiveState')=='activating':return values
    if int(values.get('MainPID','0')):
        current=read_process_owner(int(values['MainPID']))
        if owner is not None and current!=owner:raise RuntimeError('Original gateway incarnation changed')
        if current is None:raise RuntimeError('Native fixture process is unproven')
        if values['ActiveState']!='active' or values['SubState']!='running' or Path(values['ControlGroup']).name!=unit:raise RuntimeError('Native gateway unit is not running')
        if (values['Type'],values['KillMode'],values['KillSignal'],values['Restart'],values['RuntimeMaxUSec'])!=('exec','control-group','2','no','3min'):raise RuntimeError('Finite native gateway configuration differs')
        if owner is not None and duration_seconds(values['TimeoutStopUSec'])!=stop_seconds:raise RuntimeError('Supported native stop budget differs')
        if argv is not None:
            actual=Path('/proc')/str(current.pid)
            if actual.joinpath('cmdline').read_bytes().split(b'\0')[:-1]!=[os.fsencode(x) for x in argv] or actual.joinpath('exe').resolve()!=Path(argv[0]).resolve():raise RuntimeError('Exact reviewed gateway argv/image differs')
            if actual.joinpath('cgroup').read_text().strip()!='0::'+values['ControlGroup'] or read_process_owner(current.pid)!=current:raise RuntimeError('Native gateway group/incarnation changed')
    return values


def run_trial(root,source,python,*,observer_mode='profile'):
    observer_mode=checked_observer_mode(observer_mode)
    root,source=Path(root).resolve(),Path(source).resolve();python=Path(python).absolute();os.umask(0o077)
    if sys.platform!='linux' or os.geteuid()==0 or root.exists() or not source.is_dir() or not python.is_file():raise ValueError('Fresh actual SDK gateway trial required')
    root.mkdir(mode=0o700);operation=os.urandom(16).hex();runtime=Path('/run/user')/str(os.geteuid())/('hermes-gw-'+hashlib.sha256(os.path.normcase(str(root/'hermes-home')).encode()).hexdigest()[:16])
    if runtime.exists():raise ValueError('Fresh private runtime root required')
    unit='signal-grove-acceptance-'+operation+'-gateway.service'
    write_new(root/'fixture-operation.json',dict(operation=operation,unit=unit,runtime=str(runtime),source=str(source),python=str(python),phase='before_runtime_and_system_observation'))
    runtime.mkdir(mode=0o700)
    for n in ('account','config','cache','state','data','scratch','locks','hermes-home'):(root/n).mkdir(mode=0o700)
    marker=dict(scope='isolated-normal-gateway',observer_mode=observer_mode,operation=operation,runtime=str(runtime),source=str(source),unit=unit,read_only_system_inputs=system_metadata())
    write_new(root/'fixture-marker.json',marker);write_new(root/'hermes-home/config.yaml',fixture_configuration())
    env=fixture_environment(root,runtime);script=Path(__file__).resolve();record=dict(state='NORMAL_GATEWAY_TRIAL_INTENT',observer_mode=observer_mode,operation=operation,unit=unit,root=str(root),source=str(source),python=str(python),started=time.time(),samples=[],live_changes=False)
    def save():
        with (root/'trial.json').open('w',encoding='utf-8') as out:json.dump(record,out,indent=2);out.write('\n');out.flush();os.fsync(out.fileno())
    def ledger(phase):
        command=[str(python),'-I','-B',str(script),phase,str(root),str(source)]
        write_new(root/(phase+'-intent.json'),dict(argv=command,phase=phase))
        with (root/(phase+'.stdout')).open('xb') as out,(root/(phase+'.stderr')).open('xb') as err:
            process=subprocess.Popen(command,env=env,cwd=root,stdin=subprocess.DEVNULL,stdout=out,stderr=err);record[phase+'_pid']=process.pid;save();process.wait(timeout=30)
        if process.returncode!=0:raise RuntimeError('Original SDK ledger phase failed; no redispatch')
        return json.loads((root/(phase+'-result.json')).read_text())
    save();record['seed']=ledger('seed');stop_seconds=record['seed']['stop_seconds'];save()
    if unit_snapshot(unit)['LoadState']!='not-found':raise ValueError('Never adopt an existing native unit')
    argv=[str(python),'-I','-B',str(script),'serve',str(root),str(source)]
    command=['systemd-run','--user','--quiet','--wait','--pipe','--unit='+unit,'--property=Type=exec','--property=RuntimeMaxSec=180','--property=TimeoutStopSec='+str(stop_seconds),
        '--property=KillMode=control-group','--property=KillSignal=SIGINT','--property=Restart=no','--property=UMask=0077','--property=MemoryMax=1G','--property=TasksMax=128','--working-directory='+str(root)]
    command+=['--setenv='+k+'='+v for k,v in env.items()]+argv
    record.update(state='NORMAL_GATEWAY_DISPATCH_INTENT',argv=argv,command=command);save();owner=None;launcher=None;stopping=False
    try:
        with (root/'gateway.stdout').open('xb') as out,(root/'gateway.stderr').open('xb') as err:
            launcher=subprocess.Popen(command,stdin=subprocess.DEVNULL,stdout=out,stderr=err);record['launcher_pid']=launcher.pid;save()
        deadline=time.monotonic()+90
        while owner is None:
            native=unit_snapshot(unit)
            if native.get('ActiveState')=='active' and native.get('SubState')=='running' and int(native.get('MainPID','0')):owner=read_process_owner(int(native['MainPID']))
            if launcher.poll() is not None or time.monotonic()>=deadline:raise RuntimeError('Original gateway failed to start; inspect without retry')
            if owner is None:time.sleep(.05)
        record['owner']=dict(pid=owner.pid,start_ticks=owner.start_ticks,boot_id=owner.boot_id);record['native_start']=unit_snapshot(unit,owner,argv,stop_seconds);save()
        pointer=root/'hermes-home/gateway.sock.path'
        while not pointer.is_file():
            unit_snapshot(unit,owner,argv,stop_seconds)
            if launcher.poll() is not None or time.monotonic()>=deadline:raise RuntimeError('Original gateway socket never became available')
            time.sleep(.05)
        address=pointer.read_text().strip()
        if not Path(address).resolve().is_relative_to(runtime) or len(os.fsencode(address))>103:raise RuntimeError('Gateway escaped its exact private runtime')
        record['socket']=address;save()
        while observer_mode=='profile' and not (root/'ready-source.json').is_file():
            unit_snapshot(unit,owner,argv,stop_seconds)
            if launcher.poll() is not None or time.monotonic()>=deadline:raise RuntimeError('Actual SDK did not publish readiness')
            time.sleep(.05)
        def sample(verb):
            unit_snapshot(unit,owner,argv,stop_seconds);sock=socket.socket(socket.AF_UNIX,socket.SOCK_STREAM);sock.settimeout(2);sock.connect(address)
            control=NativeGatewayControl(owner,sock);started=time.time();item=dict(verb=verb,earliest=started,state='intent');record['samples'].append(item);save()
            try:body=control.exchange(verb)
            except BaseException as error:
                item.update(state=control.state,events=control.events,error_type=type(error).__name__,latest=time.time());save();raise
            finished=time.time();item.update(latest=finished,body=body,events=control.events,state=control.state)
            if verb=='status':
                from native_gateway_control import project_registered_status
                item['projection']=project_registered_status(body,owner.pid,hashlib.sha256(os.path.normcase(str(root/'hermes-home')).encode()).hexdigest(),started,finished)
            save();unit_snapshot(unit,owner,argv,stop_seconds);return item
        while True:
            identity=sample('identify')['body']
            if identity.get('state')=='ready':break
            if identity.get('state')!='starting' or time.monotonic()>=deadline:raise RuntimeError('Normal gateway readiness is unproven')
            time.sleep(.2)
        before=sample('status')['projection']
        if before['busy'] or before['lifecycle']!='running' or before['fence_epoch']!=0 or before['fence_owner'] is not None or before['admission_frozen']:raise RuntimeError('Initial actual gateway/ledger was not idle and unfrozen')
        record['freeze']=ledger('freeze');save();drain_deadline=time.monotonic()+5
        while True:
            status=sample('status')['projection']
            settled=drain_is_settled(status,operation)
            if time.monotonic()>=drain_deadline:raise RuntimeError('Actual GatewayRunner drain did not settle within its observation window')
            if settled:break
            time.sleep(.2)
        record['native_before_stop']=unit_snapshot(unit,owner,argv,stop_seconds);record['state']='NORMAL_GATEWAY_DRAIN_OBSERVED';save();stopping=True
        stop=subprocess.run(['systemctl','--user','stop',unit],stdin=subprocess.DEVNULL,capture_output=True,timeout=stop_seconds+15)
        write_new(root/'stop-result.json',dict(exit_code=stop.returncode,stdout=stop.stdout.decode(),stderr=stop.stderr.decode(),owner=record['owner']))
        if stop.returncode!=0:raise RuntimeError('Original fixture stop job failed; do not retry')
        launcher.wait(timeout=10);terminal=unit_snapshot(unit)
        if (launcher.returncode!=0 or terminal.get('MainPID')!='0' or terminal.get('ControlPID')!='0' or terminal.get('NRestarts')!='0'
            or terminal.get('ControlGroup')!='' or terminal.get('ActiveState') not in ('inactive','failed') or read_process_owner(owner.pid)==owner):raise RuntimeError('Native gateway termination is unproven')
        result=json.loads((root/'gateway-terminal.json').read_text());assert result['result'] and result['pid']==owner.pid and not result['fence_refusals'] and result['normal_gateway_facade'] and not result['sdk_functions_patched']
        events=[json.loads(line) for line in (root/'lifecycle.jsonl').read_text().splitlines()]
        if observer_mode=='profile' and not any(e['function']=='_enter_external_drain' and e['event']=='call' for e in events):raise RuntimeError('Actual external-drain callback was not observed')
        if observer_mode=='none' and (events or (root/'ready-source.json').exists() or (root/'wire-replies.private.jsonl').exists() or (root/'work-diagnostics.private.jsonl').exists()):raise RuntimeError('Profiler-free comparison unexpectedly produced profiler evidence')
        if (root/'fence-refusals.json').exists():raise RuntimeError('Boundary refusal retained; qualification held')
        if system_metadata()!=marker['read_only_system_inputs']:raise RuntimeError('Native observation inputs changed')
        record.update(state='ACTUAL_NORMAL_GATEWAY_AND_GENERATED_ADMISSION_DRAIN_VERIFIED',native_terminal=terminal,owner_ended=True,launcher_exit_code=launcher.returncode,
            gateway_result=result,lifecycle_events=len(events),normal_gateway_facade=True,cli_main=False,sdk_functions_patched=False,
            production_journal_actor_accepted=False,all_profile_worker_admission_accepted=False,live_changes=False,finished=time.time());save();return record
    except BaseException as error:
        record.update(state='NORMAL_GATEWAY_FAILED_OR_UNKNOWN',error_type=type(error).__name__);save();raise
    finally:
        # Only the exact owned fixture may be stopped on failed qualification.
        # Persistent ledger/marker/data are never cancelled, thawed or removed.
        if owner is not None and not stopping and read_process_owner(owner.pid)==owner:
            unit_snapshot(unit,owner,argv,stop_seconds);record['failure_cleanup_stop_intent']=True;save()
            cleanup=subprocess.run(['systemctl','--user','stop',unit],stdin=subprocess.DEVNULL,capture_output=True,timeout=stop_seconds+15)
            record['failure_cleanup_stop_exit']=cleanup.returncode;save()


if __name__=='__main__':
    if len(sys.argv)!=4 or sys.argv[1] not in ('seed','freeze','serve'):raise SystemExit('Exact isolated phase/home/source required')
    (serve if sys.argv[1]=='serve' else lambda root,source:ledger_phase(root,source,sys.argv[1]))(Path(sys.argv[2]),Path(sys.argv[3]))
