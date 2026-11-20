package org.hermesmobile.app.local

import android.app.Application
import android.content.ContentResolver
import android.provider.OpenableColumns
import android.net.Uri
import android.os.CancellationSignal
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import java.util.UUID
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
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
    internal val temporary = TemporaryPhoneChat()

    internal fun openTemporary() {
        if (controller.state.value.busy) return
        controller.dismissError()
        temporary.open()
    }

    internal fun closeTemporary() {
        if (temporary.close()) controller.cancel()
        controller.dismissError()
    }

    internal fun leavePhone() {
        if (temporary.state.value != null) closeTemporary()
        controller.leave()
    }

    internal val writing = app.phoneWriting
    internal val writingOpen = MutableStateFlow(false)
    internal val writingStorageError = MutableStateFlow(false)
    internal val writingSaving = MutableStateFlow(false)
    internal val writingLibrary = app.preferences.phoneWritingLibrary
        .catch { writingStorageError.value = true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PhoneWritingLibrary())

    internal fun openWriting() {
        if (controller.state.value.busy) return
        controller.dismissError()
        writingOpen.value = true
    }

    internal fun receiveWriting(token: String?, invalid: Boolean) {
        if (token == null && !invalid) return
        if (temporary.state.value != null) closeTemporary()
        writing.receive(token?.let(app.writingIntake::take))
        writingOpen.value = true
    }

    internal fun closeWriting() {
        if (controller.state.value.busy) return
        writing.cancelReview()
        writingOpen.value = false
    }

    internal fun sendWriting(review: PhoneWritingReview) {
        val state = controller.state.value
        if (!state.busy && writing.consume(review, state.loaded)) {
            controller.generateWriting(review, writing::publish)
        }
    }

    internal fun saveWritingTemplate() {
        val draft = writing.draft.value
        if (!validTitle(draft.title) || !validInstructions(draft.instructions)) return
        val template = PhoneWritingTemplate(draft.template?.id ?: UUID.randomUUID().toString(),
            draft.title.trim(), draft.instructions)
        changeWritingLibrary({ it.save(template, draft.template) }) { writing.templateSaved(template, draft) }
    }

    internal fun saveWritingNote(note: PhoneWritingNote) = changeWritingLibrary({ it.save(note) })

    internal fun removeWritingNote(note: PhoneWritingNote) = changeWritingLibrary({ library ->
        library.copy(notes = library.notes - note)
    })

    internal fun removeWritingTemplate(template: PhoneWritingTemplate) = changeWritingLibrary({ library ->
        library.copy(templates = library.templates - template)
    }) {
        val draft = writing.draft.value
        if (draft.template == template) writing.choosePreset(draft.instructions)
    }

    private fun changeWritingLibrary(
        update: (PhoneWritingLibrary) -> PhoneWritingLibrary,
        saved: () -> Unit = {}
    ) {
        if (writingSaving.value) return
        writingStorageError.value = false
        writingSaving.value = true
        viewModelScope.launch {
            try {
                app.preferences.editPhoneWritingLibrary(update)
                saved()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                writingStorageError.value = true
            } finally {
                writingSaving.value = false
            }
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
    private data class DocumentSelection(val uri: Uri, val previous: PhoneDocument?)
    private var pickingDocument = false
    private var documentTarget: PhoneDocument? = null
    private var documentSelection: DocumentSelection? = null
    internal val pendingDocument = MutableStateFlow(false)

    internal fun pickDocument(previous: PhoneDocument?) {
        pickingDocument = true
        documentTarget = previous
    }

    internal fun selectedDocument(uri: Uri?) {
        documentSelection = uri?.takeIf { pickingDocument && it.scheme == ContentResolver.SCHEME_CONTENT }
            ?.let { DocumentSelection(it, documentTarget) }
        pendingDocument.value = documentSelection != null
        pickingDocument = false
        documentTarget = null
    }

    internal fun cancelDocumentImport() {
        documentSelection = null
        pendingDocument.value = false
    }

    internal fun indexSelectedDocument() {
        val document = documentSelection ?: return
        if (controller.state.value.busy) return
        cancelDocumentImport()
        val resolver = app.contentResolver
        val signal = CancellationSignal()
        controller.indexDocument(document.previous, name = {
            resolver.query(document.uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null, signal)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: throw LocalModelStorageException("document_unavailable")
        }, cancelOpen = signal::cancel, extractPdf = { input, check ->
            PhonePdfReader.extract(app, input, check)
        }) {
            resolver.openAssetFileDescriptor(document.uri, "r", signal)?.createInputStream()
                ?: throw LocalModelStorageException("document_unavailable")
        }
    }

    override fun onCleared() {
        markdownExport.clear()
        historyBackup.clear()
        leavePhone()
    }
}
