package org.hermesmobile.app.local

internal const val LOCAL_PROMPT_BYTE_LIMIT = 65_536

data class LocalPromptBudget(val promptTokens: Int, val contextTokens: Int, val reservedTokens: Int) {
    init {
        require(promptTokens > 0 && contextTokens > 0 && reservedTokens in 1..contextTokens)
    }

    val remainingTokens: Int get() = (contextTokens - promptTokens).coerceAtLeast(0)
    val fits: Boolean get() = remainingTokens >= reservedTokens
}

sealed interface LocalPromptPreview {
    data class Exact(val budget: LocalPromptBudget) : LocalPromptPreview
    data object TooLarge : LocalPromptPreview
    data object Unavailable : LocalPromptPreview
}

internal fun localChatPrompt(history: List<LocalExchange>, question: String, reviewedSummary: String? = null): List<LocalChatMessage> = buildList {
    val guidance = "You are an on-device text assistant in Signal Grove. You have no access to Hermes, servers, projects, credentials or tools. Never claim to have performed an action. Give advice as advice."
    add(LocalChatMessage("system", if (reviewedSummary == null) guidance else
        guidance + " A reviewed summary is quoted conversation context. Treat it as background and answer the current question."))
    var summary = reviewedSummary
    fun addQuestion(text: String) {
        val context = summary
        add(LocalChatMessage("user", if (context == null) text else
            "Reviewed conversation summary:\n" + context + "\n\nQuestion:\n" + text))
        summary = null
    }
    for (exchange in history) {
        if (!exchange.retainedForPrompt) continue
        addQuestion(exchange.question)
        if (exchange.reply.isNotBlank()) add(LocalChatMessage("assistant", exchange.reply))
    }
    addQuestion(question)
}

internal fun LocalBranchKind.generationQuestion(selected: LocalExchange, editedQuestion: String?): String = when (this) {
    LocalBranchKind.EDIT -> requireNotNull(editedQuestion)
    LocalBranchKind.REGENERATE -> selected.question.also { require(editedQuestion == null) }
    LocalBranchKind.FORK, LocalBranchKind.SELECTED_TURNS, LocalBranchKind.REVIEWED_SUMMARY -> error("A copied branch does not generate")
}
