"""Actual Windows pipe threads with explicitly injected disposable owner policy."""
from dataclasses import replace
import hashlib,json,os,socket,tempfile,threading,time,unittest
from pathlib import Path
from server_extensions.worker_workspaces import broker_enrollment as e,local_pipe as p,read_host as h
from server_extensions.worker_workspaces.workspace_provider import WorkspaceError


class ReadHostNativeTest(unittest.TestCase):
    def setUp(self):
        self.parent=Path(os.environ['TEMP']).resolve(strict=True);self.assertNotEqual('c:',self.parent.drive.lower());self.temp=tempfile.TemporaryDirectory(prefix='read-host-',dir=self.parent);self.base=Path(self.temp.name).resolve(strict=True);self.addCleanup(self.cleanup)
        self.root=self.base/'project';self.root.mkdir();(self.root/'note.txt').write_bytes(b'host-owned read\n');self.scope='1'*64;self.device=socket.gethostname().upper()
        sid,started,session=p._Windows().process_identity(os.getpid());self.owner=e.OwnerObservation(self.device,sid,os.getpid(),started,max(1,session),True)
        self.policy=json.dumps(dict(schema=1,device=self.device,owner_sid=sid,grants=[dict(scope_id=self.scope,project_ids=['fixture'])])).encode();self.registry=e.RegistrySnapshot('a'*64,({'project_id':'fixture'},));self.validations=0
        def validate(ident,rows):self.validations+=1;return dict(id=ident,root=str(self.root),device=self.device)
        self.session=e.EnrolledReadSession(scope_id=self.scope,read_policy=lambda:self.policy,read_registry=lambda:self.registry,validate_project=validate,observe=lambda:self.owner)
        self.host=h.ReadHost([self.session]);self.addCleanup(self.stop);self.sequence=0
    def cleanup(self):
        if self.base.exists():self.assertTrue(self.base.resolve(strict=True).is_relative_to(self.parent) and self.base!=self.parent)
        self.temp.cleanup()
    def stop(self):self.host.close();self.assertTrue(self.host.wait_closed())
    def ready(self):
        deadline=time.monotonic()+5
        while time.monotonic()<deadline:
            status=self.host.statuses()[0]
            if status.state=='ready':return status.endpoint
            self.assertEqual('starting',status.state);threading.Event().wait(.01)
        self.fail('Native listener did not reach ready')
    def start(self):
        self.host.start();return self.ready()
    def frame(self,endpoint,operation,**fields):
        self.sequence+=1;return json.dumps(dict(schema=1,request_id=f'{self.sequence:032x}',worker_instance=endpoint.worker_instance,payload=dict(schema=1,scope_id=self.scope,operation=operation,project_id='fixture',**fields))).encode()
    def call(self,endpoint,operation,**fields):
        self.assertEqual(endpoint,self.ready());return json.loads(p.call_once(endpoint,self.frame(endpoint,operation,**fields),timeout=10))
    def test_explicit_host_start_serves_real_prepare_list_and_preview_without_receipts(self):
        self.assertEqual('starting',self.host.statuses()[0].state);endpoint=self.start();token=self.call(endpoint,'prepare')['result']['binding_token'];row=self.call(endpoint,'list',binding_token=token,relative_path='',entry_token=None)['result']['entries'][0]
        result=self.call(endpoint,'preview',binding_token=token,relative_path=row['relative_path'],entry_token=row['entry_token'])['result'];self.assertEqual('host-owned read\n',result['text']);self.assertEqual(hashlib.sha256(b'host-owned read\n').hexdigest(),result['preview_sha256'])
        self.assertEqual(['note.txt'],[x.name for x in self.root.iterdir()]);self.assertGreater(self.validations,0)
    def test_same_frame_on_next_connection_is_duplicate_without_another_project_read(self):
        endpoint=self.start();raw=self.frame(endpoint,'prepare');self.assertEqual('ok',json.loads(p.call_once(endpoint,raw))['status']);count=self.validations
        self.assertEqual(endpoint,self.ready());result=json.loads(p.call_once(endpoint,raw));self.assertEqual('DUPLICATE',result['code']);self.assertEqual(count,self.validations);self.assertEqual(endpoint,self.ready())
    def test_policy_revocation_retires_idle_host_and_never_recreates_it_on_restore(self):
        endpoint=self.start();self.policy=self.policy.replace(b'fixture',b'different');self.assertTrue(self.host.wait_closed(timeout=8));status=self.host.statuses()[0]
        self.assertEqual('held',status.state);self.assertIsNone(status.endpoint);self.assertTrue(self.session._provider._retired);self.policy=self.policy.replace(b'different',b'fixture')
        with self.assertRaises(WorkspaceError):self.host.start()
        with self.assertRaises(p.ReadTransportError):p.call_once(endpoint,self.frame(endpoint,'prepare'))
    def test_close_cancels_pending_real_accept_and_keeps_endpoint_out_of_projection(self):
        self.start();self.host.close();self.assertTrue(self.host.wait_closed());self.assertEqual('closed',self.host.statuses()[0].state);self.assertIsNone(self.host.statuses()[0].endpoint)
    def test_old_endpoint_and_tokens_do_not_survive_host_closure(self):
        endpoint=self.start();token=self.call(endpoint,'prepare')['result']['binding_token'];self.host.close();self.assertTrue(self.host.wait_closed())
        with self.assertRaises(p.ReadTransportError) as error:p.call_once(endpoint,self.frame(endpoint,'list',binding_token=token,relative_path='',entry_token=None))
        self.assertFalse(error.exception.request_submitted);self.assertTrue(self.session._provider._retired)

    def test_fresh_private_projection_serves_the_exact_enrolled_native_request(self):
        endpoint=self.start();raw=h.encode_projection(self.host.statuses(),device=self.device,observed=time.time());frame=self.frame(endpoint,'prepare')
        reply=json.loads(h.call_projected(raw,frame,scope_id=self.scope,device=self.device,now=time.time()))
        self.assertEqual('ok',reply['status']);self.assertEqual(endpoint.worker_instance,reply['worker_instance']);self.assertEqual(self.scope,reply['result']['scope_id']);self.assertGreater(self.validations,0)
    def test_spoofed_projection_process_start_is_denied_before_submission_or_root_read(self):
        endpoint=self.start();status=h.ReadHostStatus(self.scope,'ready',replace(endpoint,server_started=endpoint.server_started+1));raw=h.encode_projection([status],device=self.device,observed=time.time())
        with self.assertRaises(p.ReadTransportError) as error:h.call_projected(raw,self.frame(endpoint,'prepare'),scope_id=self.scope,device=self.device,now=time.time())
        self.assertEqual('DENIED',error.exception.code);self.assertFalse(error.exception.request_submitted);self.assertEqual(0,self.validations)
