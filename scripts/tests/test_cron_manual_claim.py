"""Guard the native manual claim's source anchors and due-scan fence."""

from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

from server_extensions.coordinator_workspaces import install_adapter


JOBS_ANCHORS = """
def mark_job_run(
):
    def apply(jobs, _i, job):
        extra = extra_fire.is_extra(job)  # read before the outcome clears the claim
        held = extra_fire.held_markers(job) if extra else {}
        if extra:  # a manual run: repeat budget and pending occurrence untouched
            return True
        return False
    return apply


def claim_dispatch(job):
    if True:
        if extra_fire.is_extra(job):  # a manual run leaves the scheduled fire's budget alone
            return True
    return False


def heartbeat_fire_claim(job_id: str, *, expected_owner: str) -> bool:
    return True


def _evaluate_due_job(job, scan, ttl):
    now = scan.now
    # Cross-process guard: another process's live one-shot run_claim
    job["scheduled_passed"] = True
    return True
"""


class CronManualClaimSourceTest(unittest.TestCase):
    def stage(self, source=JOBS_ANCHORS):
        with patch.object(install_adapter, "patch_cron_edit", side_effect=lambda original: original):
            return install_adapter.patch_cron_reviewed_run_once(source)

    def test_changed_due_scan_anchor_refuses_staging(self):
        with self.assertRaises(ValueError):
            self.stage(JOBS_ANCHORS.replace("now = scan.now", "now = scheduler_now()"))

    def test_due_tick_defers_only_a_live_reviewed_manual_claim(self):
        check = Mock(return_value=True)
        scope = {"_claim_is_live": check, "FIRE_CLAIM_TTL_SECONDS": 90}
        exec(self.stage(), scope)
        evaluate = scope["_evaluate_due_job"]
        scan = SimpleNamespace(now=20)
        claim = {"signal_grove_run_once": "a" * 32}
        job = {"fire_claim": claim}
        self.assertFalse(evaluate(job, scan, 100))
        self.assertEqual({"fire_claim": claim}, job)
        check.assert_called_once_with(claim, 20, 90)
        check.return_value = False
        self.assertTrue(evaluate(job, scan, 100))
        self.assertTrue(job["scheduled_passed"])

        ordinary = {"fire_claim": {"by": "fixture"}}
        check.reset_mock()
        self.assertTrue(evaluate(ordinary, scan, 100))
        check.assert_not_called()
