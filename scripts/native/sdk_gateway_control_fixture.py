"""Real SDK Unix listener/status functions with explicit synthetic owner/ledger.

This qualifies control framing and registered-work projection. It does not boot
GatewayRunner, engage a drain, freeze a real ledger or establish fleet readiness.
"""
from pathlib import Path
import asyncio
import hashlib
import json
import os
import socket
import sqlite3
import sys
import time
import subprocess
from types import SimpleNamespace


def run(home,source,mode):
    home,source=Path(home).resolve(),Path(source).resolve()
    if (sys.platform!='linux' or not sys.flags.isolated or not sys.dont_write_bytecode or mode not in ('idle','busy','unknown','unenrolled')
        or not (home/'fixture-marker.json').is_file() or os.environ['HERMES_HOME']!=str(home)):
        raise ValueError('Exact fresh private SDK observation fixture required')
    os.umask(0o077);runtime=Path('/run/user')/str(os.geteuid())/('hermes-gw-'+hashlib.sha256(os.path.normcase(str(home)).encode()).hexdigest()[:16])
    if runtime.exists():raise ValueError('Fresh owned control socket directory required')
    def inside(value,root):return isinstance(value,(str,bytes,os.PathLike)) and Path(os.fsdecode(value)).resolve().is_relative_to(root)
    refusals=[];allowed=[home,source,Path(sys.prefix).resolve(),Path(sys.base_prefix).resolve(),Path('/usr'),Path('/lib'),Path('/lib64'),Path('/sys'),Path('/etc/ssl/certs')]
    exact={'/etc/passwd','/etc/group','/etc/nsswitch.conf','/etc/localtime','/etc/os-release','/etc/ld.so.cache','/etc/hosts','/etc/resolv.conf','/etc/mime.types','/etc/protocols','/etc/services','/etc/host.conf','/etc/gai.conf','/dev/null','/dev/urandom',str(source.parent/'manifest.json'),str(source.parent/'tools/facts.json')}
    def deny(event):
        refusals.append(event)
        with (home/'fence-refusals.json').open('w') as out:json.dump(refusals,out)
        raise PermissionError('SDK control fixture boundary refused an operation')
    def audit(event,args):
        if event=='open' and not isinstance(args[0],int):
            p=Path(os.fsdecode(args[0])).resolve();mode,flags=args[1:3];write=(isinstance(mode,str) and any(c in mode for c in 'wax+')) or (isinstance(flags,int) and flags&(os.O_WRONLY|os.O_RDWR|os.O_CREAT|os.O_TRUNC|os.O_APPEND))
            if write and not p.is_relative_to(home) and not p.is_relative_to(runtime):deny('outside-write')
            if not write and not (str(p) in exact or p.is_relative_to('/proc') and (p.name!='environ' or p.parent.name==str(os.getpid())) or p.is_relative_to(runtime) or any(p.is_relative_to(r) for r in allowed)):deny('outside-read')
        if event in ('os.mkdir','os.remove','os.rmdir','os.chmod','os.rename','os.link','os.symlink'):
            if event in ('os.rename','os.link'):values=[(args[0],args[2]),(args[1],args[3])]
            elif event in ('os.mkdir','os.chmod','os.symlink'):values=[(args[1] if event=='os.symlink' else args[0],args[2])]
            else:values=[(args[0],args[1])]
            for value,descriptor in values:
                if isinstance(value,int):value=os.readlink('/proc/self/fd/'+str(value))
                elif not Path(value).is_absolute() and descriptor not in (-1,None):value=Path(os.readlink('/proc/self/fd/'+str(descriptor)))/value
                if not inside(value,home) and not inside(value,runtime):deny('outside-mutation')
        if event=='socket.bind' and (args[0].family!=socket.AF_UNIX or str(args[1])!=str(runtime/'control.sock')):deny('foreign-listener')
        if event in ('socket.connect','subprocess.Popen','os.system','os.kill'):deny(event)
    sys.addaudithook(audit);sys.path.insert(0,str(source))
    from gateway.control_socket import GatewayControlServer,resolve_server_socket_path
    from gateway.run_activity_status import status_handler
    from gateway.task_work import OwnedTaskSet
    bind,pointer=resolve_server_socket_path(home)
    if bind!=runtime/'control.sock' or pointer!=home/'gateway.sock.path':raise ValueError('Exact SDK private socket fallback required')
    class Counter:
        def active_count(self):return 0
    class UnavailableCounter:
        def active_count(self):raise RuntimeError('Synthetic unavailable work owner')
    runner=SimpleNamespace(_running=True,_draining=False,_external_drain_active=True,_sessions={},_profile_adapters={},adapters={},
        _background_tasks=OwnedTaskSet(),_executor_work=Counter(),_thread_work=Counter(),_periodic_work=Counter(),_deferred_agent_cleanup_tasks=set())
    if mode=='busy':runner._sessions['fixture']=SimpleNamespace(turn=SimpleNamespace(agent=object()),conversation=SimpleNamespace(queued_events=[]),persistent=SimpleNamespace(pending_command_text=None))
    if mode=='unknown':runner._periodic_work=UnavailableCounter()
    async def main():
        handler=status_handler(runner,asyncio.get_running_loop(),home)
        # A normal runner has imported its work owners before serving controls.
        # Retain the synthetic fixture's cold import cost outside the wire deadline.
        started=time.monotonic();warm=handler()
        (home/'cold-status.json').write_text(json.dumps(dict(seconds=time.monotonic()-started,body=warm)),encoding='utf-8')
        server=GatewayControlServer(home,verb_handlers={'status':handler})
        if not await server.start():raise RuntimeError('Actual SDK control listener failed')
        with (home/'ready.tmp').open('x') as out:json.dump(dict(pid=os.getpid(),socket=str(bind),pointer=str(pointer),mode=mode,synthetic_runner=True,generated_ledger=True,normal_gateway_cli=False,drain_engaged=False),out);out.flush();os.fsync(out.fileno())
        os.replace(home/'ready.tmp',home/'ready.json')
        try:
            deadline=time.monotonic()+90
            while not (home/'stop-request.json').exists() and time.monotonic()<deadline:await asyncio.sleep(.02)
            if not (home/'stop-request.json').exists():raise TimeoutError('Owned control fixture lifetime elapsed')
        finally:await server.stop()
    asyncio.run(main())
    if refusals:raise AssertionError('Actual SDK control fixture had a fence refusal')
    origins=sorted({str(Path(module.__file__).resolve().relative_to(source)) for module in tuple(sys.modules.values()) if getattr(module,'__file__',None) and Path(module.__file__).resolve().is_relative_to(source)})
    with (home/'terminal.json').open('x') as out:json.dump(dict(pid=os.getpid(),stopped=True,mode=mode,fence_refusals=[],sdk_functions_patched=False,normal_gateway_cli=False,drain_engaged=False,live_changes=False,sdk_module_origins=origins),out)


def prepare_home(home,mode):
    home=Path(home);home.mkdir(mode=0o700)
    config=dict(model=dict(provider='custom',default='fixture-no-inference',base_url='http://127.0.0.1:9/v1',api_key='fixture-no-inference'),fallback_model=[],local_runtime=dict(enabled=False),project_control=dict(enabled=mode!='unenrolled'))
    (home/'config.yaml').write_text(json.dumps(config),encoding='utf-8');(home/'fixture-marker.json').write_text('{"scope":"isolated-sdk-control-only"}\n',encoding='utf-8')
    with sqlite3.connect(home/'project-control.db') as db:
        db.execute('CREATE TABLE admission_fence(singleton INTEGER PRIMARY KEY,epoch INTEGER,owner TEXT,frozen INTEGER)');db.execute("INSERT INTO admission_fence VALUES(1,1,'fixture-owner',1)")
        db.execute('CREATE TABLE fence_transactions(owner TEXT,epoch INTEGER)')
        for table in ('turn_admissions','tool_admissions','operations'):db.execute('CREATE TABLE '+table+'(phase TEXT)')
        db.execute('CREATE TABLE process_links(state TEXT)');db.commit()


def run_trial(root,source,python):
    """Four fresh SDK peers. The caller supplies a separately verified PM Python.

    These are owned fixture children, not manager-selected production gateways.
    Failure is retained; there is no automatic restart, reattachment or replay.
    """
    root,source=Path(root).resolve(),Path(source).resolve();root.mkdir(mode=0o700)
    if sys.platform!='linux' or os.geteuid()==0 or not Path(python).is_absolute():raise ValueError('Native isolated trial required')
    sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
    from native_retirement_owner import read_process_owner
    from native_gateway_control import NativeGatewayControl,project_registered_status,Unknown
    results={}
    for mode in ('idle','busy','unknown','unenrolled'):
        home=root/mode;prepare_home(home,mode)
        for name in ('account','scratch','cache','config','state','data'):(home/name).mkdir(mode=0o700)
        env=dict(PATH='/usr/bin:/bin',LANG='C.UTF-8',HOME=str(home/'account'),HERMES_HOME=str(home),XDG_RUNTIME_DIR='/run/user/'+str(os.geteuid()),
            XDG_CACHE_HOME=str(home/'cache'),XDG_CONFIG_HOME=str(home/'config'),XDG_STATE_HOME=str(home/'state'),XDG_DATA_HOME=str(home/'data'),
            TMPDIR=str(home/'scratch'),TMP=str(home/'scratch'),TEMP=str(home/'scratch'),HF_HOME=str(home/'cache/huggingface'),PYTHONDONTWRITEBYTECODE='1')
        command=[str(python),'-I','-B',str(Path(__file__).resolve()),str(home),str(source),mode]
        intent=dict(mode=mode,argv=command,home=str(home),source=str(source),env_keys=sorted(env),normal_gateway_cli=False,synthetic_runner=True,generated_ledger=True,drain_engaged=False)
        (home/'intent.json').write_text(json.dumps(intent,indent=2)+'\n',encoding='utf-8')
        with (home/'stdout.log').open('xb') as out,(home/'stderr.log').open('xb') as err:child=subprocess.Popen(command,env=env,cwd=home,stdin=subprocess.DEVNULL,stdout=out,stderr=err)
        row=dict(pid=child.pid,mode=mode,requests=[]);results[mode]=row;owner=None
        try:
            deadline=time.monotonic()+25
            while not (home/'ready.json').exists() and child.poll() is None and time.monotonic()<deadline:time.sleep(.02)
            if not (home/'ready.json').is_file():raise RuntimeError('SDK control fixture did not become ready')
            ready=json.loads((home/'ready.json').read_text(encoding='utf-8'));owner=read_process_owner(child.pid)
            if owner is None or ready['pid']!=owner.pid:raise RuntimeError('Original fixture owner unavailable')
            row['owner']=dict(pid=owner.pid,start_ticks=owner.start_ticks,boot_id=owner.boot_id)
            expected=Path('/run/user')/str(os.geteuid())/('hermes-gw-'+hashlib.sha256(os.path.normcase(str(home)).encode()).hexdigest()[:16])/'control.sock'
            pointer=home/'gateway.sock.path'
            if ready['socket']!=str(expected) or ready['pointer']!=str(pointer) or pointer.read_text(encoding='utf-8')!=str(expected):raise RuntimeError('Exact SDK socket pointer unproven')
            row['socket']=str(expected);row['pointer']=str(pointer)
            for verb in ('identify','status'):
                sock=socket.socket(socket.AF_UNIX,socket.SOCK_STREAM)
                sample=dict(verb=verb);row['requests'].append(sample)
                try:
                    sock.settimeout(2);sock.connect(str(expected));control=NativeGatewayControl(owner,sock);started=time.time()
                    try:body=control.exchange(verb)
                    finally:sample.update(earliest=started,latest=time.time(),events=control.events,state=control.state)
                    finished=sample['latest']
                finally:sock.close()
                sample['body']=body
                if verb=='identify':
                    if body['protocol']!=1 or body['pid']!=owner.pid or body['hermes_home']!=str(home):raise RuntimeError('Real SDK identify scope differs')
                else:
                    try:sample['projection']=project_registered_status(body,owner.pid,hashlib.sha256(os.path.normcase(str(home)).encode()).hexdigest(),started,finished)
                    except Unknown:sample['projection_unknown']=True
                    if mode in ('idle','busy'):
                        projected=sample['projection']
                        if projected['busy']!=(mode=='busy') or projected['fleet_ready_to_switch'] is not False or projected['admission_frozen'] is not True:raise AssertionError('Real registered scope differs')
                    elif sample.get('projection_unknown') is not True:raise AssertionError('Unknown source incorrectly became idle')
            row['state']='REAL_SDK_CONTROL_OBSERVED'
        finally:
            (home/'samples.json').write_text(json.dumps(row,indent=2)+'\n',encoding='utf-8')
            if child.poll() is None:
                with (home/'stop-request.json').open('x') as out:json.dump(dict(pid=child.pid,fixture_only=True),out)
                child.wait(timeout=12)
            row['exit_code']=child.returncode;row['owner_ended']=owner is not None and read_process_owner(child.pid)!=owner
            (home/'samples.json').write_text(json.dumps(row,indent=2)+'\n',encoding='utf-8')
        if child.returncode!=0 or not row['owner_ended']:raise RuntimeError('Original SDK control fixture did not stop cleanly')
        terminal=json.loads((home/'terminal.json').read_text(encoding='utf-8'))
        if not terminal['stopped'] or terminal['fence_refusals'] or Path(row['socket']).exists() or Path(row['pointer']).exists():raise AssertionError('SDK fixture cleanup unproven')
    return dict(state='REAL_SDK_UNIX_CONTROL_AND_REGISTERED_PROJECTION_VERIFIED',modes=results,normal_gateway_cli=False,drain_engaged=False,generated_ledger=True,synthetic_runner=True,fleet_ready_to_switch=False,live_changes=False)


if __name__=='__main__':run(*sys.argv[1:])
