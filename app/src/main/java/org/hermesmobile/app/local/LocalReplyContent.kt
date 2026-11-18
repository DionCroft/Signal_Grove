package org.hermesmobile.app.local

import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Node
import org.commonmark.parser.Parser

/** Pure presentation data. No URI resolution, clipboard access, file writes or model calls. */
data class LocalCodeBlock(val language: String, val text: String)
data class LocalReplyContent(val preview: String, val hasMore: Boolean, val code: List<LocalCodeBlock>, val codeCount: Int) {
    companion object {
        const val PREVIEW_CHARACTERS = 1024
        const val MAX_CODE_BLOCKS = 64
        const val MAX_MARKDOWN_BYTES = 16 * 1024
        fun streamingTail(text: String): String {
            var start = maxOf(0, text.length - 512)
            if (start > 0 && Character.isLowSurrogate(text[start]) && Character.isHighSurrogate(text[start - 1])) start--
            return text.substring(start)
        }
        /** Bounded native layout, exact text reconstruction, no split UTF-16 pairs. */
        fun chunks(text: String, limit: Int = 512): List<String> {
            require(limit in 2..2048 && text.toByteArray(Charsets.UTF_8).size <= 131072)
            val parts = mutableListOf<String>(); var start = 0
            while (start < text.length) {
                var end = minOf(start + limit, text.length)
                if (end < text.length) {
                    val newline = text.lastIndexOf('\n', end - 1)
                    val space = text.lastIndexOf(' ', end - 1)
                    val natural = if (newline >= start + limit / 2) newline else space
                    if (natural >= start + limit / 2) end = natural + 1
                    if (Character.isHighSurrogate(text[end - 1]) && Character.isLowSurrogate(text[end])) end--
                }
                check(end > start)
                parts.add(text.substring(start, end)); start = end
            }
            return parts
        }
        fun read(text: String): LocalReplyContent {
            require(text.toByteArray(Charsets.UTF_8).size <= 131072)
            var end = minOf(text.length, PREVIEW_CHARACTERS)
            if (end in 1 until text.length && Character.isHighSurrogate(text[end - 1]) && Character.isLowSurrogate(text[end])) end--
            val code = mutableListOf<LocalCodeBlock>(); var count = 0
            val root = Parser.builder().build().parse(text)
            val pending = java.util.ArrayDeque<Node>(); pending.add(root)
            while (pending.isNotEmpty()) {
                val node = pending.removeLast()
                val block = when(node) {
                    is FencedCodeBlock -> LocalCodeBlock(node.info.trim().takeWhile { !it.isWhitespace() }.filter { !it.isISOControl() }.take(48), node.literal)
                    is IndentedCodeBlock -> LocalCodeBlock("", node.literal)
                    else -> null
                }
                if (block != null) { count++; if (code.size < MAX_CODE_BLOCKS) code.add(block) }
                // Preserve document order without recursive traversal on deeply nested model text.
                var child = node.lastChild
                while (child != null) {pending.add(child); child = child.previous}
            }
            return LocalReplyContent(text.substring(0, end), end < text.length, code, count)
        }
    }
}