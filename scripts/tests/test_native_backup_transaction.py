from dataclasses import asdict,replace
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import sqlite3
import stat
import subprocess
import sys
import unittest
import uuid
from unittest.mock import patch
import zipfile

scripts=Path(__file__).resolve().parents[1];sys.path.insert(0,str(scripts))
# Discovery also loads older component generations. Bind these two modules to
# the same current native class identities before constructing exact schemas.
for name in ('backup_sdk_snapshot','native_backup_transaction'):
    module_spec=importlib.util.spec_from_file_location(name,scripts/(name+'.py'));module=importlib.util.module_from_spec(module_spec);sys.modules[name]=module;module_spec.loader.exec_module(module)
s=sys.modules['backup_sdk_snapshot'];t=sys.modules['native_backup_transaction']

class BackupTransactionSchemaTests(unittest.TestCase):
    def spec(self):
        root=lambda name:s.DirectoryAnchor('/fixture/'+name,1,abs(hash(name))%100000+1,1000,1000,0o700)
        image=lambda name,mode,size:s.NativeInput('/fixture/'+name,'a'*64,(1,2,1000,1000,stat.S_IFREG|mode,1,size,4,5))
        return t.BackupTransactionSpec(s.SourceInventory(root('sdk'),tuple((n,'a'*64) for n in sorted(s.SDK_REQUIRED))),s.SourceInventory(root('dependencies'),(('a.py','a'*64),)),
            s.SourceInventory(root('executor'),tuple((n,'a'*64) for n in sorted(t.EXECUTOR_REQUIRED))),image('python',0o755,1),image('age',0o755,1),image('identity',0o600,1),
            'age1'+'a'*58,root('home'),root('output'),t.ReleaseTarget('fixture/backups',101,202,'fixture-backups','a'*64),'b'*64,'new_source')
    def test_closed_spec_binds_native_tools_source_and_target(self):
        spec=self.spec();self.assertNotEqual(spec.sha256,replace(spec,scope_configuration_sha256='c'*64).sha256)
    def test_identity_mode_and_size_are_not_inferred_from_filename(self):
        spec=self.spec()
        for image in (replace(spec.identity,identity=(1,2,1000,1000,stat.S_IFREG|0o644,1,1,4,5)),replace(spec.identity,identity=(1,2,1000,1000,stat.S_IFREG|0o600,1,0,4,5))):
            with self.assertRaises(ValueError):replace(spec,identity=image)
    def test_program_must_be_reviewed_executable_owner_input(self):
        spec=self.spec();bad=replace(spec.age,identity=(1,2,1000,1000,stat.S_IFREG|0o644,1,1,4,5))
        with self.assertRaises(ValueError):replace(spec,age=bad)
    def test_backup_output_cannot_enter_the_home_it_archives(self):
        spec=self.spec()
        with self.assertRaises(ValueError):replace(spec,output_parent=replace(spec.output_parent,path=spec.home.path+'/output'))
    def test_arbitrary_source_role_or_missing_sdk_program_is_refused(self):
        spec=self.spec()
        with self.assertRaises(ValueError):replace(spec,source_role='remote_source')
        with self.assertRaises(ValueError):replace(spec,sdk=s.SourceInventory(spec.sdk.root,(('unrelated.py','a'*64),)))
    def test_executor_and_scope_are_explicit_not_discovered(self):
        spec=self.spec()
        with self.assertRaises(ValueError):replace(spec,executor=s.SourceInventory(spec.executor.root,(('unrelated.py','a'*64),)))
        with self.assertRaises(ValueError):replace(spec,scope_configuration_sha256='not-a-review')
        with self.assertRaises(ValueError):replace(spec,owned_root='/arbitrary/root')

@unittest.skipUnless(sys.platform=='linux','Native journal/manager, age and retained backup stage fixtures run on Pi')
class NativeBackupTransactionTests(unittest.TestCase):
    """Synthetic SDK producer + real native/age/protocol effects, never live state."""
    def setUp(self):
        for name in ('retirement_guard','native_retirement_owner'):
            spec=importlib.util.spec_from_file_location(name,scripts/(name+'.py'));module=importlib.util.module_from_spec(spec);sys.modules[name]=module;spec.loader.exec_module(module)
        spec=importlib.util.spec_from_file_location('operational_paths_fixture',scripts/'native/operational_paths_fixture.py');helper=importlib.util.module_from_spec(spec);spec.loader.exec_module(helper)
        self.fixture=helper.build(Path(os.environ['SIGNAL_GROVE_TRANSACTION_RETAIN_ROOT']));self.core=self.fixture['modules'];self.root=self.fixture['root'];self.paths=self.fixture['paths'];self.guard=self.fixture['guard']
        self.modules={}
        for name in ('native_service_manager','backup_sdk_snapshot','private_backup_release','native_backup_transaction'):
            spec=importlib.util.spec_from_file_location(name,scripts/(name+'.py'));module=importlib.util.module_from_spec(spec);sys.modules[name]=module;spec.loader.exec_module(module);self.modules[name]=module
        self.r=self.modules['native_backup_transaction'];self.s=self.modules['backup_sdk_snapshot'];self.p=self.modules['private_backup_release'];self.c=self.core['cutover_coordinator'];self.jmodule=self.core['cutover_journal'];service=self.modules['native_service_manager']
        spec=importlib.util.spec_from_file_location('publication_protocol_fixture',scripts/'tests/test_private_backup_release.py');fixture=importlib.util.module_from_spec(spec);spec.loader.exec_module(fixture)
        self.server=fixture.FixtureServer();self.addCleanup(self.server.close)
        self.operation=re.search('signal-grove-acceptance-([a-f0-9]{32})-pm.service$',Path('/proc/self/cgroup').read_text().strip().split('::',1)[-1])[1]
        self.plan=self.c.Plan('a'*64,'b'*64,'c'*64,'d'*64);ledger=self.paths['journal'];self.j=self.jmodule.CutoverJournal(ledger,expected_identity=(ledger.stat().st_dev,ledger.stat().st_ino),operational_paths=self.guard,expected_operational_sha256=self.guard.sha256);self.addCleanup(self.j.close)
        a=json.loads(os.environ['SIGNAL_GROVE_PM_BUS_AUTHORITY']);Owner=sys.modules['retirement_guard'].Owner
        authority=service.BusAuthority(a['uid'],a['busIdentity'][0],a['busIdentity'][1],Owner(**a['peerOwner']),a['managerUnique'],Owner(**a['managerOwner']))
        bindings=tuple(service.UnitBinding(role,'hermes-'+role+'.service','e'*64,'e'*64,'/fixture/not-dispatched','/fixture/not-dispatched') for role in ('gateway','serve'))
        self.manager=service.NativeServiceManager(authority,self.plan,self.j,bindings);self.addCleanup(self.manager.close);self.scope=self.manager.execution_scope(self.operation,owned_fixture=True)
        self.deadline=(self.scope.active_enter_usec+self.scope.runtime_max_usec+self.scope.stop_timeout_usec)/1e6+5
        for name in self.s.SDK_REQUIRED:
            path=self.paths['new_source']/name;path.parent.mkdir(parents=True,exist_ok=True,mode=0o700);path.write_bytes(b'pass\n')
        dependency=self.paths['backup_programs']/'dependencies';dependency.mkdir(mode=0o700);(dependency/'fixture.py').write_bytes(b'pass\n')
        for name in self.r.EXECUTOR_REQUIRED:(self.paths['executor']/name).write_bytes((scripts/name).read_bytes())
        python=self.paths['new_store']/'python-not-dispatched';python.write_bytes(b'Synthetic SDK production is separately labelled in these unit fixtures.\n');python.chmod(0o755)
        age=self.paths['backup_programs']/'age';age.write_bytes(Path(os.environ['SIGNAL_GROVE_AGE_PROGRAM']).read_bytes());age.chmod(0o755)
        self.key=self.paths['backup_identity']/'fixture.age';self.recipient=self.keypair(self.key)
        home=self.paths['hermes_home'];(home/'config.yaml').write_bytes(b'{"memory":{"provider":null}}\n');(home/'retained-state.txt').write_bytes(b'keep fixture state')
        conn=sqlite3.connect(home/'state.db');conn.execute('CREATE TABLE continuity(n INTEGER)');conn.execute('INSERT INTO continuity VALUES(42)');conn.commit();conn.close()
        self.secret=self.p.GitHubCredential('fixture_secret_'+uuid.uuid4().hex);self.transport=self.p.GitHubHttpsTransport(self.secret);self.transport._connect=self.server.connect
        def inventory(root,names):return self.s.SourceInventory(self.core['native_file_publication'].anchor(root),tuple((n,self.s.observe_input(root/n).sha256) for n in sorted(names)))
        self.spec=self.r.BackupTransactionSpec(inventory(self.paths['new_source'],self.s.SDK_REQUIRED),inventory(dependency,('fixture.py',)),inventory(self.paths['executor'],self.r.EXECUTOR_REQUIRED),
            self.s.observe_input(python),self.s.observe_input(age),self.s.observe_input(self.key),self.recipient,self.core['native_file_publication'].anchor(home),self.core['native_file_publication'].anchor(self.paths['backup_output']),
            self.p.ReleaseTarget('fixture/backups',101,202,'fixture-backups',self.secret.binding_sha256),self.scope.configuration_sha256,'new_source',str(self.root))
        self.snapshot_calls=0;self.omit_member=False;self.fail_snapshot=False
    def keypair(self,path):
        env=dict(PATH='/usr/bin:/bin',HOME=str(self.root),LANG='C.UTF-8')
        with path.open('xb') as sink:p=subprocess.run([os.environ['SIGNAL_GROVE_AGE_KEYGEN']],stdout=sink,stderr=subprocess.PIPE,env=env,timeout=15)
        self.assertEqual(p.returncode,0)
        p=subprocess.run([os.environ['SIGNAL_GROVE_AGE_KEYGEN'],'-y',str(path)],capture_output=True,env=env,timeout=15);self.assertEqual(p.returncode,0);return p.stdout.decode().strip()
    def prepare(self,stage='backup_pre'):
        recovery=stage.startswith('recovery_');e=self.c.ApprovedExecution(self.operation,'8'*64 if recovery else self.plan.packet_sha256,self.plan.authority_sha256,'recovery' if recovery else 'forward','f'*64,self.deadline)
        if recovery:
            original=uuid.uuid4().hex;self.j.begin(original,self.plan.packet_sha256,authorization_sha256='9'*64);self.j.intent('preflight');self.j.unknown()
            self.j.begin_recovery(self.operation,e.packet_sha256,original_operation=original,original_plan_sha256=self.plan.packet_sha256,history_sha256=self.j.history_sha256,evidence_sha256='7'*64,authorization_sha256=e.binding_sha256)
        else:self.j.begin(self.operation,e.packet_sha256,authorization_sha256=e.binding_sha256)
        order=self.c.RECOVERY if recovery else self.c.FORWARD
        for step in order[:order.index(stage)]:self.j.intent(step);self.j.verified(step,'6'*64) # Synthetic non-backup prerequisites only.
        self.j.intent(stage);self.stage=stage;self.execution=e
        self.actor=self.r.NativeBackupTransaction(self.plan,self.j,self.manager,self.guard,stage,self.spec,self.spec.sha256,self.transport);return self.actor,e
    def snapshot(self,actor,e,scope,receipt,directory,raw):
        self.snapshot_calls+=1
        if self.fail_snapshot:raise self.jmodule.Unknown('Synthetic SDK fixture failed after claim')
        rows=[]
        with zipfile.ZipFile(raw,'x',compression=zipfile.ZIP_DEFLATED) as z:
            for p in sorted(Path(self.spec.home.path).iterdir()):
                if p.is_dir():continue # This flat synthetic producer excludes the inert runtime scaffold; actual SDK coverage is separate.
                if p.name.endswith('.db'):row=self.r.BackupMember(p.name,'sqlite',None,None)
                else:
                    image=self.s.observe_input(p);row=self.r.BackupMember(p.name,'file',image.bytes,image.sha256)
                rows.append(row)
                if not (self.omit_member and p.name=='state.db'):z.writestr(p.name,p.read_bytes())
        members=tuple(sorted(rows,key=lambda m:m.name));return dict(selection=[asdict(r) for r in members],selection_sha256=self.r.selection_sha256(members),archive=asdict(self.s.observe_input(raw)))
    def run_actor(self,actor,e):
        with patch.object(self.r.NativeBackupTransaction,'_snapshot',autospec=True,side_effect=self.snapshot):return actor.run(e)
    def output(self):return self.paths['backup_output']/('backup-'+self.operation+'-'+self.stage)
    def assert_success(self,stage,flag):
        actor,e=self.prepare(stage);result=self.run_actor(actor,e);self.assertEqual(result['state'],'effects_observed');self.assertEqual(result['proof']['checks'],{flag:True});self.assertTrue(result['proof']['owned_scope'])
        self.assertEqual(self.snapshot_calls,1);self.assertEqual(len(self.server.posts),2);self.assertEqual(self.j.view['pending'],stage)
        self.assertFalse(list(self.output().glob('*.zip')));self.assertEqual(len(list(self.output().glob('*.zip.age'))),1)
        self.assertEqual((Path(self.spec.home.path)/'retained-state.txt').read_bytes(),b'keep fixture state')
        with self.assertRaises(self.jmodule.Refused):self.run_actor(actor,e)
        self.assertEqual(len(self.server.posts),2)
    def test_complete_owned_stage_verifies_and_removes_only_verified_plaintext(self):self.assert_success('backup_pre','fresh_backup_verified')
    def test_post_backup_uses_its_exact_stage_and_check(self):self.assert_success('backup_post','post_backup_verified')
    def test_recovery_backup_uses_separate_authorization(self):self.assert_success('recovery_backup','recovery_backup_verified')
    def test_recovery_post_backup_uses_its_exact_stage_and_check(self):self.assert_success('recovery_backup_post','post_backup_verified')
    def test_failed_snapshot_holds_without_replay_or_upload(self):
        actor,e=self.prepare();self.fail_snapshot=True
        with self.assertRaises(self.jmodule.Unknown):self.run_actor(actor,e)
        self.assertEqual(self.snapshot_calls,1);self.assertTrue(self.output().is_dir());self.assertEqual(self.server.posts,[])
        other=self.r.NativeBackupTransaction(self.plan,self.j,self.manager,self.guard,self.stage,self.spec,self.spec.sha256,self.transport)
        with self.assertRaises(self.jmodule.Refused):self.run_actor(other,e)
        self.assertEqual(self.snapshot_calls,1)
    def test_ambiguous_upload_preserves_plaintext_cipher_and_receipts_without_replay(self):
        actor,e=self.prepare();self.server.fail_post=1
        with self.assertRaises(self.jmodule.Unknown):self.run_actor(actor,e)
        self.assertEqual(len(self.server.posts),1);self.assertEqual(len(list(self.output().glob('*.zip'))),2);self.assertEqual(len(list(self.output().glob('*.zip.age'))),1)
        other=self.r.NativeBackupTransaction(self.plan,self.j,self.manager,self.guard,self.stage,self.spec,self.spec.sha256,self.transport)
        with self.assertRaises(self.jmodule.Refused):self.run_actor(other,e)
        self.assertEqual(self.snapshot_calls,1);self.assertEqual(len(self.server.posts),1)
    def test_existing_remote_asset_is_never_clobbered(self):
        actor,e=self.prepare();name='hermes-backup-'+self.operation+'-'+self.stage+'.zip.age';self.server.assets[name]=dict(id=3,name=name,body=b'preserve existing')
        with self.assertRaises(self.jmodule.Unknown):self.run_actor(actor,e)
        self.assertEqual(self.server.posts,[]);self.assertEqual(self.server.assets[name]['body'],b'preserve existing')
    def test_wrong_decryption_identity_retains_outputs_and_has_no_http(self):
        other=self.paths['backup_identity']/'wrong.age';self.keypair(other);self.spec=replace(self.spec,identity=self.s.observe_input(other));actor,e=self.prepare()
        with self.assertRaises(self.jmodule.Unknown):self.run_actor(actor,e)
        self.assertEqual(self.server.requests,[]);self.assertTrue(list(self.output().glob('*.zip.age')));self.assertTrue(list(self.output().glob('*.zip')))
    def test_incomplete_archive_refuses_before_encryption_and_network(self):
        actor,e=self.prepare();self.omit_member=True
        with self.assertRaises(self.jmodule.Unknown):self.run_actor(actor,e)
        self.assertEqual(self.server.requests,[]);self.assertFalse(list(self.output().glob('*.zip.age')))
    def test_changed_program_has_no_claimed_effect_or_output(self):
        actor,e=self.prepare();Path(self.spec.age.path).write_bytes(b'changed reviewed program')
        with self.assertRaises(self.jmodule.Refused):self.run_actor(actor,e)
        self.assertFalse(self.output().exists());self.assertEqual(self.snapshot_calls,0);self.assertEqual(self.server.requests,[])
    def test_existing_output_directory_is_not_adopted_or_deleted(self):
        actor,e=self.prepare();self.output().mkdir(mode=0o700);sentinel=self.output()/'keep';sentinel.write_bytes(b'preserve')
        with self.assertRaises(self.jmodule.Refused):self.run_actor(actor,e)
        self.assertEqual(sentinel.read_bytes(),b'preserve');self.assertEqual(self.snapshot_calls,0)
    def test_operational_ancestor_drift_stops_before_effects(self):
        actor,e=self.prepare();self.paths['backup_output'].chmod(0o755)
        with self.assertRaises(self.jmodule.Unknown):self.run_actor(actor,e)
        self.assertEqual(self.server.requests,[]);self.assertEqual(self.snapshot_calls,0)
    def test_controller_mismatch_refuses_before_snapshot(self):
        self.spec=replace(self.spec,scope_configuration_sha256='1'*64);actor,e=self.prepare()
        with self.assertRaises(self.jmodule.Refused):self.run_actor(actor,e)
        self.assertEqual(self.snapshot_calls,0);self.assertFalse(self.output().exists())
    def test_replacement_journal_ownership_cannot_dispatch(self):
        actor,e=self.prepare();self.j._owns_operation=False
        with self.assertRaises(self.jmodule.Refused):self.run_actor(actor,e)
        self.assertEqual(self.snapshot_calls,0);self.assertEqual(self.server.requests,[])

if __name__=='__main__':unittest.main()
