package org.hermesmobile.app

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.hermesmobile.app.voice.KokoroPlayback
import org.hermesmobile.app.voice.KokoroSpeaker
import org.hermesmobile.app.voice.KokoroVoicePack
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KokoroVoiceTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun publisherLengthAndHashAreBothRequired() {
        val target = temporary.newFile()
        val hash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        val bytes = "abc".toByteArray()
        val updates = mutableListOf<Long>()
        KokoroVoicePack.verifyToFile(ByteArrayInputStream(bytes), target, 3L, hash, updates::add)
        assertEquals("abc", target.readText())
        assertEquals(listOf(3L), updates)
        for (input in listOf("abcd", "ab", "abd")) {
            assertThrows(IOException::class.java) {
                KokoroVoicePack.verifyToFile(ByteArrayInputStream(input.toByteArray()), target, 3L, hash) {}
            }
        }
    }

    @Test fun extractionKeepsOnlyReviewedFiles() {
        val archive = archive(
            KokoroVoicePack.ARCHIVE_ROOT + "model.int8.onnx" to "test",
            KokoroVoicePack.ARCHIVE_ROOT + "espeak-ng-data/ignored" to "unused",
            "../escape" to "unused"
        )
        val destination = temporary.newFolder()
        KokoroVoicePack.extract(archive, destination, mapOf("model.int8.onnx" to 4L)) {}
        assertEquals("test", File(destination, "model.int8.onnx").readText())
        assertEquals(listOf("model.int8.onnx"), destination.list()!!.toList())
        assertFalse(File(destination.parentFile, "escape").exists())
    }

    @Test fun incompleteDuplicateAndOversizedModelEntriesAreRejected() {
        val name = KokoroVoicePack.ARCHIVE_ROOT + "model"
        for (entries in listOf(emptyList(), listOf(name to "xy"), listOf(name to "x", name to "x"))) {
            assertThrows(IOException::class.java) {
                KokoroVoicePack.extract(archive(*entries.toTypedArray()), temporary.newFolder(), mapOf("model" to 1L)) {}
            }
        }
    }

    @Test fun voiceChoiceAndChunkingRespectLanguageAndUnicode() {
        assertEquals(KokoroSpeaker.SOL, KokoroSpeaker.forLanguage("en-GB", 1))
        assertEquals(KokoroSpeaker.MAPLE, KokoroSpeaker.forLanguage("en-GB", 58))
        assertEquals(KokoroSpeaker.MANDARIN_MALE, KokoroSpeaker.forLanguage("zh-CN", 58))
        assertEquals(KokoroSpeaker.MANDARIN_FEMALE, KokoroSpeaker.forLanguage("zh-CN", 1))
        assertEquals(listOf("你好。", "We can start.", "再见！"), KokoroPlayback.speechChunks("你好。 We can start. 再见！"))
        val longText = "a".repeat(179) + "🌲".repeat(125)
        val chunks = KokoroPlayback.speechChunks(longText)
        assertEquals(longText, chunks.joinToString(""))
        assertTrue(chunks.all { it.length <= 180 && !Character.isHighSurrogate(it.last()) })
    }

    private fun archive(vararg files: Pair<String, String>): File {
        val file = temporary.newFile()
        TarArchiveOutputStream(BZip2CompressorOutputStream(file.outputStream())).use { tar ->
            files.forEach { (path, content) ->
                val data = content.toByteArray()
                val entry = TarArchiveEntry(path).apply { size = data.size.toLong() }
                tar.putArchiveEntry(entry)
                tar.write(data)
                tar.closeArchiveEntry()
            }
        }
        return file
    }
}
