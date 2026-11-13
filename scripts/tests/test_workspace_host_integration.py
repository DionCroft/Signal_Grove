"""Full existing-broker RPC wrapper: policy changes precede native admission."""
from pathlib import Path
from types import SimpleNamespace
import hashlib,json,os,socket,sys,tempfile,unittest
from unittest.mock import Mock,patch
from server_extensions.worker_workspaces import broker_enrollment as e,host_integration as w,local_pipe as p,read_host as h


class HostIntegrationPolicyTest(unittest.TestCase):
    def setUp(self):
        self.scope='1'*64;self.device=socket.gethostname().upper();self.sid='S-1-5-21-1-2-3-1000'
        self.endpoint=p.PipeEndpoint(r'\\.\pipe\SignalGrove.Workspace.'+'a'*32,'12345678-1234-1234-1234-123456789abc',self.scope,self.sid,1,1,1)
        self.frame=dict(schema=1,request_id='2'*32,worker_instance=self.endpoint.worker_instance,payload=dict(schema=1,scope_id=self.scope,operation='prepare',project_id='fixture'))
        self.policy=json.dumps(dict(schema=1,device=self.device,owner_sid=self.sid,grants=[dict(scope_id=self.scope,project_ids=['fixture'])])).encode()
        self.policy_reads=0;self.projection_reads=0;self.paths=[];self.clock=0.;self.states=['ready'];self.change=None
        self.reader=SimpleNamespace(read_operator_file=Mock(side_effect=self.load))
        self.worker=SimpleNamespace(DEVICE=self.device,project=Mock(),projects=Mock())
        self.native=Mock(return_value=json.dumps(dict(schema=1,status='ok',result={'fixture':True})).encode())
    def raw(self,state='ready',*,age=0,endpoint=None):
        value=h.ReadHostStatus(self.scope,state,(endpoint or self.endpoint) if state=='ready' else None,'DENIED' if state=='held' else None)
        return h.encode_projection([value],device=self.device,observed=100+self.clock-age)
    def load(self,path,*,maximum):
        self.assertEqual(32768,maximum);self.paths.append(Path(path).name)
        if Path(path).name=='workspace-read-policy.json':
            self.policy_reads+=1
            return self.change(self.policy_reads) if self.change else self.policy
        self.assertEqual('workspace-reads.safe.json',Path(path).name);self.projection_reads+=1
        state=self.states[min(self.projection_reads-1,len(self.states)-1)]
        return state if type(state) is bytes else self.raw(state)
    def call(self,data=None):
        if data is None:data=dict(action='workspace_read',scope_id=self.scope,frame=self.frame)
        waiter=Mock();waiter.wait.side_effect=lambda value:setattr(self,'clock',self.clock+value)
        windows=Mock();windows.process_identity.return_value=(self.sid,1,1)
        with patch.object(w,'_runtime',return_value=(e,self.reader,h,p)),patch.object(p,'_Windows',return_value=windows),patch.object(p,'call_once',self.native),patch.object(h.time,'time',side_effect=lambda:100+self.clock),patch.object(h.time,'monotonic',side_effect=lambda:self.clock),patch.object(h.threading,'Event',return_value=waiter):
            result=w.rpc(self.worker,data)
        self.worker.project.assert_not_called();self.worker.projects.assert_not_called()
        self.assertLessEqual(set(self.paths),{'workspace-read-policy.json','workspace-reads.safe.json'})
        return result
    def test_starting_rechecks_exact_policy_then_admits_one_original_frame(self):
        self.states=['starting','starting','ready'];reply=self.call()
        self.assertEqual('reply',reply['transport']);self.assertTrue(reply['reply']['result']['fixture'])
        self.assertEqual(4,self.policy_reads);self.assertEqual(3,self.projection_reads);self.native.assert_called_once()
        args=self.native.call_args;self.assertEqual(self.frame,json.loads(args.args[1]));self.assertEqual(10,args.kwargs['deadline'])
    def test_policy_byte_revision_change_never_restores_or_rebinds_the_wait(self):
        self.states=['starting','ready'];self.change=lambda n:self.policy if n<3 else self.policy+b' '
        self.assertEqual(dict(transport='error',code='CHANGED',request_submitted=False),self.call())
        self.assertEqual(3,self.policy_reads);self.assertEqual(1,self.projection_reads);self.native.assert_not_called()
    def test_revoked_grant_is_not_satisfied_by_a_current_ready_endpoint(self):
        self.change=lambda n:self.policy if n==1 else self.policy.replace(b'fixture',b'another')
        self.assertEqual('CHANGED',self.call()['code']);self.assertEqual(0,self.projection_reads);self.native.assert_not_called()
    def test_foreign_frame_scope_or_project_is_denied_before_projection(self):
        for field,value in [('scope_id','3'*64),('project_id','another')]:
            with self.subTest(field=field):
                old=self.frame['payload'][field];self.frame['payload'][field]=value
                try:self.assertEqual(dict(transport='error',code='DENIED',request_submitted=False),self.call())
                finally:self.frame['payload'][field]=old
        self.assertEqual(0,self.projection_reads);self.native.assert_not_called()
    def test_invalid_frames_are_refused_before_projection_or_native_io(self):
        for mutate in (lambda x:x.update(schema=True),lambda x:x.update(extra='unexpected'),lambda x:x['payload'].update(project_id=[]),lambda x:x.update(worker_instance=float('nan'))):
            frame=json.loads(json.dumps(self.frame));mutate(frame)
            with self.subTest(frame=frame):self.assertEqual(dict(transport='error',code='INVALID',request_submitted=False),self.call(dict(action='workspace_read',scope_id=self.scope,frame=frame)))
        self.assertEqual(0,self.projection_reads);self.native.assert_not_called()
    def test_held_stale_and_changed_worker_refuse_without_native_submission(self):
        from dataclasses import replace
        for raw in (self.raw('held'),self.raw(age=11),self.raw(endpoint=replace(self.endpoint,worker_instance='12345678-1234-1234-1234-123456789abd'))):
            self.states=[raw]
            with self.subTest(raw=raw):self.assertFalse(self.call()['request_submitted'])
        self.native.assert_not_called()
    def test_policy_loader_exception_is_redacted_before_admission(self):
        def changed(n):
            if n>1:raise RuntimeError('private diagnostic must not escape')
            return self.policy
        self.change=changed;result=self.call()
        self.assertEqual(dict(transport='error',code='UNAVAILABLE',request_submitted=False),result);self.assertNotIn('private',json.dumps(result));self.native.assert_not_called()
    def test_uncertain_native_outcome_remains_submitted_and_is_never_retried(self):
        self.native.side_effect=p.ReadTransportError('UNAVAILABLE',request_submitted=True)
        self.assertEqual(dict(transport='error',code='UNAVAILABLE',request_submitted=True),self.call())
        self.assertEqual(1,self.projection_reads);self.assertEqual(2,self.policy_reads);self.native.assert_called_once()
    def test_unsubmitted_busy_is_returned_without_reobservation_or_retry(self):
        self.native.side_effect=p.ReadTransportError('BUSY',request_submitted=False)
        self.assertEqual(dict(transport='error',code='BUSY',request_submitted=False),self.call())
        self.assertEqual(1,self.projection_reads);self.native.assert_called_once()
    def test_status_remains_an_observation_without_a_read_or_admission(self):
        result=self.call(dict(action='workspace_status',scope_id=self.scope))
        self.assertEqual(dict(transport='status',scope_id=self.scope,device=self.device,state='ready',worker_instance=self.endpoint.worker_instance,failure_code=None),result)
        self.assertEqual(1,self.policy_reads);self.assertEqual(1,self.projection_reads);self.native.assert_not_called()
    def test_unknown_rpc_shape_is_rejected_without_loading_policy(self):
        for data in ({'action':'workspace_read','scope_id':self.scope,'frame':self.frame,'command':'anything'},{'action':'execute'},{},None):
            with self.subTest(data=data),self.assertRaises(ValueError):self.call(data if data is not None else {'action':None})
        self.assertEqual(0,self.policy_reads);self.native.assert_not_called()


class HostIntegrationPackageTest(unittest.TestCase):
    def test_pinned_package_loads_and_cached_import_does_not_hide_changed_bytes(self):
        parent=Path(os.environ['TEMP'] if os.name=='nt' else tempfile.gettempdir()).resolve(strict=True)
        if os.name=='nt':self.assertNotEqual('c:',parent.drive.lower())
        temporary=tempfile.TemporaryDirectory(prefix='wrapper-package-',dir=parent);base=Path(temporary.name).resolve(strict=True)
        prior=set(sys.modules)
        def cleanup():
            for name in set(sys.modules)-prior:
                if name.startswith('signal_grove_workspace_'+w.SOURCE_DIGEST):sys.modules.pop(name,None)
            self.assertTrue(base.is_relative_to(parent) and base!=parent);temporary.cleanup()
        self.addCleanup(cleanup)
        package=base/'workspace-read-runtime'/w.SOURCE_DIGEST/'workspace_reads_runtime';package.mkdir(parents=True)
        source=Path(w.__file__).parent;files={}
        for name in w.NAMES:
            raw=(source/name).read_bytes().replace(b'\r\n',b'\n');(package/name).write_bytes(raw);files[name]=hashlib.sha256(raw).hexdigest()
        self.assertEqual(w.SOURCE_DIGEST,hashlib.sha256(json.dumps(files,sort_keys=True,separators=(',',':')).encode()).hexdigest())
        (package.parent/'manifest.json').write_text(json.dumps(dict(schema=1,files=files)),encoding='utf8')
        with patch.object(w,'HERE',base):
            modules=w._runtime();self.assertEqual(4,len(modules));self.assertTrue(callable(modules[2].call_current_projected))
            target=package/'read_host.py';target.write_bytes(target.read_bytes()+b'\n')
            with self.assertRaisesRegex(ValueError,'Read runtime bytes'):w._runtime()
