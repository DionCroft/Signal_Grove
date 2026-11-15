"""One original-journal standalone-serve retirement exchange.

An already connected, kernel-owner-verified local socket and supported signed-in
provider bearer are supplied by the caller. No discovery/login, reconnect,
replay, cancellation, service action or automatic recovery is provided here.
The supported SDK's cooperative fence semantics remain a source-bound trust
assumption; complete deployment/worker/runtime acceptance is separate.
"""
from dataclasses import dataclass,asdict
import hashlib
import math
import os
from pathlib import Path
import re
import sys
import time

from cutover_coordinator import Plan,ApprovedExecution,FORWARD,RECOVERY
from cutover_journal import CutoverJournal,Refused,Unknown
from native_file_publication import DirectoryAnchor,anchor,digest,_Receipt
from native_operational_paths import NativeOperationalPaths
from native_service_manager import NativeServiceManager,UnitSnapshot
from native_retirement_owner import LinuxSocketOwner,read_process_owner
from pinned_retirement_http import PinnedRetirementHttp
from retirement_guard import RetirementGuard
from backup_sdk_snapshot import SourceInventory

STAGES={'serve_retirement':'serve_admission_closed','rollback_serve_retirement':'serve_fenced_or_absent'}
SDK_REQUIRED=frozenset({'hermes_cli/backend_retirement.py','hermes_cli/web_routers/status.py',
 'hermes_cli/web_server_idle_proof.py','hermes_cli/web_server_idle_exit.py',
 'hermes_cli/web_server.py','hermes_cli/dashboard_auth/middleware.py'})
EXECUTOR_REQUIRED=frozenset({'native_serve_retirement.py','pinned_retirement_http.py','retirement_guard.py',
 'native_retirement_owner.py','native_service_manager.py','native_operational_paths.py',
 'native_file_publication.py','cutover_journal.py','cutover_coordinator.py','backup_sdk_snapshot.py','native_backup_archive.py',
 'plan_pi_runtime.py','rendered_publication_catalog.py'})


@dataclass(frozen=True)
class ServeRetirementSpec:
    sdk:SourceInventory
    executor:SourceInventory
    home:DirectoryAnchor
    before:UnitSnapshot
    port:int
    scope_configuration_sha256:str
    source_role:str
    owned_root:str|None=None
    def __post_init__(self):
        if (type(self.sdk) is not SourceInventory or type(self.executor) is not SourceInventory
                or not SDK_REQUIRED<=dict(self.sdk.files).keys() or not EXECUTOR_REQUIRED<=dict(self.executor.files).keys()
                or type(self.home) is not DirectoryAnchor or self.home.mode!=0o700
                or type(self.before) is not UnitSnapshot or not re.fullmatch(r'(?:hermes|signal-grove-acceptance-[a-f0-9]{32})-serve\.service',self.before.unit)
                or type(self.port) is not int or not 0<=self.port<=65535
                or (self.before.owner is None)!=(self.port==0)
                or type(self.scope_configuration_sha256) is not str or not re.fullmatch('[a-f0-9]{64}',self.scope_configuration_sha256)
                or self.source_role not in ('old_source','new_source')
                or (self.owned_root is not None and (type(self.owned_root) is not str
                    or not re.fullmatch('signal-grove-catalog-[a-f0-9]{32}',Path(self.owned_root).name)
                    or any(not Path(p).is_relative_to(self.owned_root) for p in (self.home.path,self.sdk.root.path,self.executor.root.path))))):
            raise ValueError('Exact source, executor, home, native serve snapshot and controller bindings required')
    @property
    def sha256(self):return digest(asdict(self))


class NativeServeRetirement:
    def __init__(self,plan,journal,manager,paths,stage,spec,reviewed_spec_sha256,transport):
        if (sys.platform!='linux' or os.geteuid()==0 or type(plan) is not Plan or type(journal) is not CutoverJournal
                or type(manager) is not NativeServiceManager or manager.plan!=plan or manager.journal is not journal
                or type(paths) is not NativeOperationalPaths or journal._operational_paths is not paths
                or stage not in STAGES or type(spec) is not ServeRetirementSpec or spec.sha256!=reviewed_spec_sha256
                or (spec.before.owner is None and transport is not None)
                or (spec.before.owner is not None and type(transport) is not PinnedRetirementHttp)):
            raise Refused('Exact original-journal native serve retirement actor required')
        self.plan,self.journal,self.manager,self.paths,self.stage,self.spec,self.transport=plan,journal,manager,paths,stage,spec,transport
        self.used=False;self.state='ready';self.last_receipt=None;self.protocol_events=[]

    def _authorize(self,e,*,claimed=False):
        if type(e) is not ApprovedExecution or e.authority_sha256!=self.plan.authority_sha256:
            raise Refused('Exact serve retirement approval required')
        order=FORWARD if e.scope=='forward' else RECOVERY
        if self.stage not in order:raise Refused('Serve retirement scope/stage differs')
        now=time.monotonic()
        if not math.isfinite(now) or now>=e.deadline:raise Unknown('Serve retirement deadline elapsed')
        self.journal.assert_current();v=self.journal.view;rows=[r for r in v['records'] if r['operation']==e.operation];index=order.index(self.stage)
        if (v['state']!='pending' or v['pending']!=self.stage or v['operation']!=e.operation or v['mode']!=e.scope
                or v['authorization_sha256']!=e.binding_sha256 or v['plan_sha256']!=e.packet_sha256
                or v['root_plan_sha256']!=self.plan.packet_sha256 or (e.scope=='forward' and e.packet_sha256!=self.plan.packet_sha256)
                or [r['step'] for r in rows if r['kind']=='intent']!=list(order[:index+1])
                or [r['step'] for r in rows if r['kind']=='verified']!=list(order[:index])):
            raise Refused('Exact fixed serve retirement journal prefix required')
        if os.getpid()!=self.journal._owner_pid or not self.journal._owns_operation:
            raise Refused('Original live native journal owner required')
        if claimed and (e.operation,self.stage) not in self.journal._dispatch_claims:raise Refused('Serve retirement claim missing')
        self.paths.require('journal',self.journal._directory);self.paths.require('hermes_home',self.spec.home.path)
        self.paths.require(self.spec.source_role,self.spec.sdk.root.path);self.paths.require('executor',self.spec.executor.root.path)
        if self.spec.owned_root is None:
            if self.paths.scope!='production_review_only':raise Refused('Production retirement requires real-root operational ancestry')
            self.paths.contains('executor',Path(__file__).resolve())
        elif self.spec.owned_root!=self.paths.ancestry.root:raise Refused('Owned retirement root differs')
        if anchor(self.spec.home.path)!=self.spec.home:raise Refused('Exact serve home changed')
        scope=self.manager.execution_scope(e.operation,owned_fixture=self.spec.owned_root is not None)
        scope.require_window(e,read_process_owner(os.getpid()),now)
        if scope.configuration_sha256!=self.spec.scope_configuration_sha256:raise Refused('Native controller configuration differs')
        return scope

    def _inputs(self):
        return dict(sdk=self.spec.sdk.observe(),executor=self.spec.executor.observe())

    def _current(self):
        before=self.spec.before;binding=self.manager.bindings['serve']
        expected_configuration=binding.old_configuration_sha256 if self.spec.source_role=='old_source' else binding.new_configuration_sha256
        expected_executable=binding.old_executable if self.spec.source_role=='old_source' else binding.new_executable
        if before.unit!=binding.unit or before.configuration_sha256!=expected_configuration:
            raise Refused('Reviewed serve binding differs from the selected source role')
        current=self.manager.snapshot('serve')
        if current!=before or current.control_pid!=0 or current.job_id!=0 or current.job_path!='/' or current.load_state!='loaded':
            raise Unknown('Exact native serve incarnation is unproven')
        if current.owner is None:
            if current.active_state!='inactive' or current.sub_state!='dead' or current.cgroup:
                raise Unknown('Native serve absence is unproven')
        elif (current.active_state!='active' or current.sub_state!='running' or current.executable!=expected_executable
                or read_process_owner(current.main_pid)!=current.owner):
            raise Unknown('Native serve owner/executable is unproven')
        return current

    def _socket(self):
        t=self.transport;owner=self.spec.before.owner
        if (type(t) is not PinnedRetirementHttp or t._owner!=owner or type(t._observe) is not LinuxSocketOwner
                or t._observe._expected!=owner or t._observe._uid!=os.geteuid()
                or not t._usable or t._connection.sock is not t._socket
                or t._socket.getpeername()!=('127.0.0.1',self.spec.port) or t._observe(t._socket)!=owner):
            raise Refused('Exact kernel-owner-verified single serve connection required')
        t._verify_owner()
        return owner

    def _event(self,receipt,event):
        allowed={'step','phase','pid','start_ticks','boot_id','http_status'}
        if (type(event) is not dict or set(event)-allowed or event.get('step') not in ('identity','idle','prepare','commit','cancel')
                or event.get('phase') not in ('intent','response','refused','verified')
                or {k:event.get(k) for k in ('pid','start_ticks','boot_id')}!=asdict(self.spec.before.owner)):
            raise Unknown('Closed credential-free retirement event required')
        if 'http_status' in event and event['http_status'] is not None and type(event['http_status']) is not int:
            raise Unknown('Invalid retirement HTTP observation')
        receipt.append('serve_protocol',event);self.protocol_events.append(dict(event))

    def run(self,e):
        if self.used:raise Refused('Serve retirement actor is spent')
        receipt=None;claimed=False;checkpoint='preflight'
        try:
            self._authorize(e);inputs=self._inputs();before=self._current()
            if before.owner is not None:self._socket()
            elif self.stage!='rollback_serve_retirement':raise Refused('Forward retirement requires its actual running serve owner')
            self.journal.claim_dispatch(e.operation,self.stage,e.binding_sha256);claimed=True;self.used=True;self.state='claimed'
            receipt=_Receipt(self.journal,e,self.stage,self.spec.sha256);self.last_receipt=receipt.name
            receipt.append('serve_preimage',dict(spec_sha256=self.spec.sha256,snapshot=asdict(before),inputs=inputs))
            if before.owner is None:
                checkpoint='absence';self._authorize(e,claimed=True);self._current()
                if self._inputs()!=inputs:raise Unknown('Reviewed retirement inputs changed')
                outcome='verified_absent'
            else:
                checkpoint='identity_idle'
                # Owner observations remain native; every request itself is also
                # pinned to this same socket. Token/permit values never enter events.
                def owner():
                    self._authorize(e,claimed=True);self._current();return self._socket()
                guard=RetirementGuard(before.owner,self.transport,owner,lambda event:self._event(receipt,event))
                idle=guard.inspect()
                if idle is not True:
                    self.state='held';receipt.append('serve_held',dict(checkpoint=checkpoint,idle=idle,mutation_requested=False))
                    return self._result(receipt,False,'diagnostic_not_idle')
                checkpoint='prepare';permit=guard.prepare()
                if permit is None:
                    self.state='held';receipt.append('serve_held',dict(checkpoint=checkpoint,idle=guard.idle,mutation_requested=True))
                    return self._result(receipt,False,'prepare_not_idle')
                checkpoint='commit';guard.commit(permit)
                if guard.state!='committed':raise Unknown('Serve retirement commit is unproven')
                checkpoint='observation';self._authorize(e,claimed=True);self._current();self._socket()
                if self._inputs()!=inputs:raise Unknown('Reviewed retirement inputs changed after commit')
                outcome='source_bound_cooperative_commit_confirmed'
            self.state='effects_observed'
            receipt.append('serve_effect_observed',dict(outcome=outcome,snapshot_sha256=before.sha256,checks={STAGES[self.stage]:True},
                protocol_requests=sum(row['phase']=='intent' for row in self.protocol_events),service_action=False,automatic_cancel=False))
            return self._result(receipt,True,outcome)
        except BaseException as error:
            if claimed:
                self.state='unknown'
                if receipt is not None:
                    try:receipt.append('serve_observation_held',dict(checkpoint=checkpoint,error_type=type(error).__name__))
                    except Exception:pass
                if not isinstance(error,Exception):raise
                raise Unknown('Serve outcome uncertain; inspect the original owner/socket/receipt without replay') from None
            if not isinstance(error,Exception) or isinstance(error,(Refused,Unknown)):raise
            raise Refused('Serve retirement preflight refused without HTTP dispatch') from None
        finally:
            if self.transport is not None:self.transport.close()
            if receipt is not None:receipt.close()

    def _result(self,receipt,verified,outcome):
        return dict(state=self.state,stage=self.stage,outcome=outcome,spec_sha256=self.spec.sha256,
            receipt=receipt.name,receipt_sha256=hashlib.sha256(receipt.data).hexdigest(),
            proof=dict(checks={STAGES[self.stage]:verified},owned_scope=self.spec.owned_root is not None,
                source_bound_protocol=True,independent_admission_probe=False,service_action=False,
                protocol_requests=sum(row['phase']=='intent' for row in self.protocol_events)))
