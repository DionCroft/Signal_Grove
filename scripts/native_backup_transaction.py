"""One claimed SDK snapshot/age/private-publication backup stage.

No restore, data migration, service action, credential discovery or retry. Real
GitHub/deployment acceptance is separate from explicitly labelled fixtures.
External memory providers are a held completeness requirement, never omitted.
"""
from dataclasses import dataclass,asdict
import hashlib
import json
import math
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import threading
import time

from cutover_coordinator import Plan,ApprovedExecution,FORWARD,RECOVERY
from cutover_journal import CutoverJournal,Refused,Unknown
from native_file_publication import DirectoryAnchor,anchor,digest,_Receipt,_directory
from native_service_manager import NativeServiceManager
from native_operational_paths import NativeOperationalPaths
from native_retirement_owner import read_process_owner
from native_backup_archive import BackupMember,selection_sha256,verify_archive
from backup_sdk_snapshot import NativeInput,SourceInventory,SDK_REQUIRED,MAX_PAYLOAD,MAX_REPORT,observe_input,read_input,stamp,input_from,unique_json
from private_backup_release import (ReleaseTarget,BackupPublicationSpec,NativeBackupPublication,GitHubHttpsTransport,observe_artifact,STAGES,MAX_ASSET_BYTES)

EXECUTOR_REQUIRED=frozenset({'backup_sdk_snapshot.py','native_backup_transaction.py','native_backup_archive.py','private_backup_release.py','cutover_coordinator.py',
 'cutover_journal.py','native_file_publication.py','native_operational_paths.py','native_service_manager.py','native_retirement_owner.py','retirement_guard.py'})

@dataclass(frozen=True)
class BackupTransactionSpec:
    sdk:SourceInventory
    dependencies:SourceInventory
    executor:SourceInventory
    python:NativeInput
    age:NativeInput
    identity:NativeInput
    recipient:str
    home:DirectoryAnchor
    output_parent:DirectoryAnchor
    target:ReleaseTarget
    scope_configuration_sha256:str
    source_role:str
    owned_root:str|None=None
    def __post_init__(self):
        if (any(type(i) is not SourceInventory for i in (self.sdk,self.dependencies,self.executor)) or not SDK_REQUIRED<=dict(self.sdk.files).keys()
            or not EXECUTOR_REQUIRED<=dict(self.executor.files).keys() or any(type(i) is not NativeInput for i in (self.python,self.age,self.identity))
            or any(stat.S_IMODE(i.identity[4])!=0o755 for i in (self.python,self.age)) or stat.S_IMODE(self.identity.identity[4])!=0o600 or not 1<=self.identity.bytes<=65536
            or type(self.recipient) is not str or not re.fullmatch('age1[0-9a-z]{50,90}',self.recipient)
            or type(self.home) is not DirectoryAnchor or self.home.mode!=0o700 or type(self.output_parent) is not DirectoryAnchor or self.output_parent.mode!=0o700
            or self.home.path==self.output_parent.path or Path(self.output_parent.path).is_relative_to(self.home.path)
            or type(self.target) is not ReleaseTarget or type(self.scope_configuration_sha256) is not str or not re.fullmatch('[a-f0-9]{64}',self.scope_configuration_sha256)
            or self.source_role not in ('old_source','new_source') or (self.owned_root is not None and (type(self.owned_root) is not str
                or not re.fullmatch('signal-grove-catalog-[a-f0-9]{32}',Path(self.owned_root).name) or not Path(self.home.path).is_relative_to(self.owned_root)
                or not Path(self.output_parent.path).is_relative_to(self.owned_root)))):raise ValueError('Closed SDK/program/key/output/target backup bindings required')
        if len(json.dumps(asdict(self)).encode())>MAX_PAYLOAD:raise ValueError('Backup specification exceeds its reviewed protocol bound')
    @property
    def sha256(self):return digest(asdict(self))

class _Capture:
    def __init__(self,journal,name,stream):
        self.journal,self.name,self.stream=journal,name,stream;self.data=b'';self.overflow=False;self.error=False
        self.fd=os.open(name,os.O_RDWR|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW|os.O_CLOEXEC,0o600,dir_fd=journal._root)
        self.identity=journal._check(self.fd);os.fsync(journal._root);self.thread=threading.Thread(target=self._read,daemon=True)
    def start(self):self.thread.start()
    def _read(self):
        try:
            while block:=self.stream.read1(65536):
                kept=block[:max(0,4*1024*1024-len(self.data))];self.overflow|=len(kept)!=len(block);view=memoryview(kept)
                while view:
                    n=os.write(self.fd,view)
                    if n<=0:raise OSError('Backup output write failed')
                    view=view[n:]
                self.data+=kept
            os.fsync(self.fd)
        except Exception:self.error=True
    def finish(self):
        self.thread.join(5)
        if self.thread.is_alive() or self.overflow or self.error:raise Unknown('Backup output is incomplete or exceeds its bound')
        self.journal.assert_current();s=os.stat(self.name,dir_fd=self.journal._root,follow_symlinks=False)
        if (s.st_dev,s.st_ino)!=self.identity or self.journal._check(self.fd)!=self.identity or os.pread(self.fd,4*1024*1024+1,0)!=self.data:raise Unknown('Backup output native identity/content changed')
    def evidence(self):return dict(name=self.name,bytes=len(self.data),sha256=hashlib.sha256(self.data).hexdigest())
    def close(self):
        if self.thread.ident is not None:self.thread.join(5)
        if not self.thread.is_alive():
            self.stream.close()
            if self.fd is not None:os.close(self.fd);self.fd=None

class _Pump:
    def __init__(self,source,destination):self.source,self.destination=source,destination;self.error=False;self.thread=threading.Thread(target=self._copy,daemon=True)
    def _copy(self):
        try:
            while block:=self.source.read(65536):self.destination.write(block)
            self.destination.close()
        except Exception:self.error=True
    def start(self):self.thread.start()
    def finish(self):
        self.thread.join(5)
        if self.thread.is_alive() or self.error:raise Unknown('Backup tool input did not finish exactly')

class NativeBackupTransaction:
    def __init__(self,plan,journal,manager,paths,stage,spec,reviewed_spec_sha256,transport):
        if (sys.platform!='linux' or os.geteuid()==0 or type(plan) is not Plan or type(journal) is not CutoverJournal
            or type(manager) is not NativeServiceManager or manager.plan!=plan or manager.journal is not journal or type(paths) is not NativeOperationalPaths
            or journal._operational_paths is not paths or stage not in STAGES or type(spec) is not BackupTransactionSpec or spec.sha256!=reviewed_spec_sha256
            or type(transport) is not GitHubHttpsTransport or transport.credential.binding_sha256!=spec.target.credential_binding_sha256):raise Refused('Exact reviewed native backup transaction required')
        self.plan,self.journal,self.manager,self.paths,self.stage,self.spec,self.transport=plan,journal,manager,paths,stage,spec,transport
        self.used=False;self.state='ready';self.last_receipt=None;self.child_process=None;self.last_child_owner=None;self.child_history=[]
    def _authorize(self,e,*,claimed=False):
        if type(e) is not ApprovedExecution or e.authority_sha256!=self.plan.authority_sha256:raise Refused('Exact backup approval required')
        order=FORWARD if e.scope=='forward' else RECOVERY
        if self.stage not in order:raise Refused('Backup scope/stage differs')
        now=time.monotonic()
        if not math.isfinite(now) or now>=e.deadline:raise Unknown('Backup deadline elapsed')
        self.journal.assert_current();v=self.journal.view;rows=[r for r in v['records'] if r['operation']==e.operation];index=order.index(self.stage)
        if (v['state']!='pending' or v['pending']!=self.stage or v['operation']!=e.operation or v['mode']!=e.scope or v['authorization_sha256']!=e.binding_sha256
            or v['plan_sha256']!=e.packet_sha256 or v['root_plan_sha256']!=self.plan.packet_sha256 or (e.scope=='forward' and e.packet_sha256!=self.plan.packet_sha256)
            or [r['step'] for r in rows if r['kind']=='intent']!=list(order[:index+1]) or [r['step'] for r in rows if r['kind']=='verified']!=list(order[:index])):raise Refused('Exact fixed backup journal prefix required')
        if os.getpid()!=self.journal._owner_pid or not self.journal._owns_operation:raise Refused('Original native backup journal owner required')
        if claimed and (e.operation,self.stage) not in self.journal._dispatch_claims:raise Refused('Backup dispatch claim is missing')
        self.paths.require('hermes_home',self.spec.home.path);self.paths.require('backup_output',self.spec.output_parent.path);self.paths.require(self.spec.source_role,self.spec.sdk.root.path)
        self.paths.require('executor',self.spec.executor.root.path)
        self.paths.contains(self.spec.source_role.replace('_source','_store'),self.spec.python.path)
        self.paths.contains('backup_programs',self.spec.age.path);self.paths.contains('backup_programs',self.spec.dependencies.root.path);self.paths.contains('backup_identity',self.spec.identity.path)
        if self.spec.owned_root is None:
            if self.paths.scope!='production_review_only':raise Refused('Production backup requires real-root operational ancestry')
            self.paths.contains('executor',Path(__file__).resolve())
        elif self.spec.owned_root!=self.paths.ancestry.root:raise Refused('Owned backup root differs from operational witness')
        if anchor(self.spec.home.path)!=self.spec.home or anchor(self.spec.output_parent.path)!=self.spec.output_parent:raise Refused('Exact private backup directories changed')
        scope=self.manager.execution_scope(e.operation,owned_fixture=self.spec.owned_root is not None);scope.require_window(e,read_process_owner(os.getpid()),now)
        if scope.configuration_sha256!=self.spec.scope_configuration_sha256:raise Refused('Backup controller configuration changed')
        return scope
    def _inputs(self):
        result={};directories={}
        roles={'python':self.spec.source_role.replace('_source','_store'),'age':'backup_programs','identity':'backup_identity'}
        for name,i in (('python',self.spec.python),('age',self.spec.age),('identity',self.spec.identity)):
            directories[name]=self._input_chain(roles[name],Path(i.path).parent)
            if observe_input(i.path)!=i:raise Refused('Exact reviewed backup program/identity changed')
            result[name]=asdict(i)
        directories['dependencies']=self._input_chain('backup_programs',Path(self.spec.dependencies.root.path))
        return dict(native=result,directories=directories,sdk=self.spec.sdk.observe(),dependencies=self.spec.dependencies.observe(),executor=self.spec.executor.observe())
    def _input_chain(self,role,endpoint):
        """Bind every nested program/key/dependency parent, including '+' names.

        The existing role witness owns the starting FD and the seven explicit
        mutable mode exceptions; no other writable directory is accepted.
        """
        root=Path(dict(self.paths.directories.directories)[role].path)
        if endpoint.resolve()!=endpoint or not endpoint.is_relative_to(root):raise Refused('Literal nested backup input ancestry required')
        allowed={m.directory.path for m in self.paths.ancestry.modes};fd=self.paths.open_directory(role);result={};current=root
        try:
            for part in ('',*endpoint.relative_to(root).parts):
                if part:
                    nextfd=os.open(part,os.O_RDONLY|os.O_DIRECTORY|os.O_NOFOLLOW|os.O_CLOEXEC,dir_fd=fd);os.close(fd);fd=nextfd;current=current/part
                s=os.fstat(fd);named=current.lstat();mode=stat.S_IMODE(s.st_mode)
                row=(s.st_dev,s.st_ino,s.st_uid,s.st_gid,mode)
                if (not stat.S_ISDIR(s.st_mode) or s.st_uid!=os.geteuid() or row!=(named.st_dev,named.st_ino,named.st_uid,named.st_gid,stat.S_IMODE(named.st_mode))
                    or (mode not in (0o700,0o755) and not (mode==0o775 and str(current) in allowed))):raise Refused('Unprotected nested backup input directory refused')
                result[str(current)]=row
            self.paths.check();return result
        finally:os.close(fd)
    def _wait(self,child,e):
        while child.poll() is None:
            self.journal.assert_current()
            if time.monotonic()>=e.deadline:raise Unknown('Backup child deadline elapsed; exact finite unit retains ownership')
            try:child.wait(timeout=0.2)
            except subprocess.TimeoutExpired:pass
        if child.returncode!=0:raise Unknown('Backup child failed; inspect retained evidence without replay')
    def _owner(self,child,program,scope,receipt,phase,*,deadline):
        # CLOEXEC notification can precede the new /proc image on supported
        # kernels. Observe only this original child; never dispatch it again.
        self.child_process=child;owner=None;original=None;observations=0;observing=False;observed_image='unavailable'
        started=time.monotonic();bound=min(float(deadline),started+0.5) if type(deadline) in (int,float) and math.isfinite(deadline) else started
        checks=dict(incarnation_observed=None,parent_matches=None,uid_matches=None,executable_matches=None,inode_matches=None,cgroup_matches=None)
        try:
            if type(child) is not subprocess.Popen or not child._child_created or type(child.pid) is not int:raise ValueError('Original spawned handle required')
            executor=os.readlink('/proc/self/exe');executor_image=os.stat('/proc/self/exe')
            while observations<64 and time.monotonic()<bound:
                observations+=1;observed_image='unavailable'
                checks={k:None for k in checks};owner=read_process_owner(child.pid)
                checks['incarnation_observed']=owner is not None
                text=Path('/proc',str(child.pid),'stat').read_text();fields=text.rpartition(')')[2].split()
                checks['parent_matches']=len(text)<=8192 and len(fields)>=2 and fields[1]==str(os.getpid())
                checks['uid_matches']=Path('/proc',str(child.pid)).stat().st_uid==os.geteuid()
                executable=os.readlink('/proc/'+str(child.pid)+'/exe');image=os.stat('/proc/'+str(child.pid)+'/exe')
                checks['executable_matches']=executable==program.path
                checks['inode_matches']=(image.st_dev,image.st_ino)==program.identity[:2]
                checks['cgroup_matches']=Path('/proc',str(child.pid),'cgroup').read_text().splitlines()==['0::'+scope.cgroup]
                observed_image='requested' if checks['executable_matches'] and checks['inode_matches'] else 'other'
                if executable==executor and (image.st_dev,image.st_ino)==(executor_image.st_dev,executor_image.st_ino):observed_image='executor'
                if owner is None or child.poll() is not None or (original is not None and owner!=original):break
                if original is None:original=owner
                if all(value is True for value in checks.values()):
                    if read_process_owner(child.pid)!=original or time.monotonic()>=bound:break
                    event=dict(phase=phase,owner=asdict(owner),program_sha256=program.sha256,exec_observations=observations)
                    receipt.append('child_owned',event)
                    if time.monotonic()>=bound:break
                    self.last_child_owner=owner;self.child_history.append(event);return owner
                # Only the unchanged executor preimage may be pending. Missing
                # identity, another parent/user/cgroup/image or a dead child holds.
                if observed_image!='executor' or not all(checks[k] is True for k in ('incarnation_observed','parent_matches','uid_matches','cgroup_matches')):break
                if not observing:
                    receipt.append('child_exec_observing',dict(phase=phase,owner=asdict(original),program_sha256=program.sha256,max_observations=64,max_milliseconds=500,dispatches=1));observing=True
                remaining=bound-time.monotonic()
                if remaining<=0:break
                time.sleep(min(.01,remaining))
        except (OSError,ValueError,IndexError):
            pass
        receipt.append('child_observation_held',dict(phase=phase,pid=child.pid,checks=checks,returncode=child.poll(),program_sha256=program.sha256,
            observed_image=observed_image,observed_owner=asdict(owner) if owner is not None else None,original_owner=asdict(original) if original is not None else None,exec_observations=observations))
        raise Unknown('Backup child native identity/scope unproven')
    def _snapshot(self,e,scope,receipt,directory,raw):
        request=dict(sdk=asdict(self.spec.sdk),dependencies=asdict(self.spec.dependencies),home=asdict(self.spec.home),output=str(raw));request_hash=digest(request);request['request_sha256']=request_hash
        report_name='native-backup-report-'+e.operation+'-'+self.stage+'.json';report_fd=os.open(report_name,os.O_RDWR|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW|os.O_CLOEXEC,0o600,dir_fd=self.journal._root)
        report_identity=self.journal._check(report_fd);os.fsync(self.journal._root);captures=[];child=None
        try:
            receipt.append('snapshot_intent',dict(request_sha256=request_hash,report=report_name,retention=0,restore=False))
            code='import sys;sys.path.insert(0,'+repr(self.spec.executor.root.path)+');from backup_sdk_snapshot import child_main;child_main('+str(report_fd)+')'
            env=dict(PATH='/usr/bin:/bin',HOME=self.spec.home.path,HERMES_HOME=self.spec.home.path,LANG='C.UTF-8',PYTHONDONTWRITEBYTECODE='1',
                TMPDIR=str(directory/'scratch'),TMP=str(directory/'scratch'),TEMP=str(directory/'scratch'),HERMES_DISABLE_LAZY_INSTALLS='1',HERMES_CONFIG_BACKEND='file')
            child=subprocess.Popen([self.spec.python.path,'-I','-B','-S','-c',code],cwd=directory,env=env,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,pass_fds=(report_fd,))
            owner=self._owner(child,self.spec.python,scope,receipt,'snapshot',deadline=e.deadline)
            for suffix,stream in (('stdout',child.stdout),('stderr',child.stderr)):
                capture=_Capture(self.journal,'native-backup-'+e.operation+'-'+self.stage+'-snapshot.'+suffix,stream);captures.append(capture);capture.start()
            data=json.dumps(request).encode()
            if len(data)>MAX_PAYLOAD:raise Refused('Snapshot request exceeds bound')
            child.stdin.write(data);child.stdin.close();self._wait(child,e)
            for capture in captures:capture.finish()
            self._authorize(e,claimed=True)
            if read_process_owner(child.pid)==owner:raise Unknown('Snapshot child incarnation did not end')
            s=os.stat(report_name,dir_fd=self.journal._root,follow_symlinks=False)
            if (s.st_dev,s.st_ino)!=report_identity or self.journal._check(report_fd)!=report_identity:raise Unknown('Snapshot report native identity changed')
            data=os.pread(report_fd,MAX_REPORT+1,0)
            if not data or len(data)>MAX_REPORT:raise Unknown('Snapshot evidence unavailable or exceeds bound')
            report=unique_json(data)
            if (report.get('state')!='SDK_SNAPSHOT_SELECTION_VERIFIED' or report.get('pid')!=child.pid or report.get('request_sha256')!=request_hash
                or report.get('retention')!=0 or report.get('restore_performed') is not False or report.get('external_memory_state_accepted') is not False):raise Unknown('Exact SDK snapshot outcome unproven')
            loaded=report.get('loaded_sources');expected={str(Path(i.root.path)/n):h for i in (self.spec.sdk,self.spec.dependencies) for n,h in i.files}
            if (type(loaded) is not dict or report.get('loaded_source_sha256')!=digest(loaded) or any(expected.get(n)!=h for n,h in loaded.items())
                or not {str(Path(self.spec.sdk.root.path)/n) for n in ('hermes_cli/backup.py','hermes_cli/backup_sqlite.py','hermes_yaml.py')}<=loaded.keys()):raise Unknown('Snapshot imports differ from the reviewed source set')
            receipt.append('snapshot_observed',dict(report=report_name,sha256=hashlib.sha256(data).hexdigest(),logs=[c.evidence() for c in captures],request_sha256=request_hash))
            return report
        finally:
            for capture in captures:capture.close()
            if child is not None and child.poll() is not None:
                if child.stdin and not child.stdin.closed:child.stdin.close()
            os.close(report_fd)
    def _age(self,e,scope,receipt,phase,source,destination):
        captures=[];child=None;pump=None;key=None
        # Every output is exclusively created before the recorded tool intent.
        fd=os.open(destination,os.O_RDWR|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW|os.O_CLOEXEC,0o600);native=stamp(os.fstat(fd));os.fsync(fd)
        parent=os.open(destination.parent,os.O_RDONLY|os.O_DIRECTORY|os.O_NOFOLLOW|os.O_CLOEXEC)
        try:os.fsync(parent)
        finally:os.close(parent)
        try:
            argv=[self.spec.age.path,'--encrypt','-r',self.spec.recipient]
            if phase=='decrypt':
                key=os.open(self.spec.identity.path,os.O_RDONLY|os.O_NOFOLLOW|os.O_CLOEXEC)
                if stamp(os.fstat(key))!=self.spec.identity.identity:raise Refused('Exact age identity changed')
                argv=[self.spec.age.path,'--decrypt','-i','/proc/self/fd/'+str(key)]
            receipt.append('age_intent',dict(phase=phase,output=destination.name,native_identity=list(native[:2]),program_sha256=self.spec.age.sha256,identity_sha256=self.spec.identity.sha256,recipient_sha256=hashlib.sha256(self.spec.recipient.encode()).hexdigest()))
            env=dict(PATH='/usr/bin:/bin',HOME=self.spec.home.path,LANG='C.UTF-8',TMPDIR=str(destination.parent/'scratch'))
            child=subprocess.Popen(argv,cwd=destination.parent,env=env,stdin=subprocess.PIPE,stdout=fd,stderr=subprocess.PIPE,pass_fds=(key,) if key is not None else ())
            owner=self._owner(child,self.spec.age,scope,receipt,phase,deadline=e.deadline)
            capture=_Capture(self.journal,'native-backup-'+e.operation+'-'+self.stage+'-'+phase+'.stderr',child.stderr);captures.append(capture);capture.start()
            with source.open('rb') as input_stream:
                pump=_Pump(input_stream,child.stdin);pump.start();self._wait(child,e);pump.finish()
            capture.finish();self._authorize(e,claimed=True)
            if read_process_owner(child.pid)==owner:raise Unknown('Age child incarnation did not end')
            os.fsync(fd);s=os.fstat(fd)
            if stamp(destination.lstat())!=stamp(s) or (s.st_dev,s.st_ino)!=native[:2] or not 0<s.st_size<=MAX_ASSET_BYTES:raise Unknown('Age output identity/size unproven')
            image=observe_input(destination,limit=MAX_ASSET_BYTES)
            if image.identity!=stamp(s):raise Unknown('Age output changed during verification')
            receipt.append('age_observed',dict(phase=phase,output=destination.name,image=asdict(image),log=capture.evidence()));return image
        finally:
            for capture in captures:capture.close()
            if key is not None:os.close(key)
            os.close(fd)
    def run(self,e):
        if self.used:raise Refused('This backup transaction actor is spent')
        receipt=None;claimed=False;outfd=None;checkpoint='preflight'
        try:
            scope=self._authorize(e);before=self._inputs();self._authorize(e)
            directory=Path(self.spec.output_parent.path)/('backup-'+e.operation+'-'+self.stage)
            if directory.exists() or directory.is_symlink():raise Refused('Backup output preimage is not absent')
            self.journal.claim_dispatch(e.operation,self.stage,e.binding_sha256);claimed=True;self.used=True;self.state='running'
            receipt=_Receipt(self.journal,e,self.stage,self.spec.sha256);self.last_receipt=receipt.name
            receipt.append('backup_intent',dict(spec_sha256=self.spec.sha256,operational_sha256=self.paths.sha256,scope_sha256=scope.sha256,output=directory.name,restore=False,retention=0))
            parent=self.paths.open_directory('backup_output')
            try:os.mkdir(directory.name,mode=0o700,dir_fd=parent);os.fsync(parent)
            finally:os.close(parent)
            out=anchor(directory);outfd=os.open(directory,os.O_RDONLY|os.O_DIRECTORY|os.O_NOFOLLOW|os.O_CLOEXEC);_directory(outfd,out)
            os.mkdir('scratch',mode=0o700,dir_fd=outfd);os.fsync(outfd);receipt.append('output_created',asdict(out))
            stem='hermes-backup-'+e.operation+'-'+self.stage;raw=directory/(stem+'.zip');encrypted=directory/(stem+'.zip.age');decrypted=directory/(stem+'.roundtrip.zip')
            checkpoint='snapshot';report=self._snapshot(e,scope,receipt,directory,raw)
            members=tuple(BackupMember(**r) for r in report['selection']);binding=selection_sha256(members);image=input_from(report['archive'])
            if binding!=report.get('selection_sha256') or image.path!=str(raw) or image!=observe_input(raw,limit=MAX_ASSET_BYTES) or stat.S_IMODE(image.identity[4])!=0o600:raise Unknown('SDK snapshot content/native binding differs')
            for row in members:
                if row.kind=='file':
                    current=observe_input(Path(self.spec.home.path)/row.name,limit=MAX_ASSET_BYTES)
                    if (current.bytes,current.sha256)!=(row.size,row.sha256):raise Unknown('Source state changed before archive verification')
            checkpoint='archive'
            with raw.open('rb') as stream:first=verify_archive(stream,members,binding,directory/'scratch')
            receipt.append('archive_verified',dict(sha256=image.sha256,selection_sha256=binding,manifest_sha256=first['manifest_sha256'],members=first['members'],sqlite_members=first['sqlite_members']))
            self._authorize(e,claimed=True);_directory(outfd,out)
            checkpoint='encrypt';cipher=self._age(e,scope,receipt,'encrypt',raw,encrypted)
            checkpoint='decrypt';roundtrip=self._age(e,scope,receipt,'decrypt',encrypted,decrypted)
            if roundtrip.sha256!=image.sha256 or roundtrip.bytes!=image.bytes:raise Unknown('Age round-trip differs from the exact archive')
            with decrypted.open('rb') as stream:second=verify_archive(stream,members,binding,directory/'scratch')
            if first!=second:raise Unknown('Decrypted backup content proof differs')
            receipt.append('encryption_roundtrip_verified',dict(archive_sha256=image.sha256,cipher_sha256=cipher.sha256,cipher_bytes=cipher.bytes))
            checksum=encrypted.with_name(encrypted.name+'.sha256');fd=os.open(checksum,os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW|os.O_CLOEXEC,0o600)
            with os.fdopen(fd,'wb') as sink:sink.write((cipher.sha256+'  '+encrypted.name+'\n').encode());sink.flush();os.fsync(sink.fileno())
            os.fsync(outfd);self._authorize(e,claimed=True)
            if self._inputs()!=before:raise Unknown('Backup program/source/key changed before publication')
            checkpoint='publication';pubspec=BackupPublicationSpec(self.spec.target,out,tuple(observe_artifact(directory,n) for n in (encrypted.name,checksum.name)),self.spec.scope_configuration_sha256,self.spec.owned_root)
            publication=NativeBackupPublication(self.plan,self.journal,self.manager,self.paths,self.stage,pubspec,pubspec.sha256,self.transport).publish(e)
            if publication.get('state')!='effects_observed' or publication.get('download_bytes_verified') is not True or publication.get('private_target_verified') is not True:raise Unknown('Exact private publication outcome unproven')
            checkpoint='final_conservation'
            if self._inputs()!=before or observe_input(encrypted,limit=MAX_ASSET_BYTES)!=cipher:raise Unknown('Backup inputs/encrypted content changed after publication')
            self._authorize(e,claimed=True);_directory(outfd,out)
            for own in (image,roundtrip):
                path=Path(own.path)
                if path.parent!=directory or observe_input(path,limit=MAX_ASSET_BYTES)!=own:raise Unknown('Own verified plaintext changed before cleanup')
                receipt.append('plaintext_removal_intent',dict(name=path.name,identity=list(own.identity[:2]),sha256=own.sha256));os.unlink(path.name,dir_fd=outfd);os.fsync(outfd)
                receipt.append('plaintext_removed',dict(name=path.name))
            flag={'backup_pre':'fresh_backup_verified','backup_post':'post_backup_verified','recovery_backup':'recovery_backup_verified','recovery_backup_post':'post_backup_verified'}[self.stage]
            proof=dict(stage=self.stage,snapshot_selection_verified=True,archive_contents_verified=True,encryption_roundtrip_verified=True,private_pair_download_verified=True,
                selection_sha256=binding,archive_manifest_sha256=first['manifest_sha256'],archive_sha256=image.sha256,encrypted_sha256=cipher.sha256,encrypted_bytes=cipher.bytes,publication=publication,
                restore_performed=False,external_memory_state_accepted=False,owned_scope=self.spec.owned_root is not None,checks={flag:True})
            receipt.append('backup_stage_verified',proof);self._authorize(e,claimed=True);self.state='effects_observed'
            return dict(state=self.state,receipt=receipt.name,receipt_sha256=hashlib.sha256(receipt.data).hexdigest(),proof=proof)
        except BaseException as error:
            self.state='unknown' if claimed else 'held'
            if not isinstance(error,Exception):raise
            if claimed:raise Unknown('Backup stage outcome held at '+checkpoint+'; preserve exact artifacts without replay') from None
            raise
        finally:
            if outfd is not None:os.close(outfd)
            if receipt is not None:receipt.close()
