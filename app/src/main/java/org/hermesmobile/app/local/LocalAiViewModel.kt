package org.hermesmobile.app.local

import android.app.Application
import android.net.Uri
import android.os.CancellationSignal
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import org.hermesmobile.app.HermesApplication

/** Drafts and picker results survive rotation in memory, never Android's saved-state bundle. */
class LocalAiViewModel(application: Application) : AndroidViewModel(application) {
    val controller = (application as HermesApplication).localAi
    val draft = MutableStateFlow("")
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
                if(draft.value.isNotEmpty() || !controller.startDraft()) {handoffState.value = "unavailable";return@launch}
                draft.value = text; handoffState.value = "received"
            } catch(e:TimeoutCancellationException) {handoffState.value = "unavailable"}
        }
    }
    val pendingImport = MutableStateFlow<Uri?>(null)
    fun send() {
        val text = draft.value.trim()
        val state = controller.state.value
        if (state.busy || state.loaded == null || text.isBlank() || text.toByteArray().size > 8192 ||
            state.chat?.let { it.modelSha256 != state.loaded.sha256 || it.exchanges.size >= 30 } == true) return
        controller.send(text); draft.value = ""; handoffState.value = ""
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
    override fun onCleared() { controller.leave() }
}
