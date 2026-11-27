package org.hermesmobile.app.local

import java.util.UUID
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test

class PhoneWorkspaceBackupTest {
    private val capture = WorkspaceBackupCapture(
        UUID.randomUUID().toString(), "Garden notes 花🌱", CaptureSource.NOTE.name, 100, 101, 102
    )
    private val task = LocalTask(UUID.randomUUID().toString(), "Open the greenhouse", "2026-11-27", 2, true)
    private val notebook = PhoneNotebook(UUID.randomUUID().toString(), "Garden project", 90, 100)
    private val item = NotebookItem(UUID.randomUUID().toString(), notebook.id, NotebookItemKind.ANSWER.name,
        "b".repeat(64), "", "Opening time", "Seven [S1]", 102)
    private val citation = NotebookSource(item.id, 0, UUID.randomUUID().toString(), "a".repeat(64),
        "gate.txt", null, 1, 1, "Opens at seven.")
    private val archive = WorkspaceBackupArchive(
        1, listOf(capture), listOf(task), listOf(PhoneCaptureTaskLink(task.id, capture.id)),
        listOf(WorkspaceBackupNotebook(notebook, listOf(WorkspaceBackupEntry(item, listOf(citation)))))
    )

    @Test fun workspaceBackupUsesDistinctAuthenticatedFormatAndRetainsArchivedNotesAndCitations() {
        val phrase = "garden fixture 花".toCharArray()
        try {
            val first = PhoneWorkspaceBackup.encrypt(archive, phrase) {}
            val second = PhoneWorkspaceBackup.encrypt(archive, phrase) {}
            assertFalse(first.contentEquals(second))
            assertTrue(LocalHistoryBackupProtection.recognizesWorkspaceEnvelope(first))
            assertFalse(LocalHistoryBackupProtection.recognizesProfileEnvelope(first))
            assertFalse(LocalHistoryBackupProtection.recognizesHeader(first.copyOf(8)))
            assertFalse(first.toString(Charsets.ISO_8859_1).contains("Garden notes"))
            assertArrayEquals(first, PhoneWorkspaceBackup.readFile(first.inputStream()) {})
            assertEquals(archive, PhoneWorkspaceBackup.unlock(first, phrase) {})
        } finally { phrase.fill('\u0000') }
    }

    @Test fun wrongPassphraseTamperingAndInvalidRelationshipsProduceNoRestorePayload() {
        val phrase = "backup test".toCharArray()
        try {
            val encrypted = PhoneWorkspaceBackup.encrypt(archive, phrase) {}
            val invalid = "invalid".toCharArray()
            try {
                assertEquals(LocalHistoryBackupProblem.AUTHENTICATION, assertThrows(LocalHistoryBackupException::class.java) {
                    PhoneWorkspaceBackup.unlock(encrypted, invalid) {}
                }.problem)
            } finally { invalid.fill('\u0000') }
            encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
            assertEquals(LocalHistoryBackupProblem.AUTHENTICATION, assertThrows(LocalHistoryBackupException::class.java) {
                PhoneWorkspaceBackup.unlock(encrypted, phrase) {}
            }.problem)
            assertThrows(IllegalArgumentException::class.java) {
                PhoneWorkspaceBackup.encrypt(archive.copy(captures = emptyList()), phrase) {}
            }
            assertThrows(LocalHistoryBackupException::class.java) {
                PhoneWorkspaceBackup.readFile(ByteArray(PhoneWorkspaceBackup.MAX_FILE + 1).inputStream()) {}
            }
        } finally { phrase.fill('\u0000') }
    }

    @Test fun restoreSelectionKeepsOnlyLinksBetweenChosenRecordsAndPreservesSavedCitations() {
        val records = setOf(WorkspaceBackupKey(WorkspaceBackupKind.NOTEBOOK, notebook.id),
            WorkspaceBackupKey(WorkspaceBackupKind.TASK, task.id))
        val subset = archive.selected(records)
        assertEquals(records, subset.rows().map { it.key }.toSet())
        assertTrue(subset.captures.isEmpty())
        assertTrue(subset.taskLinks.isEmpty())
        assertEquals(listOf(citation), subset.notebooks.single().entries.single().excerpts)
        assertThrows(IllegalArgumentException::class.java) {
            archive.selected(records + WorkspaceBackupKey(WorkspaceBackupKind.CAPTURE, UUID.randomUUID().toString()))
        }
    }
}
