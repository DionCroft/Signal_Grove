package org.hermesmobile.app.local

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalHistoryBackupProtectionTest {
    @get:Rule val temporary = TemporaryFolder()
    private val model = "a".repeat(64)
    private val labels = LocalHistoryOrganizationEntry(model, LocalHistoryPreferences("花园 🌱", true, true))
    private fun fixturePassphrase() = "canopy-森-🌱-fixture".toCharArray()

    private fun record(large: Boolean = false): LocalHistoryBackupRecord {
        val store = LocalConversationStore(temporary.newFolder())
        val turns = List(if (large) 12 else 1) { index ->
            LocalExchange("Garden question $index", "花园 🌱 " + if (large) "x".repeat(100_000) else "", LocalReplyState.COMPLETE)
        }
        val saved = LocalConversation(UUID(0, 19).toString(), model, 123, turns)
        store.saveNew(saved)
        return store.backupRecord(store.list().single().copy(organization = labels), labels)
    }

    private fun protect(record: LocalHistoryBackupRecord) = ByteArrayOutputStream().also { output ->
        LocalHistoryBackupProtection.write(listOf(record.id), fixturePassphrase(), output, { record })
    }.toByteArray()

    private fun file(bytes: ByteArray) = temporary.newFile().apply { writeBytes(bytes) }

    private fun refused(problem: LocalHistoryBackupProblem, operation: () -> Unit) {
        val error = assertThrows(LocalHistoryBackupException::class.java) { operation() }
        assertEquals(problem, error.problem)
    }

    @Test fun authenticatedMultisegmentArchiveKeepsTranscriptAndLabelBytesWithFreshSalt() {
        val record = record(large = true)
        val first = protect(record)
        val second = protect(record)
        assertTrue(first.size > (1 shl 20))
        assertEquals("SGBKAE01", first.copyOfRange(0, 8).toString(Charsets.US_ASCII))
        assertFalse(first.copyOfRange(8, 24).contentEquals(second.copyOfRange(8, 24)))
        assertFalse(first.toString(Charsets.ISO_8859_1).contains("Garden question"))
        val ciphertext = file(first)
        val reviews = temporary.newFolder()

        val opened = LocalHistoryBackupProtection.inspect(ciphertext, fixturePassphrase(), reviews)
        val inspection = opened.inspection
        opened.use {
            assertEquals(labels.preferences, inspection.items.single().labels)
            val restored = inspection.readRecord(record.id)
            assertEquals(record.conversation, restored.conversation)
            assertArrayEquals(record.transcriptHash(), restored.transcriptHash())
            assertEquals(1, reviews.listFiles()!!.size)
        }
        opened.close()
        assertTrue(reviews.listFiles()!!.isEmpty())
        refused(LocalHistoryBackupProblem.CHANGED) { inspection.readRecord(record.id) }
        assertArrayEquals(first, ciphertext.readBytes())
    }

    @Test fun wrongPassphraseTamperedSaltTagTruncationAndTrailingBytesExposeNoReview() {
        val record = record()
        val intact = protect(record)
        val reviews = temporary.newFolder()
        val wrong = file(intact)
        refused(LocalHistoryBackupProblem.AUTHENTICATION) {
            LocalHistoryBackupProtection.inspect(wrong, "unrelated fixture".toCharArray(), reviews)
        }
        assertTrue(reviews.listFiles()!!.isEmpty())
        assertArrayEquals(intact, wrong.readBytes())

        val variations = listOf(
            intact.clone().apply { this[16] = (this[16].toInt() xor 1).toByte() },
            intact.clone().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() },
            intact.copyOf(intact.size - 1),
            intact + byteArrayOf(1),
        )
        for (bytes in variations) {
            val changed = file(bytes)
            refused(LocalHistoryBackupProblem.AUTHENTICATION) {
                LocalHistoryBackupProtection.inspect(changed, fixturePassphrase(), reviews)
            }
            assertTrue(reviews.listFiles()!!.isEmpty())
            assertArrayEquals(bytes, changed.readBytes())
        }
    }

    @Test fun authenticatedEnvelopeStillRequiresAValidInnerArchiveChecksum() {
        // Standalone JCE / RFC 5869 fixture. This ciphertext authenticates but the archive digest is damaged.
        val bytes = Base64.getDecoder().decode(DAMAGED_ARCHIVE_VECTOR)
        val reviews = temporary.newFolder()
        refused(LocalHistoryBackupProblem.INVALID) {
            LocalHistoryBackupProtection.inspect(file(bytes), fixturePassphrase(), reviews)
        }
        assertTrue(reviews.listFiles()!!.isEmpty())
    }

    @Test fun cancellationAfterDecryptedBytesReachPrivateStorageRemovesThePartial() {
        val source = file(protect(record(large = true)))
        val reviews = temporary.newFolder()

        assertThrows(CancellationException::class.java) {
            LocalHistoryBackupProtection.inspect(source, fixturePassphrase(), reviews) {
                if (reviews.listFiles().orEmpty().any { it.length() >= 65_536 }) throw CancellationException()
            }
        }
        assertTrue(reviews.listFiles()!!.isEmpty())
        assertTrue(source.length() > (1 shl 20))
    }

    @Test fun unsupportedVersionOversizeAndShortHeadersAreRefusedBeforeCredentialsAreNeeded() {
        val original = protect(record())
        val reviews = temporary.newFolder()
        val unsupported = original.clone().apply { this[7] = '9'.code.toByte() }
        refused(LocalHistoryBackupProblem.UNSUPPORTED_VERSION) {
            LocalHistoryBackupProtection.inspect(file(unsupported), CharArray(0), reviews)
        }
        refused(LocalHistoryBackupProblem.INVALID) {
            LocalHistoryBackupProtection.inspect(file(unsupported.copyOf(23)), CharArray(0), reviews)
        }
        val oversized = temporary.newFile()
        RandomAccessFile(oversized, "rw").use { it.setLength(LocalHistoryBackupProtection.MAX_BYTES + 1L) }
        refused(LocalHistoryBackupProblem.INVALID) {
            LocalHistoryBackupProtection.inspect(oversized, CharArray(0), reviews)
        }
        assertTrue(reviews.listFiles()!!.isEmpty())
    }

    @Test fun invalidPassphraseDoesNotWriteHeaderOrKeepPlaintext() {
        val record = record()
        val output = ByteArrayOutputStream()
        val malformed = charArrayOf('\uD800')
        refused(LocalHistoryBackupProblem.INVALID_PASSPHRASE) {
            LocalHistoryBackupProtection.write(listOf(record.id), malformed, output, { record })
        }
        assertEquals(0, output.size())

        val reviews = temporary.newFolder()
        refused(LocalHistoryBackupProblem.INVALID_PASSPHRASE) {
            LocalHistoryBackupProtection.inspect(file(protect(record)), CharArray(257) { 'x' }, reviews)
        }
        assertTrue(reviews.listFiles()!!.isEmpty())
    }

    @Test fun failedDestinationDoesNotReceiveAdditionalBytesOrCloseTheBorrowedStream() {
        val record = record(large = true)
        var writes = 0
        var closed = false
        val destination = object : ByteArrayOutputStream() {
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                writes++
                if (writes == 3) throw IOException("synthetic destination failure")
                super.write(bytes, offset, length)
            }

            override fun close() { closed = true }
        }
        assertThrows(IOException::class.java) {
            LocalHistoryBackupProtection.write(listOf(record.id), fixturePassphrase(), destination, { record })
        }
        // The envelope header and Tink header reached the stream before the first body write failed.
        assertEquals(3, writes)
        assertEquals(24 + 40, destination.size())
        assertFalse(closed)
    }

    companion object {
        private const val DAMAGED_ARCHIVE_VECTOR = "U0dCS0FFMDEAAQIDBAUGBwgJCgsMDQ4PKBAREhMUFRYXGBkaGxwdHh8gISIjJCUmJygpKissLS4vYGFiY2RlZk2IKmeFJhUWYlNdh1OmIwix2xkRJ33HKiZi36/aHPFp4mnSv2nGNB3b6t/dndcelZFhYAXqHOizVu8+8mWkZ6/5Jy80Yx2j+lCWWMLda1N4W9DCUmymvfPBvbgKwrWsk83TPfNAccZSmXMvCgHYKIvQMrAZ83To2lci0cASuPxi7tuNHXfC0PVxBPpOfX1DaGyKqfU4/m/g333+hGfr+Fo3vXXlC78KsUw9VDIjcYDaqXUdu54xZfHGWMHTTayUfBHtiY9RgcMaJtYAyJPBX/IpCt7PCVW4uHNHxkp7UCoAXC+bXoOICEyrK5o4hcbmkeiKPLO1rQNDcVBqZKidubZwEWgh8dBTSmmX4mCck3dUW9Evch+OO/Z2PgBrwc1ewi5ab1MJ+QFhrx7HuTNvcudxWnG9KARRrAQvvvkxDqDHR/La7P2W7uRaYcaLY95mPiripinJM1AmRirIJTA="
    }
}
