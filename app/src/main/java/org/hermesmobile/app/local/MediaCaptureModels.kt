package org.hermesmobile.app.local

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream

internal enum class MediaCaptureKind(val fileName: String, val bytes: Long, val sha256: String, val download: String) {
    SCREENSHOT("eng.traineddata", 4_113_088,
        "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2",
        "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/65727574dfcd264acbb0c3e07860e4e9e9b22185/eng.traineddata"),
    RECORDING("vosk-model-small-en-us-0.15.zip", 41_205_931,
        "30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498",
        "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip")
}

internal class MediaCaptureModels(root: File) {
    private val directory = root.canonicalFile
    private val expectedSpeechFiles = setOf(
        "am/final.mdl", "graph/disambig_tid.int", "graph/HCLr.fst", "graph/Gr.fst", "graph/phones/word_boundary.int",
        "conf/model.conf", "conf/mfcc.conf", "ivector/splice.conf", "ivector/final.dubm", "ivector/global_cmvn.stats",
        "ivector/final.ie", "ivector/online_cmvn.conf", "ivector/final.mat", "README"
    )

    fun installed(kind: MediaCaptureKind): File? {
        val model = File(directory, kind.sha256)
        val proof = File(model, "verified-sha256")
        return model.takeIf { proof.isFile && runCatching { proof.readText() == kind.sha256 }.getOrDefault(false) }
    }

    /** Accepts the exact reviewed language pack. It never extracts an unverified archive. */
    @Synchronized fun install(kind: MediaCaptureKind, input: InputStream, check: () -> Unit): File {
        installed(kind)?.let { return it }
        check(directory.isDirectory || directory.mkdirs())
        val stage = File(directory, "media-stage-" + UUID.randomUUID())
        check(stage.mkdir())
        try {
            val download = File(stage, "download")
            val digest = MessageDigest.getInstance("SHA-256")
            val count = download.outputStream().use { output ->
                boundedCopy(input, kind.bytes, check) { block, size ->
                    output.write(block, 0, size)
                    digest.update(block, 0, size)
                }.also { output.fd.sync() }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (count != kind.bytes || actual != kind.sha256) {
                throw LocalModelStorageException("media_model_checksum")
            }

            val ready = File(stage, "ready")
            check(ready.mkdir())
            when (kind) {
                MediaCaptureKind.SCREENSHOT -> {
                    val tessdata = File(ready, "tessdata")
                    check(tessdata.mkdir())
                    Files.move(download.toPath(), File(tessdata, kind.fileName).toPath(), StandardCopyOption.ATOMIC_MOVE)
                }
                MediaCaptureKind.RECORDING -> extractSpeechModel(download, ready, check)
            }
            File(ready, "verified-sha256").outputStream().use { output ->
                output.write(kind.sha256.toByteArray(Charsets.US_ASCII))
                output.fd.sync()
            }
            check()
            val target = File(directory, kind.sha256)
            Files.move(ready.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            return target
        } finally {
            stage.deleteRecursively()
        }
    }

    private fun extractSpeechModel(archive: File, target: File, check: () -> Unit) {
        val seen = mutableSetOf<String>()
        var remaining = 72_000_000L
        ZipInputStream(archive.inputStream().buffered()).use { stream ->
            while (true) {
                check()
                val entry = stream.nextEntry ?: break
                if (!entry.isDirectory) {
                    val name = entry.name.removePrefix("vosk-model-small-en-us-0.15/")
                    if (name !in expectedSpeechFiles || !seen.add(name)) {
                        throw LocalModelStorageException("media_model_checksum")
                    }
                    val file = File(target, name)
                    val parent = requireNotNull(file.parentFile)
                    check(parent.isDirectory || parent.mkdirs())
                    val size = file.outputStream().use { output ->
                        boundedCopy(stream, remaining, check) { data, count -> output.write(data, 0, count) }
                            .also { output.fd.sync() }
                    }
                    remaining -= size
                }
                stream.closeEntry()
            }
        }
        if (seen != expectedSpeechFiles) throw LocalModelStorageException("media_model_checksum")
    }

    private fun boundedCopy(
        input: InputStream, limit: Long, check: () -> Unit, write: (ByteArray, Int) -> Unit
    ): Long {
        var written = 0L
        val block = ByteArray(64 * 1024)
        while (true) {
            check()
            val count = input.read(block)
            if (count < 0) return written
            if (count == 0) continue
            if (count > limit - written) throw LocalModelStorageException("media_model_checksum")
            write(block, count)
            written += count
        }
    }
}
