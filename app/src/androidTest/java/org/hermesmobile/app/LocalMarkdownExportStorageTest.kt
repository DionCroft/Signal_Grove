package org.hermesmobile.app

import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CancellationException
import org.hermesmobile.app.local.*
import org.junit.Assert.*
import org.junit.Test

/** Real Android private QA files; no existing phone models/history, destinations or network. */
class LocalMarkdownExportStorageTest {
    private fun root() = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "markdown-export-${UUID.randomUUID()}").also {check(it.mkdir())}
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)}
    private fun files(root: File) = root.listFiles()!!.associate {it.name to hash(it.readBytes())}
    @Test fun androidFrozenMarkdownKeepsExactUnicodeStatesAndStoredSourceBytesWithoutWeights() {
        val root = root(); val store = LocalConversationStore(root)
        val question = "花园 🌱\n```\n~~~\n# literal heading"; val reply = "<script>literal()</script>\r\nA peaceful garden."
        val value = LocalConversation(modelSha256 = "a".repeat(64), updatedAt = 1234,
            exchanges = listOf(LocalExchange(question, reply, LocalReplyState.INTERRUPTED, LocalGenerationSettings(2048, 2, 96))))
        store.saveNew(value); val before = files(root); val export = store.markdownExport(value, "花园 Garden")
        val out = ByteArrayOutputStream(); export.writeTo(out); val bytes = out.toByteArray(); val text = bytes.toString(Charsets.UTF_8)
        assertEquals(export.byteCount, bytes.size); assertEquals(hash(bytes), export.documentSha256)
        assertEquals(before.getValue(value.id + ".json"), export.sourceSha256)
        assertTrue(text.contains("\n````text\n" + question + "\n````\n"))
        assertTrue(text.contains(reply)); assertTrue(text.contains("Assistant (INTERRUPTED)")); assertTrue(text.contains("context=2048; threads=2; output_limit=96"))
        assertEquals(before, files(root)); assertEquals(value, store.load(value.id))
    }
    @Test fun androidReadAndOutputCancellationKeepTheSameHistoryAndDoNotPublishAReplacement() {
        val root = root(); val store = LocalConversationStore(root)
        val value = LocalConversation(modelSha256 = "a".repeat(64), updatedAt = 1,
            exchanges = listOf(LocalExchange("Garden", "r".repeat(100000), LocalReplyState.COMPLETE)))
        store.saveNew(value); val before = files(root); var checks = 0
        try {store.markdownExport(value) {if(++checks == 20) throw CancellationException("fixture")}; fail("Expected cancellation")} catch (_: CancellationException) {}
        assertEquals(20, checks); assertEquals(before, files(root))
        val frozen = store.markdownExport(value); val output = ByteArrayOutputStream()
        try {frozen.writeTo(output) {if(output.size() >= 8192) throw CancellationException("fixture")}; fail("Expected output cancellation")} catch (_: CancellationException) {}
        assertEquals(8192, output.size()); assertEquals(before, files(root)); assertEquals(value, store.load(value.id))
    }
}
