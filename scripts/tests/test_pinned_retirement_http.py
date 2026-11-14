"""Real portable TCP tests, with synthetic owner/session seams; no native acceptance."""
import importlib.util
import json
from pathlib import Path
import socket
import sys
import threading
import time
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import retirement_guard as guards
spec = importlib.util.spec_from_file_location('pinned_retirement_http', Path(__file__).resolve().parents[1] / 'pinned_retirement_http.py')
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
OWNER = m.Owner(4321, 123456, '00000000-0000-4000-8000-000000000001')
ACCESS = 'fixture.provider.access'
PERMIT = 'fixture_prepare_' + 'x' * 28


def reply(body=b'{"ok":true}', *, status='200 OK', headers=None):
    pairs = [('Content-Type', 'application/json'), ('Content-Length', str(len(body)))] if headers is None else headers
    return ('HTTP/1.1 ' + status + '\r\n' + ''.join(k + ': ' + v + '\r\n' for k, v in pairs) + '\r\n').encode('ascii') + body


class Wire:
    def __init__(self, replies):
        self.replies = replies
        self.requests = []
        self.accepts = 0
        self.errors = []
        self.done = threading.Event()
        self.connection = None
        self.listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.listener.bind(('127.0.0.1', 0))
        self.listener.listen(1)
        self.listener.settimeout(2)
        self.thread = threading.Thread(target=self.serve, daemon=True)
        self.thread.start()
        self.client = socket.create_connection(self.listener.getsockname(), timeout=2)
        self.observations = 0
        self.owner = OWNER

    def observe(self, sock):
        self.observations += 1
        if sock is not self.client:
            raise AssertionError('Observer must receive the actual socket')
        return self.owner

    def adapter(self, **kwargs):
        return m.PinnedRetirementHttp(OWNER, self.client, ACCESS, self.observe, **kwargs)

    def serve(self):
        try:
            connection, _ = self.listener.accept()
            self.connection = connection
            self.accepts += 1
            connection.settimeout(3)
            with connection, connection.makefile('rb') as stream:
                while not self.done.is_set():
                    line = stream.readline(4096)
                    if not line:
                        break
                    method, path, _ = line.decode('ascii').strip().split(' ')
                    headers = {}
                    while True:
                        line = stream.readline(8192)
                        if line == b'\r\n':
                            break
                        if not line:
                            return
                        key, value = line.decode('ascii').split(':', 1)
                        headers[key.lower()] = value.strip()
                    body = stream.read(int(headers.get('content-length', '0')))
                    self.requests.append((method, path, headers, body))
                    index = len(self.requests) - 1
                    if index >= len(self.replies):
                        return
                    result = self.replies[index]
                    if callable(result):
                        result(connection)
                    elif result is None:
                        return
                    else:
                        connection.sendall(result)
        except (OSError, ValueError) as exc:
            self.errors.append(type(exc).__name__)

    def close(self):
        self.done.set()
        for sock in (self.client, self.connection, self.listener):
            if sock is not None:
                try:
                    sock.shutdown(socket.SHUT_RDWR)
                except OSError:
                    pass
                sock.close()
        self.thread.join(4)
        if self.thread.is_alive():
            raise AssertionError('Owned TCP fixture failed to exit')

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.close()


class PinnedRetirementHttpTests(unittest.TestCase):
    def test_three_exchanges_use_exact_same_socket_and_provider_bearer(self):
        with Wire([reply(), reply(b'{"ok":true,"idle":true}'), reply()]) as wire:
            client = wire.adapter()
            self.assertEqual(client('GET', '/api/host/identity', None), (200, {'ok': True}))
            self.assertEqual(client('GET', '/api/health/idle', None)[1]['idle'], True)
            self.assertEqual(client('POST', '/api/health/retirement', {'action': 'prepare'}), (200, {'ok': True}))
            self.assertEqual(wire.accepts, 1)
            self.assertEqual(wire.observations, 7)
            self.assertEqual([r[0] for r in wire.requests], ['GET', 'GET', 'POST'])
            self.assertTrue(all(r[2]['authorization'] == 'Bearer ' + ACCESS for r in wire.requests))
            self.assertFalse(any('cookie' in r[2] or 'x-hermes-session-token' in r[2] for r in wire.requests))
            self.assertEqual(json.loads(wire.requests[-1][3]), {'action': 'prepare'})
            self.assertNotIn(ACCESS, repr(client))
            client.close()

    def test_owner_mismatch_at_construction_sends_no_credentials(self):
        with Wire([]) as wire:
            wire.owner = m.Owner(4321, 123457, OWNER.boot_id)
            with self.assertRaises(m.Unknown):
                wire.adapter()
            self.assertEqual(wire.requests, [])

    def test_owner_mismatch_before_request_sends_nothing_and_poisons_socket(self):
        with Wire([]) as wire:
            client = wire.adapter()
            wire.owner = None
            with self.assertRaises(m.Unknown):
                client('POST', '/api/health/retirement', {'action': 'prepare'})
            self.assertEqual(wire.requests, [])
            self.assertIsNone(client._token)
            self.assertEqual(wire.client.fileno(), -1)

    def test_owner_change_after_response_is_unknown_and_cannot_reconnect(self):
        with Wire([reply()]) as wire:
            client = wire.adapter()
            calls = 0
            def observer(sock):
                nonlocal calls
                calls += 1
                return OWNER if calls == 1 else None
            client._observe = observer
            with self.assertRaises(m.Unknown):
                client('GET', '/api/host/identity', None)
            with self.assertRaises(m.Unknown):
                client('GET', '/api/host/identity', None)
            self.assertEqual(len(wire.requests), 1)
            self.assertEqual(wire.accepts, 1)

    def test_connection_close_is_unknown_without_a_replacement_connection(self):
        with Wire([reply(headers=[('Content-Type', 'application/json'), ('Content-Length', '11'), ('Connection', 'close')])]) as wire:
            client = wire.adapter()
            for _ in range(2):
                with self.assertRaises(m.Unknown):
                    client('GET', '/api/host/identity', None)
            self.assertEqual(len(wire.requests), 1)

    def test_reset_after_post_is_not_retried(self):
        with Wire([None]) as wire:
            client = wire.adapter()
            for _ in range(2):
                with self.assertRaises(m.Unknown):
                    client('POST', '/api/health/retirement', {'action': 'commit', 'token': PERMIT})
            self.assertEqual(len(wire.requests), 1)

    def test_401_is_returned_once_without_refresh_or_authentication_fallback(self):
        with Wire([reply(b'{"error":"unauthorized"}', status='401 Unauthorized')]) as wire:
            client = wire.adapter()
            self.assertEqual(client('GET', '/api/host/identity', None)[0], 401)
            with self.assertRaises(m.Unknown):
                client('GET', '/api/host/identity', None)
            self.assertEqual(len(wire.requests), 1)

    def test_redirect_is_never_followed(self):
        with Wire([reply(status='302 Found', headers=[('Content-Type', 'application/json'), ('Content-Length', '11'), ('Location', 'http://127.0.0.1:1/credential-sink')])]) as wire:
            client = wire.adapter()
            self.assertEqual(client('GET', '/api/host/identity', None)[0], 302)
            self.assertEqual(len(wire.requests), 1)
            self.assertIsNone(client._token)

    def test_unbounded_ambiguous_and_non_json_framing_refuses(self):
        for headers in [[], [('Content-Length', '65537')], [('Content-Length', '11'), ('Content-Length', '11')],
                        [('Content-Length', '-1')], [('Content-Length', '11'), ('Transfer-Encoding', 'chunked')],
                        [('Content-Length', '11'), ('Content-Encoding', 'gzip')],
                        [('Content-Length', '11'), ('Content-Type', 'text/html')]]:
            with self.subTest(headers=headers), Wire([reply(headers=headers)]) as wire:
                client = wire.adapter()
                with self.assertRaises(m.Unknown):
                    client('GET', '/api/host/identity', None)
                self.assertEqual(len(wire.requests), 1)

    def test_aggregate_response_headers_are_bounded(self):
        large = [('X-Padding-' + str(i), 'p' * 1000) for i in range(18)]
        with Wire([reply(headers=large + [('Content-Length', '11'), ('Content-Type', 'application/json')])]) as wire:
            with self.assertRaises(m.Unknown):
                wire.adapter()('GET', '/api/host/identity', None)

    def test_nonfinite_duplicate_invalid_and_nonobject_json_refuses(self):
        for body in [b'{"idle":true,"idle":false}', b'{"idle":NaN}', b'[]', b'null', b'{', b'"\xff"']:
            with self.subTest(body=body), Wire([reply(body)]) as wire:
                client = wire.adapter()
                with self.assertRaises(m.Unknown):
                    client('GET', '/api/health/idle', None)

    def test_truncated_body_is_unknown(self):
        def truncated(connection):
            connection.sendall(reply(b'{', headers=[('Content-Length', '11'), ('Content-Type', 'application/json')]))
            connection.shutdown(socket.SHUT_WR)
        with Wire([truncated]) as wire:
            with self.assertRaises(m.Unknown):
                wire.adapter()('GET', '/api/health/idle', None)

    def test_absolute_deadline_interrupts_a_trickling_response(self):
        def trickle(connection):
            for byte in reply():
                connection.sendall(bytes([byte]))
                time.sleep(.03)
        with Wire([trickle]) as wire:
            client = wire.adapter(timeout=.15)
            start = time.monotonic()
            with self.assertRaises(m.Unknown):
                client('GET', '/api/host/identity', None)
            self.assertLess(time.monotonic() - start, 1.5)
            self.assertIsNone(client._token)

    def test_foreign_paths_methods_bodies_and_header_injection_send_nothing(self):
        with Wire([]) as wire:
            client = wire.adapter()
            for args in [('GET', 'https://example.invalid/api/host/identity', None),
                         ('GET', '/api/host/identity?token=private', None),
                         ('GET', '/api/host/identity', {}), ('DELETE', '/api/health/retirement', None),
                         ('POST', '/api/health/retirement', {'action': 'prepare', 'extra': True}),
                         ('POST', '/api/health/retirement', {'action': 'commit', 'token': '\r\n'}),
                         ('POST', '/api/health/retirement', {'action': []}),
                         ('GET', [], None)]:
                with self.subTest(args=args), self.assertRaises(m.Refused):
                    client(*args)
            self.assertEqual(wire.requests, [])
            client.close()

    def test_unsafe_session_timeout_and_unconnected_socket_are_refused(self):
        with Wire([]) as wire:
            for token, timeout in [('', 5), ('x\r\nHost: elsewhere', 5), (ACCESS, True),
                                   (ACCESS, float('nan')), (ACCESS, 0), (ACCESS, 11)]:
                with self.subTest(timeout=timeout), self.assertRaises(ValueError):
                    m.PinnedRetirementHttp(OWNER, wire.client, token, wire.observe, timeout=timeout)
            with socket.socket() as unconnected, self.assertRaises(OSError):
                m.PinnedRetirementHttp(OWNER, unconnected, ACCESS, wire.observe)
            self.assertEqual(wire.requests, [])

    def test_observer_exception_never_exposes_credential_values(self):
        with Wire([]) as wire:
            def broken(_):
                raise RuntimeError(ACCESS + PERMIT)
            try:
                m.PinnedRetirementHttp(OWNER, wire.client, ACCESS, broken)
                self.fail('Expected refusal')
            except m.Unknown as exc:
                self.assertNotIn(ACCESS, str(exc))
                self.assertNotIn(PERMIT, str(exc))
                self.assertIsNone(exc.__cause__)

    def test_concurrent_request_cannot_send_a_second_action(self):
        with Wire([]) as wire:
            client = wire.adapter()
            client._lock.acquire()
            try:
                with self.assertRaises(m.Refused):
                    client('POST', '/api/health/retirement', {'action': 'prepare'})
                self.assertEqual(wire.requests, [])
            finally:
                client._lock.release()
                client.close()

    def test_guard_prepare_and_commit_use_same_socket_without_replay(self):
        response_bodies = [dict(ok=True, pid=OWNER.pid, role='serve', protocolVersion=1),
                           dict(ok=True, idle=True), dict(ok=True, idle=True, token=PERMIT), dict(ok=True)]
        with Wire([reply(json.dumps(body).encode('utf8')) for body in response_bodies]) as wire:
            adapter = wire.adapter()
            events = []
            guard = guards.RetirementGuard(OWNER, adapter, lambda: OWNER, events.append)
            self.assertIs(guard.inspect(), True)
            permit = guard.prepare()
            guard.commit(permit)
            self.assertEqual(guard.state, 'committed')
            with self.assertRaises(m.Refused):
                guard.commit(permit)
            self.assertEqual(len(wire.requests), 4)
            self.assertEqual(wire.accepts, 1)
            self.assertNotIn(ACCESS, repr(events))
            self.assertNotIn(PERMIT, repr(events))
            adapter.close()


if __name__ == '__main__':
    unittest.main()
