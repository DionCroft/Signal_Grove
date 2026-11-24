"""Retained owned renderer publication/recovery evidence, never live activation.

All non-file stages are explicit synthetic prerequisites. The SDK launcher and
policy identities are generated fixtures. No service, network, worker, model,
credential, real backup or actual protected-ancestry acceptance is performed.
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

from cutover_coordinator import Plan,ApprovedExecution,FORWARD,RECOVERY
from cutover_journal import CutoverJournal,Refused
from native_file_publication import NativeFilePublication,FileBinding,DirectoryAnchor,anchor,observe,digest,FILE_ROLES
from rendered_publication_catalog import RenderedCatalog,PolicyReview,capture_native


def run(home):
    home=Path(home)
    if home.resolve()!=home or home.stat().st_uid!=os.geteuid() or stat.S_IMODE(home.stat().st_mode)!=0o700:
        raise ValueError('One native private owned evidence directory required')
    if (home/'fixture-result.json').exists() or (home/'ledger').exists():raise ValueError('Fresh owned fixture required; never resume')
    root=home/('signal-grove-catalog-'+uuid.uuid4().hex);root.mkdir(mode=0o700);account=root/'account';fleet=account/'Projects/fleet'
    policy=json.dumps(dict(schema=1,authority='fixture-authority',grants=[dict(subject='fixture-owner',profile_id=str(fleet/'hermes'),device='FIXTURE',project_ids=['fixture'])]),separators=(',',':')).encode()
    launcher=b'#!/bin/sh\nexit 0\n';old_client=b'#!/bin/sh\nprintf owned-original-client\n'
    review=PolicyReview('fixture-authority',('FIXTURE',),hashlib.sha256(policy).hexdigest())
    definition=RenderedCatalog.for_owned_fixture(root,review,policy,hashlib.sha256(launcher).hexdigest());packet=definition.packet()
    commands=Path(packet['command_directory']);commands.mkdir(parents=True,mode=0o700);(commands/'hermes').write_bytes(launcher);(commands/'hermes').chmod(0o755)
    (commands.parent/packet['layout']['source_name']).mkdir(mode=0o700) # Inert source-root scaffold, not SDK source acceptance.
    client=account/'.local/bin';client.mkdir(parents=True,mode=0o700);(client/'hermes').write_bytes(old_client);(client/'hermes').chmod(0o755)
    (account/'.config/systemd/user').mkdir(parents=True,mode=0o700)
    (account/'.local/state').mkdir(mode=0o700)
    for _,path in definition.mode_paths():
        directory=Path(path);directory.mkdir(parents=True,exist_ok=True,mode=0o700);directory.chmod(0o775)
    datafile=fleet/'hermes/newer-history-fixture';datafile.write_bytes(b'newer-owned-data-before-forward')
    captured=capture_native(definition,definition.sha256);snapshot=captured.sha256
    ancestry=captured.capture_ancestry(snapshot);ancestry_hash=ancestry.sha256
    plan=Plan(digest(dict(scope='owned_rendered_files_only',definition=definition.sha256,capture=snapshot,ancestry=ancestry_hash)),
        digest(dict(scope=definition.scope,root=asdict(anchor(root)))),digest(dict(scope='synthetic_non_file_preflight')),digest(dict(scope='explicit_owned_runtime_only_recovery')))
    ledger=home/'ledger';ledger.mkdir(mode=0o700);identity=(ledger.stat().st_dev,ledger.stat().st_ino)
    actual=[];synthetic=[];effects={};created={};receipts=[]
    def seed(journal,execution,stage):
        journal.intent(stage);journal.verified(stage,digest(dict(scope='synthetic_non_file_prerequisite',stage=stage,operation=execution.operation)))
        synthetic.append(dict(operation=execution.operation,stage=stage))
    def verify_known():
        ancestry.check();captured.assert_launcher()
        for role,row in effects.items():
            current=observe(DirectoryAnchor(**row['parent']),role)
            if asdict(current)!=row['image']:raise AssertionError('An earlier owned effect changed before the next stage')
    def perform(journal,execution,stage,bindings):
        verify_known();journal.intent(stage);catalog=digest([b.evidence() for b in bindings])
        actor=NativeFilePublication(plan,journal,stage,bindings,catalog,ancestry=ancestry,expected_ancestry_sha256=ancestry_hash);result=actor.run(execution);observed=[]
        if stage=='secure_modes':
            for b in bindings:
                after=anchor(b.directory.path)
                if after.mode!=0o755 or (after.device,after.inode,after.uid,after.gid)!=(b.directory.device,b.directory.inode,b.directory.uid,b.directory.gid):
                    raise AssertionError('Owned mode effect differs')
                observed.append(dict(role=b.role,directory=asdict(after)))
        else:
            for b in actor.resolved_bindings:
                image=observe(b.parent,b.role)
                if image.kind!=b.kind:raise AssertionError('Owned entry kind differs')
                if b.kind!='absent':
                    raw=b.value if type(b.value) is bytes else b.value.encode()
                    if image.sha256!=hashlib.sha256(raw).hexdigest() or image.mode!=b.mode:raise AssertionError('Owned entry content/mode differs')
                row=dict(role=b.role,parent=asdict(b.parent),image=asdict(image));effects[b.role]=row;observed.append(row)
        receipt=ledger/result['receipt']
        if hashlib.sha256(receipt.read_bytes()).hexdigest()!=result['receipt_sha256']:raise AssertionError('Owned receipt changed')
        rows=[json.loads(line) for line in receipt.read_text().splitlines()]
        for row in rows:
            if row['phase']=='parent_observed':created[row['evidence']['image']['path']]=row['evidence']['image']
        journal.verified(stage,digest(dict(scope='independently_observed_owned_files',observed=observed,receipt_sha256=result['receipt_sha256'])))
        actual.append(dict(operation=execution.operation,stage=stage,observed=observed));receipts.append(result)
        return actor
    with CutoverJournal(ledger,expected_identity=identity) as journal:
        forward=ApprovedExecution(uuid.uuid4().hex,plan.packet_sha256,plan.authority_sha256,'forward',digest(dict(scope='explicit_owned_forward_only')),time.monotonic()+180)
        journal.begin(forward.operation,forward.packet_sha256,authorization_sha256=forward.binding_sha256)
        for stage in FORWARD[:FORWARD.index('secure_modes')]:seed(journal,forward,stage)
        for stage in ('secure_modes','publish_reviewed','install_policy','bind_gateway','bind_serve','bind_cli'):
            perform(journal,forward,stage,captured.for_stage(stage))
        verify_known();journal.unknown();forward_history_sha=journal.history_sha256
        # New data after the forward hold is deliberately preserved through recovery.
        datafile.write_bytes(b'newer-owned-data-after-forward');preserved_data_sha=hashlib.sha256(datafile.read_bytes()).hexdigest()
        snapshot_after=digest(dict(effects=effects,modes=[asdict(anchor(p)) for _,p in definition.mode_paths()]))
        recovery=ApprovedExecution(uuid.uuid4().hex,plan.rollback_packet_sha256,plan.authority_sha256,'recovery',
            digest(dict(scope='explicit_owned_runtime_recovery_only',history=forward_history_sha,snapshot=snapshot_after)),time.monotonic()+180)
        journal.begin_recovery(recovery.operation,recovery.packet_sha256,original_operation=forward.operation,original_plan_sha256=forward.packet_sha256,
            history_sha256=forward_history_sha,evidence_sha256=snapshot_after,authorization_sha256=recovery.binding_sha256)
        for stage in RECOVERY[:RECOVERY.index('rollback_policy')]:seed(journal,recovery,stage)
        originals=dict(captured.original_values)
        def recovery_binding(role):
            row=effects[role];parent=DirectoryAnchor(**row['parent']);current=observe(parent,role)
            if asdict(current)!=row['image']:raise AssertionError('Current owned generation changed before recovery')
            original=next(b.before for b in captured.bindings if b.role==role)
            if original.kind=='absent':return FileBinding(role,parent,current,'absent',None)
            value=originals[role]
            raw=value if type(value) is bytes else value.encode()
            if hashlib.sha256(raw).hexdigest()!=original.sha256:raise AssertionError('Original recovery value differs from its native snapshot')
            return FileBinding(role,parent,current,original.kind,value)
        perform(journal,recovery,'rollback_policy',(recovery_binding('policy'),))
        perform(journal,recovery,'rollback_runtime',tuple(recovery_binding(role) for role in ('gateway_link','serve_link','client_link')))
        for stage in RECOVERY[RECOVERY.index('rollback_runtime')+1:]:seed(journal,recovery,stage)
        verify_known()
        if datafile.read_bytes()!=b'newer-owned-data-after-forward':raise AssertionError('Owned newer data was overwritten')
        if (client/'hermes').read_bytes()!=old_client or (client/'hermes').is_symlink():raise AssertionError('Original regular CLI was not restored')
        if any(anchor(path).mode!=0o755 for _,path in definition.mode_paths()):raise AssertionError('Reviewed owned mode disposition changed')
        if len(created)!=5:raise AssertionError('Exactly two artifact, policy and two drop-in parents must be created')
        journal.complete(digest(dict(scope='owned_file_component_only',effects=effects,receipts=receipts,data_sha256=preserved_data_sha)))
        history=journal.history_sha256
    result=dict(state='OWNED_RENDERED_PUBLICATION_RECOVERY_COMPONENT_PASS',scope='owned_renderer_file_component_only',root=str(root),ledger=str(ledger),
        definition=definition.metadata(),definitionSha256=definition.sha256,captureSha256=snapshot,actualStages=actual,syntheticStages=synthetic,
        createdParents=list(created.values()),receipts=receipts,finalEffects=effects,forwardHistorySha256=forward_history_sha,journalSha256=history,
        preservedDataPath=str(datafile),preservedDataSha256=preserved_data_sha,launcherPath=str(commands/'hermes'),launcherSha256=definition.launcher_sha256,
        modeDisposition='keep_0755_after_policy_retirement',canonicalPolicySchemaValidated=True,protectedAncestryAccepted=False,ownedPublicationAncestryAccepted=True,
        ancestry=ancestry.metadata(),ancestrySha256=ancestry_hash,ancestryStateSha256=ancestry.state_sha256,
        actualServicesBackupsPmAdmissionAccepted=False,fullCutoverAccepted=False,liveChanges=False)
    with (home/'fixture-result.json').open('x') as output:json.dump(result,output,indent=2);output.write('\n');output.flush();os.fsync(output.fileno())
    return result
