"""Portable framing/locator checks do not certify native OS authentication."""
from dataclasses import replace
import json
import unittest
from unittest.mock import patch
from server_extensions.worker_workspaces import local_pipe as t
from server_extensions.worker_workspaces.workspace_provider import WorkspaceError


class LocalPipePolicyTest(unittest.TestCase):
    def setUp(self):
        self.endpoint=t.PipeEndpoint(r'\\.\pipe\SignalGrove.Workspace.'+'a'*32,'12345678-1234-1234-1234-123456789abc','1'*64,'S-1-5-21-1-2-3-1000',1,1,1)
        self.request=dict(schema=1,request_id='2'*32,worker_instance=self.endpoint.worker_instance,payload=dict(schema=1,scope_id=self.endpoint.scope_id,operation='prepare',project_id='fixture'))
    def test_locator_rejects_remote_arbitrary_pipe_and_invalid_identity_metadata(self):
        for values in [dict(address=r'\\remote\pipe\SignalGrove.Workspace.'+'a'*32),dict(address=r'\\.\pipe\other'),dict(server_pid=True),dict(server_pid=0),dict(server_started=0),dict(server_session=-1),dict(owner_sid='private'),dict(scope_id='other'),dict(worker_instance='other')]:
            with self.subTest(fields=sorted(values)),self.assertRaises(WorkspaceError):t._endpoint(replace(self.endpoint,**values))
        self.assertIs(self.endpoint,t._endpoint(self.endpoint))
    def test_deadlines_reject_nonfinite_unbounded_and_boolean_values(self):
        for value in [True,None,'3',0,-1,.001,11,float('inf'),float('nan'),10**1000]:
            with self.subTest(value_type=type(value).__name__),self.assertRaises(WorkspaceError):t._deadline(value)
        self.assertGreater(t._deadline(1),0)
    def reply(self,**fields):
        value=dict(schema=1,request_id=self.request['request_id'],worker_instance=self.endpoint.worker_instance,status='error',code='BUSY');value.update(fields);return json.dumps(value).encode()
    def test_reply_rejects_duplicate_fields_wrong_correlation_unknown_codes_and_nonfinite_values(self):
        for raw in [b'{}',b'{"schema":1,"schema":1}',self.reply(request_id='3'*32),self.reply(worker_instance='other'),self.reply(code='private exception'),self.reply(extra='unknown'),self.reply(schema=True),self.reply(status='unknown'),self.reply(code=float('nan'))]:
            with self.subTest(raw=raw[:50]),self.assertRaises(WorkspaceError):t._reply(raw,self.request,self.endpoint)
        self.assertEqual(self.reply(),t._reply(self.reply(),self.request,self.endpoint))
    def test_wrong_scope_is_refused_before_native_api_or_submission(self):
        request={**self.request,'payload':{**self.request['payload'],'scope_id':'3'*64}}
        with patch.object(t,'_Windows') as native,self.assertRaises(t.ReadTransportError) as error:t.call_once(self.endpoint,json.dumps(request).encode())
        native.assert_not_called();self.assertEqual('CHANGED',error.exception.code);self.assertFalse(error.exception.request_submitted)
    def test_unsupported_platform_has_no_unrestricted_transport_fallback(self):
        with patch.object(t.sys,'platform','linux'),self.assertRaises(t.ReadTransportError) as error:t.call_once(self.endpoint,json.dumps(self.request).encode())
        self.assertEqual('UNSUPPORTED',error.exception.code);self.assertFalse(error.exception.request_submitted)
    def test_endpoint_representation_omits_address_scope_and_os_account(self):
        value=repr(self.endpoint);self.assertNotIn(self.endpoint.address,value);self.assertNotIn(self.endpoint.scope_id,value);self.assertNotIn(self.endpoint.owner_sid,value)
