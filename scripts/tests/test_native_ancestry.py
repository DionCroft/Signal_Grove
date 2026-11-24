from dataclasses import asdict,replace
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import sys
import tempfile
import time
import unittest
from unittest.mock import patch
import uuid

for name in ('cutover_journal','cutover_coordinator','plan_pi_runtime','native_file_publication','rendered_publication_catalog'):
    spec=importlib.util.spec_from_file_location(name,Path(__file__).resolve().parents[1]/(name+'.py'))
    module=importlib.util.module_from_spec(spec);sys.modules[name]=module;spec.loader.exec_module(module)
import native_file_publication as native
from native_file_publication import AncestryNode,NativeFilePublication,MODE_ROLES,anchor,digest
from rendered_publication_catalog import RenderedCatalog,PolicyReview,capture_native
from cutover_coordinator import Plan,ApprovedExecution,FORWARD
from cutover_journal import CutoverJournal,Refused,Unknown


class AncestrySchemaTests(unittest.TestCase):
    def test_root_owned_directory_has_native_identity_without_an_owner_script_claim(self):
        node=AncestryNode('/',1,2,0,0,0o755)
        self.assertEqual(node.path,'/');self.assertEqual(node.uid,0)

    def test_boolean_identity_and_special_modes_are_not_native_directory_witnesses(self):
        node=AncestryNode('/home/fixture',1,2,1000,1000,0o700)
        for change in (dict(inode=True),dict(uid=False),dict(mode=0o1777),dict(mode=True),dict(inode=0)):
            with self.subTest(change=change),self.assertRaises(ValueError):replace(node,**change)

    def test_directory_witness_paths_cannot_hide_traversal_or_commands(self):
        node=AncestryNode('/home/fixture',1,2,1000,1000,0o700)
        for path in ('relative','//home','/home/../outside','/home/fixture/$(command)','/home/fixture\nother'):
            with self.subTest(path=path),self.assertRaises(ValueError):replace(node,path=path)


@unittest.skipUnless(sys.platform=='linux','Descriptor ancestry and publication effects run on the native Pi')
class NativeAncestryTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(prefix='signal-grove-ancestry-');self.addCleanup(self.temp.cleanup)
        self.home=Path(self.temp.name).resolve();self.root=self.home/('signal-grove-catalog-'+uuid.uuid4().hex);self.root.mkdir(mode=0o700)
        account=self.root/'account';fleet=account/'Projects/fleet'
        policy=json.dumps(dict(schema=1,authority='fixture-authority',grants=[dict(subject='fixture-owner',profile_id=str(fleet/'hermes'),device='FIXTURE',project_ids=['fixture'])]),separators=(',',':')).encode()
        launcher=b'#!/bin/sh\nexit 0\n';review=PolicyReview('fixture-authority',('FIXTURE',),hashlib.sha256(policy).hexdigest())
        self.definition=RenderedCatalog.for_owned_fixture(self.root,review,policy,hashlib.sha256(launcher).hexdigest());p=self.definition.packet()
        self.commands=Path(p['command_directory']);self.commands.mkdir(parents=True,mode=0o700);(self.commands/'hermes').write_bytes(launcher);(self.commands/'hermes').chmod(0o755)
        self.source=self.commands.parent/p['layout']['source_name'];self.source.mkdir(mode=0o700)
        client=account/'.local/bin';client.mkdir(parents=True,mode=0o700);(client/'hermes').write_bytes(b'original-cli');(client/'hermes').chmod(0o755)
        (account/'.config/systemd/user').mkdir(parents=True,mode=0o700)
        (account/'.local/state').mkdir(mode=0o700)
        for _,path in self.definition.mode_paths():
            directory=Path(path);directory.mkdir(parents=True,exist_ok=True,mode=0o700);directory.chmod(0o775)
        self.captured=capture_native(self.definition,self.definition.sha256)
        self.ancestry=self.captured.capture_ancestry(self.captured.sha256)
        ledger=self.home/'ledger';ledger.mkdir(mode=0o700)
        self.journal=CutoverJournal(ledger,expected_identity=(ledger.stat().st_dev,ledger.stat().st_ino));self.addCleanup(self.journal.close)
        self.ledger=ledger;self.plan=Plan(digest(dict(capture=self.captured.sha256,ancestry=self.ancestry.sha256)),'b'*64,'c'*64,'d'*64)

    def tree(self):
        return sorted((p.relative_to(self.root).as_posix(),p.lstat().st_ino,p.lstat().st_mode,p.lstat().st_size,p.lstat().st_mtime_ns,p.lstat().st_ctime_ns) for p in self.root.rglob('*'))

    def begin(self,stage='secure_modes',*,hash_value=None):
        e=ApprovedExecution(uuid.uuid4().hex,self.plan.packet_sha256,self.plan.authority_sha256,'forward','f'*64,time.monotonic()+180)
        self.journal.begin(e.operation,e.packet_sha256,authorization_sha256=e.binding_sha256)
        for prior in FORWARD[:FORWARD.index(stage)]:
            self.journal.intent(prior);self.journal.verified(prior,'c'*64) # Explicit synthetic non-file prerequisites.
        self.journal.intent(stage);bindings=self.captured.for_stage(stage)
        return NativeFilePublication(self.plan,self.journal,stage,bindings,digest([b.evidence() for b in bindings]),ancestry=self.ancestry,
            expected_ancestry_sha256=self.ancestry.sha256 if hash_value is None else hash_value),e

    def test_full_owned_capture_and_recheck_are_read_only_and_scope_bound(self):
        before=self.tree();guard=self.captured.capture_ancestry(self.captured.sha256);guard.check()
        self.assertEqual(before,self.tree());self.assertEqual(guard.sha256,self.ancestry.sha256)
        self.assertEqual(guard.scope,'owned_fixture_only');self.assertNotIn(str(self.home),[n['path'] for n in guard.metadata()['directories']])

    def test_changed_review_capture_hash_refuses_without_tree_writes(self):
        before=self.tree()
        with self.assertRaises(Refused):self.captured.capture_ancestry('9'*64)
        self.assertEqual(before,self.tree())

    def test_unlisted_writable_intermediate_ancestor_refuses_capture(self):
        (self.root/'account/.local').chmod(0o775);before=self.tree()
        with self.assertRaises(Refused):self.captured.capture_ancestry(self.captured.sha256)
        self.assertEqual(before,self.tree())

    def test_intermediate_symlink_to_the_same_original_directory_is_not_followed(self):
        local=self.root/'account/.local';old=local.with_name('retained-local');local.rename(old);local.symlink_to(old,target_is_directory=True)
        with self.assertRaises(Refused):self.ancestry.check()
        self.assertTrue(local.is_symlink());self.assertEqual((old/'bin/hermes').read_bytes(),b'original-cli')

    def test_same_mode_replacement_directory_cannot_replace_its_native_identity(self):
        old=self.source.with_name('retained-source');self.source.rename(old);self.source.mkdir(mode=0o700)
        with self.assertRaises(Unknown):self.ancestry.check()
        self.assertTrue(old.is_dir());self.assertNotEqual(old.stat().st_ino,self.source.stat().st_ino)

    def test_lost_source_directory_is_not_created_as_a_missing_publication_parent(self):
        self.source.rmdir()
        with self.assertRaises(Refused):self.ancestry.check()
        self.assertFalse(self.source.exists())

    def test_source_root_permissions_drift_is_not_repaired(self):
        self.source.chmod(0o775)
        with self.assertRaises(Unknown):self.ancestry.check()
        self.assertEqual(self.source.stat().st_mode&0o777,0o775)

    def test_mode_witness_cannot_advance_without_the_actual_inode_mode_change(self):
        with self.assertRaises(Unknown):self.ancestry._mode_observed(self.captured.modes[0])
        self.assertEqual(Path(self.captured.modes[0].directory.path).stat().st_mode&0o777,0o775)
        with self.assertRaises(Unknown):self.ancestry.check()

    def test_wrong_guard_hash_has_no_native_receipt_or_dispatch(self):
        with self.assertRaises(Refused):self.begin(hash_value='9'*64)
        self.assertEqual(list(self.ledger.glob('native-files-*')),[])
        self.assertEqual(self.journal.view['pending'],'secure_modes')

    def test_files_cannot_publish_before_all_seven_modes_are_observed(self):
        with self.assertRaises(Refused):self.begin('publish_reviewed')
        self.assertTrue(all(Path(path).stat().st_mode&0o777==0o775 for _,path in self.definition.mode_paths()))

    def test_arriving_generation_parent_is_preserved_without_adoption(self):
        creation=self.ancestry.creations[0];Path(creation.path).mkdir(mode=0o700);before=self.tree()
        with self.assertRaises(Refused):self.begin()
        self.assertEqual(before,self.tree());self.assertTrue(Path(creation.path).is_dir())

    def test_bound_directory_descriptor_is_the_exact_captured_inode(self):
        expected=self.captured.modes[0].directory;fd=self.ancestry.open_directory(expected)
        try:self.assertEqual((os.fstat(fd).st_dev,os.fstat(fd).st_ino),(expected.device,expected.inode))
        finally:os.close(fd)

    def test_real_mode_actor_advances_all_witnesses_and_records_the_guard(self):
        c,e=self.begin();before_hash=self.ancestry.sha256;before_state=self.ancestry.state_sha256;result=c.run(e)
        self.assertEqual(self.ancestry._secured,set(MODE_ROLES));self.ancestry.check()
        self.assertEqual(self.ancestry.sha256,before_hash);self.assertNotEqual(self.ancestry.state_sha256,before_state)
        self.assertEqual(result['ancestry_sha256'],before_hash);self.assertEqual(result['ancestry_state_sha256'],self.ancestry.state_sha256)
        rows=[json.loads(line) for line in (self.ledger/result['receipt']).read_text().splitlines()]
        self.assertEqual(len([r for r in rows if r['phase']=='ancestry_bound']),1)
        self.assertEqual(self.journal.view['pending'],'secure_modes')

    def test_ancestry_drift_after_claim_holds_unknown_and_cannot_replay_modes(self):
        c,e=self.begin();original=native._Receipt.append
        def append(receipt,phase,evidence):
            result=original(receipt,phase,evidence)
            if phase=='ancestry_bound':self.source.chmod(0o775)
            return result
        with patch.object(native._Receipt,'append',append),self.assertRaises(Unknown):c.run(e)
        self.assertTrue(c.used);self.assertEqual(c.state,'unknown');self.assertEqual(self.journal.view['pending'],'secure_modes')
        self.assertTrue(all(Path(path).stat().st_mode&0o777==0o775 for _,path in self.definition.mode_paths()))
        with self.assertRaises(Refused):c.run(e)
        self.assertTrue((self.ledger/c.last_receipt).exists())

    def test_unreviewed_open_parent_is_refused_before_an_fd_is_returned(self):
        other=self.root/'outside-catalog';other.mkdir(mode=0o700)
        with self.assertRaises(Refused):self.ancestry.open_directory(anchor(other))


if __name__=='__main__':unittest.main()
