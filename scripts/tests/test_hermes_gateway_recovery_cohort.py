"""Guard the pin-specific upstream recovery inventory."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location(
    'hermes_persistence_compatibility',
    Path(__file__).resolve().parents[1] / 'hermes_persistence_compatibility.py',
)
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


class HermesGatewayRecoveryCohortTests(unittest.TestCase):
    def test_main_runs_held_routing_in_addition_to_shared_replay_cases(self):
        stable, stable_count, stable_digest = gate.gateway_recovery_cohort('8fc0374447313e276f6b9a2764bfc750190c966a')
        main, main_count, main_digest = gate.gateway_recovery_cohort('1d15aad1dfa8e9e5fcbd2082cad5666a0907a07d')
        for revision in ('337903eeffd696cf73936a3a00af08b43feb2028', '9bba163bc1d090852eccc86802eddc454df47d1b'):
            self.assertEqual((main, main_count, main_digest), gate.gateway_recovery_cohort(revision))
        self.assertEqual((stable_count, main_count), (5, 20))
        self.assertNotEqual(stable_digest, main_digest)
        self.assertEqual(main, (*stable, 'tests/gateway/test_held_route_late_platform.py'))

    def test_unreviewed_pin_cannot_inherit_the_recovery_gate(self):
        with self.assertRaisesRegex(ValueError, 'not been reviewed'):
            gate.gateway_recovery_cohort('caafe5389ae585ef77387a962aee05b1bcf17029')
