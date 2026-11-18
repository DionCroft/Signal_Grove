package org.hermesmobile.app.local

import org.junit.Assert.*
import org.junit.Test

class LocalReplyContentTest {
    @Test fun fencedAndIndentedCodeKeepDocumentOrderAndLiteralContents() {
        val text = "Text\n\n```kotlin\nval grove = 1\n```\n\n    indented()\n"
        val content = LocalReplyContent.read(text)
        assertEquals(listOf(LocalCodeBlock("kotlin", "val grove = 1\n"), LocalCodeBlock("", "indented()\n")), content.code)
        assertEquals(2, content.codeCount); assertEquals(text, content.preview); assertFalse(content.hasMore)
    }
    @Test fun inlineCodeAndLinksDoNotBecomeRunnableCodeBlocks() {
        val text = "Use `literal()` and [read](intent://example) ![image](https://example.invalid/never.png)"
        val content = LocalReplyContent.read(text)
        assertTrue(content.code.isEmpty()); assertEquals(0, content.codeCount); assertEquals(text, content.preview)
    }
    @Test fun anUnclosedFenceStillHasASelectableLiteralBody() {
        assertEquals(listOf(LocalCodeBlock("python", "unfinished\n")), LocalReplyContent.read("```python\nunfinished").code)
    }
    @Test fun manyCodeBlocksAreCountedButOnlyABoundedListIsRetained() {
        val text = (0 until 100).joinToString("\n") { "```text\nBlock $it\n```" }
        val content = LocalReplyContent.read(text)
        assertEquals(100, content.codeCount); assertEquals(64, content.code.size)
        assertEquals("Block 0\n", content.code.first().text); assertEquals("Block 63\n", content.code.last().text)
        assertTrue(content.hasMore); assertTrue(text.startsWith(content.preview))
    }
    @Test fun previewAndStreamingBoundariesNeverSplitAUnicodePair() {
        val text = "x".repeat(LocalReplyContent.PREVIEW_CHARACTERS - 1) + "🌱" + "tail"
        val content = LocalReplyContent.read(text)
        assertEquals("x".repeat(LocalReplyContent.PREVIEW_CHARACTERS - 1), content.preview); assertTrue(content.hasMore)
        val stream = "🌱" + "x".repeat(511)
        assertEquals(stream, LocalReplyContent.streamingTail(stream))
        assertEquals("x".repeat(512), LocalReplyContent.streamingTail("prefix" + "x".repeat(512)))
    }
    @Test fun chunksPreserveAllWhitespaceUnicodeAndTheCompleteSource() {
        val text = ("prefix\r\n你好 🌱  ").repeat(80) + "x".repeat(3000)
        for (limit in listOf(2, 17, 512, 2048)) {
            val parts = LocalReplyContent.chunks(text, limit)
            assertEquals(text, parts.joinToString("")); assertTrue(parts.all {it.length <= limit && it.isNotEmpty()})
            assertTrue(parts.none {Character.isHighSurrogate(it.last()) || Character.isLowSurrogate(it.first())})
        }
    }
    @Test fun maximumSizeBlankTextStaysBoundedAndCanBeCopiedExactly() {
        val text = "\n".repeat(131072); val content = LocalReplyContent.read(text)
        assertEquals(1024, content.preview.length); assertTrue(content.hasMore); assertTrue(content.code.isEmpty())
        val parts = LocalReplyContent.chunks(text)
        assertTrue(parts.all {it.length <= 512}); assertEquals(text, parts.joinToString(""))
    }
    @Test fun oversizedTextAndInvalidLayoutLimitsAreRefused() {
        assertThrows(IllegalArgumentException::class.java) {LocalReplyContent.read("x".repeat(131073))}
        assertThrows(IllegalArgumentException::class.java) {LocalReplyContent.chunks("text", 1)}
        assertThrows(IllegalArgumentException::class.java) {LocalReplyContent.chunks("text", 2049)}
    }
}