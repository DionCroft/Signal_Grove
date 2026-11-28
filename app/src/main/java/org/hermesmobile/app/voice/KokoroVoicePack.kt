package org.hermesmobile.app.voice

import android.content.Context
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.hermesmobile.app.HermesApplication
import org.hermesmobile.app.local.AndroidModelDownloadNetwork

/** The publisher's hash is checked before any archive entries or native model files are opened. */
internal class KokoroVoicePack private constructor(
    private val directory: File,
    private val route: AndroidModelDownloadNetwork
) {
    constructor(context: Context) : this(
        File(context.noBackupFilesDir, "kokoro-voice"),
        (context.applicationContext as HermesApplication).phoneDownloadNetwork
    )

    private val installed = File(directory, HASH)
    private val staging = File(directory, "installing")

    suspend fun isInstalled(): Boolean = withContext(Dispatchers.IO) { ready() }
    suspend fun remove() = exclusive {
        delete(installed)
        delete(staging)
    }

    suspend fun play(block: (File) -> Unit) = exclusive {
        check(ready())
        block(installed)
    }

    suspend fun install(progress: (Long) -> Unit) = exclusive {
        if (ready()) return@exclusive
        check(directory.isDirectory || directory.mkdirs())
        delete(staging)
        check(directory.usableSpace > DOWNLOAD_BYTES + PACK_FILES.values.sum() + 16_777_216) { "Insufficient voice pack storage" }
        check(staging.mkdir())
        try {
            val archive = File(staging, "pack.tar.bz2")
            download(archive, progress)
            val target = File(staging, "verified")
            check(target.mkdir())
            val operation = currentCoroutineContext()
            extract(archive, target, PACK_FILES, operation::ensureActive)
            File(target, "verified-sha256").outputStream().use {
                it.write(HASH.toByteArray(Charsets.US_ASCII))
                it.fd.sync()
            }
            operation.ensureActive()
            delete(installed)
            Files.move(target.toPath(), installed.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } finally {
            delete(staging)
        }
    }

    private suspend fun <T> exclusive(action: suspend () -> T): T =
        withContext(Dispatchers.IO) { lock.withLock { action() } }

    private fun ready(): Boolean = runCatching {
        val proof = File(installed, "verified-sha256")
        proof.length() == HASH.length.toLong() && proof.readText(Charsets.US_ASCII) == HASH &&
            PACK_FILES.all { (name, bytes) -> File(installed, name).let { it.isFile && it.length() == bytes } }
    }.getOrDefault(false)

    private fun delete(file: File) {
        if (!file.deleteRecursively()) throw IOException("Could not remove voice pack files")
    }

    private suspend fun download(target: File, progress: (Long) -> Unit) = coroutineScope {
        val network = route.prepare(client)
        val request = Request.Builder().url(URL).header("Accept-Encoding", "identity").build()
        val call = network.client.newCall(request)
        network.watch(call::cancel).use {
            val operation = currentCoroutineContext()
            val watcher = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
                try { awaitCancellation() } finally { call.cancel() }
            }
            try {
                network.check()
                call.execute().use { response ->
                    val body = response.body
                    if (response.code != 200 || response.header("Content-Encoding")?.equals("identity", ignoreCase = true) == false ||
                        (body.contentLength() >= 0L && body.contentLength() != DOWNLOAD_BYTES)) {
                        throw IOException("Invalid Kokoro package response")
                    }
                    verifyToFile(body.byteStream(), target, DOWNLOAD_BYTES, HASH) {
                        operation.ensureActive()
                        network.check()
                        progress(it)
                    }
                }
            } catch (failure: IOException) {
                operation.ensureActive()
                network.check()
                throw failure
            } finally {
                watcher.cancel()
            }
        }
    }

    companion object {
        const val DOWNLOAD_BYTES = 147_052_523L
        const val HASH = "259c9c8bc906d6078e4e0570421885dabb68b539c2f970fcbb890a58eeaf3e9c"
        const val ARCHIVE_ROOT = "kokoro-int8-multi-lang-v1_1/"
        private const val URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-multi-lang-v1_1.tar.bz2"
        private val lock = Mutex()
        private val client = OkHttpClient.Builder()
            .followSslRedirects(false).retryOnConnectionFailure(false)
            .callTimeout(12, TimeUnit.MINUTES).readTimeout(50, TimeUnit.SECONDS)
            .addNetworkInterceptor { chain ->
                val url = chain.request().url
                if (!url.isHttps || url.host !in setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")) {
                    throw IOException("Unexpected voice pack publisher")
                }
                chain.proceed(chain.request())
            }.build()

        internal val PACK_FILES = mapOf(
            "LICENSE" to 11_358L,
            "model.int8.onnx" to 114_203_914L,
            "voices.bin" to 53_790_720L,
            "tokens.txt" to 1_111L,
            "lexicon-us-en.txt" to 5_956_885L,
            "lexicon-zh.txt" to 2_120_131L,
            "date-zh.fst" to 59_154L,
            "number-zh.fst" to 64_482L,
            "phone-zh.fst" to 88_630L,
            "dict/idf.utf8" to 5_998_717L,
            "dict/user.dict.utf8" to 829_194L,
            "dict/pos_dict/char_state_tab.utf8" to 327_139L,
            "dict/pos_dict/prob_start.utf8" to 4_347L,
            "dict/pos_dict/prob_emit.utf8" to 1_687_686L,
            "dict/pos_dict/prob_trans.utf8" to 124_159L,
            "dict/stop_words.utf8" to 8_974L,
            "dict/hmm_model.utf8" to 519_739L,
            "dict/jieba.dict.utf8" to 5_071_204L
        )

        internal fun verifyToFile(input: InputStream, file: File, bytes: Long, hash: String, progress: (Long) -> Unit) {
            val digest = MessageDigest.getInstance("SHA-256")
            file.outputStream().use { output ->
                copyExact(input, bytes) { data, size, written ->
                    output.write(data, 0, size)
                    digest.update(data, 0, size)
                    progress(written)
                }
            }
            if (digest.digest().joinToString("") { "%02x".format(it) } != hash) throw IOException("Voice pack checksum mismatch")
        }

        internal fun extract(archive: File, target: File, expected: Map<String, Long>, checkpoint: () -> Unit) {
            val seen = mutableSetOf<String>()
            TarArchiveInputStream(BZip2CompressorInputStream(archive.inputStream().buffered())).use { stream ->
                while (true) {
                    checkpoint()
                    val entry = stream.nextEntry ?: break
                    val name = entry.name.removePrefix(ARCHIVE_ROOT)
                    if (name !in expected) continue
                    val size = expected.getValue(name)
                    if (name == entry.name || !entry.isFile || entry.isSparse || entry.size != size || !seen.add(name)) {
                        throw IOException("Unexpected voice pack entry")
                    }
                    val file = File(target, name)
                    check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
                    file.outputStream().use { output ->
                        copyExact(stream, size) { data, count, _ ->
                            checkpoint()
                            output.write(data, 0, count)
                        }
                        output.fd.sync()
                    }
                }
            }
            if (seen != expected.keys) throw IOException("Incomplete voice pack")
        }

        private fun copyExact(input: InputStream, expected: Long, write: (ByteArray, Int, Long) -> Unit) {
            val data = ByteArray(64 * 1024)
            var written = 0L
            while (true) {
                val size = input.read(data)
                if (size < 0) break
                written += size
                if (written > expected) throw IOException("Voice pack file exceeds its published size")
                write(data, size, written)
            }
            if (written != expected) throw IOException("Voice pack file is truncated")
        }
    }
}
