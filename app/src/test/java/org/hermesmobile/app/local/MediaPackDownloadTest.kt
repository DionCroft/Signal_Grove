package org.hermesmobile.app.local

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MediaPackDownloadTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun publisherFailureAndWrongLengthLeaveNoPack() = runBlocking {
        val kind = MediaCaptureKind.RECORDING
        val root = temp.newFolder()
        val models = MediaCaptureModels(root)
        for ((status, reason) in listOf(302 to "media_download_http", 200 to "media_model_checksum")) {
            val transport = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(status).message("Publisher fixture").body(byteArrayOf(1, 2).toResponseBody()).build()
            }.build()
            val error = runCatching { MediaPackDownload(models, { ModelDownloadRoute(it) }, transport).install(kind) }
                .exceptionOrNull()
            assertEquals(reason, (error as? LocalModelStorageException)?.reason)
            assertNull(models.installed(kind))
            assertTrue(root.list().orEmpty().isEmpty())
        }
    }

    @Test fun closingTheTransferCancelsTheActivePublisherCall() = runBlocking {
        val started = CompletableDeferred<Call>()
        val root = temp.newFolder()
        val models = MediaCaptureModels(root)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val call = chain.call()
            started.complete(call)
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while (!call.isCanceled() && System.nanoTime() < deadline) Thread.sleep(5)
            throw IOException("Publisher fixture stopped")
        }.build()
        val transfer = launch(Dispatchers.IO) {
            MediaPackDownload(models, { ModelDownloadRoute(it) }, client).install(MediaCaptureKind.RECORDING)
        }
        val call = withTimeout(5_000) { started.await() }
        transfer.cancelAndJoin()
        assertTrue(call.isCanceled())
        assertNull(models.installed(MediaCaptureKind.RECORDING))
        assertTrue(root.list().orEmpty().isEmpty())
    }
}
