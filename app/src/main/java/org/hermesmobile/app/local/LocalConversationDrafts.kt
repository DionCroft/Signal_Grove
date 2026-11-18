package org.hermesmobile.app.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory drafts follow local chat IDs, never Hermes IDs or Android saved-state bundles. */
class LocalConversationDrafts(initialId: String?) {
    private val texts = mutableMapOf<String?, String>()
    private var selected = initialId
    private val mutable = MutableStateFlow("")
    val text = mutable.asStateFlow()
    val selectedId: String? get() = selected

    fun edit(value: String) { mutable.value = value }
    fun select(id: String?) {
        if (selected == id) return
        rememberCurrent()
        selected = id
        mutable.value = texts.remove(id).orEmpty()
    }
    fun canReceiveFresh(): Boolean = mutable.value.isEmpty() && texts[null].isNullOrEmpty()
    fun forget(id: String) {
        texts.remove(id)
        if (selected == id) mutable.value = ""
    }
    /** Clear only the exact submitted draft after the question actually exists on disk. */
    fun published(sourceId: String?, submittedDraft: String, publishedId: String) {
        if (selected == sourceId && mutable.value == submittedDraft) mutable.value = ""
        if (texts[sourceId] == submittedDraft) texts.remove(sourceId)
        select(publishedId)
    }
    private fun rememberCurrent() {
        if (mutable.value.isEmpty()) texts.remove(selected) else texts[selected] = mutable.value
    }
}