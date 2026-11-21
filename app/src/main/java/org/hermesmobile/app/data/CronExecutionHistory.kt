package org.hermesmobile.app.data

import java.time.OffsetDateTime
import kotlinx.serialization.json.*

internal class InvalidCronExecutionHistory : Exception()
internal class CronExecutionHistoryUnavailable : Exception()

enum class CronExecutionStatus(val wire: String) {
    Claimed("claimed"), Running("running"), Completed("completed"), Failed("failed"),
    Unknown("unknown"), Cancelled("cancelled"), Missed("missed")
}

class CronExecutionAttempt internal constructor(
    val id: String,
    val status: CronExecutionStatus,
    val claimedAt: OffsetDateTime,
    val startedAt: OffsetDateTime?,
    val finishedAt: OffsetDateTime?,
    val scheduledAt: OffsetDateTime?,
    val deliveryOutcome: String?,
    val reportedError: Boolean
) {
    override fun toString() = "CronExecutionAttempt(redacted)"
}

class CronExecutionHistory private constructor(
    val owner: ManagementTarget,
    val homeId: String,
    val jobId: String,
    val ledgerAvailable: Boolean,
    val attempts: List<CronExecutionAttempt>,
    val limited: Boolean
) {
    override fun toString() = "CronExecutionHistory(redacted)"

    companion object {
        internal const val Method = "signal_grove.cron.executions_v1"
        internal const val Limit = 20
        private val jobPattern = Regex("[A-Za-z0-9][A-Za-z0-9_.:-]{0,255}")
        private val profilePattern = Regex("[a-z0-9][a-z0-9_-]{0,63}")
        private val receiptPattern = Regex("[a-f0-9]{32}")
        private val deliveryPattern = Regex("[a-z][a-z0-9_]{0,47}")
        private val payloadFields = setOf("profile", "job_id", "ledger", "attempts", "limited")
        private val attemptFields = setOf("execution_id", "status", "claimed_at", "started_at",
            "finished_at", "scheduled_instant", "delivery_outcome", "reported_error")
        private val refusals = setOf("INVALID", "DENIED", "UNAVAILABLE", "UNSUPPORTED", "CHANGED",
            "NOT_FOUND", "DUPLICATE")

        internal fun supportsProfile(profile: String) =
            profilePattern.matches(profile) && GatewayCronInventory.supportedProfile(profile)

        private fun selector(owner: ManagementTarget, jobId: String, fields: JsonObjectBuilder.() -> Unit) =
            buildJsonObject {
                require(supportsProfile(owner.profile) && jobId != "unknown" && jobPattern.matches(jobId))
                put("expected_profile", owner.profile)
                put("job_id", jobId)
                fields()
            }

        internal fun request(owner: ManagementTarget, jobId: String) = selector(owner, jobId) {
            put("limit", Limit)
        }

        internal fun receiptRequest(owner: ManagementTarget, jobId: String, executionId: String) = selector(owner, jobId) {
            require(receiptPattern.matches(executionId))
            put("execution_id", executionId)
        }

        internal fun parse(
            reply: JsonObject,
            owner: ManagementTarget,
            homeId: String,
            jobId: String
        ): CronExecutionHistory = try {
            val payload = scopedPayload(reply, owner, jobId)
            require(payload.keys == payloadFields)
            val ledger = payload.string("ledger")
            require(ledger == "available" || ledger == "absent")
            val rows = payload["attempts"] as? JsonArray ?: error("Invalid attempts.")
            val limited = payload.flag("limited")
            require(rows.size <= Limit && (!limited || rows.size == Limit))
            require(ledger != "absent" || (rows.isEmpty() && !limited))
            val attempts = rows.map(::parseAttempt)
            require(attempts.map { it.id }.toSet().size == attempts.size)
            CronExecutionHistory(owner, homeId, jobId, ledger == "available",
                java.util.Collections.unmodifiableList(attempts), limited)
        } catch (error: CronExecutionHistoryUnavailable) {
            throw error
        } catch (_: Exception) {
            throw InvalidCronExecutionHistory()
        }

        internal fun parseAttempt(raw: JsonElement): CronExecutionAttempt {
            val row = raw as? JsonObject ?: error("Invalid attempt.")
            require(row.keys == attemptFields)
            val id = row.string("execution_id")
            require(receiptPattern.matches(id))
            val wireStatus = row.string("status")
            val status = CronExecutionStatus.entries.single { it.wire == wireStatus }
            val delivery = row.optionalString("delivery_outcome")
            require(delivery == null || deliveryPattern.matches(delivery))
            return CronExecutionAttempt(id, status, checkNotNull(row.time("claimed_at")), row.time("started_at"),
                row.time("finished_at"), row.time("scheduled_instant"), delivery, row.flag("reported_error"))
        }

        internal fun scopedPayload(reply: JsonObject, owner: ManagementTarget, jobId: String): JsonObject {
            require(reply["schema"] == JsonPrimitive(1))
            if (reply["state"] == JsonPrimitive("error")) {
                require(reply.keys == setOf("schema", "state", "code", "request_submitted"))
                require(reply["request_submitted"] == JsonPrimitive(false) && reply.string("code") in refusals)
                throw CronExecutionHistoryUnavailable()
            }
            require(reply.keys == setOf("schema", "state", "payload") && reply["state"] == JsonPrimitive("ok"))
            val payload = reply["payload"] as? JsonObject ?: error("Invalid ledger payload.")
            require(payload["profile"] == JsonPrimitive(owner.profile) && payload["job_id"] == JsonPrimitive(jobId))
            return payload
        }

        private fun JsonObject.string(key: String): String =
            (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content ?: error("Invalid ledger field.")

        private fun JsonObject.optionalString(key: String): String? =
            if (get(key) == JsonNull) null else string(key)

        private fun JsonObject.flag(key: String): Boolean = when (get(key)) {
            JsonPrimitive(true) -> true
            JsonPrimitive(false) -> false
            else -> error("Invalid ledger flag.")
        }

        private fun JsonObject.time(key: String): OffsetDateTime? = optionalString(key)?.let {
            require(it.length in 1..64)
            OffsetDateTime.parse(it).also { value -> require(value.year in 1..9999) }
        }
    }
}
