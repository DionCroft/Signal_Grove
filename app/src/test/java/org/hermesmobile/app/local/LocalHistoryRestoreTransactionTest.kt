package org.hermesmobile.app.local

import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalHistoryRestoreTransactionTest {
    @get:Rule val temporary = TemporaryFolder()
    private val model = "a".repeat(64)
    private val source = LocalHistoryPreferences("Source garden", pinned = true, archived = true)
    private val saved = LocalHistoryPreferences("Existing garden", pinned = false, archived = true)

    private class SimulatedProcessStop : Error()

    private fun chat(number: Long) = LocalConversation(
        UUID(0, number).toString(), model, 107,
        listOf(LocalExchange("Garden", "Quiet evening", LocalReplyState.COMPLETE))
    )

    private inner class Library(root: File) {
        val store = LocalConversationStore(File(root, "chats"))
        val labels = LocalHistoryOrganization(File(root, "labels"), store)
        val journal = File(store.directory, ".restore")

        fun file(conversation: LocalConversation) = store.file(conversation.id)
        fun transaction(
            sync: (File) -> Unit = {},
            publish: (File, File) -> Unit = { staged, target -> LocalHistoryRestoreTransaction.publishNewChat(staged, target) }
        ) = LocalHistoryRestoreTransaction(store, labels, sync, publish)

        fun archive(vararg chats: LocalConversation): LocalHistoryBackupArchive.Inspection {
            val rows = labels.history().associateBy { it.id }
            val path = temporary.newFile()
            path.outputStream().use { output ->
                LocalHistoryBackupArchive.write(chats.map { it.id }, output, { id ->
                    val row = rows.getValue(id)
                    store.backupRecord(row, row.organization)
                })
            }
            return LocalHistoryBackupArchive.inspect(path)
        }

        fun remember(conversation: LocalConversation, preferences: LocalHistoryPreferences) {
            store.saveNew(conversation)
            labels.update(conversation.id, conversation.modelSha256, null, preferences)
        }
    }

    @Test fun restoresRemappedBranchesAndLabelsWithoutChangingExistingFiles() {
        val origin = Library(temporary.newFolder())
        val target = Library(temporary.newFolder())
        val parent = chat(1)
        val unaffected = chat(2)
        origin.remember(parent, source)
        origin.store.saveNew(unaffected)
        val child = origin.store.createBranch(parent, 0, LocalBranchKind.FORK, 108)
        origin.labels.update(child.id, model, null, source)
        val old = parent.copy(modelSha256 = "b".repeat(64))
        target.remember(old, saved)
        val oldBytes = target.file(old).readBytes()

        val transaction = target.transaction()
        val plan = transaction.prepare(origin.archive(child, parent, unaffected))
        val childReview = plan.items[0]
        val parentReview = plan.items[1]
        transaction.restore(plan)

        assertTrue(parentReview.remapped)
        assertArrayEquals(oldBytes, target.file(old).readBytes())
        assertEquals(saved, target.labels.entries().getValue(old.id).preferences)
        val imported = target.store.load(childReview.targetId)
        assertEquals(child.exchanges, imported.exchanges)
        assertEquals(child.origin?.copy(linkedParentId = parentReview.targetId), imported.origin)
        assertEquals(parent.modelSha256, target.store.load(parentReview.targetId).modelSha256)
        assertEquals(source, target.labels.entries().getValue(childReview.targetId).preferences)
        assertEquals(source, target.labels.entries().getValue(parentReview.targetId).preferences)
        assertArrayEquals(origin.file(unaffected).readBytes(), target.file(unaffected).readBytes())
        assertEquals(4, target.store.list().size)
        assertEquals(LocalHistoryRestoreOutcome.NONE, target.transaction().recover())
        assertThrows(LocalHistoryBackupException::class.java) { transaction.restore(plan) }
        assertEquals(4, target.store.list().size)
    }

    @Test fun detachedProvenanceCannotBecomeClickableAfterABackupRoundTrip() {
        val original = Library(temporary.newFolder())
        val target = Library(temporary.newFolder())
        val final = Library(temporary.newFolder())
        val parent = chat(1)
        original.store.saveNew(parent)
        val child = original.store.createBranch(parent, 0, LocalBranchKind.FORK, 108)
        target.store.saveNew(parent)

        val restore = target.transaction()
        restore.restore(restore.prepare(original.archive(child)))
        val detached = target.store.load(child.id)
        assertEquals(child.origin?.copy(linkedParentId = null), detached.origin)
        assertNull(detached.origin?.linkedParentId)

        val again = final.transaction()
        val plan = again.prepare(target.archive(detached, parent))
        assertNull(plan.items.first().targetParentId)
        again.restore(plan)
        assertEquals(detached, final.store.load(child.id))
        assertEquals(parent, final.store.load(parent.id))
    }

    @Test fun aPublishFailureRollsBackExactNewChatsAndKeepsTheOriginalIndexBytes() {
        val origin = Library(temporary.newFolder())
        val target = Library(temporary.newFolder())
        val old = chat(1)
        target.remember(old, saved)
        val originals = target.file(old).readBytes()
        val index = target.labels.index().readBytes()
        val first = chat(2)
        val second = chat(3)
        origin.remember(first, source)
        origin.remember(second, source)
        val restore = target.transaction(publish = { staged, destination ->
            if (destination.name == target.file(second).name) throw IOException("fixture")
            LocalHistoryRestoreTransaction.publishNewChat(staged, destination)
        })
        val plan = restore.prepare(origin.archive(first, second))

        assertThrows(IOException::class.java) { restore.restore(plan) }
        assertEquals(listOf(old.id), target.store.list().map { it.id })
        assertArrayEquals(originals, target.file(old).readBytes())
        assertArrayEquals(index, target.labels.index().readBytes())
        assertEquals(LocalHistoryRestoreOutcome.NONE, target.transaction().recover())
    }

    @Test fun coldRecoveryRevertsInstalledLabelsToExactBytesWithoutReplayingTheArchive() {
        val origin = Library(temporary.newFolder())
        val target = Library(temporary.newFolder())
        val old = chat(1)
        target.remember(old, saved)
        val original = target.file(old).readBytes()
        val index = target.labels.index().apply {
            // Valid bytes that differ from the current encoder's formatting.
            appendText("\n")
        }.readBytes()
        val imported = chat(2)
        origin.remember(imported, source)
        val restore = target.transaction(sync = { directory ->
            if (directory == target.store.directory && File(target.journal, "pending").isFile &&
                target.labels.entries().containsKey(imported.id)
            ) throw SimulatedProcessStop()
        })
        val plan = restore.prepare(origin.archive(imported))

        assertThrows(SimulatedProcessStop::class.java) { restore.restore(plan) }
        assertTrue(target.file(imported).exists())
        val recovered = target.transaction()
        assertEquals(LocalHistoryRestoreOutcome.ROLLED_BACK, recovered.recover())
        assertFalse(target.file(imported).exists())
        assertArrayEquals(original, target.file(old).readBytes())
        assertArrayEquals(index, target.labels.index().readBytes())
        assertEquals(LocalHistoryRestoreOutcome.NONE, recovered.recover())
    }

    @Test fun coldRecoveryKeepsAnAlreadyCommittedImportAndDoesNotDuplicateIt() {
        val origin = Library(temporary.newFolder())
        val target = Library(temporary.newFolder())
        val imported = chat(2)
        origin.remember(imported, source)
        val restore = target.transaction(sync = { directory ->
            if (directory == target.journal && File(target.journal, "committed").isFile) throw SimulatedProcessStop()
        })
        val plan = restore.prepare(origin.archive(imported))

        assertThrows(SimulatedProcessStop::class.java) { restore.restore(plan) }
        val recovered = target.transaction()
        assertEquals(LocalHistoryRestoreOutcome.COMMITTED, recovered.recover())
        assertEquals(imported, target.store.load(imported.id))
        assertEquals(source, target.labels.entries().getValue(imported.id).preferences)
        assertEquals(LocalHistoryRestoreOutcome.NONE, recovered.recover())
        assertThrows(LocalHistoryBackupException::class.java) { recovered.restore(plan) }
        assertEquals(1, target.store.list().size)
    }

    @Test fun unknownLiveBytesStopRecoveryBeforeItDeletesAnyChatOrLabels() {
        val origin = Library(temporary.newFolder())
        val target = Library(temporary.newFolder())
        val first = chat(1)
        val second = chat(2)
        origin.remember(first, source)
        origin.remember(second, source)
        val restore = target.transaction(sync = { directory ->
            if (directory == target.store.directory && File(target.journal, "pending").isFile) {
                throw SimulatedProcessStop()
            }
        })
        val plan = restore.prepare(origin.archive(first, second))
        assertThrows(SimulatedProcessStop::class.java) { restore.restore(plan) }
        val firstBytes = target.file(first).readBytes()
        val index = target.labels.index().readBytes()
        target.store.save(second.copy(updatedAt = 203))
        val unknown = target.file(second).readBytes()

        assertThrows(LocalHistoryRestoreRecoveryException::class.java) { target.transaction().recover() }
        assertArrayEquals(firstBytes, target.file(first).readBytes())
        assertArrayEquals(unknown, target.file(second).readBytes())
        assertArrayEquals(index, target.labels.index().readBytes())
        assertTrue(File(target.journal, "pending").isFile)
    }

    @Test fun aDestinationThatAppearsAfterStagingIsNeverReplaced() {
        val origin = Library(temporary.newFolder())
        val target = Library(temporary.newFolder())
        val imported = chat(2)
        val unknown = imported.copy(updatedAt = 310)
        origin.remember(imported, source)
        val restore = target.transaction(publish = { staged, destination ->
            target.store.saveNew(unknown)
            LocalHistoryRestoreTransaction.publishNewChat(staged, destination)
        })
        val plan = restore.prepare(origin.archive(imported))

        assertThrows(LocalHistoryRestoreRecoveryException::class.java) { restore.restore(plan) }
        assertEquals(unknown, target.store.load(imported.id))
        assertFalse(target.labels.index().exists())
        assertTrue(File(target.journal, "pending").isFile)
    }

    @Test fun cancellingAfterStagingPublishesNothingAndRemovesStagedData() {
        val origin = Library(temporary.newFolder())
        val target = Library(temporary.newFolder())
        val imported = chat(1)
        origin.remember(imported, source)
        val restore = target.transaction()
        val plan = restore.prepare(origin.archive(imported))

        assertThrows(CancellationException::class.java) {
            restore.restore(plan) {
                if (File(target.journal, "prepared").isFile) throw CancellationException()
            }
        }
        assertTrue(target.store.list().isEmpty())
        assertFalse(target.labels.index().exists())
        assertTrue(target.journal.listFiles()!!.isEmpty())
    }

    @Test fun aChangedLibraryRequiresFreshReviewBeforeAnyJournalOrChatIsWritten() {
        val origin = Library(temporary.newFolder())
        val target = Library(temporary.newFolder())
        val existing = chat(1)
        val imported = chat(2)
        target.remember(existing, saved)
        origin.remember(imported, source)
        val restore = target.transaction()
        val plan = restore.prepare(origin.archive(imported))
        target.labels.update(existing.id, model, target.labels.entries()[existing.id], LocalHistoryPreferences("Renamed"))
        val afterRename = target.labels.index().readBytes()

        val denied = assertThrows(LocalHistoryBackupException::class.java) { restore.restore(plan) }
        assertEquals(LocalHistoryBackupProblem.CHANGED, denied.problem)
        assertFalse(target.journal.exists())
        assertFalse(target.file(imported).exists())
        assertArrayEquals(afterRename, target.labels.index().readBytes())
    }
}
