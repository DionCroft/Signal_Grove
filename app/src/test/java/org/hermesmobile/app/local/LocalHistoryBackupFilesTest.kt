package org.hermesmobile.app.local

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalHistoryBackupFilesTest {
    @get:Rule val temporary = TemporaryFolder()
    private val root by lazy { temporary.newFolder() }
    private val stage by lazy { File(root, "history-backup-stages") }
    private val store by lazy { LocalConversationStore(File(root, "chats")) }
    private val labels by lazy { LocalHistoryOrganization(File(root, "labels"), store) }
    private val files by lazy { LocalHistoryBackupFiles(stage, store, labels) }
    private val preferences = LocalHistoryPreferences("Garden archive 花园", pinned = true, archived = true)
    private fun fixturePassphrase() = "paper-花园-🌱-fixture".toCharArray()

    private fun record(number: Long = 1, organize: Boolean = true): LocalConversation {
        val chat = LocalConversation(UUID(0, number).toString(), "a".repeat(64), 120 + number,
            listOf(LocalExchange("Offline garden " + number, "A quiet garden 🌱", LocalReplyState.COMPLETE)))
        store.saveNew(chat)
        if (organize) labels.update(chat.id, chat.modelSha256, null, preferences)
        return chat
    }

    private fun select(chat: LocalConversation) = labels.history().filter { it.id == chat.id }
    private fun bytes(document: LocalHistoryBackupDocument) = ByteArrayOutputStream().also { document.writeTo(it) }.toByteArray()

    private fun refused(expected: LocalHistoryBackupProblem, operation: () -> Unit) {
        assertEquals(expected, assertThrows(LocalHistoryBackupException::class.java) { operation() }.problem)
    }

    @Test fun preparedExportFreezesTheSelectedTranscriptAndItsArchivedLabels() {
        val chosen = record()
        val unselected = record(2)
        val before = store.file(chosen.id).readBytes()
        files.export(select(chosen), null).use { document ->
            val frozen = bytes(document)
            store.save(chosen.copy(updatedAt = 901))
            val prior = labels.entries()[chosen.id]
            labels.update(chosen.id, chosen.modelSha256, prior, LocalHistoryPreferences("Rename"))
            assertEquals(LocalHistoryBackupFormat.PLAIN, document.format)
            assertArrayEquals(frozen, bytes(document))
            document.inspect(null).use { review ->
                assertEquals(listOf(chosen.id), review.archive.items.map { it.id })
                assertEquals(preferences, review.archive.items.single().labels)
                val archived = ByteArrayOutputStream()
                review.archive.readRecord(chosen.id).writeTranscript(archived) {}
                assertArrayEquals(before, archived.toByteArray())
            }
            assertFalse(frozen.toString(Charsets.UTF_8).contains(unselected.id))
            assertEquals(frozen.size.toLong(), document.bytes)
        }
        assertTrue(stage.listFiles()!!.isEmpty())
    }

    @Test fun rejectedPassphraseKeepsOnlyTheEncryptedImportUntilAnExplicitNewReview() {
        val chat = record()
        val prepared = files.export(select(chat), fixturePassphrase()).use(::bytes)
        files.stageImport(prepared.inputStream()).use { document ->
            assertEquals(LocalHistoryBackupFormat.PROTECTED, document.format)
            assertEquals(1, stage.listFiles()!!.size)
            refused(LocalHistoryBackupProblem.AUTHENTICATION) {
                document.inspect("another synthetic passphrase".toCharArray())
            }
            assertEquals(1, stage.listFiles()!!.size)
            val opened = document.inspect(fixturePassphrase())
            opened.use { review ->
                assertEquals(preferences, review.archive.items.single().labels)
                assertEquals(chat, review.archive.readRecord(chat.id).conversation)
                assertEquals(2, stage.listFiles()!!.size)
            }
            assertEquals(1, stage.listFiles()!!.size)
            refused(LocalHistoryBackupProblem.CHANGED) { opened.archive.readRecord(chat.id) }
        }
        assertTrue(stage.listFiles()!!.isEmpty())
    }

    @Test fun canceledImportAfterBodyBytesWereStagedLeavesNoCopy() {
        val chat = record()
        val source = files.export(select(chat), null).use(::bytes)
        assertThrows(CancellationException::class.java) {
            files.stageImport(source.inputStream()) {
                if (stage.listFiles().orEmpty().any { it.length() > 8 }) throw CancellationException()
            }
        }
        assertTrue(stage.listFiles()!!.isEmpty())
        assertEquals(chat, store.load(chat.id))
    }

    @Test fun aChangedLabelRefusesTheOldSelectionAndReleasesThePartial() {
        val chat = record()
        val reviewed = select(chat)
        val before = store.file(chat.id).readBytes()
        labels.update(chat.id, chat.modelSha256, labels.entries()[chat.id], LocalHistoryPreferences("Later title"))

        refused(LocalHistoryBackupProblem.CHANGED) { files.export(reviewed, null) }
        assertTrue(stage.listFiles()!!.isEmpty())
        assertArrayEquals(before, store.file(chat.id).readBytes())
        assertEquals("Later title", labels.history().single().title)
    }

    @Test fun aChangedOrClosedStageWritesNoBytesToTheDestination() {
        val chat = record()
        val document = files.export(select(chat), null)
        val local = stage.listFiles()!!.single()
        RandomAccessFile(local, "rw").use {
            val offset = it.length() - 1
            it.seek(offset)
            val old = it.readUnsignedByte()
            it.seek(offset)
            it.writeByte(old xor 1)
        }
        val destination = ByteArrayOutputStream()
        refused(LocalHistoryBackupProblem.CHANGED) { document.writeTo(destination) }
        document.close()
        document.close()
        refused(LocalHistoryBackupProblem.CHANGED) { document.writeTo(destination) }
        assertEquals(0, destination.size())
        assertTrue(stage.listFiles()!!.isEmpty())
    }

    @Test fun unknownAndUnsupportedHeadersStopBeforeReadingTheBody() {
        fun prefix(value: String): InputStream = object : InputStream() {
            private var count = 0
            override fun read(): Int {
                assertTrue("The unknown archive body must stay unread", count < 8)
                return value[count++].code
            }
        }
        refused(LocalHistoryBackupProblem.INVALID) {
            files.stageImport(prefix("NOTABACK"))
        }
        refused(LocalHistoryBackupProblem.UNSUPPORTED_VERSION) {
            files.stageImport(prefix("SGBKAE02"))
        }
        refused(LocalHistoryBackupProblem.INVALID) {
            files.stageImport("SGHISTOR".byteInputStream())
        }
        assertTrue(stage.listFiles()!!.isEmpty())
    }

    @Test fun aFreshOwnerRemovesAbandonedStagesAndKeepsUnrecognizedFilesAndLiveHistory() {
        val chat = record()
        val raw = store.file(chat.id).readBytes()
        val index = labels.index().readBytes()
        val abandoned = files.export(select(chat), null)
        File(stage, "review-00000000-0000-0000-0000-000000000001.part").writeText("Rejected synthetic review")
        val unrelated = File(stage, "notes.keep")
        unrelated.writeText("Retained fixture")
        val fresh = LocalHistoryBackupFiles(stage, store, labels)

        fresh.discardAbandonedDocuments()
        assertEquals(listOf(unrelated), stage.listFiles()!!.toList())
        refused(LocalHistoryBackupProblem.CHANGED) { bytes(abandoned) }
        fresh.export(select(chat), null).use { active ->
            fresh.discardAbandonedDocuments()
            assertTrue(bytes(active).isNotEmpty())
        }
        assertEquals("Retained fixture", unrelated.readText())
        assertArrayEquals(raw, store.file(chat.id).readBytes())
        assertArrayEquals(index, labels.index().readBytes())
    }
}
