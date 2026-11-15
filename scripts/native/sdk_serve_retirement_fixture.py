"""Normal SDK/native-role retirement component with generated provider state.

The caller supplies exact isolated candidate/controller/path identities. All
non-retirement prerequisites are synthetic; no live service/home, model or real
credential is used. The SDK server and its audit/lifespan observer are unchanged.
"""
from dataclasses import asdict
from pathlib import Path
import base64
import hashlib
import http.cookies
import json
import os
import re
import secrets
import signal
import socket
import subprocess
import sys
import threading
import time
import traceback
import urllib.parse

from cutover_coordinator import Plan,ApprovedExecution,FORWARD
from cutover_journal import CutoverJournal,Refused
from native_file_publication import anchor,digest
from native_operational_paths import OperationalDirectories,NativeOperationalPaths,ROLES
from native_service_manager import NativeServiceManager,UnitBinding
from native_serve_retirement import NativeServeRetirement,ServeRetirementSpec,EXECUTOR_REQUIRED
from native_retirement_owner import LinuxSocketOwner,read_process_owner
from pinned_retirement_http import PinnedRetirementHttp,_ExistingConnection
from backup_sdk_snapshot import SourceInventory,observe_code
from rendered_publication_catalog import capture_native
from native.sdk_provider_fixture import child_environment,callback_code


def _write(path,value):
    with path.open('x',encoding='utf-8') as out:
        json.dump(value,out,indent=2);out.write('\n');out.flush();os.fsync(out.fileno())


def _role_configuration(unit,argv):
    return digest(dict(unit=unit,fragment='/run/user/'+str(os.geteuid())+'/systemd/transient/'+unit,
        dropins=[],kill_mode='control-group',service_type='exec',
        commands={'ExecStart':((argv[0],tuple(argv),False),),'ExecStop':(),'ExecStopPost':()}))


# This provider flow follows the existing sdk_provider_fixture at this source.
# It uses generated credentials and transfers the same socket to the actor.
def _authenticate(home,owner,port,password,record,save,secrets_seen):
    sock=None
    try:
        sock = socket.create_connection(('127.0.0.1', port), timeout=5)
        native = LinuxSocketOwner(owner)
        deadline = time.monotonic() + 2
        while native(sock) is None and time.monotonic() < deadline:
            time.sleep(.01)
        assert native(sock) == owner
        connection = _ExistingConnection('127.0.0.1', port, timeout=5)
        connection.sock = sock

        def exchange(method, route, body=None, headers=None):
            assert native(sock) == owner and connection.sock is sock
            timer = threading.Timer(5, lambda: sock.shutdown(socket.SHUT_RDWR))
            timer.daemon = True
            timer.start()
            try:
                connection.request(method, route, body=None if body is None else json.dumps(body).encode(),
                                   headers={'Content-Type': 'application/json', 'Accept': 'application/json', **(headers or {})})
                response = connection.getresponse()
                assert response.version == 11 and not response.will_close and not response.chunked
                lengths = response.headers.get_all('Content-Length', [])
                assert len(lengths) == 1 and lengths[0].isdigit() and int(lengths[0]) <= 65536
                raw = response.read(65537)
                assert len(raw) == int(lengths[0]) and native(sock) == owner
                status, response_headers = response.status, list(response.getheaders())
                response.close()
                record['requests'].append(dict(method=method, route=route.split('?', 1)[0], status=status, authenticated='Authorization' in (headers or {})))
                save()
                return status, response_headers, json.loads(raw) if raw else None
            finally:
                timer.cancel()
                timer.join()

        status, _, health = exchange('GET', '/api/health')
        assert status == 200 and health['auth_required'] is True
        assert exchange('GET', '/api/host/identity')[0] == 401
        verifier, state = secrets.token_urlsafe(48), secrets.token_urlsafe(24)
        challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip('=')
        secrets_seen += [verifier]
        with socket.socket() as callback:
            callback.bind(('127.0.0.1', 0))
            callback.listen(1)
            uri = 'http://127.0.0.1:' + str(callback.getsockname()[1]) + '/complete'
            route = '/auth/native/authorize?' + urllib.parse.urlencode(dict(provider='basic', code_challenge=challenge, code_challenge_method='S256', redirect_uri=uri, state=state))
            status, headers, _ = exchange('GET', route)
            assert status == 302
            cookies = http.cookies.SimpleCookie()
            for key, value in headers:
                if key.lower() == 'set-cookie':
                    cookies.load(value)
            assert set(cookies) == {'hermes_session_pkce'}
            cookie = 'hermes_session_pkce=' + cookies['hermes_session_pkce'].coded_value
            status, headers, login = exchange('POST', '/auth/password-login', dict(provider='basic', username='fixture-operator', password=password), {'Cookie': cookie})
            assert status == 200 and login['ok'] is True
            code = callback_code(login['next'], uri, state)
        secrets_seen.append(code)
        status, _, token = exchange('POST', '/auth/native/token', dict(code=code, code_verifier=verifier))
        assert status == 200 and token['provider'] == 'basic' and token['user_id'] == 'fixture-operator' and token['token_type'] == 'Bearer'
        secrets_seen += [token['access_token'], token['refresh_token']]
        assert exchange('POST', '/auth/native/token', dict(code=code, code_verifier=verifier))[0] == 400
        status, _, renewed = exchange('POST', '/auth/native/refresh', dict(provider='basic', refresh_token=token['refresh_token']))
        assert status == 200 and renewed['provider'] == 'basic' and renewed['access_token'] != token['access_token']
        secrets_seen += [renewed['access_token'], renewed['refresh_token']]
        status, _, me = exchange('GET', '/api/auth/me', headers={'Authorization': 'Bearer ' + renewed['access_token']})
        assert status == 200 and me['provider'] == 'basic' and me['user_id'] == 'fixture-operator'
        # Transfer this same fully consumed socket, never connect a successor.
        connection.sock = None
        adapter = PinnedRetirementHttp(owner, sock, renewed['access_token'], native)
        return adapter
    except BaseException:
        if sock is not None:sock.close()
        raise



def run(root,operation,service_operation,authority,definition,source,source_files,store,python,
        scope_configuration_sha256,approval_receipt_sha256,operational_roots):
    root,source,store,python=map(Path,(root,source,store,python));os.umask(0o077)
    if (sys.platform!='linux' or not sys.flags.isolated or not sys.dont_write_bytecode or os.geteuid()==0
        or not re.fullmatch('[a-f0-9]{32}',operation) or not re.fullmatch('[a-f0-9]{32}',service_operation)
        or operation==service_operation or root.name!='signal-grove-retirement-'+operation or anchor(root).mode!=0o700
        or python.name!='python' or python.parent.name!='bin' or not (python.parent.parent/'pyvenv.cfg').is_file()
        or definition.scope!='production_review_only' or set(operational_roots)!={'backup_programs','old_source','old_store'}):
        raise ValueError('Exact isolated native SDK retirement context required')
    scripts=Path(__file__).resolve().parents[1]
    home=root/'sdk';home.mkdir(mode=0o700)
    for name in ('account','config','cache','state','data','scratch','hermes-home'):(home/name).mkdir(mode=0o700)
    for name in ('ledger','identity','output'):(root/name).mkdir(mode=0o700)
    password,signing=secrets.token_urlsafe(32),secrets.token_urlsafe(32);salt=secrets.token_bytes(16)
    hashed=hashlib.scrypt(password.encode(),salt=salt,n=2**14,r=8,p=1,dklen=32,maxmem=0)
    password_hash='scrypt$16384$8$1$'+base64.b64encode(salt).decode()+'$'+base64.b64encode(hashed).decode()
    config=dict(model=dict(provider='custom',default='fixture-no-inference',base_url='http://127.0.0.1:9/v1',api_key='fixture-no-inference'),
        fallback_model=[],local_runtime=dict(enabled=False),dashboard=dict(public_url='http://signal-grove-fixture.invalid',
        basic_auth=dict(username='fixture-operator',password_hash=password_hash,secret=signing)))
    _write(home/'hermes-home/config.yaml',config)
    _write(home/'fixture-marker.json',dict(schema=1,sdkSource=str(source),mode='isolated-provider-lifespan'))
    paths={**operational_roots,'hermes_home':home/'hermes-home','journal':root/'ledger','executor':scripts,
        'new_source':source,'new_store':store,'backup_identity':root/'identity','backup_output':root/'output'}
    directories=OperationalDirectories(tuple((n,anchor(paths[n])) for n in ROLES));captured=capture_native(definition,definition.sha256)
    publication=captured.capture_ancestry(captured.sha256);boundary=NativeOperationalPaths.capture(publication,publication.sha256,directories,directories.sha256)
    sdk=SourceInventory(anchor(source),tuple(source_files))
    executor=SourceInventory(anchor(scripts),tuple(sorted((p.relative_to(scripts).as_posix(),observe_code(p).sha256) for p in scripts.rglob('*.py'))))
    if not EXECUTOR_REQUIRED<=dict(executor.files).keys():raise ValueError('Complete executor inventory required')
    unit='signal-grove-acceptance-'+service_operation+'-serve.service'
    server=scripts/'native/sdk_provider_fixture.py';environment=child_environment(home)
    argv=['/usr/bin/env','-i',*(k+'='+v for k,v in environment.items()),str(python),'-I','-B',str(server),'--server',str(home),str(source)]
    role_configuration=_role_configuration(unit,argv);executable=str(python.resolve())
    plan=Plan(digest(dict(scope='isolated_actual_sdk_retirement_only',sdk_sha256=sdk.sha256,executor_sha256=executor.sha256,
        boundary_sha256=boundary.sha256,unit=unit,configuration_sha256=role_configuration)),digest(asdict(authority)),
        digest(dict(scope='synthetic_non_retirement_prerequisites')),digest(dict(scope='separate_runtime_recovery_required')))
    bindings=tuple(UnitBinding(role,'signal-grove-acceptance-'+service_operation+'-'+role+'.service',role_configuration,role_configuration,executable,executable) for role in ('gateway','serve'))
    record=dict(state='NORMAL_SDK_NATIVE_ROLE_INTENT',operation=operation,unit=unit,service_operation=service_operation,
        argv=argv,role_configuration_sha256=role_configuration,sdk_inventory_sha256=sdk.sha256,sdk_files=len(sdk.files),
        executor_inventory_sha256=executor.sha256,executor_files=len(executor.files),requests=[],live_changes=False,
        real_credential_used=False,synthetic_non_retirement_prerequisites=True,production_retirement_accepted=False,started=time.time())
    def save():
        with (root/'operation.json').open('w',encoding='utf-8') as out:
            json.dump(record,out,indent=2);out.write('\n');out.flush();os.fsync(out.fileno())
    save();dispatched=False;adapter=None;owner=None;failure=None;secrets_seen=[password,signing]
    stdout=home/'server.stdout';stderr=home/'server.stderr';stdout.touch(mode=0o600,exist_ok=False);stderr.touch(mode=0o600,exist_ok=False)
    with CutoverJournal(paths['journal'],expected_identity=(paths['journal'].stat().st_dev,paths['journal'].stat().st_ino),operational_paths=boundary,expected_operational_sha256=boundary.sha256) as journal,NativeServiceManager(authority,plan,journal,bindings) as manager:
        try:
            scope=manager.execution_scope(operation,owned_fixture=False)
            if scope.configuration_sha256!=scope_configuration_sha256:raise AssertionError('Exact finite controller required')
            record['controller']=asdict(scope);save()
            probe=subprocess.run(['systemctl','--user','show',unit,'--property=LoadState,ActiveState'],capture_output=True,timeout=8)
            if b'LoadState=not-found' not in probe.stdout or b'ActiveState=inactive' not in probe.stdout:raise AssertionError('Fresh owned native role required')
            command=['systemd-run','--user','--quiet','--unit='+unit,'--property=Type=exec','--property=RuntimeMaxSec=180',
                '--property=TimeoutStopSec=8','--property=Restart=no','--property=KillMode=control-group','--property=UMask=0077',
                '--property=MemoryMax=2G','--property=TasksMax=512','--property=StandardOutput=file:'+str(stdout),
                '--property=StandardError=file:'+str(stderr),'--working-directory='+str(home),*argv]
            record.update(state='NORMAL_SDK_NATIVE_ROLE_DISPATCH_INTENT',command=command);save();dispatched=True
            started=subprocess.run(command,capture_output=True,timeout=15)
            (root/'start.stdout').write_bytes(started.stdout);(root/'start.stderr').write_bytes(started.stderr)
            if started.returncode:raise AssertionError('Owned role start acknowledgment failed; inspect original unit')
            deadline=time.monotonic()+60;port=None
            while time.monotonic()<deadline:
                matches=re.findall(r'^HERMES_BACKEND_READY port=([0-9]+)$',stdout.read_text(encoding='utf-8'),re.M)
                if matches:
                    if len(matches)!=1:raise AssertionError('Ambiguous SDK readiness')
                    port=int(matches[0]);break
                time.sleep(.1)
            if port is None or not 0<port<=65535:raise AssertionError('Owned normal SDK readiness unproven')
            before=manager.snapshot('serve');owner=before.owner
            if owner is None or before.configuration_sha256!=role_configuration or before.executable!=executable:raise AssertionError('Actual SDK native role differs')
            manager._manager('RefUnit','s',(unit,),'')
            _write(root/'native-start.json',asdict(before));record.update(state='NORMAL_SDK_PROVIDER_LOGIN',owner=asdict(owner),port=port);save()
            spec=ServeRetirementSpec(sdk,executor,anchor(home/'hermes-home'),before,port,scope_configuration_sha256,'new_source')
            execution=ApprovedExecution(operation,plan.packet_sha256,plan.authority_sha256,'forward',approval_receipt_sha256,
                (scope.active_enter_usec+scope.runtime_max_usec+scope.stop_timeout_usec)/1e6+5)
            journal.begin(operation,plan.packet_sha256,authorization_sha256=execution.binding_sha256)
            for step in FORWARD[:FORWARD.index('serve_retirement')]:
                journal.intent(step);journal.verified(step,digest(dict(scope='synthetic_non_retirement_prerequisite',step=step)))
            journal.intent('serve_retirement');actor=NativeServeRetirement(plan,journal,manager,boundary,'serve_retirement',spec,spec.sha256,None)
            started=time.monotonic();prepared=actor.prepare_inputs(execution)
            record.update(state='NORMAL_SDK_INPUTS_PREPARED_BEFORE_LOGIN',prepared_inputs=prepared,content_preparation_seconds=time.monotonic()-started);save()
            adapter=_authenticate(home,owner,port,password,record,save,secrets_seen)
            started=time.monotonic();actor.bind_transport(execution,adapter);record['transport_binding_seconds']=time.monotonic()-started;save()
            started=time.monotonic();result=actor.run(execution);record['retirement_run_seconds']=time.monotonic()-started

            if result['state']!='effects_observed' or result['proof']['checks']!={'serve_admission_closed':True} or result['proof']['protocol_requests']!=4:raise AssertionError('Normal SDK actor result unproven')
            if journal.view['pending']!='serve_retirement' or manager.snapshot('serve')!=before:raise AssertionError('Actor changed its journal stage or service')
            try:actor.run(execution)
            except Refused:pass
            else:raise AssertionError('Spent actor dispatched again')
            raw=(paths['journal']/result['receipt']).read_bytes();rows=[json.loads(x) for x in raw.splitlines()]
            if len(raw)>65536 or any(value.encode() in raw for value in secrets_seen):raise AssertionError('Bounded credential-free effect receipt required')
            witness=rows[1]['evidence']['inputs']['sdk']
            if witness['inventory_sha256']!=sdk.sha256 or witness['files']!=len(sdk.files) or not re.fullmatch('[a-f0-9]{64}',witness['identities_sha256']):raise AssertionError('Full SDK inventory witness differs')
            if adapter._usable or adapter._token is not None:raise AssertionError('Terminal transport/credential was not closed')
            record.update(state='NORMAL_SDK_NATIVE_ACTOR_OBSERVED_SHUTDOWN_PENDING',retirement=result,spec_sha256=spec.sha256,
                journal_pending=journal.view['pending'],journal_sha256=journal.history_sha256,receipt_bytes=len(raw),normal_pkce_login=True,
                one_time_code_replay_refused=True,native_refresh=True,one_socket_across_login_and_retirement=True,
                real_root_ancestry=True,actor_replay_refused=True,actor_stopped_service=False,actor_completed_journal_stage=False)
            save()
        except BaseException as error:
            failure=type(error).__name__;record.update(state='NORMAL_SDK_NATIVE_FIXTURE_FAILED_OR_UNKNOWN',error_type=failure,
                error_frames=[dict(file=Path(x.filename).name,line=x.lineno,function=x.name) for x in traceback.extract_tb(error.__traceback__)[-8:]])
            save()
        finally:
            if adapter is not None:adapter.close()
            if dispatched:
                details=subprocess.run(['systemctl','--user','show',unit,'--property=LoadState,ExecStart,MainPID'],capture_output=True,timeout=8)
                (root/'cleanup-before.log').write_bytes(details.stdout+details.stderr)
                if b'LoadState=loaded' in details.stdout:
                    if str(server).encode() not in details.stdout or str(home).encode() not in details.stdout:raise AssertionError('Owned cleanup command differs; no stop sent')
                    current=manager.snapshot('serve')
                    if current.owner is not None:
                        if owner is not None and current.owner!=owner:raise AssertionError('Owned cleanup incarnation changed; no stop sent')
                        _write(home/'stop-authorized.json',dict(owner=asdict(current.owner),reason='owned_native_fixture_terminal_cleanup'))
                    record['cleanup_stop_intent']=True;save()
                    stopped=subprocess.run(['systemctl','--user','stop',unit],capture_output=True,timeout=20)
                    (root/'cleanup-stop.log').write_bytes(stopped.stdout+stopped.stderr)
                    if stopped.returncode:raise AssertionError('Owned cleanup outcome unproven; inspect without replay')
                observed=subprocess.run(['systemctl','--user','show',unit,'--property=LoadState,ActiveState,SubState,MainPID,ControlPID,NRestarts,ControlGroup,Result'],capture_output=True,timeout=8)
                (root/'cleanup-after.log').write_bytes(observed.stdout+observed.stderr)
                properties=dict(line.split('=',1) for line in observed.stdout.decode().splitlines() if '=' in line)
                record['cleanup_native']=properties;record['original_owner_ended']=owner is None or read_process_owner(owner.pid)!=owner;save()
                if properties['MainPID']!='0' or properties['ControlPID']!='0' or properties['NRestarts']!='0' or properties['ActiveState'] not in ('inactive','failed') or properties['ControlGroup']!='':raise AssertionError('Owned role is not fully stopped')
                if failure is None and (properties['ActiveState']!='inactive' or properties['Result']!='success'):raise AssertionError('Owned normal SDK shutdown did not succeed')
    logs=stdout.read_text(encoding='utf-8')+stderr.read_text(encoding='utf-8')
    record.update(credential_values_absent_from_logs=not any(v in logs for v in secrets_seen),
        fence_refusals=json.loads((home/'fence-refusals.json').read_text(encoding='utf-8')) if (home/'fence-refusals.json').exists() else [],
        pm_readiness_warning='install out of sync' in logs);save()
    if failure is not None or not record['credential_values_absent_from_logs'] or record['fence_refusals'] or record['pm_readiness_warning']:raise AssertionError('Normal SDK native fixture remains held; inspect original evidence')
    trace=[json.loads(x) for x in (home/'lifecycle.jsonl').read_text(encoding='utf-8').splitlines()]
    if sum(x['function']=='commit' and x['event']=='call' for x in trace)!=1:raise AssertionError('Expected exactly one actual SDK commit')
    if not any(x['function']=='_unlink_pty_markers' and x['event']=='return' for x in trace):raise AssertionError('Normal SDK marker cleanup unproven')
    cleanup=max(i for i,x in enumerate(trace) if x['function']=='shutdown_local_runtime' and x['event']=='return')
    if not any(x['function']=='standalone_lifespan' and x['event']=='return' for x in trace[cleanup+1:]):raise AssertionError('Normal SDK lifecycle exit ordering unproven')
    if not (home/'source-commit.json').is_file() or not (home/'source-shutdown.json').is_file():raise AssertionError('Actual SDK source locations missing')
    if any(v in logs for v in ('ProgrammingError','Cannot operate on a closed database','Exception ignored in:','Traceback (most recent call last):')):raise AssertionError('Original SDK shutdown warning/error remains')
    record.update(state='NORMAL_SDK_NATIVE_JOURNAL_RETIREMENT_COMPONENT_PASS',lifecycle_events=len(trace),sdk_functions_patched=False,
        live_user_provider_session_accepted=False,full_executor_accepted=False,production_retirement_accepted=False,finished=time.time());save()
    _write(root/'fixture-result.json',record)
    return record
