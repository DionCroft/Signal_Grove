package org.hermesmobile.app.ui

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonObject
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*

internal data class SharedChatState(
    val available: Boolean = false,
    val draft: String = "",
    val pending: SharedChatPending? = null,
    val status: Words? = null,
    val reply: String = "",
    val replyTruncated: Boolean = false,
    val approvals: List<SharedApproval> = emptyList(),
    val busy: Boolean = false,
    val checking: Boolean = false
) {
    override fun toString() = "SharedChatState(redacted)"
}

internal class SharedChatController(
    private val scope: CoroutineScope,
    private val guard: (Any, Boolean) -> Unit,
    private val pollDelay: Long = 2_500
) {
    val state = MutableStateFlow(SharedChatState())
    private var attached: CanonicalViewerLink? = null
    private var isCurrent: (() -> Boolean)? = null
    private var action: Job? = null
    private var poll: Job? = null
    private var ready = false

    suspend fun attach(link: CanonicalViewerLink, current: () -> Boolean) {
        close()
        if (!link.sharedChatAvailable) return
        val pending = link.pendingPrompt()
        if (!current()) return
        val approvals = link.attachment.snapshot.approvalRows.mapNotNull {
            SharedChatProtocol.approval(it as? JsonObject ?: throw GatewayProtocolException())
        }
        require(approvals.map { it.id }.distinct().size == approvals.size)
        attached = link
        isCurrent = current
        ready = false
        state.value = SharedChatState(available = true, approvals = approvals, pending = pending,
            status = if (pending?.admissionId == null && pending != null) Words.SharedPromptUnknown else null)
    }

    fun start() {
        ready = true
        val link = attached ?: return
        state.value.pending?.takeIf { it.admissionId != null }?.let { watch(link, it) }
    }

    fun apply(events: List<CanonicalFeedEvent>) {
        val link = attached ?: return
        if (!current(link)) return
        val snapshotSequence = link.attachment.snapshot.cursor.sequence
        val before = state.value.approvals
        var approvals = before
        for (event in events) {
            if (event.sequence <= snapshotSequence) continue
            when (event.type) {
                "approval.request" -> SharedChatProtocol.approval(event.payload)?.let { approval ->
                    approvals = (approvals.filterNot { it.id == approval.id } + approval).takeLast(64)
                }
                "approval.settled" -> approvals = approvals.filterNot { SharedChatProtocol.settled(it, event.payload) }
            }
        }
        if (approvals != before) state.value = state.value.copy(approvals = approvals)
    }

    fun edit(text: String) {
        if (ready && !state.value.busy && text.length <= SharedChatProtocol.MaxPrompt) {
            state.value = state.value.copy(draft = text)
        }
    }

    fun send() {
        val before = state.value
        if (before.pending != null || !SharedChatProtocol.validPrompt(before.draft)) return
        run(Words.SharedPromptUnknown) { link ->
            val receipt = link.sendPrompt(before.draft)
            if (!current(link)) return@run
            val pending = link.pendingPrompt()
            if (!current(link)) return@run
            state.value = state.value.copy(draft = "", pending = pending, reply = "", replyTruncated = false,
                status = if (receipt == null) Words.SharedVoiceStopped else status(receipt))
            if (pending != null) watch(link, pending)
        }
    }

    fun reviewedUnknown(pending: SharedChatPending) {
        if (state.value.pending !== pending || pending.admissionId != null) return
        run(Words.SharedPromptUnknown) { link ->
            link.reviewUnknownPrompt(pending)
            if (current(link)) state.value = state.value.copy(pending = null, status = null, draft = "")
        }
    }

    fun decide(approval: SharedApproval, choice: String) {
        if (approval !in state.value.approvals || choice !in approval.choices) return
        run(Words.SharedApprovalUnknown) { link ->
            val resolved = link.answerApproval(approval, choice)
            if (current(link)) state.value = state.value.copy(approvals = state.value.approvals - approval,
                status = if (resolved) Words.SharedApprovalRecorded else Words.SharedApprovalAlreadyResolved)
        }
    }

    private fun current(link: CanonicalViewerLink) = attached === link && isCurrent?.invoke() == true

    private fun run(uncertain: Words, block: suspend (CanonicalViewerLink) -> Unit) {
        val link = attached ?: return
        if (!ready || !current(link) || state.value.busy) return
        state.value = state.value.copy(busy = true, status = null)
        val owner = Any()
        guard(owner, true)
        action = scope.launch {
            try {
                block(link)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (current(link)) {
                    val pending = runCatching { link.pendingPrompt() }.getOrNull() ?: state.value.pending
                    if (current(link)) state.value = state.value.copy(
                        pending = pending,
                        draft = if (pending?.admissionId == null && pending != null) "" else state.value.draft,
                        status = when (e) {
                            is SharedChatRefused -> Words.SharedChatRefused
                            is ManagementOutcomePending -> Words.ManagementOutcomePending
                            else -> uncertain
                        })
                }
            } finally {
                if (current(link)) state.value = state.value.copy(busy = false)
            }
        }.also { it.invokeOnCompletion { guard(owner, false) } }
    }

    private fun watch(link: CanonicalViewerLink, pending: SharedChatPending) {
        if (pending.admissionId == null) return
        poll?.cancel()
        poll = scope.launch {
            try {
                while (current(link)) {
                    if (!state.value.busy) {
                        state.value = state.value.copy(checking = true)
                        val receipt = try { link.checkPrompt(pending) }
                        finally { if (current(link)) state.value = state.value.copy(checking = false) }
                        if (!current(link)) break
                        val done = receipt.status == SharedPromptStatus.Terminal
                        state.value = state.value.copy(pending = if (done) null else pending,
                            status = status(receipt), reply = receipt.reply, replyTruncated = receipt.replyTruncated)
                        if (done || receipt.status == SharedPromptStatus.Unknown) break
                    }
                    delay(pollDelay)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (current(link)) state.value = state.value.copy(status = Words.SharedReceiptUnavailable)
            }
        }
    }

    private fun status(receipt: SharedPromptReceipt) = when (receipt.status) {
        SharedPromptStatus.Queued -> Words.SharedPromptQueued
        SharedPromptStatus.Started -> Words.SharedPromptStarted
        SharedPromptStatus.Unknown -> Words.SharedPromptUnknown
        SharedPromptStatus.Terminal -> when (receipt.outcome) {
            "completed", "success" -> Words.SharedPromptCompleted
            else -> Words.SharedPromptEnded
        }
    }

    fun hold() {
        ready = false
        attached = null
        isCurrent = null
        action?.cancel()
        poll?.cancel()
        val before = state.value
        state.value = before.copy(available = false, busy = false, checking = false, approvals = emptyList(),
            status = if (before.busy) Words.SharedReceiptUnavailable else before.status)
    }

    fun close() {
        hold()
        state.value = SharedChatState()
    }
}
