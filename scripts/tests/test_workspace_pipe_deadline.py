"""Deterministic budget checks; native identity is covered by the Windows suite."""
import hashlib
from types import SimpleNamespace
import threading
import unittest
from unittest.mock import Mock,patch
from server_extensions.worker_workspaces import local_pipe as t
from server_extensions.worker_workspaces.workspace_provider import WorkspaceError


class PipeDeadlineTest(unittest.TestCase):
    def fixture(self,*,adapter_seconds=.4,accept_failure=False,ack_failure=False):
        clock=[0.0];deadlines=[];reply=b'fixture-reply';native=Mock()
        def accepted(operation,deadline):
            self.assertEqual(3.0,deadline)
            if accept_failure:raise WorkspaceError('UNAVAILABLE')
            clock[0]=2.8
        native.wait.side_effect=accepted
        pipe=object.__new__(t.LocalReadPipe);pipe._lock=threading.RLock();pipe._closed=False;pipe._serving=False;pipe._handle=123
        pipe._listening=threading.Event();pipe._windows=native;pipe.endpoint=SimpleNamespace(owner_sid='fixture-owner')
        def read(limit,deadline):
            deadlines.append(('read',deadline));clock[0]+=.1
            if limit==35:
                if ack_failure:raise WorkspaceError('UNAVAILABLE')
                return b'ACK'+hashlib.sha256(reply).digest()
            return b'fixture-request'
        def handle(raw):clock[0]+=adapter_seconds;return reply
        def write(raw,deadline):deadlines.append(('write',deadline));clock[0]+=.1
        pipe._read=Mock(side_effect=read);pipe._write=Mock(side_effect=write);pipe._adapter=SimpleNamespace(handle=Mock(side_effect=handle))
        return pipe,clock,deadlines,native

    def test_late_accepted_client_has_a_full_single_exchange_budget(self):
        pipe,clock,deadlines,native=self.fixture()
        with patch.object(t.time,'monotonic',side_effect=lambda:clock[0]):self.assertEqual('served',pipe.serve_one(timeout=3))
        self.assertEqual([('read',5.8),('write',5.8),('read',5.8)],deadlines)
        native.client_owner.assert_called_once();pipe._adapter.handle.assert_called_once_with(b'fixture-request')
        self.assertFalse(pipe._serving);self.assertFalse(pipe._listening.is_set());native.k.DisconnectNamedPipe.assert_called_once_with(123)

    def test_exchange_expiry_does_not_write_or_extend_for_an_expensive_adapter(self):
        pipe,clock,deadlines,native=self.fixture(adapter_seconds=3.1)
        with patch.object(t.time,'monotonic',side_effect=lambda:clock[0]),self.assertRaises(WorkspaceError) as error:pipe.serve_one(timeout=3)
        self.assertEqual('UNAVAILABLE',error.exception.code);pipe._write.assert_not_called();self.assertEqual([('read',5.8)],deadlines)
        pipe._adapter.handle.assert_called_once();self.assertFalse(pipe._serving)

    def test_idle_accept_and_missing_ack_stay_bounded_without_replay(self):
        for kind in ('accept','ack'):
            with self.subTest(kind=kind):
                pipe,clock,deadlines,native=self.fixture(accept_failure=kind=='accept',ack_failure=kind=='ack')
                with patch.object(t.time,'monotonic',side_effect=lambda:clock[0]),self.assertRaises(WorkspaceError) as error:pipe.serve_one(timeout=3)
                self.assertEqual('UNAVAILABLE',error.exception.code);self.assertFalse(pipe._serving)
                self.assertEqual(0 if kind=='accept' else 1,pipe._adapter.handle.call_count)
                self.assertEqual([] if kind=='accept' else [('read',5.8),('write',5.8),('read',5.8)],deadlines)


if __name__=='__main__':unittest.main()
