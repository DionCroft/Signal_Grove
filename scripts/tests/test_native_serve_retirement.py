from dataclasses import asdict,replace
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import socket
import subprocess
import sys
import time
import unittest
import uuid
from unittest.mock import patch

scripts=Path(__file__).resolve().parents[1];sys.path.insert(0,str(scripts))
for module_name in ('retirement_guard','native_retirement_owner','cutover_journal','cutover_coordinator','plan_pi_runtime','native_file_publication','rendered_publication_catalog','native_operational_paths','native_service_manager','pinned_retirement_http','native_backup_archive','backup_sdk_snapshot','native_verified_inventory','native_serve_retirement'):
    module_spec=importlib.util.spec_from_file_location(module_name,scripts/(module_name+'.py'));module=importlib.util.module_from_spec(module_spec);sys.modules[module_name]=module;module_spec.loader.exec_module(module)
schema=sys.modules['native_serve_retirement'];SchemaOwner=sys.modules['retirement_guard'].Owner


class ServeRetirementSchemaTests(unittest.TestCase):
    def specimen(self):
        root=lambda n:schema.DirectoryAnchor('/fixture/'+n,1,abs(hash(n))%10000+1,1000,1000,0o700)
        owner=schema.UnitSnapshot('hermes-serve.service','loaded','active','running',4000,0,'1'*32,'/fixture/hermes-serve.service',0,'/','success','a'*64,
            SchemaOwner(4000,123,'00000000-0000-4000-8000-000000000001'),'/fixture/python',True,needs_daemon_reload=False)
        return schema.ServeRetirementSpec(schema.SourceInventory(root('sdk'),tuple((n,'a'*64) for n in sorted(schema.SDK_REQUIRED))),
            schema.SourceInventory(root('executor'),tuple((n,'b'*64) for n in sorted(schema.EXECUTOR_REQUIRED))),root('home'),owner,9118,'c'*64,'old_source')
    def test_required_source_and_executor_bindings_cannot_be_dropped(self):
        spec=self.specimen()
        for field in ('sdk','executor'):
            inventory=getattr(spec,field)
            with self.assertRaises(ValueError):replace(spec,**{field:schema.SourceInventory(inventory.root,inventory.files[:-1])})
    def test_only_native_serve_roles_and_exact_port_shapes_are_accepted(self):
        spec=self.specimen()
        for changes in (dict(port=True),dict(port=0),dict(port=65536),dict(before=replace(spec.before,unit='hermes-gateway.service'))):
            with self.subTest(changes=changes),self.assertRaises(ValueError):replace(spec,**changes)
    def test_absent_owner_requires_no_socket_port(self):
        spec=self.specimen();absent=replace(spec.before,active_state='inactive',sub_state='dead',main_pid=0,invocation='',cgroup='',owner=None,executable='')
        self.assertEqual(0,replace(spec,before=absent,port=0).port)
        with self.assertRaises(ValueError):replace(spec,before=absent)
    def test_source_role_and_controller_digest_are_closed(self):
        for changes in (dict(source_role='remote_source'),dict(scope_configuration_sha256='missing'),dict(home=replace(self.specimen().home,mode=0o755))):
            with self.assertRaises(ValueError):replace(self.specimen(),**changes)
    def test_owned_fixture_root_must_cover_all_inputs(self):
        with self.assertRaises(ValueError):replace(self.specimen(),owned_root='/fixture/signal-grove-catalog-'+'a'*32)


@unittest.skipUnless(sys.platform=='linux','Requires finite native controller, owned serve service and kernel-verified TCP owner')
class NativeServeRetirementTests(unittest.TestCase):
    def setUp(self):
        # Bind all native class generations together, independently of discovery order.
        for name in ('retirement_guard','native_retirement_owner'):
            self.load(name,scripts/(name+'.py'))
        helper=self.load('operational_paths_fixture',scripts/'native/operational_paths_fixture.py')
        self.fixture=helper.build(Path(os.environ['SIGNAL_GROVE_TRANSACTION_RETAIN_ROOT']));self.core=self.fixture['modules'];self.paths=self.fixture['paths'];self.root=self.fixture['root'];self.guard=self.fixture['guard']
        for name in ('native_service_manager','pinned_retirement_http','backup_sdk_snapshot','native_verified_inventory','native_serve_retirement'):
            self.load(name,scripts/(name+'.py'))
        self.r=sys.modules['native_serve_retirement'];self.service=sys.modules['native_service_manager'];self.c=self.core['cutover_coordinator'];self.errors=self.core['cutover_journal']
        self.operation=re.search(r'signal-grove-acceptance-([a-f0-9]{32})-pm\.service$',Path('/proc/self/cgroup').read_text().strip().split('::',1)[-1])[1]
        self.plan=self.c.Plan('a'*64,'b'*64,'c'*64,'d'*64)
        ledger=self.paths['journal'];self.j=self.errors.CutoverJournal(ledger,expected_identity=(ledger.stat().st_dev,ledger.stat().st_ino),operational_paths=self.guard,expected_operational_sha256=self.guard.sha256);self.addCleanup(self.j.close)
        for n in self.r.SDK_REQUIRED:
            p=self.paths['old_source']/n;p.parent.mkdir(parents=True,exist_ok=True,mode=0o700);p.write_bytes(b'# Synthetic SDK identity for protocol-unit tests only.\n')
        for n in self.r.EXECUTOR_REQUIRED:(self.paths['executor']/n).write_bytes((scripts/n).read_bytes())
        self.server_root=self.root/'protocol-server';self.server_root.mkdir(mode=0o700)
        self.service_operation=uuid.uuid4().hex;self.unit='signal-grove-acceptance-'+self.service_operation+'-serve.service';self.dispatched=False
        self.python=Path(sys.executable).absolute();self.executable=str(self.python.resolve());self.argv=[str(self.python),'-I','-B',str(scripts/'native/serve_retirement_server.py'),str(self.server_root)]
        self.configuration=self.core['native_file_publication'].digest(dict(unit=self.unit,fragment='/run/user/'+str(os.geteuid())+'/systemd/transient/'+self.unit,dropins=[],kill_mode='control-group',service_type='exec',commands={'ExecStart':((str(self.python),tuple(self.argv),False),),'ExecStop':(),'ExecStopPost':()}))
        with (self.server_root/'service-intent.json').open('x',encoding='utf-8') as out:json.dump(dict(case=self.id(),controller_operation=self.operation,unit=self.unit,configuration_sha256=self.configuration,argv=self.argv),out,indent=2)
        self.addCleanup(self.stop_owned_service)
        a=json.loads(os.environ['SIGNAL_GROVE_PM_BUS_AUTHORITY']);Owner=sys.modules['retirement_guard'].Owner
        authority=self.service.BusAuthority(a['uid'],a['busIdentity'][0],a['busIdentity'][1],Owner(**a['peerOwner']),a['managerUnique'],Owner(**a['managerOwner']))
        bindings=tuple(self.service.UnitBinding(role,'signal-grove-acceptance-'+self.service_operation+'-'+role+'.service',self.configuration,self.configuration,self.executable,self.executable) for role in ('gateway','serve'))
        self.manager=self.service.NativeServiceManager(authority,self.plan,self.j,bindings);self.addCleanup(self.manager.close)
        self.scope=self.manager.execution_scope(self.operation,owned_fixture=True)

    def load(self,name,path):
        spec=importlib.util.spec_from_file_location(name,path);module=importlib.util.module_from_spec(spec);sys.modules[name]=module;spec.loader.exec_module(module);return module

    def start(self,mode='normal'):
        (self.server_root/'server-config.json').write_text(json.dumps(dict(mode=mode)),encoding='utf-8')
        before=subprocess.run(['systemctl','--user','show',self.unit,'--property=LoadState,ActiveState'],capture_output=True,timeout=8)
        self.assertIn(b'LoadState=not-found',before.stdout);self.assertIn(b'ActiveState=inactive',before.stdout)
        self.dispatched=True
        command=['systemd-run','--user','--quiet','--unit='+self.unit,'--property=Type=exec','--property=RuntimeMaxSec=120','--property=TimeoutStopSec=5','--property=Restart=no','--property=KillMode=control-group','--property=UMask=0077','--setenv=PYTHONDONTWRITEBYTECODE=1',*self.argv]
        result=subprocess.run(command,capture_output=True,timeout=15);self.assertEqual(result.returncode,0,result.stderr[-500:])
        deadline=time.monotonic()+10
        while not (self.server_root/'observations.json').exists() and time.monotonic()<deadline:time.sleep(0.02)
        self.assertTrue((self.server_root/'observations.json').is_file())
        self.manager._manager('RefUnit','s',(self.unit,),'') # Keep this owned transient definition loaded for explicit absence checks.
        row=self.manager.snapshot('serve');self.assertEqual(self.configuration,row.configuration_sha256);self.assertEqual(self.observations()['pid'],row.main_pid)
        with (self.server_root/'native-start.json').open('x',encoding='utf-8') as out:json.dump(asdict(row),out,indent=2)
        return row

    def observations(self):return json.loads((self.server_root/'observations.json').read_text(encoding='utf-8'))

    def stop_owned_service(self):
        if not self.dispatched:return
        # Only this fresh, explicitly created random acceptance unit is eligible.
        details=subprocess.run(['systemctl','--user','show',self.unit,'--property=LoadState,ExecStart'],capture_output=True,timeout=8)
        if b'LoadState=loaded' in details.stdout:
            self.assertIn(str(scripts/'native/serve_retirement_server.py').encode(),details.stdout)
            self.assertIn(str(self.server_root).encode(),details.stdout)
            stopped=subprocess.run(['systemctl','--user','stop',self.unit],capture_output=True,timeout=15);self.assertEqual(stopped.returncode,0)
        status=subprocess.run(['systemctl','--user','show',self.unit,'--property=LoadState,MainPID,ActiveState,ControlGroup,NRestarts'],capture_output=True,timeout=8)
        self.assertIn(b'MainPID=0',status.stdout);self.assertIn(b'ActiveState=inactive',status.stdout)
        (self.server_root/'cleanup-unit.log').write_bytes(status.stdout+status.stderr)
        properties=dict(line.split('=',1) for line in status.stdout.decode().splitlines() if '=' in line)
        self.assertEqual('0',properties['NRestarts']);self.assertEqual('',properties['ControlGroup'])
        with (self.server_root/'cleanup-unit.json').open('w',encoding='utf-8') as out:json.dump(dict(unit=self.unit,observed=properties,case=self.id()),out,indent=2)
        self.dispatched=False

    def transport(self,before):
        client=socket.create_connection(('127.0.0.1',self.observations()['port']),timeout=3)
        deadline=time.monotonic()+2;observer=self.r.LinuxSocketOwner(before.owner)
        while observer(client)!=before.owner and time.monotonic()<deadline:time.sleep(0.01)
        self.assertEqual(before.owner,observer(client))
        return self.r.PinnedRetirementHttp(before.owner,client,'fixture_access',observer)

    def prepare(self,stage='serve_retirement',mode='normal',absent=False):
        before=self.start(mode)
        if absent:
            self.stop_owned_service();before=self.manager.snapshot('serve')
        inventory=lambda root,names:self.r.SourceInventory(self.core['native_file_publication'].anchor(root),tuple((n,hashlib.sha256((root/n).read_bytes()).hexdigest()) for n in sorted(names)))
        self.spec=self.r.ServeRetirementSpec(inventory(self.paths['old_source'],self.r.SDK_REQUIRED),inventory(self.paths['executor'],self.r.EXECUTOR_REQUIRED),
            self.core['native_file_publication'].anchor(self.paths['hermes_home']),before,0 if absent else self.observations()['port'],self.scope.configuration_sha256,'old_source',str(self.root))
        recovery=stage.startswith('rollback_');packet='8'*64 if recovery else self.plan.packet_sha256
        self.execution=self.c.ApprovedExecution(self.operation,packet,self.plan.authority_sha256,'recovery' if recovery else 'forward','f'*64,
            (self.scope.active_enter_usec+self.scope.runtime_max_usec+self.scope.stop_timeout_usec)/1e6+5)
        if recovery:
            original=uuid.uuid4().hex;self.j.begin(original,self.plan.packet_sha256,authorization_sha256='9'*64);self.j.intent('preflight');self.j.unknown()
            self.j.begin_recovery(self.operation,packet,original_operation=original,original_plan_sha256=self.plan.packet_sha256,history_sha256=self.j.history_sha256,evidence_sha256='7'*64,authorization_sha256=self.execution.binding_sha256)
        else:self.j.begin(self.operation,packet,authorization_sha256=self.execution.binding_sha256)
        order=self.c.RECOVERY if recovery else self.c.FORWARD
        for prior in order[:order.index(stage)]:self.j.intent(prior);self.j.verified(prior,'6'*64) # Explicit synthetic non-retirement prerequisites.
        self.j.intent(stage);self.stage=stage;self.adapter=None if absent else self.transport(before)
        self.actor=self.r.NativeServeRetirement(self.plan,self.j,self.manager,self.guard,stage,self.spec,self.spec.sha256,self.adapter)
        return self.actor,self.execution

    def test_forward_commit_is_observed_without_verifying_or_stopping_its_journal_stage(self):
        actor,e=self.prepare();result=actor.run(e)
        self.assertEqual('effects_observed',result['state']);self.assertEqual({'serve_admission_closed':True},result['proof']['checks']);self.assertEqual(4,result['proof']['protocol_requests'])
        self.assertTrue(self.observations()['committed']);self.assertEqual('serve_retirement',self.j.view['pending']);self.assertEqual(self.spec.before,self.manager.snapshot('serve'))
        receipt=(self.paths['journal']/result['receipt']).read_text();self.assertNotIn('fixture_access',receipt);self.assertNotIn('fixture_permit_',receipt)
        self.assertEqual(hashlib.sha256(receipt.encode()).hexdigest(),result['receipt_sha256']);self.assertFalse(result['proof']['service_action'])
    def test_recovery_uses_its_exact_distinct_authorization_and_flag(self):
        actor,e=self.prepare('rollback_serve_retirement');result=actor.run(e);self.assertEqual({'serve_fenced_or_absent':True},result['proof']['checks']);self.assertTrue(self.observations()['committed']);self.assertEqual('recovery',self.j.view['mode'])
    def test_recovery_absence_is_native_and_sends_no_request(self):
        actor,e=self.prepare('rollback_serve_retirement',absent=True);result=actor.run(e);self.assertEqual('verified_absent',result['outcome']);self.assertEqual(0,result['proof']['protocol_requests']);self.assertEqual([],self.observations()['requests'])
    def test_forward_absence_cannot_claim_running_service_retirement(self):
        actor,e=self.prepare(absent=True)
        with self.assertRaises(self.errors.Refused):actor.run(e)
        self.assertFalse(actor.used);self.assertEqual([],self.observations()['requests'])
    def test_actor_and_replacement_cannot_repeat_any_http_effect(self):
        actor,e=self.prepare();actor.run(e);count=len(self.observations()['requests'])
        with self.assertRaises(self.errors.Refused):actor.run(e)
        other=self.transport(self.spec.before);replacement=self.r.NativeServeRetirement(self.plan,self.j,self.manager,self.guard,self.stage,self.spec,self.spec.sha256,other)
        with self.assertRaises(self.errors.Refused):replacement.run(e)
        self.assertEqual(count,len(self.observations()['requests']));self.assertEqual(1,sum(r['action']=='commit' for r in self.observations()['requests']))
    def test_busy_diagnostic_is_held_without_mutation_or_automatic_retry(self):
        actor,e=self.prepare(mode='busy');result=actor.run(e);self.assertEqual('held',result['state']);self.assertEqual({'serve_admission_closed':False},result['proof']['checks']);self.assertEqual(2,len(self.observations()['requests']));self.assertFalse(self.observations()['prepared'])
        with self.assertRaises(self.errors.Refused):actor.run(e)
    def test_busy_prepare_stays_held_without_commit_or_cancel(self):
        actor,e=self.prepare(mode='prepare-busy');result=actor.run(e);self.assertEqual('held',result['state']);self.assertEqual(3,len(self.observations()['requests']));self.assertFalse(self.observations()['committed'])
    def test_lost_commit_response_is_unknown_and_never_replayed(self):
        actor,e=self.prepare(mode='drop-commit')
        with self.assertRaises(self.errors.Unknown):actor.run(e)
        self.assertEqual('unknown',actor.state);self.assertTrue(self.observations()['committed']);self.assertEqual(4,len(self.observations()['requests']))
        with self.assertRaises(self.errors.Refused):actor.run(e)
        self.assertEqual(4,len(self.observations()['requests']))
    def test_invalid_prepare_permit_is_unknown_without_commit(self):
        actor,e=self.prepare(mode='invalid-permit')
        with self.assertRaises(self.errors.Unknown):actor.run(e)
        self.assertFalse(self.observations()['committed']);self.assertEqual(3,len(self.observations()['requests']))
    def test_wrong_authenticated_role_is_unknown_without_prepare(self):
        actor,e=self.prepare(mode='wrong-role')
        with self.assertRaises(self.errors.Unknown):actor.run(e)
        self.assertEqual(1,len(self.observations()['requests']));self.assertFalse(self.observations()['prepared'])
    def test_changed_sdk_source_refuses_before_a_claim_or_http(self):
        actor,e=self.prepare();p=self.paths['old_source']/next(iter(self.r.SDK_REQUIRED));p.write_bytes(p.read_bytes()+b'# unreviewed\n')
        with self.assertRaises(self.errors.Refused):actor.run(e)
        self.assertFalse(actor.used);self.assertIsNone(actor.last_receipt);self.assertEqual([],self.observations()['requests'])
    def test_endpoint_and_controller_mismatch_refuse_before_http(self):
        actor,e=self.prepare();spec=replace(self.spec,port=1 if self.spec.port!=1 else 2)
        changed=self.r.NativeServeRetirement(self.plan,self.j,self.manager,self.guard,self.stage,spec,spec.sha256,self.adapter)
        with self.assertRaises(self.errors.Refused):changed.run(e)
        self.assertFalse(changed.used);self.assertEqual([],self.observations()['requests'])
        other=self.transport(self.spec.before);spec=replace(self.spec,scope_configuration_sha256='9'*64)
        changed=self.r.NativeServeRetirement(self.plan,self.j,self.manager,self.guard,self.stage,spec,spec.sha256,other)
        with self.assertRaises(self.errors.Refused):changed.run(e)
        self.assertFalse(changed.used);self.assertEqual([],self.observations()['requests'])
    def test_lost_native_service_owner_refuses_before_a_claim(self):
        actor,e=self.prepare();self.stop_owned_service()
        with self.assertRaises(self.errors.Unknown):actor.run(e)
        self.assertFalse(actor.used);self.assertEqual([],self.observations()['requests'])
    def test_foreign_authorization_scope_and_expired_deadline_send_no_http(self):
        actor,e=self.prepare()
        cases=(replace(e,approval_receipt_sha256='0'*64),replace(e,scope='recovery'),replace(e,deadline=time.monotonic()-1))
        for index,bad in enumerate(cases):
            adapter=self.adapter if index==0 else self.transport(self.spec.before)
            candidate=self.r.NativeServeRetirement(self.plan,self.j,self.manager,self.guard,self.stage,self.spec,self.spec.sha256,adapter)
            with self.subTest(index=index),self.assertRaises((self.errors.Refused,self.errors.Unknown)):candidate.run(bad)
            self.assertFalse(candidate.used);self.assertEqual([],self.observations()['requests'])

    def test_owner_loss_after_commit_response_stays_unknown(self):
        actor,e=self.prepare();original=self.r.PinnedRetirementHttp.__call__
        def request(adapter,method,path,body):
            result=original(adapter,method,path,body)
            if method=='POST' and body.get('action')=='commit':self.stop_owned_service()
            return result
        with patch.object(self.r.PinnedRetirementHttp,'__call__',request),self.assertRaises(self.errors.Unknown):actor.run(e)
        self.assertEqual('unknown',actor.state);self.assertTrue(self.observations()['committed']);self.assertEqual(4,len(self.observations()['requests']))
        with self.assertRaises(self.errors.Refused):actor.run(e)

    def test_existing_receipt_is_never_adopted_or_overwritten(self):
        actor,e=self.prepare();p=self.paths['journal']/('native-files-'+e.operation+'-'+self.stage+'.jsonl');p.write_bytes(b'preserve earlier outcome\n')
        with self.assertRaises(self.errors.Unknown):actor.run(e)
        self.assertTrue(actor.used);self.assertEqual(b'preserve earlier outcome\n',p.read_bytes());self.assertEqual([],self.observations()['requests'])
    def test_large_inventory_fully_verifies_with_a_bounded_identity_digest_receipt(self):
        actor,e=self.prepare();root=self.paths['old_source']
        for n in range(1000):
            p=root/('reviewed-extra-module-'+str(n).zfill(4)+'-'+'x'*40+'.py');p.write_bytes(b'# Reviewed additional code.\n')
        rows=tuple(sorted((p.relative_to(root).as_posix(),hashlib.sha256(p.read_bytes()).hexdigest()) for p in root.rglob('*.py')))
        inventory=self.r.SourceInventory(self.core['native_file_publication'].anchor(root),rows);observed=inventory.observe()
        self.assertGreater(len(json.dumps(observed).encode()),65536)
        spec=replace(self.spec,sdk=inventory)
        actor=self.r.NativeServeRetirement(self.plan,self.j,self.manager,self.guard,self.stage,spec,spec.sha256,self.adapter)
        result=actor.run(e);self.assertEqual('effects_observed',result['state']);self.assertTrue(self.observations()['committed'])
        raw=(self.paths['journal']/result['receipt']).read_bytes();self.assertLess(len(raw),65536)
        receipt=[json.loads(line) for line in raw.splitlines()];witness=receipt[1]['evidence']['inputs']['sdk']
        self.assertEqual({'inventory_sha256':inventory.sha256,'files':len(rows),'identities_sha256':self.core['native_file_publication'].digest(observed)},{k:witness[k] for k in ('inventory_sha256','files','identities_sha256')})
        self.assertGreater(witness['directories'],1);self.assertRegex(witness['directories_sha256'],r'^[a-f0-9]{64}$')
        self.assertEqual('serve_retirement',self.j.view['pending']);self.assertEqual(4,len(self.observations()['requests']))

    def test_same_content_native_identity_change_after_commit_stays_unknown(self):
        actor,e=self.prepare();original=self.r.PinnedRetirementHttp.__call__;p=self.paths['old_source']/sorted(self.r.SDK_REQUIRED)[0];before=p.stat()
        def request(adapter,method,path,body):
            result=original(adapter,method,path,body)
            if method=='POST' and body.get('action')=='commit':
                p.write_bytes(p.read_bytes());self.assertNotEqual(before.st_ctime_ns,p.stat().st_ctime_ns)
            return result
        with patch.object(self.r.PinnedRetirementHttp,'__call__',request),self.assertRaises(self.errors.Unknown):actor.run(e)
        self.assertTrue(self.observations()['committed']);self.assertEqual('unknown',actor.state);self.assertEqual(4,len(self.observations()['requests']))
        with self.assertRaises(self.errors.Refused):actor.run(e)

    def deferred(self):
        _,e=self.prepare();self.adapter.close()
        actor=self.r.NativeServeRetirement(self.plan,self.j,self.manager,self.guard,self.stage,self.spec,self.spec.sha256,None)
        return actor,e

    def test_content_preparation_before_socket_claims_nothing_then_runs_one_exchange(self):
        actor,e=self.deferred();inputs=actor.prepare_inputs(e)
        self.assertEqual('inputs_prepared',actor.state);self.assertFalse(actor.used);self.assertEqual([],self.observations()['requests']);self.assertNotIn((e.operation,self.stage),self.j._dispatch_claims)
        self.assertEqual(len(self.r.SDK_REQUIRED),inputs['sdk']['files'])
        adapter=self.transport(self.spec.before);actor.bind_transport(e,adapter);result=actor.run(e)
        self.assertEqual('effects_observed',result['state']);self.assertEqual(4,len(self.observations()['requests']))

    def test_prepared_content_rejects_identical_byte_rewrite_before_dispatch(self):
        actor,e=self.deferred();actor.prepare_inputs(e);p=self.paths['old_source']/sorted(self.r.SDK_REQUIRED)[0];p.write_bytes(p.read_bytes())
        adapter=self.transport(self.spec.before)
        actor.bind_transport(e,adapter)
        with self.assertRaises(self.errors.Unknown):actor.run(e)
        self.assertFalse(adapter._usable);self.assertFalse(actor.used);self.assertEqual([],self.observations()['requests'])

    def test_prepared_directory_symlink_cannot_redirect_unchanged_files(self):
        actor,e=self.deferred();actor.prepare_inputs(e);root=self.paths['old_source'];folder=root/'hermes_cli';saved=root/'retained-original-folder';folder.rename(saved);folder.symlink_to(saved,target_is_directory=True)
        adapter=self.transport(self.spec.before)
        actor.bind_transport(e,adapter)
        with self.assertRaises(self.errors.Unknown):actor.run(e)
        self.assertFalse(adapter._usable);self.assertFalse(actor.used);self.assertEqual([],self.observations()['requests'])

    def test_prepared_executor_identity_drift_refuses_before_http(self):
        actor,e=self.deferred();actor.prepare_inputs(e);p=self.paths['executor']/'native_verified_inventory.py';p.write_bytes(p.read_bytes())
        adapter=self.transport(self.spec.before)
        actor.bind_transport(e,adapter)
        with self.assertRaises(self.errors.Unknown):actor.run(e)
        self.assertFalse(adapter._usable);self.assertFalse(actor.used);self.assertEqual([],self.observations()['requests'])

    def test_prepared_authorization_changes_and_deadline_refuse_transport(self):
        actor,e=self.deferred();actor.prepare_inputs(e)
        for bad in (replace(e,approval_receipt_sha256='0'*64),replace(e,scope='recovery'),replace(e,deadline=time.monotonic()-1)):
            adapter=self.transport(self.spec.before)
            with self.assertRaises((self.errors.Refused,self.errors.Unknown)):actor.bind_transport(bad,adapter)
            self.assertFalse(adapter._usable)
        self.assertFalse(actor.used);self.assertEqual([],self.observations()['requests'])

    def test_prepared_inputs_cannot_outlive_their_native_serve_owner(self):
        actor,e=self.deferred();actor.prepare_inputs(e);adapter=self.transport(self.spec.before);self.stop_owned_service()
        with self.assertRaises(self.errors.Unknown):actor.bind_transport(e,adapter)
        self.assertFalse(adapter._usable);self.assertFalse(actor.used);self.assertEqual([],self.observations()['requests'])

    def test_replacement_cannot_adopt_another_actors_prepared_witness(self):
        actor,e=self.deferred();actor.prepare_inputs(e)
        other=self.r.NativeServeRetirement(self.plan,self.j,self.manager,self.guard,self.stage,self.spec,self.spec.sha256,None);other._prepared=actor._prepared
        adapter=self.transport(self.spec.before)
        with self.assertRaises(self.errors.Refused):other.bind_transport(e,adapter)
        self.assertFalse(adapter._usable);self.assertFalse(other.used);self.assertEqual([],self.observations()['requests'])

    def test_repeated_preparation_and_binding_cannot_replace_the_owned_socket(self):
        actor,e=self.deferred();actor.prepare_inputs(e)
        with self.assertRaises(self.errors.Refused):actor.prepare_inputs(e)
        first=self.transport(self.spec.before);actor.bind_transport(e,first);other=self.transport(self.spec.before)
        with self.assertRaises(self.errors.Refused):actor.bind_transport(e,other)
        self.assertFalse(other._usable);self.assertIs(first,actor.transport);self.assertTrue(first._usable)
        actor.run(e);self.assertEqual(4,len(self.observations()['requests']))

    def test_synthetic_socket_observer_cannot_substitute_for_native_proof(self):
        actor,e=self.prepare();self.adapter._observe=lambda sock:self.spec.before.owner
        with self.assertRaises(self.errors.Refused):actor.run(e)
        self.assertFalse(actor.used);self.assertEqual([],self.observations()['requests'])


if __name__=='__main__':unittest.main()
