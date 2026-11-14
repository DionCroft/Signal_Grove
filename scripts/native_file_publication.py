"""Exact native file/mode effects under a separately approved cutover stage.

No CLI, service action, recursive chmod, delete, fallback or automatic rollback.
Callers bind exact existing parents or explicit one-level creation intents to the
reviewed packet and supply real admission/backup/source/ancestry gates. Owned fixtures test this component; they do not approve live paths.
"""
from __future__ import annotations

from dataclasses import dataclass, asdict, field, replace
import ctypes
import hashlib
import json
import math
import os
from pathlib import Path, PurePosixPath
import re
import stat
import sys
import time
import uuid

from cutover_coordinator import ApprovedExecution, Plan, FORWARD, RECOVERY
from cutover_journal import CutoverJournal, Refused, Unknown, HASH

MAX_FILE=1024*1024
FILE_ROLES={
    'source_gateway':('hermes-gateway.service.conf',0o644),
    'source_serve':('hermes-serve.service.conf',0o644),
    'source_client':('hermes-client',0o755),
    'source_server':('hermes-serve',0o755),
    'runtime_client':('hermes-client',0o755),
    'runtime_server':('hermes-serve',0o755),
    'policy':('workspace-read-policy.json',0o600),
    'gateway_link':('100-signal-grove.conf',0o777),
    'serve_link':('100-signal-grove.conf',0o777),
    'client_link':('hermes',0o777),
}
MODE_ROLES=('account_config','account_bin','projects','fleet','runtime_generations','source_bin','source_systemd')
STAGE_ROLES={
    'publish_reviewed':frozenset(tuple(FILE_ROLES)[:6]),'install_policy':frozenset({'policy'}),
    'bind_gateway':frozenset({'gateway_link'}),'bind_serve':frozenset({'serve_link'}),'bind_cli':frozenset({'client_link'}),
    'rollback_policy':frozenset({'policy'}),'rollback_runtime':frozenset({'gateway_link','serve_link','client_link'}),
    'secure_modes':frozenset(MODE_ROLES),
}


def digest(value):return hashlib.sha256(json.dumps(value,sort_keys=True,separators=(',',':'),allow_nan=False).encode()).hexdigest()
def _uint(value):return type(value) is int and 0<=value<2**64
def _hash(value):return type(value) is str and HASH.fullmatch(value) is not None
def _path(value):return (type(value) is str and re.fullmatch(r'/[A-Za-z0-9_./-]{1,2047}',value) is not None and not value.startswith('//')
    and '\0' not in value and str(PurePosixPath(value))==value and '..' not in PurePosixPath(value).parts)
def _stamp(info):return (info.st_dev,info.st_ino,info.st_mode,info.st_uid,info.st_gid,info.st_nlink,info.st_size,info.st_mtime_ns,info.st_ctime_ns)


@dataclass(frozen=True)
class DirectoryAnchor:
    path:str
    device:int
    inode:int
    uid:int
    gid:int
    mode:int

    def __post_init__(self):
        if (not _path(self.path) or any(not _uint(n) for n in (self.device,self.inode,self.uid,self.gid))
                or not self.inode or not self.uid or type(self.mode) is not int or self.mode not in (0o700,0o755,0o775)):
            raise ValueError('Exact owner directory identity and reviewed mode required')


@dataclass(frozen=True)
class DirectoryCreation:
    """One reviewed absent child; no recursive mkdir or adoption of an arrival."""
    parent:DirectoryAnchor
    name:str

    def __post_init__(self):
        if (type(self.parent) is not DirectoryAnchor or self.parent.mode not in (0o700,0o755)
                or type(self.name) is not str or not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]{0,95}',self.name)
                or self.name in ('.','..')):
            raise ValueError('Exact safe parent and one ordinary child component required')
    @property
    def path(self):return self.parent.path+'/'+self.name
    @property
    def uid(self):return self.parent.uid
    @property
    def gid(self):return self.parent.gid
    @property
    def mode(self):return 0o700


@dataclass(frozen=True)
class FileImage:
    kind:str
    device:int=0
    inode:int=0
    mode:int=0
    uid:int=0
    gid:int=0
    links:int=0
    size:int=0
    mtime_ns:int=0
    ctime_ns:int=0
    sha256:str=''
    target:str=''

    def __post_init__(self):
        values=(self.device,self.inode,self.uid,self.gid,self.links,self.size,self.mtime_ns,self.ctime_ns)
        if self.kind=='absent':
            valid=all(type(n) is int and n==0 for n in (*values,self.mode)) and self.sha256==self.target==''
        else:
            valid=(self.kind in ('file','link') and all(_uint(n) for n in values) and self.inode>0 and self.uid>0
                and self.links==1 and type(self.mode) is int and _hash(self.sha256)
                and (self.mode in (0o600,0o644,0o755) and self.size<=MAX_FILE and self.target=='' if self.kind=='file' else
                     self.mode==0o777 and type(self.target) is str and 0<len(self.target)<=2048 and '\0' not in self.target))
        if not valid:raise ValueError('Exact bounded regular-file/link image or verified absence required')

    @property
    def sha(self):return digest(asdict(self))


@dataclass(frozen=True)
class FileBinding:
    role:str
    parent:DirectoryAnchor|DirectoryCreation
    before:FileImage
    kind:str
    value:bytes|str|None=field(repr=False)

    def __post_init__(self):
        if (type(self.role) is not str or self.role not in FILE_ROLES or type(self.parent) not in (DirectoryAnchor,DirectoryCreation)
                or self.parent.mode not in (0o700,0o755) or type(self.before) is not FileImage
                or (self.before.kind!='absent' and self.before.uid!=self.parent.uid)
                or (type(self.parent) is DirectoryCreation and self.before.kind!='absent')
                or self.kind not in ('file','link','absent')):raise ValueError('One exact role/parent/preimage required')
        if self.kind=='absent':valid=self.value is None
        elif self.role.endswith('_link'):valid=(self.kind=='link' and _path(self.value)) or (
            self.role=='client_link' and self.kind=='file' and type(self.value) is bytes and 0<len(self.value)<=MAX_FILE)
        else:valid=self.kind=='file' and type(self.value) is bytes and 0<len(self.value)<=MAX_FILE
        if not valid:raise ValueError('Reviewed bounded bytes or literal link target required')

    @property
    def name(self):return FILE_ROLES[self.role][0]
    @property
    def mode(self):return 0 if self.kind=='absent' else 0o755 if self.role=='client_link' and self.kind=='file' else FILE_ROLES[self.role][1]
    def evidence(self):return dict(role=self.role,parent=asdict(self.parent),before=asdict(self.before),kind=self.kind,mode=self.mode,
        bytes=len(self.value) if type(self.value) is bytes else None,
        payload_sha256=hashlib.sha256(self.value).hexdigest() if type(self.value) is bytes else None,
        target=self.value if type(self.value) is str else None)


@dataclass(frozen=True)
class ModeBinding:
    role:str
    directory:DirectoryAnchor

    def __post_init__(self):
        if self.role not in MODE_ROLES or type(self.directory) is not DirectoryAnchor or self.directory.mode!=0o775:
            raise ValueError('Only exact named 0775 to 0755 directory changes are allowed')
    def evidence(self):return dict(role=self.role,directory=asdict(self.directory),after_mode=0o755)


def anchor(path):
    """Read only. Protected ancestry/caller approval are separate packet gates."""
    path=Path(path)
    if not _path(str(path)) or path.resolve()!=path:raise Refused('Literal native directory required')
    info=path.lstat()
    if not stat.S_ISDIR(info.st_mode):raise Refused('Native directory required')
    return DirectoryAnchor(str(path),info.st_dev,info.st_ino,info.st_uid,info.st_gid,stat.S_IMODE(info.st_mode))


def _directory(fd,expected):
    info=os.fstat(fd);named=os.stat(expected.path,follow_symlinks=False)
    if (not stat.S_ISDIR(info.st_mode) or not stat.S_ISDIR(named.st_mode) or Path(expected.path).resolve()!=Path(expected.path)
            or (info.st_dev,info.st_ino,info.st_uid,info.st_gid,stat.S_IMODE(info.st_mode))!=
                (expected.device,expected.inode,expected.uid,expected.gid,expected.mode)
            or (named.st_dev,named.st_ino)!=(expected.device,expected.inode) or info.st_uid!=os.geteuid()):
        raise Unknown('Bound native directory changed')


def _open_directory(expected):
    fd=os.open(expected.path,os.O_RDONLY|os.O_DIRECTORY|os.O_NOFOLLOW|os.O_CLOEXEC)
    try:_directory(fd,expected);return fd
    except BaseException:os.close(fd);raise


def _image(fd,name,uid):
    try:info=os.stat(name,dir_fd=fd,follow_symlinks=False)
    except FileNotFoundError:return FileImage('absent')
    if info.st_uid!=uid or info.st_nlink!=1:raise Refused('Foreign or hardlinked entry refused')
    target=''
    if stat.S_ISREG(info.st_mode):
        if info.st_size>MAX_FILE:raise Refused('File exceeds reviewed bound')
        opened=os.open(name,os.O_RDONLY|os.O_NOFOLLOW|os.O_NONBLOCK|os.O_CLOEXEC,dir_fd=fd)
        try:
            if _stamp(os.fstat(opened))!=_stamp(info):raise Unknown('File identity changed before read')
            data=os.pread(opened,MAX_FILE+1,0)
            if len(data)!=info.st_size or len(data)>MAX_FILE or _stamp(os.fstat(opened))!=_stamp(info):raise Unknown('File changed during bounded read')
        finally:os.close(opened)
        kind='file'
    elif stat.S_ISLNK(info.st_mode):
        target=os.readlink(name,dir_fd=fd);data=target.encode();kind='link'
    else:raise Refused('Only bounded regular files and literal links may be published')
    if _stamp(os.stat(name,dir_fd=fd,follow_symlinks=False))!=_stamp(info):raise Unknown('Entry changed during observation')
    return FileImage(kind,info.st_dev,info.st_ino,stat.S_IMODE(info.st_mode),info.st_uid,info.st_gid,info.st_nlink,
        info.st_size,info.st_mtime_ns,info.st_ctime_ns,hashlib.sha256(data).hexdigest(),target)


def observe(parent,role):
    if type(parent) is not DirectoryAnchor or role not in FILE_ROLES:raise ValueError('Exact bound parent and role required')
    fd=_open_directory(parent)
    try:result=_image(fd,FILE_ROLES[role][0],parent.uid);_directory(fd,parent);return result
    finally:os.close(fd)


def _same_renamed(actual,expected):
    # rename legitimately changes ctime; all other identity/content fields stay.
    a,b=asdict(actual),asdict(expected);a.pop('ctime_ns');b.pop('ctime_ns');return a==b


def _renameat2(fd,source,target,flags):
    if type(flags) is not int or flags not in (1,2) or any('/' in n or not n or n in ('.','..') for n in (source,target)):
        raise Refused('Only exact same-directory no-replace/exchange allowed')
    libc=ctypes.CDLL(None,use_errno=True);call=getattr(libc,'renameat2',None)
    if call is None:raise Refused('Native atomic publication is unavailable; no fallback')
    call.argtypes=(ctypes.c_int,ctypes.c_char_p,ctypes.c_int,ctypes.c_char_p,ctypes.c_uint);call.restype=ctypes.c_int
    if call(fd,os.fsencode(source),fd,os.fsencode(target),flags)!=0:
        raise OSError(ctypes.get_errno(),'Native atomic publication refused')


class _Receipt:
    def __init__(self,journal,execution,stage,catalog):
        self.journal=journal;self.data=b'';self.fd=None;self.name='native-files-'+execution.operation+'-'+stage+'.jsonl'
        journal.assert_current()
        try:
            self.fd=os.open(self.name,os.O_RDWR|os.O_CREAT|os.O_EXCL|os.O_APPEND|os.O_NOFOLLOW|os.O_CLOEXEC,0o600,dir_fd=journal._root)
            self.identity=journal._check(self.fd);os.fsync(journal._root)
            self.append('begin',dict(operation=execution.operation,authorization_sha256=execution.binding_sha256,stage=stage,catalog_sha256=catalog))
        except BaseException:
            self.close();raise
    def check(self):
        self.journal.assert_current();info=os.stat(self.name,dir_fd=self.journal._root,follow_symlinks=False)
        if (info.st_dev,info.st_ino)!=self.identity or self.journal._check(self.fd)!=self.identity or os.pread(self.fd,2*MAX_FILE+1,0)!=self.data:
            raise Unknown('Native effect receipt changed')
    def append(self,phase,evidence):
        self.check();line=(json.dumps(dict(seq=self.data.count(b'\n')+1,phase=phase,evidence=evidence),sort_keys=True,separators=(',',':'))+'\n').encode()
        if len(line)>65536 or len(self.data)+len(line)>2*MAX_FILE:raise Unknown('Native evidence bound exceeded')
        remaining=memoryview(line)
        while remaining:
            written=os.write(self.fd,remaining)
            if type(written) is not int or not 0<written<=len(remaining):raise Unknown('Native receipt write is unproven')
            remaining=remaining[written:]
        os.fsync(self.fd);self.data+=line;self.check();os.fsync(self.journal._root)
    def close(self):
        if self.fd is not None:os.close(self.fd);self.fd=None


@dataclass(frozen=True)
class AncestryNode:
    path:str
    device:int
    inode:int
    uid:int
    gid:int
    mode:int

    def __post_init__(self):
        if ((self.path!='/' and not _path(self.path)) or any(not _uint(v) for v in (self.device,self.inode,self.uid,self.gid))
                or not self.inode or type(self.mode) is not int or not 0<=self.mode<=0o777):
            raise ValueError('Exact native directory ancestry identity required')


class NativeAncestry:
    """Read-only path witness; never permission, publication or approval issuer.

    Production walks begin at /. Owned fixtures name an explicit private root;
    ancestors above that root are no security acceptance. The original catalog
    stays immutable; only actual actor-observed mode/parent transitions advance
    the in-memory witness. A crash needs explicit read-only reconciliation.
    """
    @classmethod
    def capture(cls,scope,root,endpoints,modes,creations,capture_sha256):
        if (sys.platform!='linux' or os.geteuid()==0 or scope not in ('production_review_only','owned_fixture_only')
                or type(endpoints) is not tuple or not 1<=len(endpoints)<=32 or tuple(sorted(set(endpoints)))!=endpoints
                or type(modes) is not tuple or len(modes)!=len(MODE_ROLES) or any(type(m) is not ModeBinding for m in modes)
                or {m.role for m in modes}!=set(MODE_ROLES) or type(creations) is not tuple
                or any(type(c) is not DirectoryCreation for c in creations) or len(set(creations))!=len(creations)
                or not _hash(capture_sha256)):
            raise ValueError('Exact native closed ancestry inputs required')
        if (scope=='production_review_only' and root!='/') or (scope=='owned_fixture_only' and (
                not _path(root) or not re.fullmatch(r'signal-grove-catalog-[a-f0-9]{32}',PurePosixPath(root).name))):
            raise ValueError('Production root or explicit private owned fixture root required')
        base=PurePosixPath(root)
        if any(not _path(p) or not PurePosixPath(p).is_relative_to(base) for p in endpoints):
            raise ValueError('Every exact endpoint must be within the bound root')
        mode_paths={m.directory.path:m for m in modes}
        missing={c.path:c for c in creations}
        if len(mode_paths)!=len(modes) or any(p not in endpoints for p in missing):
            raise ValueError('Unique named mode paths and endpoint creations required')
        self=cls();self.scope,self.root,self.endpoints,self.modes,self.creations,self.capture_sha256=scope,root,endpoints,modes,creations,capture_sha256
        self._secured=set();self._created={};self._nodes={};self._missing=set(missing)
        self._capture=True
        try:
            for endpoint in endpoints:self._walk(endpoint)
            if set(mode_paths)-set(self._nodes):raise Refused('A named mode role is outside the protected ancestry')
            for path,binding in mode_paths.items():
                if asdict(self._nodes[path])!=asdict(binding.directory):raise Refused('Named mode identity differs from the captured renderer')
            for path,creation in missing.items():
                node=self._nodes.get(creation.parent.path)
                if node is None or asdict(node)!=dict(asdict(creation.parent),mode=mode_paths[creation.parent.path].directory.mode if creation.parent.path in mode_paths else creation.parent.mode):
                    raise Refused('Creation parent differs from the captured renderer')
            if scope=='owned_fixture_only' and (self._nodes[root].uid!=os.geteuid() or self._nodes[root].mode!=0o700):
                raise Refused('Owned fixture ancestry root must be private')
        finally:self._capture=False
        self.check();return self

    def metadata(self):
        return dict(scope=self.scope,root=self.root,endpoints=self.endpoints,capture_sha256=self.capture_sha256,
            directories=[asdict(self._nodes[p]) for p in sorted(self._nodes)],
            modes=[m.evidence() for m in self.modes],missing=sorted(self._missing))

    @property
    def sha256(self):return digest(self.metadata())
    @property
    def state_sha256(self):return digest(dict(catalog_sha256=self.sha256,secured=sorted(self._secured),created=[asdict(self._created[p]) for p in sorted(self._created)]))

    def _expected(self,path):
        if path in self._created:return AncestryNode(**asdict(self._created[path]))
        node=self._nodes.get(path)
        if node is not None and any(m.role in self._secured and m.directory.path==path for m in self.modes):return replace(node,mode=0o755)
        return node

    def _node(self,fd,path):
        st=os.fstat(fd)
        if not stat.S_ISDIR(st.st_mode):raise Refused('Protected ancestry requires native directories')
        try:node=AncestryNode(path,st.st_dev,st.st_ino,st.st_uid,st.st_gid,stat.S_IMODE(st.st_mode))
        except ValueError:raise Refused('Unsupported protected-directory native mode') from None
        if self._capture:
            allowed=next((m.directory for m in self.modes if m.directory.path==path),None)
            if (node.uid not in (0,os.geteuid()) or (node.mode&0o022 and (allowed is None or asdict(node)!=asdict(allowed)))):
                raise Refused('Unreviewed writable or foreign ancestry refused')
            if path in self._nodes and self._nodes[path]!=node:raise Unknown('Ancestry changed during native capture')
            self._nodes[path]=node
        elif node!=self._expected(path):raise Unknown('Bound protected ancestry changed')
        return node

    def _root_fd(self):
        flags=os.O_RDONLY|os.O_DIRECTORY|os.O_NOFOLLOW|os.O_CLOEXEC
        fd=os.open('/',flags)
        try:
            if self.root!='/':
                for part in PurePosixPath(self.root).parts[1:]:
                    child=os.open(part,flags,dir_fd=fd);os.close(fd);fd=child
            self._node(fd,self.root);return fd
        except BaseException:os.close(fd);raise

    def _walk(self,path,*,keep=False):
        fd=None
        try:
            fd=self._root_fd();current=PurePosixPath(self.root)
            parts=PurePosixPath(path).relative_to(current).parts
            for index,part in enumerate(parts):
                candidate=str(current/part)
                if candidate in self._missing and candidate not in self._created:
                    if index!=len(parts)-1:raise Refused('Recursive missing protected ancestors refused')
                    try:os.stat(part,dir_fd=fd,follow_symlinks=False)
                    except FileNotFoundError:return None
                    raise Refused('A declared absent ancestry parent arrived; never adopt it')
                child=os.open(part,os.O_RDONLY|os.O_DIRECTORY|os.O_NOFOLLOW|os.O_CLOEXEC,dir_fd=fd)
                try:self._node(child,candidate)
                except BaseException:os.close(child);raise
                os.close(fd);fd=child;current=current/part
            if keep:result=fd;fd=None;return result
            return self._expected(path) if not self._capture else self._nodes[path]
        except (OSError,ValueError):raise Refused('Protected descriptor traversal refused') from None
        finally:
            if fd is not None:os.close(fd)

    def check(self):
        for endpoint in self.endpoints:self._walk(endpoint)
        return self.state_sha256

    def bind_stage(self,stage,bindings):
        self.check()
        if stage=='secure_modes':
            if self._secured or tuple(bindings)!=self.modes:raise Refused('Exact initial seven-mode ancestry catalog required')
        else:
            if self._secured!=set(MODE_ROLES):raise Refused('All seven protected modes must be observed before publication')
            for binding in bindings:
                parent=binding.parent
                if parent.path not in self.endpoints:raise Refused('Publication parent is outside the closed ancestry catalog')
                if type(parent) is DirectoryCreation:
                    if parent not in self.creations or parent.path in self._created:raise Refused('Exact unspent parent-creation witness required')
                elif self._expected(parent.path)!=AncestryNode(**asdict(parent)):
                    raise Refused('Publication parent differs from the protected ancestry')

    def open_directory(self,expected):
        if type(expected) is not DirectoryAnchor or self._expected(expected.path)!=AncestryNode(**asdict(expected)):
            raise Refused('Exact currently bound publication directory required')
        self.check();return self._walk(expected.path,keep=True)

    def _mode_observed(self,binding):
        if binding not in self.modes or binding.role in self._secured:raise Refused('Mode witness is not an unspent reviewed role')
        self._secured.add(binding.role);self.check()

    def _parent_observed(self,creation,actual):
        if (creation not in self.creations or creation.path in self._created or type(actual) is not DirectoryAnchor
                or actual.path!=creation.path or (actual.uid,actual.gid,actual.mode)!=(creation.uid,creation.gid,0o700)):
            raise Refused('Exact newly witnessed native parent required')
        self._created[creation.path]=actual;self.check()


class NativeFilePublication:
    """Exact parent/one-level creation effects; no step is marked verified here.

    The external caller must bind expected_catalog_sha256 to the actual reviewed
    packet and enforce real source/work/backup/native ancestry observations. This
    class validates that binding, the original live journal claim and native
    effects. It does not manufacture approval or attest those other gates.
    """
    def __init__(self,plan,journal,stage,bindings,expected_catalog_sha256,*,ancestry=None,expected_ancestry_sha256=None):
        if (sys.platform!='linux' or os.geteuid()==0 or type(plan) is not Plan or type(journal) is not CutoverJournal
                or type(stage) is not str or stage not in STAGE_ROLES or type(bindings) is not tuple or not bindings or not _hash(expected_catalog_sha256)):
            raise ValueError('Exact Linux stage, journal, plan and reviewed catalog required')
        expected_type=ModeBinding if stage=='secure_modes' else FileBinding
        if any(type(b) is not expected_type for b in bindings) or {b.role for b in bindings}!=STAGE_ROLES[stage] or len(bindings)!=len(STAGE_ROLES[stage]):
            raise ValueError('Complete closed role catalog required for this stage')
        locations=[(b.directory.device,b.directory.inode) if stage=='secure_modes' else
                   (('new',b.parent.parent.device,b.parent.parent.inode,b.parent.name,b.name) if type(b.parent) is DirectoryCreation
                    else ('existing',b.parent.device,b.parent.inode,b.name)) for b in bindings]
        if len(set(locations))!=len(locations):raise ValueError('Duplicate native mutation targets refused')
        if stage not in ('rollback_policy','rollback_runtime') and any(type(b) is FileBinding and b.kind=='absent' for b in bindings):
            raise ValueError('Only separately approved recovery may retire a reference')
        if stage not in ('rollback_policy','rollback_runtime') and any(type(b) is FileBinding and b.role.endswith('_link') and b.kind!='link' for b in bindings):
            raise ValueError('Forward reference publication requires the reviewed literal link')
        if stage in ('rollback_policy','rollback_runtime') and any(type(b.parent) is DirectoryCreation for b in bindings):
            raise ValueError('Recovery requires observed existing parents; absent-parent reconciliation is read only')
        self.plan,self.journal,self.stage,self.bindings=plan,journal,stage,bindings
        self.catalog=digest([b.evidence() for b in bindings])
        if self.catalog!=expected_catalog_sha256:raise Refused('Actual catalog differs from the reviewed binding')
        if ancestry is None:
            if expected_ancestry_sha256 is not None:raise ValueError('A hash without a native ancestry witness is insufficient')
        elif type(ancestry) is not NativeAncestry or ancestry.sha256!=expected_ancestry_sha256:
            raise Refused('Exact reviewed native ancestry binding required')
        if ancestry is not None:ancestry.bind_stage(stage,bindings)
        self.ancestry=ancestry
        self.used=False;self.state='ready';self.last_receipt=None;self.resolved_bindings=()
    def _authorize(self,execution):
        if (type(execution) is not ApprovedExecution or execution.authority_sha256!=self.plan.authority_sha256
                or (execution.scope=='forward')!=(self.stage in FORWARD)):
            raise Refused('Exact native file execution approval required')
        now=time.monotonic()
        if not math.isfinite(now) or now>=execution.deadline:raise Unknown('Native publication deadline elapsed')
        self.journal.assert_current();view=self.journal.view;stages=FORWARD if execution.scope=='forward' else RECOVERY
        if (view['state']!='pending' or view['pending']!=self.stage or view['operation']!=execution.operation
                or view['authorization_sha256']!=execution.binding_sha256 or view['plan_sha256']!=execution.packet_sha256
                or view['root_plan_sha256']!=self.plan.packet_sha256 or view['mode']!=execution.scope
                or (execution.scope=='forward' and execution.packet_sha256!=self.plan.packet_sha256)):
            raise Refused('Exact native file stage is not durably authorized')
        rows=[r for r in view['records'] if r['operation']==execution.operation];index=stages.index(self.stage)
        if ([r['step'] for r in rows if r['kind']=='intent']!=list(stages[:index+1])
                or [r['step'] for r in rows if r['kind']=='verified']!=list(stages[:index])):
            raise Refused('Fixed native file stage prefix has not been verified')
        if self.ancestry is not None:self.ancestry.check()
    def _prepare_parent(self,creation,fd,execution,receipt):
        self._authorize(execution);receipt.check();_directory(fd,creation.parent)
        temporary='.signal-grove-'+execution.operation+'-'+uuid.uuid4().hex[:16]+'.directory'
        receipt.append('parent_intent',dict(parent=asdict(creation.parent),name=creation.name,mode=0o700,temporary=temporary))
        self._authorize(execution);_directory(fd,creation.parent);opened=None
        try:
            # A unique private child is opened/bound before its final name exists.
            os.mkdir(temporary,0o700,dir_fd=fd);os.fsync(fd)
            prepared=anchor(Path(creation.parent.path)/temporary)
            if prepared.mode!=0o700 or prepared.uid!=creation.uid or prepared.gid!=creation.gid or prepared.device!=creation.parent.device:
                raise Unknown('Prepared parent identity differs from reviewed owner/mode')
            opened=_open_directory(prepared)
            if os.listdir(opened):raise Unknown('Prepared private parent is not empty')
            receipt.append('parent_prepared',dict(image=asdict(prepared),name=creation.name,temporary=temporary))
            self._authorize(execution);_directory(fd,creation.parent);_directory(opened,prepared)
            _renameat2(fd,temporary,creation.name,1);os.fsync(opened);os.fsync(fd)
            actual=replace(prepared,path=creation.path);_directory(opened,actual)
            if os.listdir(opened):raise Unknown('New parent changed before file publication')
            receipt.append('parent_observed',dict(image=asdict(actual),temporary=temporary,name=creation.name))
            if self.ancestry is not None:self.ancestry._parent_observed(creation,actual)
            self._authorize(execution);result=(opened,actual);opened=None;return result
        finally:
            if opened is not None:os.close(opened)

    def run(self,execution):
        if self.used:raise Refused('This native publication instance is spent')
        self._authorize(execution);fds={};receipt=None;claimed=False
        try:
            for b in self.bindings:
                parent=b.directory if type(b) is ModeBinding else b.parent
                directory=parent.parent if type(parent) is DirectoryCreation else parent
                key=(directory.device,directory.inode)
                if key not in fds:fds[key]=self.ancestry.open_directory(directory) if self.ancestry is not None else _open_directory(directory)
                if type(parent) is DirectoryCreation:
                    try:os.stat(parent.name,dir_fd=fds[key],follow_symlinks=False)
                    except FileNotFoundError:pass
                    else:raise Refused('A declared absent parent already exists; never adopt it')
                elif type(b) is FileBinding and _image(fds[key],b.name,directory.uid)!=b.before:
                    raise Refused('Exact native file preimage changed')
            self._authorize(execution);self.journal.claim_dispatch(execution.operation,self.stage,execution.binding_sha256)
            claimed=True;self.used=True;self.state='running'
            receipt=_Receipt(self.journal,execution,self.stage,self.catalog);self.last_receipt=receipt.name
            if self.ancestry is not None:receipt.append('ancestry_bound',dict(catalog_sha256=self.ancestry.sha256,state_sha256=self.ancestry.check()))
            resolved={}
            for creation in dict.fromkeys(b.parent for b in self.bindings if type(b) is FileBinding and type(b.parent) is DirectoryCreation):
                fd,actual=self._prepare_parent(creation,fds[(creation.parent.device,creation.parent.inode)],execution,receipt)
                if (actual.device,actual.inode) in fds:
                    os.close(fd);raise Unknown('New parent aliases an existing bound directory')
                fds[(actual.device,actual.inode)]=fd;resolved[creation]=actual
            self.resolved_bindings=tuple(replace(b,parent=resolved[b.parent]) if type(b) is FileBinding and type(b.parent) is DirectoryCreation else b for b in self.bindings)
            if self.stage!='secure_modes':
                receipt.append('resolved_catalog',dict(intent_catalog_sha256=self.catalog,
                    native_catalog_sha256=digest([b.evidence() for b in self.resolved_bindings])))
                # All old/new file preimages remain checked before the first file effect.
                for b in self.resolved_bindings:
                    _directory(fds[(b.parent.device,b.parent.inode)],b.parent)
                    if _image(fds[(b.parent.device,b.parent.inode)],b.name,b.parent.uid)!=b.before:
                        raise Unknown('Native file preimage changed after parent preparation')
            for b in self.resolved_bindings:
                self._authorize(execution);receipt.check()
                directory=b.directory if type(b) is ModeBinding else b.parent;fd=fds[(directory.device,directory.inode)]
                _directory(fd,directory)
                if type(b) is ModeBinding:
                    receipt.append('mode_intent',b.evidence());self._authorize(execution);_directory(fd,directory)
                    os.fchmod(fd,0o755);os.fsync(fd)
                    after=DirectoryAnchor(directory.path,directory.device,directory.inode,directory.uid,directory.gid,0o755)
                    _directory(fd,after);receipt.append('mode_observed',dict(role=b.role,after=asdict(after)))
                    if self.ancestry is not None:self.ancestry._mode_observed(b)
                    self._authorize(execution);continue
                if _image(fd,b.name,directory.uid)!=b.before:raise Unknown('Native file changed after intent')
                temporary='.signal-grove-'+execution.operation+'-'+b.role+'-'+uuid.uuid4().hex[:16]+'.incoming'
                receipt.append('entry_intent',dict(**b.evidence(),temporary=temporary));self._authorize(execution)
                wanted_hash=hashlib.sha256(b.value if type(b.value) is bytes else b.value.encode()).hexdigest() if b.kind!='absent' else None
                if ((b.kind=='absent' and b.before.kind=='absent') or
                        (b.before.kind==b.kind and b.before.mode==b.mode and b.before.sha256==wanted_hash)):
                    if _image(fd,b.name,directory.uid)!=b.before:raise Unknown('No-op target changed')
                    receipt.append('entry_unchanged',dict(role=b.role,image=asdict(b.before)));self._authorize(execution);continue
                if b.kind=='file':
                    created=os.open(temporary,os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW|os.O_CLOEXEC,0o600,dir_fd=fd)
                    try:
                        view=memoryview(b.value)
                        while view:
                            written=os.write(created,view)
                            if type(written) is not int or not 0<written<=len(view):raise Unknown('Prepared entry write is unproven')
                            view=view[written:]
                        os.fchmod(created,b.mode);os.fsync(created)
                    finally:os.close(created)
                elif b.kind=='link':os.symlink(b.value,temporary,dir_fd=fd)
                prepared=_image(fd,temporary,directory.uid)
                if b.kind=='absent':
                    if prepared.kind!='absent':raise Unknown('Retirement slot unexpectedly exists')
                elif (prepared.kind!=b.kind or prepared.mode!=b.mode or prepared.sha256!=wanted_hash
                        or prepared.uid!=directory.uid or prepared.gid!=directory.gid):
                    raise Unknown('Prepared native entry differs from reviewed content')
                os.fsync(fd);receipt.append('entry_prepared',dict(role=b.role,image=asdict(prepared),temporary=temporary))
                self._authorize(execution);_directory(fd,directory)
                if _image(fd,b.name,directory.uid)!=b.before:raise Unknown('Target changed before atomic publication')
                if _image(fd,temporary,directory.uid)!=prepared:raise Unknown('Prepared entry changed before publication')
                if b.kind=='absent':_renameat2(fd,b.name,temporary,1)
                else:_renameat2(fd,temporary,b.name,1 if b.before.kind=='absent' else 2)
                os.fsync(fd);_directory(fd,directory)
                after=_image(fd,b.name,directory.uid);displaced=_image(fd,temporary,directory.uid)
                receipt.append('entry_observed',dict(role=b.role,after=asdict(after),displaced=asdict(displaced),temporary=temporary))
                if b.kind=='absent':valid=after.kind=='absent' and _same_renamed(displaced,b.before)
                else:valid=_same_renamed(after,prepared) and (displaced.kind=='absent' if b.before.kind=='absent' else _same_renamed(displaced,b.before))
                if not valid:raise Unknown('Atomic effect differs; retain both names without automatic compensation')
                self._authorize(execution)
            receipt.append('catalog_effects_observed',dict(catalog_sha256=self.catalog,roles=[b.role for b in self.bindings]));self._authorize(execution)
            self.state='effects_observed';return dict(state=self.state,stage=self.stage,catalog_sha256=self.catalog,receipt=receipt.name,receipt_sha256=hashlib.sha256(receipt.data).hexdigest(),ancestry_sha256=self.ancestry.sha256 if self.ancestry is not None else None,ancestry_state_sha256=self.ancestry.check() if self.ancestry is not None else None)
        except BaseException as exc:
            if claimed:
                self.state='unknown'
                if not isinstance(exc,Exception):raise
                raise Unknown('Native file outcome uncertain; preserve entries and receipt without replay') from None
            if not isinstance(exc,Exception) or isinstance(exc,(Refused,Unknown)):raise
            raise Refused('Native file preflight refused without dispatch') from None
        finally:
            if receipt is not None:receipt.close()
            for fd in fds.values():os.close(fd)
