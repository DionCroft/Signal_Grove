package org.hermesmobile.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hermesmobile.app.data.*
import org.hermesmobile.app.local.LocalHistoryBackupException
import org.hermesmobile.app.local.LocalHistoryBackupProblem
import org.junit.Assert.*
import org.junit.Test

class PhoneWorkspaceBackupStorageTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun id() = UUID.randomUUID().toString()

    @Test fun restoredCopiesRetainTheirLinksAndCitationsAfterReopening() = runBlocking {
        val name = "workspace-backup-" + id() + ".db"
        fun open() = Room.databaseBuilder(context, HermesDatabase::class.java, name).addMigrations(*ALL_HERMES_MIGRATIONS).build()
        val note = PhoneCapture(id(), "Check the gate on Friday", CaptureSource.NOTE.name, 100)
        val book = PhoneNotebook(id(), "Calibration project", 90, 90)
        val noteEntry = NotebookItem(id(), book.id, NotebookItemKind.CAPTURE.name, note.id, "", "Gate note", "", 105)
        val answer = NotebookItem(id(), book.id, NotebookItemKind.ANSWER.name, "b".repeat(64), "",
            "When can we open the gate?", "On Friday [S1]", 106)
        val source = NotebookSource(answer.id, 0, id(), "a".repeat(64), "calibration.txt", null, 2, 3, "On Friday")
        val original = WorkspaceBackupKey(WorkspaceBackupKind.NOTEBOOK, book.id)
        try {
            val prepared: WorkspaceRestorePlan
            val first = open()
            try {
                val captures = first.phoneCaptures()
                captures.save(note)
                assertEquals(1, captures.saveReviewedTasks(note.id, id(), note.content, listOf("Check the gate"), 103))
                captures.setArchived(checkNotNull(captures.observe(note.id).first()), 104)
                val books = first.phoneNotebooks()
                books.create(book)
                books.attach(noteEntry, emptyList())
                books.attach(answer, listOf(source))
                val archive = first.workspaceBackup().snapshot(setOf(original))
                assertEquals(3, archive.count)
                assertEquals(note.id, archive.captures.single().id)
                assertEquals(104L, archive.captures.single().archivedAt)
                assertEquals(note.id, archive.taskLinks.single().captureId)
                assertEquals(listOf(source), archive.notebooks.single().entries.first { it.item.id == answer.id }.excerpts)
                val detached = first.workspaceBackup().review(archive.selected(setOf(original))).restored
                val missing = detached.notebooks.single().entries.single { it.item.kind == NotebookItemKind.CAPTURE.name }.item
                assertNotEquals(note.id, missing.sourceId)
                assertNull(captures.observe(missing.sourceId).first())
                prepared = first.workspaceBackup().review(archive)
                assertEquals(archive.rows().map { it.key }.toSet(), prepared.copied)
                assertEquals(3, first.workspaceBackup().catalog().size)
                assertEquals(3, first.workspaceBackup().restore(prepared))
            } finally { first.close() }
            val reopened = open()
            try {
                val backup = reopened.workspaceBackup()
                val copy = prepared.restored.notebooks.single().notebook
                val localCapture = prepared.restored.captures.single().id
                assertEquals(6, backup.catalog().size)
                assertEquals(book, reopened.phoneNotebooks().notebook(book.id))
                assertEquals(copy, reopened.phoneNotebooks().notebook(copy.id))
                val attached = reopened.phoneNotebooks().items(copy.id).first()
                assertEquals(localCapture, attached.single { it.kind == NotebookItemKind.CAPTURE.name }.sourceId)
                val copiedAnswer = attached.single { it.kind == NotebookItemKind.ANSWER.name }
                assertEquals(listOf(source.copy(itemId = copiedAnswer.id)),
                    reopened.phoneNotebooks().sources(copy.id, copiedAnswer.id).first())
                assertEquals(note.content, reopened.phoneCaptures().observe(localCapture).first()?.content)
                assertEquals(104L, reopened.phoneCaptures().observe(localCapture).first()?.archivedAt)
                assertEquals(prepared.restored.tasks.single().id, reopened.phoneCaptures().linkedTasks(localCapture).first().single().id)
                assertEquals(0, reopened.today().unprocessedCaptures().first())
                val replay = runCatching { backup.restore(prepared) }.exceptionOrNull() as LocalHistoryBackupException
                assertEquals(LocalHistoryBackupProblem.CHANGED, replay.problem)
                assertEquals(6, backup.catalog().size)
            } finally { reopened.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun lateIdCollisionLeavesBothTheOriginalAndOtherPlannedInsertsUntouched() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, HermesDatabase::class.java).build()
        try {
            val note = PhoneCapture(id(), "Imported task source", CaptureSource.NOTE.name, 100)
            val task = LocalTask(id(), "Imported task")
            val archive = WorkspaceBackupArchive(1, listOf(WorkspaceBackupCapture(note)), listOf(task),
                listOf(PhoneCaptureTaskLink(task.id, note.id)), emptyList())
            val backup = db.workspaceBackup()
            val reviewed = backup.review(archive)
            assertTrue(reviewed.copied.isEmpty())
            val original = note.copy(content = "A newly saved local capture")
            db.phoneCaptures().save(original)
            val failure = runCatching { backup.restore(reviewed) }.exceptionOrNull() as LocalHistoryBackupException
            assertEquals(LocalHistoryBackupProblem.CHANGED, failure.problem)
            assertEquals(original, db.phoneCaptures().observe(note.id).first())
            assertTrue(db.dao().tasks().first().isEmpty())
            assertTrue(db.phoneCaptures().taskSources().first().isEmpty())
        } finally { db.close() }
    }
}
