package org.hermesmobile.app.local

import java.io.Closeable
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.UUID

internal enum class LocalHistoryBackupFormat(val minBytes: Long, val maxBytes: Long) {
    PLAIN(LocalHistoryBackupArchive.MIN_BYTES.toLong(), LocalHistoryBackupArchive.MAX_BYTES.toLong()),
    PROTECTED(LocalHistoryBackupProtection.MIN_BYTES.toLong(), LocalHistoryBackupProtection.MAX_BYTES.toLong())
}

/** Owns one immutable, app-private copy for the duration of a picker and review. */
internal class LocalHistoryBackupDocument internal constructor(
    private val staged: File,
    val format: LocalHistoryBackupFormat,
    val bytes: Long,
    private val digest: ByteArray
) : Closeable {
    val suggestedFilename = "SignalGrove-phone-history." + LocalHistoryBackupArchive.FILE_EXTENSION

    fun writeTo(output: OutputStream, checkpoint: () -> Unit = {}) {
        checkStage(checkpoint)
        staged.inputStream().use { input ->
            val copied = readBackupBytes(input, bytes, checkpoint) { buffer, count -> output.write(buffer, 0, count) }
            if (copied != bytes) changed()
        }
    }

    /** Keep this document open until the returned review and its restore plan are closed. */
    fun inspect(passphrase: CharArray?, checkpoint: () -> Unit = {}): LocalHistoryBackupReview {
        checkStage(checkpoint)
        return when (format) {
            LocalHistoryBackupFormat.PLAIN -> {
                require(passphrase == null)
                LocalHistoryBackupReview(LocalHistoryBackupArchive.inspect(staged, checkpoint))
            }
            LocalHistoryBackupFormat.PROTECTED -> {
                if (passphrase == null) throw LocalHistoryBackupException(LocalHistoryBackupProblem.INVALID_PASSPHRASE)
                val review = LocalHistoryBackupProtection.inspect(staged, passphrase, checkNotNull(staged.parentFile), checkpoint)
                LocalHistoryBackupReview(review.inspection, review)
            }
        }
    }

    override fun close() {
        Files.deleteIfExists(staged.toPath())
    }

    private fun checkStage(checkpoint: () -> Unit) {
        if (!staged.isFile || Files.isSymbolicLink(staged.toPath()) || staged.length() != bytes) changed()
        val current = MessageDigest.getInstance("SHA-256")
        val measured = staged.inputStream().use { input ->
            readBackupBytes(input, bytes, checkpoint) { buffer, count -> current.update(buffer, 0, count) }
        }
        if (measured != bytes || !MessageDigest.isEqual(current.digest(), digest)) changed()
    }

    private fun changed(): Nothing = throw LocalHistoryBackupException(LocalHistoryBackupProblem.CHANGED)
}

internal class LocalHistoryBackupReview internal constructor(
    val archive: LocalHistoryBackupArchive.Inspection,
    private val decrypted: Closeable? = null
) : Closeable {
    override fun close() {
        decrypted?.close()
    }
}

/** One owner per app process. Its fixed directory is scoped to phone history document work. */
internal class LocalHistoryBackupFiles(
    private val stagingDirectory: File,
    private val conversations: LocalConversationStore,
    private val organization: LocalHistoryOrganization
) {
    private var ready = false

    fun export(
        selection: List<LocalConversationSummary>,
        passphrase: CharArray?,
        checkpoint: () -> Unit = {}
    ): LocalHistoryBackupDocument {
        val selected = selection.associateBy { it.id }
        require(selected.size == selection.size && selected.size in 1..LocalConversationStore.MAX_CHATS)
        selected.keys.forEach(::requireCanonicalChatId)
        val format = if (passphrase == null) LocalHistoryBackupFormat.PLAIN else LocalHistoryBackupFormat.PROTECTED
        return stage("export", format, checkpoint) { output ->
            synchronized(organization) {
                synchronized(conversations) {
                    val labels = organization.entries(checkpoint)
                    val load = { id: String -> conversations.backupRecord(selected.getValue(id), labels[id], checkpoint) }
                    val ids = selected.keys.toList()
                    if (passphrase == null) LocalHistoryBackupArchive.write(ids, output, load, checkpoint)
                    else LocalHistoryBackupProtection.write(ids, passphrase, output, load, checkpoint)
                }
            }
        }
    }

    /** Copy a single picker stream into private storage. The caller owns the provider stream. */
    fun stageImport(input: InputStream, checkpoint: () -> Unit = {}): LocalHistoryBackupDocument {
        checkpoint()
        val header = ByteArray(8)
        try {
            DataInputStream(input).readFully(header)
        } catch (_: EOFException) {
            throw LocalHistoryBackupException(LocalHistoryBackupProblem.INVALID)
        }
        val format = when {
            LocalHistoryBackupArchive.recognizesHeader(header) -> LocalHistoryBackupFormat.PLAIN
            LocalHistoryBackupProtection.recognizesHeader(header) -> LocalHistoryBackupFormat.PROTECTED
            else -> throw LocalHistoryBackupException(LocalHistoryBackupProblem.INVALID)
        }
        return stage("import", format, checkpoint) { output ->
            output.write(header)
            readBackupBytes(input, format.maxBytes - header.size, checkpoint) { buffer, count ->
                output.write(buffer, 0, count)
            }
        }
    }

    /** The process can reclaim a prior session when phone history first becomes available. */
    fun discardAbandonedDocuments() {
        if (stagingDirectory.exists()) prepareDirectory()
    }

    private fun stage(
        prefix: String,
        format: LocalHistoryBackupFormat,
        checkpoint: () -> Unit,
        write: (OutputStream) -> Unit
    ): LocalHistoryBackupDocument {
        checkpoint()
        val directory = prepareDirectory()
        val staged = File(directory, prefix + "-" + UUID.randomUUID() + ".part")
        Files.createFile(staged.toPath())
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            staged.outputStream().use { output ->
                write(DigestOutputStream(output, digest))
                checkpoint()
                output.fd.sync()
            }
            val bytes = staged.length()
            if (bytes !in format.minBytes..format.maxBytes) throw LocalHistoryBackupException(LocalHistoryBackupProblem.INVALID)
            checkpoint()
            return LocalHistoryBackupDocument(staged, format, bytes, digest.digest())
        } catch (failure: Throwable) {
            try {
                Files.deleteIfExists(staged.toPath())
            } catch (cleanup: IOException) {
                failure.addSuppressed(cleanup)
            }
            throw failure
        }
    }

    @Synchronized private fun prepareDirectory(): File {
        val absolute = stagingDirectory.absoluteFile
        val directory = File(checkNotNull(absolute.parentFile).canonicalFile, absolute.name)
        if (Files.isSymbolicLink(directory.toPath()) || directory.canonicalFile != directory) {
            throw IOException("backup_stage_unavailable")
        }
        if (!ready) {
            if (!directory.exists()) Files.createDirectory(directory.toPath())
            val previous = directory.listFiles() ?: throw IOException("backup_stage_unavailable")
            for (file in previous) {
                if (!STAGE_NAME.matches(file.name)) continue
                if (Files.isSymbolicLink(file.toPath()) || !file.isFile) throw IOException("backup_stage_unavailable")
                Files.delete(file.toPath())
            }
            ready = true
        }
        return directory
    }

    private companion object {
        val STAGE_NAME = Regex("(?:export|import|review)-[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}\\.part")
    }
}

private fun readBackupBytes(
    input: InputStream,
    limit: Long,
    checkpoint: () -> Unit,
    consume: (ByteArray, Int) -> Unit
): Long {
    val buffer = ByteArray(8192)
    var size = 0L
    try {
        while (true) {
            checkpoint()
            val count = input.read(buffer)
            if (count < 0) return size
            if (count == 0) throw IOException("backup_stream_did_not_advance")
            size += count
            if (size > limit) throw LocalHistoryBackupException(LocalHistoryBackupProblem.INVALID)
            consume(buffer, count)
        }
    } finally {
        buffer.fill(0)
    }
}
