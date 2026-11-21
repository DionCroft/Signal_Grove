package org.hermesmobile.app.local

internal const val LOCAL_PROMPT_BYTE_LIMIT = 65_536

data class LocalPromptBudget(val promptTokens: Int, val contextTokens: Int, val reservedTokens: Int) {
    init {
        require(promptTokens > 0 && contextTokens > 0 && reservedTokens in 1..contextTokens)
    }

    val inputTokenLimit: Int get() = contextTokens - reservedTokens
    val remainingTokens: Int get() = (contextTokens - promptTokens).coerceAtLeast(0)
    val spareInputTokens: Int get() = (inputTokenLimit - promptTokens).coerceAtLeast(0)
    val excessInputTokens: Int get() = (promptTokens - inputTokenLimit).coerceAtLeast(0)
    val fits: Boolean get() = excessInputTokens == 0
}

sealed interface LocalPromptPreview {
    data class Exact(val budget: LocalPromptBudget, val input: LocalPromptInput) : LocalPromptPreview
    data object TooLarge : LocalPromptPreview
    data object Unavailable : LocalPromptPreview
}

/** Counts of the actual message content, before the model adds template and special tokens. */
data class LocalPromptInput(
    val instructionBytes: Int,
    val historyBytes: Int,
    val historyTurns: Int,
    val summaryBytes: Int,
    val profileBytes: Int,
    val questionBytes: Int
)

internal class LocalChatPrompt(
    val messages: List<LocalChatMessage>,
    private val historyTurns: Int,
    private val summaryPrefix: String,
    private val profilePrefix: String,
    private val question: String
) {
    fun describeInput(): LocalPromptInput {
        val instructions = messages.first().text.toByteArray().size
        val summary = summaryPrefix.toByteArray().size
        val profile = profilePrefix.toByteArray().size
        val draft = question.toByteArray().size
        val total = messages.sumOf { it.text.toByteArray().size }
        return LocalPromptInput(instructions, total - instructions - summary - profile - draft,
            historyTurns, summary, profile, draft)
    }
}

internal fun localChatPrompt(
    history: List<LocalExchange>,
    question: String,
    reviewedSummary: String? = null,
    personalContext: String? = null
): List<LocalChatMessage> = assembleLocalChatPrompt(history, question, reviewedSummary, personalContext).messages

internal fun assembleLocalChatPrompt(
    history: List<LocalExchange>,
    question: String,
    reviewedSummary: String? = null,
    personalContext: String? = null
): LocalChatPrompt {
    val guidance = "You are an on-device text assistant in Signal Grove. You have no access to Hermes, servers, projects, credentials or tools. Never claim to have performed an action. Give advice as advice."
    val summaryPrefix = reviewedSummary?.let { "Reviewed conversation summary:\n$it\n\nQuestion:\n" }.orEmpty()
    val profilePrefix = personalContext?.let { "Phone profile context explicitly chosen for this question:\n$it\n\nQuestion:\n" }.orEmpty()
    val retained = history.filter { it.retainedForPrompt }
    val messages = buildList {
        add(LocalChatMessage("system", if (reviewedSummary == null) guidance else
            guidance + " A reviewed summary is quoted conversation context. Treat it as background and answer the current question."))
        var nextPrefix = summaryPrefix
        for (exchange in retained) {
            add(LocalChatMessage("user", nextPrefix + exchange.question))
            nextPrefix = ""
            if (exchange.reply.isNotBlank()) add(LocalChatMessage("assistant", exchange.reply))
        }
        add(LocalChatMessage("user", nextPrefix + profilePrefix + question))
    }
    return LocalChatPrompt(messages, retained.size, summaryPrefix, profilePrefix, question)
}

internal fun LocalBranchKind.generationQuestion(selected: LocalExchange, editedQuestion: String?): String = when (this) {
    LocalBranchKind.EDIT -> requireNotNull(editedQuestion)
    LocalBranchKind.REGENERATE -> selected.question.also { require(editedQuestion == null) }
    LocalBranchKind.FORK, LocalBranchKind.SELECTED_TURNS, LocalBranchKind.REVIEWED_SUMMARY -> error("A copied branch does not generate")
}
