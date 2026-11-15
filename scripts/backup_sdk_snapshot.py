"""Pinned SDK backup child and strict source-selection proof, never restore.

This implementation requires file-backed configuration with built-in memory.
Any configured external provider is an explicit held completeness prerequisite;
the SDK's catch-and-return-empty provider helper is not evidence of no state.
"""
from dataclasses import dataclass,asdict
import hashlib
import importlib.abc
import importlib.machinery
import importlib.util
import json
import os
from pathlib import Path,PurePosixPath
import re
import stat
import sys
from types import SimpleNamespace

from cutover_journal import Refused,Unknown
from native_file_publication import DirectoryAnchor,anchor,digest
from native_backup_archive import BackupMember,selection_sha256

MAX_FILE=256*1024*1024
MAX_INVENTORY_BYTES=1024*1024*1024
MAX_PAYLOAD=8*1024*1024
MAX_REPORT=8*1024*1024
SDK_REQUIRED=frozenset({'hermes_cli/__init__.py','hermes_cli/backup.py','hermes_cli/backup_sqlite.py','hermes_cli/backup_restore.py',
 'hermes_cli/archive_safe.py','hermes_cli/home_data_layout.py','hermes_cli/config.py','hermes_cli/config_backend.py','hermes_constants.py','hermes_yaml.py','plugins/memory/__init__.py'})

def _hash(value):return type(value) is str and re.fullmatch('[a-f0-9]{64}',value) is not None
def _path(value):return type(value) is str and 1<len(value)<=4096 and value.startswith('/') and not value.startswith('//') and '\\' not in value and not any(c in value for c in '\0\r\n') and str(PurePosixPath(value))==value and '..' not in PurePosixPath(value).parts
def _relative(value):return type(value) is str and 0<len(value)<=4096 and not value.startswith('/') and '\\' not in value and not any(c in value for c in '\0\r\n') and str(PurePosixPath(value))==value and '..' not in PurePosixPath(value).parts and value!='.'
def stamp(s):return (s.st_dev,s.st_ino,s.st_uid,s.st_gid,s.st_mode,s.st_nlink,s.st_size,s.st_mtime_ns,s.st_ctime_ns)

def _valid_file(path,h,identity,code=False):
    if (not _path(path) or not _hash(h) or type(identity) is not tuple or len(identity)!=9 or any(type(v) is not int or v<0 for v in identity)
        or not identity[1] or not identity[2] or not stat.S_ISREG(identity[4])):return False
    mode=stat.S_IMODE(identity[4])
    # PM's cache can hard-link installed code. Preserve the actual link count,
    # inode and times in its witness; hash every imported source before execute.
    if code:return identity[5]>=1 and bool(mode&0o400) and not mode&0o7022
    return mode in (0o600,0o644,0o755) and identity[5]==1

@dataclass(frozen=True)
class NativeInput:
    path:str
    sha256:str
    identity:tuple
    def __post_init__(self):
        if not _valid_file(self.path,self.sha256,self.identity):
            raise ValueError('Literal owner file identity and content digest required')
    @property
    def bytes(self):return self.identity[6]

@dataclass(frozen=True)
class ReviewedCodeInput(NativeInput):
    def __post_init__(self):
        if not _valid_file(self.path,self.sha256,self.identity,True):raise ValueError('Protected reviewed code identity and digest required')

def _read_input(path,limit,keep_data,code=False):
    """No mutation; open the exact regular owner file and compare both views."""
    path=Path(path)
    if sys.platform!='linux' or not _path(str(path)) or path.parent.resolve()!=path.parent or type(limit) is not int or not 0<limit<=2*1024**3:raise Refused('Bounded literal native input required')
    try:fd=os.open(path,os.O_RDONLY|os.O_NOFOLLOW|os.O_NONBLOCK|os.O_CLOEXEC)
    except FileNotFoundError:raise
    except OSError:raise Refused('Native input open refused') from None
    try:
        before=os.fstat(fd)
        if before.st_uid!=os.geteuid() or not _valid_file(str(path),'a'*64,stamp(before),code) or not 0<=before.st_size<=limit:raise Refused('Private or non-writable owner input required')
        data=bytearray();count=0;h=hashlib.sha256()
        while block:=os.read(fd,min(1024*1024,limit+1-count)):
            count+=len(block);h.update(block)
            if keep_data:data.extend(block)
            if count>limit:raise Refused('Input exceeds its bound')
        if count!=before.st_size or stamp(os.fstat(fd))!=stamp(before) or stamp(path.lstat())!=stamp(before):raise Unknown('Native input changed while read')
        cls=ReviewedCodeInput if code else NativeInput
        return bytes(data) if keep_data else None,cls(str(path),h.hexdigest(),stamp(before))
    finally:os.close(fd)

def read_input(path,*,limit=MAX_FILE):return _read_input(path,limit,True)
def observe_input(path,*,limit=MAX_FILE):return _read_input(path,limit,False)[1]
def read_code(path,*,limit=MAX_FILE):return _read_input(path,limit,True,True)
def observe_code(path,*,limit=MAX_FILE):return _read_input(path,limit,False,True)[1]

@dataclass(frozen=True)
class SourceInventory:
    root:DirectoryAnchor
    files:tuple[tuple[str,str],...]
    def __post_init__(self):
        if (type(self.root) is not DirectoryAnchor or self.root.mode not in (0o700,0o755) or type(self.files) is not tuple or not 1<=len(self.files)<=50000
            or any(type(p) is not tuple or len(p)!=2 for p in self.files) or tuple(sorted(self.files))!=self.files or len(dict(self.files))!=len(self.files)
            or any(not _relative(n) or not _hash(h) for n,h in self.files)):raise ValueError('Exact bounded owner source inventory required')
    @property
    def sha256(self):return digest(asdict(self))
    def observe(self):
        if anchor(self.root.path)!=self.root:raise Refused('Source inventory root changed')
        result={};total=0;parents={}
        for name,h in self.files:
            path=Path(self.root.path)/name
            current=path.parent
            while current.is_relative_to(self.root.path) and current not in parents:
                s=current.lstat()
                if current.resolve()!=current or not stat.S_ISDIR(s.st_mode) or s.st_uid!=os.geteuid() or stat.S_IMODE(s.st_mode) not in (0o700,0o755):raise Refused('Source input has unprotected ancestry')
                parents[current]=(s.st_dev,s.st_ino,s.st_mode,s.st_uid,s.st_gid);current=current.parent
            observed=observe_code(path);total+=observed.bytes
            if total>MAX_INVENTORY_BYTES or observed.sha256!=h:raise Refused('Reviewed source inventory changed or exceeds bound')
            result[name]=observed.identity
        for path,before in parents.items():
            s=path.lstat()
            if (s.st_dev,s.st_ino,s.st_mode,s.st_uid,s.st_gid)!=before:raise Unknown('Source directory identity changed')
        if anchor(self.root.path)!=self.root:raise Unknown('Source root changed during inventory')
        return result

def inventory_from(row):return SourceInventory(DirectoryAnchor(**row['root']),tuple(tuple(p) for p in row['files']))
def input_from(row):return NativeInput(row['path'],row['sha256'],tuple(row['identity']))

def unique_json(data):
    def pairs(rows):
        out={}
        for key,value in rows:
            if key in out:raise ValueError('Duplicate protocol field')
            out[key]=value
        return out
    return json.loads(data,object_pairs_hook=pairs,parse_constant=lambda _:(_ for _ in ()).throw(ValueError('Non-finite protocol value')))

class _SourceLoader(importlib.machinery.SourceFileLoader):
    def __init__(self,name,path,expected,recorded):super().__init__(name,path);self.expected,self.recorded=expected,recorded
    def get_code(self,fullname):
        source,image=read_code(self.path)
        if image.sha256!=self.expected:raise ImportError('Reviewed SDK/dependency source changed')
        self.recorded[self.path]=image.sha256
        return compile(source,self.path,'exec',dont_inherit=True) # Never execute an unchecked .pyc.

class ReviewedImports(importlib.abc.MetaPathFinder):
    def __init__(self,inventories):
        self.files={str(Path(i.root.path)/n):h for i in inventories for n,h in i.files};self.recorded={}
        self.directories={str(p) for name in self.files for p in Path(name).parents if str(p)!='/' and any(Path(name).is_relative_to(i.root.path) and p.is_relative_to(i.root.path) for i in inventories)}
    def find_spec(self,fullname,path=None,target=None):
        spec=importlib.machinery.PathFinder.find_spec(fullname,path,target)
        if spec is None:return None
        if spec.origin is None:
            if not spec.submodule_search_locations or any(str(Path(p)) not in self.directories or Path(p).resolve()!=Path(p) for p in spec.submodule_search_locations):raise ImportError('Unreviewed namespace import')
            return spec
        filename=Path(spec.origin)
        if filename.is_relative_to(sys.base_prefix) and 'site-packages' not in filename.parts:return spec
        expected=self.files.get(str(filename))
        if expected is None or filename.resolve()!=filename:raise ImportError('Unreviewed non-standard module import')
        if filename.suffix=='.py':
            loader=_SourceLoader(fullname,str(filename),expected,self.recorded)
            return importlib.util.spec_from_file_location(fullname,filename,loader=loader,submodule_search_locations=list(spec.submodule_search_locations) if spec.submodule_search_locations is not None else None)
        if filename.suffix=='.so':
            image=observe_code(filename)
            if image.sha256!=expected:raise ImportError('Reviewed native extension changed')
            self.recorded[str(filename)]=expected;return spec
        raise ImportError('Unreviewed module loader')

def require_builtin_memory_document(document):
    if document is None:return
    if type(document) is not dict:raise Refused('Backup configuration must be a mapping')
    memory=document.get('memory')
    if memory is None:return
    if type(memory) is not dict:raise Refused('Memory configuration must be a mapping')
    provider=memory.get('provider')
    if provider is not None and (type(provider) is not str or provider!=''):
        raise Refused('External memory provider requires its own reviewed complete backup path')

def _selected(backup,home,output):
    members=[];identities={}
    for path,relative in backup._iter_backup_files(home,output):
        name=relative.as_posix()
        if path!=home/relative or path.parent.resolve()!=path.parent:raise Refused('SDK selection escaped its literal home')
        s=path.lstat()
        if not stat.S_ISREG(s.st_mode) or s.st_uid!=os.geteuid() or s.st_nlink!=1 or stat.S_IMODE(s.st_mode) not in (0o600,0o644,0o755):raise Refused('SDK selection has an unprotected file')
        if path.suffix=='.db':
            member=BackupMember(name,'sqlite',None,None);identities[name]=(s.st_dev,s.st_ino,s.st_uid,s.st_gid,s.st_mode,s.st_nlink)
        else:
            image=observe_input(path,limit=2*1024**3);member=BackupMember(name,'file',image.bytes,image.sha256);identities[name]=image.identity
        members.append(member)
        if len(members)>250000:raise Refused('SDK selection exceeds its member bound')
    result=tuple(sorted(members,key=lambda m:m.name));selection_sha256(result)
    if len(json.dumps([asdict(m) for m in result]).encode())>4*1024*1024:raise Refused('Private SDK selection exceeds its protocol bound')
    return result,identities

def _memory(backup,home,yaml,config):
    paths=[home/'config.yaml'];profiles=home/'profiles'
    if profiles.exists():
        if profiles.resolve()!=profiles or not profiles.is_dir():raise Refused('Literal profile root required')
        for p in sorted(profiles.iterdir()):
            if p.is_symlink():raise Refused('A linked profile needs explicit backup review')
            if p.is_dir():paths.append(p/'config.yaml')
    reviewed={}
    for path in paths:
        try:data,image=read_input(path)
        except FileNotFoundError:reviewed[str(path.relative_to(home))]=None;continue
        require_builtin_memory_document(yaml.safe_load(data));reviewed[str(path.relative_to(home))]=image.sha256
    require_builtin_memory_document(config.load_config())
    if backup._collect_memory_provider_external_paths()!=[]:raise Refused('SDK declared external state outside the reviewed backup')
    return reviewed

def child_main(report_fd):
    """Fixed private subprocess protocol; source selection, no encryption or HTTP."""
    checkpoint='request'
    def emit(row):
        data=json.dumps(row,sort_keys=True,separators=(',',':'),allow_nan=False).encode()
        if len(data)>MAX_REPORT:raise RuntimeError('Snapshot report exceeds bound')
        view=memoryview(data)
        while view:
            n=os.write(report_fd,view)
            if n<=0:raise RuntimeError('Snapshot report write failed')
            view=view[n:]
        os.fsync(report_fd)
    try:
        if sys.platform!='linux' or os.geteuid()==0 or not sys.flags.isolated or not sys.flags.no_site or not sys.dont_write_bytecode:raise Refused('Isolated no-site native child required')
        payload=sys.stdin.buffer.read(MAX_PAYLOAD+1)
        if not payload or len(payload)>MAX_PAYLOAD:raise Refused('Bounded snapshot request required')
        row=unique_json(payload)
        if set(row)!={'sdk','dependencies','home','output','request_sha256'}:raise Refused('Closed snapshot request required')
        wanted=row.pop('request_sha256')
        if digest(row)!=wanted:raise Refused('Snapshot request digest differs')
        sdk,dependencies=inventory_from(row['sdk']),inventory_from(row['dependencies']);home=DirectoryAnchor(**row['home']);output=Path(row['output'])
        if not SDK_REQUIRED<=dict(sdk.files).keys() or anchor(home.path)!=home or home.mode!=0o700 or os.environ.get('HERMES_HOME')!=home.path or output.parent.resolve()!=output.parent or output.exists():raise Refused('Exact fresh home, output and SDK required')
        checkpoint='inputs';before_sdk=sdk.observe();before_dependencies=dependencies.observe()
        gate=ReviewedImports((sdk,dependencies));sys.meta_path.insert(0,gate);sys.path[:0]=[sdk.root.path,dependencies.root.path]
        checkpoint='sdk_binding'
        from hermes_cli import backup
        import hermes_yaml as yaml
        from hermes_cli import config
        from hermes_constants import get_default_hermes_root,get_hermes_home
        if get_default_hermes_root()!=Path(home.path) or get_hermes_home()!=Path(home.path) or Path(backup.__file__)!=Path(sdk.root.path)/'hermes_cli/backup.py':raise Refused('SDK selected another home/source')
        checkpoint='selection';members,identities=_selected(backup,Path(home.path),output);memory=_memory(backup,Path(home.path),yaml,config)
        checkpoint='backup'
        if backup.run_backup(SimpleNamespace(output=str(output),keep=0)) is not True:raise Unknown('SDK reports an incomplete backup')
        checkpoint='conservation'
        if _selected(backup,Path(home.path),output)!=(members,identities) or _memory(backup,Path(home.path),yaml,config)!=memory or sdk.observe()!=before_sdk or dependencies.observe()!=before_dependencies or anchor(home.path)!=home:raise Unknown('SDK backup inputs changed')
        image=observe_input(output,limit=2*1024**3)
        emit(dict(state='SDK_SNAPSHOT_SELECTION_VERIFIED',pid=os.getpid(),request_sha256=wanted,archive=asdict(image),selection=[asdict(m) for m in members],selection_sha256=selection_sha256(members),memory_configs=memory,
            loaded_source_sha256=digest(gate.recorded),loaded_sources=gate.recorded,retention=0,restore_performed=False,external_memory_state_accepted=False))
    except BaseException as error:
        emit(dict(state='SDK_SNAPSHOT_FAILED_OR_UNKNOWN',pid=os.getpid(),checkpoint=checkpoint,error_type=type(error).__name__,fresh_backup_verified=False))
        raise SystemExit(2) from None
