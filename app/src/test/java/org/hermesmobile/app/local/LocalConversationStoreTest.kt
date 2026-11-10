package org.hermesmobile.app.local

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalConversationStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private fun conversation(state: LocalReplyState = LocalReplyState.COMPLETE) = LocalConversation(modelSha256 = "a".repeat(64), updatedAt = 100,
        exchanges = listOf(LocalExchange("Hello 你好 🌱", "Local reply", state)))
    @Test fun savesUnicodeAndModelIdentityInIndependentFiles() {
        val root = temp.newFolder(); val store = LocalConversationStore(root); val value = conversation()
        store.save(value); assertEquals(value, store.load(value.id)); assertEquals(listOf(value.id), store.list().map { it.id })
        assertEquals("Hello 你好 🌱", store.list().single().title)
        assertEquals(listOf(value.id + ".json"), root.listFiles()!!.map { it.name })
    }
    @Test fun processInterruptionIsDurableAndNeverReplayed() {
        val root = temp.newFolder(); val value = conversation(LocalReplyState.GENERATING)
        LocalConversationStore(root).save(value)
        val nextProcess = LocalConversationStore(root); nextProcess.recoverInterrupted()
        val reopened = nextProcess.load(value.id)
        assertEquals(LocalReplyState.INTERRUPTED, reopened.exchanges.single().state)
        assertEquals("Local reply", reopened.exchanges.single().reply)
        assertEquals(reopened, LocalConversationStore(root).load(value.id))
        assertFalse(File(root, "${value.id}.json").readText().contains("GENERATING"))
    }
    @Test fun ordinaryReadsDoNotRelabelAnActiveReply() {
        val store = LocalConversationStore(temp.newFolder()); val value = conversation(LocalReplyState.GENERATING); store.save(value)
        assertEquals(value, store.load(value.id)); assertEquals(LocalReplyState.GENERATING, store.list().single().lastState)
    }
    @Test fun allExplicitTerminalStatesSurviveRecreation() {
        val store = LocalConversationStore(temp.newFolder())
        LocalReplyState.entries.filter { it != LocalReplyState.GENERATING }.forEach { state ->
            val value = conversation(state); store.save(value); assertEquals(state, store.load(value.id).exchanges.single().state)
        }
    }
    @Test fun invalidAndOversizedRecordsDoNotReplaceValidHistory() {
        val store = LocalConversationStore(temp.newFolder()); val value = conversation(); store.save(value)
        for (bad in listOf(value.copy(modelSha256 = "../remote"), value.copy(exchanges = List(31) { value.exchanges[0] }),
            value.copy(exchanges = listOf(LocalExchange("a".repeat(8193), state = LocalReplyState.GENERATING))),
            value.copy(exchanges = listOf(value.exchanges[0].copy(state = LocalReplyState.GENERATING), value.exchanges[0])))) {
            assertThrows(IllegalArgumentException::class.java) { store.save(bad) }
            assertEquals(value, store.load(value.id))
        }
    }
    @Test fun corruptHistoryIsAnErrorAndIsPreserved() {
        val root = temp.newFolder(); val store = LocalConversationStore(root); val value = conversation(); store.save(value)
        val file = File(root, "${value.id}.json"); file.writeText("broken")
        assertThrows(LocalHistoryException::class.java) { store.load(value.id) }
        assertEquals("broken", file.readText()); store.delete(value.id); assertFalse(file.exists())
    }
    @Test fun deletionCannotAddressHermesOrOtherFiles() {
        val root = temp.newFolder(); val store = LocalConversationStore(root); val other = temp.newFile().also { it.writeText("preserve") }
        assertThrows(IllegalArgumentException::class.java) { store.delete("../" + other.name) }; assertEquals("preserve", other.readText())
    }
    @Test fun partialsAreIgnoredAndHistoryIsSorted() {
        val root = temp.newFolder(); val store = LocalConversationStore(root); val old = conversation(); val recent = conversation().copy(updatedAt = 200)
        store.save(old); store.save(recent); File(root, "incomplete.part").writeText("partial")
        assertEquals(listOf(recent.id, old.id), store.list().map { it.id }); assertEquals("partial", File(root, "incomplete.part").readText())
    }
}
