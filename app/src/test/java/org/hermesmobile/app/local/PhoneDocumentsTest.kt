package org.hermesmobile.app.local

import java.io.File
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PhoneDocumentsTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun storeNormalizesTextAndReindexesOnlyTheReviewedDocument() {
        val root = temp.newFolder()
        val abandoned = File(root, "stage-00000000-0000-0000-0000-000000000001.tmp").apply { writeText("abandoned") }
        val unrelated = File(root, "user-note.keep").apply { writeText("keep") }
        val documents = PhoneDocumentLibrary(root)
        documents.save("Travel plans.md", "\uFEFF# Trip\r\nMeet at Kyoto.\r\n".byteInputStream(), null) {}
        val info = documents.list().single()
        assertFalse(abandoned.exists())
        assertEquals(3, info.lineCount)
        assertEquals("# Trip\nMeet at Kyoto.\n", documents.selected(listOf(info.id)) {}.single().sections.single())
        val original = File(root, "${info.id}.json").readBytes()
        var checkpoints = 0
        assertTrue(runCatching {
            documents.save("Travel plans.md", "Meet at Osaka.".byteInputStream(), info) {
                if (++checkpoints == 3) throw CancellationException()
            }
        }.exceptionOrNull() is CancellationException)
        assertArrayEquals(original, File(root, "${info.id}.json").readBytes())
        assertEquals(info, documents.list().single())

        documents.save("Travel updated.md", "Meet at Osaka.".byteInputStream(), info) {}
        val updated = documents.list().single()
        assertEquals(info.id, updated.id)
        assertNotEquals(info.fingerprint, updated.fingerprint)
        val rejected = runCatching { documents.remove(info) {} }.exceptionOrNull() as LocalModelStorageException
        assertEquals("document_changed", rejected.reason)
        documents.remove(updated) {}
        assertTrue(documents.list().isEmpty())
        assertEquals("keep", unrelated.readText())
    }

    @Test fun unsupportedOversizedAndMalformedSelectionsLeaveNoPrivateCopy() {
        val documents = PhoneDocumentLibrary(temp.newFolder())
        val rows = listOf(
            Triple("../travel.md", "text".toByteArray(), "document_format"),
            Triple("report.pdf", "%PDF".toByteArray(), "document_format"),
            Triple("text.txt", byteArrayOf(0xc3.toByte(), 0x28), "document_encoding"),
            Triple("binary.txt", byteArrayOf(65, 0, 66), "document_encoding"),
            Triple("empty.md", "  \n".toByteArray(), "document_empty"),
            Triple("too-large.txt", ByteArray(PhoneDocumentLibrary.MAX_DOCUMENT_BYTES + 1) { 65 }, "document_size")
        )
        for ((name, bytes, reason) in rows) {
            val failure = runCatching { documents.save(name, bytes.inputStream(), null) {} }.exceptionOrNull() as LocalModelStorageException
            assertEquals(reason, failure.reason)
            assertTrue(documents.list().isEmpty())
        }
    }

    @Test fun pdfPageNumbersSurviveReopeningAndSkipBlankPagesWithoutMergingExcerpts() {
        val root = temp.newFolder()
        PhoneDocumentLibrary(root).apply {
            save("legacy.txt", "Original text".byteInputStream(), null) {}
            savePdf("Handbook.PDF", listOf("The observatory opens at dusk.", "", "The observatory codeword is MARIGOLD."), null) {}
        }
        val documents = PhoneDocumentLibrary(root)
        val (pdf, text) = documents.list()
        assertEquals(3, pdf.pageCount)
        assertEquals(1, text.lineCount)
        val selected = documents.selected(listOf(pdf.id)) {}
        val review = checkNotNull(PhoneDocumentQuestions.review("observatory codeword", selected) {})
        assertEquals(listOf(3, 1), review.sources.map { it.page })
        assertEquals("The observatory codeword is MARIGOLD.", review.sources.first().text)
        assertEquals("Original text", documents.selected(listOf(text.id)) {}.single().sections.single())

        val invalid = runCatching { documents.savePdf("Handbook.PDF", listOf("", "  "), pdf) {} }
        assertEquals("document_pdf_image_only", (invalid.exceptionOrNull() as LocalModelStorageException).reason)
        assertEquals(listOf(pdf, text), documents.list())
        documents.savePdf("Handbook.PDF", listOf("The observatory codeword is MARIGOLD."), pdf) {}
        val updated = documents.list().first()
        assertEquals(pdf.id, updated.id)
        assertNotEquals(pdf.fingerprint, updated.fingerprint)
        documents.remove(updated) {}
        assertEquals(listOf(text), documents.list())
    }

    @Test fun uncitedReplyCanOnlyBeAnchoredAsAUniqueVerbatimExcerpt() {
        val documents = PhoneDocumentLibrary(temp.newFolder())
        documents.save("one.txt", "The observatory codeword is MARIGOLD.".byteInputStream(), null) {}
        val chosen = documents.list().single()
        val choice = PhoneDocumentQuestions.review("observatory codeword?", documents.selected(listOf(chosen.id)) {}) {}!!
        assertEquals("MARIGOLD [S1]", PhoneDocumentQuestions.anchorVerbatimReply("**marigold**", choice.sources))
        assertNull(PhoneDocumentQuestions.anchorVerbatimReply("MARIGOLD [S8]", choice.sources))
        assertNull(PhoneDocumentQuestions.anchorVerbatimReply("GOLD", choice.sources))
        assertNull(PhoneDocumentQuestions.anchorVerbatimReply("MARIGOLD or BLUE", choice.sources))
        assertNull(PhoneDocumentQuestions.anchorVerbatimReply("MARIGOLD", choice.sources + choice.sources))
    }

    @Test fun retrievalUsesExactTextFromSelectedFilesAndChecksCitationNumbers() {
        val documents = PhoneDocumentLibrary(temp.newFolder())
        val passage = "会议阶段\n交货日期是十一月二十日。\nRéunion à Lyon."
        documents.save("schedule.md", passage.byteInputStream(), null) {}
        documents.save("other.txt", "Unselected marker: secrets.".byteInputStream(), null) {}
        val selected = documents.list().single { it.name == "schedule.md" }
        val index = documents.selected(listOf(selected.id)) {}
        val review = checkNotNull(PhoneDocumentQuestions.review("交货日期？", index) {})
        assertEquals(listOf(PhoneDocumentSource(selected, 1, 3, passage)), review.sources)
        assertNotNull(PhoneDocumentQuestions.review("Réunion?", index) {})
        assertNull(PhoneDocumentQuestions.review("mars rover", index) {})
        val messages = PhoneDocumentQuestions.messages(review)
        assertTrue(messages.first().text.contains("quoted excerpts"))
        assertTrue(messages.last().text.contains("交货日期"))
        assertFalse(messages.last().text.contains("Unselected"))
        assertFalse(messages.last().text.contains(selected.id))
        assertEquals("十一月二十日 [S1]", PhoneDocumentQuestions.checkedReply("十一月二十日 [S1]", 1))
        assertNull(PhoneDocumentQuestions.checkedReply("十一月二十日 [S1] [S2]", 1))
        assertNull(PhoneDocumentQuestions.checkedReply("十一月二十日 [s1]", 1))
        assertNull(PhoneDocumentQuestions.checkedReply("Uncited", 1))
    }
}
