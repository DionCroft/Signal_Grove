package org.hermesmobile.app.local

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalMarkdownExportTest {
    @get:Rule val temp = TemporaryFolder()
    private fun chat(question: String = "Garden 你好 🌱", reply: String = "A peaceful garden.", state: LocalReplyState = LocalReplyState.COMPLETE) =
        LocalConversation(modelSha256 = "a".repeat(64), updatedAt = 1234, exchanges = listOf(LocalExchange(question, reply, state)))
    private fun bytes(value: LocalMarkdownExport) = ByteArrayOutputStream().also {value.writeTo(it)}.toByteArray()
    private fun hash(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") {"%02x".format(it)}
    private fun rejected(reason: LocalMarkdownExportFailure, block: () -> Unit) {
        try {block(); fail("Expected export refusal")} catch (error: LocalMarkdownExportException) {assertEquals(reason, error.reason)}
    }
    private fun files(root: File) = root.listFiles()!!.associate {it.name to hash(it.readBytes())}
    private fun literalBodies(document: String): List<String> {
        val opener = Regex("(?m)^(`{3,}|~{3,})text\\n")
        val rows = mutableListOf<String>(); var next = 0
        while (true) {
            val match = opener.find(document, next) ?: break
            val start = match.range.last + 1
            val closing = "\n" + match.groupValues[1] + "\n"
            val end = document.indexOf(closing, start); assertTrue("Complete matching fence", end >= start)
            rows += document.substring(start, end); next = end + closing.length
        }
        return rows
    }
    @Test fun selectedLegacySnapshotIsLiteralAndDoesNotReadOrExportAnUnrelatedBrokenChat() {
        val root = temp.newFolder(); val store = LocalConversationStore(root); val value = chat(); store.saveNew(value)
        val other = File(root, UUID.randomUUID().toString() + ".json").apply {writeText("OTHER_CHAT_SENTINEL is deliberately invalid JSON")}
        val before = files(root); val source = File(root, "${value.id}.json").readBytes()
        val export = store.markdownExport(value, "Quiet orchard"); val out = bytes(export); val text = out.toString(Charsets.UTF_8)
        assertEquals(listOf("Quiet orchard", value.exchanges.single().question, value.exchanges.single().reply), literalBodies(text))
        assertEquals(hash(source), export.sourceSha256); assertEquals(hash(out), export.documentSha256)
        assertEquals(value.id, export.chatId); assertEquals(value.modelSha256, export.modelSha256); assertEquals(1, export.exchanges)
        assertEquals(out.size, export.byteCount); assertTrue(export.suggestedFilename.matches(Regex("SignalGrove-phone-chat-[a-f0-9]{8}\\.md")))
        assertFalse(text.contains("OTHER_CHAT_SENTINEL")); assertFalse(text.contains(other.name)); assertTrue(text.contains("Generation settings: not recorded"))
        assertEquals(before, files(root)); assertEquals(value, store.load(value.id))
    }
    @Test fun maliciousMarkdownHtmlLinksAndEveryShortFenceRemainInsideExactLiteralBlocks() {
        val payload = (3..80).joinToString("\n") {"`".repeat(it) + "\n" + "~".repeat(it)} +
            "\n# forged section\n<script>literal()</script>\n[x](javascript:literal)\n![image](https://invalid.example/a)\r\n"
        val value = chat(payload, payload); val store = LocalConversationStore(temp.newFolder()); store.saveNew(value)
        val title = payload.take(2048) + "\n<script>title()</script>"
        val document = bytes(store.markdownExport(value, title)).toString(Charsets.UTF_8)
        assertEquals(listOf(title, payload, payload), literalBodies(document))
        val lines = document.lines(); var fence: String? = null
        for (line in lines) {
            if (fence == null && Regex("(`{3,}|~{3,})text").matches(line)) fence = line.removeSuffix("text")
            else if (fence != null && line == fence) fence = null
            else if (fence == null) {assertFalse(line.contains("<script>")); assertFalse(line.contains("javascript:")); assertFalse(line.contains("https://invalid.example"))}
        }
        assertNull(fence)
    }
    @Test fun partialAndTerminalStatesAndRecordedSettingsStayExplicitWithoutInventedRuntime() {
        val runtime = LocalRuntimePolicy("b".repeat(64), LocalSamplingPolicy.GREEDY_MODEL_METADATA_V1)
        val values = LocalReplyState.entries.filter {it != LocalReplyState.GENERATING}
        val value = chat().copy(exchanges = values.mapIndexed {i, state -> LocalExchange("Question $i", "Reply $i", state,
            if(i == 0) LocalGenerationSettings(4096, 4, 384, runtime) else if(i == 1) LocalGenerationSettings(2048, 2, 64) else null)})
        val store = LocalConversationStore(temp.newFolder()); store.saveNew(value)
        val text = bytes(store.markdownExport(value)).toString(Charsets.UTF_8)
        values.forEach {assertTrue(text.contains("### Assistant (${it.name})"))}
        assertTrue(text.contains("context=4096; threads=4; output_limit=384")); assertTrue(text.contains("context=2048; threads=2; output_limit=64"))
        assertEquals(1, Regex("Runtime source SHA-256:").findAll(text).count()); assertTrue(text.contains(runtime.sourceSha256))
        assertEquals(values.size * 2, literalBodies(text).size)
    }
    @Test fun aPreparedDocumentKeepsItsReviewedBytesAfterTheStoredConversationChanges() {
        val root = temp.newFolder(); val store = LocalConversationStore(root); val value = chat(); store.saveNew(value)
        val export = store.markdownExport(value, "Reviewed title"); val original = bytes(export)
        val changed = value.copy(updatedAt = 2, exchanges = listOf(LocalExchange("New question", "New reply", LocalReplyState.COMPLETE)))
        store.save(changed); val before = files(root)
        assertArrayEquals(original, bytes(export)); assertEquals(before, files(root)); assertEquals(changed, store.load(value.id))
    }
    @Test fun aStaleQuestionTimestampOrModelCannotReplaceTheSelectedSnapshot() {
        val root = temp.newFolder(); val store = LocalConversationStore(root); val value = chat(); store.saveNew(value); val before = files(root)
        for (expected in listOf(value.copy(updatedAt = 99), value.copy(modelSha256 = "c".repeat(64)), value.copy(exchanges = listOf(LocalExchange("Different", "Reply", LocalReplyState.COMPLETE))))) {
            rejected(LocalMarkdownExportFailure.CHANGED) {store.markdownExport(expected)}
        }
        assertEquals(before, files(root))
    }
    @Test fun activeAndEmptyChatsAreRefusedWithoutRecoveryOrAHistoryWrite() {
        for(value in listOf(chat(state = LocalReplyState.GENERATING), chat().copy(exchanges = emptyList()))) {
            val root = temp.newFolder(); val store = LocalConversationStore(root); store.saveNew(value); val before = files(root)
            rejected(if(value.exchanges.isEmpty()) LocalMarkdownExportFailure.EMPTY else LocalMarkdownExportFailure.ACTIVE) {store.markdownExport(value)}
            assertEquals(before, files(root)); assertEquals(value, store.load(value.id))
        }
    }
    @Test fun corruptSelectedHistoryIsRefusedAndItsBytesArePreserved() {
        val root = temp.newFolder(); val store = LocalConversationStore(root); val value = chat(); store.saveNew(value)
        File(root, "${value.id}.json").writeText("{broken"); val before = files(root)
        try {store.markdownExport(value); fail("Expected history failure")} catch (_: LocalHistoryException) {}
        assertEquals(before, files(root))
    }
    @Test fun unpairedUnicodeInAnOlderStoredQuestionIsRefusedWithoutLossyReplacement() {
        val root = temp.newFolder(); val store = LocalConversationStore(root); val value = chat(); store.saveNew(value)
        File(root, "${value.id}.json").writeText("""{"schema":1,"id":"${value.id}","model":"${value.modelSha256}","updated":1,"exchanges":[{"question":"\ud800","reply":"reply","state":"COMPLETE"}]}""")
        val before = files(root); val loaded = store.load(value.id); assertEquals('\ud800', loaded.exchanges.single().question.single())
        rejected(LocalMarkdownExportFailure.INVALID) {store.markdownExport(loaded)}; assertEquals(before, files(root))
    }
    @Test fun cancellationDuringSnapshotOrEncodingPreservesEveryHistoryFile() {
        val root = temp.newFolder(); val store = LocalConversationStore(root); val value = chat(reply = "r".repeat(100000)); store.saveNew(value); val before = files(root); var calls = 0
        try {store.markdownExport(value) {if(++calls == 20) throw CancellationException("fixture")}; fail("Expected cancellation")} catch (_: CancellationException) {}
        assertEquals(20, calls); assertEquals(before, files(root))
    }
    @Test fun aDestinationFailureIsNotRetriedOrClosedAndTheFrozenDocumentStaysAvailable() {
        val root = temp.newFolder(); val store = LocalConversationStore(root); val value = chat(); store.saveNew(value); val before = files(root)
        val export = store.markdownExport(value); val original = bytes(export); var writes = 0; var closes = 0; var flushes = 0
        val broken = object : OutputStream() {
            override fun write(value: Int) {error("Expected bounded chunks")}
            override fun write(bytes: ByteArray, offset: Int, count: Int) {writes++; throw IOException("fixture")}
            override fun close() {closes++}
            override fun flush() {flushes++}
        }
        try {export.writeTo(broken); fail("Expected destination failure")} catch (_: IOException) {}
        assertEquals(1, writes); assertEquals(0, closes); assertEquals(0, flushes); assertArrayEquals(original, bytes(export)); assertEquals(before, files(root))
    }
    @Test fun anOutputConsumerCannotMutateTheReviewedPrivatePayload() {
        val store = LocalConversationStore(temp.newFolder()); val value = chat(); store.saveNew(value); val export = store.markdownExport(value); val original = bytes(export)
        export.writeTo(object : OutputStream() {
            override fun write(value: Int) {error("Expected chunks")}
            override fun write(bytes: ByteArray, offset: Int, count: Int) {bytes.fill(0)}
        })
        assertArrayEquals(original, bytes(export)); assertEquals(hash(original), export.documentSha256)
    }
    @Test fun cancellingAfterTheFirstOutputChunkLeavesAnExplicitPartialAndUnchangedSource() {
        val root = temp.newFolder(); val store = LocalConversationStore(root); val value = chat(reply = "r".repeat(50000)); store.saveNew(value); val before = files(root)
        val export = store.markdownExport(value); val output = ByteArrayOutputStream()
        try {export.writeTo(output) {if(output.size() >= 8192) throw CancellationException("fixture")}; fail("Expected cancellation")} catch (_: CancellationException) {}
        assertEquals(8192, output.size()); assertEquals(before, files(root)); assertTrue(export.byteCount > output.size())
    }
    @Test fun maximumSupportedTurnsAndFenceRunsStayCompleteWithinTheDocumentBound() {
        val question = "`".repeat(4096) + "~".repeat(4096); val reply = "`".repeat(65536) + "~".repeat(65536)
        val value = chat().copy(exchanges = List(LocalConversationStore.MAX_TURNS) {LocalExchange(question, reply, LocalReplyState.COMPLETE)})
        val store = LocalConversationStore(temp.newFolder()); store.saveNew(value); val export = store.markdownExport(value); val out = bytes(export)
        assertTrue(out.size <= LocalMarkdownExport.MAX_BYTES); assertEquals(out.size, export.byteCount)
        assertEquals(List(30) {listOf(question, reply)}.flatten(), literalBodies(out.toString(Charsets.UTF_8)))
    }
    @Test fun documentBytesAreLocaleIndependentAndInvalidTitlesAreRefused() {
        val store = LocalConversationStore(temp.newFolder()); val value = chat(); store.saveNew(value); val baseline = bytes(store.markdownExport(value)); val locale = Locale.getDefault()
        try {Locale.setDefault(Locale.forLanguageTag("ar-EG")); assertArrayEquals(baseline, bytes(store.markdownExport(value)))} finally {Locale.setDefault(locale)}
        for(title in listOf("", "x".repeat(4097), "\ud800")) rejected(LocalMarkdownExportFailure.INVALID) {store.markdownExport(value, title)}
    }
}
