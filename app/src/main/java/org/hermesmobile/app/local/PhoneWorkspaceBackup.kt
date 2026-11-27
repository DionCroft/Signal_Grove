package org.hermesmobile.app.local

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import org.hermesmobile.app.data.*

internal object PhoneWorkspaceBackup {
    const val FILENAME = "SignalGrove-workspace.sgworkspace"
    const val MAX_PLAINTEXT = 4 * 1024 * 1024
    const val MAX_FILE = MAX_PLAINTEXT + 256

    fun encrypt(archive: WorkspaceBackupArchive, passphrase: CharArray, checkpoint: () -> Unit): ByteArray {
        requireValid(archive)
        val payload = WorkspacePayloadBuffer(checkpoint)
        try {
            PhoneWorkspaceBackupWire.write(archive, payload)
            val encoded = payload.toByteArray()
            try {
                val encrypted = ByteArrayOutputStream()
                LocalHistoryBackupProtection.writeWorkspacePayload(encoded, passphrase, encrypted, checkpoint)
                return encrypted.toByteArray()
            } finally {
                encoded.fill(0)
            }
        } finally {
            payload.wipe()
        }
    }

    fun readFile(input: InputStream, checkpoint: () -> Unit): ByteArray =
        readSmallProtectedBackup(input, MAX_FILE, LocalHistoryBackupProtection::recognizesWorkspaceEnvelope, checkpoint)

    fun unlock(ciphertext: ByteArray, passphrase: CharArray, checkpoint: () -> Unit): WorkspaceBackupArchive {
        if (!LocalHistoryBackupProtection.recognizesWorkspaceEnvelope(ciphertext)) invalid()
        val plaintext = object : ByteArrayOutputStream() {
            fun decode(): WorkspaceBackupArchive = PhoneWorkspaceBackupWire.read(ByteArrayInputStream(buf, 0, count))
            fun wipe() { buf.fill(0); reset() }
        }
        try {
            LocalHistoryBackupProtection.readWorkspacePayload(ciphertext.inputStream(), passphrase, plaintext, checkpoint)
            return try {
                plaintext.decode().also(::requireValid)
            } catch (failure: LocalHistoryBackupException) {
                throw failure
            } catch (stopped: CancellationException) {
                throw stopped
            } catch (_: Exception) {
                invalid()
            }
        } finally {
            plaintext.wipe()
        }
    }

    fun requireValid(archive: WorkspaceBackupArchive) {
        require(archive.version == 1 && archive.count > 0)
        require(archive.captures.size <= 5_000 && archive.notebooks.size <= 100)
        TaskTransfer.validate(archive.tasks)
        val captureIds = archive.captures.map { it.id }.toSet()
        require(captureIds.size == archive.captures.size)
        val taskIds = archive.tasks.map { it.id }.toSet()
        require(archive.taskLinks.size <= taskIds.size && archive.taskLinks.map { it.taskId }.toSet().size == archive.taskLinks.size)
        require(archive.taskLinks.all { it.taskId in taskIds && it.captureId in captureIds })
        require(archive.captures.all { capture ->
            validCaptureId(capture.id) && validCaptureText(capture.content) &&
                CaptureSource.entries.any { it.name == capture.source } && capture.createdAt > 0 &&
                (capture.processedAt == null || capture.processedAt > 0) &&
                (capture.archivedAt == null || capture.archivedAt > 0)
        })
        val notebooks = archive.notebooks.map { it.notebook.id }.toSet()
        require(notebooks.size == archive.notebooks.size)
        val entryIds = mutableSetOf<String>()
        archive.notebooks.forEach { archiveNotebook ->
            val notebook = archiveNotebook.notebook
            require(validCaptureId(notebook.id) && validNotebookTitle(notebook.name) &&
                notebook.createdAt > 0 && notebook.openedAt > 0 && archiveNotebook.entries.size <= 200)
            val entryKeys = mutableSetOf<Pair<String, String>>()
            archiveNotebook.entries.forEach { entry ->
                val item = entry.item
                require(item.notebookId == notebook.id && entryIds.add(item.id))
                require(entryKeys.add(item.kind to item.sourceId))
                requireValidNotebookEntry(item, entry.excerpts)
            }
        }
    }

    private class WorkspacePayloadBuffer(private val checkpoint: () -> Unit) : ByteArrayOutputStream() {
        override fun write(value: Int) {
            reserve(1)
            super.write(value)
        }

        override fun write(bytes: ByteArray, offset: Int, size: Int) {
            reserve(size)
            super.write(bytes, offset, size)
        }

        fun wipe() { buf.fill(0); reset() }

        private fun reserve(size: Int) {
            checkpoint()
            if (size > MAX_PLAINTEXT - count) throw LocalHistoryBackupException(LocalHistoryBackupProblem.TOO_LARGE)
        }
    }

    private fun invalid(): Nothing = throw LocalHistoryBackupException(LocalHistoryBackupProblem.INVALID)
}
