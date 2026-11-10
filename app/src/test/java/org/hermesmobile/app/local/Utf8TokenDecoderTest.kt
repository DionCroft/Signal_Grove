package org.hermesmobile.app.local

import org.junit.Assert.*
import org.junit.Test

class Utf8TokenDecoderTest {
    @Test fun preservesChineseEmojiAndAsciiAcrossEveryByteBoundary() {
        val original = "Signal Grove 你好 🌱\nready"
        for (split in 0..original.toByteArray().size) {
            val d = Utf8TokenDecoder(); val bytes = original.toByteArray()
            val first = d.append(bytes.copyOfRange(0, split))
            assertFalse(first.orEmpty().contains('\ufffd'))
            val final = d.append(bytes.copyOfRange(split, bytes.size)) ?: first
            assertEquals(original, final)
        }
    }
    @Test fun streamsOneByteAtATimeWithoutBrokenCharacters() {
        val d = Utf8TokenDecoder(); var result = ""
        "你好🌱".toByteArray().forEach { b -> d.append(byteArrayOf(b))?.let { result = it; assertFalse(it.contains('\ufffd')) } }
        assertEquals("你好🌱", result)
    }
    @Test fun boundsUntrustedNativePieces() { assertThrows(IllegalArgumentException::class.java) { Utf8TokenDecoder().append(ByteArray(65537)) } }
}
