"""Eight isolated real-SQLite regressions for the reviewed heartbeat candidate.

Uses the actual patched helper and official binding mechanism with an explicit
synthetic server/callback registry. This is not native owner/deployed acceptance.
"""
from pathlib import Path
import argparse, hashlib, importlib.util, json, logging, math, os, sys, threading, time, types, unittest, urllib.parse

if not __debug__ or not (sys.flags.isolated and sys.flags.no_site and sys.flags.dont_write_bytecode):
    raise SystemExit('Run -I -S -B without -O; assertions and isolated imports are required')
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--base-root',required=True,type=Path)
parser.add_argument('--source-root',required=True,type=Path)
parser.add_argument('--dependency-site',required=True,type=Path)
parser.add_argument('--work-root',required=True,type=Path)
a=parser.parse_args()
helper_path=Path(__file__).resolve().with_name('hermes_heartbeat_candidate.py')
spec=importlib.util.spec_from_file_location('signal_grove_heartbeat_candidate',helper_path)
helper=importlib.util.module_from_spec(spec);sys.modules[spec.name]=helper;spec.loader.exec_module(helper)
source=helper.absolute_root(a.source_root);base=helper.absolute_root(a.base_root);work=helper.absolute_root(a.work_root)
if work.exists() or any(x.is_relative_to(y) for x,y in ((work,source),(source,work),(work,base),(base,work))):
    raise ValueError('Use a fresh separate fixture output; never replay a partial root')
site_alias=a.dependency_site.absolute();site=site_alias.resolve()
if not a.dependency_site.is_absolute() or not site.is_dir():raise ValueError('An existing dependency site is required')
proof=helper.verify_candidate(base,source)
work.mkdir(parents=True,exist_ok=False)
for p in (work/'account',work/'temp',work/'home'):p.mkdir()
system={k:v for k,v in os.environ.items() if k.upper() in ('SYSTEMROOT','WINDIR','PATH','PATHEXT')}
os.environ.clear();os.environ.update(system)
os.environ.update(TEMP=str(work/'temp'),TMP=str(work/'temp'),TMPDIR=str(work/'temp'),HOME=str(work/'account'),USERPROFILE=str(work/'account'),APPDATA=str(work/'account'),LOCALAPPDATA=str(work/'account'),HERMES_HOME=str(work/'home'),HERMES_TEST_ISOLATION='1',HERMES_DISABLE_LAZY_INSTALLS='1',PYTHONDONTWRITEBYTECODE='1')
os.chdir(work);sys.path[:0]=[str(source),str(site_alias)];denied=[]
reads=[source,work,site,Path(sys.base_prefix).resolve(),Path(__file__).resolve(),helper_path]
missing={}
if os.name=='nt':
    for name in ('/proc/1/cgroup','/proc/self/mountinfo'):
        target=Path(work.drive+name).resolve()
        if target.exists():raise ValueError('Unexpected existing Linux probe path')
        missing[name]=target;missing[str(Path(name))]=target
absent_manifest=source.parent/'manifest.json'
if not absent_manifest.exists():missing[str(absent_manifest)]=absent_manifest
def inside(p,roots):return any(p.is_relative_to(q) for q in roots)
def audit(event,args):
    def refuse():denied.append(event);raise PermissionError('Heartbeat fixture boundary: '+event)
    if event.startswith(('subprocess.','os.system','os.startfile')) or event in ('socket.bind','socket.connect','socket.getaddrinfo'):refuse()
    if event=='open' and isinstance(args[0],(str,bytes,os.PathLike)):
        p=Path(os.fsdecode(args[0])).resolve();mode=args[1] or '';flags=args[2] or 0
        write=any(x in mode for x in 'wax+') or bool(flags&(os.O_WRONLY|os.O_RDWR|os.O_CREAT|os.O_TRUNC|os.O_APPEND))
        if not write and str(args[0]) in missing and p==missing[str(args[0])] and not p.exists():return
        if str(args[0]).lower()!=os.devnull.lower() and not inside(p,[work] if write else reads):refuse()
    if event=='sqlite3.connect' and str(args[0])!=':memory:':
        value=str(args[0])
        if value.startswith('file:'):
            url=urllib.parse.urlsplit(value)
            if url.netloc not in ('','localhost'):refuse()
            value=urllib.parse.unquote(url.path)
            if value.startswith('/') and len(value)>2 and value[2]==':':value=value[1:]
        if not Path(value).resolve().is_relative_to(work):refuse()
    if event=='os.chdir' and not Path(args[0]).resolve().is_relative_to(work):refuse()
    if event in ('os.mkdir','os.remove','os.rmdir','os.rename','os.link','os.symlink'):
        for value in args[:2] if event in ('os.rename','os.link','os.symlink') else args[:1]:
            if not Path(value).resolve().is_relative_to(work):refuse()
sys.addaudithook(audit)
from hermes_state import SessionDB
from tui_gateway import session_reaper as reaper
class Callbacks:
 def __init__(self):self.callbacks=[]
 def register(self,fn):self.callbacks.append(fn)
class HeartbeatLifecycleTest(unittest.TestCase):
 def setUp(self):
  self.db=SessionDB(work/(self._testMethodName+'.db'));self.gets=0;self.callbacks=Callbacks();self.rescue=[];self.threads=[];self.expectedFailure=False
  logger=logging.getLogger('heartbeat-lifecycle-fixture.'+self._testMethodName);logger.propagate=False;logger.addHandler(logging.NullHandler())
  self.server=types.SimpleNamespace(threading=threading,time=time,os=os,atexit=self.callbacks,logger=logger,_current_profile_name=lambda:'default')
  reaper.register(self.server)
  def getdb():self.gets+=1;return self.db
  self.server._get_db=getdb;self.server._backend_id_for_this_process=lambda:'fixture-backend';self.server._HEARTBEAT_REFRESH_S=3600.0
 def tearDown(self):
  for event in self.rescue:event.set()
  for thread in self.threads:thread.join(5);self.assertFalse(thread.is_alive())
  drained=self.server._stop_backend_heartbeat_refresher(5)
  self.assertEqual(drained,not self.expectedFailure)
  self.db.close()
 def stop(self):self.assertTrue(self.server._stop_backend_heartbeat_refresher(5))
 def callbacks_after_close(self):
  self.db.close();gets=self.gets
  for fn in self.callbacks.callbacks:fn();fn()
  self.assertEqual(self.gets,gets);self.assertIsNone(self.db._conn)
 def test_periodic_registration_is_once_and_exit_after_close_never_reopens(self):
  self.server._start_backend_heartbeat_refresher();self.server._start_backend_heartbeat_refresher()
  self.assertEqual([r['backend_id'] for r in self.db.list_backend_heartbeats()],['fixture-backend']);self.stop()
  state=self.server._heartbeat_refresher_state;self.assertFalse(state['thread'].is_alive());self.assertEqual(self.db.list_backend_heartbeats(),[]);self.callbacks_after_close()
 def test_register_once_mode_has_no_periodic_thread_and_clears(self):
  self.server._HEARTBEAT_REFRESH_S=0;self.server._start_backend_heartbeat_refresher();self.server._start_backend_heartbeat_refresher()
  self.assertEqual(self.gets,1);self.assertIsNone(self.server._heartbeat_refresher_state['thread']);self.assertEqual(len(self.db.list_backend_heartbeats()),1)
  self.stop();self.assertEqual(self.db.list_backend_heartbeats(),[]);self.callbacks_after_close()
 def test_blocked_initial_write_keeps_gate_closed_until_it_finishes(self):
  entered=threading.Event();release=threading.Event();self.rescue.append(release);original=self.server._refresh_backend_heartbeat
  def held():entered.set();assert release.wait(5);return original()
  self.server._refresh_backend_heartbeat=held;thread=threading.Thread(target=self.server._start_backend_heartbeat_refresher,daemon=True);self.threads.append(thread);thread.start();self.assertTrue(entered.wait(5))
  self.assertFalse(self.server._stop_backend_heartbeat_refresher(0));self.assertIsNotNone(self.db._conn);release.set();thread.join(5);self.assertFalse(thread.is_alive());self.stop()
  self.assertIsNone(self.server._heartbeat_refresher_state['thread']);self.assertEqual(self.db.list_backend_heartbeats(),[]);self.callbacks_after_close()
 def test_blocked_periodic_write_keeps_gate_closed_until_it_finishes(self):
  entered=threading.Event();release=threading.Event();self.rescue.append(release);original=self.server._refresh_backend_heartbeat;calls=[]
  def held():
   calls.append(1)
   if len(calls)==2:entered.set();assert release.wait(5)
   return original()
  self.server._refresh_backend_heartbeat=held;self.server._start_backend_heartbeat_refresher();self.assertTrue(entered.wait(5));self.assertFalse(self.server._stop_backend_heartbeat_refresher(0));self.assertIsNotNone(self.db._conn)
  release.set();self.stop();self.assertEqual(len(calls),2);self.assertEqual(self.db.list_backend_heartbeats(),[]);self.callbacks_after_close()
 def test_blocked_cleanup_is_bounded_and_is_not_replayed(self):
  entered=threading.Event();release=threading.Event();self.rescue.append(release);original=self.db.clear_backend_heartbeat;calls=[]
  def held(backend):calls.append(backend);entered.set();assert release.wait(5);return original(backend)
  self.db.clear_backend_heartbeat=held;self.server._HEARTBEAT_REFRESH_S=0;self.server._start_backend_heartbeat_refresher();self.assertFalse(self.server._stop_backend_heartbeat_refresher(0));self.assertTrue(entered.wait(5));self.assertIsNotNone(self.db._conn)
  release.set();self.stop();self.assertEqual(calls,['fixture-backend']);self.callbacks_after_close();self.assertEqual(calls,['fixture-backend'])
 def test_failed_cleanup_refuses_close_and_is_not_retried(self):
  calls=[]
  def failed(backend):calls.append(backend);raise OSError('fixture cleanup failure')
  self.db.clear_backend_heartbeat=failed;self.server._HEARTBEAT_REFRESH_S=0;self.server._start_backend_heartbeat_refresher();self.expectedFailure=True
  self.assertFalse(self.server._stop_backend_heartbeat_refresher(5));self.assertFalse(self.server._stop_backend_heartbeat_refresher(5));self.assertEqual(calls,['fixture-backend']);self.assertIsNotNone(self.db._conn)
  self.callbacks_after_close();self.assertEqual(calls,['fixture-backend'])
 def test_stop_before_start_prevents_late_registration_without_opening_db(self):
  self.assertTrue(self.server._stop_backend_heartbeat_refresher(0));self.server._start_backend_heartbeat_refresher();self.assertEqual(self.gets,0);self.assertEqual(self.callbacks.callbacks,[])
 def test_bad_timeout_does_not_change_lifecycle_state(self):
  for timeout in (-1,math.inf,math.nan):
   with self.assertRaises(ValueError):self.server._stop_backend_heartbeat_refresher(timeout)
  self.assertFalse(self.server._heartbeat_shutdown_requested);self.assertEqual(self.gets,0)
suite=unittest.defaultTestLoader.loadTestsFromTestCase(HeartbeatLifecycleTest)
with (work/'unittest.log').open('w',encoding='utf-8') as log:
    result=unittest.TextTestRunner(stream=log,verbosity=2).run(suite)
modules=[]
for name,module in list(sys.modules.items()):
    value=getattr(module,'__file__',None)
    if value:
        path=Path(value).resolve()
        if path.is_relative_to(source) and path.suffix=='.py':
            rel=path.relative_to(source).as_posix();raw=path.read_bytes()
            if hashlib.sha256(raw).hexdigest()!=proof['source_sha256'][rel]:raise AssertionError('SDK source changed')
            expected=proof['patched_git_blobs'].get(rel,proof['base_git_blobs'][rel])
            if helper.git_blob(raw)!=expected:raise AssertionError('SDK blob identity changed')
            modules.append(dict(module=name,path=rel,git_blob=expected))
record=dict(state='PASS_EIGHT_SOURCE_BOUND_HEARTBEAT_REGRESSIONS' if result.wasSuccessful() and not denied else 'FAILURE_RETAINED',
            tests=result.testsRun,failures=len(result.failures),errors=len(result.errors),denials=denied,
            base_commit=proof['base_commit'],patch_sha256=proof['patch_sha256'],imported_sdk_modules=modules,
            harness_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
            actual_source_bound_helpers_and_sqlite=True,synthetic_server_and_callback_registry=True,
            controlled_fixture_writes_and_failures=True,whole_owner_runtime_accepted=False,deployed=False)
(work/'record.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
print(json.dumps({k:v for k,v in record.items() if k!='imported_sdk_modules'}),flush=True)
if not result.wasSuccessful() or denied:raise SystemExit(1)
