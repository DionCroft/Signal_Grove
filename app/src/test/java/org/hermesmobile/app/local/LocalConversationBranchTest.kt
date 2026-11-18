package org.hermesmobile.app.local

import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalConversationBranchTest {
    @get:Rule val temp = TemporaryFolder()
    private val hash = "a".repeat(64)
    private fun source() = LocalConversation(modelSha256 = hash, updatedAt = 100, exchanges = listOf(
        LocalExchange("First", "First reply", LocalReplyState.COMPLETE),
        LocalExchange("Second 你好 🌱", "Second partial", LocalReplyState.CANCELLED),
        LocalExchange("Third", "Third reply", LocalReplyState.COMPLETE)))
    private fun bytes(root: File, value: LocalConversation) = File(root, "${value.id}.json").readBytes()
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test fun forkCopiesOnlyTheChosenPrefixAndPreservesTheParentBytes() {
        val root = temp.newFolder(); val store = LocalConversationStore(root); val parent = source(); store.save(parent)
        val before = bytes(root, parent)
        val child = store.createBranch(parent, 0, LocalBranchKind.FORK, 200)
        assertNotEquals(parent.id, child.id); assertEquals(hash, child.modelSha256)
        assertEquals(parent.exchanges.take(1), child.exchanges)
        assertEquals(LocalBranchOrigin(parent.id, 100, 0, LocalBranchKind.FORK, digest(before)), child.origin)
        assertArrayEquals(before, bytes(root, parent)); assertEquals(child, LocalConversationStore(root).load(child.id))
        assertEquals(child.origin, store.list().first().origin)
    }
    @Test fun regenerationStartsOnePendingTurnAndOmitsTheOldReplyAndLaterTurns() {
        val root = temp.newFolder(); val store = LocalConversationStore(root); val parent = source(); store.save(parent)
        val before = bytes(root, parent)
        val child = store.createBranch(parent, 1, LocalBranchKind.REGENERATE, 200)
        assertEquals(listOf(parent.exchanges[0], LocalExchange(parent.exchanges[1].question, state = LocalReplyState.GENERATING)), child.exchanges)
        assertArrayEquals(before, bytes(root, parent)); assertEquals(LocalBranchKind.REGENERATE, child.origin!!.kind)
    }
    @Test fun editPreservesExactReviewedTextAndDoesNotOverwriteAnEarlierReply() {
        val root = temp.newFolder(); val store = LocalConversationStore(root); val parent = source(); store.save(parent)
        val before = bytes(root, parent); val question = "  Revised\n你好 🌱  "
        val child = store.createBranch(parent, 1, LocalBranchKind.EDIT, 200, question)
        assertEquals(parent.exchanges.take(1) + LocalExchange(question, state = LocalReplyState.GENERATING), child.exchanges)
        assertArrayEquals(before, bytes(root, parent)); assertEquals(child, store.load(child.id))
    }
    @Test fun rejectedBranchRequestsPublishNothingAndPreserveEveryExistingFile() {
        val root = temp.newFolder(); val store = LocalConversationStore(root); val parent = source(); store.save(parent)
        val before = bytes(root, parent)
        val attempts = listOf<() -> Unit>(
            { store.createBranch(parent, -1, LocalBranchKind.FORK, 200) },
            { store.createBranch(parent, 3, LocalBranchKind.FORK, 200) },
            { store.createBranch(parent, 1, LocalBranchKind.FORK, 200) },
            { store.createBranch(parent, 0, LocalBranchKind.FORK, -1) },
            { store.createBranch(parent, 0, LocalBranchKind.FORK, 200, "unexpected") },
            { store.createBranch(parent, 0, LocalBranchKind.REGENERATE, 200, "unexpected") },
            { store.createBranch(parent, 0, LocalBranchKind.EDIT, 200) },
            { store.createBranch(parent, 0, LocalBranchKind.EDIT, 200, " ") },
            { store.createBranch(parent, 0, LocalBranchKind.EDIT, 200, "x".repeat(8193)) })
        attempts.forEach { attempt ->
            assertThrows(IllegalArgumentException::class.java) { attempt() }
            assertArrayEquals(before, bytes(root, parent)); assertEquals(1, root.listFiles()!!.size)
        }
    }
    @Test fun staleSelectionCannotGenerateFromOrReplaceChangedHistory() {
        val root = temp.newFolder(); val store = LocalConversationStore(root); val old = source(); store.save(old)
        val current = old.copy(updatedAt = 101, exchanges = old.exchanges + LocalExchange("New", "Reply", LocalReplyState.COMPLETE))
        store.save(current); val before = bytes(root, current)
        assertThrows(IllegalArgumentException::class.java) { store.createBranch(old, 0, LocalBranchKind.REGENERATE, 200) }
        assertArrayEquals(before, bytes(root, current)); assertEquals(1, root.listFiles()!!.size)
    }
    @Test fun noBranchCanReadAnInFlightGenerationAsFinishedHistory() {
        val root = temp.newFolder(); val store = LocalConversationStore(root)
        val parent = source().copy(exchanges = source().exchanges + LocalExchange("Working", state = LocalReplyState.GENERATING)); store.save(parent)
        LocalBranchKind.entries.forEach { kind ->
            assertThrows(IllegalArgumentException::class.java) { store.createBranch(parent, 0, kind, 200, if (kind == LocalBranchKind.EDIT) "Edit" else null) }
        }
        assertEquals(parent, store.load(parent.id)); assertEquals(1, root.listFiles()!!.size)
    }
    @Test fun coldRecoveryPreservesBranchProvenanceAndNeverReplaysItsPendingTurn() {
        val root = temp.newFolder(); val store = LocalConversationStore(root); val parent = source(); store.save(parent)
        val before = bytes(root, parent); val child = store.createBranch(parent, 0, LocalBranchKind.REGENERATE, 200)
        val restarted = LocalConversationStore(root); restarted.recoverInterrupted()
        assertEquals(child.copy(exchanges = listOf(child.exchanges.single().copy(state = LocalReplyState.INTERRUPTED))), restarted.load(child.id))
        assertArrayEquals(before, bytes(root, parent)); assertEquals(2, restarted.list().size)
    }
    @Test fun branchRemainsReadableAfterTheUserDeletesItsParent() {
        val root = temp.newFolder(); val store = LocalConversationStore(root); val parent = source(); store.save(parent)
        val child = store.createBranch(parent, 0, LocalBranchKind.FORK, 200); store.delete(parent.id)
        assertEquals(child, LocalConversationStore(root).load(child.id)); assertEquals(child.origin, store.list().single().origin)
    }
    @Test fun fullHistoryAndIdCollisionsCannotOverwriteAnExistingChat() {
        val root = temp.newFolder(); val store = LocalConversationStore(root); val parent = source(); store.save(parent)
        repeat(49) { store.save(source()) }; val before = root.listFiles()!!.associate { it.name to digest(it.readBytes()) }
        assertThrows(LocalHistoryException::class.java) { store.createBranch(parent, 0, LocalBranchKind.FORK, 200) }
        assertThrows(IllegalArgumentException::class.java) { store.saveNew(parent.copy(updatedAt = 999)) }
        assertEquals(before, root.listFiles()!!.associate { it.name to digest(it.readBytes()) })
    }
    @Test fun legacyReadsAreReadOnlyAndDoNotInventBranchOrigins() {
        val root = temp.newFolder(); val store = LocalConversationStore(root); val parent = source(); store.save(parent)
        val before = bytes(root, parent); assertTrue(String(before, Charsets.UTF_8).contains("\"schema\":1"))
        val restarted = LocalConversationStore(root); assertNull(restarted.load(parent.id).origin); assertNull(restarted.list().single().origin)
        restarted.recoverInterrupted(); assertArrayEquals(before, bytes(root, parent))
    }
    @Test fun unknownSchemasFieldsAndMalformedOriginsArePreservedForReview() {
        val root = temp.newFolder(); val store = LocalConversationStore(root); val parent = source(); store.save(parent)
        val child = store.createBranch(parent, 0, LocalBranchKind.FORK, 200); val valid = String(bytes(root, child), Charsets.UTF_8)
        val file = File(root, "${child.id}.json")
        val invalid = listOf(valid.replace("\"schema\":2", "\"schema\":4"),
            valid.replace("\"schema\":2", "\"schema\":\"2\""), valid.replace("\"origin\":", "\"future\":true,\"origin\":"),
            valid.replace("\"exchange\":0", "\"exchange\":30"), valid.replace("\"exchange\":0", "\"exchange\":\"0\""),
            valid.replace("\"parent\":\"${parent.id}\"", "\"parent\":\"../remote\""),
            valid.replace(digest(bytes(root, parent)), "bad"))
        invalid.forEach { content ->
            assertNotEquals(valid, content); file.writeText(content)
            assertThrows(LocalHistoryException::class.java) { store.load(child.id) }; assertEquals(content, file.readText())
        }
    }
    @Test fun boundedReadsHonourCancellationWithoutChangingAnyHistory() {
        val root = temp.newFolder(); val store = LocalConversationStore(root); val parent = source(); store.save(parent)
        val before = bytes(root, parent)
        assertThrows(CancellationException::class.java) { store.load(parent.id) { throw CancellationException("Stop") } }
        assertArrayEquals(before, bytes(root, parent)); assertEquals(1, root.listFiles()!!.size)
    }
}