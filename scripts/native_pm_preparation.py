"""One claimed supported PM install inside its exact finite maintenance unit.

No launcher execution, lock refresh, repair, fact copying, model/grant change,
process signalling, retry or automatic rollback. The caller owns full admission,
encrypted backup, operational ancestry and source-inventory gates. PM's own
workers inherit the verified dedicated unit's finite lifetime; unknown effects
must be recovered independently, never replayed by a replacement actor.
"""
from __future__ import annotations

from dataclasses import dataclass,asdict
import hashlib
import importlib
import json
import math
import os
from pathlib import Path,PurePosixPath
import re
import stat
import subprocess
import sys
import threading
import time

from cutover_coordinator import Plan,ApprovedExecution,FORWARD
from cutover_journal import CutoverJournal,Refused,Unknown
from native_file_publication import _Receipt,digest,DirectoryAnchor,anchor
from native_service_manager import NativeServiceManager
from native_pm_selection import PmSelectionContext,_hash
from native_retirement_owner import read_process_owner

DEPENDENCY_INPUTS=('pyproject.toml','uv.lock','setup.py','pm/lock.json','install-stamp.json')
REQUIRED_SOURCE=frozenset((*DEPENDENCY_INPUTS,'pm/__init__.py','pm/client.py','pm/runtime.py','pm/cli.py','pm/launch.py','pm/environments.py','pm/paths.py',
    'hermes_constants.py','hermes_cli/__init__.py','hermes_cli/_launchers.py'))
EXTRAS=('agentic-contextgraph','all','discord')
PROTECTED=('.env','config.yaml')
MAX_SOURCE_FILE=32*1024*1024
MAX_API_BINARY=128*1024*1024


def _relative(name):
    return (type(name) is str and 1<=len(name)<=4096 and not name.startswith('/') and '\\' not in name
        and not any(c in name for c in '\0\r\n') and str(PurePosixPath(name))==name and '..' not in PurePosixPath(name).parts)


@dataclass(frozen=True)
class PmPreparationSpec:
    selection:PmSelectionContext
    source_files:tuple[tuple[str,str],...]
    protected_hashes:tuple[tuple[str,str|None],...]
    scope_configuration_sha256:str
    home_anchor:DirectoryAnchor
    api_python:str
    api_python_sha256:str
    owned_root:str|None=None
    extras:tuple[str,...]=EXTRAS

    def __post_init__(self):
        if (type(self.selection) is not PmSelectionContext or type(self.source_files) is not tuple or not 1<=len(self.source_files)<=32000
                or any(type(v) is not tuple or len(v)!=2 for v in self.source_files)
                or tuple(sorted(self.source_files))!=self.source_files or len(dict(self.source_files))!=len(self.source_files)
                or any(not _relative(n) or not _hash(h) for n,h in self.source_files) or not REQUIRED_SOURCE<=dict(self.source_files).keys()
                or any(dict(self.source_files).get(n)!=h for n,h in self.selection.input_hashes)
                or type(self.protected_hashes) is not tuple or tuple(n for n,h in self.protected_hashes)!=PROTECTED
                or any(h is not None and not _hash(h) for n,h in self.protected_hashes)
                or type(self.home_anchor) is not DirectoryAnchor or self.home_anchor.path!=self.selection.home or self.home_anchor.mode!=0o700
                or type(self.api_python) is not str or not self.api_python.startswith('/') or any(c in self.api_python for c in '\0\r\n') or len(self.api_python)>4096
                or str(PurePosixPath(self.api_python))!=self.api_python or '..' in PurePosixPath(self.api_python).parts
                or not PurePosixPath(self.api_python).is_relative_to(PurePosixPath(self.selection.store)) or not _hash(self.api_python_sha256)
                or (self.owned_root is not None and (type(self.owned_root) is not str or not self.owned_root.startswith('/')
                    or not re.fullmatch('signal-grove-pm-[a-f0-9]{32}',PurePosixPath(self.owned_root).name)
                    or str(PurePosixPath(self.owned_root))!=self.owned_root or '..' in PurePosixPath(self.owned_root).parts
                    or not PurePosixPath(self.selection.home).is_relative_to(PurePosixPath(self.owned_root))))
                or not _hash(self.scope_configuration_sha256) or self.extras!=EXTRAS or type(self.extras) is not tuple):
            raise ValueError('Closed source/dependency/configuration and exact supported extras required')

    @property
    def sha256(self):return digest(asdict(self))


def _blob(path,*,optional=False,limit=MAX_SOURCE_FILE):
    if type(limit) is not int or not 0<limit<=MAX_API_BINARY:raise Refused('Exact bounded PM input read required')
    path=Path(path);opened=None
    def stamp(s):return (s.st_dev,s.st_ino,s.st_uid,s.st_gid,s.st_mode,s.st_nlink,s.st_size,s.st_mtime_ns,s.st_ctime_ns)
    try:
        if path.parent.resolve()!=path.parent:raise Refused('Literal PM input path required')
        try:opened=os.open(path,os.O_RDONLY|os.O_NOFOLLOW|os.O_NONBLOCK|os.O_CLOEXEC)
        except FileNotFoundError:
            if optional:return None,None
            raise
        before=os.fstat(opened)
        if not stat.S_ISREG(before.st_mode) or before.st_uid!=os.geteuid() or before.st_nlink!=1 or not 0<=before.st_size<=limit:
            raise Refused('Bounded owner PM input required')
        chunks=[];count=0
        while True:
            data=os.read(opened,min(65536,limit+1-count))
            if not data:break
            chunks.append(data);count+=len(data)
            if count>limit:raise Refused('PM input exceeds its bound')
        if count!=before.st_size or stamp(os.fstat(opened))!=stamp(before) or stamp(path.lstat())!=stamp(before):
            raise Unknown('PM input changed during native read')
        return b''.join(chunks),stamp(before)
    except (OSError,ValueError):raise Refused('PM input observation refused') from None
    finally:
        if opened is not None:os.close(opened)


class NativePmPreparation:
    def __init__(self,plan,journal,manager,stage,spec,reviewed_spec_sha256):
        if (sys.platform!='linux' or os.geteuid()==0 or type(plan) is not Plan or type(journal) is not CutoverJournal
                or type(manager) is not NativeServiceManager or manager.plan!=plan or manager.journal is not journal
                or stage not in ('pm_old','pm_new') or type(spec) is not PmPreparationSpec or spec.sha256!=reviewed_spec_sha256):
            raise Refused('Exact reviewed native PM stage, manager, journal and source context required')
        self.plan,self.journal,self.manager,self.stage,self.spec=plan,journal,manager,stage,spec
        if len(json.dumps(asdict(spec)).encode())>8*1024*1024:raise Refused('Bounded PM specification required')
        self.used=False;self.state='ready';self.last_receipt=None;self.child_process=None;self.last_child_owner=None

    def _authorize(self,execution):
        if (type(execution) is not ApprovedExecution or execution.scope!='forward' or execution.packet_sha256!=self.plan.packet_sha256
                or execution.authority_sha256!=self.plan.authority_sha256):raise Refused('Exact PM preparation approval required')
        now=time.monotonic()
        if not math.isfinite(now) or now>=execution.deadline:raise Unknown('PM preparation deadline elapsed')
        self.journal.assert_current();view=self.journal.view;rows=[r for r in view['records'] if r['operation']==execution.operation];index=FORWARD.index(self.stage)
        if (view['state']!='pending' or view['pending']!=self.stage or view['operation']!=execution.operation or view['mode']!='forward'
                or view['authorization_sha256']!=execution.binding_sha256 or view['plan_sha256']!=execution.packet_sha256
                or view['root_plan_sha256']!=self.plan.packet_sha256
                or [r['step'] for r in rows if r['kind']=='intent']!=list(FORWARD[:index+1])
                or [r['step'] for r in rows if r['kind']=='verified']!=list(FORWARD[:index])):
            raise Refused('Exact fixed PM journal prefix is not authorized')
        if self.spec.owned_root is not None:
            root=anchor(self.spec.owned_root)
            if (root.mode!=0o700 or root.uid!=os.geteuid() or Path(root.path).name!='signal-grove-pm-'+execution.operation):
                raise Refused('Exact private owned PM fixture root required')
        scope=self.manager.execution_scope(execution.operation,owned_fixture=self.spec.owned_root is not None);scope.require_window(execution,read_process_owner(os.getpid()),now)
        if scope.configuration_sha256!=self.spec.scope_configuration_sha256:raise Refused('Native PM controller command differs from the packet')
        return scope

    def _source(self):
        root=Path(self.spec.selection.source);observed={};total=0
        for name,h in self.spec.source_files:
            data,identity=_blob(root/name);total+=len(data)
            if total>256*1024*1024 or hashlib.sha256(data).hexdigest()!=h:raise Refused('Reviewed PM source inventory changed or exceeds its bound')
            observed[name]=identity
        return observed

    def _protected(self):
        result={}
        for name,h in self.spec.protected_hashes:
            data,identity=_blob(Path(self.spec.selection.home)/name,optional=True)
            if (hashlib.sha256(data).hexdigest() if data is not None else None)!=h:raise Refused('Protected configuration changed')
            result[name]=identity
        return result

    def _loaded(self):
        root=Path(self.spec.selection.source);expected=dict(self.spec.source_files);loaded={}
        for name,module in tuple(sys.modules.items()):
            if name in ('pm','hermes_cli','hermes_constants') or name.startswith(('pm.','hermes_cli.')):
                filename=getattr(module,'__file__',None)
                if not filename:raise Refused('Unbound SDK module is loaded')
                path=Path(filename)
                if path.resolve()!=path or not path.is_relative_to(root):raise Refused('Foreign SDK module is loaded')
                relative=path.relative_to(root).as_posix();data,_=_blob(path);h=hashlib.sha256(data).hexdigest()
                if expected.get(relative)!=h:raise Refused('Loaded SDK source differs from its reviewed inventory')
                loaded[relative]=h
        return loaded

    def run(self,execution):
        if self.used:raise Refused('This PM preparation actor is spent')
        receipt=None;claimed=False;child=None;report_fd=None;captures=[];checkpoint='preflight'
        try:
            scope=self._authorize(execution);selection=self.spec.selection
            if anchor(selection.home)!=self.spec.home_anchor:raise Refused('Exact private PM home changed')
            before_source=self._source();before_protected=self._protected();old_facts,_=_blob(selection.facts,optional=True)
            if (hashlib.sha256(old_facts).hexdigest() if old_facts is not None else None)!=selection.expected_facts_sha256:
                raise Refused('Initial PM facts presence/content changed')
            binary,binary_identity=_blob(self.spec.api_python,limit=MAX_API_BINARY)
            if (hashlib.sha256(binary).hexdigest()!=self.spec.api_python_sha256 or Path(self.spec.api_python).resolve()!=Path(self.spec.api_python)
                    or stat.S_IMODE(binary_identity[4])!=0o755):raise Refused('Exact permanent PM API Python required')
            del binary
            self._authorize(execution);self.journal.claim_dispatch(execution.operation,self.stage,execution.binding_sha256)
            self.used=True;claimed=True;self.state='running'
            receipt=_Receipt(self.journal,execution,self.stage,self.spec.sha256);self.last_receipt=receipt.name
            report_name='native-pm-report-'+execution.operation+'-'+self.stage+'.json'
            report_fd=os.open(report_name,os.O_RDWR|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW|os.O_CLOEXEC,0o600,dir_fd=self.journal._root)
            report_identity=self.journal._check(report_fd);os.fsync(self.journal._root)
            receipt.append('pm_intent',dict(spec_sha256=self.spec.sha256,scope_sha256=scope.sha256,source_files=len(before_source),
                source_inventory_sha256=digest(self.spec.source_files),facts_before_sha256=selection.expected_facts_sha256,extras=list(self.spec.extras),
                report=report_name,operation='install',repair=False,evict_incompatible_plugins=False))
            scripts=Path(__file__).resolve().parent
            code='import sys;sys.path.insert(0,'+repr(str(scripts))+');import native_pm_preparation as p;p.sdk_child_main('+str(report_fd)+')'
            env={k:v for k,v in os.environ.items() if k in ('PATH','HOME','USER','LOGNAME','LANG','LC_ALL','LC_CTYPE')}
            env.update(HERMES_HOME=selection.home,PYTHONDONTWRITEBYTECODE='1',TMPDIR=selection.home+'/cache/pm-operation-scratch',
                TMP=selection.home+'/cache/pm-operation-scratch',TEMP=selection.home+'/cache/pm-operation-scratch',
                UV_CACHE_DIR=selection.home+'/cache/uv',PIP_CACHE_DIR=selection.home+'/cache/pip')
            self._authorize(execution)
            checkpoint='spawn'
            child=subprocess.Popen([self.spec.api_python,'-I','-B','-c',code],cwd=selection.home,env=env,stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,stderr=subprocess.PIPE,pass_fds=(report_fd,))
            self.child_process=child
            owner=read_process_owner(child.pid)
            self.last_child_owner=owner
            if (owner is None or os.readlink('/proc/'+str(child.pid)+'/exe')!=self.spec.api_python
                    or (os.stat('/proc/'+str(child.pid)+'/exe').st_dev,os.stat('/proc/'+str(child.pid)+'/exe').st_ino)!=binary_identity[:2]
                    or Path('/proc',str(child.pid)).stat().st_uid!=os.geteuid()
                    or Path('/proc',str(child.pid),'cgroup').read_text().splitlines()!=['0::'+scope.cgroup]):
                raise Unknown('PM API child native ownership is unproven')
            receipt.append('pm_child_owned',dict(owner=asdict(owner),report=report_name,scope_sha256=scope.sha256))
            for suffix,stream in (('stdout',child.stdout),('stderr',child.stderr)):
                capture=_OutputCapture(self.journal,execution,self.stage,suffix,stream);captures.append(capture);capture.start()
            payload=json.dumps(dict(spec=asdict(self.spec),spec_sha256=self.spec.sha256)).encode()
            child.stdin.write(payload);child.stdin.close()
            while child.poll() is None:
                self.journal.assert_current()
                if time.monotonic()>=execution.deadline:raise Unknown('PM deadline elapsed; dedicated native unit retains ownership')
                try:child.wait(timeout=0.2)
                except subprocess.TimeoutExpired:pass
            checkpoint='child_completion'
            for capture in captures:capture.finish()
            self._authorize(execution)
            if child.returncode!=0 or read_process_owner(child.pid)==owner:raise Unknown('PM API child did not finish successfully')
            checkpoint='report'
            named=os.stat(report_name,dir_fd=self.journal._root,follow_symlinks=False)
            if (named.st_dev,named.st_ino)!=report_identity or self.journal._check(report_fd)!=report_identity:raise Unknown('PM report native identity changed')
            data=os.pread(report_fd,1024*1024+1,0)
            if not data or len(data)>1024*1024:raise Unknown('Bounded PM result unavailable')
            report=json.loads(data)
            if report.get('state')!='SDK_PM_INSTALL_AND_CURRENCY_PASS' or report.get('spec_sha256')!=self.spec.sha256 or report.get('pid')!=child.pid:
                raise Unknown('PM result identity is unproven')
            checkpoint='facts'
            facts,identity=_blob(selection.facts)
            if hashlib.sha256(facts).hexdigest()!=report['facts_sha256'] or list(identity)!=report['facts_identity']:
                raise Unknown('PM facts differ from the observed child result')
            checkpoint='activation'
            committed=Path(report['committed_environment']);python=Path(report['selected_python']);stamps=Path(selection.home)/'installs'/selection.install_key/'inputs'
            recorded=json.loads(facts)['packages']['venv']['environment']
            if (committed.resolve()!=committed or committed!=Path(recorded).resolve() or not committed.is_relative_to(Path(selection.home)/'installs'/selection.install_key/'environments')
                    or not (committed/'pyvenv.cfg').is_file() or not python.is_file() or not python.is_relative_to(committed)
                    or (stamps/'.project-root').read_text()!=selection.source or (stamps/'.test-environment').exists()
                    or any((stamps/n).stat().st_mtime_ns!=(Path(selection.source)/n).stat().st_mtime_ns for n in DEPENDENCY_INPUTS[:-1] if (Path(selection.source)/n).is_file())):
                raise Unknown('Native committed PM generation/activation inputs are unproven')
            checkpoint='conservation'
            for name,h in report['loaded_sources'].items():
                if dict(self.spec.source_files).get(name)!=h:raise Unknown('PM child loaded unreviewed source')
            if (report.get('supported_install_verified') is not True or report.get('supported_currency_verified') is not True
                    or self._source()!=before_source or self._protected()!=before_protected):
                raise Unknown('PM source, protected state or supported currency is unproven')
            current_binary,current_identity=_blob(self.spec.api_python,limit=MAX_API_BINARY)
            if current_identity!=binary_identity or hashlib.sha256(current_binary).hexdigest()!=self.spec.api_python_sha256:
                raise Unknown('PM API Python changed during preparation')
            checkpoint='final_scope';self._authorize(execution)
            evidence=dict(facts_sha256=report['facts_sha256'],facts_identity=report['facts_identity'],committed_environment=str(committed),selected_python=str(python),
                source_inventory_sha256=digest(self.spec.source_files),loaded_sources=report['loaded_sources'],scope_sha256=self.manager.execution_scope(execution.operation,owned_fixture=self.spec.owned_root is not None).sha256,
                report=report_name,report_sha256=hashlib.sha256(data).hexdigest(),outputs=[c.evidence() for c in captures],
                supported_install_verified=True,supported_currency_verified=True,activation_inputs_verified=True,source_and_protected_inputs_conserved=True)
            receipt.append('pm_effect_observed',evidence);self._authorize(execution);self.state='effects_observed'
            return dict(state=self.state,stage=self.stage,spec_sha256=self.spec.sha256,receipt=receipt.name,receipt_sha256=hashlib.sha256(receipt.data).hexdigest(),**evidence)
        except BaseException as error:
            if claimed:
                self.state='unknown'
                # Fixed phase/type only: exception text may contain private PM
                # configuration. A damaged ledger must not hide the first error.
                if receipt is not None:
                    try:receipt.append('pm_observation_held',dict(checkpoint=checkpoint,error_type=type(error).__name__,child_exit=child.returncode if child is not None else None))
                    except Exception:pass
                if not isinstance(error,Exception):raise
                raise Unknown('PM outcome uncertain; retain its facts, generations, unit and receipt without replay') from None
            if not isinstance(error,Exception) or isinstance(error,(Refused,Unknown)):raise
            raise Refused('PM preflight refused without dispatch') from None
        finally:
            if child is not None and child.stdin is not None and not child.stdin.closed:child.stdin.close()
            for capture in captures:capture.close_if_terminal()
            if report_fd is not None:os.close(report_fd)
            if receipt is not None:receipt.close()


class _OutputCapture:
    """Bounded retained prefix; overflow is explicit unknown, never a passing log."""
    def __init__(self,journal,execution,stage,suffix,stream):
        self.journal,self.stream=journal,stream;self.data=b'';self.overflow=False;self.error=False
        self.name='native-pm-output-'+execution.operation+'-'+stage+'.'+suffix
        self.fd=os.open(self.name,os.O_RDWR|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW|os.O_CLOEXEC,0o600,dir_fd=journal._root)
        self.identity=journal._check(self.fd);os.fsync(journal._root)
        self.thread=threading.Thread(target=self._read,daemon=True)
    def start(self):self.thread.start()
    def _read(self):
        try:
            while True:
                data=self.stream.read1(65536)
                if not data:break
                keep=data[:max(0,4*1024*1024-len(self.data))]
                if len(keep)!=len(data):self.overflow=True
                view=memoryview(keep)
                while view:
                    n=os.write(self.fd,view)
                    if n<=0:raise OSError('PM output write unavailable')
                    view=view[n:]
                self.data+=keep
            os.fsync(self.fd)
        except Exception:self.error=True
    def finish(self):
        self.thread.join(timeout=5)
        if self.thread.is_alive() or self.error or self.overflow:raise Unknown('PM output incomplete or exceeds its retained bound')
        self.journal.assert_current();named=os.stat(self.name,dir_fd=self.journal._root,follow_symlinks=False)
        if ((named.st_dev,named.st_ino)!=self.identity or self.journal._check(self.fd)!=self.identity
                or os.pread(self.fd,4*1024*1024+1,0)!=self.data):raise Unknown('PM output native identity/content changed')
    def evidence(self):return dict(name=self.name,bytes=len(self.data),sha256=hashlib.sha256(self.data).hexdigest(),overflow=self.overflow)
    def close_if_terminal(self):
        if self.thread.ident is not None:self.thread.join(timeout=5)
        if not self.thread.is_alive():
            self.stream.close()
            if self.fd is not None:os.close(self.fd);self.fd=None


def sdk_child_main(report_fd):
    """Fixed private child protocol. Never used as an app or general command API."""
    def emit(value):
        data=json.dumps(value,sort_keys=True,separators=(',',':')).encode()
        if len(data)>1024*1024:raise RuntimeError('PM child evidence bound exceeded')
        view=memoryview(data)
        while view:
            n=os.write(report_fd,view)
            if n<=0:raise RuntimeError('PM child evidence write unavailable')
            view=view[n:]
        os.fsync(report_fd)
    checkpoint='request'
    try:
        payload=json.loads(sys.stdin.buffer.read(8*1024*1024+1));row=payload['spec'];s=row['selection'];s['input_hashes']=tuple(tuple(p) for p in s['input_hashes'])
        row['selection']=PmSelectionContext(**s);row['source_files']=tuple(tuple(p) for p in row['source_files']);row['protected_hashes']=tuple(tuple(p) for p in row['protected_hashes'])
        row['home_anchor']=DirectoryAnchor(**row['home_anchor']);row['extras']=tuple(row['extras']);spec=PmPreparationSpec(**row)
        if spec.sha256!=payload['spec_sha256'] or os.environ.get('HERMES_HOME')!=spec.selection.home:
            raise Refused('Exact PM child specification/home required')
        # Reuse the same source/protected observation methods, without a journal
        # or manager object. This child never owns or advances their authority.
        checkpoint='inputs';inputs=_ChildInputs(spec);before=inputs._source();protected=inputs._protected();inputs._loaded()
        if anchor(spec.selection.home)!=spec.home_anchor:raise Refused('Private PM child home changed')
        for relative in ('cache','cache/pm-operation-scratch','cache/uv','cache/pip'):
            path=Path(spec.selection.home)/relative
            try:path.mkdir(mode=0o700)
            except FileExistsError:pass
            if path.resolve()!=path or anchor(path).uid!=os.geteuid() or anchor(path).mode!=0o700:raise Refused('Private PM cache ancestry required')
        checkpoint='sdk_binding'
        sys.path.insert(0,spec.selection.source);pm=importlib.import_module('pm');sdk=importlib.import_module('pm.environments');runtime=importlib.import_module('pm.runtime')
        install,currency=runtime.run_cli,pm.venv_is_current;inputs._loaded();source=Path(spec.selection.source)
        if (install.__module__!='pm.runtime' or currency.__module__!='pm.client' or str(sdk.runtime_facts_path(source))!=spec.selection.facts
                or str(sdk.store_root(source,honor_runtime_override=False))!=spec.selection.store):raise Refused('Exact supported PM child facade/store required')
        owner=sdk.owning_home_root(source)
        if (str(owner) if owner is not None else None)!=spec.selection.owner_home:raise Refused('Exact PM source owner required')
        # sync_venv commits dependencies but does not write activation inputs.
        # The supported full install owns those stamps after its checks; never
        # manufacture them or retry a successful sync to satisfy this verifier.
        checkpoint='install'
        if install(['install',*[arg for extra in spec.extras for arg in ('--extra',extra)]])!=0:raise Unknown('Supported PM install did not finish successfully')
        checkpoint='currency'
        if currency(project_root=source) is not True:raise Unknown('Supported PM currency verification failed')
        checkpoint='generation'
        facts,identity=_blob(spec.selection.facts);committed=sdk.committed_venv(source);python=sdk.project_python(source)
        if committed is None or not python.is_file() or inputs._source()!=before or inputs._protected()!=protected:
            raise Unknown('PM child effects/source/protected state unproven')
        emit(dict(state='SDK_PM_INSTALL_AND_CURRENCY_PASS',pid=os.getpid(),spec_sha256=spec.sha256,facts_sha256=hashlib.sha256(facts).hexdigest(),
            facts_identity=list(identity),committed_environment=str(committed),selected_python=str(python),loaded_sources=inputs._loaded(),supported_install_verified=True,supported_currency_verified=True))
    except BaseException as error:
        emit(dict(state='SDK_PM_FAILED_OR_UNKNOWN',pid=os.getpid(),checkpoint=checkpoint,error_type=type(error).__name__))
        raise SystemExit(2) from None


class _ChildInputs:
    def __init__(self,spec):self.spec=spec
    _source=NativePmPreparation._source
    _protected=NativePmPreparation._protected
    _loaded=NativePmPreparation._loaded
