package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test

class CronExecutionOutputTest {
    private val owner = ManagementTarget("a".repeat(64), "https://fixture.invalid/dashboard/", "worker", "stub · owner")
    private val job = "job-fixture"
    private val id = "1".repeat(32)
    private val content = "Private saved result"

    private fun payload(
        profile: String = owner.profile, jobId: String = job, executionId: String = id,
        archive: String = "available", text: JsonElement = JsonPrimitive(content), limited: JsonElement = JsonPrimitive(false)
    ) = buildJsonObject {
        put("profile", profile); put("job_id", jobId); put("execution_id", executionId)
        put("status", "unknown"); put("archive_state", archive); put("text", text); put("limited", limited)
    }

    private fun parse(payload: JsonObject) = CronExecutionOutput.parse(buildJsonObject {
        put("schema", 1); put("state", "ok"); put("payload", payload)
    }, owner, job, id)

    @Test fun exactOutputRequestAndPlainTextKeepTheOwnersReportedOutcome() {
        val request = CronExecutionHistory.receiptRequest(owner, job, id)
        assertEquals(buildJsonObject {
            put("expected_profile", "worker"); put("job_id", job); put("execution_id", id)
        }, request)
        assertThrows(IllegalArgumentException::class.java) {
            CronExecutionHistory.receiptRequest(owner, job, "../other")
        }
        val output = parse(payload(text = JsonPrimitive("$content\n庭院\u001b"), limited = JsonPrimitive(true)))
        assertEquals(CronArchiveState.Available, output.archive)
        assertEquals(CronExecutionStatus.Unknown, output.status)
        assertEquals("$content\n庭院\uFFFD", output.text)
        assertTrue(output.limited)
        assertFalse(output.toString().contains(content))
    }

    @Test fun unavailableStatesCarryNoOutputAndRemainDistinct() {
        for (state in listOf(CronArchiveState.Unlinked, CronArchiveState.Missing, CronArchiveState.Changed)) {
            val output = parse(payload(archive = state.wire, text = JsonNull))
            assertEquals(state, output.archive)
            assertNull(output.text)
            assertFalse(output.limited)
        }
    }

    @Test fun rejectsMixedOwnerWrongExecutionUnexpectedFieldsAndOversizedUtf8() {
        val invalid = listOf(
            payload(profile = "other"), payload(jobId = "other-job"), payload(executionId = "2".repeat(32)),
            payload(archive = "changed"), payload(archive = "missing", text = JsonNull, limited = JsonPrimitive(true)),
            payload(text = JsonNull), payload(text = JsonPrimitive("庭".repeat(5462))),
            payload(text = JsonPrimitive("\uD800")), payload(limited = JsonPrimitive("false")),
            JsonObject(payload() - "text"), JsonObject(payload() + ("path" to JsonPrimitive("private/archive.md")))
        )
        invalid.forEach { assertThrows(InvalidCronExecutionOutput::class.java) { parse(it) } }
    }
}
