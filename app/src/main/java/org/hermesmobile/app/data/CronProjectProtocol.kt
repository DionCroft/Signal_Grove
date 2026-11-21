package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.time.ZoneOffset

internal class CronProjectInvalid : Exception()
internal class CronProjectRefused(val code: String, val submitted: Boolean = false) : Exception()

internal data class CronProjectOccurrence(val job: String, val id: String, val scheduledAt: OffsetDateTime) {
    fun summary() = buildJsonObject {
        put("occurrence_id", id)
        put("scheduled_instant", scheduledAt.toString())
    }.toString()

    override fun toString() = "CronProjectOccurrence(redacted)"
}

internal class CronProjectEntry(val occurrence: CronProjectOccurrence, val project: String) {
    override fun toString() = "CronProjectEntry(redacted)"
}

class CronProjectInbox internal constructor(
    internal val owner: ManagementTarget, internal val home: String, internal val entries: List<CronProjectEntry>
) {
    override fun toString() = "CronProjectInbox(redacted)"
}

class CronProjectReview internal constructor(
    internal val inbox: CronProjectInbox, internal val entry: CronProjectEntry,
    internal val digest: String, internal val expiresAt: Long, internal val scope: CronRunOnceScope
) {
    override fun toString() = "CronProjectReview(redacted)"
}

internal class CronProjectObservation(val execution: String, val status: CronExecutionStatus, val denied: Boolean) {
    val settled get() = status in setOf(CronExecutionStatus.Completed, CronExecutionStatus.Failed, CronExecutionStatus.Cancelled)

    fun summary() = buildJsonObject {
        put("execution_id", execution)
        put("status", status.wire)
        put("denied", denied)
    }.toString()

    override fun toString() = "CronProjectObservation(redacted)"
}

internal object CronProjectProtocol {
    const val Action = "CronProjectDecision"
    const val Inbox = "signal_grove.cron.project_inbox_v1"
    const val Prepare = "signal_grove.cron.project_prepare_v1"
    const val Decide = "signal_grove.cron.project_decide_v1"
    const val Observe = "signal_grove.cron.project_observe_v1"
    val methods = setOf(Inbox, Prepare, Decide, Observe)
    private val id = Regex("[a-f0-9]{32}")
    private val digest = Regex("[a-f0-9]{64}")
    private val job = Regex("[A-Za-z0-9][A-Za-z0-9_.:-]{0,255}")
    private val project = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,79}")
    private val occurrenceFields = setOf("profile", "job_id", "occurrence_id")

    fun inbox(owner: ManagementTarget) = buildJsonObject {
        require(CronExecutionHistory.supportsProfile(owner.profile))
        put("expected_profile", owner.profile)
    }

    fun select(owner: ManagementTarget, occurrence: CronProjectOccurrence) = buildJsonObject {
        require(CronExecutionHistory.supportsProfile(owner.profile))
        validateIds(occurrence.job, occurrence.id)
        put("expected_profile", owner.profile)
        put("job_id", occurrence.job)
        put("occurrence_id", occurrence.id)
    }

    fun decide(review: CronProjectReview, decision: CronRunOnceDecision) = JsonObject(
        select(review.inbox.owner, review.entry.occurrence) + mapOf(
            "review_digest" to JsonPrimitive(review.digest),
            "decision" to JsonPrimitive(decision.wire)
        )
    )

    fun listed(wire: JsonObject, owner: ManagementTarget, home: String): CronProjectInbox = checked {
        val body = CronRunOnceProtocol.ownerResult(wire)
        require(body.keys == setOf("profile", "entries") && body.text("profile") == owner.profile)
        val entries = body.getValue("entries").jsonArray
        require(entries.size <= 64)
        val rows = entries.map { raw ->
            val row = raw.jsonObject
            require(row.keys == setOf("job_id", "project_id", "scheduled_instant", "occurrence_id"))
            CronProjectEntry(occurrence(row, row.text("job_id")), row.text("project_id").also { require(project.matches(it)) })
        }
        require(rows.map { it.occurrence.id }.toSet().size == rows.size &&
            rows.map { it.occurrence.job }.toSet().size == rows.size)
        CronProjectInbox(owner, home, java.util.Collections.unmodifiableList(rows))
    }

    fun prepared(wire: JsonObject, inbox: CronProjectInbox, entry: CronProjectEntry, started: Long): CronProjectReview = checked {
        val body = selectionPayload(wire, inbox.owner, entry.occurrence,
            setOf("scheduled_instant", "scope", "review_digest", "expires_in_seconds"))
        require(due(body) == entry.occurrence.scheduledAt)
        val scope = CronRunOnceProtocol.parseScope(body.getValue("scope").jsonObject)
        require(scope.project == entry.project && scope.writes)
        val expiry = body.getValue("expires_in_seconds").jsonPrimitive.also { require(!it.isString) }.int
        require(expiry in 1..180)
        CronProjectReview(inbox, entry, body.text("review_digest").also { require(digest.matches(it)) },
            started + expiry * 1000L, scope)
    }

    /** A claim acknowledges reservation. A separate original-receipt read settles the local record. */
    fun decided(wire: JsonObject, review: CronProjectReview, choice: CronRunOnceDecision): CronProjectObservation? = checked {
        val owner = review.inbox.owner
        val selected = review.entry.occurrence
        if (choice == CronRunOnceDecision.Defer) {
            val body = selectionPayload(wire, owner, selected, setOf("state"))
            require(body.text("state") == "deferred")
            null
        } else {
            val body = selectionPayload(wire, owner, selected, setOf("state", "execution_id", "scheduled_instant"))
            val execution = executionId(owner, selected)
            val denied = choice == CronRunOnceDecision.Deny
            require(body.text("execution_id") == execution && due(body) == selected.scheduledAt)
            require(body.text("state") == if (denied) "denied" else "claimed")
            CronProjectObservation(execution, if (denied) CronExecutionStatus.Cancelled else CronExecutionStatus.Claimed, denied)
        }
    }

    fun observed(wire: JsonObject, owner: ManagementTarget, selected: CronProjectOccurrence): CronProjectObservation = checked {
        val body = selectionPayload(wire, owner, selected,
            setOf("decision", "scheduled_instant", "execution_id", "outcome", "execution"))
        val execution = executionId(owner, selected)
        require(body.text("execution_id") == execution && due(body) == selected.scheduledAt)
        val decision = body.text("decision")
        require(decision in setOf("approve", "deny"))
        val outcome = body.getValue("outcome").takeUnless { it == JsonNull }?.jsonPrimitive
            ?.also { require(it.isString) }?.content?.let(::status)
        require(outcome == null || outcome in setOf(CronExecutionStatus.Unknown, CronExecutionStatus.Completed,
            CronExecutionStatus.Failed, CronExecutionStatus.Cancelled))
        val attempt = body.getValue("execution").takeUnless { it == JsonNull }?.let { CronExecutionHistory.parseAttempt(it) }
        if (attempt != null) {
            require(attempt.id == execution && attempt.scheduledAt == selected.scheduledAt)
            require(attempt.status == outcome || (outcome == null && attempt.status in
                setOf(CronExecutionStatus.Claimed, CronExecutionStatus.Running)))
        }
        require(decision == "approve" || outcome == CronExecutionStatus.Cancelled && attempt?.startedAt == null)
        CronProjectObservation(execution, outcome ?: attempt?.status ?: CronExecutionStatus.Unknown, decision == "deny")
    }

    fun storedOccurrence(row: ManagementOperation): CronProjectOccurrence = checked {
        require(row.action == Action && row.requestSummary.length in 1..160)
        val body = Json.parseToJsonElement(row.requestSummary).jsonObject
        require(body.keys == setOf("occurrence_id", "scheduled_instant"))
        occurrence(body, row.target)
    }

    fun savedSummary(row: ManagementOperation): CronProjectObservation? = runCatching {
        val selected = storedOccurrence(row)
        require(row.resultSummary.length in 1..160)
        val body = Json.parseToJsonElement(row.resultSummary).jsonObject
        require(body.keys == setOf("execution_id", "status", "denied"))
        val execution = body.text("execution_id")
        val owner = ManagementTarget(row.apiScope, row.url, row.profile, row.actor)
        require(execution == executionId(owner, selected))
        val status = status(body.text("status"))
        val denied = body.getValue("denied").jsonPrimitive.also { require(!it.isString) }.boolean
        require(!denied || status == CronExecutionStatus.Cancelled)
        CronProjectObservation(execution, status, denied)
    }.getOrNull()

    private fun occurrence(body: JsonObject, job: String): CronProjectOccurrence {
        val occurrence = body.text("occurrence_id")
        validateIds(job, occurrence)
        return CronProjectOccurrence(job, occurrence, due(body))
    }

    private fun validateIds(jobId: String, occurrence: String) {
        require(jobId != "unknown" && job.matches(jobId) && id.matches(occurrence))
    }

    private fun due(body: JsonObject) = OffsetDateTime.parse(body.text("scheduled_instant").also {
        require(it.length in 1..64)
    }).also { require(it.offset == ZoneOffset.UTC && it.year in 1..9999) }

    private fun executionId(owner: ManagementTarget, selected: CronProjectOccurrence): String {
        validateIds(selected.job, selected.id)
        require(CronExecutionHistory.supportsProfile(owner.profile))
        val key = buildJsonArray { add(owner.profile); add(selected.job); add(selected.id) }
        return MessageDigest.getInstance("SHA-256")
            .digest(("signal-grove-project-execution-v1\u0000" + key).encodeToByteArray())
            .take(16).joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private fun selectionPayload(wire: JsonObject, owner: ManagementTarget, selected: CronProjectOccurrence, fields: Set<String>) =
        CronRunOnceProtocol.ownerResult(wire).also {
            require(it.keys == occurrenceFields + fields && it.text("profile") == owner.profile &&
                it.text("job_id") == selected.job && it.text("occurrence_id") == selected.id)
        }

    private fun status(value: String) = CronExecutionStatus.entries.single { it.wire == value }
    private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.also { require(it.isString) }.content

    private inline fun <T> checked(parse: () -> T): T = try {
        parse()
    } catch (error: CronRunOnceRefused) {
        throw CronProjectRefused(error.code, error.submitted)
    } catch (_: Exception) {
        throw CronProjectInvalid()
    }
}
