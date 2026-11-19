package org.hermesmobile.app.local

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalHistoryBackupArchiveTest {
    @get:Rule val temp = TemporaryFolder()
    private val store by lazy { LocalConversationStore(temp.newFolder()) }
    private fun chat(reply: String = "Garden 🌱", state: LocalReplyState = LocalReplyState.COMPLETE) =
        LocalConversation(modelSha256 = "a".repeat(64), updatedAt = 100, exchanges = listOf(
            LocalExchange("Quiet garden 花园", reply, state)
        ))
    private fun hash(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
    private fun file(bytes: ByteArray) = temp.newFile().apply { writeBytes(bytes) }
    private fun rejected(problem: LocalHistoryBackupProblem, run: () -> Unit) {
        try { run(); fail("Expected backup refusal") }
        catch (e: LocalHistoryBackupException) { assertEquals(problem, e.problem) }
    }
    private fun record(chat: LocalConversation, preferences: LocalHistoryPreferences? = null): LocalHistoryBackupRecord {
        val labels = preferences?.let { LocalHistoryOrganizationEntry(chat.modelSha256, it) }
        val row = store.list().single { it.id == chat.id }.copy(organization = labels)
        return store.backupRecord(row, labels)
    }
    private fun encoded(vararg records: LocalHistoryBackupRecord): ByteArray {
        val indexed = records.associateBy { it.id }
        return ByteArrayOutputStream().also {
            LocalHistoryBackupArchive.write(records.map { x -> x.id }, it, indexed::getValue)
        }.toByteArray()
    }
    private fun resign(value: ByteArray): ByteArray {
        val body = value.copyOfRange(0, value.size - 32)
        return body + hash(body)
    }

    @Test fun selectedLegacyAndModernChatsKeepExactSourceBytesLabelsAndBranchProvenance() {
        val legacy = chat()
        store.saveNew(legacy)
        val fork = store.createBranch(legacy, 0, LocalBranchKind.FORK, 101)
        val runtime = LocalRuntimePolicy("b".repeat(64), LocalSamplingPolicy.GREEDY_MODEL_METADATA_V1)
        val modern = fork.copy(updatedAt = 102, exchanges = fork.exchanges + LocalExchange(
            "Second turn", "Partial", LocalReplyState.INTERRUPTED, LocalGenerationSettings(4096, 4, 384, runtime)
        ))
        store.save(modern)
        val unrelated = chat("unrelated-secret-message")
        store.saveNew(unrelated)
        val labels = LocalHistoryPreferences("花园 🌱", pinned = true, archived = true)
        val legacyRecord = record(legacy)
        val modernRecord = record(modern, labels)
        val archive = encoded(modernRecord, legacyRecord)
        assertFalse(archive.toString(Charsets.UTF_8).contains("unrelated-secret-message"))
        val snapshot = LocalHistoryBackupArchive.inspect(file(archive))
        assertEquals(listOf(modern.id, legacy.id), snapshot.items.map { it.id })
        assertEquals(labels, snapshot.items.first().labels)
        assertEquals(modern.origin, snapshot.items.first().origin)
        assertEquals(2, snapshot.items.first().exchangeCount)
        assertEquals(modern, snapshot.readRecord(modern.id).conversation)
        assertEquals(legacy, snapshot.readRecord(legacy.id).conversation)
        assertArrayEquals(modernRecord.transcriptHash(), snapshot.readRecord(modern.id).transcriptHash())
        assertArrayEquals(legacyRecord.transcriptHash(), snapshot.readRecord(legacy.id).transcriptHash())
        assertEquals(unrelated, store.load(unrelated.id))
    }

    @Test fun archivedTitleAndModelReviewMustMatchTheCurrentSource() {
        val value = chat()
        store.saveNew(value)
        val row = store.list().single()
        val label = LocalHistoryOrganizationEntry(value.modelSha256, LocalHistoryPreferences("Earlier title", true))
        rejected(LocalHistoryBackupProblem.CHANGED) { store.backupRecord(row, label) }
        rejected(LocalHistoryBackupProblem.CHANGED) { store.backupRecord(row.copy(updatedAt = 101), null) }
        val mismatched = label.copy(modelSha256 = "b".repeat(64))
        rejected(LocalHistoryBackupProblem.CHANGED) { store.backupRecord(row.copy(organization = mismatched), mismatched) }
        assertEquals(value, store.load(value.id))
    }

    @Test fun anActiveChatCannotBeCapturedOrImportedAsAnExecutableTurn() {
        val value = chat(state = LocalReplyState.GENERATING)
        store.saveNew(value)
        rejected(LocalHistoryBackupProblem.INVALID) { record(value) }
        assertEquals(LocalReplyState.GENERATING, store.load(value.id).exchanges.single().state)

        val complete = value.copy(exchanges = listOf(value.exchanges.single().copy(state = LocalReplyState.TOKEN_LIMIT)))
        store.save(complete)
        val bytes = encoded(record(complete))
        val injected = bytes.toString(Charsets.ISO_8859_1).replace("TOKEN_LIMIT", "GENERATING ")
            .replace("\"GENERATING \"", "\"GENERATING\" ")
            .toByteArray(Charsets.ISO_8859_1)
        assertEquals(bytes.size, injected.size)
        assertFalse(injected.contentEquals(bytes))
        rejected(LocalHistoryBackupProblem.INVALID) { LocalHistoryBackupArchive.inspect(file(resign(injected))) }
    }

    @Test fun truncatedTamperedOrTrailingArchivesNeverReturnAReview() {
        val value = chat()
        store.saveNew(value)
        val bytes = encoded(record(value))
        val changedDigest = bytes.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        for (invalid in listOf(bytes.copyOf(18), bytes.copyOf(bytes.size - 1), changedDigest, bytes + byteArrayOf(0))) {
            rejected(LocalHistoryBackupProblem.INVALID) { LocalHistoryBackupArchive.inspect(file(invalid)) }
        }
    }

    @Test fun unknownVersionIsDistinguishedFromAnInvalidHeader() {
        val value = chat()
        store.saveNew(value)
        val out = encoded(record(value))
        ByteBuffer.wrap(out).putInt(8, 2)
        rejected(LocalHistoryBackupProblem.UNSUPPORTED_VERSION) { LocalHistoryBackupArchive.inspect(file(resign(out))) }
        out[0] = 0
        rejected(LocalHistoryBackupProblem.INVALID) { LocalHistoryBackupArchive.inspect(file(resign(out))) }
    }

    @Test fun parserRejectsUnboundedRecordLengthsWrongIdsAndDuplicateRecordsEvenWithAValidChecksum() {
        val value = chat()
        val other = chat("Second")
        store.saveNew(value)
        store.saveNew(other)
        val a = record(value)
        val b = record(other)
        val one = encoded(a)
        val two = encoded(a, b)
        val oversized = one.copyOf().also { ByteBuffer.wrap(it).putInt(55, Int.MAX_VALUE) }
        val wrongId = one.copyOf().also { other.id.toByteArray().copyInto(it, destinationOffset = 16) }
        val secondIdOffset = 16 + 43 + a.transcriptBytes
        val duplicate = two.copyOf().also { value.id.toByteArray().copyInto(it, destinationOffset = secondIdOffset) }
        for (invalid in listOf(oversized, wrongId, duplicate)) {
            rejected(LocalHistoryBackupProblem.INVALID) { LocalHistoryBackupArchive.inspect(file(resign(invalid))) }
        }
    }

    @Test fun deepMalformedAndLossyTextIsRejectedBeforeReview() {
        val id = UUID.randomUUID().toString()
        val deep = "[".repeat(3000) + "]".repeat(3000)
        val prefix = """{"schema":1,"id":"$id","model":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","updated":1,"exchanges":"""
        val surrogate = prefix + """[{"question":"\ud800","reply":"","state":"COMPLETE"}]}"""
        val files = listOf(deep.toByteArray(), surrogate.toByteArray(), byteArrayOf(0xc3.toByte(), 0x28))
        for (value in files) {
            try { LocalConversationStore.decodeBackupTranscript(id, value) {}; fail("Untrusted transcript accepted") }
            catch (_: Exception) { }
        }
    }

    @Test fun aStagedTranscriptChangeAfterReviewCannotChangeWhatWillBeImported() {
        val value = chat("Unique answer")
        store.saveNew(value)
        val bytes = encoded(record(value))
        val staged = file(bytes)
        val snapshot = LocalHistoryBackupArchive.inspect(staged)
        RandomAccessFile(staged, "rw").use {
            it.seek(59)
            it.writeByte('{'.code xor 1)
        }
        rejected(LocalHistoryBackupProblem.CHANGED) { snapshot.readRecord(value.id) }
        assertEquals(value, store.load(value.id))
    }

    @Test fun cancellingAnyChunkPreservesTheSourceAndInvalidatesAPartialDestination() {
        val value = chat("t".repeat(100_000))
        store.saveNew(value)
        val rec = record(value)
        val target = ByteArrayOutputStream()
        var calls = 0
        try {
            LocalHistoryBackupArchive.write(listOf(value.id), target, { calls++; rec }) {
                if (target.size() >= 8192) throw CancellationException("fixture")
            }
            fail("Expected write cancellation")
        } catch (_: CancellationException) { }
        assertEquals(1, calls)
        rejected(LocalHistoryBackupProblem.INVALID) { LocalHistoryBackupArchive.inspect(file(target.toByteArray())) }
        val complete = file(encoded(rec))
        var checked = 0
        try {
            LocalHistoryBackupArchive.inspect(complete) { if (++checked == 5) throw CancellationException("fixture") }
            fail("Expected read cancellation")
        } catch (_: CancellationException) { }
        assertEquals(value, store.load(value.id))
        assertEquals(value, LocalHistoryBackupArchive.inspect(complete).readRecord(value.id).conversation)
    }

    @Test fun providerFailureDoesNotRetryOrCloseTheDestination() {
        val value = chat("t".repeat(100_000))
        store.saveNew(value)
        val rec = record(value)
        var loads = 0
        var writes = 0
        var closed = false
        val target = object : OutputStream() {
            override fun write(v: Int) { }
            override fun write(buffer: ByteArray, offset: Int, length: Int) {
                if (length == 8192) {
                    writes++
                    throw IOException("fixture")
                }
            }
            override fun close() { closed = true }
        }
        try {
            LocalHistoryBackupArchive.write(listOf(value.id), target, { loads++; rec })
            fail("Expected provider failure")
        } catch (_: IOException) { }
        assertEquals(1, loads)
        assertEquals(1, writes)
        assertFalse(closed)
    }
}
