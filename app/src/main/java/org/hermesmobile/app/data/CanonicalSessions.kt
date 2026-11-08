package org.hermesmobile.app.data

import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.json.*
import org.hermesmobile.app.network.CanonicalRuntimeBinding
import org.hermesmobile.app.network.GatewayProtocolException

internal object CanonicalResumeAction { const val Id = "CanonicalResume" }

internal class CanonicalReviewChanged : Exception("The canonical session review changed.")
internal class CanonicalApprovalUsed : Exception("This session approval was already used.")

internal enum class CanonicalSessionScope(val wire: String) { Stored("stored"), Live("live") }

internal data class CanonicalReplayCheckpoint(val owner: CanonicalFeedOwner, val cursor: CanonicalReplayCursor) {
    override fun toString() = "CanonicalReplayCheckpoint(redacted)"
}

internal class CanonicalStoredSession(
    val id: String,
    val sessionId: String,
    val resolvedId: String?,
    val title: String,
    val source: String?,
    val model: String?,
    val messageCount: Long,
    val startedAt: Double?,
    val reportedRunning: Boolean,
    internal val fingerprint: String
) {
    override fun toString() = "CanonicalStoredSession(redacted)"
}

internal class CanonicalSessionInventory(
    val binding: CanonicalRuntimeBinding,
    val rows: List<CanonicalStoredSession>,
    val scope: CanonicalSessionScope = CanonicalSessionScope.Stored
) {
    override fun toString() = "CanonicalSessionInventory(redacted)"
    fun review(sessionId: String): CanonicalResumeReview {
        val row = rows.singleOrNull { it.sessionId == sessionId } ?: throw CanonicalReviewChanged()
        return CanonicalResumeReview.create(this, row)
    }
}

internal class CanonicalResumeReview private constructor(
    val expected: CanonicalSessionInventory,
    val row: CanonicalStoredSession
) {
    private val consumed = AtomicBoolean(false)
    internal fun consume(current: CanonicalSessionInventory) {
        if (!consumed.compareAndSet(false, true)) throw CanonicalApprovalUsed()
        val old = expected.binding
        val now = current.binding
        if (expected.scope != current.scope || old.target != now.target || old.homeId != now.homeId || old.profileId != now.profileId ||
            old.instanceId != now.instanceId || old.authorityEpoch != now.authorityEpoch ||
            current.rows.singleOrNull { it.sessionId == row.sessionId }?.fingerprint != row.fingerprint) throw CanonicalReviewChanged()
    }
    internal fun summary() = buildJsonObject {
        put("profile_home", expected.binding.homeId)
        put("inventory_scope", expected.scope.wire)
        put("instance_id", expected.binding.instanceId)
        put("authority_epoch", expected.binding.authorityEpoch)
        put("requested_session_id", row.sessionId)
        put("review_fingerprint", row.fingerprint)
        put("viewer_lifecycle_reviewed", true)
        put("automatic_replay", false)
    }.toString()
    override fun toString() = "CanonicalResumeReview(redacted)"
    companion object {
        internal fun create(inventory: CanonicalSessionInventory, row: CanonicalStoredSession) =
            CanonicalResumeReview(inventory, row)
    }
}

/** Snapshot stamps are reported observations. running=false is not an idle/terminal grant. */
internal class CanonicalSessionSnapshot(
    val owner: CanonicalFeedOwner,
    val requestedSessionId: String,
    val reportedStoredId: String,
    val subscriptionId: String,
    val cursor: CanonicalReplayCursor,
    val revision: Long,
    val executionGeneration: Long,
    val reportedRunning: Boolean,
    val messageCount: Long,
    val messagesOmitted: Boolean
) {
    override fun toString() = "CanonicalSessionSnapshot(redacted)"
    internal fun receiptSummary() = buildJsonObject {
        put("requested_session_id", requestedSessionId)
        put("resolved_session_id", owner.sessionId)
        put("reported_stored_id", reportedStoredId)
        put("authority_epoch", owner.authorityEpoch)
        put("revision", revision)
        put("execution_generation", executionGeneration)
        put("execution_outcome", "unproven")
        put("viewer_attached", true)
    }.toString()
}

internal object CanonicalSessionProtocol {
    private fun text(j: JsonObject, key: String, max: Int = 8192): String =
        (j[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?.takeIf { it.isNotBlank() && it.length <= max && it.none(Char::isISOControl) &&
                it.codePoints().noneMatch { c -> c in 0xD800..0xDFFF } } ?: throw GatewayProtocolException()
    private fun optional(j: JsonObject, key: String, max: Int): String? =
        if (j[key] == null || j[key] == JsonNull) null else text(j, key, max)
    private fun label(j: JsonObject, key: String, max: Int): String? {
        val value = j[key]
        if (value == null || value == JsonNull) return null
        return (value as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?.takeIf { it.length <= max && it.none(Char::isISOControl) && it.codePoints().noneMatch { c -> c in 0xD800..0xDFFF } }
            ?: throw GatewayProtocolException()
    }
    private fun number(j: JsonObject, key: String): Long =
        (j[key] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull?.takeIf { it >= 0 } ?: throw GatewayProtocolException()
    private fun flag(j: JsonObject, key: String): Boolean =
        (j[key] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull ?: throw GatewayProtocolException()
    private fun digest(j: JsonObject) = MessageDigest.getInstance("SHA-256").digest(j.toString().toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun inventory(j: JsonObject, binding: CanonicalRuntimeBinding, scope: CanonicalSessionScope = CanonicalSessionScope.Stored): CanonicalSessionInventory {
        if (j["scope"] != JsonPrimitive(scope.wire)) throw GatewayProtocolException()
        val rows = j["sessions"] as? JsonArray ?: throw GatewayProtocolException()
        require(rows.size <= 200)
        val parsed = rows.map { element ->
            val row = element as? JsonObject ?: throw GatewayProtocolException()
            val title = (row["title"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw GatewayProtocolException()
            require(title.length <= 4096 && title.none { it == '\u0000' } && title.codePoints().noneMatch { it in 0xD800..0xDFFF })
            val start = row["started_at"].let { value ->
                if (value == null || value == JsonNull) null
                else (value as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.takeIf { it.isFinite() && it >= 0 }
                    ?: throw GatewayProtocolException()
            }
            val normalized = buildJsonObject {
                put("id", text(row, "id")); put("session_id", text(row, "session_id"))
                put("resolved_id", optional(row, "resolved_id", 8192)?.let(::JsonPrimitive) ?: JsonNull)
                put("title", title); put("source", label(row, "source", 256)?.let(::JsonPrimitive) ?: JsonNull)
                put("model", label(row, "model", 1024)?.let(::JsonPrimitive) ?: JsonNull)
                number(row, "message_count") // Observe counts without making a changing count an approval fence.
                put("started_at", start?.let(::JsonPrimitive) ?: JsonNull)
                flag(row, "running") // Activity observations do not grant authority or invalidate a viewer review.
            }
            CanonicalStoredSession(text(row,"id"), text(row,"session_id"), optional(row,"resolved_id",8192), title,
                label(row,"source",256),label(row,"model",1024),number(row,"message_count"),start,flag(row,"running"),digest(normalized))
        }
        require(parsed.map { it.sessionId }.toSet().size == parsed.size)
        return CanonicalSessionInventory(binding, parsed, scope)
    }

    fun snapshot(j: JsonObject, binding: CanonicalRuntimeBinding, requested: String): CanonicalSessionSnapshot {
        val epoch = number(j, "authority_epoch")
        if (epoch != binding.authorityEpoch) throw CanonicalReviewChanged()
        val messages = j["messages"] as? JsonArray ?: throw GatewayProtocolException()
        val omitted = j["messages_omitted"].let { if (it == null || it == JsonNull) false else flag(j,"messages_omitted") }
        if (omitted && messages.isNotEmpty()) throw GatewayProtocolException()
        val count = number(j,"message_count")
        if (!omitted && count != messages.size.toLong()) throw GatewayProtocolException()
        // This stage never exposes transcript or prompt text through operation receipts.
        require(messages.size <= 2048 && messages.all { it is JsonObject })
        val info = j["info"] as? JsonObject ?: throw GatewayProtocolException()
        if (info["desktop_protocol"] != JsonPrimitive("hermes-gateway-v1")) throw GatewayProtocolException()
        optional(info,"profile_id",8192)?.let {
            if (org.hermesmobile.app.data.PluginHome.project(JsonPrimitive(it))?.identity() != binding.homeId) throw CanonicalReviewChanged()
        }
        for (key in listOf("pending","prompts")) require(j[key] is JsonArray && j[key]!!.jsonArray.size <= 256)
        val owner = CanonicalFeedOwner(binding.target,binding.homeId,binding.profileId,binding.instanceId,text(j,"session_id"),epoch)
        return CanonicalSessionSnapshot(owner,requested,text(j,"stored_session_id"),text(j,"subscription_id",256),
            CanonicalReplayCursor(text(j,"replay_epoch",256),number(j,"last_sequence")),number(j,"revision"),
            number(j,"execution_generation"),flag(j,"running"),count,omitted)
    }
}
