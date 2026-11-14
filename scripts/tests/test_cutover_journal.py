from pathlib import Path
import importlib.util
import hashlib
import json
import os
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('cutover_journal', Path(__file__).resolve().parents[1] / 'cutover_journal.py')
module = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = module
spec.loader.exec_module(module)
from cutover_journal import CutoverJournal, Refused, Unknown, inspect_bytes, MAX_BYTES, MAX_RECORDS

OP = 'a' * 32
PLAN = 'b' * 64
PROOF = 'c' * 64


def encoded(*events):
    result = []
    for seq, event in enumerate(events, 1):
        row = dict(schema=1, seq=seq, operation=OP, plan_sha256=PLAN)
        row.update(event)
        result.append(json.dumps(row, separators=(',', ':')).encode() + b'\n')
    return b''.join(result)


def recovery_event(prefix, **changes):
    row = dict(kind='recovery_begin', operation='d'*32, plan_sha256='e'*64,
               original_operation=OP, original_plan_sha256=PLAN,
               history_sha256=hashlib.sha256(prefix).hexdigest(), evidence_sha256=PROOF)
    row.update(changes)
    return row


BEGIN = dict(kind='begin')
INTENT = dict(kind='intent', step='preflight')
VERIFIED = dict(kind='verified', step='preflight', evidence_sha256=PROOF)
COMPLETE = dict(kind='complete', evidence_sha256=PROOF)


class JournalParserTests(unittest.TestCase):
    def test_authorization_binding_survives_separate_recovery_without_replacing_root(self):
        before = encoded(dict(BEGIN, authorization_sha256='f'*64), INTENT)
        view = inspect_bytes(before + encoded(recovery_event(before, seq=3, authorization_sha256='a'*64)))
        self.assertEqual(view['authorization_sha256'], 'a'*64)
        self.assertEqual(view['root_authorization_sha256'], 'f'*64)

    def test_optional_authorization_field_refuses_nonhash_values(self):
        for value in (True, None, 'private', 'f'*63):
            with self.subTest(value=value), self.assertRaises(Unknown):
                inspect_bytes(encoded(dict(BEGIN, authorization_sha256=value)))

    def test_authorization_cannot_be_switched_mid_operation(self):
        with self.assertRaises(Unknown):
            inspect_bytes(encoded(dict(BEGIN, authorization_sha256='f'*64), dict(INTENT, authorization_sha256='a'*64)))
    def test_explicit_recovery_binds_exact_original_history_and_root(self):
        before = encoded(BEGIN, INTENT)
        view = inspect_bytes(before + encoded(recovery_event(before, seq=3)))
        self.assertEqual(view['mode'], 'recovery')
        self.assertEqual(view['root_operation'], OP)
        self.assertEqual(view['root_plan_sha256'], PLAN)
        self.assertEqual(view['recovery_origin']['history_sha256'], hashlib.sha256(before).hexdigest())

    def test_recovery_refuses_stale_history_or_wrong_original_identity(self):
        before = encoded(BEGIN, INTENT)
        for change in (dict(history_sha256='f'*64), dict(original_operation='f'*32), dict(original_plan_sha256='f'*64), dict(evidence_sha256='private')):
            with self.subTest(change=change), self.assertRaises(Unknown):
                inspect_bytes(before + encoded(recovery_event(before, seq=3, **change)))

    def test_recovery_cannot_replace_empty_or_completed_history(self):
        for before in (b'', encoded(BEGIN, INTENT, VERIFIED, COMPLETE)):
            with self.subTest(before=before), self.assertRaises(Unknown):
                inspect_bytes(before + encoded(recovery_event(before, seq=len(before.splitlines())+1)))

    def test_recovery_never_reenables_forward_steps(self):
        before = encoded(BEGIN, INTENT)
        row = recovery_event(before, seq=3)
        with self.assertRaises(Unknown):
            inspect_bytes(before + encoded(row) + encoded(dict(INTENT, seq=4, operation='d'*32, plan_sha256='e'*64)))

    def test_forward_operation_cannot_dispatch_recovery_steps(self):
        with self.assertRaises(Unknown):
            inspect_bytes(encoded(BEGIN, dict(INTENT, step='rollback_runtime')))

    def test_recovery_bytes_hash_preserves_crlf_in_original_receipt(self):
        before = encoded(BEGIN, INTENT).replace(b'\n', b'\r\n')
        self.assertEqual(inspect_bytes(before + encoded(recovery_event(before, seq=3)))['mode'], 'recovery')

    def test_second_explicit_recovery_retains_the_original_root(self):
        before = encoded(BEGIN, INTENT)
        first = before + encoded(recovery_event(before, seq=3)) + encoded(dict(kind='unknown', seq=4, operation='d'*32, plan_sha256='e'*64))
        second = recovery_event(first, seq=5, operation='f'*32, plan_sha256='a'*64, original_operation='d'*32, original_plan_sha256='e'*64)
        view = inspect_bytes(first + encoded(second))
        self.assertEqual(view['root_operation'], OP)
        self.assertEqual(view['recovery_origin']['operation'], 'd'*32)

    def test_recovery_cannot_reuse_any_prior_operation_identity(self):
        before = encoded(BEGIN, INTENT)
        with self.assertRaises(Unknown): inspect_bytes(before + encoded(recovery_event(before, seq=3, operation=OP)))
    def test_empty_does_not_claim_external_acceptance(self):
        self.assertEqual(inspect_bytes(b'')['state'], 'empty')

    def test_full_history_is_complete_but_only_records_evidence_hashes(self):
        view = inspect_bytes(encoded(BEGIN, INTENT, VERIFIED, COMPLETE))
        self.assertEqual(view['state'], 'complete')
        self.assertFalse(view['recovery_required'])
        self.assertEqual(view['records'][2]['evidence_sha256'], PROOF)

    def test_unfinished_or_unknown_always_requires_recovery(self):
        for events in [(BEGIN,), (BEGIN, INTENT), (BEGIN, INTENT, VERIFIED), (BEGIN, dict(kind='unknown'))]:
            with self.subTest(events=events):
                self.assertTrue(inspect_bytes(encoded(*events))['recovery_required'])

    def test_extra_credential_or_command_fields_refuse_without_echo(self):
        for key in ('token', 'password', 'command', 'pid', 'note'):
            with self.subTest(key=key), self.assertRaises(Unknown) as caught:
                inspect_bytes(encoded(dict(BEGIN, **{key: 'PRIVATE_VALUE'})))
            self.assertNotIn('PRIVATE_VALUE', str(caught.exception))

    def test_duplicate_json_keys_refuse(self):
        with self.assertRaises(Unknown):
            inspect_bytes(encoded(BEGIN).replace(b'"schema":1', b'"schema":1,"schema":1'))

    def test_partial_bad_utf8_empty_and_oversized_lines_refuse(self):
        for data in (encoded(BEGIN)[:-1], b'\xff\n', b'\n', b'x' * 1025 + b'\n', b'x' * (MAX_BYTES + 1)):
            with self.subTest(size=len(data)), self.assertRaises(Unknown):
                inspect_bytes(data)

    def test_record_count_is_bounded(self):
        with self.assertRaises(Unknown):
            inspect_bytes(b'{}\n' * (MAX_RECORDS + 1))

    def test_sequence_schema_and_hash_types_refuse(self):
        for field, value in [('seq', True), ('seq', 2), ('schema', True), ('operation', 'x'), ('plan_sha256', 'z' * 64)]:
            with self.subTest(field=field, value=value), self.assertRaises(Unknown):
                inspect_bytes(encoded(dict(BEGIN, **{field: value})))

    def test_plan_and_operation_cannot_change_mid_operation(self):
        for row in (dict(INTENT, operation='d' * 32), dict(INTENT, plan_sha256='e' * 64)):
            with self.subTest(row=row), self.assertRaises(Unknown):
                inspect_bytes(encoded(BEGIN, row))

    def test_verification_requires_exact_pending_step(self):
        for events in ((BEGIN, VERIFIED), (BEGIN, INTENT, dict(VERIFIED, step='stop_old'))):
            with self.subTest(events=events), self.assertRaises(Unknown):
                inspect_bytes(encoded(*events))

    def test_step_cannot_be_replayed_even_after_verification(self):
        with self.assertRaises(Unknown):
            inspect_bytes(encoded(BEGIN, INTENT, VERIFIED, INTENT))

    def test_new_begin_cannot_discard_pending_or_unknown_work(self):
        for events in ((BEGIN, INTENT), (BEGIN, dict(kind='unknown'))):
            with self.subTest(events=events), self.assertRaises(Unknown):
                inspect_bytes(encoded(*events, dict(BEGIN, operation='d' * 32)))

    def test_completion_requires_verified_nonempty_work(self):
        for events in ((BEGIN, COMPLETE), (BEGIN, INTENT, COMPLETE), (COMPLETE,)):
            with self.subTest(events=events), self.assertRaises(Unknown):
                inspect_bytes(encoded(*events))

    def test_operation_identity_cannot_be_reused_after_completion(self):
        with self.assertRaises(Unknown):
            inspect_bytes(encoded(BEGIN, INTENT, VERIFIED, COMPLETE, BEGIN))

    def test_only_closed_step_names_and_proof_hashes_are_accepted(self):
        for row in (dict(INTENT, step='shell'), dict(VERIFIED, evidence_sha256='SECRET'), dict(kind='retry')):
            with self.subTest(row=row), self.assertRaises(Unknown):
                inspect_bytes(encoded(*([BEGIN, INTENT, row] if row.get('kind') == 'verified' else [BEGIN, row])))


@unittest.skipUnless(os.name == 'posix', 'Actual POSIX flock/inode/durability checks require the native Linux cohort')
class NativeJournalTests(unittest.TestCase):
    def test_dispatch_claim_is_shared_by_replacement_adapters_and_never_released(self):
        journal=self.opened();journal.begin(OP,PLAN,authorization_sha256=PROOF);journal.intent('stop_gateway')
        before=(self.root/'journal.jsonl').read_bytes()
        journal.claim_dispatch(OP,'stop_gateway',PROOF)
        other_adapter_journal=journal
        with self.assertRaises(Refused):other_adapter_journal.claim_dispatch(OP,'stop_gateway',PROOF)
        self.assertEqual((self.root/'journal.jsonl').read_bytes(),before)
        journal.verified('stop_gateway',PROOF);journal.intent('stop_serve')
        journal.claim_dispatch(OP,'stop_serve',PROOF)
        with self.assertRaises(Refused):journal.claim_dispatch(OP,'stop_serve',PROOF)

    def test_reopened_pending_intent_cannot_dispatch_even_if_original_never_claimed(self):
        with self.opened() as journal:
            journal.begin(OP,PLAN,authorization_sha256=PROOF);journal.intent('stop_gateway')
        before=(self.root/'journal.jsonl').read_bytes()
        with self.opened() as journal:
            with self.assertRaises(Refused):journal.claim_dispatch(OP,'stop_gateway',PROOF)
            self.assertEqual(journal.view['pending'],'stop_gateway')
        self.assertEqual((self.root/'journal.jsonl').read_bytes(),before)

    def test_dispatch_claim_requires_exact_authorization_operation_and_current_step(self):
        journal=self.opened();journal.begin(OP,PLAN,authorization_sha256=PROOF);journal.intent('stop_gateway')
        for args in ((OP,'stop_gateway',None),(OP,'stop_gateway',True),(OP,'stop_gateway','d'*64),
                     ('d'*32,'stop_gateway',PROOF),(OP,'stop_serve',PROOF)):
            with self.subTest(args=args),self.assertRaises(Refused):journal.claim_dispatch(*args)
        journal.claim_dispatch(OP,'stop_gateway',PROOF);journal.unknown()
        with self.assertRaises(Refused):journal.claim_dispatch(OP,'stop_gateway',PROOF)

    def test_forked_native_process_cannot_claim_original_parent_intent(self):
        journal=self.opened();journal.begin(OP,PLAN,authorization_sha256=PROOF);journal.intent('stop_gateway')
        read_fd,write_fd=os.pipe();child=os.fork()
        if child==0:
            os.close(read_fd)
            try:
                try:journal.claim_dispatch(OP,'stop_gateway',PROOF)
                except Refused:os.write(write_fd,b'refused')
                else:os.write(write_fd,b'unsafe')
            finally:
                os.close(write_fd);os._exit(0)
        os.close(write_fd)
        try:
            import select
            self.assertTrue(select.select([read_fd],[],[],3)[0])
            self.assertEqual(os.read(read_fd,32),b'refused')
        finally:
            os.close(read_fd)
            _,status=os.waitpid(child,0)
        self.assertEqual(status,0)
        journal.claim_dispatch(OP,'stop_gateway',PROOF)

    def test_explicit_recovery_op_can_only_append_recovery_evidence(self):
        with self.opened() as journal:
            journal.begin(OP, PLAN); journal.intent('stop_old')
        with self.opened() as journal:
            before = (self.root/'journal.jsonl').read_bytes()
            journal.begin_recovery('d'*32, 'e'*64, original_operation=OP, original_plan_sha256=PLAN,
                                   history_sha256=hashlib.sha256(before).hexdigest(), evidence_sha256=PROOF)
            journal.intent('recovery_preflight'); journal.verified('recovery_preflight', PROOF)
            journal.complete(PROOF)
            self.assertTrue((self.root/'journal.jsonl').read_bytes().startswith(before))
            self.assertEqual(journal.view['mode'], 'recovery')

    def test_recovery_does_not_bypass_current_inode_or_content_fence(self):
        journal = self.opened(); journal.begin(OP, PLAN); journal.intent('stop_old')
        digest = journal.history_sha256
        with (self.root/'journal.jsonl').open('ab') as f: f.write(b'changed\n')
        with self.assertRaises(Unknown): journal.begin_recovery('d'*32, 'e'*64,
            original_operation=OP, original_plan_sha256=PLAN, history_sha256=digest, evidence_sha256=PROOF)

    def test_recovery_with_wrong_history_preserves_original_bytes(self):
        with self.opened() as journal:
            journal.begin(OP, PLAN); journal.intent('stop_old')
        before = (self.root/'journal.jsonl').read_bytes()
        with self.opened() as journal:
            with self.assertRaises(Unknown): journal.begin_recovery('d'*32, 'e'*64,
                original_operation=OP, original_plan_sha256=PLAN, history_sha256='f'*64, evidence_sha256=PROOF)
        self.assertEqual((self.root/'journal.jsonl').read_bytes(), before)

    def test_assert_current_detects_external_change_and_poison(self):
        journal = self.opened(); journal.begin(OP, PLAN); journal.assert_current()
        with (self.root/'journal.jsonl').open('ab') as f: f.write(b'changed\n')
        with self.assertRaises(Unknown): journal.assert_current()
        self.assertTrue(journal.view['durability_uncertain'])
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='signal-grove-journal-')
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name).resolve()
        self.root = self.base / 'private'
        self.root.mkdir(mode=0o700)
        self.identity = (self.root.stat().st_dev, self.root.stat().st_ino)

    def opened(self):
        journal = CutoverJournal(self.root, expected_identity=self.identity)
        self.addCleanup(journal.close)
        return journal

    def test_completed_operations_append_without_rewriting_previous_bytes(self):
        with self.opened() as journal:
            journal.begin(OP, PLAN); journal.intent('preflight'); journal.verified('preflight', PROOF); journal.complete(PROOF)
        before = (self.root/'journal.jsonl').read_bytes()
        with self.opened() as journal:
            journal.begin('d'*32, 'e'*64); journal.intent('verify_new'); journal.verified('verify_new', PROOF); journal.complete(PROOF)
            self.assertEqual(journal.view['state'], 'complete')
        self.assertTrue((self.root/'journal.jsonl').read_bytes().startswith(before))

    def test_same_process_contender_cannot_acquire_the_lock(self):
        self.opened()
        with self.assertRaises(Refused):
            CutoverJournal(self.root, expected_identity=self.identity)

    def test_other_process_contender_is_refused_by_actual_kernel_lock(self):
        self.opened()
        code = "import sys;sys.path.insert(0,sys.argv[1]);from cutover_journal import CutoverJournal,Refused\ntry: CutoverJournal(sys.argv[2],expected_identity=tuple(map(int,sys.argv[3:5])))\nexcept Refused: print('LOCK_REFUSED')\nelse: raise AssertionError('Concurrent lock admitted')"
        result = subprocess.run([sys.executable, '-I', '-B', '-c', code, str(Path(__file__).resolve().parent.parent), str(self.root), *map(str, self.identity)], capture_output=True, timeout=10, check=True)
        self.assertEqual(result.stdout.strip(), b'LOCK_REFUSED')

    def test_close_reopen_preserves_unfinished_intent_and_forbids_new_begin(self):
        with self.opened() as journal:
            journal.begin(OP, PLAN); journal.intent('stop_old')
        before = (self.root/'journal.jsonl').read_bytes()
        with self.opened() as journal:
            self.assertEqual(journal.view['pending'], 'stop_old')
            with self.assertRaises(Unknown): journal.begin('d'*32, PLAN)
        self.assertEqual((self.root/'journal.jsonl').read_bytes(), before)

    def test_explicit_unknown_is_terminal_without_replay_or_reset(self):
        with self.opened() as journal:
            journal.begin(OP, PLAN); journal.unknown()
            with self.assertRaises(Unknown): journal.intent('preflight')
        with self.opened() as journal:
            self.assertEqual(journal.view['state'], 'unknown')
            with self.assertRaises(Unknown): journal.begin('d'*32, PLAN)

    def test_reopening_does_not_grant_any_step_or_verification_authority(self):
        with self.opened() as journal:
            journal.begin(OP, PLAN); journal.intent('stop_old')
        before = (self.root/'journal.jsonl').read_bytes()
        with self.opened() as journal:
            for action in (lambda: journal.intent('start_new'), lambda: journal.verified('stop_old', PROOF),
                           lambda: journal.complete(PROOF), journal.unknown):
                with self.assertRaises(Unknown): action()
        self.assertEqual((self.root/'journal.jsonl').read_bytes(), before)

    def test_view_is_detached_from_journal_state(self):
        journal = self.opened(); journal.begin(OP, PLAN)
        journal.view['records'][0]['plan_sha256'] = 'f'*64
        journal.intent('preflight')
        self.assertEqual(journal.view['plan_sha256'], PLAN)

    def test_group_writable_private_root_is_refused_without_permission_repair(self):
        self.root.chmod(0o775)
        with self.assertRaises(Refused): CutoverJournal(self.root, expected_identity=self.identity)
        self.assertEqual(self.root.stat().st_mode & 0o777, 0o775)

    def test_symlink_root_is_refused(self):
        link = self.base/'alias'; link.symlink_to(self.root, target_is_directory=True)
        with self.assertRaises(Refused): CutoverJournal(link, expected_identity=self.identity)

    def test_unsafe_lock_mode_is_refused_without_chmod(self):
        lock = self.root/'cutover.lock'; lock.write_bytes(b''); lock.chmod(0o644)
        with self.assertRaises(Refused): CutoverJournal(self.root, expected_identity=self.identity)
        self.assertEqual(lock.stat().st_mode & 0o777, 0o644)

    def test_hardlinked_lock_is_refused(self):
        lock = self.root/'cutover.lock'; lock.write_bytes(b''); lock.chmod(0o600)
        os.link(lock, self.base/'other-lock')
        with self.assertRaises(Refused): CutoverJournal(self.root, expected_identity=self.identity)

    def test_journal_symlink_cannot_touch_its_target(self):
        target = self.base/'outside'; target.write_bytes(b'protected')
        (self.root/'journal.jsonl').symlink_to(target)
        with self.assertRaises(Refused): CutoverJournal(self.root, expected_identity=self.identity)
        self.assertEqual(target.read_bytes(), b'protected')

    def test_corrupt_existing_journal_is_never_truncated_or_repaired(self):
        target = self.root/'journal.jsonl'; target.write_bytes(b'partial'); target.chmod(0o600)
        with self.assertRaises(Unknown): CutoverJournal(self.root, expected_identity=self.identity)
        self.assertEqual(target.read_bytes(), b'partial')

    def test_external_content_change_poison_preserves_external_bytes(self):
        journal = self.opened(); journal.begin(OP, PLAN)
        with (self.root/'journal.jsonl').open('ab') as f: f.write(b'outside\n')
        before = (self.root/'journal.jsonl').read_bytes()
        with self.assertRaises(Unknown): journal.intent('preflight')
        self.assertTrue(journal.view['durability_uncertain'])
        with self.assertRaises(Unknown): journal.intent('backup_pre')
        self.assertEqual((self.root/'journal.jsonl').read_bytes(), before)

    def test_lock_name_replacement_poison_refuses_dispatch(self):
        journal = self.opened(); journal.begin(OP, PLAN)
        (self.root/'cutover.lock').rename(self.root/'saved-lock')
        (self.root/'cutover.lock').write_bytes(b''); (self.root/'cutover.lock').chmod(0o600)
        with self.assertRaises(Unknown): journal.intent('stop_old')

    def test_journal_name_replacement_is_not_followed(self):
        journal = self.opened(); journal.begin(OP, PLAN)
        (self.root/'journal.jsonl').rename(self.root/'saved-journal')
        (self.root/'journal.jsonl').write_bytes(b''); (self.root/'journal.jsonl').chmod(0o600)
        with self.assertRaises(Unknown): journal.intent('stop_old')
        self.assertEqual((self.root/'journal.jsonl').read_bytes(), b'')

    def test_directory_replacement_does_not_redirect_writes(self):
        journal = self.opened(); journal.begin(OP, PLAN)
        self.root.rename(self.base/'saved'); self.root.mkdir(mode=0o700)
        with self.assertRaises(Unknown): journal.intent('stop_old')
        self.assertEqual(list(self.root.iterdir()), [])

    def test_replaced_root_cannot_create_a_second_journal_owner(self):
        self.opened()
        self.root.rename(self.base/'saved'); self.root.mkdir(mode=0o700)
        with self.assertRaises(Refused): CutoverJournal(self.root, expected_identity=self.identity)
        self.assertEqual(list(self.root.iterdir()), [])

    def test_unobserved_or_boolean_native_identity_is_refused_before_files(self):
        for identity in ((True, 1), (1, False), (-1, 1), (1, 0), [1, 1], None):
            with self.subTest(identity=identity), self.assertRaises(Refused):
                CutoverJournal(self.root, expected_identity=identity)
        self.assertEqual(list(self.root.iterdir()), [])

    def test_fsync_error_poison_holds_valid_pending_bytes(self):
        journal = self.opened(); journal.begin(OP, PLAN)
        with patch('cutover_journal.os.fsync', side_effect=OSError('PRIVATE_VALUE')):
            with self.assertRaises(Unknown) as caught: journal.intent('stop_old')
        self.assertNotIn('PRIVATE_VALUE', str(caught.exception))
        self.assertTrue(journal.view['durability_uncertain'])
        self.assertEqual(inspect_bytes((self.root/'journal.jsonl').read_bytes())['pending'], 'stop_old')
        with self.assertRaises(Unknown): journal.verified('stop_old', PROOF)

    def test_interrupt_poison_cannot_be_treated_as_safe_retry(self):
        journal = self.opened(); journal.begin(OP, PLAN)
        with patch('cutover_journal.os.fsync', side_effect=KeyboardInterrupt):
            with self.assertRaises(KeyboardInterrupt): journal.intent('stop_old')
        with self.assertRaises(Unknown): journal.intent('stop_old')

    def test_short_writes_complete_then_fsync_before_return(self):
        journal = self.opened(); journal.begin(OP, PLAN); write = os.write; fsync = os.fsync; events = []
        def short(fd, data): events.append('write'); return write(fd, data[:7])
        def sync(fd): events.append('fsync'); return fsync(fd)
        with patch('cutover_journal.os.write', side_effect=short), patch('cutover_journal.os.fsync', side_effect=sync): journal.intent('stop_old')
        self.assertGreater(events.count('write'), 1); self.assertEqual(events[-1], 'fsync')
        self.assertEqual(inspect_bytes((self.root/'journal.jsonl').read_bytes())['pending'], 'stop_old')

    def test_partial_write_error_preserves_unparseable_bytes(self):
        journal = self.opened(); journal.begin(OP, PLAN); write = os.write; before = (self.root/'journal.jsonl').read_bytes(); calls = []
        def fail(fd, data):
            if calls: raise OSError('interrupted IO')
            calls.append(1); return write(fd, data[:9])
        with patch('cutover_journal.os.write', side_effect=fail), self.assertRaises(Unknown): journal.intent('stop_old')
        self.assertEqual((self.root/'journal.jsonl').read_bytes(), before + b'{"kind":"')
        with self.assertRaises(Unknown): inspect_bytes((self.root/'journal.jsonl').read_bytes())

    def test_zero_write_and_closed_instances_refuse(self):
        journal = self.opened(); journal.begin(OP, PLAN)
        with patch('cutover_journal.os.write', return_value=0), self.assertRaises(Unknown): journal.intent('preflight')
        journal.close()
        with self.assertRaises(Unknown): journal.intent('stop_old')


if __name__ == '__main__':
    unittest.main()
