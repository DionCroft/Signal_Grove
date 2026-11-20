package org.hermesmobile.app.local

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.RemoteException
import androidx.annotation.RequiresApi
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal object PhonePdfReader {
    private const val MAX_BYTES = 8 * 1024 * 1024
    private const val EXTRACT = 1
    private const val RESULT = 2
    private const val SOURCE = "source"
    private const val PAGES = "pages"
    private const val ERROR = "error"
    private val errors = setOf("document_pdf_password", "document_pdf_page_limit",
        "document_pdf_invalid", "document_pdf_image_only", "document_pdf_size", "document_size")

    suspend fun extract(context: Context, input: InputStream, check: () -> Unit): List<String> {
        if (Build.VERSION.SDK_INT < 35) throw LocalModelStorageException("document_pdf_unsupported")
        return seekableCopy(context, input, check).use { source ->
            withContext(Dispatchers.Main.immediate) { request(context, source) }
        }
    }

    private fun seekableCopy(context: Context, input: InputStream, check: () -> Unit): ParcelFileDescriptor {
        val temporary = File.createTempFile("phone-pdf-", ".pending", context.cacheDir)
        try {
            val source = ParcelFileDescriptor.open(temporary, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(
                    ParcelFileDescriptor.open(temporary, ParcelFileDescriptor.MODE_READ_WRITE)
                ).use { output ->
                    // Remove the directory entry before the first byte is written.
                    if (!temporary.delete()) throw IOException("Could not remove PDF staging entry")
                    val buffer = ByteArray(32 * 1024)
                    var total = 0
                    while (true) {
                        check()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_BYTES) throw LocalModelStorageException("document_pdf_size")
                        output.write(buffer, 0, count)
                    }
                }
                check()
                return source
            } catch (failure: Throwable) {
                source.close()
                throw failure
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private suspend fun request(context: Context, source: ParcelFileDescriptor): List<String> {
        val result = CompletableDeferred<Bundle>()
        fun unavailable() {
            result.completeExceptionally(LocalModelStorageException("document_pdf_unavailable"))
        }
        val receiver = Messenger(Handler(Looper.getMainLooper()) { message ->
            if (message.what == RESULT) result.complete(message.data)
            true
        })
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, remote: IBinder) {
                if (result.isCompleted) return
                try {
                    Messenger(remote).send(Message.obtain(null, EXTRACT).apply {
                        replyTo = receiver
                        data = Bundle().apply { putParcelable(SOURCE, source) }
                    })
                } catch (_: RemoteException) {
                    unavailable()
                }
            }

            override fun onServiceDisconnected(name: ComponentName) = unavailable()
            override fun onNullBinding(name: ComponentName) = unavailable()
            override fun onBindingDied(name: ComponentName) = unavailable()
        }
        if (!context.bindService(Intent(context, PhonePdfService::class.java), connection, Context.BIND_AUTO_CREATE)) {
            context.unbindService(connection)
            throw LocalModelStorageException("document_pdf_unavailable")
        }
        try {
            val reply = withTimeoutOrNull(20_000) { result.await() }
                ?: throw LocalModelStorageException("document_pdf_timeout")
            reply.getString(ERROR)?.let {
                throw LocalModelStorageException(it.takeIf(errors::contains) ?: "document_pdf_invalid")
            }
            val pages = reply.getStringArrayList(PAGES)
                ?: throw LocalModelStorageException("document_pdf_invalid")
            if (pages.size !in 1..PhoneDocumentLibrary.MAX_PDF_PAGES || pages.sumOf { it.toByteArray().size } > PhoneDocumentLibrary.MAX_DOCUMENT_BYTES) {
                throw LocalModelStorageException("document_pdf_invalid")
            }
            return pages.toList()
        } finally {
            result.cancel()
            context.unbindService(connection)
        }
    }

    @RequiresApi(35)
    fun reply(message: Message): Boolean {
        if (message.what != EXTRACT) return false
        val reply = message.replyTo
        val fd = message.data.getParcelable(SOURCE, ParcelFileDescriptor::class.java)
        val data = Bundle()
        try {
            val pages = fd?.use(::readPages) ?: throw LocalModelStorageException("document_pdf_invalid")
            data.putStringArrayList(PAGES, ArrayList(pages))
        } catch (failure: LocalModelStorageException) {
            data.putString(ERROR, failure.reason)
        } catch (_: SecurityException) {
            data.putString(ERROR, "document_pdf_password")
        } catch (_: IOException) {
            data.putString(ERROR, "document_pdf_invalid")
        } catch (_: IllegalArgumentException) {
            data.putString(ERROR, "document_pdf_invalid")
        }
        try {
            reply?.send(Message.obtain(null, RESULT).apply { this.data = data })
        } catch (_: RemoteException) {
            // The importing screen may have been closed before this request finished.
        }
        return true
    }

    @RequiresApi(35)
    private fun readPages(fd: ParcelFileDescriptor): List<String> {
        if (fd.statSize !in 1L..MAX_BYTES.toLong()) throw LocalModelStorageException("document_pdf_invalid")
        return PdfRenderer(fd).use { renderer ->
            if (renderer.pageCount !in 1..PhoneDocumentLibrary.MAX_PDF_PAGES) throw LocalModelStorageException("document_pdf_page_limit")
            var bytes = 0
            val pages = List(renderer.pageCount) { page ->
                renderer.openPage(page).use { current ->
                    val text = StringBuilder()
                    for (item in current.textContents) {
                        val part = item.text
                        if (part.length > PhoneDocumentLibrary.MAX_DOCUMENT_BYTES - bytes) {
                            throw LocalModelStorageException("document_size")
                        }
                        val separator = if (text.isNotEmpty()) 1 else 0
                        bytes += separator + part.toByteArray().size
                        if (bytes > PhoneDocumentLibrary.MAX_DOCUMENT_BYTES) throw LocalModelStorageException("document_size")
                        if (separator != 0) text.append('\n')
                        text.append(part)
                    }
                    text.toString()
                }
            }
            if (pages.all(String::isBlank)) throw LocalModelStorageException("document_pdf_image_only")
            pages
        }
    }
}

class PhonePdfService : Service() {
    private val worker = HandlerThread("phone-pdf")

    override fun onCreate() {
        super.onCreate()
        worker.start()
    }

    override fun onBind(intent: Intent): IBinder? {
        if (Build.VERSION.SDK_INT < 35 || !Process.isIsolated()) return null
        return extractorBinder()
    }

    @RequiresApi(35)
    private fun extractorBinder(): IBinder = Messenger(Handler(worker.looper) { PhonePdfReader.reply(it) }).binder

    override fun onDestroy() {
        worker.quitSafely()
        super.onDestroy()
    }
}
