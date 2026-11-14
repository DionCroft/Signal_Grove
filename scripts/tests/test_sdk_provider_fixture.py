"""Callback/credential boundary checks; do not launch an SDK or certify native flow."""
import importlib.util
import os
import socket
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
spec = importlib.util.spec_from_file_location('sdk_provider_fixture', ROOT / 'native/sdk_provider_fixture.py')
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
URI = 'http://127.0.0.1:54321/complete'
CODE = 'fixture_code_' + 'x' * 24


class SdkProviderFixtureTests(unittest.TestCase):
    def test_ephemeral_ipv4_and_ipv6_tcp_loopback_binds_exclude_other_addresses_ports_and_protocols(self):
        self.assertTrue(m.fixture_bind_allowed(socket.AF_INET, socket.SOCK_STREAM, ('127.0.0.1', 0)))
        self.assertTrue(m.fixture_bind_allowed(socket.AF_INET6, socket.SOCK_STREAM, ('::1', 0)))
        for family, kind, address in [(socket.AF_INET, socket.SOCK_DGRAM, ('127.0.0.1', 0)),
                                      (socket.AF_INET, socket.SOCK_STREAM, ('0.0.0.0', 0)),
                                      (socket.AF_INET, socket.SOCK_STREAM, ('127.0.0.1', 9119)),
                                      (socket.AF_INET6, socket.SOCK_STREAM, ('::', 0)),
                                      (socket.AF_INET6, socket.SOCK_STREAM, ('::1', 9119)),
                                      (socket.AF_INET, socket.SOCK_STREAM, ('::1', 0))]:
            with self.subTest(address=address):
                self.assertFalse(m.fixture_bind_allowed(family, kind, address))

    def test_cli_entrypoint_is_isolated_loopback_without_lifecycle_or_auth_bypass_flags(self):
        self.assertEqual(m.server_argv(), ['hermes', 'serve', '--host', '127.0.0.1', '--port', '0', '--isolated', '--no-open'])
        self.assertFalse(set(m.server_argv()) & {'--stop', '--insecure', '--ssh-session-token-file', '--ssh-owner-nonce'})

    def test_server_environment_discards_inherited_credentials_and_agent_paths(self):
        with patch.dict(os.environ, {'OPENAI_API_KEY': 'fixture-do-not-forward',
                                    'SPARK_QWEN_API_KEY': 'fixture-do-not-forward',
                                    'SSH_AUTH_SOCK': 'fixture-do-not-forward',
                                    'HERMES_HOME': 'fixture-real-home',
                                    'PATH': 'fixture-real-tools'}):
            home = Path(tempfile.gettempdir()) / 'isolated-sdk-fixture'
            env = m.child_environment(home)
        self.assertFalse(any('fixture-do-not-forward' in value or 'fixture-real-' in value for value in env.values()))
        self.assertNotIn('OPENAI_API_KEY', env)
        self.assertNotIn('SPARK_QWEN_API_KEY', env)
        self.assertNotIn('SSH_AUTH_SOCK', env)
        for key in ('HOME', 'HERMES_HOME', 'XDG_CONFIG_HOME', 'XDG_CACHE_HOME', 'XDG_STATE_HOME',
                    'XDG_DATA_HOME', 'TMPDIR', 'TMP', 'TEMP', 'HF_HOME'):
            self.assertTrue(Path(env[key]).is_relative_to(home.resolve()), key)

    def test_exact_callback_requires_matching_state_and_a_single_code(self):
        self.assertEqual(m.callback_code(URI + '?code=' + CODE + '&state=expected', URI, 'expected'), CODE)
        for query in ['code=' + CODE + '&state=other', 'code=' + CODE + '&code=' + CODE + '&state=expected',
                      'code=' + CODE, 'code=&state=expected', 'code=' + CODE + '&state=expected&extra=1']:
            with self.subTest(query=query), self.assertRaises(ValueError):
                m.callback_code(URI + '?' + query, URI, 'expected')

    def test_callback_redirect_credentials_fragments_and_other_ports_refuse(self):
        for target in ['http://127.0.0.1:54322/complete', 'https://127.0.0.1:54321/complete',
                       'http://fixture@127.0.0.1:54321/complete', 'http://example.invalid:54321/complete',
                       'http://127.0.0.1:54321/elsewhere', URI + '#fragment']:
            with self.subTest(target=target), self.assertRaises(ValueError):
                m.callback_code(target + '?code=' + CODE + '&state=expected', URI, 'expected')

    def test_an_unsafe_expected_callback_does_not_authorize_a_matching_target(self):
        for expected in ['https://127.0.0.1:54321/complete', 'http://example.invalid:54321/complete',
                         'http://127.0.0.1/complete', 'http://127.0.0.1:054321/complete', URI + '?extra=1']:
            with self.subTest(expected=expected), self.assertRaises(ValueError):
                m.callback_code(expected + '?code=' + CODE + '&state=expected', expected, 'expected')

    def test_oversized_invalid_callbacks_fail_without_copying_the_code_in_errors(self):
        for target in [None, 'x' * 4097, URI + '?code=private%0D%0Avalue&state=expected',
                       URI + '?code=' + CODE + '&private-malformed-field&state=expected']:
            try:
                m.callback_code(target, URI, 'expected')
                self.fail('Invalid native callback accepted')
            except ValueError as exc:
                self.assertNotIn('private', str(exc))
                self.assertNotIn(CODE, str(exc))


if __name__ == '__main__':
    unittest.main()
