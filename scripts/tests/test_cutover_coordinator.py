from dataclasses import replace
import hashlib
import importlib.util
import json
import math
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

for name in ('cutover_journal', 'cutover_coordinator'):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).resolve().parents[1] / (name+'.py'))
    module = importlib.util.module_from_spec(spec); sys.modules[name] = module; spec.loader.exec_module(module)
from cutover_journal import CutoverJournal, Refused, Unknown
from cutover_coordinator import Plan, ApprovedExecution, RecoveryCase, Observation, CutoverCoordinator, required_checks

P, A, S, R, Q, E = [c*64 for c in 'abcdef']
OP, REC = 'a'*32, 'b'*32
ORDER = ('preflight', 'backup_pre', 'pm_old', 'pm_new', 'gateway_drain', 'serve_retirement',
    'stop_gateway', 'stop_serve', 'secure_modes', 'publish_reviewed', 'install_policy',
    'bind_gateway', 'bind_serve', 'bind_cli', 'reload_units', 'start_gateway', 'start_serve', 'verify_new', 'backup_post')
RECOVERY_ORDER = ('recovery_preflight', 'recovery_backup', 'rollback_gateway_drain',
    'rollback_serve_retirement', 'rollback_stop_gateway', 'rollback_stop_serve', 'rollback_policy',
    'rollback_runtime', 'rollback_reload', 'rollback_start_gateway', 'rollback_start_serve', 'verify_rollback', 'recovery_backup_post')
READS = {'preflight', 'verify_new', 'recovery_preflight', 'verify_rollback'}
# Explicit synthetic SDK/manager seam. Real native adapters remain a separate gate.
FLAGS = '''authority_bound packet_bound source_inventory credentials_unchanged models_unchanged
data_preserved no_unknown_work work_zero native_owner_or_verified_absence step_preimage step_effect
fresh_backup_verified old_pm_ready new_pm_ready gateway_admission_closed serve_admission_closed
gateway_stopped old_incarnations_ended named_modes_ready artifacts_published policy_ready
gateway_ref_installed serve_ref_installed refs_installed loaded_units_ready gateway_ready serve_ready
new_health_verified post_backup_verified all_prior_effects_observed runtime_only_recovery no_work_replay
recovery_backup_verified gateway_quiescent serve_fenced_or_absent current_incarnations_ended
policy_exact_or_absent reviewed_policy_retired runtime_refs_restored loaded_old_units_ready rollback_health_verified'''.split()


def observation(**changes):
    row = dict(operation=OP, packet_sha256=P, authority_sha256=A, scope='forward', step='preflight', phase='before',
               observed_at=100.0, snapshot_sha256=S, evidence_sha256=E, checks=tuple((name, True) for name in FLAGS))
    row.update(changes); return Observation(**row)


class CoordinatorSchemaTests(unittest.TestCase):
    def test_forward_and_rollback_plans_must_be_distinct_hashes(self):
        with self.assertRaises(ValueError): Plan(P, A, S, P)
        with self.assertRaises(ValueError): Plan('secret', A, S, R)

    def test_execution_has_explicit_scope_receipt_and_finite_native_deadline(self):
        for field, value in [('deadline', True), ('deadline', math.inf), ('deadline', math.nan), ('scope', 'all'),
                             ('approval_receipt_sha256', None), ('operation', 'arbitrary')]:
            with self.subTest(field=field), self.assertRaises(ValueError):
                replace(ApprovedExecution(OP, P, A, 'forward', Q, 500.0), **{field:value})

    def test_durable_authorization_hash_binds_every_execution_field(self):
        original = ApprovedExecution(OP, P, A, 'forward', Q, 500.0)
        for field, value in [('operation', REC), ('packet_sha256', R), ('authority_sha256', E),
                             ('scope', 'recovery'), ('approval_receipt_sha256', E), ('deadline', 501.0)]:
            with self.subTest(field=field): self.assertNotEqual(original.binding_sha256, replace(original, **{field:value}).binding_sha256)

    def test_observation_rejects_bool_like_values_unknown_or_duplicate_checks(self):
        for checks in ((('work_zero', 1),), (('token', True),), (('work_zero', True), ('work_zero', False)), [('work_zero', True)]):
            with self.subTest(checks=checks), self.assertRaises(ValueError): observation(checks=checks)

    def test_observation_accepts_explicit_negative_check_for_a_held_verdict(self):
        self.assertEqual(observation(checks=(('work_zero', False),)).checks, (('work_zero', False),))

    def test_observation_clock_context_and_evidence_are_closed(self):
        for change in (dict(observed_at=True), dict(observed_at=-1), dict(step='shell'), dict(phase='ack'), dict(evidence_sha256='PRIVATE')):
            with self.subTest(change=change), self.assertRaises(ValueError): observation(**change)

    def test_recovery_case_requires_new_packet_and_exact_hashes(self):
        with self.assertRaises(ValueError): RecoveryCase(P, R, OP, P, E, P, Q, S)
        with self.assertRaises(ValueError): RecoveryCase(E, R, OP, P, 'partial', P, Q, S)

    def test_stop_requires_both_work_fences_and_old_pm(self):
        self.assertTrue({'gateway_admission_closed','serve_admission_closed','old_pm_ready'} <= required_checks('stop_gateway','before'))

    def test_reload_requires_ended_old_processes_and_reviewed_references(self):
        self.assertTrue({'old_incarnations_ended','refs_installed'} <= required_checks('reload_units','before'))

    def test_recovery_requires_read_effects_and_no_task_replay(self):
        self.assertTrue({'all_prior_effects_observed','runtime_only_recovery','no_work_replay'} <= required_checks('recovery_preflight','before'))

    def test_no_arbitrary_stage_or_response_phase_is_a_contract(self):
        for step, phase in [('shell','before'),('stop_gateway','ack')]:
            with self.assertRaises(ValueError): required_checks(step,phase)


class Clock:
    def __init__(self): self.value = 100.0
    def __call__(self): return self.value


class Operations:
    """Unit seam with actual private files; no real SDK/service effect claimed."""
    def __init__(self, root, clock, journal):
        self.root, self.clock, self.journal = root, clock, journal
        self.calls = []; self.observations = []; self.callback = None
        self.failure = None; self.omit = None; self.negative = None; self.context = None; self.time_offset = 0
        self.source = root/'source.fixture'; self.data = root/'data.fixture'
        self.source.write_bytes(b'reviewed source'); self.data.write_bytes(b'keep newer conversations')
        self.source_hash = hashlib.sha256(self.source.read_bytes()).hexdigest(); self.data_hash = hashlib.sha256(self.data.read_bytes()).hexdigest()
    def observe(self, step, phase, execution):
        self.observations.append((step,phase))
        flags = {name:True for name in FLAGS}
        flags['data_preserved'] = hashlib.sha256(self.data.read_bytes()).hexdigest()==self.data_hash
        flags['source_inventory'] = hashlib.sha256(self.source.read_bytes()).hexdigest()==self.source_hash
        if phase=='after' and step not in READS: flags['step_effect'] = (self.root/('effect-'+step)).is_file()
        if self.negative and self.negative[:2]==(step,phase): flags[self.negative[2]]=False
        proof = json.dumps(dict(step=step,phase=phase,execution=execution.binding_sha256,flags=flags),sort_keys=True).encode()
        proof_hash = hashlib.sha256(proof).hexdigest(); path=self.root/('proof-'+proof_hash)
        with path.open('wb') as out: out.write(proof); out.flush(); os.fsync(out.fileno())
        row = dict(operation=execution.operation,packet_sha256=execution.packet_sha256,authority_sha256=execution.authority_sha256,
                   scope=execution.scope,step=step,phase=phase,observed_at=self.clock.value+self.time_offset,
                   snapshot_sha256=S,evidence_sha256=proof_hash,checks=tuple(flags.items()))
        if self.context and self.context[:2]==(step,phase): row[self.context[2]]=self.context[3]
        return Observation(**row)
    def perform(self, step, execution):
        assert self.journal.view['authorization_sha256']==execution.binding_sha256
        assert self.journal.view['pending']==step
        self.calls.append(step)
        if self.callback: self.callback(step)
        if self.failure==(step,'before'): raise OSError('PRIVATE_FAILURE')
        if self.omit!=step:
            with (self.root/('effect-'+step)).open('xb') as f: f.write(b'actual unit-fixture effect'); f.flush(); os.fsync(f.fileno())
        if self.failure==(step,'after'): raise OSError('PRIVATE_FAILURE')
        return False  # An ACK/return value cannot establish a successful effect.


@unittest.skipUnless(os.name=='posix', 'Coordinator effects require the actual native exclusive-journal cohort')
class NativeCoordinatorTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(prefix='signal-grove-coordinator-'); self.addCleanup(self.temp.cleanup)
        self.base=Path(self.temp.name).resolve(); self.index=0
    def make(self):
        self.index+=1; root=self.base/str(self.index); root.mkdir(mode=0o700)
        journal=CutoverJournal(root,expected_identity=(root.stat().st_dev,root.stat().st_ino)); self.addCleanup(journal.close)
        clock=Clock(); operations=Operations(root,clock,journal); plan=Plan(P,A,S,R)
        coordinator=CutoverCoordinator(plan,journal,operations,now=clock)
        execution=ApprovedExecution(OP,P,A,'forward',Q,500.0)
        return coordinator,journal,operations,clock,execution
    def reopen(self, journal):
        root=journal._directory; journal.close()
        reopened=CutoverJournal(root,expected_identity=(root.stat().st_dev,root.stat().st_ino)); self.addCleanup(reopened.close)
        return reopened
    def case(self, journal):
        view=journal.view
        return RecoveryCase(E,R,view['operation'],view['plan_sha256'],journal.history_sha256,P,view['root_authorization_sha256'],S)
    def fail(self):
        c,j,o,t,e=self.make(); o.failure=('start_gateway','after')
        with self.assertRaises(Unknown): c.run(e)
        return c,j,o,t,e

    def test_forward_has_exact_required_order_and_proofs_after_actual_effects(self):
        c,j,o,t,e=self.make(); result=c.run(e)
        self.assertEqual(result['completed_steps'],list(ORDER)); self.assertEqual(o.calls,[s for s in ORDER if s not in READS])
        self.assertEqual(j.view['authorization_sha256'],e.binding_sha256)
        for row in j.view['records']:
            if row['kind']=='verified': self.assertTrue((o.root/('proof-'+row['evidence_sha256'])).is_file())
        self.assertEqual(o.data.read_bytes(),b'keep newer conversations')

    def test_busy_preflight_has_no_begin_or_action(self):
        c,j,o,t,e=self.make(); o.negative=('preflight','before','work_zero')
        with self.assertRaises(Refused): c.run(e)
        self.assertEqual(j.view['state'],'empty'); self.assertEqual(o.calls,[])

    def test_wrong_approval_target_scope_or_packet_never_dispatches(self):
        for change in (dict(authority_sha256=R),dict(packet_sha256=R),dict(scope='recovery')):
            c,j,o,t,e=self.make()
            with self.assertRaises(Refused): c.run(replace(e,**change))
            self.assertEqual(o.calls,[]); self.assertEqual(j.view['state'],'empty')

    def test_expired_or_unbounded_approval_never_dispatches(self):
        for deadline in (100.0,99.0,1900.01):
            c,j,o,t,e=self.make()
            with self.assertRaises(Refused): c.run(replace(e,deadline=deadline))
            self.assertEqual(o.observations,[])

    def test_approved_initial_snapshot_must_match_native_preimage(self):
        c,j,o,t,e=self.make(); o.context=('preflight','before','snapshot_sha256',E)
        with self.assertRaises(Refused): c.run(e)
        self.assertEqual(j.view['state'],'empty')

    def test_new_work_after_durable_intent_prevents_the_action(self):
        c,j,o,t,e=self.make(); original=j.intent
        def intent(step):
            original(step)
            if step=='stop_gateway': o.negative=(step,'before','work_zero')
        with patch.object(j,'intent',side_effect=intent), self.assertRaises(Unknown): c.run(e)
        self.assertNotIn('stop_gateway',o.calls); self.assertEqual(j.view['state'],'unknown')

    def test_slow_intent_expiring_approval_prevents_dispatch(self):
        c,j,o,t,e=self.make(); original=j.intent
        def intent(step):
            original(step)
            if step=='stop_gateway': t.value=e.deadline
        with patch.object(j,'intent',side_effect=intent), self.assertRaises(Unknown): c.run(e)
        self.assertNotIn('stop_gateway',o.calls)

    def test_failed_required_before_gate_stops_at_that_exact_stage(self):
        for step,key in [('gateway_drain','new_pm_ready'),('stop_gateway','serve_admission_closed'),
                         ('stop_serve','gateway_stopped'),('bind_gateway','old_incarnations_ended'),
                         ('reload_units','refs_installed'),('start_gateway','loaded_units_ready')]:
            with self.subTest(step=step):
                c,j,o,t,e=self.make(); o.negative=(step,'before',key)
                with self.assertRaises(Unknown): c.run(e)
                self.assertNotIn(step,o.calls)

    def test_action_error_before_or_after_effect_never_retries_or_auto_rolls_back(self):
        for timing in ('before','after'):
            c,j,o,t,e=self.make(); o.failure=('bind_serve',timing)
            with self.assertRaises(Unknown) as caught: c.run(e)
            self.assertNotIn('PRIVATE_FAILURE',str(caught.exception))
            self.assertEqual(o.calls.count('bind_serve'),1); self.assertNotIn('bind_cli',o.calls)
            self.assertFalse(any(s.startswith('rollback') for s in o.calls)); self.assertEqual(j.view['state'],'unknown')

    def test_success_return_without_observed_effect_cannot_complete(self):
        c,j,o,t,e=self.make(); o.omit='bind_cli'
        with self.assertRaises(Unknown): c.run(e)
        self.assertNotIn('reload_units',o.calls)

    def test_post_action_observation_context_must_still_match(self):
        c,j,o,t,e=self.make(); o.context=('bind_cli','after','authority_sha256',R)
        with self.assertRaises(Unknown): c.run(e)
        self.assertTrue((o.root/'effect-bind_cli').exists()); self.assertNotIn('reload_units',o.calls)

    def test_stale_or_future_observation_refuses_before_first_action(self):
        for offset in (-1.0,1.0):
            c,j,o,t,e=self.make(); o.time_offset=offset
            with self.assertRaises(Refused): c.run(e)
            self.assertEqual(o.calls,[])

    def test_backward_nonfinite_and_boolean_clocks_refuse(self):
        for value in (99.0,math.nan,True):
            c,j,o,t,e=self.make()
            def callback(step): t.value=value
            o.callback=callback
            with self.assertRaises(Unknown): c.run(e)
            self.assertEqual(o.calls,['backup_pre'])

    def test_deadline_after_effect_is_unknown_not_an_acknowledged_success(self):
        c,j,o,t,e=self.make(); o.callback=lambda step:setattr(t,'value',e.deadline)
        with self.assertRaises(Unknown): c.run(e)
        self.assertEqual(o.calls,['backup_pre']); self.assertTrue((o.root/'effect-backup_pre').exists())

    def test_journal_failure_prevents_dispatch(self):
        c,j,o,t,e=self.make(); original=j.intent
        def intent(step):
            if step=='stop_gateway': raise OSError('disk failure')
            original(step)
        with patch.object(j,'intent',side_effect=intent),self.assertRaises(Unknown): c.run(e)
        self.assertNotIn('stop_gateway',o.calls)

    def test_native_name_change_after_observation_prevents_dispatch(self):
        c,j,o,t,e=self.make(); original=j.intent
        def intent(step):
            original(step)
            if step=='stop_gateway':
                (o.root/'cutover.lock').rename(o.root/'saved-lock')
                (o.root/'cutover.lock').write_bytes(b''); (o.root/'cutover.lock').chmod(0o600)
        with patch.object(j,'intent',side_effect=intent),self.assertRaises(Unknown): c.run(e)
        self.assertNotIn('stop_gateway',o.calls)

    def test_interrupt_preserves_unknown_without_compensation(self):
        c,j,o,t,e=self.make()
        def callback(step): raise KeyboardInterrupt
        o.callback=callback
        with self.assertRaises(KeyboardInterrupt): c.run(e)
        self.assertEqual(j.view['state'],'unknown'); self.assertEqual(o.calls,['backup_pre'])

    def test_coordinator_instance_cannot_be_reused_or_reentered(self):
        c,j,o,t,e=self.make(); c.run(e); before=list(o.calls)
        with self.assertRaises(Refused): c.run(e)
        self.assertEqual(o.calls,before)

    def test_action_callback_cannot_reenter_the_running_coordinator(self):
        c,j,o,t,e=self.make(); denied=[]
        def callback(step):
            try: c.run(e)
            except Refused: denied.append(step)
            else: raise AssertionError('Reentrant dispatch admitted')
        o.callback=callback; c.run(e)
        self.assertEqual(denied,[s for s in ORDER if s not in READS])

    def test_every_action_failure_holds_at_one_dispatch_without_compensation(self):
        for step in ORDER:
            if step in READS: continue
            with self.subTest(step=step):
                c,j,o,t,e=self.make(); o.failure=(step,'after')
                with self.assertRaises(Unknown): c.run(e)
                self.assertEqual(o.calls.count(step),1)
                self.assertEqual(o.calls[-1],step); self.assertEqual(j.view['state'],'unknown')

    def test_model_conservation_failure_after_pm_blocks_drain_and_stop(self):
        c,j,o,t,e=self.make(); o.negative=('pm_new','after','models_unchanged')
        with self.assertRaises(Unknown): c.run(e)
        self.assertNotIn('gateway_drain',o.calls); self.assertNotIn('stop_gateway',o.calls)

    def test_reopen_unknown_refuses_new_forward_work(self):
        c,j,o,t,e=self.fail(); new=self.reopen(j); before=list(o.calls)
        other=CutoverCoordinator(c.plan,new,o,now=t)
        with self.assertRaises(Refused): other.run(replace(e,operation=REC))
        self.assertEqual(o.calls,before)

    def test_approved_recovery_is_separate_and_does_not_repeat_forward_work(self):
        c,j,o,t,e=self.fail(); before=(o.root/'journal.jsonl').read_bytes(); new=self.reopen(j)
        o.journal=new; o.calls=[]; o.failure=None
        other=CutoverCoordinator(c.plan,new,o,now=t); case=self.case(new); approval=ApprovedExecution(REC,E,A,'recovery',Q,500.0)
        result=other.recover(approval,case)
        self.assertEqual(result['completed_steps'],list(RECOVERY_ORDER)); self.assertEqual(o.calls,[s for s in RECOVERY_ORDER if s not in READS])
        self.assertTrue((o.root/'journal.jsonl').read_bytes().startswith(before)); self.assertEqual(o.data.read_bytes(),b'keep newer conversations')
        self.assertEqual(new.view['root_authorization_sha256'],e.binding_sha256)
        self.assertEqual(new.view['authorization_sha256'],approval.binding_sha256)

    def test_recovery_requires_matching_current_history_root_and_rollback_packet(self):
        for change in (dict(history_sha256=R),dict(original_operation=REC),dict(root_authorization_sha256=R),dict(rollback_packet_sha256=E)):
            c,j,o,t,e=self.fail(); new=self.reopen(j); o.journal=new; o.calls=[]; case=replace(self.case(new),**change)
            other=CutoverCoordinator(c.plan,new,o,now=t)
            with self.assertRaises(Refused): other.recover(ApprovedExecution(REC,E,A,'recovery',Q,500.0),case)
            self.assertEqual(o.calls,[])

    def test_recovery_requires_fresh_read_only_native_effects_before_new_record(self):
        c,j,o,t,e=self.fail(); new=self.reopen(j); o.journal=new; o.calls=[]
        before=(o.root/'journal.jsonl').read_bytes(); o.negative=('recovery_preflight','before','all_prior_effects_observed')
        other=CutoverCoordinator(c.plan,new,o,now=t)
        with self.assertRaises(Refused): other.recover(ApprovedExecution(REC,E,A,'recovery',Q,500.0),self.case(new))
        self.assertEqual((o.root/'journal.jsonl').read_bytes(),before); self.assertEqual(o.calls,[])

    def test_recovery_cannot_infer_permission_from_forward_approval(self):
        c,j,o,t,e=self.fail(); new=self.reopen(j); o.journal=new; o.calls=[]
        other=CutoverCoordinator(c.plan,new,o,now=t)
        with self.assertRaises(Refused): other.recover(replace(e,operation=REC),self.case(new))
        self.assertEqual(o.calls,[])

    def test_failed_recovery_stays_unknown_and_never_resumes(self):
        c,j,o,t,e=self.fail(); new=self.reopen(j); o.journal=new; o.calls=[]; o.failure=('rollback_runtime','after')
        other=CutoverCoordinator(c.plan,new,o,now=t)
        with self.assertRaises(Unknown): other.recover(ApprovedExecution(REC,E,A,'recovery',Q,500.0),self.case(new))
        self.assertEqual(o.calls.count('rollback_runtime'),1); self.assertNotIn('rollback_reload',o.calls)
        self.assertEqual(new.view['state'],'unknown'); self.assertEqual(new.view['root_operation'],OP)

    def test_runtime_only_and_no_replay_proofs_remain_required_mid_recovery(self):
        c,j,o,t,e=self.fail(); new=self.reopen(j); o.journal=new; o.calls=[]; o.failure=None
        o.negative=('rollback_reload','before','no_work_replay')
        other=CutoverCoordinator(c.plan,new,o,now=t)
        with self.assertRaises(Unknown): other.recover(ApprovedExecution(REC,E,A,'recovery',Q,500.0),self.case(new))
        self.assertNotIn('rollback_reload',o.calls); self.assertEqual(new.view['state'],'unknown')

    def test_recovery_model_or_data_drift_prevents_any_compensation(self):
        c,j,o,t,e=self.fail(); new=self.reopen(j); o.journal=new; o.calls=[]; o.data.write_bytes(b'new data after failure')
        other=CutoverCoordinator(c.plan,new,o,now=t)
        with self.assertRaises(Refused): other.recover(ApprovedExecution(REC,E,A,'recovery',Q,500.0),self.case(new))
        self.assertEqual(o.calls,[]); self.assertEqual(o.data.read_bytes(),b'new data after failure')

    def test_post_backup_failure_does_not_claim_complete_or_restore_data(self):
        c,j,o,t,e=self.make(); o.failure=('backup_post','after')
        with self.assertRaises(Unknown): c.run(e)
        self.assertEqual(j.view['state'],'unknown'); self.assertFalse(any(s.startswith('rollback') for s in o.calls))
        self.assertEqual(o.data.read_bytes(),b'keep newer conversations')


if __name__=='__main__': unittest.main()
