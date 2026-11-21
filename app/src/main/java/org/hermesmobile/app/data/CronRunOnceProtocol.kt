package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import java.security.MessageDigest

internal class CronRunOnceInvalid : Exception()
internal class CronRunOnceRefused(val code: String, val submitted: Boolean = false) : Exception()

internal enum class CronRunOnceDecision(val wire: String) {
    Approve(""), Defer("defer"), Deny("deny")
}

internal class CronRunOnceScope(
    val project: String?, val workspace: String, val branch: String?, val worker: String,
    val workerInstance: String, val writes: Boolean, val task: String
) {
    override fun toString() = "CronRunOnceScope(redacted)"
}

class CronRunOnceReview internal constructor(
    internal val owner: ManagementTarget, internal val home: String, internal val job: String,
    internal val request: String, internal val digest: String, internal val expiresAt: Long,
    internal val scope: CronRunOnceScope
) {
    override fun toString() = "CronRunOnceReview(redacted)"
}

internal class CronRunOnceObservation(val execution: String, val status: CronExecutionStatus) {
    val settled get() = status in setOf(CronExecutionStatus.Completed, CronExecutionStatus.Failed,
        CronExecutionStatus.Cancelled, CronExecutionStatus.Missed)

    fun summary() = buildJsonObject { put("execution_id", execution); put("status", status.wire) }.toString()
    override fun toString() = "CronRunOnceObservation(redacted)"
}

internal object CronRunOnceProtocol {
    const val Action = "CronRunOnce"
    const val Prepare = "signal_grove.cron.run_once_prepare_v1"
    const val Confirm = "signal_grove.cron.run_once_confirm_v1"
    const val Dismiss = "signal_grove.cron.run_once_dismiss_v1"
    const val Observe = "signal_grove.cron.run_once_observe_v1"
    val methods = setOf(Prepare, Confirm, Dismiss, Observe)
    private val id = Regex("[a-f0-9]{32}")
    private val hash = Regex("[a-f0-9]{64}")
    private val jobId = Regex("[A-Za-z0-9][A-Za-z0-9_.:-]{0,255}")
    private val errors = setOf("INVALID", "DENIED", "CHANGED", "EXPIRED", "NOT_FOUND", "BUSY",
        "UNAVAILABLE", "UNSUPPORTED", "TOO_LARGE", "DUPLICATE", "IDENTITY_FAILURE")
    private val identityFields = setOf("profile", "job_id", "request_id")
    private val scopeFields = setOf("project", "workspace", "branch", "worker", "worker_instance", "access", "task")

    fun prepare(owner: ManagementTarget, job: String) = buildJsonObject {
        require(CronExecutionHistory.supportsProfile(owner.profile) && job != "unknown" && jobId.matches(job))
        put("expected_profile", owner.profile)
        put("job_id", job)
    }

    fun observe(owner: ManagementTarget, job: String, request: String): JsonObject {
        require(id.matches(request))
        return JsonObject(prepare(owner, job) + ("request_id" to JsonPrimitive(request)))
    }

    fun decide(review: CronRunOnceReview, decision: CronRunOnceDecision) = buildJsonObject {
        observe(review.owner, review.job, review.request).forEach { (key, value) -> put(key, value) }
        put("review_digest", review.digest)
        if (decision != CronRunOnceDecision.Approve) put("decision", decision.wire)
    }

    fun review(wire: JsonObject, owner: ManagementTarget, home: String, job: String, started: Long): CronRunOnceReview = checked {
        val body = payload(wire, owner, job, identityFields + setOf("review_digest", "expires_in_seconds", "scope"))
        val expires = body.getValue("expires_in_seconds").jsonPrimitive.also { require(!it.isString) }.int
        require(expires in 1..180)
        val scope = body.getValue("scope").jsonObject
        require(scope.keys == scopeFields)
        val access = scope.text("access")
        require(access in setOf("read", "write"))
        CronRunOnceReview(owner, home, job, body.text("request_id").also { require(id.matches(it)) },
            body.text("review_digest").also { require(hash.matches(it)) }, started + expires * 1000L,
            CronRunOnceScope(scope.identity("project"), checkNotNull(scope.identity("workspace")), scope.identity("branch"),
                checkNotNull(scope.identity("worker")), checkNotNull(scope.identity("worker_instance")),
                access == "write", scope.text("task").also { require(displaySafe(it, 8192, multiline = true)) }))
    }

    fun confirmed(wire: JsonObject, review: CronRunOnceReview): CronRunOnceObservation = checked {
        val body = payload(wire, review.owner, review.job, identityFields + setOf("execution_id", "state"))
        val execution = executionId(review.owner, review.job, review.request)
        require(body.text("request_id") == review.request && body.text("execution_id") == execution)
        require(body.text("state") == "claimed")
        CronRunOnceObservation(execution, CronExecutionStatus.Claimed)
    }

    fun dismissed(wire: JsonObject, review: CronRunOnceReview, decision: CronRunOnceDecision) = checked {
        val body = payload(wire, review.owner, review.job, identityFields + "decision")
        require(decision != CronRunOnceDecision.Approve && body.text("request_id") == review.request &&
            body.text("decision") == decision.wire)
    }

    fun observed(wire: JsonObject, owner: ManagementTarget, job: String, request: String): CronRunOnceObservation = checked {
        val body = payload(wire, owner, job, identityFields + "execution")
        require(body.text("request_id") == request)
        val attempt = CronExecutionHistory.parseAttempt(body.getValue("execution"))
        require(attempt.id == executionId(owner, job, request) && attempt.scheduledAt == null)
        CronRunOnceObservation(attempt.id, attempt.status)
    }

    fun savedSummary(row: ManagementOperation): CronRunOnceObservation? = runCatching {
        require(row.action == Action && row.resultSummary.length in 1..128)
        val body = Json.parseToJsonElement(row.resultSummary).jsonObject
        require(body.keys == setOf("execution_id", "status"))
        val execution = body.text("execution_id")
        require(execution == executionId(ManagementTarget(row.apiScope, row.url, row.profile, row.actor), row.target, row.key))
        CronRunOnceObservation(execution, CronExecutionStatus.entries.single { it.wire == body.text("status") })
    }.getOrNull()

    private fun executionId(owner: ManagementTarget, job: String, request: String): String {
        observe(owner, job, request)
        val identity = buildJsonObject {
            put("profile", owner.profile)
            put("job_id", job)
            put("request_id", request)
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(("signal-grove-cron-run-once-v1\u0000" + identity).encodeToByteArray())
            .take(16).joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private fun payload(wire: JsonObject, owner: ManagementTarget, job: String, fields: Set<String>): JsonObject {
        require(wire["schema"] == JsonPrimitive(1) && wire.toString().encodeToByteArray().size <= 384 * 1024)
        if (wire["state"] == JsonPrimitive("error")) {
            require(wire.keys == setOf("schema", "state", "code", "request_submitted"))
            val code = wire.text("code")
            require(code in errors)
            val submitted = when (wire["request_submitted"]) {
                JsonPrimitive(true) -> true
                JsonPrimitive(false) -> false
                else -> error("Invalid submission flag.")
            }
            throw CronRunOnceRefused(code, submitted)
        }
        require(wire.keys == setOf("schema", "state", "payload") && wire["state"] == JsonPrimitive("ok"))
        return wire.getValue("payload").jsonObject.also {
            require(it.keys == fields && it.text("profile") == owner.profile && it.text("job_id") == job)
        }
    }

    private fun JsonObject.identity(key: String) = getValue(key).takeUnless { it == JsonNull }
        ?.let { text(key).also { value -> require(displaySafe(value, 4096)) } }

    private fun displaySafe(value: String, limit: Int, multiline: Boolean = false): Boolean =
        value.isNotBlank() && value.codePointCount(0, value.length) <= limit && value.codePoints().noneMatch { code ->
            (Character.isISOControl(code) && !(multiline && code in setOf(9, 10))) ||
                code in 0xd800..0xdfff || code in 0x202a..0x202e || code in 0x2066..0x2069 ||
                code in setOf(0x061c, 0x200e, 0x200f, 0x2028, 0x2029)
        }

    private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.also { require(it.isString) }.content

    private inline fun <T> checked(parse: () -> T): T = try {
        parse()
    } catch (error: CronRunOnceRefused) {
        throw error
    } catch (_: Exception) {
        throw CronRunOnceInvalid()
    }
}
