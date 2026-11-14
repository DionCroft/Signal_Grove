"""Exercise only two already-created, owned acceptance services.

No live Hermes names, new unit/configuration creation, backups, PM, admission
fences, policy changes or rollout acceptance are supplied by this fixture.
Non-service prerequisites are explicit synthetic fixture journal rows. An error
preserves unfinished state and closes the connection; it never retries/cancels
or stops an uncertain service. Setup must give owned payloads a finite lifetime.
"""
from __future__ import annotations

from dataclasses import asdict
import hashlib
import json
import os
from pathlib import Path
import stat
import time
import uuid

from cutover_coordinator import ApprovedExecution, FORWARD
from cutover_journal import CutoverJournal, Refused
from native_service_manager import NativeServiceManager, ACCEPTANCE


def digest(value):
    return hashlib.sha256(json.dumps(value,sort_keys=True,separators=(',',':')).encode()).hexdigest()


def run(home,authority,plan,bindings):
    home=Path(home)
    groups=[ACCEPTANCE.fullmatch(b.unit) for b in bindings]
    if (len(groups)!=2 or not all(groups) or groups[0][1]!=groups[1][1]
            or any(b.old_configuration_sha256!=b.new_configuration_sha256 or b.old_executable!=b.new_executable for b in bindings)
            or home.resolve()!=home or home.stat().st_uid!=os.geteuid() or stat.S_IMODE(home.stat().st_mode)!=0o700
            or (home/'journal.jsonl').exists() or (home/'cutover.lock').exists()):
        raise ValueError('Fresh private fixture and two same-generation owned acceptance units required')
    identity=(home.stat().st_dev,home.stat().st_ino); receipts=[]; snapshots={}; service_steps={'stop_gateway','stop_serve','start_gateway','start_serve'}
    with CutoverJournal(home,expected_identity=identity) as journal, NativeServiceManager(authority,plan,journal,bindings) as manager:
        # Connection, native peer and signal subscription are established before setup starts units.
        # The external controller creates the already-reviewed transient unit definitions on this callback-free boundary.
        yield dict(state='PINNED_NATIVE_MANAGER_CONNECTED',manager=manager,journal=journal)
        for binding in bindings:
            snapshot=manager.snapshot(binding.role)
            if snapshot.configuration_sha256!=binding.old_configuration_sha256 or snapshot.owner is None:
                raise AssertionError('Owned loaded service differs from independent setup definition')
            snapshots['initial_'+binding.role]=asdict(snapshot)
        for cycle,steps in [('lifecycle',('stop_gateway','stop_serve','start_gateway','start_serve')),
                            ('explicit_owned_cleanup',('stop_gateway','stop_serve'))]:
            execution=ApprovedExecution(uuid.uuid4().hex,plan.packet_sha256,plan.authority_sha256,'forward',
                digest(dict(scope='explicit_owned_fixture_only',cycle=cycle)),time.monotonic()+180)
            journal.begin(execution.operation,execution.packet_sha256,authorization_sha256=execution.binding_sha256);cursor=0
            for step in steps:
                target=FORWARD.index(step)
                for prior in FORWARD[cursor:target]:
                    if prior in service_steps:raise AssertionError('An actual owned service action was skipped')
                    journal.intent(prior);journal.verified(prior,digest(dict(scope='synthetic_non_service_prerequisite',step=prior)))
                cursor=target+1;journal.intent(step);action,role=step.split('_',1);before=manager.snapshot(role)
                receipt=manager.change(role,action,before,execution)
                try:
                    manager.change(role,action,before,execution)
                except Refused:
                    pass
                else:
                    raise AssertionError('Spent native action was dispatched twice')
                journal.verified(step,digest(receipt));receipts.append(dict(cycle=cycle,**receipt))
                snapshots[cycle+'_'+step]=asdict(manager.snapshot(role))
            if cycle=='lifecycle':
                for prior in FORWARD[cursor:]:
                    if prior in service_steps:raise AssertionError('Actual service action missing at completion')
                    journal.intent(prior);journal.verified(prior,digest(dict(scope='synthetic_non_service_prerequisite',step=prior)))
            journal.complete(digest(dict(scope='owned_service_component_only',cycle=cycle,receipts=receipts)))
        if len(receipts)!=6 or len({r['job_path'] for r in receipts})!=6:
            raise AssertionError('Six distinct correlated native jobs required')
        for role in ('gateway','serve'):
            if snapshots['initial_'+role]['owner']==snapshots['lifecycle_start_'+role]['owner']:
                raise AssertionError('Owned native incarnation did not change after start')
        yield dict(state='OWNED_NATIVE_SERVICE_MANAGER_COMPONENT_PASS',scope='owned_service_component_only',
                   correlatedJobs=6,spentActionsRefused=6,snapshots=snapshots,receipts=receipts,
                   journalSha256=journal.history_sha256,syntheticNonServicePrerequisites=True,
                   actualBackupsPmAdmissionOrLiveCutoverAccepted=False)
