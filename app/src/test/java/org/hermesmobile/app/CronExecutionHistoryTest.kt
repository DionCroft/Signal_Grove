package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test

class CronExecutionHistoryTest {
    private val owner = ManagementTarget("a".repeat(64), "https://fixture.invalid/dashboard/", "worker", "stub · owner")
    private val home = "b".repeat(64)
    private val job = "job-fixture"

    private fun row(id: String = "1".repeat(32), status: JsonPrimitive = JsonPrimitive("unknown")) = buildJsonObject {
        put("execution_id", id)
        put("status", status)
        put("claimed_at", "2026-11-21T03:15:00+01:00")
        put("started_at", JsonNull)
        put("finished_at", JsonNull)
        put("scheduled_instant", "2026-11-21T02:05:00Z")
        put("delivery_outcome", "delivered")
        put("reported_error", true)
    }

    private fun reply(
        rows: List<JsonObject> = listOf(row()),
        profile: String = owner.profile,
        id: String = job,
        ledger: String = "available",
        limited: Boolean = false
    ) = buildJsonObject {
        put("schema", 1)
        put("state", "ok")
        put("payload", buildJsonObject {
            put("profile", profile)
            put("job_id", id)
            put("ledger", ledger)
            put("limited", limited)
            put("attempts", JsonArray(rows))
        })
    }

    private fun parse(reply: JsonObject) = CronExecutionHistory.parse(reply, owner, home, job)

    @Test fun exactOwnerUsesNonRoutingRequestAndPreservesUnknownStatus() {
        val params = CronExecutionHistory.request(owner, job)
        assertEquals(setOf("expected_profile", "job_id", "limit"), params.keys)
        assertEquals(JsonPrimitive("worker"), params["expected_profile"])
        assertEquals(JsonPrimitive(job), params["job_id"])
        assertEquals(JsonPrimitive(20), params["limit"])
        val page = parse(reply())
        assertEquals(owner, page.owner)
        assertEquals(home, page.homeId)
        assertEquals(job, page.jobId)
        val attempt = page.attempts.single()
        assertEquals(CronExecutionStatus.Unknown, attempt.status)
        assertEquals("2026-11-21T02:15:00Z", attempt.claimedAt.toInstant().toString())
        assertNull(attempt.finishedAt)
        assertEquals("delivered", attempt.deliveryOutcome)
        assertTrue(attempt.reportedError)
        assertFalse(attempt.toString().contains(attempt.id))
        assertFalse(page.toString().contains(owner.actor))
    }

    @Test fun missingLedgerAndEmptyHistoryRemainDistinct() {
        val absent = parse(reply(rows = emptyList(), ledger = "absent"))
        assertTrue(absent.attempts.isEmpty())
        assertFalse(absent.ledgerAvailable)
        val available = parse(reply(rows = emptyList()))
        assertTrue(available.attempts.isEmpty())
        assertTrue(available.ledgerAvailable)
    }

    @Test fun closedContractRejectsForeignDuplicateOverflowAndPrivateFields() {
        val valid = row()
        val badRows = listOf(
            listOf(valid, valid),
            List(21) { row(it.toString(16).padStart(32, '0')) },
            listOf(row("../receipt")),
            listOf(row(status = JsonPrimitive("guessed-success"))),
            listOf(JsonObject(valid + ("claimed_at" to JsonPrimitive("2026-11-21T03:15:00")))),
            listOf(JsonObject(valid + ("reported_error" to JsonPrimitive("true")))),
            listOf(JsonObject(valid + ("error" to JsonPrimitive("private diagnostic"))))
        )
        val replies = badRows.map { reply(rows = it) } +
            listOf(reply(profile = "foreign"), reply(id = "wrong-job"), reply(ledger = "absent"), reply(limited = true))
        replies.forEach { assertThrows(InvalidCronExecutionHistory::class.java) { parse(it) } }
    }

    @Test fun supportedRefusalDoesNotBecomeAnEmptySuccess() {
        val refusal = buildJsonObject {
            put("schema", 1); put("state", "error"); put("code", "UNSUPPORTED"); put("request_submitted", false)
        }
        assertThrows(CronExecutionHistoryUnavailable::class.java) { parse(refusal) }
        val ambiguous = JsonObject(refusal + ("request_submitted" to JsonPrimitive(true)))
        assertThrows(InvalidCronExecutionHistory::class.java) { parse(ambiguous) }
    }
}
