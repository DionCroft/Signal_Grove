"""Actual SDK/age round-trip in a disposable, private control-plane fixture.

This is not the production backup transaction or private Release publication.
Only generated fixture identity/state/key material is read or archived.
"""
from dataclasses import asdict
import hashlib
import json
import os
from pathlib import Path
import sqlite3
import stat
import subprocess
import sys
from types import SimpleNamespace
import zipfile

from native_backup_archive import BackupMember,selection_sha256,verify_archive


def sha(path):
    h=hashlib.sha256()
    with Path(path).open('rb') as stream:
        while data:=stream.read(1024*1024):h.update(data)
    return h.hexdigest()


def run(root,source,source_hashes,age,age_hash,keygen,keygen_hash):
    root,source,age,keygen=map(Path,(root,source,age,keygen));os.umask(0o077)
    if (not root.name.startswith('signal-grove-backup-') or root.resolve()!=root or stat.S_IMODE(root.stat().st_mode)!=0o700
            or source.resolve()!=source or sha(age)!=age_hash or sha(keygen)!=keygen_hash or 'hermes_cli.backup' in sys.modules):raise AssertionError('Exact fresh native fixture/source/tools required')
    for n in ('hermes_cli/backup.py','hermes_cli/backup_sqlite.py','hermes_cli/backup_restore.py','hermes_cli/archive_safe.py','hermes_cli/home_data_layout.py','plugins/memory/__init__.py'):
        if sha(source/n)!=source_hashes[n]:raise AssertionError('SDK backup input differs')
    home=root/'data';out=root/'output';scratch=root/'scratch';keyroot=root/'identity'
    for p in (home,out,scratch,keyroot):p.mkdir(mode=0o700)
    os.environ.update(HOME=str(root),HERMES_HOME=str(home),TMPDIR=str(scratch),TMP=str(scratch),TEMP=str(scratch),HERMES_DISABLE_LAZY_INSTALLS='1')
    sys.path.insert(0,str(source))
    from hermes_cli import backup
    if Path(backup.__file__)!=source/'hermes_cli/backup.py':raise AssertionError('SDK backup origin differs')
    fixtures={'.env':b'SIGNAL_GROVE_FIXTURE=1\n','config.yaml':b'memory:\n  provider: null\n','auth.json':b'{"fixture":true}\n','SOUL.md':b'Fixture identity\n','memories/MEMORY.md':b'Fixture memory\n','memories/USER.md':b'Fixture user\n',
        'profiles/garden/SOUL.md':b'Fixture profile\n','skills/example/hermes-agent/README.md':b'Nested user skill\n','skills/.archive/reviewed.md':b'Retained archived skill\n',
        'cache/images/example.png':b'Fixture media\n','cache/uv/regenerable.bin':b'Excluded cache\n','hermes-agent/not-state.txt':b'Excluded source\n','browser-profile/cookies':b'Excluded browser fixture\n','gateway.pid':b'99999999\n'}
    for n,b in fixtures.items():
        p=home/n;p.parent.mkdir(parents=True,exist_ok=True,mode=0o700);p.write_bytes(b)
    outside=root/'outside-fixture';outside.write_bytes(b'Never archive via a symlink');(home/'outside-link').symlink_to(outside)
    c=sqlite3.connect(home/'state.db');c.execute('PRAGMA journal_mode=WAL');c.execute('CREATE TABLE continuity (id INTEGER PRIMARY KEY, value TEXT)');c.commit();c.execute('PRAGMA wal_checkpoint(TRUNCATE)')
    c.execute("INSERT INTO continuity(value) VALUES ('fixture one'),('fixture two')");c.commit()
    if not (home/'state.db-wal').exists() or (home/'state.db-wal').stat().st_size==0:raise AssertionError('Uncheckpointed WAL proof required')
    raw=out/'fixture.zip'
    def selected():
        if backup._collect_memory_provider_external_paths():raise AssertionError('Unexpected external fixture memory')
        rows=[]
        for p,relative in backup._iter_backup_files(home,raw):
            rows.append(BackupMember(relative.as_posix(),'sqlite',None,None) if p.suffix=='.db' else BackupMember(relative.as_posix(),'file',p.stat().st_size,sha(p)))
        return tuple(sorted(rows,key=lambda m:m.name))
    before=selected();names={m.name for m in before}
    required={'.env','auth.json','SOUL.md','config.yaml','memories/MEMORY.md','memories/USER.md','profiles/garden/SOUL.md','state.db','skills/.archive/reviewed.md','skills/example/hermes-agent/README.md','cache/images/example.png'}
    excluded={'state.db-wal','state.db-shm','gateway.pid','outside-link','cache/uv/regenerable.bin','hermes-agent/not-state.txt','browser-profile/cookies'}
    if not required<=names or names&excluded:raise AssertionError('Actual SDK selection semantics differ')
    if backup.run_backup(SimpleNamespace(output=str(raw),keep=0)) is not True or not raw.is_file():raise AssertionError('Native SDK did not complete the archive')
    raw_identity=(raw.stat().st_dev,raw.stat().st_ino)
    after=selected()
    if before!=after:raise AssertionError('Fixture source selection/content changed')
    binding=selection_sha256(before)
    with raw.open('rb') as stream:first=verify_archive(stream,before,binding,scratch)
    with zipfile.ZipFile(raw) as z:
        wal_image=scratch/'wal-proof.db';wal_image.write_bytes(z.read('state.db'))
    ro=sqlite3.connect('file:'+str(wal_image)+'?mode=ro&immutable=1',uri=True)
    try:rows=ro.execute('SELECT id,value FROM continuity ORDER BY id').fetchall()
    finally:ro.close()
    if rows!=[(1,'fixture one'),(2,'fixture two')]:raise AssertionError('Archive lost committed WAL rows')
    wal_identity=(wal_image.stat().st_dev,wal_image.stat().st_ino);c.close()
    def private_file(p):return p.open('xb')
    def command(argv,*,stdin=None,stdout=None):
        err=out/('tool-'+str(len(list(out.glob('tool-*.stderr'))))+'.stderr')
        with private_file(err) as error:
            p=subprocess.run([str(a) for a in argv],stdin=stdin if stdin is not None else subprocess.DEVNULL,stdout=stdout if stdout is not None else subprocess.PIPE,stderr=error,timeout=60)
        if p.returncode:raise AssertionError('Native age fixture command failed')
        return p.stdout
    key=keyroot/'fixture.age'
    with private_file(key) as sink:command([keygen],stdout=sink)
    public=command([keygen,'-y',key]).decode('ascii').strip()
    if not public.startswith('age1'):raise AssertionError('Generated recipient type differs')
    encrypted=out/'fixture.zip.age'
    with raw.open('rb') as stream,private_file(encrypted) as sink:command([age,'--encrypt','-r',public],stdin=stream,stdout=sink)
    decrypted=out/'verified-roundtrip.zip'
    with encrypted.open('rb') as stream,private_file(decrypted) as sink:
        decrypted_identity=(os.fstat(sink.fileno()).st_dev,os.fstat(sink.fileno()).st_ino)
        command([age,'--decrypt','-i',key],stdin=stream,stdout=sink)
    original_sha=sha(raw)
    if sha(decrypted)!=original_sha:raise AssertionError('Age round-trip content differs')
    with decrypted.open('rb') as stream:second=verify_archive(stream,before,binding,scratch)
    if first!=second:raise AssertionError('Decrypted archive proof differs')
    checksum=out/'fixture.zip.age.sha256';checksum.write_text(sha(encrypted)+'  '+encrypted.name+'\n',encoding='ascii')
    cleanup=[]
    for p,expected in ((raw,raw_identity),(decrypted,decrypted_identity),(wal_image,wal_identity)):
        if p.resolve().parent not in (out,scratch) or (p.lstat().st_dev,p.lstat().st_ino)!=expected or p.stat().st_nlink!=1 or not stat.S_ISREG(p.stat().st_mode):raise AssertionError('Own verified fixture plaintext identity changed')
        p.unlink();cleanup.append(p.name)
    if sha(age)!=age_hash or sha(keygen)!=keygen_hash or selected()!=before:raise AssertionError('Fixture/tool conservation failed')
    report=dict(state='ACTUAL_SDK_BACKUP_AND_AGE_ROUNDTRIP_PASS',sdk_backup_sha256=source_hashes['hermes_cli/backup.py'],selection_sha256=binding,archive=first,plaintext_sha256=original_sha,
        encrypted_sha256=sha(encrypted),encrypted_bytes=encrypted.stat().st_size,age_sha256=age_hash,keygen_sha256=keygen_hash,wal_rows_verified=2,excluded_names_verified=len(excluded),required_names_verified=len(required),
        own_verified_plaintext_removed=cleanup,source_preserved=True,fixture_key_retained_privately=True,live_home_read=False,remote_publication_verified=False,production_backup_transaction_accepted=False,operational_restore_verified=False)
    (root/'fixture-result.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');return report
