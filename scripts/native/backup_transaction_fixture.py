"""Actual SDK + age stage in an isolated home and loopback Release protocol.

Uses a production-shaped real-root path witness and finite cutover-named unit;
all source state/key material is generated. No real GitHub credential/request,
live home, grant, launcher or service is changed. Non-backup gates are synthetic.
"""
from dataclasses import asdict
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import sqlite3
import subprocess
import sys
import zipfile

from cutover_coordinator import Plan,ApprovedExecution
from cutover_journal import CutoverJournal
from native_file_publication import anchor,digest
from native_operational_paths import OperationalDirectories,NativeOperationalPaths,ROLES
from native_service_manager import NativeServiceManager,UnitBinding
from rendered_publication_catalog import capture_native
from native_backup_transaction import NativeBackupTransaction,BackupTransactionSpec
from backup_sdk_snapshot import SourceInventory,observe_input
from private_backup_release import ReleaseTarget,GitHubCredential,GitHubHttpsTransport
from native_backup_archive import BackupMember,verify_archive,selection_sha256

def run(root,operation,authority,definition,source,source_files,dependencies,dependency_files,store,python,age,keygen,keygen_sha256,scope_configuration_sha256,approval_receipt_sha256,operational_roots):
    root,source,dependencies,store,python,age,keygen=map(Path,(root,source,dependencies,store,python,age,keygen));os.umask(0o077)
    if (root.name!='signal-grove-backup-'+operation or anchor(root).mode!=0o700 or definition.scope!='production_review_only'
        or set(operational_roots)!=set(ROLES)-{'hermes_home','journal','executor','new_source','new_store','backup_identity','backup_output'}
        or observe_input(keygen).sha256!=keygen_sha256):raise ValueError('Exact fresh isolated backup fixture/source/tool context required')
    data=root/'data';ledger=root/'ledger';identity=root/'identity';output=root/'output';readback=root/'readback'
    for p in (data,ledger,identity,output,readback):p.mkdir(mode=0o700)
    original={'.env':b'SIGNAL_GROVE_FIXTURE=1\n','auth.json':b'{"fixture":true}\n','config.yaml':b'memory:\n  provider: null\n','SOUL.md':b'Fixture identity\n',
        'memories/MEMORY.md':b'Fixture memory\n','memories/USER.md':b'Fixture user\n','profiles/garden/config.yaml':b'memory:\n  provider: null\n','profiles/garden/SOUL.md':b'Fixture garden\n',
        'skills/.archive/retained.md':b'Retained skill\n','skills/example/hermes-agent/README.md':b'Nested user skill\n','cache/images/fixture.png':b'Fixture media\n'}
    excluded={'cache/uv/excluded.bin':b'Excluded cache','hermes-agent/excluded.py':b'Excluded source','gateway.pid':b'99999999\n'}
    for name,content in {**original,**excluded}.items():
        p=data/name;p.parent.mkdir(parents=True,exist_ok=True,mode=0o700);p.write_bytes(content)
    outside=root/'outside';outside.write_bytes(b'Do not archive through links');(data/'outside-link').symlink_to(outside)
    connection=sqlite3.connect(data/'state.db');connection.execute('PRAGMA journal_mode=WAL');connection.execute('CREATE TABLE continuity(n INTEGER PRIMARY KEY,value TEXT)');connection.commit();connection.execute('PRAGMA wal_checkpoint(TRUNCATE)')
    connection.execute("INSERT INTO continuity VALUES(1,'fixture one'),(2,'fixture two')");connection.commit()
    if not (data/'state.db-wal').stat().st_size:raise AssertionError('Actual committed WAL proof required')
    key=identity/'fixture.age';env=dict(PATH='/usr/bin:/bin',HOME=str(root),LANG='C.UTF-8')
    with key.open('xb') as sink:created=subprocess.run([str(keygen)],env=env,stdout=sink,stderr=subprocess.PIPE,timeout=15)
    if created.returncode:raise AssertionError('Fixture key generation failed')
    public=subprocess.run([str(keygen),'-y',str(key)],env=env,capture_output=True,timeout=15)
    if public.returncode:raise AssertionError('Fixture recipient unavailable')
    paths={**operational_roots,'hermes_home':data,'journal':ledger,'executor':Path(__file__).resolve().parents[1],
        'new_source':source,'new_store':store,'backup_identity':identity,'backup_output':output}
    catalog=OperationalDirectories(tuple((name,anchor(paths[name])) for name in ROLES));captured=capture_native(definition,definition.sha256)
    publication=captured.capture_ancestry(captured.sha256);guard=NativeOperationalPaths.capture(publication,publication.sha256,catalog,catalog.sha256)
    scripts=Path(__file__).resolve().parents[1];files=tuple(sorted((p.relative_to(scripts).as_posix(),observe_input(p).sha256) for p in scripts.rglob('*.py')))
    secret=GitHubCredential('fixture_secret_'+operation);target=ReleaseTarget('fixture/backups',101,202,'fixture-backups',secret.binding_sha256)
    spec=BackupTransactionSpec(SourceInventory(anchor(source),tuple(source_files)),SourceInventory(anchor(dependencies),tuple(dependency_files)),SourceInventory(anchor(scripts),files),
        observe_input(python),observe_input(age),observe_input(key),public.stdout.decode().strip(),anchor(data),anchor(output),target,scope_configuration_sha256,'new_source')
    plan=Plan(digest(dict(scope='isolated_actual_sdk_backup_loopback_only',spec_sha256=spec.sha256,operational_sha256=guard.sha256)),digest(asdict(authority)),
        digest(dict(scope='synthetic_non_backup_preflight')),digest(dict(scope='separate_recovery_packet_required')))
    module_spec=importlib.util.spec_from_file_location('publication_protocol_fixture',scripts/'tests/test_private_backup_release.py');module=importlib.util.module_from_spec(module_spec);module_spec.loader.exec_module(module)
    server=module.FixtureServer();transport=GitHubHttpsTransport(secret);transport._connect=server.connect
    bindings=tuple(UnitBinding(role,'hermes-'+role+'.service','e'*64,'e'*64,'/fixture/not-dispatched','/fixture/not-dispatched') for role in ('gateway','serve'))
    try:
        with CutoverJournal(ledger,expected_identity=(ledger.stat().st_dev,ledger.stat().st_ino),operational_paths=guard,expected_operational_sha256=guard.sha256) as journal,NativeServiceManager(authority,plan,journal,bindings) as manager:
            scope=manager.execution_scope(operation,owned_fixture=False)
            if scope.configuration_sha256!=scope_configuration_sha256:raise AssertionError('Exact finite backup controller required')
            execution=ApprovedExecution(operation,plan.packet_sha256,plan.authority_sha256,'forward',approval_receipt_sha256,(scope.active_enter_usec+scope.runtime_max_usec+scope.stop_timeout_usec)/1e6+5)
            journal.begin(operation,plan.packet_sha256,authorization_sha256=execution.binding_sha256);journal.intent('preflight');journal.verified('preflight',digest(dict(scope='synthetic_non_backup_preflight')))
            journal.intent('backup_pre');actor=NativeBackupTransaction(plan,journal,manager,guard,'backup_pre',spec,spec.sha256,transport);result=actor.run(execution)
            if result['state']!='effects_observed' or result['proof']['checks']!={'fresh_backup_verified':True} or len(server.posts)!=2:raise AssertionError('Complete fixture backup stage unproven')
            directory=output/('backup-'+operation+'-backup_pre');cipher=directory/('hermes-backup-'+operation+'-backup_pre.zip.age')
            if cipher.stat().st_size>1024*1024:raise AssertionError('Generated readback fixture exceeds bound')
            with cipher.open('rb') as stream:decoded=subprocess.run([str(age),'--decrypt','-i',str(key)],stdin=stream,capture_output=True,env=env,timeout=15)
            if decoded.returncode or len(decoded.stdout)>1024*1024 or hashlib.sha256(decoded.stdout).hexdigest()!=result['proof']['archive_sha256']:raise AssertionError('Independent fixture decryption differs')
            selection=json.loads((ledger/('native-backup-report-'+operation+'-backup_pre.json')).read_text());members=tuple(BackupMember(**row) for row in selection['selection'])
            verified=verify_archive(io.BytesIO(decoded.stdout),members,selection_sha256(members),readback)
            if verified['members']!=12 or verified['sqlite_members']!=1:raise AssertionError('Actual SDK selected members differ')
            with zipfile.ZipFile(io.BytesIO(decoded.stdout)) as archive:
                if set(archive.namelist())!=set(original)|{'state.db'} or any(name in archive.namelist() for name in excluded):raise AssertionError('SDK exact inclusion/exclusion differs')
                for name,content in original.items():
                    if archive.read(name)!=content or (data/name).read_bytes()!=content:raise AssertionError('Fixture ordinary source/content changed')
                image=readback/'state.db';fd=os.open(image,os.O_RDWR|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW|os.O_CLOEXEC,0o600)
                try:
                    os.write(fd,archive.read('state.db'));os.fsync(fd);native=os.fstat(fd);sql=sqlite3.connect('file:/proc/self/fd/'+str(fd)+'?mode=ro&immutable=1',uri=True)
                    try:rows=sql.execute('SELECT n,value FROM continuity ORDER BY n').fetchall();integrity=sql.execute('PRAGMA integrity_check').fetchall()
                    finally:sql.close()
                    if rows!=[(1,'fixture one'),(2,'fixture two')] or integrity!=[('ok',)] or (image.lstat().st_dev,image.lstat().st_ino)!=(native.st_dev,native.st_ino):raise AssertionError('Actual SDK lost committed WAL fixture rows')
                    image.unlink()
                finally:os.close(fd)
            if connection.execute('SELECT n,value FROM continuity ORDER BY n').fetchall()!=rows:raise AssertionError('Source fixture SQLite state changed')
            if list(directory.glob('*.zip')) or list(directory.glob('*.partial')) or (data/'outside-link').resolve()!=outside or outside.read_bytes()!=b'Do not archive through links':raise AssertionError('Own verified plaintext or source-link preservation differs')
            journal.verified('backup_pre',digest(result));journal.complete(digest(dict(scope='isolated_backup_component_only',backup=result)));history=journal.history_sha256
            report=dict(state='ACTUAL_SDK_AGE_LOOPBACK_BACKUP_TRANSACTION_VERIFIED',scope='generated_data_and_local_protocol_only',operation=operation,spec_sha256=spec.sha256,backup=result,
                child_history=actor.child_history,guard_sha256=guard.sha256,journal_sha256=history,ledger=str(ledger),directory=str(directory),key=str(key),source_home=str(data),
                wal_rows_verified=2,archive_members=12,excluded_files_verified=len(excluded)+3,source_preserved=True,verified_plaintext_removed=True,posts=len(server.posts),
                native_execution_scope=asdict(scope),real_root_ancestry=True,synthetic_non_backup_prerequisites=True,real_github_used=False,live_home_read=False,production_backup_accepted=False,operational_restore_verified=False,live_changes=False)
    finally:connection.close();server.close()
    with (root/'fixture-result.json').open('x') as out:json.dump(report,out,indent=2);out.write('\n');out.flush();os.fsync(out.fileno())
    return report
