"""Once-only private Release publication inside an already claimed backup stage.

No snapshot generation, credential discovery, public release, clobber, deletion,
retry, resume or approval issuer. The complete backup transaction must claim its
stage before local effects and supply independently verified encrypted artifacts.
"""
from dataclasses import dataclass,asdict
import hashlib
import http.client
import json
import math
import os
from pathlib import Path
import re
import ssl
import stat
import sys
import time
from urllib.parse import urlsplit

from cutover_coordinator import Plan,ApprovedExecution,FORWARD,RECOVERY
from cutover_journal import CutoverJournal,Refused,Unknown
from native_file_publication import DirectoryAnchor,anchor,digest,_Receipt,_directory
from native_service_manager import NativeServiceManager
from native_operational_paths import NativeOperationalPaths
from native_retirement_owner import read_process_owner

STAGES=('backup_pre','backup_post','recovery_backup','recovery_backup_post')
API_VERSION='2022-11-28'
ASSET_HOSTS=frozenset({'release-assets.githubusercontent.com','objects.githubusercontent.com'})
MAX_ASSET_BYTES=2*1024**3-1 # GitHub Releases requires every asset to be under 2 GiB.

def _hash(v):return type(v) is str and re.fullmatch('[a-f0-9]{64}',v) is not None
def _positive(v):return type(v) is int and 0<v<2**63
def _stamp(s):return (s.st_dev,s.st_ino,s.st_uid,s.st_gid,s.st_mode,s.st_nlink,s.st_size,s.st_mtime_ns,s.st_ctime_ns)


class GitHubCredential:
    """Caller-supplied secret only. Never put this value in evidence or repr."""
    def __init__(self,value):
        if type(value) is not str or not re.fullmatch('[A-Za-z0-9_-]{20,512}',value):raise ValueError('A bounded existing GitHub credential is required')
        self._value=value
    def __repr__(self):return '<GitHubCredential redacted>'
    @property
    def binding_sha256(self):return hashlib.sha256(self._value.encode()).hexdigest()


@dataclass(frozen=True)
class ReleaseTarget:
    repository:str
    repository_id:int
    release_id:int
    tag:str
    credential_binding_sha256:str

    def __post_init__(self):
        if (type(self.repository) is not str or not re.fullmatch('[A-Za-z0-9][A-Za-z0-9-]{0,38}/[A-Za-z0-9_.-]{1,100}',self.repository)
                or self.repository.split('/')[1] in ('.','..') or not _positive(self.repository_id) or not _positive(self.release_id) or type(self.tag) is not str
                or not re.fullmatch('[A-Za-z0-9][A-Za-z0-9_.-]{0,127}',self.tag) or not _hash(self.credential_binding_sha256)):
            raise ValueError('Exact private repository/release identity and credential binding required')


@dataclass(frozen=True)
class EncryptedArtifact:
    name:str
    bytes:int
    sha256:str
    native_identity:tuple

    def __post_init__(self):
        if (type(self.name) is not str or not re.fullmatch('hermes-backup-[a-f0-9]{32}-(?:backup_pre|backup_post|recovery_backup|recovery_backup_post)\\.zip\\.age(?:\\.sha256)?',self.name)
                or not _positive(self.bytes) or self.bytes>MAX_ASSET_BYTES or not _hash(self.sha256)
                or type(self.native_identity) is not tuple or len(self.native_identity)!=9
                or any(type(n) is not int or n<0 for n in self.native_identity)
                or self.native_identity[5]!=1 or self.native_identity[6]!=self.bytes
                or not stat.S_ISREG(self.native_identity[4]) or stat.S_IMODE(self.native_identity[4])!=0o600):
            raise ValueError('Exact private encrypted artifact native preimage required')


@dataclass(frozen=True)
class BackupPublicationSpec:
    target:ReleaseTarget
    directory:DirectoryAnchor
    artifacts:tuple[EncryptedArtifact,...]
    scope_configuration_sha256:str
    owned_root:str|None=None

    def __post_init__(self):
        if (type(self.target) is not ReleaseTarget or type(self.directory) is not DirectoryAnchor or self.directory.mode!=0o700
                or type(self.artifacts) is not tuple or len(self.artifacts)!=2 or any(type(a) is not EncryptedArtifact for a in self.artifacts)
                or self.artifacts[1].name!=self.artifacts[0].name+'.sha256' or not self.artifacts[0].name.endswith('.zip.age')
                or self.artifacts[1].bytes>256 or not _hash(self.scope_configuration_sha256)
                or (self.owned_root is not None and (type(self.owned_root) is not str
                    or not re.fullmatch('signal-grove-catalog-[a-f0-9]{32}',Path(self.owned_root).name)
                    or not Path(self.directory.path).is_relative_to(Path(self.owned_root))))):raise ValueError('Closed encrypted pair, private directory and exact controller required')
    @property
    def sha256(self):return digest(asdict(self))


def _read_json(data):
    def unique(pairs):
        result={}
        for k,v in pairs:
            if k in result:raise ValueError('duplicate')
            result[k]=v
        return result
    try:return json.loads(data,object_pairs_hook=unique)
    except Exception:raise Unknown('GitHub metadata format refused') from None


def _asset(row,artifact):
    if (type(row) is not dict or not _positive(row.get('id')) or row.get('name')!=artifact.name or row.get('size')!=artifact.bytes
            or type(row.get('size')) is not int or row.get('state')!='uploaded' or row.get('digest')!='sha256:'+artifact.sha256):
        raise Unknown('GitHub asset identity/content outcome is unproven')
    return row['id']


class GitHubHttpsTransport:
    """One connection/request per call; TLS validation, no retry or proxy fallback."""
    def __init__(self,credential):
        if type(credential) is not GitHubCredential:raise ValueError('Exact caller-supplied GitHub credential required')
        self.credential=credential
    def _connect(self,host,timeout):return http.client.HTTPSConnection(host,443,timeout=timeout,context=ssl.create_default_context())
    def _time(self,deadline):
        remaining=deadline-time.monotonic()
        if not math.isfinite(remaining) or remaining<=0:raise Unknown('GitHub operation deadline elapsed')
        return min(15,remaining)
    def _request(self,host,method,path,deadline,*,binary=False,body=None,length=None):
        if (host not in {'api.github.com','uploads.github.com'}|ASSET_HOSTS or method not in ('GET','POST')
                or type(path) is not str or not path.startswith('/') or path.startswith('//') or len(path)>16384 or any(c in path for c in '\0\r\n')
                or (method=='POST' and (host!='uploads.github.com' or body is None or not _positive(length) or length>MAX_ASSET_BYTES))
                or (method=='GET' and (body is not None or length is not None))):raise Refused('Closed GitHub request route required')
        conn=self._connect(host,self._time(deadline))
        try:
            conn.putrequest(method,path,skip_host=True,skip_accept_encoding=True);conn.putheader('Host',host);conn.putheader('User-Agent','Signal-Grove-maintenance')
            conn.putheader('Accept','application/octet-stream' if binary else 'application/vnd.github+json');conn.putheader('Accept-Encoding','identity')
            if host in ('api.github.com','uploads.github.com'):
                conn.putheader('Authorization','Bearer '+self.credential._value);conn.putheader('X-GitHub-Api-Version',API_VERSION)
            if body is not None:conn.putheader('Content-Type','application/octet-stream');conn.putheader('Content-Length',str(length))
            conn.endheaders()
            if body is not None:
                sent=0
                while block:=body.read(65536):
                    self._time(deadline);sent+=len(block)
                    if sent>length:raise Unknown('Upload input exceeds its declared native size')
                    conn.send(block)
                if sent!=length:raise Unknown('Upload input ended before its declared native size')
            response=conn.getresponse()
            if response.getheader('Content-Encoding','identity')!='identity':raise Unknown('GitHub content encoding refused')
            return conn,response
        except BaseException:
            conn.close();raise
    def json(self,host,method,path,deadline,*,body=None,length=None):
        conn=None
        try:
            conn,r=self._request(host,method,path,deadline,body=body,length=length)
            if r.status!=(201 if method=='POST' else 200):raise Unknown('GitHub request did not finish successfully')
            data=r.read(2*1024*1024+1)
            if len(data)>2*1024*1024:raise Unknown('GitHub metadata exceeds its bound')
            self._time(deadline);return _read_json(data)
        except (Refused,Unknown):raise
        except Exception:raise Unknown('GitHub request outcome unknown; no retry') from None
        finally:
            if conn is not None:conn.close()
    def download(self,path,artifact,sink,deadline):
        conn=None
        try:
            conn,r=self._request('api.github.com','GET',path,deadline,binary=True)
            if r.status==302:
                location=r.getheader('Location');conn.close();conn=None
                if type(location) is not str or any(c in location for c in '\0\r\n'):raise Unknown('Asset redirect refused')
                try:u=urlsplit(location)
                except Exception:raise Unknown('Asset redirect refused') from None
                if (u.scheme!='https' or u.hostname not in ASSET_HOSTS or u.port not in (None,443) or u.username or u.password or u.fragment
                        or not u.path.startswith('/') or len(location)>16384):raise Unknown('Asset redirect authority refused')
                conn,r=self._request(u.hostname,'GET',u.path+('?' +u.query if u.query else ''),deadline,binary=True)
            if r.status!=200:raise Unknown('Asset download did not complete')
            declared=r.getheader('Content-Length')
            if declared is not None and declared!=str(artifact.bytes):raise Unknown('Downloaded asset declared size differs')
            count=0;h=hashlib.sha256()
            while block:=r.read(65536):
                self._time(deadline);count+=len(block)
                if count>artifact.bytes:raise Unknown('Downloaded asset exceeds its bound')
                h.update(block);sink.write(block)
            if count!=artifact.bytes or h.hexdigest()!=artifact.sha256:raise Unknown('Downloaded asset content differs')
            sink.flush();os.fsync(sink.fileno());return dict(bytes=count,sha256=h.hexdigest())
        except (Refused,Unknown):raise
        except Exception:raise Unknown('Asset download outcome unknown; no retry') from None
        finally:
            if conn is not None:conn.close()


def observe_artifact(directory,name):
    """Read only; the caller must separately establish encryption/snapshot proof."""
    if type(name) is not str or not re.fullmatch('hermes-backup-[a-f0-9]{32}-(?:backup_pre|backup_post|recovery_backup|recovery_backup_post)\\.zip\\.age(?:\\.sha256)?',name):raise Refused('Closed backup artifact name required')
    directory=Path(directory);fd=os.open(directory/name,os.O_RDONLY|os.O_NOFOLLOW|os.O_NONBLOCK|os.O_CLOEXEC)
    try:
        before=os.fstat(fd)
        if not stat.S_ISREG(before.st_mode) or before.st_uid!=os.geteuid() or stat.S_IMODE(before.st_mode)!=0o600 or before.st_nlink!=1 or not 0<before.st_size<=MAX_ASSET_BYTES:raise Refused('Private bounded artifact required')
        h=hashlib.sha256();count=0
        while b:=os.read(fd,1024*1024):count+=len(b);h.update(b)
        if count!=before.st_size or _stamp(os.fstat(fd))!=_stamp(before) or _stamp((directory/name).lstat())!=_stamp(before):raise Unknown('Artifact changed during observation')
        return EncryptedArtifact(name,count,h.hexdigest(),_stamp(before))
    finally:os.close(fd)


class NativeBackupPublication:
    def __init__(self,plan,journal,manager,paths,stage,spec,reviewed_spec_sha256,transport):
        if (sys.platform!='linux' or os.geteuid()==0 or type(plan) is not Plan or type(journal) is not CutoverJournal
                or type(manager) is not NativeServiceManager or manager.plan!=plan or manager.journal is not journal
                or type(paths) is not NativeOperationalPaths or journal._operational_paths is not paths
                or stage not in STAGES or type(spec) is not BackupPublicationSpec or spec.sha256!=reviewed_spec_sha256
                or type(transport) is not GitHubHttpsTransport or transport.credential.binding_sha256!=spec.target.credential_binding_sha256):raise Refused('Exact native backup publication bindings required')
        self.plan,self.journal,self.manager,self.paths,self.stage,self.spec,self.transport=plan,journal,manager,paths,stage,spec,transport
        self.used=False;self.state='ready';self.last_receipt=None
    def _authorize(self,e):
        if type(e) is not ApprovedExecution or e.authority_sha256!=self.plan.authority_sha256:raise Refused('Exact publication approval required')
        stages=FORWARD if e.scope=='forward' else RECOVERY
        if self.stage not in stages:raise Refused('Publication scope/stage differs')
        self.journal.assert_current();v=self.journal.view;rows=[r for r in v['records'] if r['operation']==e.operation];index=stages.index(self.stage)
        if (v['state']!='pending' or v['pending']!=self.stage or v['operation']!=e.operation or v['mode']!=e.scope or v['authorization_sha256']!=e.binding_sha256
                or v['plan_sha256']!=e.packet_sha256 or v['root_plan_sha256']!=self.plan.packet_sha256 or (e.scope=='forward' and e.packet_sha256!=self.plan.packet_sha256)
                or [r['step'] for r in rows if r['kind']=='intent']!=list(stages[:index+1]) or [r['step'] for r in rows if r['kind']=='verified']!=list(stages[:index])
                or os.getpid()!=self.journal._owner_pid or not self.journal._owns_operation or (e.operation,self.stage) not in self.journal._dispatch_claims):raise Refused('Original claimed backup stage is not owned')
        self.paths.require('backup_output',Path(self.spec.directory.path).parent)
        if self.spec.owned_root is None:
            if self.paths.scope!='production_review_only':raise Refused('Production publication requires real-root ancestry')
            self.paths.contains('executor',Path(__file__).resolve().parent)
        elif self.spec.owned_root!=self.paths.ancestry.root:raise Refused('Owned publication root differs')
        expected='hermes-backup-'+e.operation+'-'+self.stage+'.zip.age'
        if self.spec.artifacts[0].name!=expected or anchor(self.spec.directory.path)!=self.spec.directory:raise Refused('Exact operation artifact/directory required')
        scope=self.manager.execution_scope(e.operation,owned_fixture=self.spec.owned_root is not None);scope.require_window(e,read_process_owner(os.getpid()),time.monotonic())
        if scope.configuration_sha256!=self.spec.scope_configuration_sha256:raise Refused('Publication controller command differs')
    def _identity(self,e):
        self._authorize(e);t=self.spec.target;base='/repos/'+t.repository
        repo=self.transport.json('api.github.com','GET',base,e.deadline)
        if type(repo) is not dict or repo.get('id')!=t.repository_id or type(repo.get('id')) is not int or repo.get('private') is not True or repo.get('full_name')!=t.repository:raise Refused('Pinned private GitHub repository required')
        release=self.transport.json('api.github.com','GET',base+'/releases/'+str(t.release_id),e.deadline)
        if (type(release) is not dict or release.get('id')!=t.release_id or type(release.get('id')) is not int or release.get('tag_name')!=t.tag
                or release.get('upload_url')!='https://uploads.github.com'+base+'/releases/'+str(t.release_id)+'/assets{?name,label}'):raise Refused('Pinned existing GitHub release required')
        self._authorize(e)
    def _assets(self,e):
        t=self.spec.target;all_rows=[]
        for page in range(1,21):
            data=self.transport.json('api.github.com','GET','/repos/'+t.repository+'/releases/'+str(t.release_id)+'/assets?per_page=100&page='+str(page),e.deadline)
            if type(data) is not list or len(data)>100 or any(type(r) is not dict for r in data):raise Unknown('Release asset inventory refused')
            all_rows.extend(data)
            if len(data)<100:return all_rows
        raise Unknown('Release inventory exceeds reviewed bound')
    def publish(self,e):
        if self.used:raise Refused('This publication instance is spent')
        self._authorize(e);receipt=None;directory=None;opened=[];claimed=False
        try:
            self.used=True;receipt=_Receipt(self.journal,e,self.stage+'-publication',self.spec.sha256);claimed=True;self.last_receipt=receipt.name;self.state='running'
            receipt.append('publication_intent',dict(spec_sha256=self.spec.sha256,operational_sha256=self.paths.sha256,target=asdict(self.spec.target),names=[a.name for a in self.spec.artifacts]))
            parent=self.paths.open_directory('backup_output')
            try:directory=os.open(Path(self.spec.directory.path).name,os.O_RDONLY|os.O_DIRECTORY|os.O_NOFOLLOW|os.O_CLOEXEC,dir_fd=parent)
            finally:os.close(parent)
            _directory(directory,self.spec.directory)
            for a in self.spec.artifacts:
                fd=os.open(a.name,os.O_RDONLY|os.O_NOFOLLOW|os.O_NONBLOCK|os.O_CLOEXEC,dir_fd=directory);opened.append(fd)
                if _stamp(os.fstat(fd))!=a.native_identity or observe_artifact(self.spec.directory.path,a.name)!=a:raise Refused('Reviewed publication artifact changed')
            archive,checksum=self.spec.artifacts
            if os.pread(opened[0],22,0)!=b'age-encryption.org/v1\n' or os.pread(opened[1],257,0)!=(archive.sha256+'  '+archive.name+'\n').encode():raise Refused('Exact encrypted archive/checksum pair required')
            self._identity(e);existing=self._assets(e)
            if any(r.get('name') in {a.name for a in self.spec.artifacts} for r in existing):raise Refused('Asset name already exists; preserve it without overwrite')
            results=[]
            for a,fd in zip(self.spec.artifacts,opened,strict=True):
                self._identity(e)
                if any(r.get('name')==a.name for r in self._assets(e)):raise Refused('Asset name arrived before upload; no overwrite')
                if observe_artifact(self.spec.directory.path,a.name)!=a:raise Unknown('Artifact changed before upload')
                receipt.append('asset_upload_intent',dict(name=a.name,bytes=a.bytes,sha256=a.sha256))
                self._authorize(e)
                with os.fdopen(os.dup(fd),'rb') as body:
                    body.seek(0);result=self.transport.json('uploads.github.com','POST','/repos/'+self.spec.target.repository+'/releases/'+str(self.spec.target.release_id)+'/assets?name='+a.name,e.deadline,body=body,length=a.bytes)
                asset_id=_asset(result,a);receipt.append('asset_upload_observed',dict(name=a.name,asset_id=asset_id,sha256=a.sha256))
                self._identity(e);metadata=self.transport.json('api.github.com','GET','/repos/'+self.spec.target.repository+'/releases/assets/'+str(asset_id),e.deadline)
                if _asset(metadata,a)!=asset_id:raise Unknown('Asset identity changed after upload')
                filename='downloaded-'+str(asset_id)+'.verified';download_fd=os.open(filename,os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW|os.O_CLOEXEC,0o600,dir_fd=directory)
                with os.fdopen(download_fd,'wb') as sink:
                    native=os.fstat(sink.fileno());identity=(native.st_dev,native.st_ino)
                    os.fsync(directory);receipt.append('asset_download_intent',dict(name=a.name,asset_id=asset_id,downloaded_name=filename,native_identity=list(identity)))
                    download=self.transport.download('/repos/'+self.spec.target.repository+'/releases/assets/'+str(asset_id),a,sink,e.deadline)
                named=os.stat(filename,dir_fd=directory,follow_symlinks=False)
                if (named.st_dev,named.st_ino)!=identity or named.st_uid!=os.geteuid() or named.st_nlink!=1 or stat.S_IMODE(named.st_mode)!=0o600 or named.st_size!=a.bytes:raise Unknown('Downloaded artifact native identity changed')
                verify=os.open(filename,os.O_RDONLY|os.O_NOFOLLOW|os.O_NONBLOCK|os.O_CLOEXEC,dir_fd=directory)
                try:
                    h=hashlib.sha256()
                    while block:=os.read(verify,1024*1024):h.update(block)
                    if _stamp(os.fstat(verify))!=_stamp(named) or _stamp(os.stat(filename,dir_fd=directory,follow_symlinks=False))!=_stamp(named) or h.hexdigest()!=a.sha256:raise Unknown('Downloaded artifact disk content changed')
                finally:os.close(verify)
                self._authorize(e);_directory(directory,self.spec.directory)
                if observe_artifact(self.spec.directory.path,a.name)!=a:raise Unknown('Original publication artifact changed')
                evidence=dict(name=a.name,asset_id=asset_id,bytes=a.bytes,sha256=a.sha256,download=download,downloaded_name=filename)
                receipt.append('asset_download_verified',evidence);results.append(evidence)
            self._identity(e);inventory=self._assets(e)
            for a,r in zip(self.spec.artifacts,results,strict=True):
                found=[row for row in inventory if row.get('name')==a.name]
                if len(found)!=1 or _asset(found[0],a)!=r['asset_id']:raise Unknown('Final release asset pair differs')
            receipt.append('private_pair_verified',dict(assets=results));self._authorize(e);self.state='effects_observed'
            return dict(state=self.state,receipt=receipt.name,receipt_sha256=hashlib.sha256(receipt.data).hexdigest(),assets=results,
                private_target_verified=True,download_bytes_verified=True,scope='owned_fixture_only' if self.spec.owned_root else 'production_target',
                snapshot_or_encryption_verified=False,fresh_backup_verified=False)
        except BaseException as error:
            self.state='unknown' if claimed else 'held'
            if not isinstance(error,Exception):raise
            raise Unknown('Publication outcome held; inspect exact native and remote receipts without retry') from None
        finally:
            for fd in opened:os.close(fd)
            if directory is not None:os.close(directory)
            if receipt is not None:receipt.close()
