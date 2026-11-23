package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test

class CronRunOnceProtocolTest {
    private val owner = ManagementTarget("a".repeat(64), "https://fixture.example/dashboard/", "worker", "password · owner")
    private val home = "b".repeat(64)
    private val job = "job:fixture"
    private val request = "a".repeat(32)
    private val digest = "c".repeat(64)
    private val execution = "94a09a6eb293ff4e9d2fbad61bb9c606"
    private val scope = buildJsonObject {
        put("project", JsonNull)
        put("workspace", "fixtures/工作区 🌱")
        put("branch", JsonNull)
        put("worker", "FIXTURE-WORKER")
        put("worker_instance", "worker-instance")
        put("access", "write")
        put("task", "Write the fixture summary.\nKeep the original.")
    }
    private fun wire(more: JsonObjectBuilder.() -> Unit) = buildJsonObject {
        put("schema", 1); put("state", "ok")
        putJsonObject("payload") {
            put("profile", owner.profile); put("job_id", job); put("request_id", request)
            more()
        }
    }
    private fun preview(scope: JsonObject = this.scope, expires: Int = 179) = wire {
        put("review_digest", digest); put("expires_in_seconds", expires); put("scope", scope)
    }
    private fun review() = CronRunOnceProtocol.review(preview(), owner, home, job, 1000L)

    @Test fun exactReviewAndApprovalParametersKeepTheOriginalScope() {
        val review = review()
        assertEquals("fixtures/工作区 🌱", review.scope.workspace)
        assertTrue(review.scope.writes)
        assertNull(review.scope.branch)
        assertNull(review.scope.commit)
        assertEquals(180000L, review.expiresAt)
        assertFalse(review.toString().contains(review.scope.task))
        assertEquals(setOf("expected_profile", "job_id"), CronRunOnceProtocol.prepare(owner, job).keys)
        val approval = CronRunOnceProtocol.decide(review, CronRunOnceDecision.Approve)
        assertEquals(setOf("expected_profile", "job_id", "request_id", "review_digest"), approval.keys)
        assertEquals(JsonObject(approval + ("decision" to JsonPrimitive("defer"))),
            CronRunOnceProtocol.decide(review, CronRunOnceDecision.Defer))
        CronRunOnceProtocol.dismissed(wire { put("decision", "deny") }, review, CronRunOnceDecision.Deny)
    }

    @Test fun projectWriteRequiresTheExactReviewedBranchAndFullCommit() {
        val project = scope + ("project" to JsonPrimitive("fixture-project"))
        val branch = project + ("branch" to JsonPrimitive("approved-branch"))
        val oid = "a".repeat(40)
        val reviewed = CronRunOnceProtocol.review(preview(JsonObject(branch + ("commit" to JsonPrimitive(oid)))),
            owner, home, job, 1)
        assertEquals(oid, reviewed.scope.commit)
        assertEquals("approved-branch", reviewed.scope.branch)
        for (bad in listOf(JsonObject(branch), JsonObject(branch + ("commit" to JsonPrimitive("a".repeat(12)))),
            JsonObject(branch + ("commit" to JsonNull)), JsonObject(project + ("commit" to JsonPrimitive(oid))))) {
            assertThrows(CronRunOnceInvalid::class.java) {
                CronRunOnceProtocol.review(preview(bad), owner, home, job, 1)
            }
        }
    }

    @Test fun unfamiliarOrMisleadingReviewFieldsFailClosed() {
        val cases = listOf(preview(expires = 181), preview(expires = 0),
            preview(JsonObject(scope + ("task" to JsonPrimitive("hide\u202escope")))),
            preview(JsonObject(scope + ("worker" to JsonPrimitive("\nworker")))),
            preview(JsonObject(scope + ("unknown" to JsonPrimitive("unsafe")))))
        for (bad in cases) assertThrows(CronRunOnceInvalid::class.java) {
            CronRunOnceProtocol.review(bad, owner, home, job, 1)
        }
        assertThrows(CronRunOnceInvalid::class.java) {
            CronRunOnceProtocol.review(preview(), owner.copy(profile = "other"), home, job, 1)
        }
    }

    @Test fun onlyTheExactNativeExecutionCanAcknowledgeOrSettleTheRun() {
        val review = review()
        assertEquals(CronExecutionStatus.Claimed, CronRunOnceProtocol.confirmed(
            wire { put("execution_id", execution); put("state", "claimed") }, review).status)
        assertThrows(CronRunOnceInvalid::class.java) {
            CronRunOnceProtocol.confirmed(wire { put("execution_id", execution); put("state", "completed") }, review)
        }
        fun observed(status: String, id: String = execution, scheduled: String? = null) = wire {
            putJsonObject("execution") {
                put("execution_id", id); put("status", status); put("claimed_at", "2026-11-21T02:00:00Z")
                put("started_at", JsonNull); put("finished_at", JsonNull); put("scheduled_instant", scheduled)
                put("delivery_outcome", JsonNull); put("reported_error", false)
            }
        }
        assertFalse(CronRunOnceProtocol.observed(observed("unknown"), owner, job, request).settled)
        assertFalse(CronRunOnceProtocol.observed(observed("running"), owner, job, request).settled)
        assertTrue(CronRunOnceProtocol.observed(observed("completed"), owner, job, request).settled)
        assertThrows(CronRunOnceInvalid::class.java) {
            CronRunOnceProtocol.observed(observed("completed", "1".repeat(32)), owner, job, request)
        }
        assertThrows(CronRunOnceInvalid::class.java) {
            CronRunOnceProtocol.observed(observed("completed", scheduled = "2026-11-21T02:00:00Z"), owner, job, request)
        }
    }

    @Test fun aReportedSubmissionSurvivesARefusalReply() {
        val refused = assertThrows(CronRunOnceRefused::class.java) {
            CronRunOnceProtocol.confirmed(buildJsonObject {
                put("schema", 1); put("state", "error"); put("code", "DENIED"); put("request_submitted", true)
            }, review())
        }
        assertTrue(refused.submitted)
    }
}
