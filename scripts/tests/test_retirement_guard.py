"""Client-side safety checks; these do not impersonate an OS or SDK owner."""
import importlib.util
import json
from pathlib import Path
import sys
import unittest

spec = importlib.util.spec_from_file_location('retirement_guard', Path(__file__).resolve().parents[1] / 'retirement_guard.py')
m = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = m
spec.loader.exec_module(m)
BOOT = '00000000-0000-4000-8000-000000000001'
TOKEN = 'fixture_prepare_' + 'x' * 28


class Harness:
    def __init__(self):
        self.owner = m.Owner(4321, 123456, BOOT)
        self.current = self.owner
        self.time = 100.0
        self.calls = []
        self.events = []
        self.replies = []
        self.on_request = lambda: None
        self.on_record = lambda: None
        self.client = m.RetirementGuard(self.owner, self.request, lambda: self.current,
                                        self.record, now=lambda: self.time)

    def request(self, method, path, body):
        self.calls.append((method, path, body))
        self.on_request()
        result = self.replies.pop(0)
        if isinstance(result, BaseException):
            raise result
        return result

    def record(self, event):
        self.on_record()
        self.events.append(dict(event))

    def ready(self, idle=True):
        self.replies += [(200, dict(ok=True, pid=4321, role='serve', protocolVersion=1)),
                         (200, dict(ok=True, idle=idle))]
        return self.client.inspect()

    def prepared(self):
        self.ready()
        self.replies.append((200, dict(ok=True, idle=True, token=TOKEN)))
        return self.client.prepare()


class RetirementGuardTests(unittest.TestCase):
    def test_owner_rejects_missing_native_incarnation_and_boolean_ids(self):
        for args in [(True, 123, BOOT), (1, 123, BOOT), (4321, True, BOOT),
                     (4321, 0, BOOT), (4321, 123, ''), (4321, 123, 'old-record')]:
            with self.subTest(args=args), self.assertRaises(ValueError):
                m.Owner(*args)

    def test_positive_idle_snapshot_is_not_a_prepare_or_commit(self):
        h = Harness()
        self.assertIs(h.ready(), True)
        self.assertEqual(h.client.state, 'ready')
        self.assertEqual([x[0] for x in h.calls], ['GET', 'GET'])
        self.assertIsNone(h.client._permit)
        with self.assertRaises(m.Refused):
            h.client.commit(None)
        self.assertEqual(len(h.calls), 2)

    def test_provider_auth_refusal_never_falls_back_or_posts(self):
        h = Harness()
        h.replies.append((401, dict(detail='Unauthorized')))
        with self.assertRaises(m.Refused):
            h.client.inspect()
        with self.assertRaises(m.Refused):
            h.client.prepare()
        self.assertEqual(h.client.state, 'refused')
        self.assertEqual(h.calls, [('GET', '/api/host/identity', None)])

    def test_authenticated_identity_must_match_pid_role_and_protocol(self):
        for patch in [dict(pid=9999), dict(pid=True), dict(role='gateway'),
                      dict(protocolVersion=True), dict(protocolVersion=2), dict(ok=1)]:
            h = Harness()
            payload = dict(ok=True, pid=4321, role='serve', protocolVersion=1)
            payload.update(patch)
            h.replies.append((200, payload))
            with self.subTest(patch=patch), self.assertRaises(m.Refused):
                h.client.inspect()
            self.assertEqual(len(h.calls), 1)

    def test_busy_and_unknown_idle_never_request_prepare(self):
        for idle in [False, None]:
            h = Harness()
            self.assertIs(h.ready(idle), idle)
            with self.subTest(idle=idle), self.assertRaises(m.Refused):
                h.client.prepare()
            self.assertEqual(len(h.calls), 2)

    def test_missing_or_non_boolean_idle_is_not_idle(self):
        for payload in [dict(ok=True), dict(ok=True, idle=1), dict(ok=True, idle='true'), dict(ok=1, idle=True)]:
            h = Harness()
            h.replies = [(200, dict(ok=True, pid=4321, role='serve', protocolVersion=1)), (200, payload)]
            with self.subTest(payload=payload), self.assertRaises(m.Refused):
                h.client.inspect()
            self.assertEqual(h.client.state, 'refused')

    def test_prepare_can_hold_when_work_arrives_after_diagnostic(self):
        for idle in [False, None]:
            h = Harness()
            h.ready()
            h.replies.append((200, dict(ok=False, idle=idle)))
            self.assertIsNone(h.client.prepare())
            self.assertEqual(h.client.state, 'held')
            self.assertEqual([x[2] for x in h.calls if x[0] == 'POST'], [dict(action='prepare')])

    def test_prepare_permit_then_commit_is_exactly_one_each(self):
        h = Harness()
        permit = h.prepared()
        self.assertEqual(h.client.state, 'prepared')
        self.assertNotIn(TOKEN, repr(permit))
        self.assertFalse(hasattr(permit, '_token'))
        h.replies.append((200, dict(ok=True)))
        h.client.commit(permit)
        self.assertEqual(h.client.state, 'committed')
        self.assertIsNone(h.client._token)
        self.assertEqual([x[2] for x in h.calls if x[0] == 'POST'],
                         [dict(action='prepare'), dict(action='commit', token=TOKEN)])
        self.assertNotIn(TOKEN, json.dumps(h.events))

    def test_cancel_only_the_exact_uncommitted_permit(self):
        h = Harness()
        permit = h.prepared()
        h.replies.append((200, dict(ok=True)))
        h.client.cancel(permit)
        self.assertEqual(h.client.state, 'cancelled')
        self.assertIsNone(h.client._token)
        with self.assertRaises(m.Refused):
            h.client.commit(permit)
        self.assertEqual(len(h.calls), 4)

    def test_another_guards_permit_is_never_sent(self):
        h = Harness()
        h.prepared()
        other = Harness().prepared()
        with self.assertRaises(m.Refused):
            h.client.commit(other)
        self.assertEqual(len(h.calls), 3)

    def test_committed_and_cancelled_permits_are_never_replayed(self):
        for action in ['commit', 'cancel']:
            h = Harness()
            permit = h.prepared()
            h.replies.append((200, dict(ok=True)))
            getattr(h.client, action)(permit)
            for repeat in ['commit', 'cancel', 'prepare', 'inspect'] if action == 'commit' else ['commit', 'cancel', 'prepare']:
                with self.subTest(action=action, repeat=repeat), self.assertRaises(m.Refused):
                    getattr(h.client, repeat)(permit) if repeat in ['commit', 'cancel'] else getattr(h.client, repeat)()
            self.assertEqual(len(h.calls), 4)

    def test_prepare_timeout_is_unknown_and_never_retried(self):
        h = Harness()
        h.ready()
        h.replies.append(TimeoutError('sensitive transport detail: ' + TOKEN))
        with self.assertRaises(m.Unknown) as caught:
            h.client.prepare()
        self.assertNotIn(TOKEN, str(caught.exception))
        self.assertIsNone(h.client._token)
        for operation in [h.client.prepare, h.client.inspect]:
            with self.assertRaises(m.Refused):
                operation()
        self.assertEqual(len(h.calls), 3)

    def test_commit_timeout_never_cancels_or_repeats(self):
        h = Harness()
        permit = h.prepared()
        h.replies.append(ConnectionResetError('sensitive transport detail: ' + TOKEN))
        with self.assertRaises(m.Unknown):
            h.client.commit(permit)
        self.assertEqual(h.client.state, 'unknown')
        self.assertIsNone(h.client._token)
        with self.assertRaises(m.Refused):
            h.client.cancel(permit)
        with self.assertRaises(m.Refused):
            h.client.commit(permit)
        self.assertEqual(len(h.calls), 4)

    def test_cancel_timeout_never_repeats(self):
        h = Harness()
        permit = h.prepared()
        h.replies.append(TimeoutError())
        with self.assertRaises(m.Unknown):
            h.client.cancel(permit)
        with self.assertRaises(m.Refused):
            h.client.cancel(permit)
        self.assertEqual(len(h.calls), 4)

    def test_operator_interrupt_preserves_unknown_state_without_replay(self):
        for action in ['prepare', 'commit']:
            h = Harness()
            permit = h.prepared() if action == 'commit' else h.ready()
            count = len(h.calls)
            h.replies.append(KeyboardInterrupt())
            with self.subTest(action=action), self.assertRaises(KeyboardInterrupt):
                h.client.commit(permit) if action == 'commit' else h.client.prepare()
            self.assertEqual(h.client.state, 'unknown')
            self.assertIsNone(h.client._token)
            with self.assertRaises(m.Refused):
                h.client.prepare()
            self.assertEqual(len(h.calls), count + 1)

    def test_mutation_http_refusal_is_not_a_success_or_retry(self):
        for action in ['prepare', 'commit', 'cancel']:
            h = Harness()
            permit = h.prepared() if action != 'prepare' else h.ready()
            h.replies.append((401, dict(detail='Unauthorized')))
            with self.subTest(action=action), self.assertRaises(m.Unknown):
                getattr(h.client, action)(permit) if action != 'prepare' else h.client.prepare()
            self.assertEqual(h.client.state, 'unknown')
            self.assertIsNone(h.client._token)

    def test_invalid_prepare_response_cannot_authorize_a_stop(self):
        for payload in [dict(ok=True, idle=True), dict(ok=True, idle=False, token=TOKEN),
                        dict(ok=1, idle=True, token=TOKEN), dict(ok=True, idle=True, token='short'),
                        dict(ok=True, idle=True, token=TOKEN + '\n'), dict(ok=False, idle=False, token=TOKEN)]:
            h = Harness()
            h.ready()
            h.replies.append((200, payload))
            with self.subTest(payload=payload), self.assertRaises(m.Unknown):
                h.client.prepare()
            self.assertEqual(h.client.state, 'unknown')
            self.assertEqual(len(h.calls), 3)

    def test_unconfirmed_commit_or_cancel_is_unknown(self):
        for action in ['commit', 'cancel']:
            for payload in [dict(ok=False), dict(ok=1), {}]:
                h = Harness()
                permit = h.prepared()
                h.replies.append((200, payload))
                with self.subTest(action=action, payload=payload), self.assertRaises(m.Unknown):
                    getattr(h.client, action)(permit)
                self.assertEqual(h.client.state, 'unknown')
                self.assertIsNone(h.client._token)

    def test_native_owner_change_before_prepare_sends_no_post(self):
        h = Harness()
        h.ready()
        h.current = m.Owner(4321, 123457, BOOT)
        with self.assertRaises(m.Unknown):
            h.client.prepare()
        self.assertEqual(len(h.calls), 2)

    def test_owner_change_after_prepare_response_cannot_yield_permit(self):
        h = Harness()
        h.ready()
        h.replies.append((200, dict(ok=True, idle=True, token=TOKEN)))
        h.on_request = lambda: setattr(h, 'current', m.Owner(4321, 123457, BOOT))
        with self.assertRaises(m.Unknown):
            h.client.prepare()
        self.assertEqual(h.client.state, 'unknown')
        self.assertIsNone(h.client._token)

    def test_owner_change_during_intent_journal_sends_no_mutation(self):
        h = Harness()
        h.ready()
        h.on_record = lambda: setattr(h, 'current', m.Owner(4321, 123457, BOOT))
        with self.assertRaises(m.Unknown):
            h.client.prepare()
        self.assertEqual(len(h.calls), 2)

    def test_boot_change_before_commit_never_sends_token(self):
        h = Harness()
        permit = h.prepared()
        h.current = m.Owner(4321, 123456, '00000000-0000-4000-8000-000000000002')
        with self.assertRaises(m.Unknown):
            h.client.commit(permit)
        self.assertEqual(len(h.calls), 3)

    def test_expired_or_backward_clock_refuses_commit(self):
        for clock in [99.0, 125.0, 126.0, float('nan'), float('inf'), True]:
            h = Harness()
            permit = h.prepared()
            h.time = clock
            with self.subTest(clock=clock), self.assertRaises(m.Unknown):
                h.client.commit(permit)
            self.assertEqual(len(h.calls), 3)
            self.assertIsNone(h.client._token)

    def test_deadline_is_rechecked_after_slow_intent_journal(self):
        h = Harness()
        permit = h.prepared()
        h.on_record = lambda: setattr(h, 'time', 126.0)
        with self.assertRaises(m.Unknown):
            h.client.commit(permit)
        self.assertEqual(len(h.calls), 3)

    def test_slow_prepare_response_does_not_extend_lifetime(self):
        h = Harness()
        h.ready()
        h.replies.append((200, dict(ok=True, idle=True, token=TOKEN)))
        h.on_request = lambda: setattr(h, 'time', 126.0)
        with self.assertRaises(m.Unknown):
            h.client.prepare()
        self.assertEqual(len(h.calls), 3)

    def test_journal_failure_before_dispatch_refuses_all_network(self):
        h = Harness()
        h.ready()
        h.on_record = lambda: (_ for _ in ()).throw(OSError('secret: ' + TOKEN))
        with self.assertRaises(m.Unknown) as caught:
            h.client.prepare()
        self.assertNotIn(TOKEN, str(caught.exception))
        self.assertEqual(len(h.calls), 2)

    def test_journal_failure_after_response_keeps_mutation_unknown(self):
        h = Harness()
        h.ready()
        h.replies.append((200, dict(ok=True, idle=True, token=TOKEN)))
        h.on_request = lambda: setattr(h, 'on_record', lambda: (_ for _ in ()).throw(OSError('secret: ' + TOKEN)))
        with self.assertRaises(m.Unknown):
            h.client.prepare()
        self.assertIsNone(h.client._token)
        self.assertEqual(h.client.state, 'unknown')
        self.assertEqual(len(h.calls), 3)

    def test_non_json_and_invalid_status_replies_cannot_pass(self):
        for reply in [(200, []), (True, dict(ok=True)), (302, dict(ok=True)), (200, None)]:
            h = Harness()
            h.replies.append(reply)
            with self.subTest(reply=reply), self.assertRaises(m.Refused):
                h.client.inspect()
            self.assertEqual(len(h.calls), 1)

    def test_journal_contains_identity_and_phases_but_no_payload_or_permit(self):
        h = Harness()
        permit = h.prepared()
        h.replies.append((200, dict(ok=True)))
        h.client.commit(permit)
        for event in h.events:
            self.assertEqual(set(event) - {'http_status'}, {'step', 'phase', 'pid', 'start_ticks', 'boot_id'})
            self.assertEqual((event['pid'], event['start_ticks'], event['boot_id']), (4321, 123456, BOOT))
        self.assertNotIn(TOKEN, repr(h.client))
        self.assertNotIn(TOKEN, json.dumps(h.events))


if __name__ == '__main__':
    unittest.main()
