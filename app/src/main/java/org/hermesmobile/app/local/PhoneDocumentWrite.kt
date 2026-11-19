package org.hermesmobile.app.local

import android.content.ContentResolver
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException

internal enum class DocumentWriteResult { SAVED, STOPPED, UNCERTAIN }

internal class PhoneDocumentWrite {
    private val stopped = AtomicBoolean()
    private val signal = CancellationSignal()

    fun stop() {
        markStopped()
        cancelProvider()
    }

    fun markStopped() { stopped.set(true) }

    fun cancelProvider() { runCatching { signal.cancel() } }

    fun save(resolver: ContentResolver, uri: Uri, writeTo: (OutputStream, () -> Unit) -> Unit): DocumentWriteResult {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return DocumentWriteResult.UNCERTAIN
        fun checkpoint() {
            if (stopped.get()) throw CancellationException("phone_document_write_stopped")
        }
        return try {
            checkpoint()
            val descriptor = resolver.openFileDescriptor(uri, "wt", signal) ?: throw IOException("no_document")
            ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { output ->
                writeTo(output, ::checkpoint)
                output.flush()
            }
            DocumentWriteResult.SAVED
        } catch (_: Exception) {
            if (stopped.get()) DocumentWriteResult.STOPPED else DocumentWriteResult.UNCERTAIN
        }
    }
}
