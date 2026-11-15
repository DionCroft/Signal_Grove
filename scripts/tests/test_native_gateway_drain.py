from dataclasses import replace
from datetime import datetime,timezone,timedelta
import hashlib
import importlib.util
import importlib
import json
import os
from pathlib import Path,PurePosixPath
import sqlite3
import subprocess
import stat
import sys
import time
import tempfile
import py_compile
import unittest
import uuid

scripts=Path(__file__).resolve().parents[1];sys.path.insert(0,str(scripts))
for name in ('retirement_guard','native_retirement_owner','cutover_journal','cutover_coordinator','plan_pi_runtime','native_file_publication','rendered_publication_catalog','native_operational_paths','native_service_manager','native_backup_archive','backup_sdk_snapshot','native_verified_inventory','native_gateway_control','native_gateway_drain'):
    spec=importlib.util.spec_from_file_location(name,scripts/(name+'.py'));module=importlib.util.module_from_spec(spec);sys.modules[name]=module;spec.loader.exec_module(module)
m=sys.modules['native_gateway_drain'];SchemaOwner=sys.modules['retirement_guard'].Owner

BOOT='a'*8+'-'+('b'*4)+'-'+('c'*4)+'-'+('d'*4)+'-'+('e'*12)
OPERATION='a'*32


def fence(epoch=0,owner=None,frozen=False,**counts):
    return dict(epoch=epoch,owner=owner,frozen=frozen,**{n:counts.get(n,0) for n in m.LEDGER_COUNTERS})


class DrainFenceTests(unittest.TestCase):
    def test_initial_and_released_nonzero_preimages_are_supported(self):
        self.assertEqual(fence(),m.require_fence(fence(),m.FencePreimage(0,None)))
        self.assertEqual(fence(9,'completed-mode'),m.require_fence(fence(9,'completed-mode'),m.FencePreimage(9,'completed-mode')))
    def test_new_frozen_epoch_must_follow_the_exact_reviewed_preimage(self):
        before=m.FencePreimage(9,'completed-mode');actual=fence(10,OPERATION,True)
        self.assertEqual(actual,m.require_fence(actual,before,OPERATION))
        for row in (fence(9,OPERATION,True),fence(11,OPERATION,True),fence(10,'foreign-mode',True),fence(10,OPERATION,False)):
            with self.subTest(row=row),self.assertRaises(m.Unknown):m.require_fence(row,before,OPERATION)
    def test_frozen_owner_cannot_be_adopted_as_an_unfrozen_preimage(self):
        with self.assertRaises(m.Unknown):m.require_fence(fence(5,'previous-mode',True),m.FencePreimage(5,'previous-mode'))
    def test_unknown_missing_boolean_or_negative_counters_cannot_be_idle(self):
        complete=fence()
        for row in ({},dict(complete,active_turns=False),dict(complete,active_tools=-1),dict(complete,epoch=True),dict(complete,unexpected=0)):
            with self.subTest(row=row),self.assertRaises(m.Unknown):m.fence_snapshot(row)
        for epoch,owner in ((0,'owner'),(1,None),(True,None),(-1,None),(2**63-1,'owner')):
            with self.subTest(epoch=epoch,owner=owner),self.assertRaises(ValueError):m.FencePreimage(epoch,owner)
    def test_active_and_unverified_counts_are_retained_without_exclusions(self):
        actual=fence(1,OPERATION,True,active_turns=2,active_tools=1,unverified_operations=1)
        self.assertEqual(actual,m.require_fence(actual,m.FencePreimage(0,None),OPERATION));self.assertEqual(2,actual['active_turns'])


class DrainPayloadTests(unittest.TestCase):
    def test_strong_epoch_accepts_native_zero_ticks_and_rejects_partial_identity(self):
        self.assertEqual(BOOT+':0',m.checked_epoch(BOOT,'0'))
        for boot,ticks in (('', '0'),(BOOT,''),(BOOT,'00'),(BOOT,'-1'),(BOOT,True),(BOOT,str(2**64))):
            with self.subTest(boot=boot,ticks=ticks),self.assertRaises(m.Unknown):m.checked_epoch(boot,ticks)
    def test_supported_payload_has_only_fixed_sdk_fields_and_suppresses_broadcast(self):
        value=m.marker_payload(OPERATION,BOOT+':0',datetime(2026,1,1,tzinfo=timezone.utc))
        self.assertEqual({'action','requested_at','principal','epoch','suppress_notification'},set(value));self.assertEqual('drain',value['action']);self.assertIs(True,value['suppress_notification'])
    def test_unbound_operation_or_non_utc_timestamp_is_refused(self):
        for operation,when in (('../outside',datetime.now(timezone.utc)),(False,datetime.now(timezone.utc)),(OPERATION,datetime(2026,1,1)),(OPERATION,datetime(2026,1,1,tzinfo=timezone(timedelta(hours=1))))):
            with self.subTest(operation=operation,when=when),self.assertRaises(ValueError):m.marker_payload(operation,BOOT+':0',when)


class DrainSpecTests(unittest.TestCase):
    def specimen(self):
        directory=lambda name:m.DirectoryAnchor('/fixture/'+name,1,abs(hash(name))%10000+1,1000,1000,0o700)
        home=directory('home');owner=m.UnitSnapshot('hermes-gateway.service','loaded','active','running',4000,0,'1'*32,'/fixture/hermes-gateway.service',0,'/','success','a'*64,
            SchemaOwner(4000,123,'00000000-0000-4000-8000-000000000001'),'/fixture/python',True)
        address=str(PurePosixPath('/run/user/1000')/('hermes-gw-'+hashlib.sha256(os.path.normcase(home.path).encode()).hexdigest()[:16])/'control.sock')
        return m.GatewayDrainSpec(m.SourceInventory(directory('sdk'),tuple((n,'a'*64) for n in sorted(m.SDK_REQUIRED))),m.SourceInventory(directory('executor'),tuple((n,'b'*64) for n in sorted(m.EXECUTOR_REQUIRED))),home,owner,address,m.FencePreimage(0,None),(1,2,1000,1000,0o600,1),'c'*64,'/fixture/sdk-venv','d'*64,'old_source')
    def test_sdk_and_executor_requirements_cannot_be_dropped(self):
        spec=self.specimen()
        for name in ('sdk','executor'):
            value=getattr(spec,name)
            with self.assertRaises(ValueError):replace(spec,**{name:m.SourceInventory(value.root,value.files[:-1])})
    def test_socket_cannot_redirect_to_another_home_user_or_address(self):
        spec=self.specimen()
        for address in ('/tmp/control.sock',spec.socket_path.replace('/1000/','/1001/'),spec.socket_path.replace('control.sock','other.sock')):
            with self.subTest(address=address),self.assertRaises(ValueError):replace(spec,socket_path=address)
    def test_ledger_identity_rejects_hardlinks_foreign_owners_and_unsafe_modes(self):
        spec=self.specimen()
        for value in ((1,2,1001,1000,0o600,1),(1,2,1000,1000,0o666,1),(1,2,1000,1000,0o600,2),(1,True,1000,1000,0o600,1)):
            with self.subTest(value=value),self.assertRaises(ValueError):replace(spec,ledger_identity=value)
    def test_running_gateway_and_closed_source_and_prefix_are_required(self):
        spec=self.specimen()
        for changes in (dict(before=replace(spec.before,unit='hermes-serve.service')),dict(source_role='remote_source'),dict(sdk_python_prefix='relative'),dict(sdk_python_prefix='/safe/../foreign'),dict(home=replace(spec.home,mode=0o755)),dict(scope_configuration_sha256='missing')):
            with self.subTest(changes=changes),self.assertRaises(ValueError):replace(spec,**changes)
    def test_owned_root_must_cover_actual_input_roots(self):
        with self.assertRaises(ValueError):replace(self.specimen(),owned_root='/fixture/signal-grove-catalog-'+'a'*32)
    def test_no_default_native_authority_or_live_entrypoint_is_available(self):
        spec=self.specimen()
        with self.assertRaises(m.Refused):m.NativeGatewayDrain(None,None,None,None,spec,spec.sha256)


class DrainSchemaTests(unittest.TestCase):
    def database(self):
        db=sqlite3.connect(':memory:');self.addCleanup(db.close)
        for table in m.TABLES:db.execute('CREATE TABLE '+table+'('+('owner_birth REAL' if table in ('turn_admissions','tool_admissions') else 'fixture INTEGER')+')')
        return db
    def test_schema_observation_is_bounded_and_does_not_migrate(self):
        db=self.database();before=list(db.iterdump());digest=m.schema_digest(db)
        self.assertEqual(64,len(digest));self.assertEqual(before,list(db.iterdump()))
    def test_missing_ledger_table_or_owner_column_needs_separate_migration(self):
        db=self.database();db.execute('DROP TABLE projects')
        with self.assertRaises(m.Refused):m.schema_digest(db)
        db=self.database();db.execute('DROP TABLE tool_admissions');db.execute('CREATE TABLE tool_admissions(fixture INTEGER)')
        with self.assertRaises(m.Refused):m.schema_digest(db)
    def test_triggered_or_unbounded_schema_is_not_used_for_freeze(self):
        db=self.database();db.execute('CREATE TRIGGER fixture_trigger AFTER INSERT ON projects BEGIN SELECT 1; END')
        with self.assertRaises(m.Refused):m.schema_digest(db)
        db=self.database()
        for index in range(260):db.execute('CREATE TABLE fixture_extra_'+str(index)+'(x INTEGER)')
        with self.assertRaises(m.Refused):m.schema_digest(db)


@unittest.skipUnless(sys.platform=='linux','Requires native owner directory descriptors and renameat2 no-replace')
class DrainMarkerNativeTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(prefix='signal-grove-drain-');self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name);os.chmod(self.root,0o700)
        self.home=m.anchor(self.root);self.fd=os.open(self.root,os.O_RDONLY|os.O_DIRECTORY|os.O_NOFOLLOW);self.addCleanup(os.close,self.fd)
        self.payload=m.marker_payload(OPERATION,BOOT+':0',datetime.now(timezone.utc));self.temporary=self.root/('.signal-grove-drain-'+OPERATION+'.json')
    def test_native_publication_is_atomic_exclusive_and_exact(self):
        calls=[];image=m.publish_marker(self.fd,self.home,OPERATION,self.payload,lambda:calls.append(True))
        self.assertEqual([True],calls);self.assertEqual(self.payload,json.loads((self.root/m.MARKER).read_text()));self.assertFalse(self.temporary.exists());self.assertEqual(0o600,image.mode)
    def test_existing_matching_marker_is_never_adopted_or_refreshed(self):
        target=self.root/m.MARKER;target.write_text(json.dumps(self.payload));os.chmod(target,0o600);before=target.read_bytes();identity=target.stat()
        with self.assertRaises(m.Refused):m.publish_marker(self.fd,self.home,OPERATION,self.payload,lambda:self.fail('must not authorize a replacement'))
        self.assertEqual(before,target.read_bytes());self.assertEqual(identity.st_ino,target.stat().st_ino);self.assertFalse(self.temporary.exists())
    def test_arrival_during_last_authorization_is_preserved_without_overwrite(self):
        target=self.root/m.MARKER
        def arrive():target.write_text('foreign-arrival');os.chmod(target,0o600)
        with self.assertRaises(FileExistsError):m.publish_marker(self.fd,self.home,OPERATION,self.payload,arrive)
        self.assertEqual('foreign-arrival',target.read_text());self.assertTrue(self.temporary.is_file())
    def test_failed_last_authorization_preserves_prepared_bytes_and_no_marker(self):
        def refuse():raise m.Unknown('generated approval expiry')
        with self.assertRaises(m.Unknown):m.publish_marker(self.fd,self.home,OPERATION,self.payload,refuse)
        self.assertFalse((self.root/m.MARKER).exists());self.assertEqual(self.payload,json.loads(self.temporary.read_text()))
    def test_spent_temporary_name_is_not_reused(self):
        self.temporary.write_text('retained-unknown');os.chmod(self.temporary,0o600)
        with self.assertRaises(FileExistsError):m.publish_marker(self.fd,self.home,OPERATION,self.payload,lambda:None)
        self.assertEqual('retained-unknown',self.temporary.read_text());self.assertFalse((self.root/m.MARKER).exists())
    def test_symlink_target_does_not_touch_the_referenced_file(self):
        outside=self.root/'preserve';outside.write_text('original');(self.root/m.MARKER).symlink_to(outside)
        with self.assertRaises(m.Refused):m.publish_marker(self.fd,self.home,OPERATION,self.payload,lambda:None)
        self.assertEqual('original',outside.read_text());self.assertTrue((self.root/m.MARKER).is_symlink())
    def test_untrusted_operation_or_payload_cannot_create_a_file(self):
        for operation,payload in (('../outside',self.payload),(OPERATION,{**self.payload,'principal':'other'}),(OPERATION,{**self.payload,'suppress_notification':False}),(OPERATION,{**self.payload,'unexpected':'private'})):
            with self.subTest(operation=operation,payload=payload),self.assertRaises(m.Refused):m.publish_marker(self.fd,self.home,operation,payload,lambda:None)
        self.assertEqual([],list(self.root.iterdir()))


@unittest.skipUnless(sys.platform=='linux','Requires protected native source reads')
class DrainSdkImportNativeTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(prefix='signal-grove-sdk-import-');self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name);os.chmod(self.root,0o700)
        self.name='signal_grove_drain_sdk_fixture';self.source=self.root/(self.name+'.py');self.old_path=list(sys.path);self.old_meta=list(sys.meta_path);self.addCleanup(self.restore)
    def restore(self):
        sys.path[:]=self.old_path;sys.meta_path[:]=self.old_meta;sys.modules.pop(self.name,None);importlib.invalidate_caches()
    def install(self,content):
        self.source.write_text(content);inventory=m.SourceInventory(m.anchor(self.root),((self.source.name,hashlib.sha256(self.source.read_bytes()).hexdigest()),));gate=m._SdkSourceImports(inventory)
        sys.path.insert(0,str(self.root));sys.meta_path.insert(0,gate);importlib.invalidate_caches();return gate
    def test_reviewed_source_executes_instead_of_an_unchecked_bytecode_cache(self):
        self.source.write_text("value='unreviewed-cache'\n");py_compile.compile(str(self.source),doraise=True,invalidation_mode=py_compile.PycInvalidationMode.UNCHECKED_HASH)
        gate=self.install("value='reviewed-source'\n");loaded=importlib.import_module(self.name)
        self.assertEqual('reviewed-source',loaded.value);self.assertEqual(hashlib.sha256(self.source.read_bytes()).hexdigest(),gate.recorded[str(self.source)])
    def test_changed_source_is_refused_before_any_module_effect(self):
        self.install("value='reviewed-source'\n");effect=self.root/'unexpected-effect';self.source.write_text('from pathlib import Path\nPath('+repr(str(effect))+').write_text("must-not-run")\n')
        with self.assertRaises(ImportError):importlib.import_module(self.name)
        self.assertFalse(effect.exists())
    def test_sdk_namespace_cannot_be_satisfied_from_another_search_root(self):
        self.install("value='reviewed-source'\n");other=self.root/'other';other.mkdir(mode=0o700);(other/(self.name+'.py')).write_text("value='foreign-source'\n");sys.path.insert(0,str(other));importlib.invalidate_caches()
        with self.assertRaises(ImportError):importlib.import_module(self.name)
    def test_unlisted_sdk_root_module_cannot_fall_through_to_an_unverified_loader(self):
        self.install("value='reviewed-source'\n");name='signal_grove_unlisted_sdk_fixture';(self.root/(name+'.py')).write_text("value='unreviewed-source'\n");importlib.invalidate_caches()
        try:
            with self.assertRaises(ImportError):importlib.import_module(name)
        finally:sys.modules.pop(name,None)


@unittest.skipUnless(sys.platform=='linux','Requires real native controller/journal and owned gateway service')
class DrainActorNativeRefusalTests(unittest.TestCase):
    def load(self,name,path):
        spec=importlib.util.spec_from_file_location(name,path);module=importlib.util.module_from_spec(spec);sys.modules[name]=module;spec.loader.exec_module(module);return module
    def setUp(self):
        for name in ('retirement_guard','native_retirement_owner'):self.load(name,scripts/(name+'.py'))
        helper=self.load('operational_paths_fixture',scripts/'native/operational_paths_fixture.py');self.fixture=helper.build(Path(os.environ['SIGNAL_GROVE_TRANSACTION_RETAIN_ROOT']))
        self.root=self.fixture['root'];self.paths=self.fixture['paths'];self.guard=self.fixture['guard'];self.core=self.fixture['modules']
        for name in ('native_service_manager','native_gateway_control','backup_sdk_snapshot','native_verified_inventory','native_gateway_drain'):self.load(name,scripts/(name+'.py'))
        self.m=sys.modules['native_gateway_drain'];self.service=sys.modules['native_service_manager'];self.c=self.core['cutover_coordinator'];self.errors=self.core['cutover_journal']
        group=Path('/proc/self/cgroup').read_text().strip().split('::',1)[-1];match=__import__('re').search(r'signal-grove-(?:acceptance-([a-f0-9]{32})-pm|cutover-([a-f0-9]{32}))\.service$',group)
        self.assertIsNotNone(match);self.operation=match[1] or match[2];self.plan=self.c.Plan('a'*64,'b'*64,'c'*64,'d'*64)
        ledger=self.paths['journal'];self.j=self.errors.CutoverJournal(ledger,expected_identity=(ledger.stat().st_dev,ledger.stat().st_ino),operational_paths=self.guard,expected_operational_sha256=self.guard.sha256);self.addCleanup(self.j.close)
        self.effect=self.root/'sdk-import-effect'
        for name in self.m.SDK_REQUIRED:
            p=self.paths['old_source']/name;p.parent.mkdir(parents=True,exist_ok=True,mode=0o700)
            body='from pathlib import Path\nPath('+repr(str(self.effect))+').write_text("synthetic SDK imported")\n' if name=='agent/__init__.py' else '# Synthetic identities for refusal tests; no real SDK qualification.\n'
            p.write_text(body)
        for name in self.m.EXECUTOR_REQUIRED:(self.paths['executor']/name).write_bytes((scripts/name).read_bytes())
        self.db_path=self.paths['hermes_home']/'project-control.db'
        with sqlite3.connect(self.db_path) as db:
            for table in self.m.TABLES:db.execute('CREATE TABLE '+table+'('+('owner_birth REAL' if table in ('turn_admissions','tool_admissions') else 'fixture INTEGER')+')')
        os.chmod(self.db_path,0o600)
        self.server_root=self.root/'protocol';self.server_root.mkdir(mode=0o700);self.service_operation=uuid.uuid4().hex;self.unit='signal-grove-acceptance-'+self.service_operation+'-gateway.service';self.dispatched=False;self.runtime_created=False
        self.runtime=Path('/run/user')/str(os.geteuid())/('hermes-gw-'+hashlib.sha256(os.path.normcase(str(self.paths['hermes_home'])).encode()).hexdigest()[:16]);self.address=self.runtime/'control.sock'
        self.runtime.mkdir(mode=0o700);self.runtime_created=True;self.runtime_identity=self.runtime.stat();self.addCleanup(self.cleanup_runtime);self.addCleanup(self.stop_service)
        self.python=Path(sys.executable).absolute();self.executable=str(self.python.resolve());self.argv=[str(self.python),'-I','-B',str(scripts/'native/gateway_control_server.py'),str(self.server_root),os.fsencode(self.address).hex(),'normal']
        self.configuration=self.core['native_file_publication'].digest(dict(unit=self.unit,fragment='/run/user/'+str(os.geteuid())+'/systemd/transient/'+self.unit,dropins=[],kill_mode='control-group',service_type='exec',commands={'ExecStart':((str(self.python),tuple(self.argv),False),),'ExecStop':(),'ExecStopPost':()}))
        (self.server_root/'service-intent.json').write_text(json.dumps(dict(case=self.id(),controller_operation=self.operation,unit=self.unit,configuration_sha256=self.configuration,argv=self.argv,runtime=str(self.runtime)),indent=2))
        a=json.loads(os.environ['SIGNAL_GROVE_PM_BUS_AUTHORITY']);Owner=sys.modules['retirement_guard'].Owner
        authority=self.service.BusAuthority(a['uid'],a['busIdentity'][0],a['busIdentity'][1],Owner(**a['peerOwner']),a['managerUnique'],Owner(**a['managerOwner']))
        bindings=tuple(self.service.UnitBinding(role,'signal-grove-acceptance-'+self.service_operation+'-'+role+'.service',self.configuration,self.configuration,self.executable,self.executable) for role in ('gateway','serve'))
        self.manager=self.service.NativeServiceManager(authority,self.plan,self.j,bindings);self.addCleanup(self.manager.close);self.scope=self.manager.execution_scope(self.operation,owned_fixture=True)
        old=os.environ.get('HERMES_HOME');os.environ['HERMES_HOME']=str(self.paths['hermes_home']);self.addCleanup(lambda:os.environ.pop('HERMES_HOME',None) if old is None else os.environ.__setitem__('HERMES_HOME',old))
        self.before=self.start()
        sdk=self.m.SourceInventory(self.m.anchor(self.paths['old_source']),tuple(sorted((name,hashlib.sha256((self.paths['old_source']/name).read_bytes()).hexdigest()) for name in self.m.SDK_REQUIRED)))
        executor=self.m.SourceInventory(self.m.anchor(self.paths['executor']),tuple(sorted((name,hashlib.sha256((self.paths['executor']/name).read_bytes()).hexdigest()) for name in self.m.EXECUTOR_REQUIRED)))
        with sqlite3.connect(self.db_path) as db:schema_hash=self.m.schema_digest(db)
        self.spec=self.m.GatewayDrainSpec(sdk,executor,self.m.anchor(self.paths['hermes_home']),self.before,str(self.address),self.m.FencePreimage(0,None),self.m.ledger_stamp(self.db_path.stat()),schema_hash,str(Path(sys.prefix)),self.scope.configuration_sha256,'old_source',str(self.root))
        self.e=self.c.ApprovedExecution(self.operation,self.plan.packet_sha256,self.plan.authority_sha256,'forward','e'*64,(self.scope.active_enter_usec+self.scope.runtime_max_usec+self.scope.stop_timeout_usec)/1e6+5)
    def start(self):
        probe=subprocess.run(['systemctl','--user','show',self.unit,'--property=LoadState,ActiveState'],capture_output=True,timeout=8);self.assertIn(b'LoadState=not-found',probe.stdout)
        self.dispatched=True
        result=subprocess.run(['systemd-run','--user','--quiet','--unit='+self.unit,'--property=Type=exec','--property=RuntimeMaxSec=120','--property=TimeoutStopSec=5','--property=Restart=no','--property=KillMode=control-group','--property=UMask=0077','--setenv=PYTHONDONTWRITEBYTECODE=1',*self.argv],capture_output=True,timeout=15);self.assertEqual(0,result.returncode,result.stderr[-500:])
        deadline=time.monotonic()+10
        while not (self.server_root/'observations.json').is_file() and time.monotonic()<deadline:time.sleep(.02)
        self.assertTrue((self.server_root/'observations.json').is_file());self.manager._manager('RefUnit','s',(self.unit,),'');before=self.manager.snapshot('gateway')
        self.assertEqual(self.configuration,before.configuration_sha256);self.assertEqual(json.loads((self.server_root/'observations.json').read_text())['pid'],before.main_pid)
        (self.server_root/'native-start.json').write_text(json.dumps(__import__('dataclasses').asdict(before),indent=2));return before
    def stop_service(self):
        if not self.dispatched:return
        before=subprocess.run(['systemctl','--user','show',self.unit,'--property=LoadState,ExecStart'],capture_output=True,timeout=8)
        if b'LoadState=loaded' in before.stdout:
            self.assertIn(str(scripts/'native/gateway_control_server.py').encode(),before.stdout);self.assertIn(str(self.server_root).encode(),before.stdout)
            result=subprocess.run(['systemctl','--user','stop',self.unit],capture_output=True,timeout=15);self.assertEqual(0,result.returncode)
        row=subprocess.run(['systemctl','--user','show',self.unit,'--property=MainPID,ControlPID,NRestarts,ControlGroup,ActiveState'],capture_output=True,timeout=8)
        values=dict(line.split('=',1) for line in row.stdout.decode().splitlines() if '=' in line);self.assertEqual(('0','0','0','','inactive'),tuple(values[n] for n in ('MainPID','ControlPID','NRestarts','ControlGroup','ActiveState')))
        (self.server_root/'cleanup-native.json').write_text(json.dumps(values,indent=2))
    def cleanup_runtime(self):
        if not self.runtime_created:return
        current=self.runtime.stat();self.assertEqual((self.runtime_identity.st_dev,self.runtime_identity.st_ino),(current.st_dev,current.st_ino))
        if self.address.exists():
            info=self.address.lstat();self.assertTrue(stat.S_ISSOCK(info.st_mode));self.assertEqual(os.geteuid(),info.st_uid);self.address.unlink()
        self.runtime.rmdir()
    def actor(self):return self.m.NativeGatewayDrain(self.plan,self.j,self.manager,self.guard,self.spec,self.spec.sha256)
    def begin(self,pending=True):
        self.j.begin(self.operation,self.plan.packet_sha256,authorization_sha256=self.e.binding_sha256)
        if pending:
            for step in self.c.FORWARD[:self.c.FORWARD.index('gateway_drain')]:self.j.intent(step);self.j.verified(step,'f'*64)
            self.j.intent('gateway_drain')
    def requests(self):return json.loads((self.server_root/'observations.json').read_text())['requests']
    def test_preparation_reads_sources_without_sdk_import_or_ledger_open(self):
        self.begin();active=[True];opens=[]
        def audit(event,args):
            if active[0] and event=='sqlite3.connect':opens.append(args[0])
        sys.addaudithook(audit)
        try:result=self.actor().prepare_inputs(self.e)
        finally:active[0]=False
        self.assertEqual([],opens);self.assertFalse(self.effect.exists());self.assertEqual([],self.requests());self.assertEqual(len(self.m.SDK_REQUIRED),result['sdk']['files']);self.assertEqual(set(),self.j._dispatch_claims)
    def test_unproven_ready_reply_is_unknown_after_one_claim_and_cannot_replay(self):
        self.begin();actor=self.actor();before=self.db_path.read_bytes()
        with self.assertRaises(self.errors.Unknown):actor.run(self.e)
        self.assertEqual('unknown',actor.state);self.assertTrue(actor.used);self.assertEqual(0,actor.freeze_calls);self.assertFalse(self.effect.exists());self.assertEqual(before,self.db_path.read_bytes());self.assertFalse((self.paths['hermes_home']/self.m.MARKER).exists())
        self.assertEqual(['identify'],[r['verb'] for r in self.requests()]);self.assertEqual('gateway_drain',self.j.view['pending']);self.assertEqual(self.before,self.manager.snapshot('gateway'))
        raw=(self.paths['journal']/actor.last_receipt).read_bytes();self.assertIn(b'gateway_observation_held',raw)
        with self.assertRaises(self.errors.Refused):actor.run(self.e)
        self.assertEqual(1,len(self.requests()))
    def test_expired_approval_never_claims_or_imports_or_connects(self):
        self.begin();actor=self.actor()
        with self.assertRaises(self.errors.Unknown):actor.run(replace(self.e,deadline=time.monotonic()-1))
        self.assertEqual('preflight_refused',actor.state);self.assertEqual(set(),self.j._dispatch_claims);self.assertFalse(self.effect.exists());self.assertEqual([],self.requests())
    def test_missing_pending_prefix_never_prepares_inputs(self):
        self.begin(pending=False)
        with self.assertRaises(self.errors.Refused):self.actor().prepare_inputs(self.e)
        self.assertFalse(self.effect.exists());self.assertEqual([],self.requests())
    def test_existing_marker_is_preserved_before_claim_or_rpc(self):
        self.begin();actor=self.actor();actor.prepare_inputs(self.e);target=self.paths['hermes_home']/self.m.MARKER;target.write_text('retained-original');os.chmod(target,0o600)
        with self.assertRaises(self.errors.Refused):actor.run(self.e)
        self.assertEqual('retained-original',target.read_text());self.assertEqual(set(),self.j._dispatch_claims);self.assertEqual([],self.requests());self.assertFalse(self.effect.exists())
    def test_replaced_ledger_is_unknown_before_claim_or_rpc(self):
        self.begin();actor=self.actor();actor.prepare_inputs(self.e);retained=self.db_path.with_name('retained-ledger.db');self.db_path.rename(retained);self.db_path.write_bytes(retained.read_bytes());os.chmod(self.db_path,0o600)
        with self.assertRaises(self.errors.Unknown):actor.run(self.e)
        self.assertEqual(set(),self.j._dispatch_claims);self.assertEqual([],self.requests());self.assertFalse(self.effect.exists());self.assertTrue(retained.is_file())
    def test_changed_prepared_source_never_claims_or_imports(self):
        self.begin();actor=self.actor();actor.prepare_inputs(self.e);(self.paths['old_source']/'agent/__init__.py').write_text('# changed after preparation\n')
        with self.assertRaises(self.errors.Unknown):actor.run(self.e)
        self.assertEqual(set(),self.j._dispatch_claims);self.assertEqual([],self.requests());self.assertFalse(self.effect.exists())
    def test_preparation_is_spent_without_a_second_import_attempt(self):
        self.begin();actor=self.actor();actor.prepare_inputs(self.e)
        with self.assertRaises(self.errors.Refused):actor.prepare_inputs(self.e)
        self.assertFalse(self.effect.exists());self.assertEqual([],self.requests())
