"""Readiness observation must not replay, rebind or extend an admitted frame."""
from dataclasses import replace
import json
import socket
import unittest
from unittest.mock import Mock,patch
from server_extensions.worker_workspaces import local_pipe as p,read_host as h
from server_extensions.worker_workspaces.workspace_provider import WorkspaceError


def fixture():
    endpoint=p.PipeEndpoint(r'\\.\pipe\SignalGrove.Workspace.'+'a'*32,'12345678-1234-1234-1234-123456789abc','1'*64,'S-1-5-21-1-2-3-1000',1,1,1)
    frame=dict(schema=1,request_id='2'*32,worker_instance=endpoint.worker_instance,payload=dict(schema=1,scope_id=endpoint.scope_id,operation='prepare',project_id='fixture'))
    return endpoint,json.dumps(frame).encode()


class CurrentProjectionAdmissionTest(unittest.TestCase):
    def setUp(self):
        self.endpoint,self.frame=fixture();self.scope=self.endpoint.scope_id;self.device=socket.gethostname().upper();self.clock=0.0;self.waiter=Mock()
        self.waiter.wait.side_effect=self.advance
    def advance(self,seconds):self.clock+=seconds
    def raw(self,state='ready',*,observed=100,endpoint=None):
        status=h.ReadHostStatus(self.scope,state,(endpoint or self.endpoint) if state=='ready' else None,'DENIED' if state=='held' else None)
        return h.encode_projection([status],device=self.device,observed=observed)
    def call(self,reader,*,frame=None,timeout=3,native=None):
        with patch.object(h.time,'monotonic',side_effect=lambda:self.clock),patch.object(h.time,'time',side_effect=lambda:100+self.clock),patch.object(h.threading,'Event',return_value=self.waiter),patch.object(p,'call_once',native or Mock(return_value=b'reply')):
            return h.call_current_projected(reader,self.frame if frame is None else frame,scope_id=self.scope,device=self.device,timeout=timeout)

    def test_ready_projection_admits_one_frame_without_waiting(self):
        reader=Mock(return_value=self.raw());native=Mock(return_value=b'reply')
        self.assertEqual(b'reply',self.call(reader,native=native));reader.assert_called_once_with();self.waiter.wait.assert_not_called()
        native.assert_called_once_with(self.endpoint,self.frame,timeout=3,deadline=3.0)

    def test_fresh_starting_is_reobserved_before_one_frame_with_the_original_deadline(self):
        reader=Mock(side_effect=[self.raw('starting'),self.raw()]);native=Mock(return_value=b'reply')
        self.assertEqual(b'reply',self.call(reader,native=native));self.assertEqual(2,reader.call_count);self.waiter.wait.assert_called_once_with(.05)
        native.assert_called_once_with(self.endpoint,self.frame,timeout=3,deadline=3.0)

    def test_starting_until_expiry_never_opens_or_submits_a_frame(self):
        reader=Mock(return_value=self.raw('starting'));native=Mock()
        with self.assertRaises(p.ReadTransportError) as error:self.call(reader,timeout=.1,native=native)
        self.assertEqual('UNAVAILABLE',error.exception.code);self.assertFalse(error.exception.request_submitted);native.assert_not_called();self.assertLessEqual(reader.call_count,3);self.assertAlmostEqual(.1,self.clock)

    def test_held_closed_and_stale_data_refuse_without_waiting_for_restoration(self):
        for raw in (self.raw('held'),self.raw('closed'),self.raw('starting',observed=80),self.raw('starting',observed=101),b'{"schema":1,"schema":1}'):
            reader=Mock(return_value=raw);native=Mock();self.waiter.reset_mock()
            with self.subTest(raw=raw[:20]),self.assertRaises(p.ReadTransportError) as error:self.call(reader,native=native)
            self.assertFalse(error.exception.request_submitted);reader.assert_called_once();native.assert_not_called();self.waiter.wait.assert_not_called()

    def test_policy_or_owner_change_during_wait_refuses_before_native_admission(self):
        for code in ('DENIED','CHANGED'):
            self.clock=0;self.waiter.reset_mock();reader=Mock(side_effect=[self.raw('starting'),WorkspaceError(code)]);native=Mock()
            with self.subTest(code=code),self.assertRaises(p.ReadTransportError) as error:self.call(reader,native=native)
            self.assertEqual(code,error.exception.code);self.assertFalse(error.exception.request_submitted);self.assertEqual(2,reader.call_count);native.assert_not_called()

    def test_new_ready_worker_does_not_rebind_the_waiting_frame(self):
        other=replace(self.endpoint,worker_instance='12345678-1234-1234-1234-123456789abd');reader=Mock(side_effect=[self.raw('starting'),self.raw(endpoint=other)]);native=Mock()
        with self.assertRaises(p.ReadTransportError) as error:self.call(reader,native=native)
        self.assertEqual('CHANGED',error.exception.code);self.assertFalse(error.exception.request_submitted);native.assert_not_called()

    def test_loader_error_or_budget_exhaustion_refuses_before_a_ready_pipe_is_opened(self):
        def slow():self.clock=4.0;return self.raw()
        for effect in (RuntimeError('private loader detail'),slow):
            self.clock=0;native=Mock();reader=Mock(side_effect=effect)
            with self.subTest(kind=type(effect).__name__),self.assertRaises(p.ReadTransportError) as error:self.call(reader,native=native)
            self.assertEqual('UNAVAILABLE',error.exception.code);self.assertFalse(error.exception.request_submitted);self.assertNotIn('private',str(error.exception));native.assert_not_called();reader.assert_called_once()

    def test_bad_frame_scope_and_timeout_are_refused_before_loading_projection(self):
        wrong=json.loads(self.frame);wrong['payload']['scope_id']='3'*64
        for raw,timeout in ((b'{}',3),(json.dumps(wrong).encode(),3),(self.frame,True),(self.frame,float('inf')),(self.frame,0)):
            reader=Mock();native=Mock()
            with self.subTest(timeout=timeout),self.assertRaises(p.ReadTransportError) as error:self.call(reader,frame=raw,timeout=timeout,native=native)
            self.assertFalse(error.exception.request_submitted);reader.assert_not_called();native.assert_not_called()

    def test_admitted_errors_keep_uncertainty_without_a_second_read_or_submission(self):
        for failure in (p.ReadTransportError('BUSY',request_submitted=False),p.ReadTransportError('UNAVAILABLE',request_submitted=True),RuntimeError('native failure')):
            reader=Mock(return_value=self.raw());native=Mock(side_effect=failure)
            with self.subTest(kind=type(failure).__name__),self.assertRaises(type(failure)) as error:self.call(reader,native=native)
            self.assertIs(failure,error.exception);reader.assert_called_once();native.assert_called_once();self.waiter.wait.assert_not_called()


class AbsoluteAdmissionDeadlineTest(unittest.TestCase):
    def native(self,endpoint):
        value=Mock();value.process_identity.return_value=(endpoint.owner_sid,1,1);value.k.CreateFileW.return_value=123;value.k.CloseHandle.return_value=True
        def server_pid(handle,output):output._obj.value=endpoint.server_pid;return True
        value.k.GetNamedPipeServerProcessId.side_effect=server_pid
        value.read.return_value=json.dumps(dict(schema=1,request_id='2'*32,worker_instance=endpoint.worker_instance,status='error',code='BUSY')).encode()
        return value
    def test_supplied_deadline_is_shared_by_wait_write_read_and_ack(self):
        endpoint,raw=fixture();native=self.native(endpoint)
        with patch.object(p,'_Windows',return_value=native),patch.object(p.time,'monotonic',return_value=10):p.call_once(endpoint,raw,timeout=3,deadline=11)
        native.wait_available.assert_called_once_with(endpoint.address,11);self.assertEqual([11,11],[call.args[2] for call in native.write.call_args_list]);native.read.assert_called_once_with(123,p.MAX_REPLY_BYTES,11)

    def test_invalid_extended_or_expired_absolute_budget_never_opens_native_api(self):
        endpoint,raw=fixture()
        for deadline in (True,'11',float('inf'),float('nan'),0,-1,14,10**1000,-10**1000,9):
            with self.subTest(kind=type(deadline).__name__),patch.object(p,'_Windows') as native,patch.object(p.time,'monotonic',return_value=10),self.assertRaises(p.ReadTransportError) as error:p.call_once(endpoint,raw,timeout=3,deadline=deadline)
            self.assertFalse(error.exception.request_submitted);native.assert_not_called()

    def test_identity_work_that_exhausts_budget_never_writes_a_frame(self):
        endpoint,raw=fixture();native=self.native(endpoint);clock=[10.0];calls=[]
        def identity(pid):
            calls.append(pid)
            if len(calls)==2:clock[0]=11.0
            return endpoint.owner_sid,1,1
        native.process_identity.side_effect=identity
        with patch.object(p,'_Windows',return_value=native),patch.object(p.time,'monotonic',side_effect=lambda:clock[0]),self.assertRaises(p.ReadTransportError) as error:p.call_once(endpoint,raw,timeout=3,deadline=11)
        self.assertEqual('UNAVAILABLE',error.exception.code);self.assertFalse(error.exception.request_submitted);native.write.assert_not_called();native.api.SetNamedPipeHandleState.assert_not_called();native.k.CloseHandle.assert_called_once_with(123)

    def test_failed_write_after_admission_keeps_uncertainty_and_never_reopens(self):
        endpoint,raw=fixture();native=self.native(endpoint);native.write.side_effect=WorkspaceError('UNAVAILABLE')
        with patch.object(p,'_Windows',return_value=native),patch.object(p.time,'monotonic',return_value=10),self.assertRaises(p.ReadTransportError) as error:p.call_once(endpoint,raw,timeout=3,deadline=11)
        self.assertTrue(error.exception.request_submitted);native.k.CreateFileW.assert_called_once();native.write.assert_called_once_with(123,raw,11);native.read.assert_not_called()


if __name__=='__main__':unittest.main()
