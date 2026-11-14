"""Source-bound, read-only SDK PM selection observations in a disposable child.

No bootstrap, PM client/manager call, sync, doctor, provisioning, repair, launcher
execution or readiness attestation. A committed path alone is not a ready runtime.
The complete backend still needs supported PM verification and native ownership.
"""
from __future__ import annotations

from dataclasses import dataclass,asdict
import hashlib
import importlib.abc
import importlib.util
import json
import os
from pathlib import Path,PurePosixPath
import re
import stat
import sys

MODULES={'pm':'pm/__init__.py','pm.environments':'pm/environments.py','pm.paths':'pm/paths.py',
         'hermes_constants':'hermes_constants.py','hermes_cli':'hermes_cli/__init__.py','hermes_cli._launchers':'hermes_cli/_launchers.py'}
INPUTS=tuple(sorted((*MODULES.values(),'install-stamp.json')))
MAX_INPUT=256*1024
_used=False


class SelectionRefused(RuntimeError):pass


def _path(value):
    return (type(value) is str and re.fullmatch(r'/[A-Za-z0-9_./-]{1,2047}',value) is not None
        and not value.startswith('//') and str(PurePosixPath(value))==value and '..' not in PurePosixPath(value).parts)


def _hash(value):return type(value) is str and re.fullmatch('[a-f0-9]{64}',value) is not None
def _digest(value):return hashlib.sha256(json.dumps(value,sort_keys=True,separators=(',',':'),allow_nan=False).encode()).hexdigest()


@dataclass(frozen=True)
class PmSelectionContext:
    source:str
    home:str
    store:str
    owner_home:str|None
    input_hashes:tuple[tuple[str,str],...]
    expected_facts_sha256:str|None

    def __post_init__(self):
        if (any(not _path(v) for v in (self.source,self.home,self.store)) or (self.owner_home is not None and not _path(self.owner_home))
                or type(self.input_hashes) is not tuple or any(type(v) is not tuple or len(v)!=2 for v in self.input_hashes)
                or tuple(n for n,h in self.input_hashes)!=INPUTS or any(not _hash(h) for n,h in self.input_hashes)
                or (self.expected_facts_sha256 is not None and not _hash(self.expected_facts_sha256))):
            raise ValueError('Exact source/home/store, seven fixed inputs and expected facts identity required')

    @property
    def install_key(self):return hashlib.sha256(self.source.encode()).hexdigest()[:16]
    @property
    def facts(self):return self.home+'/installs/'+self.install_key+'/facts.json'
    @property
    def sha256(self):return _digest(asdict(self))


def _read(path,*,optional=False):
    path=Path(path);fd=None
    try:
        if path.parent.resolve()!=path.parent:raise SelectionRefused('Literal observed input parent required')
        try:fd=os.open(path,os.O_RDONLY|os.O_NOFOLLOW|os.O_NONBLOCK|os.O_CLOEXEC)
        except FileNotFoundError:
            if optional:return None,None
            raise
        first=os.fstat(fd)
        def stamp(s):return (s.st_dev,s.st_ino,s.st_uid,s.st_gid,s.st_mode,s.st_nlink,s.st_size,s.st_mtime_ns,s.st_ctime_ns)
        if (not stat.S_ISREG(first.st_mode) or first.st_uid!=os.geteuid() or first.st_nlink!=1 or first.st_mode&0o022
                or not 1<=first.st_size<=MAX_INPUT):raise SelectionRefused('Bounded owner input required')
        data=os.pread(fd,MAX_INPUT+1,0)
        if len(data)!=first.st_size or stamp(os.fstat(fd))!=stamp(first) or stamp(path.lstat())!=stamp(first):
            raise SelectionRefused('Observed input changed during read')
        return data,stamp(first)
    except (OSError,ValueError):raise SelectionRefused('Native selection input unavailable') from None
    finally:
        if fd is not None:os.close(fd)


class _VerifiedModules(importlib.abc.MetaPathFinder,importlib.abc.Loader):
    def __init__(self,source,inputs):self.source=Path(source);self.inputs=inputs;self.loaded={}
    def find_spec(self,fullname,path=None,target=None):
        if fullname in MODULES:
            return importlib.util.spec_from_loader(fullname,self,origin=str(self.source/MODULES[fullname]),is_package=fullname in ('pm','hermes_cli'))
        if fullname=='hermes_bootstrap' or fullname.startswith(('pm.','hermes_cli.','hermes_constants.')):
            raise SelectionRefused('Unreviewed SDK import refused')
        return None
    def create_module(self,spec):return None
    def exec_module(self,module):
        name=MODULES[module.__name__];path=self.source/name;module.__file__=str(path)
        if module.__name__ in ('pm','hermes_cli'):module.__path__=[str(path.parent)]
        self.loaded[name]=hashlib.sha256(self.inputs[name]).hexdigest()
        # Execute the reviewed bytes already read, never a .pyc or a later path read.
        exec(compile(self.inputs[name],str(path),'exec',dont_inherit=True),module.__dict__)


def observe_selection(context,reviewed_context_sha256):
    global _used
    if (sys.platform!='linux' or os.geteuid()==0 or not sys.flags.isolated or not sys.flags.no_site or not sys.dont_write_bytecode
            or type(context) is not PmSelectionContext or context.sha256!=reviewed_context_sha256 or _used):
        raise SelectionRefused('One fresh isolated no-site native observer and reviewed context required')
    _used=True
    if (os.environ.get('HERMES_HOME')!=context.home or any(k.startswith('HERMES_') and k!='HERMES_HOME' for k in os.environ)
            or any(n in ('pm','hermes_cli','hermes_constants','hermes_bootstrap') or n.startswith(('pm.','hermes_cli.')) for n in sys.modules)):
        raise SelectionRefused('Fresh exact home and unloaded SDK namespace required')
    violations=[]
    def readonly(event,args):
        denied=False
        if event=='open':
            mode,flags=args[1:3]
            denied=(isinstance(mode,str) and any(c in mode for c in 'wax+')) or (isinstance(flags,int) and bool(flags&(os.O_WRONLY|os.O_RDWR|os.O_CREAT|os.O_APPEND|os.O_TRUNC)))
        elif event in ('os.chmod','os.chown','os.mkdir','os.remove','os.rmdir','os.rename','os.link','os.symlink','os.utime','os.truncate',
                       'os.kill','os.killpg','os.system','os.exec','os.posix_spawn','subprocess.Popen','socket.__new__','socket.connect','socket.bind','socket.sendto'):
            denied=True
        if denied:
            violations.append(event);raise SelectionRefused('Read-only selection observer refused an effect')
    sys.addaudithook(readonly)
    before={};inputs={}
    for name,h in context.input_hashes:
        data,identity=_read(Path(context.source)/name)
        if hashlib.sha256(data).hexdigest()!=h:raise SelectionRefused('Reviewed SDK input changed')
        inputs[name]=data;before[name]=identity
    facts,facts_identity=_read(context.facts,optional=True)
    actual_hash=hashlib.sha256(facts).hexdigest() if facts is not None else None
    if actual_hash!=context.expected_facts_sha256:raise SelectionRefused('Exact expected facts presence/content changed')
    loader=_VerifiedModules(context.source,inputs);sys.meta_path.insert(0,loader)
    try:
        from pm import environments as sdk
        source=Path(context.source)
        if sdk.install_key(source)!=context.install_key or str(sdk.runtime_facts_path(source))!=context.facts:
            raise SelectionRefused('SDK source/home selection differs from the reviewed context')
        owner=sdk.owning_home_root(source);store=sdk.store_root(source,honor_runtime_override=False)
        if (str(owner) if owner is not None else None)!=context.owner_home or str(store)!=context.store:
            raise SelectionRefused('SDK owner/store differs from the reviewed context')
        committed=sdk.committed_venv(source);selected=sdk.project_python(source)
        if committed is not None and (facts is None or not _path(str(committed))
                or not committed.is_relative_to(Path(context.home)/'installs'/context.install_key/'environments')
                or not (committed/'pyvenv.cfg').is_file()):raise SelectionRefused('Committed selection is outside the exact install')
        for name,h in context.input_hashes:
            data,identity=_read(source/name)
            if identity!=before[name] or hashlib.sha256(data).hexdigest()!=h:raise SelectionRefused('SDK input changed during observation')
        after,identity=_read(context.facts,optional=True)
        if identity!=facts_identity or after!=facts:raise SelectionRefused('Facts changed during observation')
        if violations:raise SelectionRefused('Read-only effect was attempted')
        return dict(state='COMMITTED_SELECTION_OBSERVED' if committed is not None else 'COMMITTED_SELECTION_MISSING',
            context_sha256=context.sha256,source=context.source,home=context.home,store=context.store,owner_home=context.owner_home,
            install_key=context.install_key,facts=context.facts,facts_sha256=actual_hash,
            committed_environment=str(committed) if committed is not None else None,selected_python=str(selected),selected_python_exists=selected.is_file(),
            loaded_sources=loader.loaded,input_identities={name:list(identity) for name,identity in before.items()},facts_identity=list(facts_identity) if facts_identity else None,
            pid=os.getpid(),read_only=True,violations=[],pm_actions=0,normal_launcher_verified=False,pm_readiness_verified=False)
    except SelectionRefused:raise
    except Exception:raise SelectionRefused('SDK selection observation refused') from None
    finally:sys.meta_path.remove(loader)
