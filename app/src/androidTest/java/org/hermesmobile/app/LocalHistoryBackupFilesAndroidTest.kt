package org.hermesmobile.app

import android.os.ParcelFileDescriptor
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

class LocalHistoryBackupFilesAndroidTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun root() = File(context.noBackupFilesDir, "phone-backup-file-fixture-" + UUID.randomUUID())
        .also { check(it.mkdir()) }
    private fun chat() = LocalConversation(modelSha256 = "c".repeat(64), updatedAt = 130,
        exchanges = listOf(LocalExchange("Garden question", "Private garden 🌱", LocalReplyState.COMPLETE)))
    private fun fixturePassphrase() = "orchard-花园-🌱-fixture".toCharArray()

    @Test fun androidPrivateStagingFreezeSurvivesExternalFileChangesUntilConfirmedRestore() {
        val root = root()
        try {
            val source = LocalConversationStore(File(root, "source"))
            val organization = LocalHistoryOrganization(File(root, "source-labels"), source)
            val staging = File(root, "history-backup-stages")
            val files = LocalHistoryBackupFiles(staging, source, organization)
            val seed = chat()
            val savedLabels = LocalHistoryPreferences("Archived garden 🌱", pinned = true, archived = true)
            source.saveNew(seed)
            organization.update(seed.id, seed.modelSha256, null, savedLabels)
            val output = File(root, "picker-output.sgbackup")
            files.export(organization.history(), fixturePassphrase()).use { backup ->
                val fd = ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_WRITE_ONLY)
                ParcelFileDescriptor.AutoCloseOutputStream(fd).use { backup.writeTo(it) }
            }
            val expected = source.file(seed.id).readBytes()
            val fd = ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY)
            val imported = ParcelFileDescriptor.AutoCloseInputStream(fd).use { input -> files.stageImport(input) }
            output.writeText("A later provider edit")
            val restored = LocalConversationStore(File(root, "destination"))
            val restoredLabels = LocalHistoryOrganization(File(root, "destination-labels"), restored)
            val transaction = LocalHistoryRestoreTransaction(restored, restoredLabels)
            imported.use { document ->
                assertEquals(LocalHistoryBackupFormat.PROTECTED, document.format)
                document.inspect(fixturePassphrase()).use { review ->
                    val plan = transaction.prepare(review.archive)
                    assertTrue(restored.list().isEmpty())
                    transaction.restore(plan)
                }
            }
            assertEquals(seed, restored.load(seed.id))
            assertEquals(savedLabels, restoredLabels.entries().getValue(seed.id).preferences)
            assertArrayEquals(expected, source.file(seed.id).readBytes())
            assertTrue(staging.listFiles()!!.isEmpty())
        } finally {
            assertTrue(root.deleteRecursively())
        }
    }

    @Test fun androidColdHistoryReadReclaimsAbandonedStagesWithoutChangingTheLibrary() {
        val root = root()
        try {
            val store = LocalConversationStore(File(root, "chats"))
            val conversation = chat()
            store.saveNew(conversation)
            val before = store.file(conversation.id).readBytes()
            val staging = File(root, "history-backup-stages").apply { check(mkdir()) }
            val leftover = File(staging, "review-00000000-0000-0000-0000-000000000001.part")
            leftover.writeText("Abandoned review fixture")
            val other = File(staging, "unrecognized.keep")
            other.writeText("Keep this fixture")
            val controller = LocalAiController(File(root, "models"), store.directory, context.applicationInfo.nativeLibraryDir,
                supported = false, freeMemory = {Long.MAX_VALUE}, guard = UpdateWorkGuard())

            val state = runBlocking { withTimeout(15_000) { controller.state.first { !it.busy } } }
            assertNull(state.error)
            assertEquals(listOf(conversation.id), state.history.map { it.id })
            assertTrue(!leftover.exists())
            assertEquals("Keep this fixture", other.readText())
            assertArrayEquals(before, store.file(conversation.id).readBytes())
        } finally {
            assertTrue(root.deleteRecursively())
        }
    }
}
