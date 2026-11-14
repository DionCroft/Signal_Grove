"""Real Linux owner-file fixtures; no production policy, SSH, inference or OS faking."""
import asyncio
import json
import os
from pathlib import Path
import unittest
from unittest.mock import patch
from server_extensions.coordinator_workspaces import coordinator as c
from server_extensions.coordinator_workspaces import policy_file as p

NAME='worker-reads.json'
DEVICE='FIXTURE-WORKER'
AUTHORITY='fixture-authority'
INSTANCE='11111111-2222-4333-8444-555555555555'


class PosixPolicyFileTest(unittest.TestCase):
    def setUp(self):
        self.directory=Path(os.environ['SIGNAL_GROVE_POLICY_TEST_ROOT'])/self._testMethodName
        self.directory.mkdir(mode=0o700)
        self.context=c.NativeReadContext('uid:'+str(os.geteuid()),str(self.directory/'hermes'),'fixture-transport','fixture-authority-instance')
        self.calls=[];self.edit=None

    def policy(self, grants=None):
        if grants is None:grants=[dict(subject=self.context.subject,profile_id=self.context.profile_id,device=DEVICE,project_ids=['fixture-project'])]
        return json.dumps(dict(schema=1,authority=AUTHORITY,grants=grants)).encode()

    def write(self, data=None, path=None):
        path=path or self.directory/NAME;path.write_bytes(self.policy() if data is None else data);path.chmod(0o600)
        return path

    def read(self):return p.read_owner_document(self.directory,NAME)

    def refused(self, directory=None):
        with self.assertRaises(c.ReadRefused) as error:p.read_owner_document(directory or self.directory,NAME)
        self.assertEqual('UNAVAILABLE',str(error.exception))
        self.assertNotIn(str(self.directory),repr(error.exception))

    def service(self):return p.policy_file_service(self.directory,NAME,authority=AUTHORITY,routes={DEVICE:self.route})

    async def route(self,payload):
        self.calls.append(payload)
        if self.edit:self.edit()
        if payload['action']=='workspace_status':
            return dict(transport='status',scope_id=payload['scope_id'],device=DEVICE,state='ready',worker_instance=INSTANCE,failure_code=None)
        frame=payload['frame']
        data=dict(schema=1,scope_id=payload['scope_id'],project_id='fixture-project',worker=dict(device=DEVICE,instance_id=INSTANCE))
        return dict(transport='reply',reply=dict(schema=1,request_id=frame['request_id'],worker_instance=INSTANCE,status='ok',result=data))

    def request(self):return dict(schema=1,request_id='a'*32,worker_instance=INSTANCE,operation='prepare',project_id='fixture-project')

    def test_private_file_observations_are_stable_and_supply_exact_explicit_grants(self):
        path=self.write();first=self.read();second=self.read()
        self.assertEqual(path.read_bytes(),first.data);self.assertEqual(first,second)
        result=asyncio.run(self.service().describe(lambda:self.context,DEVICE))
        self.assertEqual(['fixture-project'],result['project_ids']);self.assertEqual(1,len(self.calls))

    def test_file_and_ancestor_permission_changes_refuse(self):
        path=self.write()
        for mode in (0o644,0o660,0o620):
            with self.subTest(file_mode=oct(mode)):
                path.chmod(mode)
                try:self.refused()
                finally:path.chmod(0o600)
        self.directory.chmod(0o770)
        try:self.refused()
        finally:self.directory.chmod(0o700)
        self.assertEqual(self.policy(),self.read().data)

    def test_leaf_and_ancestor_symlinks_never_redirect_policy_reads(self):
        target=self.directory/'target';target.mkdir(mode=0o700);original=self.write(path=target/NAME)
        (self.directory/NAME).symlink_to(original)
        self.refused()
        alias=self.directory/'alias';alias.symlink_to(target,target_is_directory=True)
        self.refused(alias);self.assertEqual(self.policy(),original.read_bytes())

    def test_hardlinks_fifo_and_directories_are_not_policy_files(self):
        path=self.write();other=self.directory/'second-link';os.link(path,other)
        self.refused();other.unlink();path.unlink()
        os.mkfifo(path,0o600);self.refused();path.unlink()
        path.mkdir(mode=0o700);self.refused()

    def test_oversize_and_changes_during_native_read_are_refused(self):
        path=self.write(b'x'*(p.MAX_POLICY+1));self.refused();self.write()
        actual_read=os.read;changed=False
        def changing_read(fd,count):
            nonlocal changed
            value=actual_read(fd,count)
            if not changed:
                changed=True;path.write_bytes(self.policy()+b'\n')
            return value
        with patch.object(p.os,'read',changing_read):self.refused()
        self.assertTrue(changed)
        self.write(b'not-json-private-fixture')
        with self.assertRaises(c.ReadRefused) as error:self.service()
        self.assertEqual('UNAVAILABLE',str(error.exception))

    def test_revocation_during_service_read_discards_contents_with_submitted_true(self):
        self.write();service=self.service();self.edit=lambda:self.write(self.policy(grants=[]))
        with self.assertRaises(c.ReadRefused) as error:asyncio.run(service.read(lambda:self.context,DEVICE,self.request()))
        self.assertEqual('DENIED',error.exception.code);self.assertTrue(error.exception.request_submitted)
        with self.assertRaises(c.ReadRefused):asyncio.run(service.describe(lambda:self.context,DEVICE))
        self.assertEqual(1,len(self.calls))

    def test_revoke_restore_bytes_by_replacement_or_inplace_cannot_reuse_a_verdict(self):
        for mode in ('replace','inplace'):
            with self.subTest(mode=mode):
                path=self.write();original=self.read();service=self.service();self.calls=[]
                def edit():
                    if mode=='inplace':
                        path.write_bytes(self.policy(grants=[]));path.write_bytes(original.data)
                    else:
                        staged=self.directory/'staged.json';self.write(self.policy(grants=[]),staged);os.replace(staged,path)
                        self.write(original.data,staged);os.replace(staged,path)
                self.edit=edit
                with self.assertRaises(c.ReadRefused) as error:asyncio.run(service.read(lambda:self.context,DEVICE,self.request()))
                self.assertEqual('CHANGED',error.exception.code);self.assertTrue(error.exception.request_submitted)
                current=self.read();self.assertEqual(original.data,current.data);self.assertNotEqual(original.version,current.version)
                self.assertEqual(1,len(self.calls))

    def test_successes_and_refusals_close_every_native_descriptor(self):
        path=self.directory/NAME;path.symlink_to(self.directory/'absent')
        before=len(os.listdir('/proc/self/fd'))
        for _ in range(8):self.refused()
        self.assertEqual(before,len(os.listdir('/proc/self/fd')))
        path.unlink();self.write()
        for _ in range(8):self.read()
        self.assertEqual(before,len(os.listdir('/proc/self/fd')))
