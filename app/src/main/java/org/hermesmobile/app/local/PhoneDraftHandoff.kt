package org.hermesmobile.app.local

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A text edit is distinct from a send attempt or a staged retry of prior work. */
class UnsentDraft {
    private val mutable = MutableStateFlow("")
    val text = mutable.asStateFlow()
    private var revision = 0L
    private var attempted = false
    class Review internal constructor(internal val owner: UnsentDraft, internal val revision: Long,
        val text: String, internal val scope: String, internal val conversation: String)
    @Synchronized fun change(value: String) {
        if(value == mutable.value) return
        revision++; attempted = false; mutable.value = value
    }
    @Synchronized fun reset() {revision++; attempted = false; mutable.value = ""}
    @Synchronized fun invalidate() {revision++}
    @Synchronized fun markAttempted() {revision++; attempted = true}
    @Synchronized fun isAttempted() = attempted
    @Synchronized fun review(scope: String, conversation: String): Review? =
        mutable.value.takeIf {valid(it) && !attempted}?.let {Review(this, revision, it, scope, conversation)}
    @Synchronized fun matches(review: Review, scope: String, conversation: String) =
        review.owner === this && review.revision == revision && !attempted && review.text == mutable.value &&
            review.scope == scope && review.conversation == conversation && valid(review.text)
    companion object { fun valid(text: String) = text.isNotBlank() && text.toByteArray(Charsets.UTF_8).size <= 8192 }
}

/** One expiring, process-memory-only delivery. Android intents carry only an opaque token. */
class PhoneDraftHandoff(private val now: () -> Long = {System.nanoTime() / 1_000_000}) {
    private data class Pending(val token: String, val text: String, val at: Long)
    private var pending: Pending? = null
    private fun current(): Pending? = pending?.takeIf {now() - it.at in 0 until 300_000L}.also {if(it == null) pending = null}
    @Synchronized fun offer(text: String): String? {
        if(!UnsentDraft.valid(text) || current() != null) return null
        return UUID.randomUUID().toString().also {pending = Pending(it, text, now())}
    }
    @Synchronized fun take(token: String): String? {
        val value = current()?.takeIf {it.token == token} ?: return null
        pending = null; return value.text
    }
    @Synchronized fun cancel(token: String) {if(pending?.token == token) pending = null}
}
