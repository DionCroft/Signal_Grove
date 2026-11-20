package org.hermesmobile.app.local

internal data class PhoneVoiceDraft(val chat: LocalConversation?, val draft: String) {
    val reply = chat?.exchanges?.lastOrNull {
        it.state in setOf(LocalReplyState.COMPLETE, LocalReplyState.TOKEN_LIMIT) && it.reply.isNotBlank()
    }?.reply.orEmpty()

    fun current(state: LocalAiState, currentDraft: String): Boolean =
        chat == state.chat && draft == currentDraft
}

internal fun appendPhoneTranscript(draft: String, transcript: String): String? {
    val addition = transcript.trim()
    if (!validProfileText(addition, 8192)) return null
    val joined = if (draft.isEmpty()) addition else draft + "\n" + addition
    return joined.takeIf { validProfileText(it, 8192) }
}
