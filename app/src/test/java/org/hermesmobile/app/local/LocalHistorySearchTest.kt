package org.hermesmobile.app.local

import java.io.File
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalHistorySearchTest {
    @get:Rule val temp = TemporaryFolder()
    private val model = "a".repeat(64)
    private fun chat(question: String, reply: String, updated: Long = 1, hash: String = model,
        state: LocalReplyState = LocalReplyState.COMPLETE, id: String = java.util.UUID.randomUUID().toString()) =
        LocalConversation(id, hash, updated, listOf(LocalExchange(question, reply, state)))
    private fun fixture(vararg values: LocalConversation): Pair<LocalConversationStore, File> {
        val root=temp.newFolder(); val store=LocalConversationStore(root);values.forEach(store::saveNew);return store to root
    }
    private fun bytes(root: File) = root.listFiles()!!.associate {it.name to it.readBytes().toList()}
    @Test fun findsQuestionAndReplyWithLiteralCaseInsensitiveText() {
        val question=chat("Quiet Garden", "Water the plants")
        val reply=chat("A different title", "THE GARDEN is peaceful",2)
        val irrelevant=chat("Forest", "Tall trees",3)
        val (store,root)=fixture(question,reply,irrelevant);val before=bytes(root)
        assertEquals(listOf(reply.id,question.id),LocalHistorySearch(store).find(LocalHistoryQuery("garden")).map {it.id})
        assertEquals(before,bytes(root))
    }
    @Test fun matchesChineseEmojiAndLiteralRegexCharacters() {
        val target=chat("你好 🌱", "Use [a.*] as literal text")
        val (store,_)=fixture(target,chat("Other", "aaaa"));val search=LocalHistorySearch(store)
        for (term in listOf("你好","🌱","[a.*]")) assertEquals(listOf(target.id),search.find(LocalHistoryQuery(term)).map {it.id})
        assertTrue(search.find(LocalHistoryQuery("a.+")).isEmpty())
    }
    @Test fun matchesTheVisibleDerivedTitleAfterControlCharactersAreRemoved() {
        val target=chat("Quiet\nGarden","reply");val (store,_)=fixture(target)
        assertEquals(listOf(target.id),LocalHistorySearch(store).find(LocalHistoryQuery("QuietGarden")).map {it.id})
    }
    @Test fun modelAndDateFiltersUseAnInclusiveStartAndExclusiveEnd() {
        val first=chat("same","text",10);val last=chat("same","text",20)
        val another=chat("same","text",10,"b".repeat(64))
        val (store,_)=fixture(first,last,another);val search=LocalHistorySearch(store)
        assertEquals(listOf(first.id),search.find(LocalHistoryQuery("same",model,10,20)).map {it.id})
        assertEquals(listOf(last.id),search.find(LocalHistoryQuery("",model,20,21)).map {it.id})
    }
    @Test fun emptyOrWhitespaceSearchReadsAllWithoutAnyModelFile() {
        val a=chat("A","one",1);val b=chat("B","two",2);val (store,root)=fixture(a,b)
        assertTrue(root.listFiles()!!.all {it.extension=="json"})
        assertEquals(listOf(b.id,a.id),LocalHistorySearch(store).find(LocalHistoryQuery(" \n ")).map {it.id})
    }
    @Test fun equalTimestampsHaveStableIdentifierOrder() {
        val a=chat("A","reply",id="00000000-0000-0000-0000-000000000001")
        val b=chat("B","reply",id="00000000-0000-0000-0000-000000000002")
        val (store,_)=fixture(b,a)
        assertEquals(listOf(a.id,b.id),LocalHistorySearch(store).find(LocalHistoryQuery()).map {it.id})
    }
    @Test fun searchesTheEndOfALongReplyAndEveryExchange() {
        val original=chat("First","A".repeat(128*1024-16)+" tail-needle")
        val later=original.copy(id=java.util.UUID.randomUUID().toString(),exchanges=listOf(
            LocalExchange("Start","reply",LocalReplyState.COMPLETE),LocalExchange("Later question needle","later",LocalReplyState.COMPLETE)))
        val (store,_)=fixture(original,later);val search=LocalHistorySearch(store)
        assertEquals(listOf(original.id),search.find(LocalHistoryQuery("tail-needle")).map {it.id})
        assertEquals(listOf(later.id),search.find(LocalHistoryQuery("question needle")).map {it.id})
    }
    @Test fun cancellationPropagatesAndPreservesAllOriginalFiles() {
        val (store,root)=fixture(chat("One","Garden"),chat("Two","Forest"));val before=bytes(root);var reads=0
        try {LocalHistorySearch(store).find(LocalHistoryQuery("garden")) {if(++reads==5) throw CancellationException("stop")};fail("Expected cancellation")}
        catch (_: CancellationException) {}
        assertEquals(5,reads);assertEquals(before,bytes(root))
    }
    @Test fun malformedHistoryFailsWithoutPublishingAPartialResultOrChangingFiles() {
        val bad=chat("Broken","reply");val (store,root)=fixture(chat("Good","Garden"),bad)
        File(root,"${bad.id}.json").writeText("{broken")
        val before=bytes(root)
        try {LocalHistorySearch(store).find(LocalHistoryQuery("Garden"));fail("Expected history failure")}
        catch (_: LocalHistoryException) {}
        assertEquals(before,bytes(root))
    }
    @Test fun readingInterruptedOrGeneratingRepliesNeverChangesTheirState() {
        val a=chat("Question","Partial garden",state=LocalReplyState.INTERRUPTED)
        val b=chat("Question","Partial forest",state=LocalReplyState.GENERATING)
        val (store,root)=fixture(a,b);val before=bytes(root)
        assertEquals(2,LocalHistorySearch(store).find(LocalHistoryQuery("Partial")).size)
        assertEquals(before,bytes(root));assertEquals(b,store.load(b.id))
    }
    @Test fun rejectsOversizedQueriesInvalidModelsAndInvalidTimeWindows() {
        val invalid=listOf<()->Unit>({LocalHistoryQuery("x".repeat(1025))},{LocalHistoryQuery("你".repeat(342))},
            {LocalHistoryQuery("nul\u0000")},{LocalHistoryQuery("\uD800")},{LocalHistoryQuery("\uDC00")},{LocalHistoryQuery(modelSha256="../model")},
            {LocalHistoryQuery(fromInclusive=-1)},{LocalHistoryQuery(untilExclusive=-1)},
            {LocalHistoryQuery(fromInclusive=10,untilExclusive=10)},{LocalHistoryQuery(fromInclusive=11,untilExclusive=10)})
        invalid.forEach {f->try {f();fail("Expected invalid filter rejection")} catch (_: IllegalArgumentException) {}}
    }
}
