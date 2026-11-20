package org.hermesmobile.app.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class TemporaryPhoneChatState(
    val draft: String = "",
    val modelSha256: String? = null,
    val exchanges: List<LocalExchange> = emptyList(),
    val pending: Boolean = false
) {
    val hasContent: Boolean get() = draft.isNotEmpty() || exchanges.isNotEmpty()
    val atTurnLimit: Boolean get() = exchanges.size >= LocalConversationStore.MAX_TURNS

    fun canSend(model: InstalledLocalModel?): Boolean = model != null && !pending && !atTurnLimit &&
        (modelSha256 == null || modelSha256 == model.sha256) && validTemporaryQuestion(draft.trim())
}

/** Foreground state only. Never passed to the history, clipboard or backup facilities. */
internal class TemporaryPhoneChat {
    internal class Request(
        val question: String,
        val modelSha256: String,
        val exchanges: List<LocalExchange>
    )

    private val mutable = MutableStateFlow<TemporaryPhoneChatState?>(null)
    val state = mutable.asStateFlow()
    private var pending: Request? = null

    fun open() {
        if (mutable.value == null) mutable.value = TemporaryPhoneChatState()
    }

    fun edit(text: String) {
        val current = mutable.value ?: return
        if (pending == null && text.length <= MAX_QUESTION_BYTES) mutable.value = current.copy(draft = text)
    }

    fun begin(model: InstalledLocalModel): Request? {
        val current = mutable.value ?: return null
        if (!current.canSend(model)) return null
        val request = Request(current.draft.trim(), model.sha256, current.exchanges)
        pending = request
        mutable.value = current.copy(pending = true)
        return request
    }

    fun complete(request: Request, reply: String, end: LocalGenerationEnd) {
        val current = mutable.value ?: return
        if (pending !== request || end == LocalGenerationEnd.CANCELLED) return
        val replyState = if (end == LocalGenerationEnd.TOKEN_LIMIT) LocalReplyState.TOKEN_LIMIT else LocalReplyState.COMPLETE
        mutable.value = current.copy(
            draft = "",
            modelSha256 = request.modelSha256,
            exchanges = request.exchanges + LocalExchange(request.question, reply, replyState)
        )
    }

    fun finish(request: Request) {
        if (pending !== request) return
        pending = null
        mutable.value = mutable.value?.copy(pending = false)
    }

    /** Drop both the current draft and the identity that could publish a late reply. */
    fun close(): Boolean {
        val generating = pending != null
        pending = null
        mutable.value = null
        return generating
    }

    companion object {
        const val MAX_QUESTION_BYTES = 8192
    }
}

private fun validTemporaryQuestion(text: String) = UnsentDraft.valid(text) &&
    text.none { it.isISOControl() && it !in "\n\r\t" } && Charsets.UTF_8.newEncoder().canEncode(text)
