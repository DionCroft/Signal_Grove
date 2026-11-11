package org.hermesmobile.app.local

import android.app.Application
import android.net.Uri
import android.os.CancellationSignal
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.hermesmobile.app.HermesApplication

/** Drafts and picker results survive rotation in memory, never Android's saved-state bundle. */
class LocalAiViewModel(application: Application) : AndroidViewModel(application) {
    val controller = (application as HermesApplication).localAi
    val draft = MutableStateFlow("")
    val pendingImport = MutableStateFlow<Uri?>(null)
    fun send() {
        val text = draft.value.trim()
        val state = controller.state.value
        if (state.busy || state.loaded == null || text.isBlank() || text.toByteArray().size > 8192 ||
            state.chat?.let { it.modelSha256 != state.loaded.sha256 || it.exchanges.size >= 30 } == true) return
        controller.send(text); draft.value = ""
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
