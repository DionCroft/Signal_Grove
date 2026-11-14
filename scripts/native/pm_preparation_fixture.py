"""Real supported PM sync in one fresh, finite, owned acceptance unit.

Backup/preflight/old-PM rows are explicitly synthetic. Only the candidate's PM
stage is exercised in a separate private home. No canonical home, live service,
gateway/model, credential, policy, grant or project operation is performed.
"""
from dataclasses import asdict
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time

from cutover_coordinator import Plan,ApprovedExecution,FORWARD
from cutover_journal import CutoverJournal
from native_file_publication import anchor,digest
from native_service_manager import NativeServiceManager,UnitBinding
from native_pm_selection import PmSelectionContext,INPUTS
from native_pm_preparation import NativePmPreparation,PmPreparationSpec,PROTECTED


def run(root,operation,authority,source,source_files,store,owner_home,api_python,api_sha256,scope_configuration_sha256,approval_receipt_sha256):
    root,source,store,owner_home=map(Path,(root,source,store,owner_home))
    if (root.name!='signal-grove-pm-'+operation or anchor(root).mode!=0o700 or anchor(root).uid!=os.geteuid()
            or source.parent!=owner_home or store!=owner_home/'tools' or source.resolve()!=source
            or (root/'data').exists() or (root/'ledger').exists()):
        raise ValueError('Fresh private owned PM fixture with a reviewed owner/source/store required')
    data=root/'data';ledger=root/'ledger';data.mkdir(mode=0o700);ledger.mkdir(mode=0o700)
    sentinel=data/'retained-fixture-data';sentinel.write_bytes(b'preserve-owned-pm-fixture-data')
    selection=PmSelectionContext(str(source),str(data),str(store),str(owner_home),tuple((name,dict(source_files)[name]) for name in INPUTS),None)
    spec=PmPreparationSpec(selection,tuple(source_files),tuple((n,None) for n in PROTECTED),scope_configuration_sha256,anchor(data),str(api_python),api_sha256,str(root))
    plan=Plan(digest(dict(scope='owned_real_pm_component_only',spec=spec.sha256)),digest(asdict(authority)),digest(dict(scope='synthetic_non_pm_prerequisites')),digest(dict(scope='separate_recovery_required')))
    bindings=tuple(UnitBinding(role,'hermes-'+role+'.service','e'*64,'e'*64,'/fixture/not-dispatched','/fixture/not-dispatched') for role in ('gateway','serve'))
    with CutoverJournal(ledger,expected_identity=(ledger.stat().st_dev,ledger.stat().st_ino)) as journal, NativeServiceManager(authority,plan,journal,bindings) as manager:
        scope=manager.execution_scope(operation,owned_fixture=True)
        if scope.configuration_sha256!=scope_configuration_sha256:raise AssertionError('Owned controller differs from its reviewed command')
        deadline=(scope.active_enter_usec+scope.runtime_max_usec+scope.stop_timeout_usec)/1_000_000+5
        execution=ApprovedExecution(operation,plan.packet_sha256,plan.authority_sha256,'forward',approval_receipt_sha256,deadline)
        journal.begin(operation,plan.packet_sha256,authorization_sha256=execution.binding_sha256)
        for prior in FORWARD[:FORWARD.index('pm_new')]:
            journal.intent(prior);journal.verified(prior,digest(dict(scope='synthetic_non_pm_prerequisite',step=prior)))
        journal.intent('pm_new');actor=NativePmPreparation(plan,journal,manager,'pm_new',spec,spec.sha256);result=actor.run(execution)
        journal.verified('pm_new',digest(result))
        command="import importlib.metadata,json,platform,sys;import contextgraph,yaml;print(json.dumps(dict(machine=platform.machine(),python=sys.version.split()[0],prefix=sys.prefix,contextgraph=importlib.metadata.version('project-contextgraph'),pyyaml=importlib.metadata.version('PyYAML'),contextgraphOrigin=contextgraph.__file__)))"
        env=dict(PATH='/usr/bin:/bin',HOME=str(data),LANG='C.UTF-8',PYTHONDONTWRITEBYTECODE='1',TMPDIR=str(data/'cache/pm-operation-scratch'))
        native=subprocess.run([result['selected_python'],'-I','-B','-c',command],cwd=root,env=env,capture_output=True,timeout=45)
        (root/'abi.stdout').write_bytes(native.stdout);(root/'abi.stderr').write_bytes(native.stderr)
        if native.returncode!=0:raise AssertionError('Owned PM generation native dependency probe failed')
        abi=json.loads(native.stdout)
        if (abi['machine']!='aarch64' or abi['python']!='3.14.9' or abi['contextgraph']!='0.8.1' or abi['pyyaml']!='6.0.3'
                or Path(abi['prefix'])!=Path(result['committed_environment']) or not Path(abi['contextgraphOrigin']).is_relative_to(Path(abi['prefix']))):
            raise AssertionError('Actual PM native dependency identity differs')
        if sentinel.read_bytes()!=b'preserve-owned-pm-fixture-data':raise AssertionError('Newer owned fixture data changed')
        after=manager.execution_scope(operation,owned_fixture=True)
        if after!=scope:raise AssertionError('Owned maintenance controller incarnation/configuration changed')
        journal.complete(digest(dict(scope='owned_pm_component_only',pm=result,abi=abi,preserved_data_sha256=hashlib.sha256(sentinel.read_bytes()).hexdigest())))
        history=journal.history_sha256
    report=dict(state='OWNED_REAL_PM_PREPARATION_COMPONENT_PASS',scope='owned_candidate_pm_only',operation=operation,spec_sha256=spec.sha256,
        execution_scope=asdict(scope),pm=result,abi=abi,ledger=str(ledger),journal_sha256=history,sentinel=str(sentinel),sentinel_sha256=hashlib.sha256(sentinel.read_bytes()).hexdigest(),
        synthetic_non_pm_prerequisites=True,old_canonical_pm_prepared=False,new_canonical_pm_prepared=False,
        live_services_backups_admission_or_full_cutover_accepted=False,live_changes=False)
    with (root/'fixture-result.json').open('x') as out:json.dump(report,out,indent=2);out.write('\n');out.flush();os.fsync(out.fileno())
    return report
