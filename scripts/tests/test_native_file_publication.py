from dataclasses import replace
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

for name in ('cutover_journal','cutover_coordinator','native_file_publication'):
    spec=importlib.util.spec_from_file_location(name,Path(__file__).resolve().parents[1]/(name+'.py'))
    module=importlib.util.module_from_spec(spec);sys.modules[name]=module;spec.loader.exec_module(module)
from cutover_journal import CutoverJournal,Refused,Unknown
from cutover_coordinator import Plan,ApprovedExecution,FORWARD,RECOVERY
import native_file_publication as native
from native_file_publication import DirectoryAnchor,DirectoryCreation,FileImage,FileBinding,ModeBinding,NativeFilePublication,anchor,observe,digest,FILE_ROLES,MODE_ROLES

P,A,S,R=['a'*64,'b'*64,'c'*64,'d'*64]
DUMMY=DirectoryAnchor('/home/fixture/parent',10,20,1000,1000,0o700)


class PublicationSchemaTests(unittest.TestCase):
    def test_new_parent_requires_one_literal_child_and_an_absent_file_preimage(self):
        new=DirectoryCreation(DUMMY,'reviewed-generation')
        for name in ('../outside','a/b','.','..',True,'/home/other'):
            with self.subTest(name=name),self.assertRaises(ValueError):replace(new,name=name)
        image=FileImage('file',10,20,0o600,1000,1000,1,3,1,2,'a'*64)
        with self.assertRaises(ValueError):FileBinding('policy',new,image,'file',b'{}')

    def test_directory_identity_refuses_boolean_root_uid_or_ambiguous_paths(self):
        for change in (dict(inode=True),dict(uid=0),dict(mode=0o777),dict(path='/home/fixture/../outside'),dict(path='/home/fixture\nfile')):
            with self.subTest(change=change),self.assertRaises(ValueError):replace(DUMMY,**change)

    def test_absence_cannot_carry_file_identity_or_boolean_fields(self):
        absent=FileImage('absent')
        for change in (dict(inode=1),dict(mode=True),dict(size=False),dict(sha256='a'*64)):
            with self.subTest(change=change),self.assertRaises(ValueError):replace(absent,**change)

    def test_file_image_refuses_hardlinks_special_modes_and_oversized_content(self):
        image=FileImage('file',10,20,0o600,1000,1000,1,3,1,2,'a'*64)
        for change in (dict(links=2),dict(size=native.MAX_FILE+1),dict(mode=0o6755),dict(uid=True),dict(kind='directory')):
            with self.subTest(change=change),self.assertRaises(ValueError):replace(image,**change)

    def test_only_closed_role_names_and_reviewed_payload_types_bind(self):
        good=FileBinding('policy',DUMMY,FileImage('absent'),'file',b'{}')
        for change in (dict(role='.env'),dict(role='../hermes'),dict(kind='file',value='private'),dict(kind='link',value='/home/fixture/x')):
            with self.subTest(change=change),self.assertRaises(ValueError):replace(good,**change)

    def test_payload_bytes_are_hashed_and_not_in_repr_or_evidence(self):
        value=b'SYNTHETIC_PRIVATE_VALUE';binding=FileBinding('policy',DUMMY,FileImage('absent'),'file',value)
        self.assertNotIn(value.decode(),repr(binding));self.assertNotIn(value.decode(),json.dumps(binding.evidence()))
        self.assertNotEqual(digest([binding.evidence()]),digest([replace(binding,value=value+b'2').evidence()]))

    def test_new_link_targets_are_literal_absolute_paths(self):
        binding=FileBinding('client_link',DUMMY,FileImage('absent'),'link','/home/fixture/runtime/hermes')
        for target in ('relative','/home/../other','//home/fixture','/home/fixture/\x00private','/home/fixture/$(command)'):
            with self.subTest(target=target),self.assertRaises(ValueError):replace(binding,value=target)

    def test_modes_cannot_choose_recursive_paths_or_other_mode_transitions(self):
        binding=ModeBinding('fleet',replace(DUMMY,mode=0o775))
        for change in (dict(role='recursive'),dict(directory=replace(DUMMY,mode=0o755)),dict(directory=replace(DUMMY,mode=0o700))):
            with self.subTest(change=change),self.assertRaises(ValueError):replace(binding,**change)

    def test_native_rename_never_falls_back_to_overwrite_flags_or_relative_traversal(self):
        for source,target,flags in (('a','b',0),('a','b',True),('../a','b',1),('a','b/c',2)):
            with self.subTest(source=source,target=target,flags=flags),self.assertRaises(Refused):native._renameat2(-1,source,target,flags)


@unittest.skipUnless(sys.platform=='linux','Actual Linux renameat2, inode, fsync and mode checks run in the owned Pi cohort')
class NativePublicationTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(prefix='signal-grove-files-');self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name).resolve();self.root.chmod(0o700);self.ledger=self.root/'ledger';self.ledger.mkdir(mode=0o700)
        self.journal=CutoverJournal(self.ledger,expected_identity=(self.ledger.stat().st_dev,self.ledger.stat().st_ino));self.addCleanup(self.journal.close)
        self.plan=Plan(P,A,S,R)

    def binding(self,role='policy',old=b'old-reviewed',kind='file',value=b'new-reviewed',old_mode=0o600):
        directory=self.root/role;directory.mkdir(mode=0o700);target=directory/FILE_ROLES[role][0]
        if old is not None:
            if type(old) is str:target.symlink_to(old)
            else:target.write_bytes(old);target.chmod(old_mode)
        parent=anchor(directory)
        return FileBinding(role,parent,observe(parent,role),kind,value)

    def begin(self,stage,bindings,*,deadline=None,recovery=False):
        packet='e'*64 if recovery else P
        execution=ApprovedExecution(uuid.uuid4().hex,packet,A,'recovery' if recovery else 'forward','f'*64,
            time.monotonic()+180 if deadline is None else deadline)
        if recovery:
            original=uuid.uuid4().hex;self.journal.begin(original,P,authorization_sha256='9'*64);self.journal.intent('preflight');self.journal.unknown()
            history=self.journal.history_sha256
            self.journal.begin_recovery(execution.operation,packet,original_operation=original,original_plan_sha256=P,
                history_sha256=history,evidence_sha256=S,authorization_sha256=execution.binding_sha256)
        else:self.journal.begin(execution.operation,packet,authorization_sha256=execution.binding_sha256)
        stages=RECOVERY if recovery else FORWARD
        for prior in stages[:stages.index(stage)]:
            # Explicit synthetic non-file prerequisites. Real native effects are
            # tested here; this is not PM/backup/admission/cutover acceptance.
            self.journal.intent(prior);self.journal.verified(prior,S)
        self.journal.intent(stage)
        component=NativeFilePublication(self.plan,self.journal,stage,bindings,digest([b.evidence() for b in bindings]))
        return component,execution

    def target(self,b):return Path(b.parent.path)/b.name
    def rows(self,c):return [json.loads(line) for line in (self.ledger/c.last_receipt).read_text(encoding='utf8').splitlines()]
    def assert_pending(self,stage):self.assertEqual(self.journal.view['state'],'pending');self.assertEqual(self.journal.view['pending'],stage)

    def test_atomic_existing_file_exchange_preserves_displaced_inode_and_bytes(self):
        b=self.binding();c,e=self.begin('install_policy',(b,));result=c.run(e)
        self.assertEqual(self.target(b).read_bytes(),b'new-reviewed');row=next(r['evidence'] for r in self.rows(c) if r['phase']=='entry_observed')
        retained=Path(b.parent.path)/row['temporary'];self.assertEqual(retained.read_bytes(),b'old-reviewed');self.assertEqual(retained.stat().st_ino,b.before.inode)
        self.assertEqual(result['receipt_sha256'],hashlib.sha256((self.ledger/c.last_receipt).read_bytes()).hexdigest());self.assert_pending('install_policy')

    def test_verified_absence_uses_native_no_replace(self):
        b=self.binding(old=None);c,e=self.begin('install_policy',(b,));c.run(e)
        self.assertEqual(self.target(b).read_bytes(),b'new-reviewed');self.assertEqual(list(Path(b.parent.path).glob('*.incoming')),[])

    def test_identical_file_keeps_its_inode_without_preparing_an_entry(self):
        b=self.binding(old=b'new-reviewed');c,e=self.begin('install_policy',(b,));c.run(e)
        self.assertEqual(self.target(b).stat().st_ino,b.before.inode);self.assertFalse(list(Path(b.parent.path).glob('*.incoming')))
        self.assertTrue(any(r['phase']=='entry_unchanged' for r in self.rows(c)))

    def test_symlink_exchange_does_not_follow_or_modify_its_old_target(self):
        protected=self.root/'protected';protected.write_bytes(b'unrelated-original')
        b=self.binding('client_link',old=str(protected),kind='link',value=str(self.root/'new-runtime'))
        c,e=self.begin('bind_cli',(b,));c.run(e);self.assertEqual(protected.read_bytes(),b'unrelated-original')
        self.assertEqual(os.readlink(self.target(b)),b.value);retained=next(Path(b.parent.path).glob('*.incoming'));self.assertEqual(os.readlink(retained),str(protected))

    def test_regular_client_wrapper_can_be_exchanged_for_a_link_without_losing_old_bytes(self):
        b=self.binding('client_link',old=b'old-client-wrapper',old_mode=0o755,kind='link',value=str(self.root/'runtime'))
        c,e=self.begin('bind_cli',(b,));c.run(e);self.assertTrue(self.target(b).is_symlink());self.assertEqual(next(Path(b.parent.path).glob('*.incoming')).read_bytes(),b'old-client-wrapper')

    def test_target_drift_refuses_before_claim_or_receipt_creation(self):
        b=self.binding();self.target(b).write_bytes(b'concurrent-owner-change');c,e=self.begin('install_policy',(b,))
        with self.assertRaises(Refused):c.run(e)
        self.assertIsNone(c.last_receipt);self.assertEqual(self.target(b).read_bytes(),b'concurrent-owner-change');self.assert_pending('install_policy')

    def test_parent_replacement_is_not_followed(self):
        b=self.binding();parent=Path(b.parent.path);parent.rename(self.root/'saved-parent');parent.mkdir(mode=0o700)
        c,e=self.begin('install_policy',(b,))
        with self.assertRaises((Refused,Unknown)):c.run(e)
        self.assertEqual(list(parent.iterdir()),[]);self.assertEqual((self.root/'saved-parent'/b.name).read_bytes(),b'old-reviewed')

    def test_parent_symlink_cannot_redirect_publication(self):
        b=self.binding();parent=Path(b.parent.path);parent.rename(self.root/'saved-parent');victim=self.root/'victim';victim.mkdir(mode=0o700);parent.symlink_to(victim,target_is_directory=True)
        c,e=self.begin('install_policy',(b,))
        with self.assertRaises((Refused,Unknown)):c.run(e)
        self.assertEqual(list(victim.iterdir()),[])

    def test_hardlinked_preimage_is_refused_without_breaking_any_link(self):
        b=self.binding();os.link(self.target(b),self.root/'second-name');c,e=self.begin('install_policy',(b,))
        with self.assertRaises(Refused):c.run(e)
        self.assertEqual(self.target(b).stat().st_nlink,2);self.assertEqual((self.root/'second-name').read_bytes(),b'old-reviewed')

    def test_short_native_read_is_not_a_complete_content_hash(self):
        b=self.binding();read=os.pread;inode=b.before.inode
        def short(fd,size,offset):
            value=read(fd,size,offset);return value[:-1] if os.fstat(fd).st_ino==inode else value
        with patch.object(native.os,'pread',side_effect=short),self.assertRaises(Unknown):observe(b.parent,b.role)

    def test_concurrent_creation_wins_no_replace_and_both_contents_are_retained(self):
        b=self.binding(old=None);c,e=self.begin('install_policy',(b,));rename=native._renameat2
        def race(fd,source,target,flags):
            self.target(b).write_bytes(b'concurrent-owner-created');rename(fd,source,target,flags)
        with patch.object(native,'_renameat2',side_effect=race),self.assertRaises(Unknown):c.run(e)
        self.assertEqual(self.target(b).read_bytes(),b'concurrent-owner-created');self.assertEqual(next(Path(b.parent.path).glob('*.incoming')).read_bytes(),b'new-reviewed');self.assert_pending('install_policy')

    def test_exchange_race_preserves_displaced_concurrent_content_and_holds(self):
        b=self.binding();c,e=self.begin('install_policy',(b,));rename=native._renameat2
        def race(fd,source,target,flags):
            foreign=Path(b.parent.path)/'concurrent';foreign.write_bytes(b'concurrent-unique');foreign.chmod(0o600);os.replace(foreign,self.target(b));rename(fd,source,target,flags)
        with patch.object(native,'_renameat2',side_effect=race),self.assertRaises(Unknown):c.run(e)
        self.assertEqual(self.target(b).read_bytes(),b'new-reviewed');self.assertEqual(next(Path(b.parent.path).glob('*.incoming')).read_bytes(),b'concurrent-unique')
        self.assertEqual(c.state,'unknown');self.assert_pending('install_policy')

    def test_post_rename_fsync_failure_does_not_restore_or_repeat_the_effect(self):
        b=self.binding();c,e=self.begin('install_policy',(b,));sync=os.fsync;calls=[]
        def fail(fd):
            if os.fstat(fd).st_ino==b.parent.inode:
                calls.append(fd)
                if len(calls)==2:raise OSError('injected native storage error')
            sync(fd)
        with patch.object(native.os,'fsync',side_effect=fail),self.assertRaises(Unknown):c.run(e)
        self.assertEqual(self.target(b).read_bytes(),b'new-reviewed');self.assertEqual(next(Path(b.parent.path).glob('*.incoming')).read_bytes(),b'old-reviewed')
        self.assertNotIn('entry_observed',[r['phase'] for r in self.rows(c)]);self.assert_pending('install_policy')
        with self.assertRaises(Refused):c.run(e)

    def test_receipt_fsync_failure_retains_pending_intent_and_closes_owned_fds(self):
        b=self.binding();c,e=self.begin('install_policy',(b,));sync=os.fsync;before=len(list(Path('/proc/self/fd').iterdir()))
        def fail(fd):
            name=os.readlink('/proc/self/fd/'+str(fd))
            if '/native-files-' in name:raise OSError('injected receipt storage error')
            sync(fd)
        with patch.object(native.os,'fsync',side_effect=fail),self.assertRaises(Unknown):c.run(e)
        self.assertEqual(self.target(b).read_bytes(),b'old-reviewed');self.assertEqual(before,len(list(Path('/proc/self/fd').iterdir())))
        self.assert_pending('install_policy');self.assertEqual(len(list(self.ledger.glob('native-files-*.jsonl'))),1)
        replacement=NativeFilePublication(self.plan,self.journal,'install_policy',(b,),c.catalog)
        with self.assertRaises(Refused):replacement.run(e)

    def test_existing_receipt_is_not_overwritten_or_followed(self):
        b=self.binding();c,e=self.begin('install_policy',(b,));receipt=self.ledger/('native-files-'+e.operation+'-install_policy.jsonl');receipt.write_bytes(b'unknown-existing-receipt')
        with self.assertRaises(Unknown):c.run(e)
        self.assertEqual(receipt.read_bytes(),b'unknown-existing-receipt');self.assertEqual(self.target(b).read_bytes(),b'old-reviewed')

    def test_replacement_component_cannot_reclaim_an_unchanged_pending_intent(self):
        b=self.binding(old=b'new-reviewed');c,e=self.begin('install_policy',(b,));c.run(e)
        replacement=NativeFilePublication(self.plan,self.journal,'install_policy',(b,),c.catalog)
        with self.assertRaises(Refused):replacement.run(e)
        self.assertEqual(self.target(b).stat().st_ino,b.before.inode);self.assertEqual(len(list(self.ledger.glob('native-files-*.jsonl'))),1)

    def test_catalog_digest_and_complete_role_set_are_required(self):
        bindings=tuple(self.binding(role=r) for r in tuple(FILE_ROLES)[:6]);catalog=digest([b.evidence() for b in bindings])
        with self.assertRaises(Refused):NativeFilePublication(self.plan,self.journal,'publish_reviewed',bindings,'f'*64)
        with self.assertRaises(ValueError):NativeFilePublication(self.plan,self.journal,'publish_reviewed',bindings[:-1],catalog)
        alias=replace(bindings[4],parent=bindings[2].parent)
        with self.assertRaises(ValueError):NativeFilePublication(self.plan,self.journal,'publish_reviewed',(*bindings[:4],alias,bindings[5]),catalog)

    def test_unverified_fixed_prefix_never_claims_or_mutates(self):
        b=self.binding();e=ApprovedExecution(uuid.uuid4().hex,P,A,'forward','f'*64,time.monotonic()+60)
        self.journal.begin(e.operation,P,authorization_sha256=e.binding_sha256);self.journal.intent('install_policy')
        c=NativeFilePublication(self.plan,self.journal,'install_policy',(b,),digest([b.evidence()]))
        with self.assertRaises(Refused):c.run(e)
        self.assertEqual(self.target(b).read_bytes(),b'old-reviewed');self.assertIsNone(c.last_receipt)

    def test_expired_approval_holds_before_dispatch(self):
        b=self.binding();c,e=self.begin('install_policy',(b,),deadline=time.monotonic()-1)
        with self.assertRaises(Unknown):c.run(e)
        self.assertEqual(self.target(b).read_bytes(),b'old-reviewed');self.assertIsNone(c.last_receipt)

    def test_deadline_after_atomic_effect_holds_without_compensation(self):
        b=self.binding();c,e=self.begin('install_policy',(b,));rename=native._renameat2;clock=time.monotonic;effect=[]
        def commit(fd,source,target,flags):rename(fd,source,target,flags);effect.append(True)
        def now():return e.deadline+1 if effect else clock()
        with patch.object(native,'_renameat2',side_effect=commit),patch.object(native.time,'monotonic',side_effect=now),self.assertRaises(Unknown):c.run(e)
        self.assertEqual(self.target(b).read_bytes(),b'new-reviewed');self.assertEqual(next(Path(b.parent.path).glob('*.incoming')).read_bytes(),b'old-reviewed');self.assert_pending('install_policy')
        self.assertNotIn('catalog_effects_observed',[r['phase'] for r in self.rows(c)])

    def test_all_six_artifacts_are_observed_without_verifying_the_journal_stage(self):
        bindings=tuple(self.binding(role=r,old=None if i%2 else b'old-reviewed') for i,r in enumerate(tuple(FILE_ROLES)[:6]));c,e=self.begin('publish_reviewed',bindings);c.run(e)
        for b in bindings:
            self.assertEqual(self.target(b).read_bytes(),b'new-reviewed');self.assertEqual(self.target(b).stat().st_mode&0o777,b.mode)
        self.assertEqual(self.rows(c)[-1]['evidence']['roles'],[b.role for b in bindings]);self.assert_pending('publish_reviewed')

    def test_a_late_bad_catalog_preimage_prevents_every_artifact_write(self):
        bindings=tuple(self.binding(role=r) for r in tuple(FILE_ROLES)[:6]);self.target(bindings[-1]).write_bytes(b'owner-edited-last');c,e=self.begin('publish_reviewed',bindings)
        with self.assertRaises(Refused):c.run(e)
        for b in bindings[:-1]:self.assertEqual(self.target(b).read_bytes(),b'old-reviewed')
        self.assertIsNone(c.last_receipt)

    def modes(self):
        result=[]
        for role in MODE_ROLES:
            directory=self.root/role;directory.mkdir(mode=0o700);child=directory/'unrelated';child.write_bytes(b'unchanged');child.chmod(0o600);directory.chmod(0o775)
            result.append(ModeBinding(role,anchor(directory)))
        return tuple(result)

    def test_seven_named_modes_change_without_recursive_or_ownership_effects(self):
        bindings=self.modes();c,e=self.begin('secure_modes',bindings);c.run(e)
        for b in bindings:
            directory=Path(b.directory.path);self.assertEqual(directory.stat().st_mode&0o777,0o755);self.assertEqual(directory.stat().st_uid,b.directory.uid)
            self.assertEqual((directory/'unrelated').stat().st_mode&0o777,0o600);self.assertEqual((directory/'unrelated').read_bytes(),b'unchanged')
        self.assertEqual(len([r for r in self.rows(c) if r['phase']=='mode_observed']),7);self.assert_pending('secure_modes')

    def test_mode_preimage_change_prevents_all_chmods(self):
        bindings=self.modes();Path(bindings[-1].directory.path).chmod(0o700);c,e=self.begin('secure_modes',bindings)
        with self.assertRaises(Unknown):c.run(e)
        for b in bindings[:-1]:self.assertEqual(Path(b.directory.path).stat().st_mode&0o777,0o775)
        self.assertIsNone(c.last_receipt)

    def test_failure_after_first_chmod_preserves_partial_native_outcome(self):
        bindings=self.modes();c,e=self.begin('secure_modes',bindings);chmod=os.fchmod;effects=[]
        def fail(fd,mode):chmod(fd,mode);effects.append(True);raise OSError('injected post-chmod failure')
        with patch.object(native.os,'fchmod',side_effect=fail),self.assertRaises(Unknown):c.run(e)
        self.assertEqual(len(effects),1);self.assertEqual(Path(bindings[0].directory.path).stat().st_mode&0o777,0o755)
        for b in bindings[1:]:self.assertEqual(Path(b.directory.path).stat().st_mode&0o777,0o775)
        self.assert_pending('secure_modes')

    def test_explicit_recovery_retires_policy_by_preserving_its_actual_entry(self):
        b=self.binding(kind='absent',value=None);c,e=self.begin('rollback_policy',(b,),recovery=True);c.run(e)
        self.assertFalse(self.target(b).exists());self.assertEqual(next(Path(b.parent.path).glob('*.incoming')).read_bytes(),b'old-reviewed');self.assert_pending('rollback_policy')

    def test_explicit_recovery_can_observe_an_already_absent_policy(self):
        b=self.binding(old=None,kind='absent',value=None);c,e=self.begin('rollback_policy',(b,),recovery=True);c.run(e)
        self.assertFalse(self.target(b).exists());self.assertFalse(list(Path(b.parent.path).glob('*.incoming')));self.assert_pending('rollback_policy')

    def test_explicit_runtime_recovery_changes_only_the_three_exact_links(self):
        new=str(self.root/'new-runtime');old=str(self.root/'old-runtime')
        bindings=tuple(self.binding(role=r,old=new,kind='absent' if r!='client_link' else 'link',value=None if r!='client_link' else old) for r in ('gateway_link','serve_link','client_link'))
        protected=self.root/'newer-user-data';protected.write_bytes(b'preserve-new-history')
        c,e=self.begin('rollback_runtime',bindings,recovery=True);c.run(e)
        for b in bindings:
            if b.role=='client_link':self.assertEqual(os.readlink(self.target(b)),old)
            else:self.assertFalse(self.target(b).is_symlink())
            self.assertEqual(os.readlink(next(Path(b.parent.path).glob('*.incoming'))),new)
        self.assertEqual(protected.read_bytes(),b'preserve-new-history');self.assert_pending('rollback_runtime')

    def test_explicit_recovery_restores_an_original_regular_cli_without_relaxing_forward_links(self):
        new=str(self.root/'new-runtime')
        bindings=tuple(self.binding(role=r,old=new,kind='file' if r=='client_link' else 'absent',
            value=b'original-reviewed-wrapper' if r=='client_link' else None) for r in ('gateway_link','serve_link','client_link'))
        client=bindings[-1]
        with self.assertRaises(ValueError):NativeFilePublication(self.plan,self.journal,'bind_cli',(client,),digest([client.evidence()]))
        c,e=self.begin('rollback_runtime',bindings,recovery=True);c.run(e)
        self.assertFalse(self.target(client).is_symlink());self.assertEqual(self.target(client).read_bytes(),b'original-reviewed-wrapper')
        self.assertEqual(self.target(client).stat().st_mode&0o777,0o755);self.assertEqual(os.readlink(next(Path(client.parent.path).glob('*.incoming'))),new)
        self.assert_pending('rollback_runtime')


    def missing(self,role='policy',*,kind='file',value=b'new-reviewed'):
        root=self.root/'existing-parent';root.mkdir(mode=0o700)
        return FileBinding(role,DirectoryCreation(anchor(root),'reviewed-child'),FileImage('absent'),kind,value)

    def test_new_parent_is_bound_and_published_before_its_exact_file(self):
        b=self.missing();c,e=self.begin('install_policy',(b,));c.run(e)
        self.assertEqual(self.target(b).read_bytes(),b'new-reviewed');self.assertEqual(Path(b.parent.path).stat().st_mode&0o777,0o700)
        self.assertIs(type(c.resolved_bindings[0].parent),DirectoryAnchor)
        phases=[r['phase'] for r in self.rows(c)];self.assertLess(phases.index('parent_observed'),phases.index('entry_intent'))
        self.assert_pending('install_policy')

    def test_arrived_parent_is_never_adopted_even_when_it_is_empty(self):
        b=self.missing();Path(b.parent.path).mkdir(mode=0o700);c,e=self.begin('install_policy',(b,))
        with self.assertRaises(Refused):c.run(e)
        self.assertEqual(list(Path(b.parent.path).iterdir()),[]);self.assertIsNone(c.last_receipt)

    def test_parent_creation_race_preserves_both_directories_without_file_writes(self):
        b=self.missing();c,e=self.begin('install_policy',(b,));rename=native._renameat2
        def race(fd,source,target,flags):
            if target==b.parent.name:
                path=Path(b.parent.path);path.mkdir(mode=0o700);(path/'unique').write_bytes(b'concurrent-directory-data')
            rename(fd,source,target,flags)
        with patch.object(native,'_renameat2',side_effect=race),self.assertRaises(Unknown):c.run(e)
        self.assertEqual((Path(b.parent.path)/'unique').read_bytes(),b'concurrent-directory-data');self.assertFalse(self.target(b).exists())
        retained=list(Path(b.parent.parent.path).glob('*.directory'));self.assertEqual(len(retained),1);self.assertEqual(list(retained[0].iterdir()),[])
        self.assert_pending('install_policy')

    def test_parent_symlink_arrival_does_not_redirect_file_creation(self):
        b=self.missing();protected=self.root/'protected';protected.mkdir(mode=0o700);c,e=self.begin('install_policy',(b,));rename=native._renameat2
        def race(fd,source,target,flags):
            if target==b.parent.name:Path(b.parent.path).symlink_to(protected,target_is_directory=True)
            rename(fd,source,target,flags)
        with patch.object(native,'_renameat2',side_effect=race),self.assertRaises(Unknown):c.run(e)
        self.assertEqual(list(protected.iterdir()),[]);self.assertTrue(Path(b.parent.path).is_symlink())

    def test_failure_after_parent_rename_retains_the_new_directory_and_pending_intent(self):
        b=self.missing();c,e=self.begin('install_policy',(b,));rename=native._renameat2
        def fail(fd,source,target,flags):rename(fd,source,target,flags);raise OSError('injected post-directory-rename error')
        with patch.object(native,'_renameat2',side_effect=fail),self.assertRaises(Unknown):c.run(e)
        self.assertTrue(Path(b.parent.path).is_dir());self.assertEqual(list(Path(b.parent.path).iterdir()),[])
        self.assertNotIn('parent_observed',[r['phase'] for r in self.rows(c)]);self.assert_pending('install_policy')
        replacement=NativeFilePublication(self.plan,self.journal,'install_policy',(b,),c.catalog)
        with self.assertRaises(Refused):replacement.run(e)

    def test_shared_new_parents_are_created_once_for_the_six_artifact_roles(self):
        root=self.root/'artifact-root';root.mkdir(mode=0o700);runtime=self.root/'runtime';runtime.mkdir(mode=0o700)
        systemd=DirectoryCreation(anchor(root),'systemd-generation');binary=DirectoryCreation(anchor(root),'bin-generation');bindings=[]
        for role in tuple(FILE_ROLES)[:6]:
            parent=systemd if role in ('source_gateway','source_serve') else binary if role.startswith('source_') else anchor(runtime)
            bindings.append(FileBinding(role,parent,FileImage('absent'),'file',b'reviewed-artifact'))
        c,e=self.begin('publish_reviewed',tuple(bindings));c.run(e)
        self.assertEqual(len([r for r in self.rows(c) if r['phase']=='parent_observed']),2)
        for b in bindings:self.assertEqual(self.target(b).read_bytes(),b'reviewed-artifact')
        self.assertTrue(all(type(b.parent) is DirectoryAnchor for b in c.resolved_bindings))

    def test_planned_safe_parent_mode_does_not_replace_actual_native_permission_proof(self):
        b=self.missing();root=Path(b.parent.parent.path);root.chmod(0o775)
        c,e=self.begin('install_policy',(b,))
        with self.assertRaises(Unknown):c.run(e)
        self.assertFalse(Path(b.parent.path).exists());self.assertEqual(root.stat().st_mode&0o777,0o775)

    def test_recovery_cannot_create_a_missing_parent_as_an_absence_shortcut(self):
        b=self.missing(kind='absent',value=None)
        with self.assertRaises(ValueError):NativeFilePublication(self.plan,self.journal,'rollback_policy',(b,),digest([b.evidence()]))
        self.assertFalse(Path(b.parent.path).exists())


if __name__=='__main__':unittest.main()
