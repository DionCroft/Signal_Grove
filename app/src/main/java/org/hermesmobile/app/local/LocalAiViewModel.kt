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
    fun openChat(id: String) { controller.openChat(id) { drafts.select(controller.state.value.chat?.id); handoffState.value = "" } }
    fun deleteChat(id: String) { controller.deleteChat(id) { drafts.forget(id); drafts.select(controller.state.value.chat?.id) } }
    fun branch(source: LocalConversation, index: Int, kind: LocalBranchKind, edited: String?) {
        controller.branch(source, index, kind, edited) { child -> drafts.select(child.id); handoffState.value = "" }
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
    val pendingImport = MutableStateFlow<Uri?>(null)
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
        val uri = pendingImport.value ?: return
        pendingImport.value = null
        val signal = CancellationSignal()
        controller.importModel(true, signal::cancel) {
            getApplication<Application>().contentResolver.openAssetFileDescriptor(uri, "r", signal)?.createInputStream()
                ?: throw LocalModelStorageException("import_unavailable")
        }
    }
    override fun onCleared() {
        markdownExport.clear()
        controller.leave()
    }
}
