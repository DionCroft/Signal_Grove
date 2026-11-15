"""Actual staged SDK drain through one original-journal owned controller.

Prerequisite journal stages are synthetic fixture records. The gateway, source,
PM interpreter, native manager, SQLite ledger, freeze and marker are real. This
is one registered-home component, never a unified cutover or live deployment.
"""
from contextlib import closing
from dataclasses import asdict
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import sqlite3
import stat
import sys
import time
import traceback

SCRIPTS=Path(__file__).resolve().parents[1]


def load(name,path):
    spec=importlib.util.spec_from_file_location(name,path)
    module=importlib.util.module_from_spec(spec);sys.modules[name]=module
    spec.loader.exec_module(module);return module


def write_new(path,value):
    with Path(path).open('x',encoding='utf-8') as out:
        json.dump(value,out,indent=2);out.write('\n');out.flush();os.fsync(out.fileno())


def copy_source(source,target,files):
    """Only a new owned fixture receives reviewed control-plane source bytes."""
    from backup_sdk_snapshot import SourceInventory
    from native_file_publication import anchor
    source,target=Path(source),Path(target)
    inventory=SourceInventory(anchor(source),tuple(sorted(files.items())))
    before=inventory.observe()
    if any(target.iterdir()):raise ValueError('Empty owned source destination required')
    for name,h in inventory.files:
        original=source/name;data=original.read_bytes();observed=original.stat()
        if hashlib.sha256(data).hexdigest()!=h:raise ValueError('Reviewed source changed during copy')
        p=target/name;p.parent.mkdir(mode=0o700,parents=True,exist_ok=True)
        with p.open('xb') as out:out.write(data)
        p.chmod(stat.S_IMODE(observed.st_mode))
    if inventory.observe()!=before:raise ValueError('Original source identity changed during copy')
    copied=SourceInventory(anchor(target),inventory.files);copied.observe();return copied


def run_actor_trial(parent,source,python,source_files,authority,operation):
    if sys.platform!='linux' or not sys.flags.isolated or not sys.dont_write_bytecode or os.geteuid()==0:
        raise ValueError('Native isolated SDK PM controller required')
    lifecycle=load('sdk_gateway_lifecycle_fixture',SCRIPTS/'native/sdk_gateway_lifecycle_fixture.py')
    operation=lifecycle.checked_trial_operation(operation)
    helper=load('operational_paths_fixture',SCRIPTS/'native/operational_paths_fixture.py')
    fixture=helper.build(parent);root=fixture['root'];paths=fixture['paths'];core=fixture['modules']
    for name in ('native_service_manager','native_gateway_control','backup_sdk_snapshot','native_verified_inventory','native_gateway_drain'):
        load(name,SCRIPTS/(name+'.py'))
    actor_module=sys.modules['native_gateway_drain'];service=sys.modules['native_service_manager']
    sdk=copy_source(source,paths['old_source'],source_files)
    executor_hashes={}
    for name in sorted(actor_module.EXECUTOR_REQUIRED):
        data=(SCRIPTS/name).read_bytes();p=paths['executor']/name
        with p.open('xb') as out:out.write(data)
        p.chmod(0o600);executor_hashes[name]=hashlib.sha256(data).hexdigest()
        loaded=sys.modules[name.removesuffix('.py')]
        if Path(loaded.__file__).resolve()!=SCRIPTS/name:raise ValueError('Reviewed executor module origin differs')
    executor=actor_module.SourceInventory(actor_module.anchor(paths['executor']),tuple(sorted(executor_hashes.items())))
    write_new(root/'actor-fixture-inputs.json',dict(operation=operation,sdk_inventory_sha256=sdk.sha256,
        sdk_files=len(sdk.files),executor_hashes=executor_hashes,source_copy=True,
        prior_journal_stages_synthetic=True,live_changes=False))
    callback_count=0;held_managers=[]
    def drain(context):
        nonlocal callback_count
        callback_count+=1
        if callback_count!=1 or context['operation']!=operation or Path(context['source'])!=Path(sdk.root.path):
            raise ValueError('One original owned SDK drain callback required')
        home=Path(context['root'])/'hermes-home'
        native=core['native_file_publication'];ops=core['native_operational_paths']
        selected={**paths,'hermes_home':home}
        catalog=ops.OperationalDirectories(tuple((role,native.anchor(selected[role])) for role in ops.ROLES))
        guard=ops.NativeOperationalPaths.capture(fixture['publication'],fixture['publication'].sha256,catalog,catalog.sha256)
        ledger_dir=paths['journal']
        journal=core['cutover_journal'].CutoverJournal(ledger_dir,
            expected_identity=(ledger_dir.stat().st_dev,ledger_dir.stat().st_ino),
            operational_paths=guard,expected_operational_sha256=guard.sha256)
        manager=None;actor=None
        try:
            coordinator=core['cutover_coordinator'];plan=coordinator.Plan('a'*64,'b'*64,'c'*64,'d'*64)
            Owner=sys.modules['retirement_guard'].Owner
            bus=service.BusAuthority(authority['uid'],authority['busIdentity'][0],authority['busIdentity'][1],
                Owner(**authority['peerOwner']),authority['managerUnique'],Owner(**authority['managerOwner']))
            unit=context['unit'];executable=str(Path(python).resolve())
            configuration=native.digest(dict(unit=unit,fragment='/run/user/'+str(os.geteuid())+'/systemd/transient/'+unit,
                dropins=[],kill_mode='control-group',service_type='exec',
                commands={'ExecStart':((str(python),tuple(context['argv']),False),),'ExecStop':(),'ExecStopPost':()}))
            bindings=tuple(service.UnitBinding(role,'signal-grove-acceptance-'+operation+'-'+role+'.service',
                configuration,configuration,executable,executable) for role in ('gateway','serve'))
            manager=service.NativeServiceManager(bus,plan,journal,bindings)
            manager._manager('RefUnit','s',(unit,),'')
            before=manager.snapshot('gateway')
            if before.configuration_sha256!=configuration or asdict(before.owner)!=context['owner']:
                raise ValueError('Actual SDK gateway native identity differs')
            scope=manager.execution_scope(operation,owned_fixture=True)
            execution=coordinator.ApprovedExecution(operation,plan.packet_sha256,plan.authority_sha256,'forward','e'*64,
                (scope.active_enter_usec+scope.runtime_max_usec+scope.stop_timeout_usec)/1e6+5)
            db_path=home/'project-control.db'
            with closing(sqlite3.connect(db_path.as_uri()+'?mode=ro',uri=True,timeout=.75)) as db:
                db.execute('PRAGMA query_only=ON');schema=actor_module.schema_digest(db)
            spec=actor_module.GatewayDrainSpec(sdk,executor,actor_module.anchor(home),before,context['socket'],
                actor_module.FencePreimage(0,None),actor_module.ledger_stamp(db_path.stat()),schema,
                str(Path(sys.prefix)),scope.configuration_sha256,'old_source',str(root))
            write_new(root/'actor-native-inputs.json',dict(spec_sha256=spec.sha256,native_gateway=asdict(before),
                native_scope=asdict(scope),operational_paths=guard.metadata(),ledger_schema_sha256=schema,
                prior_journal_stages_synthetic=True,approval_deadline=execution.deadline))
            journal.begin(operation,plan.packet_sha256,authorization_sha256=execution.binding_sha256)
            for step in coordinator.FORWARD[:coordinator.FORWARD.index('gateway_drain')]:
                journal.intent(step);journal.verified(step,'f'*64)
            journal.intent('gateway_drain')
            os.environ['HERMES_HOME']=str(home);sys.path.insert(0,sdk.root.path)
            actor=actor_module.NativeGatewayDrain(plan,journal,manager,guard,spec,spec.sha256)
            prepared=actor.prepare_inputs(execution)
            write_new(root/'actor-prepared.json',prepared)
            report=actor.run(execution)
            if report['state']!='effects_observed' or actor.freeze_calls!=1 or not actor.used:
                raise RuntimeError('Actual original-journal actor effects are unproven')
            if manager.snapshot('gateway')!=before or journal.view['pending']!='gateway_drain':
                raise RuntimeError('Actor changed service identity or finalized a broader stage')
            try:actor.run(execution)
            except core['cutover_journal'].Refused:pass
            else:raise RuntimeError('Spent actual SDK drain was replayed')
            write_new(root/'actor-result.json',dict(result=report,prepared=prepared,
                freeze_calls=actor.freeze_calls,protocol_requests=actor.requests,replay_refused=True,
                journal_pending=journal.view['pending'],native_gateway_unchanged=True,
                original_journal_registered_home_accepted=True,full_executor_accepted=False,live_changes=False))
            # Preserve the manager's RefUnit across the caller's normal stop.
            held_managers.append(manager);manager=None
            return dict(phase='original-journal-actor',result=report,freeze_calls=actor.freeze_calls,
                replay_refused=True,prior_journal_stages_synthetic=True,live_changes=False)
        except BaseException as error:
            write_new(root/'actor-held.json',dict(error_type=type(error).__name__,actor_state=actor.state if actor else None,
                freeze_calls=actor.freeze_calls if actor else 0,protocol_requests=actor.requests if actor else 0,
                receipt=actor.last_receipt if actor else None,journal_pending=journal.view['pending'],
                observed_monotonic=time.monotonic(),error_frames=[dict(file=Path(x.filename).name,line=x.lineno,function=x.name)
                    for x in traceback.extract_tb((error.__context__ or error).__traceback__)[-8:]],
                no_replay=True,live_changes=False))
            raise
        finally:
            try:
                if manager is not None:manager.close()
            finally:journal.close()
    try:
        result=lifecycle.run_trial(root/'peers',Path(sdk.root.path),python,observer_mode='profile',
            operation=operation,drain_actor=drain)
        if callback_count!=1 or len(held_managers)!=1:raise RuntimeError('Actual SDK actor callback count differs')
        terminal=held_managers[0].snapshot('gateway')
        if terminal.owner is not None or terminal.main_pid or terminal.control_pid or terminal.cgroup or terminal.result!='success':
            raise RuntimeError('Actual SDK native terminal identity is unproven')
        write_new(root/'actor-native-terminal.json',asdict(terminal))
    finally:
        for held in held_managers:held.close()
    report=dict(state='ACTUAL_SDK_ORIGINAL_JOURNAL_REGISTERED_HOME_DRAIN_VERIFIED',
        fixture_root=str(root),gateway_trial=result,callback_count=callback_count,
        prior_journal_stages_synthetic=True,all_profile_worker_admission_accepted=False,
        full_executor_accepted=False,production_release_accepted=False,live_changes=False)
    write_new(root/'actor-trial-result.json',report);return report
