"""Actual immutable-package wrapper talking to a disposable Windows read host."""
from pathlib import Path
import hashlib,json,os,socket,sys,tempfile,threading,time,unittest
from types import SimpleNamespace
from unittest.mock import patch
from server_extensions.worker_workspaces import host_integration as w


class HostIntegrationNativeTest(unittest.TestCase):
    def test_full_pinned_wrapper_reads_and_refuses_duplicate_or_foreign_project(self):
        parent=Path(os.environ['TEMP']).resolve(strict=True);self.assertNotEqual('c:',parent.drive.lower())
        temporary=tempfile.TemporaryDirectory(prefix='full-wrapper-native-',dir=parent);base=Path(temporary.name).resolve(strict=True);prior=set(sys.modules)
        def cleanup():
            for name in set(sys.modules)-prior:
                if name.startswith('signal_grove_workspace_'+w.SOURCE_DIGEST):sys.modules.pop(name,None)
            self.assertTrue(base.is_relative_to(parent) and base!=parent);temporary.cleanup()
        self.addCleanup(cleanup)
        package=base/'workspace-read-runtime'/w.SOURCE_DIGEST/'workspace_reads_runtime';package.mkdir(parents=True);files={}
        for name in w.NAMES:
            raw=Path(w.__file__).with_name(name).read_bytes().replace(b'\r\n',b'\n');(package/name).write_bytes(raw);files[name]=hashlib.sha256(raw).hexdigest()
        (package.parent/'manifest.json').write_text(json.dumps(dict(schema=1,files=files)),encoding='utf8')
        root=base/'project';root.mkdir();text=b'Full wrapper native preview\n';(root/'note.txt').write_bytes(text)
        (base/'state').mkdir();scope='1'*64;device=socket.gethostname().upper();worker=SimpleNamespace(DEVICE=device)
        with patch.object(w,'HERE',base):
            e,f,h,p=w._runtime();sid,started,session_id=p._Windows().process_identity(os.getpid())
            # Explicit disposable owner observation; production enrollment is separate.
            owner=e.OwnerObservation(device,sid,os.getpid(),started,max(1,session_id),True)
            raw_policy=json.dumps(dict(schema=1,device=device,owner_sid=sid,grants=[dict(scope_id=scope,project_ids=['fixture'])])).encode()
            (base/'workspace-read-policy.json').write_bytes(raw_policy)
            enrolled=e.EnrolledReadSession(scope_id=scope,read_policy=lambda:raw_policy,read_registry=lambda:e.RegistrySnapshot('a'*64,({'project_id':'fixture'},)),validate_project=lambda ident,rows:dict(id=ident,root=str(root),device=device),observe=lambda:owner)
            host=h.ReadHost([enrolled])
            try:
                host.start()
                def publish():
                    deadline=time.monotonic()+5
                    while time.monotonic()<deadline:
                        statuses=host.statuses()
                        if statuses[0].state=='ready':
                            (base/'state/workspace-reads.safe.json').write_bytes(h.encode_projection(statuses,device=device,observed=time.time()));return statuses[0].endpoint
                        self.assertEqual('starting',statuses[0].state);threading.Event().wait(.01)
                    self.fail('Disposable host never became ready')
                endpoint=publish();status=w.rpc(worker,dict(action='workspace_status',scope_id=scope));self.assertEqual(endpoint.worker_instance,status['worker_instance'])
                sequence=0
                def frame(operation,**fields):
                    nonlocal sequence
                    sequence+=1
                    return dict(schema=1,request_id=f'{sequence:032x}',worker_instance=endpoint.worker_instance,payload=dict(schema=1,scope_id=scope,operation=operation,project_id='fixture',**fields))
                def read(request):
                    publish();value=w.rpc(worker,dict(action='workspace_read',scope_id=scope,frame=request));self.assertEqual('reply',value['transport']);return value['reply']
                with patch.object(p,'call_once',wraps=p.call_once) as native:
                    prepared_request=frame('prepare');prepared=read(prepared_request);self.assertEqual('ok',prepared['status']);binding=prepared['result']['binding_token']
                    listed=read(frame('list',binding_token=binding,relative_path='',entry_token=None));row=next(x for x in listed['result']['entries'] if x['relative_path']=='note.txt')
                    preview=read(frame('preview',binding_token=binding,relative_path='note.txt',entry_token=row['entry_token']))
                    self.assertEqual(text.decode(),preview['result']['text']);self.assertEqual(hashlib.sha256(text).hexdigest(),preview['result']['preview_sha256'])
                    duplicate=read(prepared_request);self.assertEqual('DUPLICATE',duplicate['code']);self.assertNotIn('result',duplicate)
                    self.assertEqual(4,native.call_count)
                    foreign=frame('prepare');foreign['payload']['project_id']='foreign'
                    denied=w.rpc(worker,dict(action='workspace_read',scope_id=scope,frame=foreign));self.assertEqual(dict(transport='error',code='DENIED',request_submitted=False),denied);self.assertEqual(4,native.call_count)
            finally:host.close();self.assertTrue(host.wait_closed())
