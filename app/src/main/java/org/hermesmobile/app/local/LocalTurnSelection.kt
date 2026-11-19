package org.hermesmobile.app.local

internal val LocalExchange.retainedForPrompt: Boolean
    get() = state == LocalReplyState.COMPLETE || state == LocalReplyState.TOKEN_LIMIT

internal fun requireValidTurnSelection(indices: List<Int>, sourceSize: Int) {
    // Keep one slot available for the continuation's next question.
    require(indices.size in 1 until LocalConversationStore.MAX_TURNS)
    require(indices.all { it in 0 until sourceSize } && indices.zipWithNext().all { (earlier, later) -> earlier < later })
}

internal fun LocalConversation.selectedContext(indices: List<Int>): List<LocalExchange> {
    requireValidTurnSelection(indices, exchanges.size)
    return indices.map { exchanges[it].also { row -> require(row.retainedForPrompt) } }
}
