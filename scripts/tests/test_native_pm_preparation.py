from dataclasses import asdict,replace
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import sys
import tempfile
import time
import unittest
import uuid

for name in ('retirement_guard','native_retirement_owner','cutover_journal','cutover_coordinator','native_file_publication','native_pm_selection','native_service_manager','native_pm_preparation'):
    spec=importlib.util.spec_from_file_location(name,Path(__file__).resolve().parents[1]/(name+'.py'));m=importlib.util.module_from_spec(spec);sys.modules[name]=m;spec.loader.exec_module(m)
from retirement_guard import Owner
from cutover_journal import CutoverJournal,Refused,Unknown
from cutover_coordinator import Plan,ApprovedExecution,FORWARD
from native_file_publication import DirectoryAnchor,anchor,digest
from native_pm_selection import INPUTS,PmSelectionContext
from native_service_manager import NativeServiceManager,BusAuthority,UnitBinding
from native_pm_preparation import NativePmPreparation,PmPreparationSpec,REQUIRED_SOURCE,PROTECTED

def sample():
    files=tuple((n,'a'*64) for n in sorted(REQUIRED_SOURCE));c=PmSelectionContext('/home/fixture/source','/home/fixture/data','/home/fixture/tools',None,tuple((n,'a'*64) for n in INPUTS),None)
    return PmPreparationSpec(c,files,tuple((n,None) for n in PROTECTED),'b'*64,DirectoryAnchor(c.home,1,2,1000,1000,0o700),c.store+'/python/bin/python3','c'*64)

class PmPreparationSchemaTests(unittest.TestCase):
    def test_required_inputs_and_exact_selection_binding_cannot_be_dropped(self):
        good=sample()
        for change in (dict(source_files=good.source_files[:-1]),dict(source_files=tuple(reversed(good.source_files))),dict(selection=replace(good.selection,input_hashes=tuple((n,'d'*64) for n in INPUTS)))):
            with self.subTest(change=change),self.assertRaises(ValueError):replace(good,**change)
    def test_arbitrary_source_traversal_extras_and_configuration_values_refuse(self):
        good=sample()
        for change in (dict(source_files=tuple(sorted(good.source_files+(('../outside','a'*64),)))),dict(extras=('cloud',)),dict(protected_hashes=(('.env','secret'),('config.yaml',None)))):
            with self.subTest(change=change),self.assertRaises(ValueError):replace(good,**change)
    def test_api_python_must_be_reviewed_and_inside_the_permanent_store(self):
        for change in (dict(api_python='/usr/bin/python3'),dict(api_python='/home/fixture/tools/../other/python'),dict(api_python_sha256='missing'),dict(api_python='/home/fixture/tools/python\nother')):
            with self.subTest(change=change),self.assertRaises(ValueError):replace(sample(),**change)
    def test_private_home_native_identity_and_scope_configuration_are_bound(self):
        good=sample();self.assertNotEqual(good.sha256,replace(good,scope_configuration_sha256='d'*64).sha256)
        with self.assertRaises(ValueError):replace(good,home_anchor=replace(good.home_anchor,mode=0o755))


@unittest.skipUnless(sys.platform=='linux','Real finite user-manager unit and native PM child checks run on the Pi')
class NativePmPreparationTests(unittest.TestCase):
    def setUp(self):
        group=Path('/proc/self/cgroup').read_text().strip().split('::',1)[-1];match=re.search(r'/signal-grove-acceptance-([a-f0-9]{32})-pm\.service$',group)
        self.assertIsNotNone(match,'Run this cohort as the sole main process of its reviewed finite native maintenance unit')
        self.op=match[1];retained=Path(os.environ['SIGNAL_GROVE_PM_RETAIN_ROOT'])
        self.assertEqual(retained.resolve(),retained);self.assertEqual(anchor(retained).mode,0o700);self.assertEqual(anchor(retained).uid,os.geteuid())
        case=retained/(self._testMethodName+'-'+uuid.uuid4().hex);case.mkdir(mode=0o700)
        self.root=case/('signal-grove-pm-'+self.op);self.root.mkdir(mode=0o700)
        self.plan=Plan('a'*64,'b'*64,'c'*64,'d'*64);ledger=self.root/'ledger';ledger.mkdir(mode=0o700);self.ledger=ledger
        self.journal=CutoverJournal(ledger,expected_identity=(ledger.stat().st_dev,ledger.stat().st_ino));self.addCleanup(self.journal.close)
        data=json.loads(os.environ['SIGNAL_GROVE_PM_BUS_AUTHORITY']);authority=BusAuthority(data['uid'],data['busIdentity'][0],data['busIdentity'][1],Owner(**data['peerOwner']),data['managerUnique'],Owner(**data['managerOwner']))
        bindings=tuple(UnitBinding(role,'hermes-'+role+'.service','e'*64,'e'*64,'/fixture/python','/fixture/python') for role in ('gateway','serve'))
        self.manager=NativeServiceManager(authority,self.plan,self.journal,bindings);self.addCleanup(self.manager.close)
        self.scope=self.manager.execution_scope(self.op,owned_fixture=True);self.execution=ApprovedExecution(self.op,self.plan.packet_sha256,self.plan.authority_sha256,'forward','f'*64,
            (self.scope.active_enter_usec+self.scope.runtime_max_usec+self.scope.stop_timeout_usec)/1_000_000+5)
        self.source,self.home=self.make_source('first');self.spec=self.specification(self.source,self.home)

    def make_source(self,label,mode='normal'):
        source=self.root/(label+'-source');source.mkdir(mode=0o700);home=self.root/(label+'-data');home.mkdir(mode=0o700)
        api=str(Path(sys.executable).resolve());store=str(next(parent for parent in Path(api).parents if parent.name=='tools'))
        env='''import hashlib,json,os
from pathlib import Path
ACTIVATION_INPUTS=('uv.lock','pyproject.toml','setup.py','pm/lock.json')
def install_key(source):return hashlib.sha256(str(source).encode()).hexdigest()[:16]
def runtime_facts_path(source):return Path(os.environ['HERMES_HOME'])/'installs'/install_key(source)/'facts.json'
def store_root(source,*,honor_runtime_override):return Path(STORE)
def owning_home_root(source):return None
def committed_venv(source):return Path(json.loads(runtime_facts_path(source).read_text())['packages']['venv']['environment'])
def project_python(source):return committed_venv(source)/'bin/python'
def activation_inputs_dir(source):return runtime_facts_path(source).parent/'inputs'
'''.replace('STORE',repr(store))
        client='''import json,os,sys
from pathlib import Path
from pm import environments as e
MODE=BEHAVIOR
def sync_venv(extras,*,explicit,project_root,repair,evict_incompatible_plugins):
 assert explicit is True and repair is False and evict_incompatible_plugins is False
 home=Path(os.environ['HERMES_HOME']);count=home/'sync-count';count.write_text(str(int(count.read_text())+1 if count.exists() else 1))
 if MODE=='error':raise RuntimeError('fixture failure')
 if MODE=='no-facts':return
 base=e.runtime_facts_path(project_root);env=base.parent/'environments/fixture-generation';(env/'bin').mkdir(parents=True,mode=0o700)
 (env/'pyvenv.cfg').write_text('fixture only');(env/'bin/python').write_bytes(b'not executed')
 base.write_text(json.dumps({'packages':{'venv':{'environment':str(env)}}}));base.chmod(0o600)
 if MODE=='config-change':(home/'config.yaml').write_text('preserve this unexpected change')
 if MODE=='overflow':sys.stdout.write('x'*(4*1024*1024+1));sys.stdout.flush()
def venv_is_current(*,project_root):return MODE!='not-current'
'''.replace('BEHAVIOR',repr(mode))
        cli='''import os
from pathlib import Path
from pm import client,environments as e
def main(argv):
 assert argv==['install','--extra','agentic-contextgraph','--extra','all','--extra','discord']
 home=Path(os.environ['HERMES_HOME']);(home/'install-count').write_text('1')
 source=Path(__file__).resolve().parents[1]
 client.sync_venv(['agentic-contextgraph','all','discord'],explicit=True,project_root=source,repair=False,evict_incompatible_plugins=False)
 if client.MODE=='missing-stamps':return 0
 stamps=e.activation_inputs_dir(source);stamps.mkdir(mode=0o700);(stamps/'.project-root').write_text(str(source))
 for n in e.ACTIVATION_INPUTS:
  p=stamps/n;p.parent.mkdir(parents=True,exist_ok=True);p.touch();mtime=(source/n).stat().st_mtime_ns;os.utime(p,ns=(mtime,mtime))
 return 0
'''
        for name in REQUIRED_SOURCE:
            p=source/name;p.parent.mkdir(parents=True,exist_ok=True,mode=0o700);p.write_text('{}' if name.endswith('.json') else '# fixture source\n');p.chmod(0o600)
        (source/'pm/__init__.py').write_text('from .client import sync_venv,venv_is_current\n');(source/'pm/environments.py').write_text(env);(source/'pm/client.py').write_text(client)
        (source/'pm/cli.py').write_text(cli);(source/'pm/runtime.py').write_text('def run_cli(argv):\n from pm.cli import main\n return main(argv)\n')
        return source,home

    def specification(self,source,home):
        files=tuple(sorted((p.relative_to(source).as_posix(),hashlib.sha256(p.read_bytes()).hexdigest()) for p in source.rglob('*') if p.is_file()))
        api=str(Path(sys.executable).resolve());store=str(next(parent for parent in Path(api).parents if parent.name=='tools'))
        c=PmSelectionContext(str(source),str(home),store,None,tuple((n,dict(files)[n]) for n in INPUTS),None)
        return PmPreparationSpec(c,files,tuple((n,None) for n in PROTECTED),self.scope.configuration_sha256,anchor(home),api,hashlib.sha256(Path(api).read_bytes()).hexdigest(),str(self.root))

    def begin(self,stage='pm_new',spec=None,execution=None):
        execution=execution or self.execution;self.journal.begin(execution.operation,execution.packet_sha256,authorization_sha256=execution.binding_sha256)
        for prior in FORWARD[:FORWARD.index(stage)]:self.journal.intent(prior);self.journal.verified(prior,'c'*64) # Explicit synthetic non-PM prerequisites.
        self.journal.intent(stage);spec=spec or self.spec
        return NativePmPreparation(self.plan,self.journal,self.manager,stage,spec,spec.sha256),execution

    def test_real_owned_child_commits_observed_state_but_does_not_verify_its_journal_step(self):
        actor,e=self.begin();result=actor.run(e)
        self.assertEqual(result['state'],'effects_observed');self.assertTrue(result['supported_currency_verified']);self.assertEqual((self.home/'sync-count').read_text(),'1')
        self.assertTrue(result['activation_inputs_verified']);self.assertTrue(result['supported_install_verified']);self.assertEqual((self.home/'install-count').read_text(),'1')
        self.assertEqual(self.journal.view['pending'],'pm_new');self.assertEqual(actor.child_process.returncode,0);self.assertNotIn('pm',sys.modules)
        self.assertEqual(hashlib.sha256((self.ledger/result['receipt']).read_bytes()).hexdigest(),result['receipt_sha256'])
    def test_actor_and_replacement_cannot_replay_the_claimed_pending_step(self):
        actor,e=self.begin();actor.run(e)
        with self.assertRaises(Refused):actor.run(e)
        current=replace(self.spec,selection=replace(self.spec.selection,expected_facts_sha256=hashlib.sha256(Path(self.spec.selection.facts).read_bytes()).hexdigest()))
        replacement=NativePmPreparation(self.plan,self.journal,self.manager,'pm_new',current,current.sha256)
        with self.assertRaises(Refused):replacement.run(e)
        self.assertEqual((self.home/'sync-count').read_text(),'1')
    def test_changed_source_refuses_before_dispatch_or_cache_creation(self):
        actor,e=self.begin();(self.source/'setup.py').write_text('changed')
        with self.assertRaises(Refused):actor.run(e)
        self.assertFalse(actor.used);self.assertFalse((self.home/'cache').exists());self.assertEqual(list(self.ledger.glob('native-files-*')),[])
    def test_changed_protected_config_is_never_overwritten(self):
        actor,e=self.begin();(self.home/'config.yaml').write_text('owner configuration')
        with self.assertRaises(Refused):actor.run(e)
        self.assertFalse(actor.used);self.assertEqual((self.home/'config.yaml').read_text(),'owner configuration')
    def test_initial_facts_arrival_is_not_adopted(self):
        actor,e=self.begin();facts=Path(self.spec.selection.facts);facts.parent.mkdir(parents=True,mode=0o700);facts.write_text('{}')
        with self.assertRaises(Refused):actor.run(e)
        self.assertFalse(actor.used);self.assertEqual(facts.read_text(),'{}')
    def test_unapproved_scope_command_or_api_binary_has_no_pm_dispatch(self):
        original,e=self.begin(spec=replace(self.spec,scope_configuration_sha256='9'*64))
        for spec in (replace(self.spec,scope_configuration_sha256='9'*64),replace(self.spec,api_python_sha256='9'*64)):
            # A fresh journal per specification is required; exercise constructor/
            # pending context without creating another operation.
            actor=NativePmPreparation(self.plan,self.journal,self.manager,'pm_new',spec,spec.sha256)
            with self.assertRaises(Refused):actor.run(e)
            self.assertFalse(actor.used);self.assertFalse((self.home/'sync-count').exists())
    def test_sdk_failure_preserves_claim_receipt_and_original_unknown_state(self):
        source,home=self.make_source('failure','error');spec=self.specification(source,home);actor,e=self.begin(spec=spec)
        with self.assertRaises(Unknown):actor.run(e)
        self.assertTrue(actor.used);self.assertEqual(actor.state,'unknown');self.assertEqual((home/'sync-count').read_text(),'1');self.assertTrue((self.ledger/actor.last_receipt).exists())
        report=json.loads(next(self.ledger.glob('native-pm-report-*')).read_text());self.assertEqual(report['checkpoint'],'install');self.assertEqual(report['error_type'],'RuntimeError')
        self.assertNotIn('fixture failure',json.dumps(report))
        with self.assertRaises(Refused):actor.run(e)
    def test_successful_dependency_sync_without_activation_stamps_remains_held_and_cannot_replay(self):
        source,home=self.make_source('unstamped','missing-stamps');spec=self.specification(source,home);actor,e=self.begin(spec=spec)
        with self.assertRaises(Unknown):actor.run(e)
        self.assertEqual(actor.child_process.returncode,0);self.assertTrue(Path(spec.selection.facts).exists());self.assertFalse((home/'installs'/spec.selection.install_key/'inputs').exists())
        report=json.loads(next(self.ledger.glob('native-pm-report-*')).read_text());self.assertEqual(report['state'],'SDK_PM_INSTALL_AND_CURRENCY_PASS')
        rows=[json.loads(line) for line in (self.ledger/actor.last_receipt).read_text().splitlines()]
        self.assertEqual(rows[-1]['phase'],'pm_observation_held');self.assertEqual(rows[-1]['evidence']['checkpoint'],'activation')
        current=replace(spec,selection=replace(spec.selection,expected_facts_sha256=hashlib.sha256(Path(spec.selection.facts).read_bytes()).hexdigest()))
        replacement=NativePmPreparation(self.plan,self.journal,self.manager,'pm_new',current,current.sha256)
        with self.assertRaises(Refused):replacement.run(e)
        self.assertEqual((home/'sync-count').read_text(),'1');self.assertEqual((home/'install-count').read_text(),'1')
    def test_sync_ack_without_facts_or_currency_never_passes(self):
        source,home=self.make_source('currency','not-current');actor,e=self.begin(spec=self.specification(source,home))
        with self.assertRaises(Unknown):actor.run(e)
        self.assertTrue(Path(actor.spec.selection.facts).exists());self.assertEqual(self.journal.view['pending'],'pm_new')
    def test_ack_without_a_committed_generation_stays_unknown(self):
        source,home=self.make_source('empty','no-facts');actor,e=self.begin(spec=self.specification(source,home))
        with self.assertRaises(Unknown):actor.run(e)
        self.assertTrue(actor.used);self.assertFalse(Path(actor.spec.selection.facts).exists())
    def test_unexpected_sdk_config_write_is_preserved_without_automatic_restore(self):
        source,home=self.make_source('config','config-change');actor,e=self.begin(spec=self.specification(source,home))
        with self.assertRaises(Unknown):actor.run(e)
        self.assertEqual((home/'config.yaml').read_text(),'preserve this unexpected change');self.assertTrue(Path(actor.spec.selection.facts).exists())
    def test_output_overflow_is_explicit_unknown_with_bounded_prefix_retained(self):
        source,home=self.make_source('logs','overflow');actor,e=self.begin(spec=self.specification(source,home))
        with self.assertRaises(Unknown):actor.run(e)
        logs=list(self.ledger.glob('native-pm-output-*.stdout'));self.assertEqual(len(logs),1);self.assertEqual(logs[0].stat().st_size,4*1024*1024)
    def test_both_pm_stages_use_fresh_sdk_children_without_parent_module_contamination(self):
        first,e=self.begin('pm_old');one=first.run(e);self.journal.verified('pm_old',digest(one))
        source,home=self.make_source('second');spec=self.specification(source,home);self.journal.intent('pm_new')
        second=NativePmPreparation(self.plan,self.journal,self.manager,'pm_new',spec,spec.sha256);two=second.run(e)
        self.assertNotEqual(first.last_child_owner,second.last_child_owner);self.assertNotIn('pm',sys.modules)
        self.assertEqual((self.home/'sync-count').read_text(),'1');self.assertEqual((home/'sync-count').read_text(),'1');self.assertEqual(two['state'],'effects_observed')

if __name__=='__main__':unittest.main()
