package org.hermesmobile.app.local

import android.app.Application
import android.net.Uri
import android.os.CancellationSignal
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import org.hermesmobile.app.HermesApplication

/** Drafts and picker results survive rotation in memory, never Android's saved-state bundle. */
class LocalAiViewModel(application: Application, savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val app = application as HermesApplication
    val controller = app.localAi
    internal val markdownExport = PhoneMarkdownExport(app.contentResolver, app.updateWorkGuard, savedState, viewModelScope)

    internal val historyBackup = PhoneHistoryBackup(app.contentResolver, controller, app.updateWorkGuard, savedState, viewModelScope)

    internal fun prepareMarkdownExport(summary: LocalConversationSummary) {
        if (markdownExport.state.value != PhoneMarkdownExportState.Idle) return
        controller.prepareMarkdownExport(summary) { document ->
            markdownExport.review(document, summary.title)
        }
    }
    private val drafts = LocalConversationDrafts(controller.state.value.chat?.id)
    val draft = drafts.text
    fun changeDraft(value: String) { drafts.edit(value) }
    fun newChat() { controller.newChat(); drafts.select(controller.state.value.chat?.id); handoffState.value = "" }
    fun newChatWithDraft() {
        val state = controller.state.value
        val text = draft.value
        if (state.busy || state.loaded == null || state.chat?.hasSavedContent != true ||
            state.history.size >= LocalConversationStore.MAX_CHATS || text.isBlank()) return
        controller.newChat()
        val fresh = controller.state.value.chat ?: return
        if (fresh.id == state.chat?.id) return
        drafts.select(fresh.id)
        drafts.edit(text)
        handoffState.value = ""
    }
    fun openChat(id: String) { controller.openChat(id) { drafts.select(controller.state.value.chat?.id); handoffState.value = "" } }
    fun deleteChat(id: String) { controller.deleteChat(id) { drafts.forget(id); drafts.select(controller.state.value.chat?.id) } }
    fun branch(source: LocalConversation, index: Int, kind: LocalBranchKind, edited: String?) {
        controller.branch(source, index, kind, edited) { child -> drafts.select(child.id); handoffState.value = "" }
    }
    fun continueWithSelectedTurns(source: LocalConversation, indices: List<Int>, reviewedDraft: String, copyDraft: Boolean) {
        if (copyDraft && draft.value != reviewedDraft) return
        controller.continueWithSelectedTurns(source, indices) { child ->
            selectContinuation(child, reviewedDraft, copyDraft)
        }
    }

    fun continueWithReviewedSummary(source: LocalConversation, indices: List<Int>, text: String,
        reviewedDraft: String, copyDraft: Boolean) {
        if (copyDraft && draft.value != reviewedDraft) return
        controller.continueWithReviewedSummary(source, indices, text) { child ->
            selectContinuation(child, reviewedDraft, copyDraft)
        }
    }

    private fun selectContinuation(child: LocalConversation, reviewedDraft: String, copyDraft: Boolean) {
        drafts.select(child.id)
        if (copyDraft) drafts.edit(reviewedDraft)
        handoffState.value = ""
    }

    val handoffState = MutableStateFlow("")
    private var receivedToken: String? = null
    fun receiveDraft(token: String?) {
        if(token == null || receivedToken == token) return
        receivedToken = token
        val text = (getApplication<Application>() as HermesApplication).phoneDrafts.take(token)
        if(text == null) {handoffState.value = "unavailable"; return}
        viewModelScope.launch {
            try {
                withTimeout(10_000) {controller.state.first {!it.busy}}
                if(!drafts.canReceiveFresh() || !controller.startDraft()) {handoffState.value = "unavailable";return@launch}
                drafts.select(null); drafts.edit(text); handoffState.value = "received"
            } catch(e:TimeoutCancellationException) {handoffState.value = "unavailable"}
        }
    }
    internal data class ModelSelection(val uri: Uri, val previous: InstalledLocalModel? = null)
    internal val pendingImport = MutableStateFlow<ModelSelection?>(null)
    private var replacementTarget: InstalledLocalModel? = null

    internal fun pickReplacement(previous: InstalledLocalModel) {
        replacementTarget = previous
    }

    internal fun selectedModel(uri: Uri?) {
        pendingImport.value = uri?.let { ModelSelection(it) }
    }

    internal fun selectedReplacement(uri: Uri?) {
        val previous = replacementTarget
        replacementTarget = null
        // A picker result cannot authorize staging if its in-memory target was lost.
        pendingImport.value = if (uri != null && previous != null) ModelSelection(uri, previous) else null
    }
    fun send() {
        val text = draft.value.trim()
        val state = controller.state.value
        if (state.busy || state.loaded == null || text.isBlank() || text.toByteArray().size > 8192 ||
            state.chat?.let { it.modelSha256 != state.loaded.sha256 || it.exchanges.size >= 30 } == true) return
        val sourceId = drafts.selectedId
        val submitted = draft.value
        controller.send(text) { published ->
            drafts.published(sourceId, submitted, published.id); handoffState.value = ""
        }
    }
    fun importSelected() {
        if (controller.state.value.busy) return
        val selection = pendingImport.value ?: return
        pendingImport.value = null
        val signal = CancellationSignal()
        val open = {
            getApplication<Application>().contentResolver.openAssetFileDescriptor(selection.uri, "r", signal)?.createInputStream()
                ?: throw LocalModelStorageException("import_unavailable")
        }
        val previous = selection.previous
        if (previous == null) controller.importModel(true, signal::cancel, open)
        else controller.stageModelReplacement(previous, true, signal::cancel, open)
    }
    override fun onCleared() {
        markdownExport.clear()
        historyBackup.clear()
        controller.leave()
    }
}
