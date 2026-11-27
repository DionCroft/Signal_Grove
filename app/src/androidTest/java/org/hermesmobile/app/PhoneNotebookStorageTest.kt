package org.hermesmobile.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.hermesmobile.app.data.*
import org.hermesmobile.app.local.*
import org.hermesmobile.app.updates.UpdateWorkGuard
import org.junit.Assert.*
import org.junit.Test

class PhoneNotebookStorageTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun notebooksKeepSourcesAndSearchIsolatedWithoutLoadingAModel() = runBlocking {
        val identifier = UUID.randomUUID().toString()
        val name = "notebook-test-$identifier.db"
        val db = Room.databaseBuilder(context, HermesDatabase::class.java, name)
            .addMigrations(*ALL_HERMES_MIGRATIONS).build()
        val fixture = File(context.noBackupFilesDir, "notebook-test-$identifier")
        check(fixture.mkdir())
        val documents = PhoneDocumentLibrary(File(fixture, "selected-documents"))
        val controller = LocalAiController(File(fixture, "models"), File(fixture, "history"),
            context.applicationInfo.nativeLibraryDir, android.os.Process.is64Bit(), { Long.MAX_VALUE }, UpdateWorkGuard())
        val workspace = PhoneNotebookWorkspace(db, controller, this)
        suspend fun settle(): NotebookWorkspaceState = withTimeout(10_000) {
            workspace.state.first { !it.busy }.also { assertNull(it.error) }
        }
        try {
            workspace.create("Greenhouse")
            val first = requireNotNull(settle().notebookId)
            workspace.create("Workshop")
            val second = requireNotNull(settle().notebookId)
            assertNotEquals(first, second)
            val note = PhoneCapture(UUID.randomUUID().toString(), "Remember the orchid humidity reading.",
                CaptureSource.NOTE.name, 10)
            val task = LocalTask(UUID.randomUUID().toString(), "Check the workshop vent", notes = "Clean the fan grill.")
            db.phoneCaptures().save(note)
            db.dao().save(task)
            workspace.attachCapture(first, note.id); settle()
            workspace.attachCapture(first, note.id); settle()
            workspace.attachTask(second, task.id); settle()

            val excerpt = "The greenhouse gate opens at seven."
            documents.save("opening.txt", excerpt.byteInputStream(), null) {}
            val doc = documents.list().single()
            workspace.attachDocument(first, doc); settle()
            assertEquals(NotebookItemKind.DOCUMENT.name, workspace.entries(first, "gate opens").first().single().kind)
            val review = requireNotNull(PhoneDocumentQuestions.review("When does the greenhouse gate open?",
                documents.selected(listOf(doc.id)) {}, {}))
            val answer = PhoneDocumentAnswer(review, "Seven [S1]", LocalReplyState.COMPLETE)
            workspace.open(answer)
            workspace.saveAnswer(first, answer); settle()
            assertNull(workspace.state.value.pendingAnswer)
            assertEquals(3, workspace.entries(first, "").first().size)
            val queriedNote = workspace.entries(first, "humidity").first().single()
            assertEquals(note, workspace.capture(queriedNote))
            assertEquals(task, workspace.task(workspace.entries(second, "fan grill").first().single()))
            assertTrue(workspace.entries(first, "fan grill").first().isEmpty())
            assertTrue(workspace.entries(second, "humidity").first().isEmpty())

            val saved = workspace.entries(first, "Seven [S1]").first().single()
            val citation = workspace.sources(first, saved.id).first().single()
            assertEquals(excerpt, citation.excerpt)
            assertEquals(excerpt, workspace.source(first, citation).sections.single())
            assertTrue(workspace.sources(second, saved.id).first().isEmpty())
            assertTrue(runCatching { workspace.source(second, citation) }.isFailure)
            documents.save("opening.txt", "Now opens at eight.".byteInputStream(), doc) {}
            assertTrue(runCatching { workspace.source(first, citation) }.isFailure)
            assertEquals(excerpt, workspace.sources(first, saved.id).first().single().excerpt)
            assertNull(controller.state.value.loaded)
        } finally {
            controller.leave()
            db.close()
            context.deleteDatabase(name)
        }
    }
}
