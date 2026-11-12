"""Portable byte-frame checks; native owner/filesystem acceptance is separate."""
import json
import socket
import unittest
from unittest.mock import Mock
from server_extensions.worker_workspaces import broker_protocol as b
from server_extensions.worker_workspaces.workspace_provider import WorkspaceError


class BrokerProtocolPolicyTest(unittest.TestCase):
    def frame(self,**changes):
        value=dict(schema=1,request_id='a'*32,worker_instance='instance',payload={});value.update(changes)
        return json.dumps(value).encode()
    def test_closed_frames_refuse_duplicate_fields_nonfinite_invalid_utf8_and_large_input(self):
        for raw in [b'',b'{}',self.frame(schema=True),self.frame(command='shell'),b'{"schema":1,"schema":1}',b'{"x":NaN}',b'\xff',b' '* (b.MAX_REQUEST_BYTES+1),self.frame(request_id='private-path'),self.frame(payload=[]),bytearray(self.frame())]:
            with self.subTest(raw=repr(raw[:40])),self.assertRaises(WorkspaceError):b.decode_frame(raw)
        self.assertEqual('a'*32,b.decode_frame(self.frame())['request_id'])
    def adapter(self):
        provider=Mock(scope='1'*64,device=socket.gethostname().upper(),instance='instance')
        context=b.CheckedReadContext(provider.scope,provider.device,'policy1',True)
        return provider,b.BrokerReadAdapter(provider,checked_context=lambda:context)
    def test_malformed_or_foreign_instance_frames_never_dispatch(self):
        provider,adapter=self.adapter()
        self.assertEqual('INVALID',json.loads(adapter.handle(b'{}'))['code'])
        self.assertEqual('CHANGED',json.loads(adapter.handle(self.frame(worker_instance='another')))['code']);provider.dispatch.assert_not_called()
    def test_context_is_trusted_input_and_cannot_be_supplied_by_the_wire(self):
        provider,adapter=self.adapter()
        result=json.loads(adapter.handle(self.frame(context={'interactive':True})));self.assertEqual('INVALID',result['code']);provider.dispatch.assert_not_called()
        with self.assertRaises(WorkspaceError):b.BrokerReadAdapter(provider,checked_context=lambda:dict(interactive=True))
    def test_unexpected_errors_are_redacted_and_do_not_echo_payloads(self):
        provider,adapter=self.adapter();provider.dispatch.side_effect=RuntimeError('private key and path')
        result=adapter.handle(self.frame(payload={'private':'secret'}));self.assertEqual('UNAVAILABLE',json.loads(result)['code']);self.assertNotIn(b'private',result);self.assertNotIn(b'secret',result)
    def test_response_budget_refuses_oversize_or_unserializable_internal_results(self):
        for value in [{'text':'x'*(b.MAX_REPLY_BYTES+1)},dict(bad=float('nan')),dict(bad=object())]:
            with self.subTest(value_type=type(value).__name__):
                provider,adapter=self.adapter();provider.dispatch.return_value=value
                result=json.loads(adapter.handle(self.frame()));self.assertEqual('error',result['status']);self.assertNotIn('result',result)
    def test_context_representation_omits_scope_and_policy_identity(self):
        self.assertNotIn('private',repr(b.CheckedReadContext('private-scope','HOST','private-policy',True)))
