package org.hermesmobile.app

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.hermesmobile.app.local.*
import org.hermesmobile.app.updates.UpdateWorkGuard
import org.junit.Assert.*
import org.junit.Test

class LocalHistoryRestoreAndroidTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val model = "c".repeat(64)
    private val title = LocalHistoryPreferences("Garden archive 花园", pinned = true, archived = true)
    private class InterruptedPublication : Error()

    private fun chat(id: Long) = LocalConversation(UUID(0, id).toString(), model, 40,
        listOf(LocalExchange("Garden", "A quiet garden 🌱", LocalReplyState.COMPLETE)))

    private fun root() = File(context.noBackupFilesDir, "restore-fixture-" + UUID.randomUUID()).also { check(it.mkdir()) }

    private inner class Library(val root: File) {
        val store = LocalConversationStore(File(root, "chats"))
        val organization = LocalHistoryOrganization(File(root, "chats-organization"), store)

        fun archive(vararg chats: LocalConversation): LocalHistoryBackupArchive.Inspection {
            val rows = organization.history().associateBy { it.id }
            val file = File(root, "fixture.sgbackup")
            file.outputStream().use { out ->
                LocalHistoryBackupArchive.write(chats.map { it.id }, out, { id ->
                    val row = rows.getValue(id)
                    store.backupRecord(row, row.organization)
                })
            }
            return LocalHistoryBackupArchive.inspect(file)
        }
    }

    @Test fun androidRestorePublishesReviewedChatsAndBranchLinksInPrivateStorage() {
        val source = root()
        val target = root()
        try {
            val origin = Library(source)
            val library = Library(target)
            val parent = chat(1)
            origin.store.saveNew(parent)
            val branch = origin.store.createBranch(parent, 0, LocalBranchKind.FORK, 41)
            origin.organization.update(branch.id, model, null, title)
            val old = parent.copy(modelSha256 = "d".repeat(64))
            library.store.saveNew(old)
            val initial = library.store.file(old.id).readBytes()
            val transaction = LocalHistoryRestoreTransaction(library.store, library.organization)
            val plan = transaction.prepare(origin.archive(branch, parent))
            transaction.restore(plan)

            val reopened = Library(target)
            assertEquals(LocalHistoryRestoreOutcome.NONE,
                LocalHistoryRestoreTransaction(reopened.store, reopened.organization).recover())
            assertArrayEquals(initial, reopened.store.file(old.id).readBytes())
            val imported = reopened.store.load(branch.id)
            assertEquals(branch.origin?.copy(linkedParentId = plan.items[1].targetId), imported.origin)
            assertEquals(branch.exchanges, imported.exchanges)
            assertEquals(title, reopened.organization.entries().getValue(branch.id).preferences)
            assertEquals(3, reopened.store.list().size)
        } finally {
            assertTrue(source.deleteRecursively())
            assertTrue(target.deleteRecursively())
        }
    }

    @Test fun androidColdControllerRollsBackPartialPublicationBeforeShowingHistory() {
        val source = root()
        val target = root()
        try {
            val origin = Library(source)
            val library = Library(target)
            val old = chat(1)
            val first = chat(2)
            val second = chat(3)
            origin.store.saveNew(first)
            origin.store.saveNew(second)
            library.store.saveNew(old)
            library.organization.update(old.id, model, null, title)
            val before = library.store.file(old.id).readBytes()
            val beforeIndex = library.organization.index().readBytes()
            val transaction = LocalHistoryRestoreTransaction(library.store, library.organization, publishChat = { staged, to ->
                LocalHistoryRestoreTransaction.publishNewChat(staged, to)
                throw InterruptedPublication()
            })
            val plan = transaction.prepare(origin.archive(first, second))
            try {
                transaction.restore(plan)
                fail("Expected interruption after the first rename")
            } catch (_: InterruptedPublication) { }
            assertTrue(library.store.file(first.id).isFile)
            assertFalse(library.store.file(second.id).exists())

            val controller = LocalAiController(File(target, "models"), library.store.directory, context.applicationInfo.nativeLibraryDir,
                supported = false, freeMemory = { Long.MAX_VALUE }, guard = UpdateWorkGuard())
            val state = runBlocking { withTimeout(15_000) { controller.state.first { !it.busy } } }
            assertNull(state.error)
            assertEquals(listOf(old.id), state.history.map { it.id })
            assertEquals(title, state.history.single().organization?.preferences)
            assertArrayEquals(before, library.store.file(old.id).readBytes())
            assertArrayEquals(beforeIndex, library.organization.index().readBytes())
            assertFalse(library.store.file(first.id).exists())
            assertFalse(library.store.file(second.id).exists())
        } finally {
            assertTrue(source.deleteRecursively())
            assertTrue(target.deleteRecursively())
        }
    }
}
