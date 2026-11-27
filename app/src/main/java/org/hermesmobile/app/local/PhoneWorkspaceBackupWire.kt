package org.hermesmobile.app.local

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import org.hermesmobile.app.data.*

/** The staging buffer bounds the total payload. Each decoded field has its own limit. */
internal object PhoneWorkspaceBackupWire {
    private const val VERSION = 1

    fun write(archive: WorkspaceBackupArchive, output: OutputStream) {
        val data = DataOutputStream(output)
        data.writeInt(VERSION)
        data.writeInt(archive.captures.size)
        archive.captures.forEach { capture ->
            data.text(capture.id)
            data.text(capture.content)
            data.text(capture.source)
            data.writeLong(capture.createdAt)
            data.writeLong(capture.processedAt ?: 0)
            data.writeLong(capture.archivedAt ?: 0)
        }
        data.writeInt(archive.tasks.size)
        archive.tasks.forEach { task ->
            data.text(task.id)
            data.text(task.title)
            data.text(task.dueDate)
            data.writeInt(task.priority)
            data.writeBoolean(task.completed)
            data.text(task.notes)
        }
        data.writeInt(archive.taskLinks.size)
        archive.taskLinks.forEach { link ->
            data.text(link.taskId)
            data.text(link.captureId)
        }
        data.writeInt(archive.notebooks.size)
        archive.notebooks.forEach { writeNotebook(data, it) }
        data.flush()
    }

    fun read(input: InputStream): WorkspaceBackupArchive {
        val data = DataInputStream(input)
        val version = data.readInt()
        if (version != VERSION) throw LocalHistoryBackupException(LocalHistoryBackupProblem.UNSUPPORTED_VERSION)
        val captures = List(data.count(5_000)) {
            WorkspaceBackupCapture(data.text(36), data.text(MAX_CAPTURE_CHARS), data.text(32), data.readLong(),
                data.readLong().takeIf { it != 0L }, data.readLong().takeIf { it != 0L })
        }
        val tasks = List(data.count(TaskTransfer.MAX_TASKS)) {
            LocalTask(data.text(120), data.text(2_000), data.text(32), data.readInt(),
                data.readUnsignedByte().also { require(it in 0..1) } != 0, data.text(128_000))
        }
        val links = List(data.count(tasks.size)) { PhoneCaptureTaskLink(data.text(120), data.text(36)) }
        val notebooks = List(data.count(100)) { readNotebook(data) }
        require(data.read() == -1)
        return WorkspaceBackupArchive(version, captures, tasks, links, notebooks)
    }

    private fun writeNotebook(data: DataOutputStream, archived: WorkspaceBackupNotebook) {
        val notebook = archived.notebook
        data.text(notebook.id)
        data.text(notebook.name)
        data.writeLong(notebook.createdAt)
        data.writeLong(notebook.openedAt)
        data.writeInt(archived.entries.size)
        archived.entries.forEach { entry ->
            val item = entry.item
            data.text(item.id)
            data.text(item.kind)
            data.text(item.sourceId)
            data.text(item.fingerprint)
            data.text(item.title)
            data.text(item.answer)
            data.writeLong(item.addedAt)
            data.writeInt(entry.excerpts.size)
            entry.excerpts.forEach { excerpt ->
                data.text(excerpt.documentId)
                data.text(excerpt.fingerprint)
                data.text(excerpt.documentName)
                data.writeInt(excerpt.page ?: 0)
                data.writeInt(excerpt.firstLine)
                data.writeInt(excerpt.lastLine)
                data.text(excerpt.excerpt)
            }
        }
    }

    private fun readNotebook(data: DataInputStream): WorkspaceBackupNotebook {
        val notebook = PhoneNotebook(data.text(36), data.text(160), data.readLong(), data.readLong())
        val entries = List(data.count(200)) {
            val item = NotebookItem(data.text(36), notebook.id, data.text(16), data.text(512), data.text(64),
                data.text(512), data.text(8192), data.readLong())
            val excerpts = List(data.count(2)) { index ->
                NotebookSource(item.id, index, data.text(36), data.text(64), data.text(512),
                    data.readInt().takeIf { it != 0 }, data.readInt(), data.readInt(), data.text(1536))
            }
            WorkspaceBackupEntry(item, excerpts)
        }
        return WorkspaceBackupNotebook(notebook, entries)
    }

    private fun DataInputStream.count(limit: Int): Int = readInt().also { require(it in 0..limit) }

    private fun DataInputStream.text(limit: Int): String {
        val bytes = ByteArray(count(limit))
        try {
            readFully(bytes)
            return Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
        } finally {
            bytes.fill(0)
        }
    }

    private fun DataOutputStream.text(value: String) {
        require(Charsets.UTF_8.newEncoder().canEncode(value))
        val bytes = value.toByteArray(Charsets.UTF_8)
        try {
            writeInt(bytes.size)
            write(bytes)
        } finally {
            bytes.fill(0)
        }
    }
}
