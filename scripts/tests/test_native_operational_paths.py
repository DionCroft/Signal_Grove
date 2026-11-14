from dataclasses import replace
import importlib.util
import json
import os
from pathlib import Path
import sys
import time
import unittest
import uuid

for name in ('cutover_journal','cutover_coordinator','native_file_publication','native_operational_paths'):
    spec=importlib.util.spec_from_file_location(name,Path(__file__).resolve().parents[1]/(name+'.py'));m=importlib.util.module_from_spec(spec);sys.modules[name]=m;spec.loader.exec_module(m)
from native_file_publication import DirectoryAnchor
from native_operational_paths import OperationalDirectories,ROLES

def sample():return OperationalDirectories(tuple((role,DirectoryAnchor('/home/fixture/'+role,1,index+1,1000,1000,0o700)) for index,role in enumerate(ROLES)))

class OperationalSchemaTests(unittest.TestCase):
    def test_all_ten_exact_roles_are_required_in_fixed_order(self):
        for rows in (sample().directories[:-1],tuple(reversed(sample().directories))):
            with self.assertRaises(ValueError):OperationalDirectories(rows)
    def test_duplicate_native_paths_and_unknown_roles_refuse(self):
        rows=list(sample().directories);rows[1]=(rows[1][0],rows[0][1])
        with self.assertRaises(ValueError):OperationalDirectories(tuple(rows))
        rows=list(sample().directories);rows[0]=('other',rows[0][1])
        with self.assertRaises(ValueError):OperationalDirectories(tuple(rows))
    def test_private_roots_and_all_ancestors_have_separate_mode_checks(self):
        for role in ('journal','executor','backup_identity','backup_output'):
            rows=tuple((n,replace(d,mode=0o755) if n==role else d) for n,d in sample().directories)
            with self.subTest(role=role),self.assertRaises(ValueError):OperationalDirectories(rows)
    def test_changed_role_identity_changes_the_review_hash(self):
        rows=list(sample().directories);n,d=rows[-1];rows[-1]=(n,replace(d,inode=d.inode+100))
        self.assertNotEqual(sample().sha256,OperationalDirectories(tuple(rows)).sha256)


@unittest.skipUnless(sys.platform=='linux','Actual descriptor/journal/operational boundaries run on the Pi')
class NativeOperationalPathsTests(unittest.TestCase):
    def setUp(self):
        spec=importlib.util.spec_from_file_location('operational_paths_fixture',Path(__file__).resolve().parents[1]/'native/operational_paths_fixture.py');helper=importlib.util.module_from_spec(spec);spec.loader.exec_module(helper)
        retained=Path(os.environ['SIGNAL_GROVE_OPERATIONAL_RETAIN_ROOT']);self.assertEqual(retained.resolve(),retained)
        self.fixture=helper.build(retained);f=self.fixture
        self.root,self.paths,self.guard,self.catalog,self.publication,self.captured=f['root'],f['paths'],f['guard'],f['catalog'],f['publication'],f['captured'];self.m=f['modules']
        self.native=self.m['native_file_publication'];self.ops=self.m['native_operational_paths'];self.errors=self.m['cutover_journal'];self.coordinator=self.m['cutover_coordinator']

    def tree(self):return sorted((str(p.relative_to(self.root)),p.lstat().st_dev,p.lstat().st_ino,p.lstat().st_mode,p.lstat().st_size,p.lstat().st_mtime_ns,p.lstat().st_ctime_ns) for p in self.root.rglob('*'))
    def journal(self,**kwargs):
        p=self.paths['journal'];options=dict(expected_identity=(p.stat().st_dev,p.stat().st_ino),operational_paths=self.guard,expected_operational_sha256=self.guard.sha256);options.update(kwargs)
        j=self.errors.CutoverJournal(p,**options);self.addCleanup(j.close);return j
    def test_capture_and_all_ten_descriptor_opens_are_read_only(self):
        before=self.tree();self.guard.check()
        for role,p in self.paths.items():
            fd=self.guard.open_directory(role)
            try:self.assertEqual((os.fstat(fd).st_dev,os.fstat(fd).st_ino),(p.stat().st_dev,p.stat().st_ino))
            finally:os.close(fd)
        self.assertEqual(before,self.tree());self.assertEqual(self.guard.scope,'owned_fixture_only')
    def test_changed_publication_or_directory_review_hash_refuses(self):
        before=self.tree()
        for a,b in (('f'*64,self.catalog.sha256),(self.publication.sha256,'f'*64)):
            with self.assertRaises(self.errors.Refused):self.ops.NativeOperationalPaths.capture(self.publication,a,self.catalog,b)
        self.assertEqual(before,self.tree())
    def test_role_swap_cannot_reuse_the_original_catalog_hash(self):
        rows=list(self.catalog.directories);(a,x),(b,y)=rows[:2];rows[:2]=[(a,y),(b,x)];changed=self.ops.OperationalDirectories(tuple(rows))
        with self.assertRaises(self.errors.Refused):self.ops.NativeOperationalPaths.capture(self.publication,self.publication.sha256,changed,self.catalog.sha256)
    def test_arriving_or_changed_operational_inode_is_never_adopted(self):
        p=self.paths['old_store'];old=p.with_name('retained-old-store');p.rename(old);p.mkdir(mode=0o700)
        with self.assertRaises(self.errors.Unknown):self.guard.check()
        self.assertTrue(old.is_dir());self.assertNotEqual(p.stat().st_ino,old.stat().st_ino)
    def test_symlink_to_the_same_original_operational_directory_refuses(self):
        p=self.paths['backup_identity'];old=p.with_name('retained-identity');p.rename(old);p.symlink_to(old,target_is_directory=True)
        with self.assertRaises(self.errors.Refused):self.guard.open_directory('backup_identity')
        self.assertTrue(p.is_symlink())
    def test_unlisted_writable_operational_intermediate_refuses_capture(self):
        (self.root/'operation').chmod(0o775);before=self.tree()
        with self.assertRaises(self.errors.Refused):self.ops.NativeOperationalPaths.capture(self.publication,self.publication.sha256,self.catalog,self.catalog.sha256)
        self.assertEqual(before,self.tree())
    def test_operational_role_cannot_be_an_approved_mutable_mode_target(self):
        rows=list(self.catalog.directories);role,_=rows[-1];mode=self.captured.modes[0].directory;rows[-1]=(role,replace(mode,mode=0o755))
        changed=self.ops.OperationalDirectories(tuple(rows))
        with self.assertRaises(self.errors.Refused):self.ops.NativeOperationalPaths.capture(self.publication,self.publication.sha256,changed,changed.sha256)
    def test_missing_operational_directory_is_neither_created_nor_ignored(self):
        self.paths['backup_programs'].rmdir()
        with self.assertRaises(self.errors.Refused):self.guard.check()
        self.assertFalse(self.paths['backup_programs'].exists())
    def test_current_role_and_executor_containment_are_explicit(self):
        with self.assertRaises(self.errors.Refused):self.guard.require('journal',self.paths['executor'])
        with self.assertRaises(self.errors.Refused):self.guard.open_directory('unknown')
        with self.assertRaises(self.errors.Refused):self.guard.contains('executor',self.paths['old_source'])
        self.guard.contains('executor',self.paths['executor'])
    def test_wrong_guard_hash_opens_no_journal_files(self):
        with self.assertRaises(self.errors.Refused):self.journal(expected_operational_sha256='f'*64)
        self.assertEqual(list(self.paths['journal'].iterdir()),[])
    def test_guarded_journal_uses_the_exact_bound_native_directory(self):
        j=self.journal();op=uuid.uuid4().hex;j.begin(op,'a'*64,authorization_sha256='b'*64);j.intent('preflight');j.verified('preflight','c'*64)
        self.assertEqual(j.view['state'],'active');self.assertIs(j._operational_paths,self.guard);self.guard.check()
    def test_ancestor_drift_poisoning_prevents_the_next_journal_write(self):
        j=self.journal();j.begin(uuid.uuid4().hex,'a'*64,authorization_sha256='b'*64);before=(self.paths['journal']/'journal.jsonl').read_bytes()
        (self.root/'operation').chmod(0o775)
        with self.assertRaises(self.errors.Unknown):j.intent('preflight')
        self.assertEqual((self.paths['journal']/'journal.jsonl').read_bytes(),before);self.assertTrue(j.view['durability_uncertain'])
    def test_missing_native_boundary_cannot_be_replaced_by_a_hash(self):
        p=self.paths['journal']
        with self.assertRaises(self.errors.Refused):self.errors.CutoverJournal(p,expected_identity=(p.stat().st_dev,p.stat().st_ino),expected_operational_sha256=self.guard.sha256)
        self.assertEqual(list(p.iterdir()),[])
    def test_real_mode_stage_and_journal_share_the_same_transition_witness(self):
        j=self.journal();c=self.coordinator;plan=c.Plan('a'*64,'b'*64,'c'*64,'d'*64);e=c.ApprovedExecution(uuid.uuid4().hex,plan.packet_sha256,plan.authority_sha256,'forward','e'*64,time.monotonic()+120)
        j.begin(e.operation,plan.packet_sha256,authorization_sha256=e.binding_sha256)
        for step in c.FORWARD[:c.FORWARD.index('secure_modes')]:j.intent(step);j.verified(step,'f'*64)
        j.intent('secure_modes');before=self.guard.check();bindings=self.captured.modes
        actor=self.native.NativeFilePublication(plan,j,'secure_modes',bindings,self.native.digest([b.evidence() for b in bindings]),ancestry=self.guard.ancestry,expected_ancestry_sha256=self.guard.ancestry.sha256)
        result=actor.run(e);self.assertNotEqual(before,self.guard.check());j.verified('secure_modes',self.native.digest(result))
        self.assertEqual(j.view['state'],'active');self.assertEqual(self.guard.ancestry._secured,set(self.native.MODE_ROLES));self.assertEqual(self.guard.sha256,j._operational_paths.sha256)
    def test_separate_old_guard_cannot_inherit_real_mode_transition_authority(self):
        m=self.captured.modes[0];Path(m.directory.path).chmod(0o755)
        with self.assertRaises(self.errors.Unknown):self.guard.check()
        with self.assertRaises(self.errors.Unknown):self.publication.check()
    def test_guarded_journal_observes_actual_parent_creation_and_publication(self):
        j=self.journal();c=self.coordinator;plan=c.Plan('a'*64,'b'*64,'c'*64,'d'*64);e=c.ApprovedExecution(uuid.uuid4().hex,plan.packet_sha256,plan.authority_sha256,'forward','e'*64,time.monotonic()+120)
        j.begin(e.operation,plan.packet_sha256,authorization_sha256=e.binding_sha256)
        for step in c.FORWARD[:c.FORWARD.index('secure_modes')]:j.intent(step);j.verified(step,'f'*64)
        for step in ('secure_modes','publish_reviewed'):
            j.intent(step);bindings=self.captured.for_stage(step)
            actor=self.native.NativeFilePublication(plan,j,step,bindings,self.native.digest([b.evidence() for b in bindings]),ancestry=self.guard.ancestry,expected_ancestry_sha256=self.guard.ancestry.sha256)
            result=actor.run(e);j.verified(step,self.native.digest(result));self.guard.check()
        self.assertEqual(j.view['state'],'active');self.assertGreater(len(self.guard.ancestry._created),0)
        for binding in actor.resolved_bindings:
            self.assertEqual((Path(binding.parent.path)/binding.name).read_bytes(),binding.value)
    def test_capture_after_a_real_transition_is_refused(self):
        first=self.captured.modes[0];Path(first.directory.path).chmod(0o755);self.publication._mode_observed(first)
        with self.assertRaises(self.errors.Refused):self.ops.NativeOperationalPaths.capture(self.publication,self.publication.sha256,self.catalog,self.catalog.sha256)

if __name__=='__main__':unittest.main()
