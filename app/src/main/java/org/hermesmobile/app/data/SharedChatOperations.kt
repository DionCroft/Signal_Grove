package org.hermesmobile.app.data

import java.util.UUID
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.hermesmobile.app.network.*

/** Prompt text stays in the foreground composer. Durable records contain only owner and admission metadata. */
internal class SharedChatPending(
    val key: String,
    val admissionId: String?,
    val sequence: Long?
) {
    override fun toString() = "SharedChatPending(redacted)"
}

internal class SharedChatOperations(
    private val dao: HermesDao,
    private val channel: CanonicalGatewayChannel,
    private val snapshot: CanonicalSessionSnapshot,
    private val verifyScope: suspend () -> Unit
) {
    private val owner get() = snapshot.owner
    private val target get() = owner.target
    val available get() = owner.sessionId == snapshot.requestedSessionId

    private suspend fun active(): ManagementOperation? {
        val rows = dao.pendingSharedPrompts(owner.homeId, owner.sessionId)
        if (rows.size > 1) throw ManagementOutcomePending()
        return rows.singleOrNull()?.also { row ->
            if (row.apiScope != target.apiScope || row.resourceScope != target.resourceScope ||
                row.actor != target.actor || row.url != target.url || row.profile != target.profile ||
                row.requestSummary != identity()) throw ManagementOutcomePending()
        }
    }

    suspend fun pending(): SharedChatPending? = active()?.let { row ->
        val accepted = if (row.state == "acknowledged") Json.parseToJsonElement(row.resultSummary).jsonObject else null
        SharedChatPending(row.key, accepted?.getValue("admission_id")?.jsonPrimitive?.content,
            accepted?.getValue("sequence")?.jsonPrimitive?.long)
    }

    private fun identity() = buildJsonObject { put("profile_id", owner.profileId) }.toString()

    suspend fun send(text: String): SharedPromptReceipt? {
        require(available && SharedChatProtocol.validPrompt(text))
        verifyScope()
        val key = UUID.randomUUID().toString()
        val row = ManagementOperation(key, target.resourceScope, target.apiScope, target.url, target.profile, target.actor,
            "SharedChatPrompt", owner.sessionId, identity(), "sending", "", System.currentTimeMillis(), owner.homeId)
        var attempted = false
        try {
            dao.claimSharedPrompt(row)
            currentCoroutineContext().ensureActive()
            attempted = true
            val receipt = channel.submitPrompt(snapshot, key, text)
            val status = if (receipt == null) "confirmed" else "acknowledged"
            withContext(NonCancellable) { dao.finishManagementOperation(key, status, summary(receipt)) }
            return receipt
        } catch (failure: Exception) {
            withContext(NonCancellable) {
                val state = if (!attempted || failure is SharedChatRefused) "refused" else "unknown"
                dao.finishManagementOperation(key, state, "")
            }
            throw failure
        }
    }

    suspend fun observe(expected: SharedChatPending): SharedPromptReceipt {
        require(available)
        val saved = pending() ?: throw CanonicalReviewChanged()
        if (saved.key != expected.key || saved.admissionId != expected.admissionId || saved.sequence != expected.sequence)
            throw CanonicalReviewChanged()
        val admissionId = saved.admissionId ?: throw CanonicalReviewChanged()
        val receipt = channel.promptReceipt(snapshot, admissionId)
        if (receipt.sequence != saved.sequence) throw CanonicalReviewChanged()
        if (receipt.status == SharedPromptStatus.Terminal) withContext(NonCancellable) {
            dao.confirmManagementOperation(saved.key, summary(receipt))
        }
        return receipt
    }

    suspend fun reviewUnknown(expected: SharedChatPending) {
        require(available && expected.admissionId == null)
        verifyScope()
        val saved = pending() ?: throw CanonicalReviewChanged()
        if (saved.key != expected.key || saved.admissionId != null ||
            dao.dismissUnknownSharedPrompt(saved.key) != 1) throw CanonicalReviewChanged()
    }

    private fun summary(receipt: SharedPromptReceipt?) = buildJsonObject {
        if (receipt != null) {
            put("admission_id", receipt.id)
            put("sequence", receipt.sequence)
        }
    }.toString()
}
