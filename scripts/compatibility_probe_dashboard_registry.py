"""Bounded real-module registry semantics; not dashboard HTTP/authentication acceptance."""
from pathlib import Path
import argparse, concurrent.futures, dataclasses, hashlib, importlib.util, json, logging, os, sys, threading, traceback, types
if not __debug__:
    raise SystemExit('Run without -O: this fixture requires enabled assertions.')
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source-root',type=Path,required=True,help='Directory containing the four exact E36 upstream files in their original paths.')
parser.add_argument('--work-root',type=Path,required=True,help='New isolated output directory; must not exist. Windows requires E:.')
args=parser.parse_args();source=args.source_root.resolve();root=args.work_root.resolve()
assert source.is_dir() and not root.exists(), 'Use existing verified source and a new fixture output directory'
assert not root.is_relative_to(source) and not source.is_relative_to(root), 'Keep the fixture separate from upstream source'
if os.name=='nt':assert source.drive.upper()=='E:' and root.drive.upper()=='E:', 'All temporary source/output material must use E:'
paths={'hermes_constants':(source/'hermes_constants.py','32dbd53c9737545edea778df17eaec42c2581b56'),'hermes_cli.dashboard_auth.base':(source/'hermes_cli/dashboard_auth/base.py','b8e71461adf30b4814634cb3fbcfdf9b49488e33'),'agent.provider_registry':(source/'agent/provider_registry.py','54d892ff2a6414915b2fff5a469b114c0c906770'),'hermes_cli.dashboard_auth.registry':(source/'hermes_cli/dashboard_auth/registry.py','082f3731e315bc20f5331aec175af8bac34d17c5')}
verified={}
for name,(path,expected) in paths.items():
 assert not path.is_symlink() and path.resolve().is_relative_to(source), 'Source files must remain inside the selected source root'
 raw=path.read_bytes();assert hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()==expected
 verified[name]=raw
root.mkdir(parents=True,exist_ok=False)
system={k:os.environ[k] for k in ['SystemRoot','WINDIR'] if k in os.environ};os.environ.clear();os.environ.update(system)
for key,sub in {'TEMP':'temp','TMP':'temp','HOME':'home','USERPROFILE':'home','APPDATA':'appdata','LOCALAPPDATA':'localappdata','HERMES_HOME':'home/default','HERMES_RUNTIME_DIR':'runtime'}.items():
 value=root/sub;value.mkdir(parents=True,exist_ok=True);os.environ[key]=str(value)
os.environ['PYTHONDONTWRITEBYTECODE']='1';sys.dont_write_bytecode=True;denied=[];results=[]
read_roots=[root,source,Path(sys.base_prefix).resolve(),Path(__file__).resolve()]
def within(path,roots):return any(path.is_relative_to(x) for x in roots)
def audit(event,args):
 if event in ('socket.connect','socket.getaddrinfo','subprocess.Popen','os.system','os.kill','os.killpg','os.exec','os.posix_spawn'):
  denied.append(event);raise PermissionError('No network/process operations permitted')
 if event=='open' and isinstance(args[0],(str,bytes,os.PathLike)):
  path=Path(os.fsdecode(args[0])).resolve();mode=args[1] or '';flags=args[2] or 0;writing=any(c in mode for c in 'wax+') or flags & (os.O_WRONLY|os.O_RDWR|os.O_CREAT|os.O_TRUNC|os.O_APPEND)
  if not within(path,[root] if writing else read_roots):denied.append('write' if writing else 'read');raise PermissionError('Registry fixture file boundary')
 if event in ('os.mkdir','os.remove','os.rmdir','os.rename','os.symlink'):
  values=args[:2] if event in ('os.rename','os.symlink') else args[:1]
  if any(not Path(os.fsdecode(v)).resolve().is_relative_to(root) for v in values):denied.append(event);raise PermissionError('Registry fixture mutation boundary')
sys.addaudithook(audit)
for name in ['agent','hermes_cli','hermes_cli.dashboard_auth']:
 package=types.ModuleType(name);package.__path__=[];sys.modules[name]=package
loaded={}
for name,(path,_) in paths.items():
 spec=importlib.util.spec_from_file_location(name,path);module=importlib.util.module_from_spec(spec);sys.modules[name]=module;exec(compile(verified[name],str(path),"exec",dont_inherit=True),module.__dict__);loaded[name]=module
reg=loaded['hermes_cli.dashboard_auth.registry'];protocol=loaded['hermes_cli.dashboard_auth.base'].DashboardAuthProvider
class Provider(protocol):
 name='synthetic';display_name='Synthetic registry fixture'
 def __init__(self,name):self.name=name
 def start_login(self,**kwargs):raise AssertionError('Authentication must not execute')
 def complete_login(self,**kwargs):raise AssertionError('Authentication must not execute')
 def verify_session(self,**kwargs):raise AssertionError('Authentication must not execute')
 def refresh_session(self,**kwargs):raise AssertionError('Authentication must not execute')
 def revoke_session(self,**kwargs):raise AssertionError('Authentication must not execute')
a=str(root/'home/a');b=str(root/'home/b');g=Provider('global');pa=Provider('private');pb=Provider('private')
def seed():
 reg.clear_providers();reg.register_global_provider(g);reg.register_provider(pa,scope=a);reg.register_provider(pb,scope=b)
def test_profiles():
 seed();assert reg.get_provider('private',scope=a) is pa and reg.get_provider('private',scope=b) is pb and reg.get_provider('private') is None
 assert reg.get_provider('global',scope=a) is g and reg.get_provider('global',scope=b) is g
 assert list(reg._merged(a))==['global','private']
def test_exact_names():
 seed();assert reg.get_provider('PRIVATE',scope=a) is None and reg.get_provider(' private ',scope=a) is None
 try:reg.register_provider(Provider('private'),scope=a)
 except ValueError:pass
 else:raise AssertionError('Duplicate accepted')
 assert reg.get_provider('private',scope=a) is pa and reg.get_provider('private',scope=b) is pb
def test_global_collision():
 seed()
 try:reg.register_provider(Provider('global'),scope=a)
 except ValueError:pass
 else:raise AssertionError('Global collision accepted')
 assert reg.get_provider('global',scope=a) is g
def test_global_rotation():
 seed();new=Provider('global');reg.register_global_provider(new);assert not reg.unregister_global_provider('global',g);assert reg.get_provider('global',scope=a) is new
 assert reg.unregister_global_provider('global',new);assert reg.get_provider('global',scope=b) is None
def test_snapshots_restore():
 seed();assert reg.snapshot_registration('global',scope=a) is None and reg.snapshot_registration('private',scope=a) is pa
 assert reg.restore_registration('private',pa,pb,scope=a);assert not reg.restore_registration('private',pa,None,scope=a)
 assert reg.get_provider('private',scope=a) is pb and reg.get_provider('private',scope=b) is pb
 assert reg.restore_registration('private',pb,pa,scope=a);assert reg.get_provider('private',scope=a) is pa
 assert reg.restore_registration('private',pa,None,scope=a);assert reg.get_provider('private',scope=a) is None and reg.get_provider('private',scope=b) is pb
def test_copy_and_scope_normalization():
 seed();copy=reg._merged(a);copy.clear();assert reg.get_provider('private',scope=a) is pa
 assert reg.get_provider('private',scope=str(root/'home/a/../a')) is pa
 assert reg.get_provider('private',scope=a) is not reg.get_provider('private',scope=b)
def test_atomic_duplicate_registration():
 reg.clear_providers();barrier=threading.Barrier(2)
 def attempt(value):
  barrier.wait(timeout=5)
  try:reg.register_provider(value,scope=a);return 'registered'
  except ValueError:return 'duplicate'
 with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
  futures=[pool.submit(attempt,Provider('racing')) for _ in range(2)];out=[f.result(timeout=10) for f in futures]
 assert sorted(out)==['duplicate','registered'] and len(reg.list_providers(scope=a))==1 and reg.list_providers(scope=b)==[]
def test_provider_capability_filters():
 reg.clear_providers();interactive=Provider('interactive');token=Provider('service');token.supports_session=False;token.supports_token=True
 provisioned=Provider('provisioned');provisioned.interactive_login=False
 for value in [interactive,token,provisioned]:reg.register_provider(value)
 assert reg.list_interactive_providers()==[interactive] and reg.list_session_providers()==[interactive,provisioned] and reg.list_token_providers()==[token]
tests=[test_profiles,test_exact_names,test_global_collision,test_global_rotation,test_snapshots_restore,test_copy_and_scope_normalization,test_atomic_duplicate_registration,test_provider_capability_filters]
try:
 for test in tests:test();results.append(test.__name__)
 assert len(results)==len(set(results))==8 and not denied
 report={'result':'PASS_BOUNDED_REAL_MODULES','tests':results,'cases':8,'upstream':'e36d4e57359074d0f9e8ccc8981bca16b893ad7f','sourceBlobs':{name:h for name,(_,h) in paths.items()},'deniedEvents':denied,'scope':'Real registry/engine/constants/protocol modules loaded in isolated package containers; parent startup not executed. Python audit hooks, not an OS sandbox. Synthetic providers never authenticate; no HTTP/login/deployment/owner-phone acceptance.'}
except BaseException:
 report={'result':'FAIL','passedBeforeFailure':results,'deniedEvents':denied,'traceback':traceback.format_exc()};raise
finally:(root/'result.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report))
