"""Regression checks for native heartbeat receipt and process settlement."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch


spec = importlib.util.spec_from_file_location(
    "heartbeat_gate", Path(__file__).resolve().parents[1] / "hermes_native_heartbeat_gate.py"
)
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


class NativeHeartbeatGateTest(unittest.TestCase):
    def fixture(self, variant, case, *, timing_overrun=False, reopened=None, terminal=None,
                missing_close_refusal=False, additional_log=""):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        folder = Path(temporary.name)
        action, interval = gate.cases(variant)[case]
        pristine = variant == "pristine"
        gate.gate.write_new(folder / "before-exit.json", dict(
            action=action, interval=interval, registered=1, guardDenials=[],
            rowsAfterAction=int(pristine or action == "exit"), closeGateHeldOpen=action == "gate",
            observedCompetingWriter=action == "overlap", heartbeatTransactions=1,
            elapsedSeconds=4.1 if timing_overrun else 1.0,
        ))
        gate.gate.write_new(folder / "post-exit.json", dict(
            rows=int(pristine and case == "stop-once"), integrity="ok"
        ))
        gate.gate.write_new(folder / "terminal.json", terminal or dict(state="TERMINAL", returnCode=0))
        has_reopen = pristine and case == "stop-periodic" if reopened is None else reopened
        log = gate.RETURN_MARKER + "\n" + additional_log
        if has_reopen:
            log += gate.REOPEN_WARNING + "\n"
        if action == "gate" and not missing_close_refusal:
            log += "skipping SessionDB close/checkpoint\n"
        (folder / "process.log").write_text(log, encoding="utf-8")
        return folder

    def test_checkout_version_reads_are_exact_commands_at_the_disposable_source(self):
        fixture_spec = importlib.util.spec_from_file_location("heartbeat_fixture", gate.CHILD)
        fixture = importlib.util.module_from_spec(fixture_spec)
        fixture_spec.loader.exec_module(fixture)
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory).resolve()
            for argv in fixture.GIT_VERSION_READS:
                self.assertTrue(fixture.checkout_version_read("git", ["git", *argv], source, None, source))
            for argv in (
                ["git", "fetch"], ["git", "config", "--global", "--list"],
                ["git", "status", "--porcelain"], ["git", "rev-parse", "--git-dir"],
            ):
                self.assertFalse(fixture.checkout_version_read("git", argv, source, None, source))
            query = ["git", "rev-parse", "HEAD"]
            self.assertFalse(fixture.checkout_version_read("git", query, source.parent, None, source))
            self.assertFalse(fixture.checkout_version_read("git", query, source, {"GIT_DIR": "elsewhere"}, source))
            self.assertFalse(fixture.checkout_version_read("sh", query, source, None, source))

    def test_process_stat_read_is_limited_by_name_and_account(self):
        fixture_spec = importlib.util.spec_from_file_location("heartbeat_fixture", gate.CHILD)
        fixture = importlib.util.module_from_spec(fixture_spec)
        fixture_spec.loader.exec_module(fixture)
        path = Mock()
        with patch.object(fixture.os, "geteuid", return_value=123, create=True):
            path.parts = ("/", "proc", "42", "stat")
            path.stat.return_value.st_uid = 123
            self.assertTrue(fixture.same_account_process_stat(path))
            path.stat.return_value.st_uid = 124
            self.assertFalse(fixture.same_account_process_stat(path))
            for leaf in ("environ", "cmdline", "status"):
                path.parts = ("/", "proc", "42", leaf)
                path.stat.reset_mock()
                self.assertFalse(fixture.same_account_process_stat(path))
                path.stat.assert_not_called()

    def test_all_eight_distinct_lifecycle_cases_require_the_intended_observations(self):
        for variant in ("pristine", "candidate"):
            for case in gate.cases(variant):
                with self.subTest(variant=variant, case=case):
                    verdict = gate.check_case(self.fixture(variant, case), variant, case)
                    self.assertEqual(verdict["sqliteIntegrity"], "ok")
                    self.assertEqual(verdict["reopenWarning"], variant == "pristine" and case == "stop-periodic")

    def test_baseline_reopen_cannot_be_passed_as_a_candidate(self):
        folder = self.fixture("candidate", "stop-periodic", reopened=True)
        with self.assertRaisesRegex(ValueError, "reopen observation"):
            gate.check_case(folder, "candidate", "stop-periodic")

    def test_cleanup_diagnostic_after_return_marker_rejects_positive_rows(self):
        folder = self.fixture("candidate", "exit-once", additional_log="Backend heartbeat cleanup failed")
        with self.assertRaisesRegex(ValueError, "action or exit produced a refusal"):
            gate.check_case(folder, "candidate", "exit-once")

    def test_close_gate_needs_both_a_bounded_wait_and_explicit_refusal(self):
        for expected, kwargs in (
            ("exceeded its bounded window", {"timing_overrun": True}),
            ("did not record the held write", {"missing_close_refusal": True}),
        ):
            with self.subTest(expected=expected):
                folder = self.fixture("candidate", "close-gate-held", **kwargs)
                with self.assertRaisesRegex(ValueError, expected):
                    gate.check_case(folder, "candidate", "close-gate-held")

    def test_unknown_process_cannot_be_accepted_from_a_written_marker(self):
        folder = self.fixture("candidate", "exit-periodic",
                              terminal=dict(state="UNKNOWN_PROCESS_PRESERVED_NO_REPLAY", pid=32))
        with self.assertRaisesRegex(ValueError, "not verified terminal"):
            gate.check_case(folder, "candidate", "exit-periodic")
