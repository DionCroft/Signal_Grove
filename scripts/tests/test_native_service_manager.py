from dataclasses import replace
import importlib.util
from pathlib import Path
import socket
import sys
import threading
import time
import unittest
from unittest.mock import Mock

for name in ('retirement_guard','native_retirement_owner','cutover_journal','cutover_coordinator','native_service_manager'):
    spec=importlib.util.spec_from_file_location(name,Path(__file__).resolve().parents[1]/(name+'.py'))
    module=importlib.util.module_from_spec(spec);sys.modules[name]=module;spec.loader.exec_module(module)
from retirement_guard import Owner
from cutover_coordinator import ApprovedExecution, Plan, FORWARD, RECOVERY
from cutover_journal import CutoverJournal,Refused,Unknown
from native_service_manager import NativeServiceManager
from native_service_manager import BusAuthority,UnitBinding,UnitSnapshot,NativeAttempt,command_rows,invocation_id,job_result,validate_preimage,_Deadline,_LimitedSocket,_Signals

BOOT='00000000-0000-4000-8000-000000000001'
OWNER=Owner(4000,500,BOOT)
OLD,NEW='a'*64,'b'*64
BIND=UnitBinding('gateway','hermes-gateway.service',OLD,NEW,'/old/python','/new/python')


def active(**changes):
    row=dict(unit='hermes-gateway.service',load_state='loaded',active_state='active',sub_state='running',main_pid=4000,control_pid=0,
             invocation='1'*32,cgroup='/user.slice/hermes-gateway.service',job_id=0,job_path='/',result='success',
             configuration_sha256=OLD,owner=OWNER,executable='/old/python',stop_helpers_ok=False,needs_daemon_reload=False)
    row.update(changes);return UnitSnapshot(**row)


def inactive(**changes):
    return replace(active(),active_state='inactive',sub_state='dead',main_pid=0,owner=None,executable='',configuration_sha256=NEW,**changes)


class NativeReloadBoundaryTests(unittest.TestCase):
    def setup_reload(self, scope="forward"):
        plan = Plan("1" * 64, "2" * 64, "3" * 64, "4" * 64)
        recovery = scope == "recovery"
        step, stages = ("rollback_reload", RECOVERY) if recovery else ("reload_units", FORWARD)
        approval = ApprovedExecution("5" * 32, plan.rollback_packet_sha256 if recovery else plan.packet_sha256,
                                     plan.authority_sha256, scope, "6" * 64, time.monotonic() + 60)
        prefix = stages[:stages.index(step)]
        view = dict(state="pending", pending=step, operation=approval.operation,
                    plan_sha256=approval.packet_sha256, authorization_sha256=approval.binding_sha256,
                    root_plan_sha256=plan.packet_sha256, mode=scope,
                    records=[dict(operation=approval.operation, kind=kind, step=item)
                             for item in prefix for kind in ("intent", "verified")]
                            + [dict(operation=approval.operation, kind="intent", step=step)])
        actor = NativeServiceManager.__new__(NativeServiceManager)
        serve = UnitBinding("serve", "hermes-serve.service", "c" * 64, "d" * 64, "/old/python", "/new/python")
        actor.plan, actor.journal, actor.bindings = plan, Mock(spec=CutoverJournal, view=view), {"gateway": BIND, "serve": serve}
        actor._busy, actor._spent = threading.Lock(), set()
        actor._pinned = {BIND.unit, serve.unit}
        actor._conn = actor._raw = None
        actor.state = "connected"
        actor._manager = Mock(return_value=())
        start = tuple(replace(inactive(), unit=b.unit,
                              configuration_sha256=b.new_configuration_sha256 if recovery else b.old_configuration_sha256,
                              needs_daemon_reload=True) for b in (BIND, serve))
        end = tuple(replace(unit, configuration_sha256=b.old_configuration_sha256 if recovery else b.new_configuration_sha256,
                            needs_daemon_reload=False) for b, unit in zip((BIND, serve), start, strict=True))
        actor._snapshot = Mock(side_effect=[*start, *start, *end])
        return actor, approval, step, start, end

    def test_forward_and_recovery_each_bind_one_reload_to_both_verified_snapshots(self):
        for scope in ("forward", "recovery"):
            with self.subTest(scope=scope):
                actor, approval, step, before, after = self.setup_reload(scope)
                result = actor.reload_units(before, approval)
                actor._manager.assert_called_once_with("Reload", "", (), "", approval.deadline, reply_timeout=30)
                actor.journal.claim_dispatch.assert_called_once_with(approval.operation, step, approval.binding_sha256)
                self.assertEqual(result["step"], step)
                self.assertEqual(result["operation"], approval.operation)
                self.assertEqual(result["before_sha256"], dict(zip(("gateway", "serve"), (u.sha256 for u in before))))
                self.assertEqual(result["after_sha256"], dict(zip(("gateway", "serve"), (u.sha256 for u in after))))
                with self.assertRaises(Refused):
                    actor.reload_units(before, approval)
                self.assertEqual(actor._manager.call_count, 1)

    def test_foreign_or_active_role_refuses_before_claiming(self):
        for fault in ("foreign", "active", "job", "already-new"):
            with self.subTest(fault=fault):
                actor, approval, _, before, after = self.setup_reload()
                serve = before[1]
                if fault == "foreign":
                    serve = replace(serve, unit="unreviewed.service")
                elif fault == "active":
                    serve = active(unit=serve.unit, configuration_sha256=serve.configuration_sha256)
                elif fault == "job":
                    serve = replace(serve, job_id=1, job_path="/org/freedesktop/systemd1/job/1")
                else:
                    serve = after[1]
                with self.assertRaises(Refused):
                    actor.reload_units((before[0], serve), approval)
                actor.journal.claim_dispatch.assert_not_called()
                actor._manager.assert_not_called()

    def test_missing_stage_prefix_or_preclaim_drift_refuses_without_reload(self):
        for fault in ("prefix", "drift"):
            with self.subTest(fault=fault):
                actor, approval, _, before, after = self.setup_reload()
                if fault == "prefix":
                    actor.journal.view["records"].pop(0)
                else:
                    actor._snapshot.side_effect = [before[0], after[1]]
                with self.assertRaises(Refused):
                    actor.reload_units(before, approval)
                actor.journal.claim_dispatch.assert_not_called()
                actor._manager.assert_not_called()

    def test_drift_after_claim_holds_the_original_operation_without_dispatch(self):
        actor, approval, step, before, after = self.setup_reload()
        actor._snapshot.side_effect = [*before, before[0], after[1]]
        with self.assertRaises(Unknown):
            actor.reload_units(before, approval)
        self.assertEqual(actor.state, "unknown")
        actor.journal.claim_dispatch.assert_called_once_with(approval.operation, step, approval.binding_sha256)
        actor._manager.assert_not_called()
        with self.assertRaises(Refused):
            actor.reload_units(before, approval)

    def test_lost_reply_or_wrong_postimage_keeps_the_original_reload_spent(self):
        for fault in ("timeout", "stale", "wrong-configuration"):
            with self.subTest(fault=fault):
                actor, approval, _, before, after = self.setup_reload()
                if fault == "timeout":
                    actor._manager.side_effect = Unknown("Reply lost")
                elif fault == "stale":
                    actor._snapshot.side_effect = [*before, *before, after[0], replace(after[1], needs_daemon_reload=True)]
                else:
                    actor._snapshot.side_effect = [*before, *before, after[0], before[1]]
                with self.assertRaises(Unknown):
                    actor.reload_units(before, approval)
                self.assertEqual(actor.state, "unknown")
                with self.assertRaises(Refused):
                    actor.reload_units(before, approval)
                self.assertEqual(actor._manager.call_count, 1)


class NativeServiceBoundaryTests(unittest.TestCase):
    def test_attempt_phase_never_claims_a_job_without_an_exact_ack(self):
        claimed=NativeAttempt('a'*32,OLD,'stop_gateway',BIND.unit,NEW,'claimed','')
        self.assertEqual(claimed.phase,'claimed')
        for phase in ('accepted','job_done','verified'):
            with self.subTest(phase=phase),self.assertRaises(ValueError):replace(claimed,phase=phase)
            observed=replace(claimed,phase=phase,job_path='/org/freedesktop/systemd1/job/123')
            self.assertNotEqual(observed.sha256,claimed.sha256)
        with self.assertRaises(ValueError):replace(claimed,job_path='/org/freedesktop/systemd1/job/123')

    def test_attempt_refuses_foreign_roles_operations_and_unbounded_job_identity(self):
        accepted=NativeAttempt('a'*32,OLD,'rollback_stop_gateway',BIND.unit,NEW,'accepted','/org/freedesktop/systemd1/job/123')
        for change in (dict(operation=True),dict(authorization_sha256='private'),dict(preimage_sha256='private'),
                       dict(step='restart_gateway'),dict(unit='ssh.service'),dict(unit='hermes-serve.service'),
                       dict(phase='complete'),dict(job_path='/org/freedesktop/systemd1/job/0')):
            with self.subTest(change=change),self.assertRaises(ValueError):replace(accepted,**change)

    def test_native_bus_authority_requires_nonroot_uid_and_exact_incarnations(self):
        good=BusAuthority(1000,44,45,OWNER,':1.1',OWNER)
        for change in (dict(uid=True),dict(uid=0),dict(bus_inode=0),dict(manager_unique='org.freedesktop.systemd1'),dict(peer_owner=None)):
            with self.subTest(change=change),self.assertRaises(ValueError):replace(good,**change)

    def test_only_exact_hermes_roles_or_owned_acceptance_names_bind(self):
        fixture='signal-grove-acceptance-'+'a'*32+'-gateway.service'
        self.assertEqual(replace(BIND,unit=fixture).unit,fixture)
        for unit in ('ssh.service','../hermes-gateway.service','hermes-gateway.service;stop','signal-grove-acceptance-a-gateway.service'):
            with self.subTest(unit=unit),self.assertRaises(ValueError):replace(BIND,unit=unit)

    def test_role_mismatch_and_unbound_commands_refuse(self):
        for change in (dict(role='serve'),dict(role='all'),dict(new_configuration_sha256='private'),dict(new_executable='../python')):
            with self.subTest(change=change),self.assertRaises(ValueError):replace(BIND,**change)

    def test_command_identity_ignores_runtime_counters_only(self):
        row=['/bin/python',['/bin/python','-B'],False,1,2,3,4,50,1,0]
        later=row[:3]+[7,8,9,10,60,1,0]
        self.assertEqual(command_rows([row]),command_rows([later]))
        changed=row.copy();changed[1]=['/bin/python','different.py']
        self.assertNotEqual(command_rows([row]),command_rows([changed]))

    def test_command_types_flags_and_bounds_are_not_coerced(self):
        row=['/bin/python',['/bin/python'],False,1,2,3,4,50,1,0]
        for index,value in ((2,1),(3,True),(7,-1),(0,'relative'),(1,['x']*129)):
            changed=row.copy();changed[index]=value
            with self.subTest(index=index),self.assertRaises(Unknown):command_rows([changed])

    def test_command_error_does_not_echo_private_arguments(self):
        with self.assertRaises(Unknown) as caught:command_rows([['PRIVATE_ARGUMENT']])
        self.assertNotIn('PRIVATE_ARGUMENT',str(caught.exception))

    def test_invocation_accepts_only_bounded_byte_arrays(self):
        self.assertEqual(invocation_id(bytes(range(16))),bytes(range(16)).hex())
        self.assertEqual(invocation_id(list(range(16))),bytes(range(16)).hex())
        self.assertEqual(invocation_id(b''),'')
        for value in (16,True,'a'*16,[True]*16,[256]*16,b'a'*17):
            with self.subTest(value=value),self.assertRaises(Unknown):invocation_id(value)

    def test_exact_successful_job_is_required(self):
        path='/org/freedesktop/systemd1/job/123'
        self.assertEqual(job_result((123,path,BIND.unit,'done'),path,BIND.unit),'done')
        self.assertIsNone(job_result((124,path[:-3]+'124',BIND.unit,'done'),path,BIND.unit))

    def test_job_boolean_mismatched_id_unit_and_failure_refuse(self):
        path='/org/freedesktop/systemd1/job/123'
        for body in ((True,path,BIND.unit,'done'),(124,path,BIND.unit,'done'),(123,path,'ssh.service','done'),
                     (123,path,BIND.unit,'failed'),(123,path,BIND.unit,'canceled'),(123,'/elsewhere',BIND.unit,'done')):
            with self.subTest(body=body),self.assertRaises(Unknown):job_result(body,path,BIND.unit)

    def test_snapshot_refuses_bool_pids_bad_owners_and_mixed_job_identity(self):
        for change in (dict(main_pid=True),dict(owner=None),dict(control_pid=True),dict(job_id=True),dict(job_id=3,job_path='/'),
                       dict(job_path='/other'),dict(stop_helpers_ok=1),dict(needs_daemon_reload=0),dict(invocation='private')):
            with self.subTest(change=change),self.assertRaises(ValueError):active(**change)

    def test_forward_stop_accepts_only_old_loaded_config_and_old_executable(self):
        snap=active();validate_preimage(BIND,'stop','forward',snap,snap)
        for change in (dict(configuration_sha256=NEW,executable='/new/python'),dict(executable='/new/python')):
            current=active(**change)
            with self.assertRaises(Refused):validate_preimage(BIND,'stop','forward',current,current)

    def test_forward_start_refuses_old_loaded_configuration_before_dispatch(self):
        good=inactive();validate_preimage(BIND,'start','forward',good,good)
        wrong=replace(good,configuration_sha256=OLD)
        with self.assertRaises(Refused):validate_preimage(BIND,'start','forward',wrong,wrong)

    def test_recovery_start_requires_old_loaded_configuration(self):
        wrong=inactive()
        with self.assertRaises(Refused):validate_preimage(BIND,'start','recovery',wrong,wrong)
        good=replace(wrong,configuration_sha256=OLD);validate_preimage(BIND,'start','recovery',good,good)

    def test_start_refuses_a_matching_loaded_config_until_the_manager_has_reloaded(self):
        for scope, config in (('forward', NEW), ('recovery', OLD)):
            snapshot = replace(inactive(), configuration_sha256=config)
            stale = replace(snapshot, needs_daemon_reload=True)
            with self.subTest(scope=scope):
                self.assertNotEqual(snapshot.sha256, stale.sha256)
                with self.assertRaisesRegex(Refused, 'requires a daemon reload'):
                    validate_preimage(BIND, 'start', scope, stale, stale)
                validate_preimage(BIND, 'start', scope, snapshot, snapshot)

    def test_forward_stop_can_drain_the_reviewed_incarnation_before_reload(self):
        snapshot = active(needs_daemon_reload=True)
        validate_preimage(BIND, 'stop', 'forward', snapshot, snapshot)

    def test_recovery_stop_accepts_each_exact_reviewed_incarnation_configuration(self):
        for snap in (active(),active(configuration_sha256=NEW,executable='/new/python')):
            validate_preimage(BIND,'stop','recovery',snap,snap)
        wrong=active(configuration_sha256=NEW)
        with self.assertRaises(Refused):validate_preimage(BIND,'stop','recovery',wrong,wrong)

    def test_stale_pid_invocation_or_foreign_loaded_config_refuses(self):
        expected=active()
        for current in (active(invocation='2'*32),active(configuration_sha256='c'*64),active(owner=Owner(4000,501,BOOT))):
            with self.assertRaises(Refused):validate_preimage(BIND,'stop','forward',current,expected)

    def test_existing_job_or_control_process_refuses(self):
        for current in (active(job_id=3,job_path='/org/freedesktop/systemd1/job/3'),active(control_pid=4001)):
            with self.assertRaises(Refused):validate_preimage(BIND,'stop','forward',current,current)

    def test_stop_never_accepts_an_inactive_or_starting_unit(self):
        for snap in (inactive(),active(active_state='activating',sub_state='start')):
            with self.assertRaises(Refused):validate_preimage(BIND,'stop','forward',snap,snap)

    def test_no_restart_or_other_scope_is_dispatched(self):
        snap=active()
        for action,scope in (('restart','forward'),('stop','all'),('kill','recovery')):
            with self.assertRaises(Refused):validate_preimage(BIND,action,scope,snap,snap)

    def test_signal_overflow_holds_without_silently_dropping_evidence(self):
        queue=_Signals()
        for i in range(128):queue.append(i)
        with self.assertRaises(Unknown):queue.append(128)
        self.assertEqual(len(queue),128);self.assertEqual(queue.popleft(),0)

    def test_limited_socket_refuses_unbounded_send_before_writing(self):
        class Sink:
            def sendall(self,data):raise AssertionError('Oversized data reached the socket')
        wrapped=_LimitedSocket(Sink())
        with self.assertRaises(Unknown):wrapped.sendall(b'x'*(64*1024+1))

    def test_limited_socket_refuses_excessive_received_bytes(self):
        class Stream:
            def recv(self,size):return b'x'*size
        wrapped=_LimitedSocket(Stream())
        for _ in range(2048):self.assertEqual(len(wrapped.recv(4096)),4096)
        with self.assertRaises(Unknown):wrapped.recv(1)

    @unittest.skipUnless(sys.platform=='linux','Native adapter watchdog requires actual Linux AF_UNIX socket behavior')
    def test_absolute_watchdog_interrupts_a_blocked_owned_socket(self):
        left,right=socket.socketpair(socket.AF_UNIX);self.addCleanup(left.close);self.addCleanup(right.close)
        start=time.monotonic()
        with self.assertRaises(Unknown):
            with _Deadline(left,time.monotonic()+0.04):left.recv(1)
        self.assertLess(time.monotonic()-start,1)

    @unittest.skipUnless(sys.platform=='linux','Native adapter watchdog requires actual Linux AF_UNIX socket behavior')
    def test_finished_watchdog_does_not_close_a_later_socket_operation(self):
        left,right=socket.socketpair(socket.AF_UNIX);self.addCleanup(left.close);self.addCleanup(right.close)
        with _Deadline(left,time.monotonic()+0.02):pass
        time.sleep(0.04);right.sendall(b'ok');left.settimeout(0.3)
        self.assertEqual(left.recv(2),b'ok')


if __name__=='__main__':unittest.main()
