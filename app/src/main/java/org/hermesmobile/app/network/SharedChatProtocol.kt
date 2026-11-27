package org.hermesmobile.app.network

import java.security.MessageDigest
import kotlinx.serialization.json.*
import org.hermesmobile.app.data.CanonicalFeedOwner

internal class SharedChatRefused private constructor(code: Int) : GatewayRpcException(code) {
    companion object {
        fun parse(code: Int, error: JsonObject): SharedChatRefused? {
            if (code == -32601) return SharedChatRefused(code)
            val reason = (error["data"] as? JsonObject)?.get("reason")
            return if (code == 4001 && error["message"] == reason && reason in listOf(
                    JsonPrimitive("permission_denied"), JsonPrimitive("not_found"), JsonPrimitive("invalid_params")))
                SharedChatRefused(code) else null
        }
    }
}

internal enum class SharedPromptStatus { Queued, Started, Unknown, Terminal }

internal class SharedPromptReceipt(
    val id: String,
    val sequence: Long,
    val status: SharedPromptStatus,
    val outcome: String?,
    val reply: String = "",
    val replyTruncated: Boolean = false
) {
    override fun toString() = "SharedPromptReceipt(redacted)"
}

internal data class SharedApproval(
    val id: String,
    val generation: Long,
    val command: String,
    val description: String,
    val choices: Set<String>
) {
    override fun toString() = "SharedApproval(redacted)"
}

/** Discovery proves a pending request, and a reviewed live snapshot supplies the generation. */
internal class SharedApprovalNotice(
    val id: String,
    val reviewable: Boolean,
    private val fingerprint: String
) {
    fun matches(approval: SharedApproval): Boolean =
        reviewable && id == approval.id &&
            fingerprint == SharedChatProtocol.approvalFingerprint(id, approval.command, approval.description, approval.choices)

    override fun toString() = "SharedApprovalNotice(redacted)"
}

internal object SharedChatProtocol {
    const val Submit = "prompt.submit"
    const val Receipt = "prompt.receipt"
    const val Respond = "approval.respond"
    const val PendingApproval = "approval.pending"
    val methods = setOf(Submit, Receipt, Respond, PendingApproval)
    const val MaxPrompt = 16_384
    private const val MaxReply = 32_768

    fun validPrompt(text: String) = text.isNotBlank() && text.length <= MaxPrompt &&
        text.none { it.isISOControl() && it !in setOf('\n', '\r', '\t') }

    fun submit(session: String, id: String, text: String): JsonObject {
        require(validPrompt(text))
        return buildJsonObject {
            put("session_id", session)
            put("submission_id", id)
            put("text", text)
        }
    }

    fun receipt(owner: CanonicalFeedOwner, j: JsonObject, expectedId: String? = null): SharedPromptReceipt {
        val ref = j["ref"] as? JsonObject ?: throw GatewayProtocolException()
        require(ref["session_id"] == JsonPrimitive(owner.sessionId) && ref["profile_id"] == JsonPrimitive(owner.profileId))
        val id = identifier(j["admission_id"])
        require(expectedId == null || expectedId == id)
        require(number(j["authority_epoch"]) <= owner.authorityEpoch)
        val sequence = number(j["sequence"])
        if (j["execution_generation"] != JsonNull) number(j["execution_generation"])
        val status = when (j["status"]) {
            JsonPrimitive("queued") -> SharedPromptStatus.Queued
            JsonPrimitive("started") -> SharedPromptStatus.Started
            JsonPrimitive("unknown") -> SharedPromptStatus.Unknown
            JsonPrimitive("terminal") -> SharedPromptStatus.Terminal
            else -> throw GatewayProtocolException()
        }
        val outcome = if (j["outcome"] == JsonNull) null else identifier(j["outcome"])
        val reply = if (status == SharedPromptStatus.Terminal) {
            (j["result"] as? JsonObject)?.get("final_response")?.let { value ->
                (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: ""
            }.orEmpty()
        } else ""
        return SharedPromptReceipt(id, sequence, status, outcome,
            reply.take(MaxReply).trimEnd { Character.isHighSurrogate(it) }, reply.length > MaxReply)
    }

    // The shared gateway can consume a typed stop phrase while voice mode is active.
    fun voiceStopped(j: JsonObject) = j == buildJsonObject { put("voice_stopped", true) }

    fun pendingApprovals(j: JsonObject): List<SharedApprovalNotice> {
        val rows = j["approvals"] as? JsonArray ?: throw GatewayProtocolException()
        require(rows.size <= 256)
        val notices = rows.map { value ->
            val row = value as? JsonObject ?: throw GatewayProtocolException()
            val id = identifier(row["request_id"])
            val command = approvalText(row["command"])
            val description = approvalText(row["description"])
            val choices = approvalChoices(row)
            require(choices.size == choices.toSet().size && row["allow_permanent"] == JsonPrimitive("always" in choices))
            val supported = decisionChoices(choices)
            SharedApprovalNotice(id, supported.isNotEmpty() && (command.isNotBlank() || description.isNotBlank()),
                approvalFingerprint(id, command, description, supported))
        }
        require(notices.map { it.id }.toSet().size == notices.size)
        return notices
    }

    internal fun approvalFingerprint(id: String, command: String, description: String, choices: Set<String>): String {
        val fields = JsonArray(listOf(JsonPrimitive(id), JsonPrimitive(command), JsonPrimitive(description),
            JsonArray(choices.sorted().map(::JsonPrimitive))))
        return MessageDigest.getInstance("SHA-256").digest(fields.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun approvalChoices(j: JsonObject): List<String> {
        val choices = j["choices"] as? JsonArray ?: throw GatewayProtocolException()
        require(choices.size in 1..8)
        return choices.map(::identifier)
    }

    private fun decisionChoices(choices: List<String>) = choices.filter { it == "once" || it == "deny" }.toSet()

    fun approval(j: JsonObject): SharedApproval? {
        if (j["kind"] != JsonPrimitive("approval")) return null
        val id = identifier(j["prompt_id"])
        val generation = number(j["execution_generation"])
        val supported = decisionChoices(approvalChoices(j))
        val command = approvalText(j["command"])
        val description = approvalText(j["description"])
        if (supported.isEmpty() || (command.isBlank() && description.isBlank())) return null
        return SharedApproval(id, generation, command, description, supported)
    }

    fun settled(approval: SharedApproval, j: JsonObject) =
        j["prompt_id"] == JsonPrimitive(approval.id) && j["execution_generation"] == JsonPrimitive(approval.generation)

    fun respond(session: String, approval: SharedApproval, choice: String): JsonObject {
        require(choice in approval.choices && choice in setOf("once", "deny"))
        return buildJsonObject {
            put("session_id", session)
            put("prompt_id", approval.id)
            put("execution_generation", approval.generation)
            put("choice", choice)
        }
    }

    fun approvalResult(j: JsonObject, expectedId: String): Boolean {
        require(j["prompt_id"] == JsonPrimitive(expectedId))
        return when (j["status"]) {
            JsonPrimitive("resolved") -> true
            JsonPrimitive("already_resolved") -> false
            else -> throw GatewayProtocolException()
        }
    }

    private fun identifier(value: JsonElement?) = (value as? JsonPrimitive)
        ?.takeIf { it.isString }?.content
        ?.takeIf { it.isNotBlank() && it.length <= 256 && it.none(Char::isISOControl) }
        ?: throw GatewayProtocolException()

    private fun number(value: JsonElement?) =
        (value as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull?.takeIf { it >= 0 }
            ?: throw GatewayProtocolException()

    private fun approvalText(value: JsonElement?): String =
        (value as? JsonPrimitive)?.takeIf { it.isString && it.content.length <= 16_384 }?.content
            ?.takeIf { it.none { c -> c.isISOControl() && c !in setOf('\n', '\r', '\t') } }
            ?: throw GatewayProtocolException()
}
