"""Portable host-loop checks; fake pipes/observations are not native acceptance."""
from dataclasses import replace
import json,os,socket,threading,unittest
from server_extensions.worker_workspaces import broker_enrollment as e,read_host as h
from server_extensions.worker_workspaces.local_pipe import IdentityRevertFailure
from server_extensions.worker_workspaces.workspace_provider import WorkspaceError


class FakePipe:
    def __init__(self,adapter,*,failure=None):
        self.adapter=adapter;self.endpoint=object();self.accepting=False;self.closed=threading.Event();self.entered=threading.Event();self.failure=failure;self.calls=0
    def serve_one(self,*,timeout):
        self.calls+=1;self.accepting=True;self.entered.set()
        if self.failure is not None:raise self.failure
        self.closed.wait(timeout);raise WorkspaceError('UNAVAILABLE')
    def close(self):self.closed.set();self.adapter.close()


class ReadHostPolicyTest(unittest.TestCase):
    def setUp(self):
        self.scope='1'*64;self.device=socket.gethostname().upper();self.owner=e.OwnerObservation(self.device,'S-1-5-21-1-2-3-1000',os.getpid(),1,1,True)
        self.raw=json.dumps(dict(schema=1,device=self.device,owner_sid=self.owner.owner_sid,grants=[dict(scope_id=self.scope,project_ids=['fixture'])])).encode()
        self.registry=e.RegistrySnapshot('a'*64,({'project_id':'fixture'},));self.observations=0;self.pipes=[]
        def observe():self.observations+=1;return self.owner
        self.session=e.EnrolledReadSession(scope_id=self.scope,read_policy=lambda:self.raw,read_registry=lambda:self.registry,validate_project=lambda ident,rows:dict(id=ident,root='E:\\Projects\\Fixture',device=self.device),observe=observe)
        self.addCleanup(self.session.adapter.close)
    def factory(self,adapter):
        pipe=FakePipe(adapter);self.pipes.append(pipe);return pipe
    def host(self,**kwargs):
        host=h.ReadHost([self.session],pipe_factory=kwargs.get('pipe_factory',self.factory));self.addCleanup(lambda:self.stop(host));return host
    def stop(self,host):host.close();self.assertTrue(host.wait_closed())
    def test_construction_starts_no_thread_or_listener_and_requires_explicit_start(self):
        host=self.host();self.assertEqual([],self.pipes);self.assertEqual('starting',host.statuses()[0].state);self.assertIsNone(host.statuses()[0].endpoint)
        host.start();self.assertTrue(self.pipes[0].entered.wait(2));self.assertEqual('ready',host.statuses()[0].state)
        with self.assertRaises(WorkspaceError):host.start()
    def test_duplicate_or_untyped_sessions_never_create_a_listener(self):
        for sessions in [[],[object()],[self.session,self.session],tuple([self.session]*9)]:
            with self.assertRaises(WorkspaceError):h.ReadHost(sessions,pipe_factory=self.factory)
        self.assertEqual([],self.pipes)
    def test_close_retires_provider_cancels_pipe_and_never_publishes_old_endpoint(self):
        host=self.host().start();self.assertTrue(self.pipes[0].entered.wait(2));endpoint=host.statuses()[0].endpoint;self.assertIsNotNone(endpoint)
        host.close();self.assertTrue(host.wait_closed());status=host.statuses()[0];self.assertEqual('closed',status.state);self.assertIsNone(status.endpoint);self.assertTrue(self.session._provider._retired)
    def test_identity_revert_failure_is_held_without_callback_or_automatic_restart(self):
        def factory(adapter):
            pipe=FakePipe(adapter,failure=IdentityRevertFailure('private token detail'));self.pipes.append(pipe);return pipe
        host=self.host(pipe_factory=factory).start();self.assertTrue(host.wait_closed());status=host.statuses()[0]
        self.assertEqual('held',status.state);self.assertEqual('IDENTITY_FAILURE',status.failure_code);self.assertIsNone(status.endpoint);self.assertEqual(1,len(self.pipes));self.assertEqual(1,self.pipes[0].calls)
        self.assertNotIn('private token detail',repr(status))
    def test_external_pipe_closure_cannot_cause_a_busy_retry_loop(self):
        def factory(adapter):
            pipe=FakePipe(adapter);self.pipes.append(pipe);pipe.close();return pipe
        host=self.host(pipe_factory=factory).start();self.assertTrue(host.wait_closed());self.assertEqual('held',host.statuses()[0].state);self.assertEqual(0,self.pipes[0].calls)
    def test_owner_loss_before_start_closes_permanently_without_creating_a_pipe(self):
        host=self.host();self.owner=replace(self.owner,active=False)
        with self.assertRaises(WorkspaceError):host.start()
        self.assertEqual([],self.pipes);self.assertEqual('closed',host.statuses()[0].state);self.assertTrue(self.session._provider._retired)
    def test_start_failure_closes_already_constructed_sessions_without_replacement(self):
        def factory(adapter):raise ValueError('private setup failure')
        host=self.host(pipe_factory=factory)
        with self.assertRaises(ValueError):host.start()
        self.assertEqual('closed',host.statuses()[0].state)
        with self.assertRaises(WorkspaceError):host.start()

    def test_thread_start_failure_retires_pipe_and_join_never_targets_an_unstarted_thread(self):
        from unittest.mock import patch
        host=self.host()
        with patch.object(h.threading.Thread,'start',side_effect=RuntimeError('fixture thread allocation failure')):
            with self.assertRaises(RuntimeError):host.start()
        self.assertTrue(host.wait_closed());self.assertEqual('closed',host.statuses()[0].state);self.assertTrue(self.session._provider._retired);self.assertTrue(self.pipes[0].closed.is_set())

class ReadProjectionPolicyTest(unittest.TestCase):
    def setUp(self):
        from server_extensions.worker_workspaces.local_pipe import PipeEndpoint
        self.scope='1'*64;self.device=socket.gethostname().upper();self.endpoint=PipeEndpoint(r'\\.\pipe\SignalGrove.Workspace.'+'a'*32,'12345678-1234-1234-1234-123456789abc',self.scope,'S-1-5-21-1-2-3-1000',1,1,1)
        self.status=h.ReadHostStatus(self.scope,'ready',self.endpoint)
    def raw(self):return h.encode_projection([self.status],device=self.device,observed=100)
    def test_fresh_closed_projection_keeps_exact_scope_and_endpoint(self):
        self.assertEqual(self.status,h.select_projection(self.raw(),scope_id=self.scope,device=self.device,now=110))
        self.assertNotIn(self.scope,repr(self.status));self.assertNotIn(self.endpoint.address,repr(self.status))
    def test_expired_future_nonfinite_duplicate_and_foreign_device_are_refused(self):
        import math
        for now in [99,111,math.inf,math.nan,True,10**1000]:
            with self.assertRaises(WorkspaceError):h.select_projection(self.raw(),scope_id=self.scope,device=self.device,now=now)
        raw=self.raw();value=json.loads(raw);value['sessions']*=2
        for data in [json.dumps(value).encode(),b'{"schema":1,"schema":1}',raw+b' '*32768]:
            with self.assertRaises(WorkspaceError):h.select_projection(data,scope_id=self.scope,device=self.device,now=100)
        with self.assertRaises(WorkspaceError):h.select_projection(raw,scope_id=self.scope,device='OTHER',now=100)
    def test_nonready_state_cannot_publish_an_old_endpoint_or_arbitrary_failure_detail(self):
        for status in [replace(self.status,state='closed'),replace(self.status,state='held',failure_code='DENIED'),replace(self.status,failure_code='private diagnostic')]:
            with self.assertRaises(WorkspaceError):h.encode_projection([status],device=self.device,observed=100)
        status=replace(self.status,state='held',endpoint=None,failure_code='IDENTITY_FAILURE')
        raw=h.encode_projection([status],device=self.device,observed=100);self.assertEqual(status,h.select_projection(raw,scope_id=self.scope,device=self.device,now=100))
    def test_request_cannot_select_a_different_authenticated_scope_before_native_io(self):
        from unittest.mock import patch
        from server_extensions.worker_workspaces import local_pipe
        frame=dict(schema=1,request_id='2'*32,worker_instance=self.endpoint.worker_instance,payload=dict(schema=1,scope_id='3'*64,operation='prepare',project_id='fixture'))
        with patch.object(local_pipe,'call_once') as native,self.assertRaises(local_pipe.ReadTransportError) as error:h.call_projected(self.raw(),json.dumps(frame).encode(),scope_id=self.scope,device=self.device,now=100)
        native.assert_not_called();self.assertFalse(error.exception.request_submitted);self.assertEqual('DENIED',error.exception.code)
    def test_stale_instance_or_inactive_projection_does_not_automatically_rebind_or_submit(self):
        from unittest.mock import patch
        from server_extensions.worker_workspaces import local_pipe
        frame=json.dumps(dict(schema=1,request_id='2'*32,worker_instance=self.endpoint.worker_instance,payload=dict(schema=1,scope_id=self.scope,operation='prepare',project_id='fixture'))).encode()
        status=replace(self.status,state='closed',endpoint=None);raw=h.encode_projection([status],device=self.device,observed=100)
        with patch.object(local_pipe,'call_once') as native,self.assertRaises(local_pipe.ReadTransportError) as error:h.call_projected(raw,frame,scope_id=self.scope,device=self.device,now=100)
        native.assert_not_called();self.assertFalse(error.exception.request_submitted)

    def test_old_worker_instance_is_not_rebound_to_the_new_endpoint(self):
        from unittest.mock import patch
        from server_extensions.worker_workspaces import local_pipe
        frame=json.dumps(dict(schema=1,request_id='2'*32,worker_instance='12345678-1234-1234-1234-123456789abd',payload=dict(schema=1,scope_id=self.scope,operation='prepare',project_id='fixture'))).encode()
        with patch.object(local_pipe,'_Windows') as native,self.assertRaises(local_pipe.ReadTransportError) as error:h.call_projected(self.raw(),frame,scope_id=self.scope,device=self.device,now=100)
        native.assert_not_called();self.assertEqual('CHANGED',error.exception.code);self.assertFalse(error.exception.request_submitted)
