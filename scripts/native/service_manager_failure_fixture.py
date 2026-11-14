"""Actual owned-unit errors and replay refusal, never a live cutover.

The external controller creates one exact finite-lifetime gateway fixture after
the first yield. Both bindings must be owned acceptance names in that operation.
All non-service prerequisite rows are explicitly synthetic. No retry, cancel,
reset or compensating service command is sent, including after uncertainty.
"""
from __future__ import annotations

from contextlib import ExitStack
from dataclasses import asdict
import hashlib
import json
import os
from pathlib import Path
import stat
import time
import uuid

from cutover_coordinator import ApprovedExecution, FORWARD
from cutover_journal import CutoverJournal, Refused, Unknown
from native_service_manager import NativeServiceManager, ACCEPTANCE, JOB, command_rows, _Deadline

SCENARIOS=('replacement_before_effect','reopened_pending','stop_helper_failure','post_helper_failure','late_completion')


def digest(value):return hashlib.sha256(json.dumps(value,sort_keys=True,separators=(',',':')).encode()).hexdigest()


def run(home,authority,plan,bindings,scenario):
    home=Path(home);groups=[ACCEPTANCE.fullmatch(b.unit) for b in bindings]
    if (scenario not in SCENARIOS or len(groups)!=2 or not all(groups) or groups[0][1]!=groups[1][1]
            or {b.role for b in bindings}!={'gateway','serve'}
            or any(b.old_configuration_sha256!=b.new_configuration_sha256 or b.old_executable!=b.new_executable for b in bindings)
            or home.resolve()!=home or home.stat().st_uid!=os.geteuid() or stat.S_IMODE(home.stat().st_mode)!=0o700
            or (home/'journal.jsonl').exists() or (home/'cutover.lock').exists()):
        raise ValueError('Fresh private failure fixture and exact owned bindings required')
    identity=(home.stat().st_dev,home.stat().st_ino);binding=next(b for b in bindings if b.role=='gateway')
    def record(phase,data):
        line=(json.dumps(dict(phase=phase,at=time.monotonic(),data=data),sort_keys=True,separators=(',',':'))+'\n').encode()
        if len(line)>65536:raise AssertionError('Owned diagnostic record exceeds bound')
        with (home/'native-observations.jsonl').open('ab') as output:
            output.write(line);output.flush();os.fsync(output.fileno())
    with ExitStack() as stack:
        journal=stack.enter_context(CutoverJournal(home,expected_identity=identity))
        actor=stack.enter_context(NativeServiceManager(authority,plan,journal,bindings))
        observer=stack.enter_context(NativeServiceManager(authority,plan,journal,bindings))
        yield dict(state='PINNED_NATIVE_FAILURE_CONNECTIONS_READY',scenario=scenario)
        before=actor.snapshot('gateway')
        if before.owner is None or before.configuration_sha256!=binding.old_configuration_sha256:
            raise AssertionError('Owned failure fixture preimage differs from independent setup')
        # The independent observation connection retains only this owned unit's
        # evidence across actor close and inactive/failed garbage collection.
        observer._manager('RefUnit','s',(binding.unit,),'')
        record('native_preimage',asdict(before))
        execution=ApprovedExecution(uuid.uuid4().hex,plan.packet_sha256,plan.authority_sha256,'forward',
            digest(dict(scope='owned_failure_fixture_only',scenario=scenario)),time.monotonic()+(8 if scenario=='late_completion' else 60))
        journal.begin(execution.operation,execution.packet_sha256,authorization_sha256=execution.binding_sha256)
        for prior in FORWARD[:FORWARD.index('stop_gateway')]:
            journal.intent(prior);journal.verified(prior,digest(dict(scope='synthetic_non_service_prerequisite',step=prior)))
        journal.intent('stop_gateway');pending_sha=journal.history_sha256;observer._signals.clear()
        report=dict(scenario=scenario,scope='owned_service_errors_only',before=asdict(before),syntheticNonServicePrerequisites=True,
                    actualBackupsPmAdmissionOrLiveCutoverAccepted=False,serviceDispatches=0)
        if scenario in ('replacement_before_effect','reopened_pending'):
            if scenario=='replacement_before_effect':journal.claim_dispatch(execution.operation,'stop_gateway',execution.binding_sha256)
            actor.close()
            if scenario=='reopened_pending':
                journal.close();journal=stack.enter_context(CutoverJournal(home,expected_identity=identity))
            replacement=stack.enter_context(NativeServiceManager(authority,plan,journal,bindings))
            try:replacement.change('gateway','stop',before,execution)
            except Refused:pass
            else:raise AssertionError('An unfinished owned intent was replayed')
            if replacement.last_attempt is not None:raise AssertionError('Replay refusal acquired dispatch authority')
            unchanged=observer.snapshot('gateway')
            if unchanged!=before or list(home.glob('gateway-stop-*.json')) or list(home.glob('gateway-post-*.json')):
                raise AssertionError('An owned service effect occurred during replay refusal')
            report.update(refusedBeforeEffect=True,unchangedAfterRefusal=asdict(unchanged),lastAttempt=None)
            record('native_replay_refused',dict(scenario=scenario,snapshot=asdict(unchanged)))
            # Finite payload expires naturally; there is no cleanup StopUnit.
            deadline=time.monotonic()+30;path=observer._manager('GetUnit','s',(binding.unit,),'o')[0]
            while True:
                observer._remaining(deadline)
                # A full snapshot correctly refuses a changing incarnation. Wait
                # on bounded native MainPID/ControlPID plus the exact owned
                # completed-helper files instead of polling full snapshots
                # across expected natural exit. No failed connection is retried.
                main=observer._get(path,'org.freedesktop.systemd1.Service','MainPID','u',deadline)
                control=observer._get(path,'org.freedesktop.systemd1.Service','ControlPID','u',deadline)
                if (not main and not control and len(list(home.glob('gateway-stop-*.json')))==1
                        and len(list(home.glob('gateway-post-*.json')))==1):break
                time.sleep(.1)
            after=observer.snapshot('gateway');record('native_natural_terminal',asdict(after))
            if after.result!='success' or not after.stop_helpers_ok:raise AssertionError('Natural owned cleanup failed')
            report['terminalAfterNaturalExpiry']=asdict(after)
        else:
            try:actor.change('gateway','stop',before,execution)
            except Unknown:pass
            else:raise AssertionError('Failed/late native action falsely reported success')
            unknown_at=time.monotonic();attempt=actor.last_attempt
            record('native_actor_unknown',dict(state=actor.state,attempt=asdict(attempt) if attempt else None))
            if actor.state!='unknown' or attempt is None or not attempt.job_path:raise AssertionError('Unknown action lost exact job evidence')
            if attempt.phase not in ('accepted','job_done') or (scenario=='late_completion' and attempt.phase!='accepted'):
                raise AssertionError('ACK and exact job completion were conflated')
            attempt_sha=attempt.sha256;report.update(serviceDispatches=1,unknownAt=unknown_at,lastAttempt=asdict(attempt),lastAttemptSha256=attempt_sha)
            if scenario=='late_completion':
                during=observer.snapshot('gateway')
                if during.active_state!='deactivating' or not during.control_pid or during.owner!=before.owner:
                    raise AssertionError('Late owned job was not independently still running at uncertainty')
                report['stillRunningAfterDeadline']=asdict(during)
            deadline=time.monotonic()+40;signal=None
            while signal is None:
                observer._native();observer._remaining(deadline)
                while len(observer._signals):
                    message=observer._signals.popleft()
                    if message.header.fields.get(observer._Fields.signature)!='uoss':raise AssertionError('Native job signature changed')
                    body=message.body
                    if (type(body) is not tuple or len(body)!=4 or type(body[0]) is not int or not 0<body[0]<2**32
                            or type(body[1]) is not str or not JOB.fullmatch(body[1]) or int(JOB.fullmatch(body[1])[1])!=body[0]
                            or type(body[2]) is not str or type(body[3]) is not str):
                        raise AssertionError('Independent native job body is invalid')
                    if body[1]!=attempt.job_path:continue
                    allowed=('done',) if scenario=='late_completion' else ('done','failed')
                    if body[2]!=binding.unit or body[3] not in allowed:raise AssertionError('Injected native job has a different outcome')
                    signal=body;record('independent_exact_job_removed',list(body));break
                if signal is None:
                    with _Deadline(observer._raw,deadline):observer._conn.recv_messages(timeout=observer._remaining(deadline))
            observed_at=time.monotonic();after=observer.snapshot('gateway');record('independent_native_terminal',asdict(after))
            wanted_phase='job_done' if signal[3]=='done' and scenario!='late_completion' else 'accepted'
            if attempt.phase!=wanted_phase:raise AssertionError('Actor attempt phase disagrees with the independently observed job outcome')
            if after.main_pid or after.control_pid or after.job_id:raise AssertionError('Owned native stop is not terminal')
            if scenario=='late_completion':
                if after.active_state!='inactive' or after.result!='success' or not after.stop_helpers_ok:
                    raise AssertionError('Late job failed instead of completing after the caller deadline')
            elif after.result!='exit-code' or after.stop_helpers_ok:
                raise AssertionError('The failed native helper was not reflected in unit state')
            path=observer._manager('GetUnit','s',(binding.unit,),'o')[0];helpers={}
            for name in ('ExecStop','ExecStopPost'):
                rows=observer._get(path,'org.freedesktop.systemd1.Service',name,'a(sasbttttuii)',time.monotonic()+3)
                command_rows(rows)
                if len(rows)!=1:raise AssertionError('One exact owned helper per phase required')
                row=rows[0];helpers[name]=dict(pid=row[7],exitCode=row[8],exitStatus=row[9],startMonotonicUsec=row[4],endMonotonicUsec=row[6])
                wanted=7 if scenario=='stop_helper_failure' and name=='ExecStop' else 9 if scenario=='post_helper_failure' and name=='ExecStopPost' else 0
                if row[8]!=1 or row[9]!=wanted or not row[4] or not row[6]:raise AssertionError('Native helper status differs from the injected fixture error')
            if actor.last_attempt!=attempt or actor.last_attempt.sha256!=attempt_sha:raise AssertionError('Late inspection retroactively verified the unknown action')
            report.update(independentJobRemoved=list(signal),independentlyObservedAt=observed_at,terminal=asdict(after),nativeHelpers=helpers)
        if journal.history_sha256!=pending_sha or journal.view['state']!='pending' or journal.view['pending']!='stop_gateway':
            raise AssertionError('Failure inspection rewrote or verified an unfinished intent')
        report.update(state='OWNED_NATIVE_FAILURE_SCENARIO_PASS',journalSha256=pending_sha,originalIntentStillPending=True,noAutomaticServiceCompensation=True)
        record('scenario_verified',report)
        yield report
