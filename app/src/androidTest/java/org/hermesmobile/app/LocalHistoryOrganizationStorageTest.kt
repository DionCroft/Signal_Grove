package org.hermesmobile.app

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CancellationException
import org.hermesmobile.app.local.*
import org.junit.Assert.*
import org.junit.Test

/** Real Android private QA directories; no existing user histories, models, network or credentials. */
class LocalHistoryOrganizationStorageTest {
    private fun root()=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"organization-storage-${UUID.randomUUID()}").also {check(it.mkdir())}
    private fun parent()=LocalConversation(modelSha256="a".repeat(64),updatedAt=1,
        exchanges=listOf(LocalExchange("Original garden question","Original reply",LocalReplyState.COMPLETE)))
    @Test fun androidOrganizationRoundTripsAndSearchesWithoutChangingTranscriptOrBranchHashes() {
        val root=root();val history=File(root,"chats");val store=LocalConversationStore(history);val chat=parent();store.saveNew(chat)
        val original=File(history,"${chat.id}.json").readBytes();val labels=LocalHistoryOrganization(File(root,"organization"),store)
        val entry=labels.update(chat.id,chat.modelSha256,null,LocalHistoryPreferences("花园 🌱",pinned=true,archived=true))
        val reloaded=LocalHistoryOrganization(File(root,"organization"),store);assertEquals(entry,reloaded.entries()[chat.id])
        assertTrue(LocalHistorySearch(store,reloaded).find(LocalHistoryQuery()).isEmpty())
        assertEquals(listOf(chat.id),LocalHistorySearch(store,reloaded).find(LocalHistoryQuery("花园",view=LocalHistoryView.ARCHIVED)).map {it.id})
        val child=store.createBranch(chat,0,LocalBranchKind.FORK,2)
        assertArrayEquals(original,File(history,"${chat.id}.json").readBytes())
        assertEquals(MessageDigest.getInstance("SHA-256").digest(original).joinToString("") {"%02x".format(it)},child.origin!!.sourceSha256)
        assertNull(reloaded.history().single {it.id==child.id}.organization);assertEquals(chat,store.load(chat.id))
    }
    @Test fun androidCancellationBeforeMetadataPublicationKeepsThePriorIndexAndAnyOlderPartial() {
        val root=root();val store=LocalConversationStore(File(root,"chats"));val chat=parent();store.saveNew(chat)
        val metadata=File(root,"organization");val labels=LocalHistoryOrganization(metadata,store)
        val entry=labels.update(chat.id,chat.modelSha256,null,LocalHistoryPreferences("Before"))
        val file=File(metadata,"index.json");val before=file.readBytes();val old=File(metadata,"index.previous.part").apply {writeText("Older interrupted evidence")}
        var stopped=false
        try {labels.update(chat.id,chat.modelSha256,entry,LocalHistoryPreferences("After",archived=true)) {
            if(metadata.listFiles()!!.any {it.extension=="part"&&it!=old}) {stopped=true;throw CancellationException("stop")}
        };fail("Expected cancellation")} catch (_:CancellationException) {}
        assertTrue(stopped);assertArrayEquals(before,file.readBytes());assertEquals(entry,labels.entries()[chat.id])
        assertEquals("Older interrupted evidence",old.readText());assertEquals(setOf("index.json",old.name),metadata.listFiles()!!.map {it.name}.toSet())
        assertEquals(chat,store.load(chat.id))
    }
}
