package org.hermesmobile.app.local

import android.content.ContentResolver
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import androidx.annotation.MainThread
import androidx.lifecycle.SavedStateHandle
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.hermesmobile.app.updates.UpdateWorkGuard

internal enum class MarkdownSaveResult { SAVED, CANCELLED, PICKER_UNAVAILABLE, STOPPED, UNCERTAIN, INTERRUPTED }

internal sealed interface PhoneMarkdownExportState {
    data object Idle : PhoneMarkdownExportState
    data class Review(val document: LocalMarkdownExport, val displayTitle: String) : PhoneMarkdownExportState
    data object ChoosingDestination : PhoneMarkdownExportState
    data object Writing : PhoneMarkdownExportState
    data class Finished(val result: MarkdownSaveResult) : PhoneMarkdownExportState
}

/** One reviewed snapshot and one destination result in an activity-scoped ViewModel. */
@MainThread
internal class PhoneMarkdownExport(
    private val resolver: ContentResolver,
    private val guard: UpdateWorkGuard,
    private val savedState: SavedStateHandle,
    private val scope: CoroutineScope
) {
    private val mutable = MutableStateFlow<PhoneMarkdownExportState>(
        if (savedState.get<Boolean>(IN_FLIGHT) == true) PhoneMarkdownExportState.Finished(MarkdownSaveResult.INTERRUPTED)
        else PhoneMarkdownExportState.Idle
    )
    val state = mutable.asStateFlow()
    private var pending: LocalMarkdownExport? = null
    private var write: DocumentWrite? = null

    fun review(document: LocalMarkdownExport, displayTitle: String) {
        if (mutable.value == PhoneMarkdownExportState.Idle) {
            mutable.value = PhoneMarkdownExportState.Review(document, displayTitle)
        }
    }

    fun dismiss() {
        if (mutable.value is PhoneMarkdownExportState.Review || mutable.value is PhoneMarkdownExportState.Finished) {
            savedState.remove<Boolean>(IN_FLIGHT)
            mutable.value = PhoneMarkdownExportState.Idle
        }
    }

    fun choose(document: LocalMarkdownExport, launch: (String) -> Unit) {
        val reviewed = mutable.value as? PhoneMarkdownExportState.Review ?: return
        if (reviewed.document !== document) return
        pending = document
        savedState[IN_FLIGHT] = true
        guard.set(this, true)
        mutable.value = PhoneMarkdownExportState.ChoosingDestination
        try {
            launch(document.suggestedFilename)
        } catch (_: RuntimeException) {
            if (mutable.value == PhoneMarkdownExportState.ChoosingDestination) {
                finish(MarkdownSaveResult.PICKER_UNAVAILABLE)
            }
        }
    }

    fun destination(uri: Uri?) {
        val document = pending ?: return
        if (mutable.value != PhoneMarkdownExportState.ChoosingDestination) return
        pending = null
        if (uri == null) {
            finish(MarkdownSaveResult.CANCELLED)
            return
        }
        val operation = DocumentWrite()
        write = operation
        mutable.value = PhoneMarkdownExportState.Writing
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            // Keep the guard until the single write closes, even when the activity finishes.
            withContext(NonCancellable) {
                try {
                    val result = withContext(Dispatchers.IO) { operation.save(resolver, uri, document) }
                    finish(result)
                } finally {
                    write = null
                    guard.set(this@PhoneMarkdownExport, false)
                }
            }
        }
    }

    fun stopWrite() { write?.stop() }

    fun clear() {
        pending = null
        if (write == null) guard.set(this, false) else stopWrite()
    }

    private fun finish(result: MarkdownSaveResult) {
        pending = null
        savedState.remove<Boolean>(IN_FLIGHT)
        mutable.value = PhoneMarkdownExportState.Finished(result)
        guard.set(this, false)
    }

    private class DocumentWrite {
        private val stopped = AtomicBoolean()
        private val signal = CancellationSignal()

        fun stop() {
            if (stopped.compareAndSet(false, true)) runCatching { signal.cancel() }
        }

        fun save(resolver: ContentResolver, uri: Uri, document: LocalMarkdownExport): MarkdownSaveResult {
            if (uri.scheme != ContentResolver.SCHEME_CONTENT) return MarkdownSaveResult.UNCERTAIN
            fun checkpoint() {
                if (stopped.get()) throw CancellationException("phone_markdown_write_stopped")
            }
            return try {
                checkpoint()
                val descriptor = resolver.openFileDescriptor(uri, "wt", signal) ?: throw IOException("no_document")
                ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { output ->
                    document.writeTo(output, ::checkpoint)
                    output.flush()
                }
                MarkdownSaveResult.SAVED
            } catch (_: Exception) {
                if (stopped.get()) MarkdownSaveResult.STOPPED else MarkdownSaveResult.UNCERTAIN
            }
        }
    }

    private companion object { const val IN_FLIGHT = "phone_markdown_export_in_flight" }
}
