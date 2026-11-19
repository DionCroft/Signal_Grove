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

    @Test fun selectedTurnProvenanceSurvivesBackupCollisionRestoreAndMarkdown() {
        val root = root()
        try {
            val source = LocalConversationStore(File(root, "source"))
            val organization = LocalHistoryOrganization(File(root, "labels"), source)
            val stages = LocalHistoryBackupFiles(File(root, "stages"), source, organization)
            val parent = chat().copy(exchanges = listOf(
                LocalExchange("Keep garden 你好", "Garden reply", LocalReplyState.COMPLETE),
                LocalExchange("Omit this question", "Never include this text", LocalReplyState.COMPLETE),
                LocalExchange("Keep forest 🌱", "Forest reply", LocalReplyState.TOKEN_LIMIT)))
            source.saveNew(parent)
            val child = source.createSelectedContinuation(parent, listOf(0, 2), 200)
            val original = listOf(parent, child).associate { it.id to source.file(it.id).readBytes() }
            val transaction = LocalHistoryRestoreTransaction(source, organization)
            val imported = stages.export(organization.history(), null).use { archive ->
                archive.inspect(null).use { review ->
                    val plan = transaction.prepare(review.archive)
                    assertEquals(2, plan.items.size)
                    assertTrue(plan.items.all { it.remapped })
                    val copiedChild = plan.items.single { it.source.id == child.id }
                    val copiedParent = plan.items.single { it.source.id == parent.id }
                    assertEquals(copiedParent.targetId, copiedChild.targetParentId)
                    transaction.restore(plan)
                    source.load(copiedChild.targetId).also {
                        assertEquals(copiedParent.targetId, it.origin!!.linkedParentId)
                    }
                }
            }
            val origin = checkNotNull(imported.origin)
            assertEquals(parent.id, origin.parentId)
            assertEquals(child.origin!!.sourceSha256, origin.sourceSha256)
            assertEquals(listOf(0, 2), origin.selectedExchanges)
            assertEquals(listOf(parent.exchanges.first(), parent.exchanges.last()), imported.exchanges)
            assertEquals(4, source.list().size)
            for ((id, bytes) in original) assertArrayEquals(bytes, source.file(id).readBytes())
            val markdown = java.io.ByteArrayOutputStream().also { source.markdownExport(imported).writeTo(it) }.toString("UTF-8")
            assertTrue(markdown.contains("Source turns: 1, 3"))
            assertTrue(markdown.contains("Source conversation ID: ${parent.id}"))
            assertTrue(markdown.contains("Keep garden 你好") && markdown.contains("Keep forest 🌱"))
            assertFalse(markdown.contains("Omit this question"))
        } finally {
            assertTrue(root.deleteRecursively())
        }
    }

    @Test fun summaryOnlyChatSurvivesBackupRestoreAndExportsLiteralReviewedText() {
        val root = root()
        try {
            val store = LocalConversationStore(File(root, "source"))
            val labels = LocalHistoryOrganization(File(root, "labels"), store)
            val stages = LocalHistoryBackupFiles(File(root, "stages"), store, labels)
            val parent = chat().copy(exchanges = listOf(
                LocalExchange("Earlier private text", "Reply 你好", LocalReplyState.COMPLETE)))
            store.saveNew(parent)
            val approved = "Reviewed garden notes 🌱\n" + "Budget information. ".repeat(5) + "\n<script>literal</script>"
            val child = store.createSummaryContinuation(parent, listOf(0), approved, 200)
            assertEquals(listOf(child.id), LocalHistorySearch(store, labels).find(LocalHistoryQuery("literal")).map { it.id })
            val parentBytes = store.file(parent.id).readBytes()
            val childBytes = store.file(child.id).readBytes()
            val transaction = LocalHistoryRestoreTransaction(store, labels)
            val restored = stages.export(labels.history().filter { it.id == child.id }, null).use { archive ->
                archive.inspect(null).use { review ->
                    val plan = transaction.prepare(review.archive)
                    val copy = plan.items.single()
                    assertTrue(copy.remapped && copy.parentOutsideArchive)
                    transaction.restore(plan)
                    store.load(copy.targetId)
                }
            }
            assertEquals(child.reviewedSummary, restored.reviewedSummary)
            assertNull(restored.origin?.linkedParentId)
            assertTrue(restored.exchanges.isEmpty())
            assertArrayEquals(parentBytes, store.file(parent.id).readBytes())
            assertArrayEquals(childBytes, store.file(child.id).readBytes())
            val markdown = java.io.ByteArrayOutputStream().also { store.markdownExport(restored).writeTo(it) }.toString("UTF-8")
            assertTrue(markdown.contains("## Reviewed summary") && markdown.contains("Source turns: 1"))
            assertTrue(markdown.contains("Source conversation ID: " + parent.id))
            assertTrue(markdown.contains("text\n" + approved + "\n"))
            assertFalse(markdown.contains("Earlier private text"))
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
