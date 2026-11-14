from dataclasses import replace
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

FILE=Path(__file__).resolve().parents[1]/'native_pm_selection.py'
spec=importlib.util.spec_from_file_location('native_pm_selection',FILE);selection=importlib.util.module_from_spec(spec);sys.modules[spec.name]=selection;spec.loader.exec_module(selection)
from native_pm_selection import INPUTS,PmSelectionContext

HASHES=tuple((n,'a'*64) for n in INPUTS)


class PmSelectionSchemaTests(unittest.TestCase):
    def context(self):return PmSelectionContext('/home/fixture/source','/home/fixture/data','/home/fixture/tools',None,HASHES,None)
    def test_active_home_changes_facts_scope_without_changing_source_install_identity(self):
        a=self.context();b=replace(a,home='/home/fixture/other-data')
        self.assertEqual(a.install_key,b.install_key);self.assertNotEqual(a.facts,b.facts);self.assertNotEqual(a.sha256,b.sha256)
    def test_only_the_seven_fixed_reviewed_inputs_are_accepted(self):
        good=self.context()
        for hashes in (HASHES[:-1],HASHES+(('.env','a'*64),),tuple(reversed(HASHES)),list(HASHES)):
            with self.subTest(hashes=hashes),self.assertRaises(ValueError):replace(good,input_hashes=hashes)
    def test_observer_paths_refuse_traversal_shell_values_and_boolean_claims(self):
        for change in (dict(source='/home/../outside'),dict(home='relative'),dict(store='/home/$(command)'),dict(owner_home=True)):
            with self.subTest(change=change),self.assertRaises(ValueError):replace(self.context(),**change)
    def test_expected_absence_and_exact_facts_content_are_distinct_reviewed_contexts(self):
        a=self.context();self.assertNotEqual(a.sha256,replace(a,expected_facts_sha256='b'*64).sha256)
        for bad in ('missing','B'*64,True):
            with self.subTest(bad=bad),self.assertRaises(ValueError):replace(a,expected_facts_sha256=bad)


@unittest.skipUnless(sys.platform=='linux','Fresh no-site native SDK selection observers run on the Pi')
class NativePmSelectionTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(prefix='signal-grove-selection-');self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name).resolve()
        self.source=self.root/'source';self.source.mkdir(mode=0o700);self.home=self.root/'data';self.home.mkdir(mode=0o700);self.store=self.root/'tools';self.store.mkdir(mode=0o700)
        self.key=hashlib.sha256(str(self.source).encode()).hexdigest()[:16];self.environment=self.home/'installs'/self.key/'environments/reviewed';self.environment.mkdir(parents=True,mode=0o700)
        (self.environment/'pyvenv.cfg').write_text('fixture only\n');(self.environment/'bin').mkdir(mode=0o700);(self.environment/'bin/python').write_bytes(b'not executed')
        self.facts=self.environment.parent.parent/'facts.json';self.facts.write_text(json.dumps(dict(packages=dict(venv=dict(environment=str(self.environment))))));self.facts.chmod(0o600)
        for name in INPUTS:
            p=self.source/name;p.parent.mkdir(parents=True,exist_ok=True,mode=0o700);p.write_text('{}' if name=='install-stamp.json' else '',encoding='utf-8');p.chmod(0o600)
        self.body='''import hashlib,json,os
from pathlib import Path
def install_key(source):return hashlib.sha256(str(source).encode()).hexdigest()[:16]
def runtime_facts_path(source):return Path(os.environ['HERMES_HOME'])/'installs'/install_key(source)/'facts.json'
def owning_home_root(source):return None
def store_root(source,*,honor_runtime_override):return Path(STORE)
def committed_venv(source):
 p=runtime_facts_path(source)
 return Path(json.loads(p.read_text())['packages']['venv']['environment']) if p.is_file() else None
def project_python(source):return (committed_venv(source) or source/'venv')/'bin/python'
'''.replace('STORE',repr(str(self.store)))
        # Nonempty pinned stub modules are deliberate boundary fixtures, not Hermes acceptance.
        for name in INPUTS:
            if name!='install-stamp.json':(self.source/name).write_text(self.body if name=='pm/environments.py' else '# bounded source fixture\n',encoding='utf-8')

    def context(self):
        hashes=tuple((n,hashlib.sha256((self.source/n).read_bytes()).hexdigest()) for n in INPUTS)
        facts=hashlib.sha256(self.facts.read_bytes()).hexdigest() if self.facts.exists() else None
        return PmSelectionContext(str(self.source),str(self.home),str(self.store),None,hashes,facts)

    def child(self,context=None,*,preamble='',env_changes=None,wait=False):
        context=context or self.context()
        code='''import importlib.util,json,sys
spec=importlib.util.spec_from_file_location('selection_probe',FILE);m=importlib.util.module_from_spec(spec);sys.modules[spec.name]=m;spec.loader.exec_module(m)
PREAMBLE
c=m.PmSelectionContext(*ARGS)
try:print(json.dumps(m.observe_selection(c,c.sha256)),flush=True)
except m.SelectionRefused:print(json.dumps(dict(refused=True)),flush=True);raise SystemExit(2)
'''.replace('FILE',repr(str(FILE))).replace('PREAMBLE',preamble).replace('ARGS',repr((context.source,context.home,context.store,context.owner_home,context.input_hashes,context.expected_facts_sha256)))
        env=dict(PATH='/usr/bin:/bin',HOME=str(self.root),LANG='C.UTF-8',HERMES_HOME=str(self.home),PYTHONDONTWRITEBYTECODE='1');env.update(env_changes or {})
        command=[sys.executable,'-I','-S','-B','-c',code]
        if wait:return subprocess.Popen(command,cwd=self.root,env=env,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
        p=subprocess.run(command,cwd=self.root,env=env,capture_output=True,timeout=15)
        self.assertIn(p.returncode,(0,2),p.stderr.decode());return p,json.loads(p.stdout.splitlines()[-1])

    def test_committed_selection_is_observed_without_claiming_pm_or_launcher_readiness(self):
        p,r=self.child();self.assertEqual(p.returncode,0);self.assertEqual(r['state'],'COMMITTED_SELECTION_OBSERVED')
        self.assertEqual(r['committed_environment'],str(self.environment));self.assertFalse(r['pm_readiness_verified']);self.assertFalse(r['normal_launcher_verified']);self.assertEqual(r['pm_actions'],0)
    def test_missing_facts_report_missing_even_with_a_plausible_fallback_python(self):
        self.facts.unlink();legacy=self.source/'venv/bin';legacy.mkdir(parents=True,mode=0o700);(legacy/'python').write_bytes(b'not executed')
        p,r=self.child();self.assertEqual(p.returncode,0);self.assertEqual(r['state'],'COMMITTED_SELECTION_MISSING');self.assertTrue(r['selected_python_exists']);self.assertIsNone(r['committed_environment'])
    def test_changed_source_is_refused_before_its_new_bytes_execute(self):
        c=self.context();target=self.root/'unexpected';(self.source/'pm/environments.py').write_text('open('+repr(str(target))+',"w").write("bad")')
        p,r=self.child(c);self.assertEqual(p.returncode,2);self.assertTrue(r['refused']);self.assertFalse(target.exists())
    def test_unreviewed_sdk_import_is_refused_even_when_a_file_exists(self):
        (self.source/'pm/extra.py').write_text('value=1\n');(self.source/'pm/environments.py').write_text('import pm.extra\n'+self.body)
        p,r=self.child();self.assertEqual(p.returncode,2);self.assertTrue(r['refused'])
    def test_sdk_file_write_is_blocked_and_not_disguised_as_a_selection(self):
        target=self.root/'unexpected';(self.source/'pm/environments.py').write_text('open('+repr(str(target))+',"w").write("bad")\n'+self.body)
        p,r=self.child();self.assertEqual(p.returncode,2);self.assertTrue(r['refused']);self.assertFalse(target.exists())
    def test_preloaded_sdk_alias_is_not_trusted(self):
        p,r=self.child(preamble='import types;sys.modules["pm"]=types.ModuleType("pm")');self.assertEqual(p.returncode,2);self.assertTrue(r['refused'])
    def test_home_mismatch_and_runtime_override_refuse_before_sdk_selection(self):
        for env in ({'HERMES_HOME':str(self.root/'other')},{'HERMES_RUNTIME_DIR':str(self.store)}):
            with self.subTest(env=env):p,r=self.child(env_changes=env);self.assertEqual(p.returncode,2);self.assertTrue(r['refused'])
    def test_facts_symlink_and_hardlink_are_refused_without_following_or_repair(self):
        old=self.facts.with_name('retained-facts');self.facts.rename(old);self.facts.symlink_to(old)
        p,r=self.child();self.assertEqual(p.returncode,2);self.assertTrue(self.facts.is_symlink());self.facts.unlink();os.link(old,self.facts)
        p,r=self.child();self.assertEqual(p.returncode,2);self.assertEqual(self.facts.stat().st_nlink,2);self.assertEqual(self.facts.read_bytes(),old.read_bytes())
    def test_facts_inode_replacement_during_sdk_selection_is_detected(self):
        body=self.body.replace('def committed_venv(source):\n', '''_waited=False
def committed_venv(source):
 global _waited
 if not _waited:
  import sys
  _waited=True;print('SELECTION_WAIT',flush=True);sys.stdin.readline()
''')
        (self.source/'pm/environments.py').write_text(body);p=self.child(wait=True)
        try:
            self.assertEqual(p.stdout.readline(),b'SELECTION_WAIT\n');old=self.facts.stat().st_ino;new=self.facts.with_name('replacement');new.write_bytes(self.facts.read_bytes());new.chmod(0o600);os.replace(new,self.facts)
            out,err=p.communicate(b'\n',timeout=15);self.assertEqual(p.returncode,2,err.decode());self.assertTrue(json.loads(out)['refused']);self.assertNotEqual(old,self.facts.stat().st_ino)
        finally:
            if p.poll() is None:p.kill();p.wait(timeout=5) # Only this fixture-owned child, never an SDK/runtime process.
    def test_read_only_observer_cannot_be_reused_in_the_same_child(self):
        preamble='''original=m.observe_selection
def twice(c,h):
 original(c,h)
 return original(c,h)
m.observe_selection=twice'''
        p,r=self.child(preamble=preamble);self.assertEqual(p.returncode,2);self.assertTrue(r['refused'])


if __name__=='__main__':unittest.main()
