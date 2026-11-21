"""One original-journal forward drain of one registered SDK home.

No live CLI, stop, cancellation, thaw, marker refresh or recovery is provided.
The caller runs in the reviewed SDK PM environment, supplies the exact native
gateway/controller bindings and qualifies other profiles/workers separately.
Source-bound cooperative SDK semantics are not whole-fleet exclusion proof.
"""
from contextlib import closing
from dataclasses import dataclass,asdict,replace
from datetime import datetime,timezone
import hashlib
import importlib
import importlib.machinery
import json
import math
import os
from pathlib import Path
import re
import socket
import sqlite3
import stat
import sys
import time
from types import ModuleType

from cutover_coordinator import Plan,ApprovedExecution,FORWARD
from cutover_journal import CutoverJournal,Refused,Unknown
from native_file_publication import DirectoryAnchor,anchor,digest,_Receipt,_image,_directory,_renameat2,_same_renamed,_path
from native_operational_paths import NativeOperationalPaths
from native_service_manager import NativeServiceManager,UnitSnapshot
from native_retirement_owner import read_process_owner
from native_gateway_control import NativeGatewayControl,project_registered_status,LEDGER_COUNTERS
from backup_sdk_snapshot import SourceInventory,ReviewedImports
from native_verified_inventory import VerifiedInventory

SDK_REQUIRED=frozenset({'agent/__init__.py','agent/project_control_store.py','agent/project_session_scope.py',
    'gateway/__init__.py','gateway/drain_control.py','gateway/control_socket.py','gateway/run_activity_status.py',
    'gateway/memory_status.py','hermes_constants.py','utils.py'})
EXECUTOR_REQUIRED=frozenset({'native_gateway_drain.py','native_gateway_control.py','native_retirement_owner.py',
    'native_service_manager.py','native_operational_paths.py','native_file_publication.py','cutover_journal.py',
    'cutover_coordinator.py','backup_sdk_snapshot.py','native_backup_archive.py','native_verified_inventory.py',
    'plan_pi_runtime.py','rendered_publication_catalog.py','retirement_guard.py'})
MARKER='.drain_request.json'
TABLES=frozenset({'projects','operations','session_bindings','turn_admissions','admission_fence',
    'fence_transactions','tool_admissions','process_links'})
IDENTIFIER=re.compile(r'[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}')


@dataclass(frozen=True)
class FencePreimage:
    epoch:int
    owner:str|None
    def __post_init__(self):
        if (type(self.epoch) is not int or not 0<=self.epoch<2**63-1
            or (self.epoch==0 and self.owner is not None)
            or (self.epoch>0 and (type(self.owner) is not str or not IDENTIFIER.fullmatch(self.owner)))):
            raise ValueError('Exact initial or previously released SDK fence preimage required')


def ledger_stamp(info):
    if (not stat.S_ISREG(info.st_mode) or info.st_uid!=os.geteuid() or info.st_nlink!=1
        or stat.S_IMODE(info.st_mode) not in (0o600,0o644)):
        raise Refused('Existing protected owner SQLite ledger required')
    return (info.st_dev,info.st_ino,info.st_uid,info.st_gid,stat.S_IMODE(info.st_mode),info.st_nlink)


def schema_digest(connection):
    """Bounded metadata on an already authorized SQLite connection; no SQL writes."""
    rows=connection.execute("SELECT type,name,tbl_name,sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' ORDER BY type,name").fetchmany(257)
    encoded=json.dumps([tuple(row) for row in rows],separators=(',',':')).encode()
    tables={row[1] for row in rows if row[0]=='table'}
    if len(rows)>256 or len(encoded)>256*1024 or not TABLES<=tables or any(row[0]=='trigger' for row in rows):
        raise Refused('Existing reviewed ledger schema without triggers required')
    for table in ('turn_admissions','tool_admissions'):
        if 'owner_birth' not in {row[1] for row in connection.execute('PRAGMA table_info('+table+')')}:
            raise Refused('Ledger migration must be reviewed separately from drain')
    return hashlib.sha256(encoded).hexdigest()


def fence_snapshot(value):
    if (type(value) is not dict or set(value)!=LEDGER_COUNTERS|{'epoch','owner','frozen'}
        or type(value['epoch']) is not int or not 0<=value['epoch']<2**63
        or type(value['frozen']) is not bool
        or (value['epoch']==0 and (value['owner'] is not None or value['frozen']))
        or (value['epoch']>0 and (type(value['owner']) is not str or not IDENTIFIER.fullmatch(value['owner'])))
        or any(type(value[n]) is not int or value[n]<0 for n in LEDGER_COUNTERS)):
        raise Unknown('Exact SDK fence and all registered counters are unproven')
    return dict(value)


def require_fence(value,preimage,operation=None):
    value=fence_snapshot(value)
    expected=(preimage.epoch,preimage.owner,False) if operation is None else (preimage.epoch+1,operation,True)
    if (value['epoch'],value['owner'],value['frozen'])!=expected:
        raise Unknown('Reviewed SDK fence ownership or epoch changed; no adoption or replay')
    return value


def observe_frozen_unbound(path, preimage, operation, count_unbound, timeout):
    """Snapshot the exact frozen owner and SDK's unbound count without a writer transaction."""
    try:
        with closing(sqlite3.connect(path.as_uri()+'?mode=ro',uri=True,timeout=timeout)) as db:
            db.execute('PRAGMA query_only=ON')
            db.execute('BEGIN')
            row=db.execute('SELECT epoch,owner,frozen FROM admission_fence WHERE singleton=1').fetchone()
            if (row is None or type(row[0]) is not int or type(row[2]) is not int
                or row!=(preimage.epoch+1,operation,1)):
                raise Unknown('Frozen ledger ownership changed during the gateway observation')
            count=count_unbound(db)
            if type(count) is not int or count<0:
                raise Unknown('Unbound turn count is unproven')
            return count
    except (sqlite3.Error,OSError):
        raise Unknown('Unbound ledger observation is unproven') from None


def checked_epoch(boot,ticks):
    if (type(boot) is not str or not re.fullmatch('[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}',boot)
        or type(ticks) is not str or not re.fullmatch('0|[1-9][0-9]*',ticks) or int(ticks)>=2**64):
        raise Unknown('Strong native boot/PID1 epoch required')
    return boot+':'+ticks


def native_epoch():
    return checked_epoch(Path('/proc/sys/kernel/random/boot_id').read_text().strip(),Path('/proc/1/stat').read_text().rsplit(')',1)[1].split()[19])


def marker_payload(operation,epoch,requested_at):
    if type(operation) is not str or not re.fullmatch('[a-f0-9]{32}',operation) or type(epoch) is not str or ':' not in epoch:
        raise ValueError('Exact operation and strong epoch required')
    boot,ticks=epoch.split(':',1);checked_epoch(boot,ticks)
    if type(requested_at) is not datetime or requested_at.tzinfo is None or requested_at.utcoffset().total_seconds()!=0:
        raise ValueError('Exact timezone-aware UTC marker timestamp required')
    return dict(action='drain',requested_at=requested_at.isoformat(),principal=operation,epoch=epoch,suppress_notification=True)


def publish_marker(fd,home,operation,payload,before_dispatch):
    """Private primitive. The original actor authorizes immediately before rename.

    A failed temporary file/arrival is preserved. There is no overwrite, unlink,
    refresh, cancellation, cleanup or fallback to a weaker publication primitive.
    """
    if type(operation) is not str or not re.fullmatch('[a-f0-9]{32}',operation):raise Refused('Exact marker operation required')
    if type(payload) is not dict or set(payload)!={'action','requested_at','principal','epoch','suppress_notification'}:raise Refused('Closed supported drain payload required')
    try:expected=marker_payload(operation,payload['epoch'],datetime.fromisoformat(payload['requested_at']))
    except (ValueError,TypeError,Unknown):raise Refused('Exact supported drain payload required') from None
    if payload!=expected or payload['suppress_notification'] is not True:raise Refused('Exact supported drain payload required')
    _directory(fd,home)
    if _image(fd,MARKER,home.uid).kind!='absent':raise Refused('Never overwrite or adopt an existing drain marker')
    data=(json.dumps(payload,sort_keys=True,separators=(',',':'))+'\n').encode()
    if len(data)>4096:raise Refused('Bounded supported marker payload required')
    temporary='.signal-grove-drain-'+operation+'.json'
    opened=os.open(temporary,os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW|os.O_CLOEXEC,0o600,dir_fd=fd)
    try:
        remaining=memoryview(data)
        while remaining:
            count=os.write(opened,remaining)
            if type(count) is not int or not 0<count<=len(remaining):raise Unknown('Drain marker write is unproven')
            remaining=remaining[count:]
        os.fsync(opened)
    finally:os.close(opened)
    prepared=_image(fd,temporary,home.uid)
    if prepared.kind!='file' or prepared.mode!=0o600 or prepared.sha256!=hashlib.sha256(data).hexdigest():raise Unknown('Exact temporary marker is unproven')
    os.fsync(fd);_directory(fd,home)
    if _image(fd,MARKER,home.uid).kind!='absent':raise Refused('Drain marker arrived during preparation')
    if _image(fd,temporary,home.uid)!=prepared:raise Unknown('Prepared marker changed')
    before_dispatch();_renameat2(fd,temporary,MARKER,1);os.fsync(fd)
    observed=_image(fd,MARKER,home.uid)
    if not _same_renamed(observed,prepared) or _image(fd,temporary,home.uid).kind!='absent':raise Unknown('Exclusive marker publication is unproven')
    return observed


@dataclass(frozen=True)
class GatewayDrainSpec:
    sdk:SourceInventory
    executor:SourceInventory
    home:DirectoryAnchor
    before:UnitSnapshot
    socket_path:str
    fence:FencePreimage
    ledger_identity:tuple
    ledger_schema_sha256:str
    sdk_python_prefix:str
    scope_configuration_sha256:str
    source_role:str
    owned_root:str|None=None
    def __post_init__(self):
        if (type(self.sdk) is not SourceInventory or type(self.executor) is not SourceInventory
            or not SDK_REQUIRED<=dict(self.sdk.files).keys() or not EXECUTOR_REQUIRED<=dict(self.executor.files).keys()
            or type(self.home) is not DirectoryAnchor or self.home.mode!=0o700 or type(self.before) is not UnitSnapshot
            or self.before.owner is None or not re.fullmatch(r'(?:hermes|signal-grove-acceptance-[a-f0-9]{32})-gateway\.service',self.before.unit)
            or type(self.fence) is not FencePreimage or type(self.ledger_identity) is not tuple or len(self.ledger_identity)!=6
            or any(type(n) is not int or n<0 for n in self.ledger_identity) or not self.ledger_identity[1] or self.ledger_identity[2]!=self.home.uid
            or self.ledger_identity[4] not in (0o600,0o644) or self.ledger_identity[5]!=1
            or any(type(h) is not str or not re.fullmatch('[a-f0-9]{64}',h) for h in (self.ledger_schema_sha256,self.scope_configuration_sha256))
            or not _path(self.sdk_python_prefix)
            or self.source_role not in ('old_source','new_source') or type(self.socket_path) is not str
            or len(os.fsencode(self.socket_path))>103
            or Path(self.socket_path)!=Path('/run/user')/str(self.home.uid)/('hermes-gw-'+hashlib.sha256(os.path.normcase(self.home.path).encode()).hexdigest()[:16])/'control.sock'
            or (self.owned_root is not None and (type(self.owned_root) is not str or not re.fullmatch('signal-grove-catalog-[a-f0-9]{32}',Path(self.owned_root).name)
                or any(not Path(p).is_relative_to(self.owned_root) for p in (self.home.path,self.sdk.root.path,self.executor.root.path))))):
            raise ValueError('Exact reviewed SDK, executor, home, native gateway, ledger and controller bindings required')
    @property
    def sha256(self):return digest(asdict(self))


@dataclass(frozen=True)
class _Prepared:
    actor:object
    execution:ApprovedExecution
    pid:int
    sdk:VerifiedInventory
    executor:VerifiedInventory
    ledger_class:object=None
    drain_module:ModuleType|None=None
    count_unbound:object=None


class _SdkSourceImports(ReviewedImports):
    """Verify SDK source on import; PM dependency qualification stays separate."""
    def __init__(self,inventory):
        super().__init__((inventory,));self.root=Path(inventory.root.path)
        self.roots={name.split('/',1)[0].removesuffix('.py') for name,_ in inventory.files if name.endswith('.py')}
    def find_spec(self,fullname,path=None,target=None):
        found=importlib.machinery.PathFinder.find_spec(fullname,path,target)
        if fullname.split('.',1)[0] not in self.roots:
            inside=found is not None and ((found.origin is not None and Path(found.origin).is_relative_to(self.root))
                or (found.origin is None and any(Path(p).is_relative_to(self.root) for p in (found.submodule_search_locations or ()))))
            if not inside:return None
            return super().find_spec(fullname,path,target)
        if found is None:raise ImportError('Reviewed SDK module resolution unavailable')
        if found is not None and found.origin is not None and not Path(found.origin).is_relative_to(self.root):
            raise ImportError('SDK namespace resolved outside its reviewed source')
        return super().find_spec(fullname,path,target)


class NativeGatewayDrain:
    def __init__(self,plan,journal,manager,paths,spec,reviewed_spec_sha256):
        if (sys.platform!='linux' or os.geteuid()==0 or not sys.flags.isolated or not sys.dont_write_bytecode
            or type(plan) is not Plan or type(journal) is not CutoverJournal or type(manager) is not NativeServiceManager
            or manager.plan!=plan or manager.journal is not journal or type(paths) is not NativeOperationalPaths
            or journal._operational_paths is not paths or type(spec) is not GatewayDrainSpec or spec.sha256!=reviewed_spec_sha256):
            raise Refused('Exact original-journal native SDK gateway drain context required')
        self.plan,self.journal,self.manager,self.paths,self.spec=plan,journal,manager,paths,spec
        self.stage='gateway_drain';self.used=False;self.state='ready';self.last_receipt=None;self._prepared=None;self._prepare_attempted=False;self.requests=0;self.freeze_calls=0;self._import_gate=None

    def _deadline(self,e):
        now=time.monotonic()
        if not math.isfinite(now) or now>=e.deadline:raise Unknown('Original gateway drain approval expired; no dispatch')
        return now

    def _authorize(self,e,*,claimed=False):
        if type(e) is not ApprovedExecution or e.scope!='forward' or e.authority_sha256!=self.plan.authority_sha256 or e.packet_sha256!=self.plan.packet_sha256:
            raise Refused('Original forward gateway drain approval required')
        now=self._deadline(e)
        self.journal.assert_current();view=self.journal.view;rows=[r for r in view['records'] if r['operation']==e.operation];index=FORWARD.index(self.stage)
        if (view['state']!='pending' or view['pending']!=self.stage or view['operation']!=e.operation or view['mode']!='forward'
            or view['authorization_sha256']!=e.binding_sha256 or view['plan_sha256']!=e.packet_sha256 or view['root_plan_sha256']!=self.plan.packet_sha256
            or [r['step'] for r in rows if r['kind']=='intent']!=list(FORWARD[:index+1])
            or [r['step'] for r in rows if r['kind']=='verified']!=list(FORWARD[:index])
            or os.getpid()!=self.journal._owner_pid or not self.journal._owns_operation):
            raise Refused('Exact pending prefix and original live journal owner required')
        if claimed and (e.operation,self.stage) not in self.journal._dispatch_claims:raise Refused('Original gateway drain claim missing')
        self.paths.require('journal',self.journal._directory);self.paths.require('hermes_home',self.spec.home.path)
        self.paths.require(self.spec.source_role,self.spec.sdk.root.path);self.paths.require('executor',self.spec.executor.root.path)
        if self.spec.owned_root is None:
            if self.paths.scope!='production_review_only':raise Refused('Real-root operational ancestry required')
            self.paths.contains('executor',Path(__file__).resolve())
        elif self.spec.owned_root!=self.paths.ancestry.root:raise Refused('Owned drain root differs')
        if anchor(self.spec.home.path)!=self.spec.home or str(Path(sys.prefix))!=self.spec.sdk_python_prefix or os.environ.get('HERMES_HOME')!=self.spec.home.path:
            raise Refused('Reviewed SDK PM environment or home differs')
        scope=self.manager.execution_scope(e.operation,owned_fixture=self.spec.owned_root is not None);scope.require_window(e,read_process_owner(os.getpid()),now)
        if scope.configuration_sha256!=self.spec.scope_configuration_sha256:raise Refused('Reviewed native controller changed')
        self._deadline(e);return scope

    def _current(self):
        binding=self.manager.bindings['gateway'];before=self.spec.before
        config=binding.old_configuration_sha256 if self.spec.source_role=='old_source' else binding.new_configuration_sha256
        executable=binding.old_executable if self.spec.source_role=='old_source' else binding.new_executable
        current=self.manager.snapshot('gateway')
        if (before.unit!=binding.unit or before.configuration_sha256!=config or current!=before
            or current.control_pid!=0 or current.job_id!=0 or current.job_path!='/' or current.load_state!='loaded'
            or current.active_state!='active' or current.sub_state!='running' or current.executable!=executable
            or str(Path(sys.executable).resolve())!=executable or read_process_owner(current.main_pid)!=current.owner):
            raise Unknown('Original native SDK gateway/controller executable is unproven')
        return current

    def prepare_inputs(self,e):
        if self.used or self._prepare_attempted or self._prepared is not None:raise Refused('Gateway drain preparation is spent')
        self._prepare_attempted=True
        try:
            self._authorize(e);self._current()
            sdk=VerifiedInventory.capture(self.spec.sdk,self.paths,self.spec.source_role);executor=VerifiedInventory.capture(self.spec.executor,self.paths,'executor')
            gate=_SdkSourceImports(self.spec.sdk)
            if any(name.split('.',1)[0] in gate.roots for name in tuple(sys.modules)):
                raise Refused('Use a fresh controller without preloaded SDK namespaces')
            self._authorize(e);self._current();self._prepared=_Prepared(self,e,os.getpid(),sdk,executor);self.state='inputs_prepared'
            return dict(sdk=sdk.summary(),executor=executor.summary())
        except BaseException:
            self._remove_import_gate();self.state='preflight_refused';raise

    def _remove_import_gate(self):
        if self._import_gate is not None:
            sys.meta_path[:]=[gate for gate in sys.meta_path if gate is not self._import_gate];self._import_gate=None

    def _load_sdk(self,e):
        # Import-time SDK effects also belong behind the original stage claim.
        self._authorize(e,claimed=True);self._inputs(e);self._current();p=self._prepared
        if p.ledger_class is not None or self._import_gate is not None:raise Refused('SDK loading is spent')
        gate=_SdkSourceImports(self.spec.sdk)
        if any(name.split('.',1)[0] in gate.roots for name in tuple(sys.modules)):raise Refused('Fresh SDK namespaces required')
        self._deadline(e);self._import_gate=gate;sys.meta_path.insert(0,gate)
        ledger=importlib.import_module('agent.project_control_store');drain=importlib.import_module('gateway.drain_control')
        session_scope=importlib.import_module('agent.project_session_scope')
        for module,name in ((ledger,'agent/project_control_store.py'),(drain,'gateway/drain_control.py'),
                            (session_scope,'agent/project_session_scope.py')):
            if type(module) is not ModuleType or Path(module.__file__).resolve()!=Path(self.spec.sdk.root.path)/name:raise Refused('Exact reviewed SDK module origin required')
        for method in ('__init__','freeze','fence_status','close'):
            if Path(getattr(ledger.Ledger,method).__code__.co_filename).resolve()!=Path(self.spec.sdk.root.path)/'agent/project_control_store.py':raise Refused('SDK ledger method origin changed')
        count_unbound=session_scope.active_unbound_count
        if Path(count_unbound.__code__.co_filename).resolve()!=Path(self.spec.sdk.root.path)/'agent/project_session_scope.py':
            raise Refused('SDK unbound counter origin changed')
        self._prepared=replace(p,ledger_class=ledger.Ledger,drain_module=drain,count_unbound=count_unbound)
        self._authorize(e,claimed=True);self._inputs(e)

    def _inputs(self,e):
        p=self._prepared
        if type(p) is not _Prepared or p.actor is not self or p.execution!=e or p.pid!=os.getpid():raise Refused('Original process-local gateway inputs required')
        if p.ledger_class is not None and (self._import_gate is None or sum(gate is self._import_gate for gate in sys.meta_path)!=1):
            raise Refused('Original source-verifying SDK import gate required')
        p.sdk.check(self.spec.sdk,self.paths,self.spec.source_role);p.executor.check(self.spec.executor,self.paths,'executor')
        return dict(sdk=p.sdk.summary(),executor=p.executor.summary())

    def _database_identity(self,home_fd,pinned=None):
        _directory(home_fd,self.spec.home)
        identity=ledger_stamp(os.stat('project-control.db',dir_fd=home_fd,follow_symlinks=False))
        if identity!=self.spec.ledger_identity or (pinned is not None and ledger_stamp(os.fstat(pinned))!=identity):raise Unknown('Original ledger file identity changed')

    def _request(self,e,receipt,phase,verb,deadline):
        self._authorize(e,claimed=True);self._current();now=time.monotonic()
        if now>=deadline or self.requests>=64:raise Unknown('Original observation window or request bound expired')
        self.requests+=1;receipt.append('gateway_read_intent',dict(sequence=self.requests,phase=phase,verb=verb,owner=asdict(self.spec.before.owner)))
        self._authorize(e,claimed=True);self._current();remaining=min(2.,deadline-time.monotonic(),e.deadline-time.monotonic())
        if remaining<=0:raise Unknown('No gateway request budget remains')
        sock=socket.socket(socket.AF_UNIX,socket.SOCK_STREAM);control=None
        try:
            sock.settimeout(remaining);sock.connect(self.spec.socket_path);control=NativeGatewayControl(self.spec.before.owner,sock,timeout=remaining)
            started=time.time();body=control.exchange(verb);finished=time.time()
            projection=project_registered_status(body,self.spec.before.main_pid,hashlib.sha256(os.path.normcase(self.spec.home.path).encode()).hexdigest(),started,finished) if verb=='status' else None
            observed_state=body.get('state')
            safe_state=observed_state if type(observed_state) is str and observed_state in ('ready','starting','draining','failed') else None
            receipt.append('gateway_read_observed',dict(sequence=self.requests,phase=phase,verb=verb,body_sha256=digest(body),projection=projection,state=safe_state if verb=='identify' else None))
            self._authorize(e,claimed=True);self._current()
            if time.monotonic()>=deadline:raise Unknown('Gateway reply or receipt arrived after original expiry')
            return body if projection is None else projection
        finally:
            if control is not None:control.close()
            else:sock.close()

    def _read_unbound(self,e,home_fd,db_fd,deadline):
        self._authorize(e,claimed=True);self._current();self._database_identity(home_fd,db_fd)
        now=time.monotonic();remaining=min(.75,deadline-now,e.deadline-now)
        if remaining<=0:raise Unknown('Original observation window expired before unbound read')
        path=Path(self.spec.home.path)/'project-control.db'
        count=observe_frozen_unbound(path,self.spec.fence,e.operation,self._prepared.count_unbound,remaining)
        self._database_identity(home_fd,db_fd)
        return count

    def _observe_idle(self,e,receipt,phase,*,unbound=None):
        if (phase=='drain')!=(unbound is not None):raise Refused('Drain requires the original frozen ledger observer')
        started=time.monotonic();deadline=min(started+5,e.deadline)
        receipt.append('gateway_window',dict(phase=phase,started_monotonic=started,deadline_monotonic=deadline,maximum_seconds=5))
        while True:
            projection=self._request(e,receipt,phase,'status',deadline)
            expected=(self.spec.fence.epoch,self.spec.fence.owner,False) if phase=='startup' else (self.spec.fence.epoch+1,e.operation,True)
            if (projection['fence_epoch'],projection['fence_owner'],projection['admission_frozen'])!=expected:raise Unknown('Foreign or unexpected admission fence; no adoption')
            if projection['lifecycle'] not in (('running',) if phase=='startup' else ('running','draining')):raise Unknown('Gateway lifecycle is unproven')
            ready=not projection['busy'] and projection['lifecycle']==('running' if phase=='startup' else 'draining')
            if ready and unbound is not None:
                active=unbound(deadline)
                receipt.append('gateway_unbound_observed',dict(sequence=self.requests,active_unbound_turns=active))
                self._authorize(e,claimed=True);self._current()
                ready=active==0
            now=time.monotonic()
            if now>=deadline:raise Unknown('Busy gateway did not settle before original expiry')
            if ready:return projection
            time.sleep(min(.2,deadline-now))

    def run(self,e):
        if self.used:raise Refused('Gateway drain actor is spent')
        receipt=None;claimed=False;home_fd=None;db_fd=None;ledger=None;checkpoint='preflight'
        try:
            if self._prepared is None:self.prepare_inputs(e)
            self._authorize(e);inputs=self._inputs(e);self._current()
            home_fd=self.paths.open_directory('hermes_home');self._database_identity(home_fd)
            if _image(home_fd,MARKER,self.spec.home.uid).kind!='absent':raise Refused('An existing drain marker cannot be adopted')
            self.journal.claim_dispatch(e.operation,self.stage,e.binding_sha256);claimed=True;self.used=True;self.state='claimed'
            receipt=_Receipt(self.journal,e,self.stage,self.spec.sha256);self.last_receipt=receipt.name
            receipt.append('gateway_preimage',dict(spec_sha256=self.spec.sha256,inputs=inputs,unit_snapshot_sha256=self.spec.before.sha256,fence=asdict(self.spec.fence)))
            checkpoint='identity';identity=self._request(e,receipt,'identity','identify',min(time.monotonic()+5,e.deadline))
            if identity.get('state')!='ready' or identity.get('hermes_home')!=self.spec.home.path:raise Unknown('Original ready SDK home is unproven')
            checkpoint='startup';self._observe_idle(e,receipt,'startup')
            checkpoint='schema';self._authorize(e,claimed=True);self._inputs(e);self._current();self._database_identity(home_fd)
            db_fd=os.open('project-control.db',os.O_RDONLY|os.O_NOFOLLOW|os.O_NONBLOCK|os.O_CLOEXEC,dir_fd=home_fd);self._database_identity(home_fd,db_fd)
            path=Path(self.spec.home.path)/'project-control.db'
            with closing(sqlite3.connect(path.as_uri()+'?mode=ro',uri=True,timeout=.75)) as observed:
                observed.execute('PRAGMA query_only=ON');observed.execute('BEGIN')
                if schema_digest(observed)!=self.spec.ledger_schema_sha256:raise Unknown('Reviewed existing ledger schema differs')
                row=observed.execute('SELECT epoch,owner,frozen FROM admission_fence WHERE singleton=1').fetchone()
                if row!=(self.spec.fence.epoch,self.spec.fence.owner,0) or observed.execute('SELECT 1 FROM fence_transactions WHERE owner=?',(e.operation,)).fetchone():raise Unknown('Ledger preimage or fresh operation differs')
            self._authorize(e,claimed=True);self._current();self._database_identity(home_fd,db_fd)
            checkpoint='sdk_import';self._load_sdk(e);self._authorize(e,claimed=True);self._current();self._database_identity(home_fd,db_fd)
            checkpoint='sdk_constructor';self._deadline(e);ledger=self._prepared.ledger_class(path)
            if schema_digest(ledger.db)!=self.spec.ledger_schema_sha256:raise Unknown('SDK constructor changed the reviewed schema')
            require_fence(ledger.fence_status(),self.spec.fence);self._database_identity(home_fd,db_fd)
            checkpoint='freeze';receipt.append('gateway_freeze_intent',dict(operation=e.operation,expected_epoch=self.spec.fence.epoch,next_epoch=self.spec.fence.epoch+1))
            self._authorize(e,claimed=True);self._inputs(e);self._current();self._database_identity(home_fd,db_fd)
            if _image(home_fd,MARKER,self.spec.home.uid).kind!='absent':raise Unknown('Drain marker arrived before freeze')
            self._deadline(e);self.freeze_calls+=1;frozen=require_fence(ledger.freeze(e.operation),self.spec.fence,e.operation)
            self._authorize(e,claimed=True);self._database_identity(home_fd,db_fd);receipt.append('gateway_frozen',dict(fence=frozen,freeze_calls=self.freeze_calls))
            checkpoint='marker';epoch=native_epoch()
            if self._prepared.drain_module.current_instantiation_epoch()!=epoch:raise Unknown('SDK epoch differs from strong native epoch')
            payload=marker_payload(e.operation,epoch,datetime.now(timezone.utc))
            receipt.append('gateway_marker_intent',dict(payload_sha256=digest(payload),temporary='.signal-grove-drain-'+e.operation+'.json',overwrite=False))
            def before_publish():
                self._authorize(e,claimed=True);self._current();self._inputs(e);self._database_identity(home_fd,db_fd)
                require_fence(ledger.fence_status(),self.spec.fence,e.operation)
                if native_epoch()!=epoch or self._prepared.drain_module.current_instantiation_epoch()!=epoch:raise Unknown('Native marker epoch changed')
                self._deadline(e)
            image=publish_marker(home_fd,self.spec.home,e.operation,payload,before_publish)
            self._authorize(e,claimed=True);receipt.append('gateway_marker_published',dict(image=asdict(image),payload_sha256=digest(payload),exclusive=True))
            checkpoint='drain'
            projection=self._observe_idle(e,receipt,'drain',
                unbound=lambda deadline:self._read_unbound(e,home_fd,db_fd,deadline))
            # Recheck the entire ledger before recording a successful drain.
            final=require_fence(ledger.fence_status(),self.spec.fence,e.operation)
            if any(final[n] for n in LEDGER_COUNTERS):raise Unknown('SDK registered/unbound work remains active')
            if schema_digest(ledger.db)!=self.spec.ledger_schema_sha256:raise Unknown('Ledger schema changed during drain')
            ledger.close();ledger=None;self._authorize(e,claimed=True);self._current();self._database_identity(home_fd,db_fd)
            if self._inputs(e)!=inputs or _image(home_fd,MARKER,self.spec.home.uid)!=image or native_epoch()!=epoch:raise Unknown('Original drain inputs or marker changed')
            if self._prepared.drain_module.read_drain_request(home=Path(self.spec.home.path))!=payload or self._prepared.drain_module.drain_requested(home=Path(self.spec.home.path)) is not True:raise Unknown('SDK does not honor the exact published drain marker')
            receipt.append('gateway_registered_home_drained',dict(fence=final,projection=projection,marker_image=asdict(image),freeze_calls=self.freeze_calls,protocol_requests=self.requests,service_action=False,fleet_ready_to_switch=False))
            self._authorize(e,claimed=True);self.state='effects_observed'
            return dict(state=self.state,stage=self.stage,spec_sha256=self.spec.sha256,receipt=receipt.name,receipt_sha256=hashlib.sha256(receipt.data).hexdigest(),
                proof=dict(registered_home_drained=True,exclusive_marker=True,freeze_calls=self.freeze_calls,protocol_requests=self.requests,source_bound_sdk=True,
                    gateway_admission_closed=False,all_profile_worker_admission=False,fleet_ready_to_switch=False,service_action=False,automatic_thaw=False))
        except BaseException as error:
            if claimed:
                self.state='unknown'
                if receipt is not None:
                    try:receipt.append('gateway_observation_held',dict(checkpoint=checkpoint,error_type=type(error).__name__,freeze_calls=self.freeze_calls))
                    except Exception:pass
                if not isinstance(error,Exception):raise
                raise Unknown('Original gateway drain outcome uncertain; inspect ledger/marker/receipt without replay') from None
            self.state='preflight_refused'
            if not isinstance(error,Exception) or isinstance(error,(Refused,Unknown)):raise
            raise Refused('Gateway drain preflight refused without mutation dispatch') from None
        finally:
            cleanup_failed=False
            for close in ([ledger.close] if ledger is not None else [])+([lambda:os.close(db_fd)] if db_fd is not None else [])+([lambda:os.close(home_fd)] if home_fd is not None else []):
                try:close()
                except Exception:cleanup_failed=True
            self._prepared=None
            self._remove_import_gate()
            if receipt is not None:
                if cleanup_failed:
                    try:receipt.append('gateway_cleanup_held',dict(checkpoint=checkpoint))
                    except Exception:pass
                try:receipt.close()
                except Exception:cleanup_failed=True
            if cleanup_failed:
                self.state='unknown' if claimed else 'preflight_refused'
                raise Unknown('Gateway resource closure is unproven; inspect without replay') from None
