package org.hermesmobile.app

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import org.hermesmobile.app.local.*
import org.junit.Assert.*
import org.junit.Test

/** Real Android private filesystem; no models, server, credentials or pre-existing histories. */
class LocalConversationBranchStorageTest {
    private fun root() = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
        "branch-storage-${UUID.randomUUID()}").also { check(it.mkdir()) }
    private fun original() = LocalConversation(modelSha256 = "a".repeat(64), updatedAt = 100,
        exchanges = listOf(LocalExchange("Original 你好 🌱", "Preserved reply", LocalReplyState.COMPLETE)))
    @Test fun legacyAndBranchFilesRoundTripWithoutChangingParentBytes() {
        val root = root(); val store = LocalConversationStore(root); val parent = original(); store.save(parent)
        val file = File(root, "${parent.id}.json"); val bytes = file.readBytes()
        val child = store.createBranch(parent, 0, LocalBranchKind.FORK, 200)
        assertArrayEquals(bytes, file.readBytes()); assertEquals(child, LocalConversationStore(root).load(child.id))
        assertEquals(parent.exchanges, child.exchanges); assertNull(store.load(parent.id).origin)
        assertEquals(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, child.origin!!.sourceSha256)
        assertEquals(2, root.listFiles()!!.size)
    }
    @Test fun interruptedEditedChildRecoversWithoutRewritingOrReplayingParent() {
        val root = root(); val store = LocalConversationStore(root); val parent = original(); store.save(parent)
        val bytes = File(root, "${parent.id}.json").readBytes()
        val child = store.createBranch(parent, 0, LocalBranchKind.EDIT, 200, "Explicit edit")
        assertEquals(LocalReplyState.GENERATING, store.load(child.id).exchanges.single().state)
        val restarted = LocalConversationStore(root); restarted.recoverInterrupted()
        val reopened = restarted.load(child.id)
        assertEquals(LocalReplyState.INTERRUPTED, reopened.exchanges.single().state)
        assertEquals(child.origin, reopened.origin); assertEquals("Explicit edit", reopened.exchanges.single().question)
        assertArrayEquals(bytes, File(root, "${parent.id}.json").readBytes()); assertEquals(2, restarted.list().size)
    }
    @Test fun deletedParentDoesNotBreakTheIndependentChildOrCauseReconstruction() {
        val root = root(); val store = LocalConversationStore(root); val parent = original(); store.save(parent)
        val child = store.createBranch(parent, 0, LocalBranchKind.FORK, 200); store.delete(parent.id)
        val reopened = LocalConversationStore(root); assertEquals(child, reopened.load(child.id))
        assertEquals(listOf(child.id), reopened.list().map { it.id }); assertEquals(1, root.listFiles()!!.size)
        assertFalse(File(root, "${parent.id}.json").exists())
    }
}