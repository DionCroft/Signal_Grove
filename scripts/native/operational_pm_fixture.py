"""Actual SDK install under the shared real-root operational boundary.

Only a fresh isolated home is prepared. Non-PM transaction prerequisites are
synthetic; no canonical provisioning, permission or live service action runs.
"""
from dataclasses import asdict
import hashlib
import json
import os
from pathlib import Path
import subprocess

from cutover_coordinator import Plan,ApprovedExecution,FORWARD
from cutover_journal import CutoverJournal
from native_file_publication import anchor,digest
from native_operational_paths import OperationalDirectories,NativeOperationalPaths,ROLES
from native_service_manager import NativeServiceManager,UnitBinding
from native_pm_selection import PmSelectionContext,INPUTS
from native_pm_preparation import NativePmPreparation,PmPreparationSpec,PROTECTED
from rendered_publication_catalog import capture_native


def run(root,operation,authority,definition,source,source_files,store,owner_home,api_python,api_sha256,scope_configuration_sha256,approval_receipt_sha256,operational_roots):
    root,source,store,owner_home=map(Path,(root,source,store,owner_home))
    if (root.name!='signal-grove-pm-'+operation or anchor(root).mode!=0o700 or source.parent!=owner_home or store!=owner_home/'tools'
            or definition.scope!='production_review_only' or set(operational_roots)!=set(ROLES)-{'hermes_home','journal','executor','new_source','new_store'}):
        raise ValueError('Exact fresh private PM fixture and closed real operational parents required')
    data=root/'data';ledger=root/'ledger';data.mkdir(mode=0o700);ledger.mkdir(mode=0o700)
    sentinel=data/'retained-fixture-data';sentinel.write_bytes(b'preserve-guarded-pm-fixture-data')
    paths={**operational_roots,'hermes_home':data,'journal':ledger,'executor':root.parent,'new_source':source,'new_store':store}
    catalog=OperationalDirectories(tuple((n,anchor(paths[n])) for n in ROLES));captured=capture_native(definition,definition.sha256)
    publication=captured.capture_ancestry(captured.sha256);guard=NativeOperationalPaths.capture(publication,publication.sha256,catalog,catalog.sha256)
    guard.contains('executor',Path(__file__).resolve().parent)
    selection=PmSelectionContext(str(source),str(data),str(store),str(owner_home),tuple((n,dict(source_files)[n]) for n in INPUTS),None)
    spec=PmPreparationSpec(selection,tuple(source_files),tuple((n,None) for n in PROTECTED),scope_configuration_sha256,anchor(data),str(api_python),api_sha256,str(root))
    plan=Plan(digest(dict(scope='isolated_guarded_pm_only',spec=spec.sha256,operational=guard.sha256)),digest(asdict(authority)),digest(dict(scope='synthetic_non_pm_prerequisites')),digest(dict(scope='separate_recovery_required')))
    bindings=tuple(UnitBinding(role,'hermes-'+role+'.service','e'*64,'e'*64,'/fixture/not-dispatched','/fixture/not-dispatched') for role in ('gateway','serve'))
    with CutoverJournal(ledger,expected_identity=(ledger.stat().st_dev,ledger.stat().st_ino),operational_paths=guard,expected_operational_sha256=guard.sha256) as journal, NativeServiceManager(authority,plan,journal,bindings) as manager:
        scope=manager.execution_scope(operation,owned_fixture=True)
        if scope.configuration_sha256!=scope_configuration_sha256:raise AssertionError('Owned controller command differs')
        execution=ApprovedExecution(operation,plan.packet_sha256,plan.authority_sha256,'forward',approval_receipt_sha256,(scope.active_enter_usec+scope.runtime_max_usec+scope.stop_timeout_usec)/1_000_000+5)
        journal.begin(operation,plan.packet_sha256,authorization_sha256=execution.binding_sha256)
        for step in FORWARD[:FORWARD.index('pm_new')]:journal.intent(step);journal.verified(step,digest(dict(scope='synthetic_non_pm_prerequisite',step=step)))
        journal.intent('pm_new');actor=NativePmPreparation(plan,journal,manager,'pm_new',spec,spec.sha256,operational_paths=guard,expected_operational_sha256=guard.sha256);result=actor.run(execution)
        journal.verified('pm_new',digest(result))
        code="import importlib.metadata,json,platform,sys;import contextgraph,yaml;print(json.dumps(dict(machine=platform.machine(),python=sys.version.split()[0],prefix=sys.prefix,contextgraph=importlib.metadata.version('project-contextgraph'),pyyaml=importlib.metadata.version('PyYAML'),contextgraphOrigin=contextgraph.__file__)))"
        p=subprocess.run([result['selected_python'],'-I','-B','-c',code],cwd=root,env=dict(PATH='/usr/bin:/bin',HOME=str(data),LANG='C.UTF-8',PYTHONDONTWRITEBYTECODE='1',TMPDIR=str(data/'cache/pm-operation-scratch')),capture_output=True,timeout=45)
        (root/'abi.stdout').write_bytes(p.stdout);(root/'abi.stderr').write_bytes(p.stderr)
        if p.returncode!=0:raise AssertionError('Guarded PM native dependency probe failed')
        abi=json.loads(p.stdout)
        if (abi['machine']!='aarch64' or abi['python']!='3.14.9' or abi['contextgraph']!='0.8.1' or abi['pyyaml']!='6.0.3'
                or Path(abi['prefix'])!=Path(result['committed_environment']) or not Path(abi['contextgraphOrigin']).is_relative_to(Path(abi['prefix']))):raise AssertionError('Guarded PM dependency identity differs')
        if sentinel.read_bytes()!=b'preserve-guarded-pm-fixture-data' or manager.execution_scope(operation,owned_fixture=True)!=scope:raise AssertionError('Guarded PM retained state/controller changed')
        after=guard.metadata();journal.complete(digest(dict(scope='isolated_guarded_pm_only',pm=result,abi=abi,operational=after['state_sha256'])));history=journal.history_sha256
    report=dict(state='OWNED_GUARDED_REAL_PM_COMPONENT_PASS',operation=operation,pid=os.getpid(),scope='fresh_isolated_home_only',pm=result,abi=abi,operational=after,ledger=str(ledger),journal_sha256=history,
        sentinel_sha256=hashlib.sha256(sentinel.read_bytes()).hexdigest(),synthetic_non_pm_prerequisites=True,canonical_homes_prepared=False,named_modes_changed=False,live_changes=False,full_cutover_accepted=False)
    with (root/'fixture-result.json').open('x') as out:json.dump(report,out,indent=2);out.write('\n');out.flush();os.fsync(out.fileno())
    return report
