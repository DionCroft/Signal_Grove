"""A pre-admission wait must never extend or replay a workspace request."""
import ctypes
import json
import unittest
from unittest.mock import Mock,patch
from server_extensions.worker_workspaces import local_pipe as t
from server_extensions.worker_workspaces.workspace_provider import WorkspaceError


class PipeAvailabilityTest(unittest.TestCase):
    def fixture(self):
        endpoint=t.PipeEndpoint(r'\\.\pipe\SignalGrove.Workspace.'+'a'*32,'12345678-1234-1234-1234-123456789abc','1'*64,'S-1-5-21-1-2-3-1000',1,1,1)
        request=dict(schema=1,request_id='2'*32,worker_instance=endpoint.worker_instance,payload=dict(schema=1,scope_id=endpoint.scope_id,operation='prepare',project_id='fixture'))
        reply=json.dumps(dict(schema=1,request_id=request['request_id'],worker_instance=endpoint.worker_instance,status='error',code='BUSY')).encode()
        native=Mock();native.process_identity.return_value=(endpoint.owner_sid,1,1)
        native.k.CreateFileW.return_value=123;native.k.CloseHandle.return_value=True
        def server_pid(handle,output):output._obj.value=endpoint.server_pid;return True
        native.k.GetNamedPipeServerProcessId.side_effect=server_pid;native.read.return_value=reply
        native.check.side_effect=lambda value: value if value else (_ for _ in ()).throw(WorkspaceError('UNAVAILABLE'))
        return endpoint,json.dumps(request).encode(),reply,native

    def test_one_availability_wait_and_open_share_the_original_write_read_ack_deadline(self):
        endpoint,raw,reply,native=self.fixture();clock=[10.0]
        def available(address,deadline):
            self.assertEqual(13.0,deadline);clock[0]=12.9
        native.wait_available.side_effect=available
        with patch.object(t,'_Windows',return_value=native),patch.object(t.time,'monotonic',side_effect=lambda:clock[0]):
            self.assertEqual(reply,t.call_once(endpoint,raw,timeout=3))
        native.wait_available.assert_called_once_with(endpoint.address,13.0);native.k.CreateFileW.assert_called_once()
        self.assertEqual(2,native.write.call_count)
        self.assertEqual([13.0,13.0],[call.args[2] for call in native.write.call_args_list])
        native.read.assert_called_once_with(123,t.MAX_REPLY_BYTES,13.0);native.k.CloseHandle.assert_called_once_with(123)

    def test_wait_timeout_is_unsubmitted_and_opens_or_writes_nothing(self):
        endpoint,raw,_,native=self.fixture();native.wait_available.side_effect=WorkspaceError('UNAVAILABLE')
        with patch.object(t,'_Windows',return_value=native),self.assertRaises(t.ReadTransportError) as error:t.call_once(endpoint,raw)
        self.assertEqual('UNAVAILABLE',error.exception.code);self.assertFalse(error.exception.request_submitted)
        native.wait_available.assert_called_once();native.k.CreateFileW.assert_not_called();native.write.assert_not_called();native.k.CloseHandle.assert_not_called()

    def test_busy_open_after_successful_wait_is_refused_once_without_retry(self):
        endpoint,raw,_,native=self.fixture();native.k.CreateFileW.return_value=ctypes.c_void_p(-1).value;native.error.side_effect=WorkspaceError('BUSY')
        with patch.object(t,'_Windows',return_value=native),self.assertRaises(t.ReadTransportError) as error:t.call_once(endpoint,raw)
        self.assertEqual('BUSY',error.exception.code);self.assertFalse(error.exception.request_submitted)
        native.wait_available.assert_called_once();native.k.CreateFileW.assert_called_once();native.write.assert_not_called();native.k.CloseHandle.assert_not_called()

    def test_owner_mismatch_refuses_before_wait_and_server_identity_mismatch_before_write(self):
        for owner_mismatch in (True,False):
            endpoint,raw,_,native=self.fixture()
            native.process_identity.side_effect=[('wrong-owner',1,1)] if owner_mismatch else [(endpoint.owner_sid,1,1),(endpoint.owner_sid,2,1)]
            with self.subTest(owner=owner_mismatch),patch.object(t,'_Windows',return_value=native),self.assertRaises(t.ReadTransportError) as error:t.call_once(endpoint,raw)
            self.assertEqual('DENIED',error.exception.code);self.assertFalse(error.exception.request_submitted);native.write.assert_not_called()
            self.assertEqual(0 if owner_mismatch else 1,native.wait_available.call_count)
            self.assertEqual(0 if owner_mismatch else 1,native.k.CreateFileW.call_count)

    def test_failed_write_keeps_submission_uncertain_and_never_reopens_or_replays(self):
        endpoint,raw,_,native=self.fixture();native.write.side_effect=WorkspaceError('UNAVAILABLE')
        with patch.object(t,'_Windows',return_value=native),self.assertRaises(t.ReadTransportError) as error:t.call_once(endpoint,raw)
        self.assertTrue(error.exception.request_submitted);native.wait_available.assert_called_once();native.k.CreateFileW.assert_called_once();native.write.assert_called_once();native.read.assert_not_called();native.k.CloseHandle.assert_called_once_with(123)

    def test_native_wait_uses_remaining_milliseconds_and_never_the_infinite_or_default_value(self):
        for remaining,expected in ((2.25,2250),(.0001,1)):
            native=Mock()
            with self.subTest(remaining=remaining),patch.object(t.time,'monotonic',return_value=10):t._Windows.wait_available(native,'local-pipe',10+remaining)
            native.k.WaitNamedPipeW.assert_called_once_with('local-pipe',expected);native.check.assert_called_once()

    def test_expired_budget_and_native_wait_overshoot_never_continue_to_open(self):
        for times in ((10.0,),(9.0,10.0)):
            native=Mock()
            with self.subTest(times=times),patch.object(t.time,'monotonic',side_effect=times),self.assertRaises(WorkspaceError) as error:t._Windows.wait_available(native,'local-pipe',10.0)
            self.assertEqual('UNAVAILABLE',error.exception.code);self.assertEqual(0 if len(times)==1 else 1,native.k.WaitNamedPipeW.call_count)


if __name__=='__main__':unittest.main()
