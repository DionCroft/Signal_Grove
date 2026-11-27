package org.hermesmobile.app.local

import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

/** Fetch only the published pack. The same installer verifies downloaded and imported bytes. */
internal class MediaPackDownload(
    private val models: MediaCaptureModels,
    private val prepareRoute: suspend (OkHttpClient) -> ModelDownloadRoute,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
) {
    suspend fun install(kind: MediaCaptureKind): File {
        models.installed(kind)?.let { return it }
        val route = prepareRoute(client)
        val request = Request.Builder().url(kind.download)
            .header("Accept-Encoding", "identity").build()
        val call = route.client.newCall(request)
        return route.watch(call::cancel).use {
            coroutineScope {
                val operation = currentCoroutineContext()
                val check = { operation.ensureActive(); route.check() }
                // Cancel blocking OkHttp reads as soon as the foreground transfer is cancelled.
                val watcher = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
                    try { awaitCancellation() } finally { call.cancel() }
                }
                try {
                    check()
                    call.execute().use { response ->
                        if (response.code != 200) throw LocalModelStorageException("media_download_http")
                        if (response.header("Content-Encoding")?.equals("identity", ignoreCase = true) == false) {
                            throw LocalModelStorageException("media_model_checksum")
                        }
                        val body = response.body ?: throw LocalModelStorageException("media_model_checksum")
                        val length = body.contentLength()
                        if (length >= 0 && length != kind.bytes) {
                            throw LocalModelStorageException("media_model_checksum")
                        }
                        models.install(kind, body.byteStream(), check)
                    }
                } catch (_: IOException) {
                    check()
                    throw LocalModelStorageException("media_download_http")
                } finally {
                    watcher.cancel()
                }
            }
        }
    }
}
