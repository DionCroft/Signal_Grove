package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test

class CronProjectProtocolTest {
    private val owner = ManagementTarget("a".repeat(64), "https://fixture.example/dashboard/", "worker", "basic · owner")
    private val home = "b".repeat(64)
    private val key = "e".repeat(32)
    private val execution = "7047c736b0b60bf43d79cd179f05a59b"
    private val due = "2026-11-21T09:00:00+00:00"
    private val row = buildJsonObject {
        put("job_id", "job-fixture"); put("occurrence_id", key); put("scheduled_instant", due)
        put("project_id", "registered-fixture")
    }
    private val scope = buildJsonObject {
        put("project", "registered-fixture"); put("workspace", "fixture/workspace")
        put("branch", "main"); put("worker", "FIXTURE-WORKER"); put("worker_instance", "worker-instance")
        put("access", "write"); put("task", "Update the fixture notes.")
    }
    private fun wire(body: JsonObject) = buildJsonObject {
        put("schema", 1); put("state", "ok"); put("payload", body)
    }

    private fun list(vararg rows: JsonObject) = wire(buildJsonObject {
        put("profile", owner.profile); put("entries", JsonArray(rows.toList()))
    })

    private fun selected(fields: JsonObjectBuilder.() -> Unit) = wire(buildJsonObject {
        put("profile", owner.profile); put("job_id", "job-fixture"); put("occurrence_id", key)
        fields()
    })

    private fun preview(scope: JsonObject = this.scope, time: String = due) = selected {
        put("scheduled_instant", time); put("scope", scope); put("review_digest", "c".repeat(64))
        put("expires_in_seconds", 179)
    }

    @Test fun exactInboxAndReviewRejectForeignOrDuplicateScopes() {
        val inbox = CronProjectProtocol.listed(list(row), owner, home)
        val entry = inbox.entries.single()
        val review = CronProjectProtocol.prepared(preview(), inbox, entry, 1000L)
        assertEquals("registered-fixture", review.scope.project)
        assertTrue(review.scope.writes)
        assertEquals(180000L, review.expiresAt)
        val selectors = CronProjectProtocol.decide(review, CronRunOnceDecision.Approve)
        assertEquals(setOf("expected_profile", "job_id", "occurrence_id", "review_digest", "decision"), selectors.keys)
        assertEquals(JsonPrimitive("approve"), selectors["decision"])
        assertFalse(review.toString().contains("fixture"))
        assertThrows(CronProjectInvalid::class.java) { CronProjectProtocol.listed(list(row, row), owner, home) }
        assertThrows(CronProjectInvalid::class.java) {
            CronProjectProtocol.listed(list(row), owner.copy(profile = "other"), home)
        }
        for (value in listOf(preview(time = "2026-11-21T09:00:01Z"),
            preview(JsonObject(scope + ("project" to JsonPrimitive("other")))),
            preview(JsonObject(scope + ("access" to JsonPrimitive("read")))))) {
            assertThrows(CronProjectInvalid::class.java) { CronProjectProtocol.prepared(value, inbox, entry, 1) }
        }
    }

    @Test fun compactOriginalReceiptDistinguishesUnknownFromCompletedOrCancelled() {
        val inbox = CronProjectProtocol.listed(list(row), owner, home)
        val entry = inbox.entries.single().occurrence
        fun observe(outcome: String?, decision: String = "approve", id: String = execution) = selected {
            put("scheduled_instant", due); put("decision", decision); put("outcome", outcome)
            put("execution_id", id); put("execution", JsonNull)
        }
        assertFalse(CronProjectProtocol.observed(observe("unknown"), owner, entry).settled)
        assertFalse(CronProjectProtocol.observed(observe(null), owner, entry).settled)
        assertTrue(CronProjectProtocol.observed(observe("completed"), owner, entry).settled)
        assertTrue(CronProjectProtocol.observed(observe("cancelled", "deny"), owner, entry).denied)
        assertThrows(CronProjectInvalid::class.java) {
            CronProjectProtocol.observed(observe("completed", "deny"), owner, entry)
        }
        assertThrows(CronProjectInvalid::class.java) {
            CronProjectProtocol.observed(observe("completed", id = "1".repeat(32)), owner, entry)
        }
    }

    @Test fun acknowledgementAndPrivateLocalReceiptKeepTheSameOccurrence() {
        val inbox = CronProjectProtocol.listed(list(row), owner, home)
        val review = CronProjectProtocol.prepared(preview(), inbox, inbox.entries.single(), 1000L)
        val ack = selected {
            put("execution_id", execution); put("scheduled_instant", due); put("state", "claimed")
        }
        val result = checkNotNull(CronProjectProtocol.decided(ack, review, CronRunOnceDecision.Approve))
        assertEquals(CronExecutionStatus.Claimed, result.status)
        val record = ManagementOperation("local-key", owner.resourceScope, owner.apiScope, owner.url, owner.profile,
            owner.actor, CronProjectProtocol.Action, review.entry.occurrence.job, review.entry.occurrence.summary(),
            "unknown", result.summary(), 1, home)
        assertEquals(review.entry.occurrence, CronProjectProtocol.storedOccurrence(record))
        assertEquals(execution, CronProjectProtocol.savedSummary(record)?.execution)
        assertFalse(record.requestSummary.contains(review.scope.task))
        val foreign = ManagementReceiptVisibility.project(listOf(record), "c".repeat(64), owner.resourceScope,
            owner.url, home, owner.actor).single()
        assertEquals(ManagementReceiptVisibility.SharedCronProjectPending, foreign.action)
        assertTrue(foreign.requestSummary.isEmpty() && foreign.resultSummary.isEmpty() && foreign.target.isEmpty())
    }
}
