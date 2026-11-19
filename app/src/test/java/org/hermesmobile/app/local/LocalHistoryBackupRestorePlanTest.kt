package org.hermesmobile.app.local

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

class LocalHistoryBackupRestorePlanTest {
    @get:Rule val temp = TemporaryFolder()
    private val model = "a".repeat(64)
    private val labels = LocalHistoryOrganizationEntry(model, LocalHistoryPreferences("Garden 🌱", true, true))
    private val source by lazy { LocalConversationStore(temp.newFolder()) }

    private fun original(number: Long) = LocalConversation(
        UUID(0, number).toString(), model, 100,
        listOf(LocalExchange("Quiet garden", "Existing answer", LocalReplyState.COMPLETE))
    )

    private fun archive(vararg selected: LocalConversation): LocalHistoryBackupArchive.Inspection {
        val rows = source.list().associateBy { it.id }
        val file = temp.newFile()
        file.outputStream().use { output ->
            LocalHistoryBackupArchive.write(selected.map { it.id }, output, { id ->
                source.backupRecord(rows.getValue(id).copy(organization = labels), labels)
            })
        }
        return LocalHistoryBackupArchive.inspect(file)
    }

    @Test fun collisionsGetSeparateIdsAndIncludedParentsFollowTheReviewedMapping() {
        val parent = original(1)
        source.saveNew(parent)
        val child = source.createBranch(parent, 0, LocalBranchKind.FORK, 101)
        val inspected = archive(child, parent)
        val target = LocalConversationStore(temp.newFolder())
        target.saveNew(parent.copy(modelSha256 = "b".repeat(64)))
        val before = target.list()
        val occupiedLabels = mapOf(child.id to labels)
        val candidates = listOf(child.id, parent.id, original(2).id, original(2).id, original(3).id).iterator()

        val review = LocalHistoryBackupRestorePlan.prepare(inspected, before, occupiedLabels, candidates::next)
        val (childReview, parentReview) = review.items
        assertEquals(original(2).id, childReview.targetId)
        assertEquals(original(3).id, parentReview.targetId)
        assertEquals(parentReview.targetId, childReview.targetParentId)
        assertTrue(childReview.remapped && parentReview.remapped)
        assertFalse(childReview.parentOutsideArchive)
        assertEquals(child.origin, childReview.source.origin)
        assertEquals(labels.preferences, childReview.source.labels)
        assertEquals(model, childReview.source.modelSha256)
        val restoredRecord = review.readOriginal(child.id)
        assertEquals(child, restoredRecord.conversation)
        assertArrayEquals(inspected.readRecord(child.id).transcriptHash(), restoredRecord.transcriptHash())
        assertEquals(before, target.list())
        review.requireCurrent(target.list(), occupiedLabels)
    }

    @Test fun aParentOutsideTheArchiveCannotAttachToAnyExistingConversation() {
        val parent = original(1)
        source.saveNew(parent)
        val child = source.createBranch(parent, 0, LocalBranchKind.FORK, 101)
        val inspected = archive(child)
        val target = LocalConversationStore(temp.newFolder())
        target.saveNew(parent)

        val item = LocalHistoryBackupRestorePlan.prepare(inspected, target.list(), emptyMap()).items.single()
        assertEquals(child.id, item.targetId)
        assertEquals(parent.id, item.source.origin?.parentId)
        assertTrue(item.parentOutsideArchive)
        assertFalse(item.remapped)
        assertNull(item.targetParentId)
    }

    @Test fun aGeneratedIdCannotOccupyAnOmittedParentAddress() {
        val parent = original(1)
        source.saveNew(parent)
        val child = source.createBranch(parent, 0, LocalBranchKind.FORK, 101)
        val target = LocalConversationStore(temp.newFolder())
        target.saveNew(child)
        val candidates = listOf(parent.id, original(3).id).iterator()

        val result = LocalHistoryBackupRestorePlan.prepare(archive(child), target.list(), emptyMap(), candidates::next)
        assertEquals(original(3).id, result.items.single().targetId)
        assertTrue(result.items.single().parentOutsideArchive)
    }

    @Test fun bothConversationAndLabelCapacityAreCheckedBeforeAllocatingIds() {
        val fixture = original(1)
        source.saveNew(fixture)
        val inspected = archive(fixture)
        val ids = List(LocalConversationStore.MAX_CHATS) { original(it + 10L).id }
        val full = ids.map { LocalConversationSummary(it, model, 100, "Existing chat", LocalReplyState.COMPLETE) }
        val allocate: () -> String = { error("capacity must be checked first") }

        val fullChats = assertThrows(LocalHistoryBackupException::class.java) {
            LocalHistoryBackupRestorePlan.prepare(inspected, full, emptyMap(), allocate)
        }
        val fullLabels = assertThrows(LocalHistoryBackupException::class.java) {
            LocalHistoryBackupRestorePlan.prepare(inspected, emptyList(), ids.associateWith { labels }, allocate)
        }
        assertEquals(LocalHistoryBackupProblem.CAPACITY, fullChats.problem)
        assertEquals(LocalHistoryBackupProblem.CAPACITY, fullLabels.problem)
    }

    @Test fun changedDestinationsAndCancelledReviewsAreRefused() {
        val fixture = original(1)
        source.saveNew(fixture)
        val inspected = archive(fixture)
        val before = listOf(LocalConversationSummary(original(2).id, model, 100, "Existing", LocalReplyState.COMPLETE))
        val existingLabels = mutableMapOf(before.single().id to labels)
        val review = LocalHistoryBackupRestorePlan.prepare(inspected, before, existingLabels)
        review.requireCurrent(before, existingLabels)

        existingLabels.clear()
        val changedLabels = assertThrows(LocalHistoryBackupException::class.java) {
            review.requireCurrent(before, existingLabels)
        }
        val changedChat = assertThrows(LocalHistoryBackupException::class.java) {
            review.requireCurrent(listOf(before.single().copy(updatedAt = 101)), mapOf(before.single().id to labels))
        }
        assertEquals(LocalHistoryBackupProblem.CHANGED, changedLabels.problem)
        assertEquals(LocalHistoryBackupProblem.CHANGED, changedChat.problem)
        assertThrows(CancellationException::class.java) {
            LocalHistoryBackupRestorePlan.prepare(inspected, before, existingLabels, checkpoint = { throw CancellationException() })
        }
    }

    @Test fun repeatedAllocationConflictsStopWithoutAnUnboundedRetry() {
        val fixture = original(1)
        source.saveNew(fixture)
        val inspected = archive(fixture)
        var calls = 0

        val failure = assertThrows(LocalHistoryBackupException::class.java) {
            LocalHistoryBackupRestorePlan.prepare(inspected, source.list(), emptyMap(), {
                calls++
                fixture.id
            })
        }
        assertEquals(64, calls)
        assertEquals(LocalHistoryBackupProblem.IDENTIFIER_UNAVAILABLE, failure.problem)
    }
}
