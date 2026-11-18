package org.hermesmobile.app.local

/** Literal local text only. No regex, model invocation, remote search or hidden snippets. */
data class LocalHistoryQuery(val text: String = "", val modelSha256: String? = null,
    val fromInclusive: Long? = null, val untilExclusive: Long? = null) {
    init {
        require(text.toByteArray(Charsets.UTF_8).size <= 1024 && !text.contains('\u0000'))
        require(modelSha256 == null || modelSha256.matches(Regex("[a-f0-9]{64}")))
        require(fromInclusive == null || fromInclusive >= 0)
        require(untilExclusive == null || untilExclusive >= 0)
        require(fromInclusive == null || untilExclusive == null || fromInclusive < untilExclusive)
    }
}

/** Uses the existing bounded strict reader; caller schedules this away from the UI thread.
 * Keep one transcript at a time. Errors/cancellation return no partial result and write nothing.
 * The application controller must serialize this with its history writes, as for list/load. */
class LocalHistorySearch(private val store: LocalConversationStore) {
    fun find(query: LocalHistoryQuery, checkpoint: () -> Unit = {}): List<LocalConversationSummary> {
        val term = query.text.trim()
        val results = mutableListOf<LocalConversationSummary>()
        for (summary in store.list(checkpoint)) {
            checkpoint()
            // List reads validated summaries. Avoid another transcript read if its
            // model/date already excludes it, and never retain all reply bodies.
            if (!inScope(summary.modelSha256, summary.updatedAt, query)) continue
            val chat = store.load(summary.id, checkpoint)
            if (!inScope(chat.modelSha256, chat.updatedAt, query)) continue
            if (term.isNotEmpty() && !summary.title.contains(term, ignoreCase = true) && chat.exchanges.none {row ->
                    checkpoint()
                    row.question.contains(term, ignoreCase = true) || row.reply.contains(term, ignoreCase = true)
                }) continue
            results.add(LocalConversationSummary(chat.id, chat.modelSha256, chat.updatedAt,
                chat.exchanges.firstOrNull()?.question.orEmpty().filter { !it.isISOControl() }.take(80),
                chat.exchanges.lastOrNull()?.state, chat.origin))
        }
        checkpoint()
        return results.sortedWith(compareByDescending<LocalConversationSummary> {it.updatedAt}.thenBy {it.id})
    }
    private fun inScope(model: String, updated: Long, query: LocalHistoryQuery) =
        (query.modelSha256 == null || query.modelSha256 == model) &&
        (query.fromInclusive == null || updated >= query.fromInclusive) &&
        (query.untilExclusive == null || updated < query.untilExclusive)
}
