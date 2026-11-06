import asyncio
import contextvars
from copy import deepcopy
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import tempfile
import threading
import unittest
from unittest.mock import patch

import api_server_model_status as status
from install_adapter import prepare


def config():
    return {'security': {'inference': {'enabled': True, 'allowed_routes': {
        'spark-one': {'models': ['model-one'], 'base_url': 'http://inference.example.ts.net:30000/v1'},
        'spark-two': {'models': ['model-two'], 'base_url': 'http://inference.example.ts.net:30001/v1'}}}}}


def resolve(provider, target_model=None):
    row = config()['security']['inference']['allowed_routes'][provider]
    return {'provider': 'custom', 'base_url': row['base_url'], 'api_key': 'fixture'}


class PolicyTests(unittest.TestCase):
    def test_candidates_keep_credentials_private(self):
        rows = status.candidates(config(), resolve)
        self.assertEqual(['model-one', 'model-two'], [r.model for r in rows])
        self.assertNotIn('fixture', repr(rows))

    def test_untrusted_transports_and_routes_are_refused(self):
        for change in ['foreign-provider', 'query', 'userinfo', 'foreign-host', 'duplicate', 'multi-model', 'oversized', 'disabled']:
            with self.subTest(change=change):
                value = config(); p = value['security']['inference']; routes = p['allowed_routes']
                if change == 'foreign-provider': routes['cloud'] = routes.pop('spark-one')
                elif change == 'query': routes['spark-one']['base_url'] += '?key=private'
                elif change == 'userinfo': routes['spark-one']['base_url'] = 'http://user:pass@inference.example.ts.net:30000/v1'
                elif change == 'foreign-host': routes['spark-one']['base_url'] = 'http://public.example:30000/v1'
                elif change == 'duplicate': routes['spark-two']['base_url'] = routes['spark-one']['base_url']
                elif change == 'multi-model': routes['spark-one']['models'].append('other')
                elif change == 'oversized': routes['spark-one']['models'] = ['m' * 513]
                else: p['enabled'] = False
                with self.assertRaises((status.PolicyUnavailable, KeyError)):
                    status.candidates(value, resolve)

    def test_resolved_credential_target_must_match_the_allowlist(self):
        for field, value in [('provider', 'remote'), ('base_url', 'http://other.example.ts.net:30000/v1'), ('api_key', '')]:
            with self.subTest(field=field):
                def altered(provider, target_model=None):
                    result = resolve(provider, target_model); result[field] = value; return result
                with self.assertRaises(status.PolicyUnavailable): status.candidates(config(), altered)

    def test_status_never_guesses_a_unique_route(self):
        rows = status.candidates(config(), resolve)
        cases = [(['ready', 'offline'], 'ready'), (['ready', 'ready'], 'ambiguous'),
                 (['ready', 'unknown'], 'unverified'), (['ready', 'unsafe'], 'unverified'),
                 (['offline', 'unready'], 'unavailable'), (['ready', 'invented'], 'unverified')]
        for states, expected in cases:
            with self.subTest(states=states):
                result = status.summarize(rows, states, now=lambda: 123)
                self.assertEqual(expected, result['status']); self.assertEqual(123000, result['observed_at'])
                self.assertNotIn('api_key', json.dumps(result)); self.assertNotIn('base_url', json.dumps(result))
                self.assertEqual([s == 'ready' for s in states], [m['verified'] for m in result['models']])

    def test_incomplete_probe_results_are_refused(self):
        with self.assertRaises(status.PolicyUnavailable): status.summarize(status.candidates(config(), resolve), ['ready'])


class ProbeTests(unittest.TestCase):
    def run_endpoint(self, mode):
        visits = []
        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *_args): pass
            def do_GET(self):
                authenticated = self.headers.get('Authorization') == 'Bearer fixture'
                visits.append((self.path, authenticated))
                code, body = 200, {'data': [{'id': 'model-one'}]}
                if not authenticated: code = 200 if mode == 'anonymous' else 401
                elif self.path == '/v1/models':
                    if mode == 'wrong-model': body = {'data': [{'id': 'another-model'}]}
                    elif mode == 'duplicate-model': body = {'data': [{'id': 'model-one'}, {'id': 'model-one'}]}
                    elif mode == 'redirect': code = 302
                    elif mode == 'denied': code = 401
                elif self.path == '/health': code = 503 if mode == 'unhealthy' else 200
                self.send_response(code)
                if code == 302: self.send_header('Location', '/should-not-follow')
                self.end_headers(); self.wfile.write(json.dumps(body).encode())
        server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True); thread.start()
        try: result = status.probe(status.Candidate('spark-one', 'model-one', f'http://127.0.0.1:{server.server_port}/v1', 'fixture'))
        finally: server.shutdown(); server.server_close(); thread.join(2)
        return result, visits

    def test_actual_http_requires_auth_identity_and_health(self):
        result, visits = self.run_endpoint('ready')
        self.assertEqual('ready', result)
        self.assertEqual([('/v1/models', False), ('/v1/models', True), ('/health', True)], visits)

    def test_anonymous_endpoint_gets_no_credential(self):
        result, visits = self.run_endpoint('anonymous')
        self.assertEqual('unsafe', result); self.assertEqual([('/v1/models', False)], visits)

    def test_wrong_or_multiple_model_ids_do_not_become_ready(self):
        for mode in ['wrong-model', 'duplicate-model', 'denied']:
            with self.subTest(mode=mode):
                result, visits = self.run_endpoint(mode)
                self.assertEqual('unsafe', result); self.assertEqual(2, len(visits))

    def test_redirect_is_not_followed_or_retried(self):
        result, visits = self.run_endpoint('redirect')
        self.assertEqual('unsafe', result); self.assertEqual(2, len(visits))
        self.assertFalse(any(p == '/should-not-follow' for p, _ in visits))

    def test_unhealthy_loaded_endpoint_is_unready(self):
        result, visits = self.run_endpoint('unhealthy')
        self.assertEqual('unready', result); self.assertEqual(3, len(visits))


class AsyncTests(unittest.IsolatedAsyncioTestCase):
    async def test_disabled_observer_resolves_no_credentials_or_network(self):
        with patch.object(status, '_observe', side_effect=AssertionError('must not probe')):
            result = await status.read_runtime_status({}, resolve=lambda *_a, **_k: self.fail('must not resolve'))
        self.assertEqual('not_configured', result['status']); self.assertEqual([], result['models'])

    async def test_failures_never_echo_secret_url_or_exception(self):
        def fail(*_args, **_kwargs): raise RuntimeError('private-credential http://private-host/path')
        result = await status.read_runtime_status(config(), fail)
        self.assertEqual('unverified', result['status']); self.assertEqual([], result['models'])
        self.assertNotIn('private', json.dumps(result))

    async def test_profile_credentials_are_bound_before_threaded_observation(self):
        scope = contextvars.ContextVar('fixture_scope'); owner = threading.get_ident(); observations = []
        def scoped_resolve(provider, target_model=None):
            self.assertEqual(owner, threading.get_ident())
            result = resolve(provider, target_model); result['api_key'] = scope.get(); return result
        def observe(rows):
            observations.append({r.api_key for r in rows}); return status.summarize(rows, ['ready', 'offline'])
        async def request(name):
            token = scope.set(name)
            try: return await status.read_runtime_status(config(), scoped_resolve)
            finally: scope.reset(token)
        with patch.object(status, '_observe', side_effect=observe):
            a, b = await asyncio.gather(request('profile-a'), request('profile-b'))
        self.assertEqual('ready', a['status']); self.assertEqual('ready', b['status'])
        self.assertCountEqual([{'profile-a'}, {'profile-b'}], observations)
        self.assertNotIn('profile-a', json.dumps(a)); self.assertNotIn('profile-b', json.dumps(b))

    async def test_invalid_policy_cannot_start_a_probe(self):
        value = config(); value['security']['inference']['allowed_routes']['bad'] = {}
        with patch.object(status, '_observe', side_effect=AssertionError('must not probe')):
            result = await status.read_runtime_status(value, resolve)
        self.assertEqual('unverified', result['status'])


class AdapterGuardTests(unittest.TestCase):
    def test_changed_source_hash_is_refused_without_writes(self):
        with tempfile.TemporaryDirectory() as name:
            api = Path(name) / 'api_server.py'; api.write_text('existing_custom_fix = True\n')
            before = api.read_bytes()
            with self.assertRaises(ValueError): prepare(api, '0' * 64)
            self.assertEqual(before, api.read_bytes())


if __name__ == '__main__':
    unittest.main()
