from pathlib import Path
import importlib.util
import json
import os
import socket
import subprocess
import sys
import time
import unittest
import uuid
from datetime import datetime,timezone

scripts=Path(__file__).resolve().parents[1];sys.path.insert(0,str(scripts))
spec=importlib.util.spec_from_file_location('native_gateway_control_schema',scripts/'native_gateway_control.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
ID='a'*32


class GatewayControlSchemaTests(unittest.TestCase):
    def wire(self):return json.dumps(dict(ok=True,protocol=1,id=ID,result=dict(value='garden'))).encode()+b'\n'
    def test_exact_sdk_envelope_preserves_non_ascii_read_data(self):
        raw=json.dumps(dict(ok=True,protocol=1,id=ID,result=dict(value='花园')),ensure_ascii=False).encode()+b'\n'
        self.assertEqual({'value':'花园'},m.decode_control_reply(raw,ID))
    def test_protocol_ok_id_and_result_types_are_strict(self):
        original=dict(ok=True,protocol=1,id=ID,result={})
        for key,values in {'ok':[1,False,None],'protocol':[True,0,2,'1'],'id':['b'*32,None],'result':[[],None,'value',False]}.items():
            for value in values:
                with self.subTest(key=key,value=value),self.assertRaises(m.Unknown):m.decode_control_reply(json.dumps({**original,key:value}).encode()+b'\n',ID)
    def test_missing_and_extra_envelope_fields_refuse(self):
        original=dict(ok=True,protocol=1,id=ID,result={})
        for key in original:
            with self.assertRaises(m.Unknown):m.decode_control_reply(json.dumps({k:v for k,v in original.items() if k!=key}).encode()+b'\n',ID)
        with self.assertRaises(m.Unknown):m.decode_control_reply(json.dumps({**original,'extra':1}).encode()+b'\n',ID)
    def test_duplicate_fields_at_any_depth_refuse(self):
        for raw in (b'{"ok":true,"ok":true,"id":"'+ID.encode()+b'","protocol":1,"result":{}}\n',b'{"ok":true,"id":"'+ID.encode()+b'","protocol":1,"result":{"x":1,"x":2}}\n'):
            with self.assertRaises(m.Unknown):m.decode_control_reply(raw,ID)
    def test_one_complete_line_is_required(self):
        raw=self.wire()
        for value in (raw[:-1],raw+raw,raw+b'\n',b'\n',b'{}\ntrailing'):
            with self.assertRaises(m.Unknown):m.decode_control_reply(value,ID)
    def test_invalid_utf8_bom_and_json_refuse_without_echo(self):
        for raw in (b'\xff\n',b'\xef\xbb\xbf'+self.wire(),b'fixture-private-value\n'):
            with self.assertRaises(m.Unknown) as caught:m.decode_control_reply(raw,ID)
            self.assertNotIn('fixture-private',str(caught.exception))
    def test_nonfinite_values_and_numeric_overflow_refuse(self):
        for value in (b'NaN',b'Infinity',b'-Infinity',b'1e999'):
            raw=b'{"ok":true,"id":"'+ID.encode()+b'","protocol":1,"result":{"x":'+value+b'}}\n'
            with self.assertRaises(m.Unknown):m.decode_control_reply(raw,ID)
    def test_fixed_byte_and_nesting_budgets_refuse(self):
        with self.assertRaises(m.Unknown):m.decode_control_reply(b'x'*m.MAX_RESPONSE+b'\n',ID)
        raw=b'{"ok":true,"id":"'+ID.encode()+b'","protocol":1,"result":{"x":'+b'['*65+b'0'+b']'*65+b'}}\n'
        with self.assertRaises(m.Unknown):m.decode_control_reply(raw,ID)
    def test_sdk_error_details_do_not_escape(self):
        raw=json.dumps(dict(ok=False,id=ID,protocol=1,error='fixture-private-error')).encode()+b'\n'
        with self.assertRaises(m.Unknown) as caught:m.decode_control_reply(raw,ID)
        self.assertNotIn('fixture-private',str(caught.exception))
    def test_invalid_frame_and_correlation_inputs_refuse(self):
        for raw,request in ((None,ID),(self.wire().decode(),ID),(self.wire(),'A'*32),(self.wire(),'a'*31),(self.wire(),None)):
            with self.assertRaises(m.Unknown):m.decode_control_reply(raw,request)


class GatewayRegisteredStatusTests(unittest.TestCase):
    def status(self):
        when=datetime.fromtimestamp(100,timezone.utc).isoformat()
        return dict(protocol=1,answering_pid=4000,answered_at=100,
            live_activity=dict(contract='gateway_live_work_v1',state='OBSERVED',responding_pid=4000,fleet_ready_to_switch=False,
                coverage='REGISTERED_WORK_ONLY',observed_at=when,busy=False,lifecycle='draining',adapter_instances_observed=1,secondary_profile_maps_observed=0,counters={k:0 for k in m.LIVE_COUNTERS}),
            project_admission=dict(contract='native_admission_snapshot_v1',state='OBSERVED',responding_pid=4000,fleet_ready_to_switch=False,
                observed_at=when,home_digest='a'*64,fence=dict(epoch=0,owner=None,frozen=False,**{k:0 for k in m.LEDGER_COUNTERS})))
    def project(self,body):return m.project_registered_status(body,4000,'a'*64,99,101)
    def test_observed_registered_idle_never_becomes_fleet_permission(self):
        result=self.project(self.status());self.assertFalse(result['busy']);self.assertFalse(result['fleet_ready_to_switch']);self.assertFalse(result['admission_frozen']);self.assertEqual('REGISTERED_WORK_ONLY',result['coverage'])
    def test_initial_sdk_fence_preserves_zero_epoch_and_no_owner(self):
        result=self.project(self.status())
        self.assertEqual(0,result['fence_epoch']);self.assertIsNone(result['fence_owner']);self.assertFalse(result['admission_frozen'])
    def test_frozen_and_released_fences_retain_exact_sdk_owner(self):
        for frozen in (True,False):
            body=self.status();body['project_admission']['fence'].update(epoch=2,owner='mode-coordinator:7',frozen=frozen)
            result=self.project(body)
            self.assertEqual('mode-coordinator:7',result['fence_owner']);self.assertEqual(2,result['fence_epoch']);self.assertIs(frozen,result['admission_frozen']);self.assertFalse(result['fleet_ready_to_switch'])
    def test_positive_fence_epoch_requires_valid_sdk_transaction_owner(self):
        for owner in (None,'',' invalid','invalid/owner','owner\n','a'*129,False,17):
            for frozen in (True,False):
                with self.subTest(owner=owner,frozen=frozen):
                    body=self.status();body['project_admission']['fence'].update(epoch=1,owner=owner,frozen=frozen)
                    with self.assertRaises(m.Unknown):self.project(body)
    def test_zero_fence_epoch_cannot_be_owned_or_frozen(self):
        for owner,frozen in ((None,True),('other-owner',False),('other-owner',True)):
            body=self.status();body['project_admission']['fence'].update(epoch=0,owner=owner,frozen=frozen)
            with self.assertRaises(m.Unknown):self.project(body)
    def test_foreign_fence_is_observed_without_adopting_its_authority(self):
        body=self.status();body['project_admission']['fence'].update(epoch=3,owner='unrelated-operation',frozen=True)
        result=self.project(body)
        self.assertEqual('unrelated-operation',result['fence_owner']);self.assertFalse(result['fleet_ready_to_switch']);self.assertEqual('REGISTERED_WORK_ONLY',result['coverage'])
    def test_each_live_and_ledger_work_source_remains_busy(self):
        for key in m.LIVE_COUNTERS:
            body=self.status();body['live_activity']['counters'][key]=1;body['live_activity']['busy']=True;self.assertTrue(self.project(body)['busy'],key)
        for key in m.LEDGER_COUNTERS:
            body=self.status();body['project_admission']['fence'][key]=1;self.assertTrue(self.project(body)['busy'],key)
    def test_missing_extra_and_invalid_work_counts_are_unknown(self):
        for key in m.LIVE_COUNTERS:
            body=self.status();del body['live_activity']['counters'][key]
            with self.assertRaises(m.Unknown):self.project(body)
        for value in (True,-1,0.0,None):
            body=self.status();body['project_admission']['fence']['active_turns']=value
            with self.assertRaises(m.Unknown):self.project(body)
        body=self.status();body['live_activity']['counters']['unsupported_source']=0
        with self.assertRaises(m.Unknown):self.project(body)
        body=self.status();body['project_admission']['fence']['unsupported_source']=0
        with self.assertRaises(m.Unknown):self.project(body)
    def test_unknown_or_promoted_coverage_is_not_idle(self):
        for area in ('live_activity','project_admission'):
            for key,value in (('state','UNKNOWN'),('fleet_ready_to_switch',True)):
                body=self.status();body[area][key]=value
                with self.assertRaises(m.Unknown):self.project(body)
        body=self.status();body['live_activity']['coverage']='FLEET'
        with self.assertRaises(m.Unknown):self.project(body)
    def test_other_native_process_or_home_cannot_supply_counts(self):
        for area in ('live_activity','project_admission'):
            body=self.status();body[area]['responding_pid']=4001
            with self.assertRaises(m.Unknown):self.project(body)
        body=self.status();body['answering_pid']=True
        with self.assertRaises(m.Unknown):self.project(body)
        body=self.status();body['project_admission']['home_digest']='b'*64
        with self.assertRaises(m.Unknown):self.project(body)
    def test_stale_future_naive_or_missing_timestamps_refuse(self):
        for area in ('live_activity','project_admission'):
            for value in ('1970-01-01T00:00:00+00:00','1970-01-01T00:02:00+00:00','1970-01-01T00:01:40',None):
                body=self.status();body[area]['observed_at']=value
                with self.assertRaises(m.Unknown):self.project(body)
        for value in (float('nan'),True,98,102):
            body=self.status();body['answered_at']=value
            with self.assertRaises(m.Unknown):self.project(body)
    def test_inconsistent_busy_lifecycle_or_fence_metadata_refuse(self):
        body=self.status();body['live_activity']['counters']['cron']=1
        with self.assertRaises(m.Unknown):self.project(body)
        for key,value in (('epoch',True),('epoch',-1),('owner',17),('frozen',0)):
            body=self.status();body['project_admission']['fence'][key]=value
            with self.assertRaises(m.Unknown):self.project(body)
        for key,value in (('lifecycle','stopped'),('secondary_profile_maps_observed',None),('busy',0)):
            body=self.status();body['live_activity'][key]=value
            with self.assertRaises(m.Unknown):self.project(body)
    def test_unproven_status_reports_only_a_fixed_validation_phase(self):
        vectors = (
            (None, 'protocol', 'fixture-secret', 'reply_identity'),
            (None, 'answered_at', 98, 'reply_freshness'),
            ('live_activity', 'contract', 'fixture-secret', 'activity_contract'),
            ('live_activity', 'state', 'fixture-secret', 'activity_unobserved'),
            ('live_activity', 'observed_at', 98, 'activity_sample'),
            ('live_activity', 'counters', {}, 'activity_counts'),
            ('project_admission', 'contract', 'fixture-secret', 'admission_contract'),
            ('project_admission', 'state', 'fixture-secret', 'admission_unobserved'),
            ('project_admission', 'home_digest', 'fixture-secret', 'admission_sample'),
            ('project_admission', 'fence', {}, 'admission_fence'),
        )
        for section, field, value, expected in vectors:
            with self.subTest(field=field, expected=expected):
                body = self.status()
                target = body if section is None else body[section]
                target[field] = value
                with self.assertRaises(m.Unknown) as error:
                    self.project(body)
                self.assertIsInstance(error.exception, m.RegisteredStatusUnknown)
                self.assertEqual(expected, error.exception.reason)
                self.assertNotIn('fixture-secret', str(error.exception))

    def test_legacy_state_file_zeroes_and_unenrolled_ledger_are_unknown(self):
        body=dict(protocol=1,answering_pid=4000,answered_at=100,gateway_state='draining',active_agents=0)
        with self.assertRaises(m.Unknown):self.project(body)
        body=self.status();body['project_admission']=dict(contract='native_admission_snapshot_v1',state='UNKNOWN',reason='NATIVE_ADMISSION_UNPROVEN')
        with self.assertRaises(m.Unknown):self.project(body)


@unittest.skipUnless(sys.platform=='linux','Requires real Linux Unix peer credentials and procfs')
class NativeGatewayControlTests(unittest.TestCase):
    def setUp(self):
        for name in ('retirement_guard','native_retirement_owner','cutover_journal','native_gateway_control'):
            spec=importlib.util.spec_from_file_location(name,scripts/(name+'.py'));module=importlib.util.module_from_spec(spec);sys.modules[name]=module;spec.loader.exec_module(module)
        self.m=sys.modules['native_gateway_control'];self.native=sys.modules['native_retirement_owner'];self.root=Path(os.environ['SIGNAL_GROVE_TRANSACTION_RETAIN_ROOT'])/('gateway-wire-'+uuid.uuid4().hex);self.root.mkdir(mode=0o700)
        self.address=b'\x00signal-grove-control-'+uuid.uuid4().hex.encode();self.proc=None;self.owner=None;self.sockets=[];self.addCleanup(self.finish)
    def start(self,mode='normal'):
        command=[sys.executable,'-I','-B',str(scripts/'native/gateway_control_server.py'),str(self.root),self.address.hex(),mode]
        with (self.root/'stdout.log').open('xb') as out,(self.root/'stderr.log').open('xb') as err:self.proc=subprocess.Popen(command,stdout=out,stderr=err,stdin=subprocess.DEVNULL)
        deadline=time.monotonic()+8
        while not (self.root/'observations.json').exists() and self.proc.poll() is None and time.monotonic()<deadline:time.sleep(.01)
        self.assertTrue((self.root/'observations.json').is_file());self.owner=self.native.read_process_owner(self.proc.pid);self.assertIsNotNone(self.owner)
        (self.root/'intent.json').write_text(json.dumps(dict(case=self.id(),argv=command,owner=dict(pid=self.owner.pid,start_ticks=self.owner.start_ticks,boot_id=self.owner.boot_id),mode=mode)),encoding='utf-8')
    def connect(self):
        sock=socket.socket(socket.AF_UNIX,socket.SOCK_STREAM);self.sockets.append(sock);sock.settimeout(2);sock.connect(self.address);return sock
    def observations(self):return json.loads((self.root/'observations.json').read_text(encoding='utf-8'))
    def finish(self):
        for sock in self.sockets:sock.close()
        if self.proc is not None:
            if self.proc.poll() is None:
                self.assertEqual(self.owner,self.native.read_process_owner(self.proc.pid));self.proc.terminate();self.proc.wait(timeout=5)
            self.assertNotEqual(self.owner,self.native.read_process_owner(self.proc.pid))
            (self.root/'terminal.json').write_text(json.dumps(dict(pid=self.proc.pid,exit_code=self.proc.returncode,owner_ended=True,case=self.id())),encoding='utf-8')
    def test_kernel_peer_and_one_request_per_connection_match_sdk_contract(self):
        self.start();control=self.m.NativeGatewayControl(self.owner,self.connect());result=control.exchange('identify')
        self.assertEqual(self.owner.pid,result['pid']);self.assertEqual('observed',control.state)
        with self.assertRaises(self.m.Refused):control.exchange('status')
        other=self.m.NativeGatewayControl(self.owner,self.connect());self.assertTrue(other.exchange('status')['fixture'])
        self.assertEqual(['identify','status'],[r['verb'] for r in self.observations()['requests']]);self.assertEqual(2,self.observations()['accepted'])
    def test_actual_parent_cannot_impersonate_native_peer(self):
        self.start();parent=self.native.read_process_owner(os.getpid())
        with self.assertRaises(self.m.Refused):self.m.NativeGatewayControl(parent,self.connect())
        self.assertEqual([],self.observations()['requests'])
    def test_same_pid_with_wrong_start_ticks_sends_nothing(self):
        self.start();owner=self.m.Owner(self.owner.pid,self.owner.start_ticks+1,self.owner.boot_id)
        with self.assertRaises(self.m.Refused):self.m.NativeGatewayControl(owner,self.connect())
        self.assertEqual([],self.observations()['requests'])
    def test_mutation_verbs_are_refused_before_send(self):
        self.start()
        for verb in ('pause-for-update','session-ticket','reload-plugins','drain'):
            control=self.m.NativeGatewayControl(self.owner,self.connect())
            with self.assertRaises(self.m.Refused):control.exchange(verb)
        self.assertEqual([],self.observations()['requests'])
    def test_registered_scope_failure_keeps_authenticated_frame_outcome_unknown(self):
        self.start()
        control = self.m.NativeGatewayControl(self.owner, self.connect())
        with self.assertRaises(self.m.RegisteredStatusUnknown) as error:
            control.registered_status('a' * 64)
        self.assertEqual('reply_identity', error.exception.reason)
        self.assertEqual('unknown', control.state)
        with self.assertRaises(self.m.Refused):
            control.exchange('status')
        self.assertEqual(['status'], [row['verb'] for row in self.observations()['requests']])

    def test_wrong_id_and_sdk_errors_stay_unknown_without_replay(self):
        self.start('wrong_id');control=self.m.NativeGatewayControl(self.owner,self.connect())
        with self.assertRaises(self.m.Unknown):control.exchange('status')
        with self.assertRaises(self.m.Refused):control.exchange('status')
        self.assertEqual(1,len(self.observations()['requests']));self.assertEqual('unknown',control.state)
    def test_multiple_response_frames_are_not_a_valid_observation(self):
        self.start('two_frames');control=self.m.NativeGatewayControl(self.owner,self.connect())
        with self.assertRaises(self.m.Unknown):control.exchange('status')
        self.assertEqual(1,len(self.observations()['requests']))
    def test_lost_response_and_timeout_do_not_reconnect(self):
        self.start('slow');control=self.m.NativeGatewayControl(self.owner,self.connect(),timeout=.08)
        with self.assertRaises(self.m.Unknown):control.exchange('status')
        with self.assertRaises(self.m.Refused):control.exchange('status')
        self.assertEqual(1,self.observations()['accepted']);self.assertEqual(1,len(self.observations()['requests']))
    def test_dead_native_owner_cannot_be_replaced_on_a_pinned_socket(self):
        self.start();control=self.m.NativeGatewayControl(self.owner,self.connect());self.proc.terminate();self.proc.wait(timeout=5)
        with self.assertRaises(self.m.Refused):control.exchange('status')
        self.assertEqual([],self.observations()['requests'])


if __name__=='__main__':unittest.main()
