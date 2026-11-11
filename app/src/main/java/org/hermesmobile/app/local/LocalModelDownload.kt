package org.hermesmobile.app.local

import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

internal data class ModelDownloadSpec(val url: HttpUrl, val bytes: Long, val sha256: String) {
    init {
        require(url.isHttps && url.username.isEmpty() && url.password.isEmpty())
        require(bytes in 24..GgufModelReader.MAX_MODEL_BYTES && sha256.matches(Regex("[a-f0-9]{64}")))
    }
}

object RecommendedLocalModel {
    const val NAME = "LFM3-1.7B QAD Q4_0"
    const val SHA256 = "43a7d09a0e9cba91b63eb7052488b9564569bddfd65754d2e426465e572a7452"
    const val BYTES = 1_049_615_680L
    const val REVISION = "cd0c74c575b5c416f3011b5b4c298077f26ca8c2"
    const val MODEL_PAGE = "https://huggingface.co/LiquidAI/LFM3-1.7B-GGUF"
    const val LICENCE = "LFM Open License v1.0"
    internal val spec = ModelDownloadSpec("https://huggingface.co/LiquidAI/LFM3-1.7B-GGUF/resolve/$REVISION/LFM3-1.7B-QAD-Q4_0.gguf".toHttpUrl(), BYTES, SHA256)
}

enum class LocalTransferPhase { DOWNLOADING, VERIFYING, CHECKING, INSTALLING }
data class LocalTransferProgress(val bytes: Long, val total: Long, val phase: LocalTransferPhase)

/** A separate, unauthenticated HTTPS client. Never reuse the Hermes client's headers or cookies. */
internal class LocalModelDownload(private val store: LocalModelStore,
    private val client: OkHttpClient = OkHttpClient.Builder().retryOnConnectionFailure(false).followSslRedirects(false)
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build(),
    private val spec: ModelDownloadSpec = RecommendedLocalModel.spec,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO) {
    private val mutex = Mutex()
    private fun partial() = store.owned("${spec.sha256}.part")

    suspend fun partialBytes(): Long = mutex.withLock { withContext(ioDispatcher) {
        val file = partial()
        if (!file.exists()) 0L else {
            if (!file.isFile || file.length() > spec.bytes) throw LocalModelStorageException("partial_size")
            file.length()
        }
    } }

    suspend fun discardPartial() = mutex.withLock { withContext(ioDispatcher) {
        val file = partial()
        if (file.exists() && !file.delete()) throw LocalModelStorageException("delete_failed")
    } }

    suspend fun download(licenceReviewed: Boolean, progress: (LocalTransferProgress) -> Unit = {}): InstalledLocalModel = mutex.withLock {
        require(licenceReviewed)
        withContext(ioDispatcher) {
            val context = currentCoroutineContext()
            val checkpoint = { context.ensureActive() }
            val existing = store.models(checkpoint).find { it.sha256 == spec.sha256 }
            if (existing != null) { store.verifiedFile(existing.sha256, checkpoint); return@withContext existing }
            val file = partial()
            if (file.exists() && (!file.isFile || file.length() > spec.bytes)) throw LocalModelStorageException("partial_size")
            var count = file.length()
            store.ensureSpace(spec.bytes - count)
            if (count < spec.bytes) {
                val request = Request.Builder().url(spec.url).header("Accept-Encoding", "identity")
                    .apply { if (count > 0) header("Range", "bytes=$count-") }.build()
                val call = client.newCall(request)
                coroutineScope {
                    val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
                        try { awaitCancellation() } finally { call.cancel() }
                    }
                    try {
                        call.execute().use { response ->
                            validateResponse(response.code, response.header("Content-Range"), count, spec.bytes)
                            if (response.header("Content-Encoding")?.let { !it.equals("identity", true) } == true) throw LocalModelStorageException("download_encoding")
                            val body = response.body ?: throw LocalModelStorageException("download_empty")
                            if (body.contentLength() >= 0 && body.contentLength() != spec.bytes - count) throw LocalModelStorageException("download_size")
                            FileOutputStream(file, count > 0).use { output -> body.byteStream().use { input ->
                                val buffer = ByteArray(256 * 1024)
                                while (true) {
                                    checkpoint()
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    if (n == 0) continue
                                    if (n > spec.bytes - count) throw LocalModelStorageException("download_size")
                                    store.ensureSpace(n.toLong()); output.write(buffer, 0, n); count += n
                                    progress(LocalTransferProgress(count, spec.bytes, LocalTransferPhase.DOWNLOADING))
                                }
                                output.fd.sync()
                            } }
                        }
                    } finally { watcher.cancel() }
                }
            }
            checkpoint()
            if (count != spec.bytes) throw LocalModelStorageException("download_incomplete")
            progress(LocalTransferProgress(count, spec.bytes, LocalTransferPhase.VERIFYING))
            if (store.hash(file, checkpoint) != spec.sha256) throw LocalModelStorageException("checksum_mismatch")
            progress(LocalTransferProgress(count, spec.bytes, LocalTransferPhase.CHECKING))
            val metadata = GgufModelReader.read(file, checkpoint)
            progress(LocalTransferProgress(count, spec.bytes, LocalTransferPhase.INSTALLING))
            store.install(file, spec.sha256, metadata, checkpoint)
        }
    }

    companion object {
        internal fun validateResponse(code: Int, range: String?, offset: Long, total: Long) {
            if (code == 200 && offset == 0L) return
            // A resume must not silently truncate the user's partial or mix response ranges.
            if (code != 206) throw LocalModelStorageException(if (code == 200) "download_resume_unsupported" else "download_http")
            val match = range?.takeIf { it.length <= 100 }?.let { Regex("bytes ([0-9]{1,19})-([0-9]{1,19})/([0-9]{1,19})").matchEntire(it) }
            if (match == null || match.groupValues[1].toLongOrNull() != offset || match.groupValues[2].toLongOrNull() != total - 1 || match.groupValues[3].toLongOrNull() != total) throw LocalModelStorageException("download_range")
        }
    }
}
