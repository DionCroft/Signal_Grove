"""Installation template for a private fixed-action existing-broker seam.

Copy beside the owning worker as workspace_reads.py only through its reviewed
deployment procedure, with the pinned immutable LF-normalized runtime package.
Importing this template starts no listener and enrolls no caller.
No user-selected code, root or command is accepted.

The authenticated coordinator supplies its reviewed scope binding. This private
owner RPC is not a phone-facing authentication API. Native actor/profile wiring
must derive that scope on the coordinator, never trust a phone's claimed scope.
"""
from pathlib import Path
import hashlib,importlib,json,os,re,sys,time
HERE=Path(__file__).resolve().parent
SOURCE_DIGEST='ec08e8677e071a8b62723f3b5ac6702651f683dd8c1095b9065faf09faacc4ab'
NAMES={'__init__.py','workspace_provider.py','broker_protocol.py','local_pipe.py','broker_enrollment.py','operator_file.py','read_host.py'}


def _runtime():
 root=HERE/'workspace-read-runtime'/SOURCE_DIGEST;package=root/'workspace_reads_runtime'
 manifest_path=root/'manifest.json'
 if not manifest_path.is_file() or manifest_path.is_symlink() or manifest_path.stat().st_size>8192:raise ValueError('Read runtime manifest')
 manifest=json.loads(manifest_path.read_text(encoding='utf8'))
 if not isinstance(manifest,dict) or set(manifest)!={'schema','files'} or type(manifest['schema']) is not int or manifest['schema']!=1 or set(manifest['files'])!=NAMES:raise ValueError('Read runtime schema')
 files=manifest['files']
 if hashlib.sha256(json.dumps(files,sort_keys=True,separators=(',',':')).encode()).hexdigest()!=SOURCE_DIGEST:raise ValueError('Read runtime revision')
 for name,digest in files.items():
  path=package/name
  if (not path.is_file() or path.is_symlink() or path.resolve().parent!=package.resolve()
      or not isinstance(digest,str) or not re.fullmatch('[a-f0-9]{64}',digest) or hashlib.sha256(path.read_bytes()).hexdigest()!=digest):raise ValueError('Read runtime bytes')
 key='signal_grove_workspace_'+SOURCE_DIGEST
 if key not in sys.modules:
  from importlib import util
  spec=util.spec_from_file_location(key,package/'__init__.py',submodule_search_locations=[str(package)])
  if spec is None or spec.loader is None:raise ValueError('Read runtime loader')
  module=util.module_from_spec(spec);sys.modules[key]=module
  try:spec.loader.exec_module(module)
  except BaseException:sys.modules.pop(key,None);raise
 else:
  if Path(sys.modules[key].__file__).resolve()!=package.resolve()/'__init__.py':raise ValueError('Read runtime identity')
 return tuple(importlib.import_module(key+'.'+name) for name in ['broker_enrollment','operator_file','read_host','local_pipe'])


def _policy(worker,modules):
 e,f,h,p=modules;raw=f.read_operator_file(str(HERE/'workspace-read-policy.json'),maximum=32768);policy=e.parse_policy(raw)
 actual=p._Windows().process_identity(os.getpid())
 if policy.device!=worker.DEVICE or policy.owner_sid!=actual[0]:raise e.WorkspaceError('DENIED')
 return policy


class Host:
 """Optional component; never changes existing execution queue/receipts."""
 def __init__(self,worker):
  self.worker=worker;self.manager=None;self.modules=None;self.failure=None;self.enabled=False
  if not (HERE/'workspace-read-policy.json').exists():return
  self.enabled=True;sessions=[]
  try:
   self.modules=_runtime();e,f,h,p=self.modules
   if worker.HOST_CONTEXT is not True or e.observe_owner().active is not True:raise e.WorkspaceError('DENIED')
   policy=_policy(worker,self.modules)
   def read_policy():return f.read_operator_file(str(HERE/'workspace-read-policy.json'),maximum=32768)
   def read_registry():
    raw=f.read_operator_file(str(worker.REGISTRY));sys.path.insert(0,str(worker.RT));import hermes_yaml as yaml
    data=yaml.safe_load(raw.decode('utf-8-sig'))
    if not isinstance(data,dict) or not isinstance(data.get('projects'),list):raise e.WorkspaceError('INVALID')
    return e.RegistrySnapshot(hashlib.sha256(raw).hexdigest(),tuple(data['projects']))
   for scope,_ in policy.grants:sessions.append(e.EnrolledReadSession(scope_id=scope,read_policy=read_policy,read_registry=read_registry,validate_project=lambda ident,rows:worker.project(ident,rows)))
   self.manager=h.ReadHost(sessions).start()
  except BaseException:
   for session in sessions:session.close()
   self.failure='UNAVAILABLE'
 def tick(self):
  if self.manager is None:return
  e,f,h,p=self.modules
  try:
   raw=h.encode_projection(self.manager.statuses(),device=self.worker.DEVICE,observed=time.time());self.worker.atomic(HERE/'state/workspace-reads.safe.json',json.loads(raw))
  except Exception:
   self.failure='UNAVAILABLE'
   try:self.manager.close()
   except Exception:pass
 def close(self):
  if self.manager is None:return True
  try:
   self.manager.close();settled=self.manager.wait_closed();self.tick();return settled
  except Exception:self.failure='UNAVAILABLE';return False


def rpc(worker,data):
 """The existing authenticated control route supplies the scope, never a phone.

 Re-observe protected local policy and projection before one native admission.
 The interactive broker retains authoritative registry/root/owner validation.
 """
 try:modules=_runtime()
 except Exception:return dict(transport='error',code='UNAVAILABLE',request_submitted=False)
 e,f,h,p=modules;action=data.get('action') if isinstance(data,dict) else None
 expected={'action','scope_id'} if action=='workspace_status' else {'action','scope_id','frame'}
 if action not in {'workspace_status','workspace_read'} or set(data)!=expected:raise ValueError('Fixed workspace RPC schema')
 scope=data['scope_id']
 try:
  if not isinstance(scope,str) or not re.fullmatch('[a-f0-9]{64}',scope):raise e.WorkspaceError('INVALID')
  policy=_policy(worker,modules)
  if not policy.projects(scope):raise e.WorkspaceError('DENIED')
  if action=='workspace_status':
   raw=f.read_operator_file(str(HERE/'state/workspace-reads.safe.json'),maximum=32768)
   status=h.select_projection(raw,scope_id=scope,device=worker.DEVICE,now=time.time())
   return dict(transport='status',scope_id=scope,device=worker.DEVICE,state=status.state,worker_instance=status.endpoint.worker_instance if status.endpoint is not None else None,failure_code=status.failure_code)
  if not isinstance(data['frame'],dict):raise e.WorkspaceError('INVALID')
  try:request=json.dumps(data['frame'],allow_nan=False,separators=(',',':')).encode('utf8')
  except (ValueError,TypeError,UnicodeError,RecursionError):raise e.WorkspaceError('INVALID') from None
  frame=p.decode_frame(request);project=frame['payload'].get('project_id')
  if frame['payload'].get('scope_id')!=scope:raise e.WorkspaceError('DENIED')
  if not isinstance(project,str):raise e.WorkspaceError('INVALID')
  if project not in policy.projects(scope):raise e.WorkspaceError('DENIED')
  def read_projection():
   # revision hashes the exact protected policy bytes, including grant changes.
   # An SSH client reads no registry/project/NAS path to make this observation.
   fresh=_policy(worker,modules)
   if fresh!=policy:raise e.WorkspaceError('CHANGED')
   return f.read_operator_file(str(HERE/'state/workspace-reads.safe.json'),maximum=32768)
  reply=h.call_current_projected(read_projection,request,scope_id=scope,device=worker.DEVICE,timeout=10)
  return dict(transport='reply',reply=json.loads(reply))
 except p.ReadTransportError as error:return dict(transport='error',code=error.code,request_submitted=error.request_submitted)
 except e.WorkspaceError as error:return dict(transport='error',code=error.code,request_submitted=False)
