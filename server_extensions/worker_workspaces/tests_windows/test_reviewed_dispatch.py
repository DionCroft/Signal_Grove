"""Windows checkout holds across reviewed broker transitions."""
from dataclasses import replace
import subprocess
import unittest

from server_extensions.worker_workspaces.reviewed_dispatch import ApprovedProjectWrite, ReviewedWorkerAdmission
from server_extensions.worker_workspaces.tests_windows import test_reviewed_checkout as checkout_fixture
from server_extensions.worker_workspaces.workspace_provider import WorkspaceError


class ReviewedDispatchTest(unittest.TestCase):
    def setUp(self):
        self.native = checkout_fixture.ReviewedCheckoutTest()
        self.addCleanup(self.native.doCleanups)
        self.native.setUp()
        self.checkout = self.native.observe()
        self.now = 10
        self.granted = True
        self.checks = []
        self.approval = ApprovedProjectWrite(
            "a" * 32, "owner-profile", "b" * 64, self.checkout, "model-fixture",
            "c" * 64 + ":worker", "Write the scoped fixture notes.", 40,
        )
        self.gate = self.new_gate()
        self.payload = self.approval.queue_payload()

    def new_gate(self, approval=None):
        return ReviewedWorkerAdmission(self.native.provider, approval or self.approval,
                                       check_write_grant=self.check_grant, git_executable=self.native.git,
                                       clock=lambda: self.now)

    def check_grant(self, approval):
        self.checks.append(approval)
        return self.granted and approval == self.approval

    def test_each_transition_holds_the_exact_git_ref_once(self):
        ref = "refs/heads/review/feature"
        next_commit = self.native.git_call(
            "commit-tree", "HEAD^{tree}", "-p", self.checkout.commit, "-m", "advance fixture",
        ).stdout.decode().strip()
        for operation in (self.gate.claim, self.gate.launch):
            with operation(self.payload) as held:
                attempt = self.native.git_call("update-ref", ref, next_commit, self.checkout.commit, check=False)
                self.assertNotEqual(0, attempt.returncode)
                self.assertEqual(self.checkout, held)
        self.assertEqual(self.checks, [self.approval] * 4)
        for operation in (self.gate.claim, self.gate.launch):
            with self.assertRaisesRegex(WorkspaceError, "DENIED"), operation(self.payload):
                self.fail("The same review cannot be used twice")
        self.native.git_call("update-ref", ref, next_commit, self.checkout.commit)

    def test_scope_or_task_mutation_cannot_claim_or_reuse_review(self):
        for field in ("id", "scope", "project", "profile", "prompt", "binding", "action"):
            with self.subTest(field=field):
                gate = self.new_gate()
                with self.assertRaisesRegex(WorkspaceError, "CHANGED"), gate.claim(self.payload | {field: "other"}):
                    self.fail("An altered row reached the claim")
                with self.assertRaisesRegex(WorkspaceError, "DENIED"), gate.claim(self.payload):
                    self.fail("A rejected review cannot later claim")

    def test_a_read_bound_checkout_is_not_a_write_grant(self):
        self.granted = False
        with self.assertRaisesRegex(WorkspaceError, "DENIED"), self.gate.claim(self.payload):
            self.fail("A read checkout supplied write authority")
        self.assertEqual([self.approval], self.checks)

    def test_grant_and_deadline_rechecked_before_child_creation(self):
        for code in ("DENIED", "EXPIRED"):
            with self.subTest(code=code):
                self.now, self.granted = 10, True
                gate = self.new_gate()
                with gate.claim(self.payload):
                    pass
                if code == "DENIED":
                    self.granted = False
                else:
                    self.now = self.approval.expires_at
                with self.assertRaisesRegex(WorkspaceError, code), gate.launch(self.payload):
                    self.fail("An expired or revoked review reached launch")
                self.assertFalse(gate.launch_attempted)

    def test_wrong_profile_is_denied_even_for_the_same_worker_payload(self):
        foreign = self.new_gate(replace(self.approval, profile="another-profile"))
        with self.assertRaisesRegex(WorkspaceError, "DENIED"), foreign.claim(self.payload):
            self.fail("Approval from another profile reached the claim")



    def test_read_access_label_cannot_construct_a_write_admission(self):
        with self.assertRaisesRegex(WorkspaceError, "INVALID"):
            self.new_gate(replace(self.approval, access="read"))

    def test_job_directory_requires_the_current_launch_context(self):
        with self.assertRaisesRegex(WorkspaceError, "DENIED"), self.gate.job_directory(self.checkout):
            self.fail("A job directory was accepted before launch")
        with self.gate.claim(self.payload):
            pass
        with self.gate.launch(self.payload) as held, self.gate.job_directory(held) as job:
            self.assertEqual(self.native.root / ".agentic/dispatcher/jobs" / self.approval.execution_id, job)
        with self.assertRaisesRegex(WorkspaceError, "DENIED"), self.gate.job_directory(self.checkout):
            self.fail("A durable job directory escaped its launch context")

    def test_job_directory_junction_cannot_touch_its_external_target(self):
        outside = self.native.base / "outside"
        outside.mkdir()
        subprocess.run(["cmd.exe", "/d", "/c", "mklink", "/J", str(self.native.root / ".agentic"), str(outside)],
                       capture_output=True, check=True, timeout=10)
        with self.gate.claim(self.payload):
            pass
        with self.gate.launch(self.payload) as held:
            with self.assertRaisesRegex(WorkspaceError, "DENIED"), self.gate.job_directory(held):
                self.fail("A project junction escaped the reviewed checkout")
        self.assertEqual([], list(outside.iterdir()))
