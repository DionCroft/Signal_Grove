package org.hermesmobile.app.local

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

data class InstalledLocalModel(val sha256: String, val metadata: GgufModelMetadata)
class LocalModelStorageException(val reason: String) : IllegalStateException(reason)

/** Owns model bytes only. No server credentials, conversations or external paths are stored. */
class LocalModelStore(directory: File, private val availableBytes: () -> Long = { directory.usableSpace }) {
    private val directory = directory.canonicalFile
    companion object { const val SPACE_RESERVE = 64L * 1024 * 1024 }
    init {
        check(directory.isDirectory || directory.mkdirs())
    }

    @Synchronized fun models(checkpoint: () -> Unit = {}): List<InstalledLocalModel> {
        val files = directory.listFiles() ?: throw LocalModelStorageException("library_unreadable")
        val models = files.filter { it.name.matches(Regex("[a-f0-9]{64}\\.gguf")) }
        if (models.size > 32) throw LocalModelStorageException("library_limit")
        return models.sortedBy { it.name }.map { file ->
            checkpoint()
            requireOwned(file)
            InstalledLocalModel(file.name.removeSuffix(".gguf"), GgufModelReader.read(file, checkpoint))
        }
    }

    /** An import is copied into a unique private partial; the picker URI/name is never persisted. */
    @Synchronized fun importModel(input: InputStream, licenceReviewed: Boolean, expectedSha256: String? = null,
                                  checkpoint: () -> Unit = {}, progress: (Long) -> Unit = {}): InstalledLocalModel {
        require(licenceReviewed)
        expectedSha256?.let(::requireHash)
        val partial = owned("import-${UUID.randomUUID()}.part")
        check(partial.createNewFile())
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var count = 0L
            input.use { source -> partial.outputStream().use { target ->
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    checkpoint()
                    val n = source.read(buffer)
                    if (n < 0) break
                    if (n == 0) continue
                    count += n
                    if (count > GgufModelReader.MAX_MODEL_BYTES) throw LocalModelStorageException("model_size")
                    ensureSpace(n.toLong())
                    target.write(buffer, 0, n); digest.update(buffer, 0, n); progress(count)
                }
                target.fd.sync()
            } }
            val hash = digest.digest().hex()
            if (expectedSha256 != null && hash != expectedSha256) throw LocalModelStorageException("checksum_mismatch")
            val metadata = GgufModelReader.read(partial, checkpoint)
            return install(partial, hash, metadata, checkpoint)
        } finally {
            // Only the unique partial created by this exact import can be removed here.
            if (partial.exists() && !partial.delete()) throw LocalModelStorageException("partial_cleanup_failed")
        }
    }

    /** Rehash on explicit load. A valid header or filename is never treated as an integrity check. */
    @Synchronized fun verifiedFile(sha256: String, checkpoint: () -> Unit = {}): File {
        requireHash(sha256)
        val file = owned("$sha256.gguf")
        if (hash(file, checkpoint) != sha256) throw LocalModelStorageException("checksum_mismatch")
        GgufModelReader.read(file, checkpoint)
        return file
    }

    /** The caller must unload this model before offering its explicit Delete action. */
    @Synchronized fun delete(sha256: String) {
        requireHash(sha256)
        val file = owned("$sha256.gguf")
        if (file.exists() && !file.delete()) throw LocalModelStorageException("delete_failed")
    }

    internal fun ensureSpace(additional: Long) {
        if (additional < 0 || availableBytes() < SPACE_RESERVE + additional) throw LocalModelStorageException("insufficient_space")
    }

    internal fun owned(name: String): File {
        require(name.matches(Regex("[A-Za-z0-9.-]{1,120}")) && name != "." && name != "..")
        return File(directory, name).also(::requireOwned)
    }

    internal fun install(partial: File, sha256: String, metadata: GgufModelMetadata, checkpoint: () -> Unit): InstalledLocalModel {
        requireHash(sha256); requireOwned(partial); checkpoint()
        val target = owned("$sha256.gguf")
        if (target.exists()) {
            if (hash(target, checkpoint) != sha256) throw LocalModelStorageException("existing_model_corrupt")
        } else {
            if (models(checkpoint).size >= 32) throw LocalModelStorageException("library_limit")
            // Same private filesystem: incomplete bytes never appear as an installed model.
            Files.move(partial.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        }
        return InstalledLocalModel(sha256, metadata)
    }

    internal fun hash(file: File, checkpoint: () -> Unit): String {
        requireOwned(file)
        if (!file.isFile || file.length() !in 24..GgufModelReader.MAX_MODEL_BYTES) throw LocalModelStorageException("model_size")
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) { checkpoint(); val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        return digest.digest().hex()
    }

    private fun requireOwned(file: File) { require(file.absoluteFile.parentFile == directory.absoluteFile && file.canonicalFile == file.absoluteFile) }
    private fun requireHash(value: String) { require(value.matches(Regex("[a-f0-9]{64}"))) }
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
}
