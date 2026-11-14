"""Review packets must bind every command to one runtime without applying it."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('pi_runtime_plan', Path(__file__).resolve().parents[1] / 'plan_pi_runtime.py')
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


class PiRuntimePlanTests(unittest.TestCase):
    def setUp(self):
        self.layout = dict(account_home='/home/operator', fleet_root='/home/operator/Projects/fleet',
                           generation='reviewed-release', source_name='hermes-agent')

    def test_three_gateway_commands_bind_the_same_sdk_launcher(self):
        result = m.render(self.layout)
        text = next(v for k, v in result['source_artifacts'].items() if k.endswith('hermes-gateway.service.conf'))
        launcher = result['sdk_launcher']
        self.assertIn('ExecStart=\nExecStart=' + launcher + ' gateway run\n', text)
        self.assertIn('ExecStop=\nExecStop=-' + launcher + ' --run-module gateway.systemd_stop_mark\n', text)
        self.assertIn('ExecStopPost=\nExecStopPost=-' + launcher + ' --run-module gateway.cgroup_cleanup\n', text)

    def test_serve_preserves_private_bind_and_port_selection(self):
        result = m.render(self.layout)
        text = next(v for k, v in result['deployed_wrappers'].items() if k.endswith('hermes-serve'))
        self.assertIn('source /home/operator/Projects/fleet/scripts/hermes-service.env\n', text)
        self.assertIn('Unexpected Hermes service home', text)
        self.assertIn('"${HERMES_DASHBOARD_PORT:-9119}"', text)
        self.assertIn('serve --host "$HERMES_DASHBOARD_HOST"', text)
        self.assertNotIn('0.0.0.0', text)

    def test_client_keeps_explicit_profile_home_and_literal_arguments(self):
        result = m.render(self.layout)
        text = next(v for k, v in result['deployed_wrappers'].items() if k.endswith('hermes-client'))
        self.assertIn(': "${HERMES_HOME:=', text)
        self.assertIn('"$@"', text)
        self.assertNotIn('cd ', text)

    def test_new_dropins_preserve_original_fragments_and_old_dropins(self):
        result = m.render(self.layout)
        self.assertEqual(len(result['proposed_dropin_links']), 2)
        self.assertTrue(all(k.endswith('/100-signal-grove.conf') for k in result['proposed_dropin_links']))
        self.assertTrue(all('/scripts/systemd/reviewed-release/' in v for v in result['proposed_dropin_links'].values()))

    def test_sources_and_deployed_wrappers_match(self):
        result = m.render(self.layout)
        for path, text in result['deployed_wrappers'].items():
            self.assertEqual(text, result['source_artifacts']['scripts/bin/reviewed-release/' + path.rsplit('/', 1)[1]])

    def test_no_activation_or_state_replay_is_authorized(self):
        result = m.render(self.layout)
        self.assertFalse(any(result['effects'].values()))
        self.assertFalse(result['rollback']['restore_live_databases'])
        self.assertFalse(result['rollback']['replay_work'])
        self.assertIn('explicit-live-cutover-approval', result['required_gates'])
        self.assertIn('serve-retirement-prepare-and-commit', result['required_gates'])
        self.assertIn('supported-old-stop-launcher-pm', result['required_gates'])

    def test_permission_observations_cover_every_entry_ancestor(self):
        result = m.render(self.layout)
        for path in ['/home/operator/.local/bin', '/home/operator/.config',
                     '/home/operator/Projects/fleet/scripts/bin',
                     '/home/operator/Projects/fleet/hermes/runtime-generations']:
            self.assertIn(path, result['permission_paths_for_observation'])

    def test_unknown_or_missing_layout_fields_are_rejected(self):
        for layout in (dict(self.layout, credential='forbidden'), {k:v for k,v in self.layout.items() if k!='generation'}):
            with self.assertRaises(ValueError):
                m.render(layout)

    def test_paths_cannot_inject_shell_or_systemd_syntax(self):
        for suffix in (';touch', '$(command)', '`command`', '%h', '\nExecStart=other', ' space', '"quote', "'quote", '\\other'):
            with self.subTest(suffix=suffix), self.assertRaises(ValueError):
                m.render(dict(self.layout, fleet_root=self.layout['fleet_root'] + suffix))

    def test_relative_or_ambiguous_roots_are_rejected(self):
        for root in ('relative/fleet', '//home/operator/fleet', '/home/operator/../fleet',
                     '/home/operator/./fleet', '/home/operator//fleet', '/home/operator/fleet/'):
            with self.subTest(root=root), self.assertRaises(ValueError):
                m.render(dict(self.layout, fleet_root=root))

    def test_foreign_or_account_wide_fleet_root_is_rejected(self):
        for root in ('/home/other/fleet', '/home/operator', '/var/lib/fleet'):
            with self.subTest(root=root), self.assertRaises(ValueError):
                m.render(dict(self.layout, fleet_root=root))

    def test_source_and_generation_cannot_escape_or_inject(self):
        for key in ('generation', 'source_name'):
            for name in ('..', '.', 'a/b', '/outside', '-option', 'new release', 'x%h', 'a'*97):
                with self.subTest(key=key, name=name), self.assertRaises(ValueError):
                    m.render(dict(self.layout, **{key:name}))


if __name__ == '__main__':
    unittest.main()
