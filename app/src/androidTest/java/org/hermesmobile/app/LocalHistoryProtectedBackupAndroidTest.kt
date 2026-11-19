package org.hermesmobile.app

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.RandomAccessFile
import java.util.Base64
import java.util.UUID
import org.hermesmobile.app.local.*
import org.junit.Assert.*
import org.junit.Test

class LocalHistoryProtectedBackupAndroidTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val model = "a".repeat(64)
    private val fixtureTitle = LocalHistoryPreferences("花园 🌱", pinned = true, archived = true)
    private fun fixturePassphrase() = "canopy-森-🌱-fixture".toCharArray()
    private fun root() = File(context.noBackupFilesDir, "protected-backup-fixture-" + UUID.randomUUID())
        .also { check(it.mkdir()) }

    @Test fun androidOpensIndependentUnicodeFixtureAndRestoresOnlyAfterExplicitReview() {
        val root = root()
        try {
            val ciphertext = File(root, "fixture.sgbackup").apply {
                writeBytes(Base64.getDecoder().decode(INDEPENDENT_VECTOR))
            }
            val reviews = File(root, "reviews").apply { check(mkdir()) }
            val store = LocalConversationStore(File(root, "chats"))
            val organization = LocalHistoryOrganization(File(root, "labels"), store)
            // The fixture uses the same ID. Its reviewed restore must never overwrite this file.
            val old = LocalConversation(UUID(0, 25).toString(), "b".repeat(64), 52,
                listOf(LocalExchange("Prior chat", "Existing reply", LocalReplyState.COMPLETE)))
            store.saveNew(old)
            val exactPrior = store.file(old.id).readBytes()
            val transaction = LocalHistoryRestoreTransaction(store, organization)

            LocalHistoryBackupProtection.inspect(ciphertext, fixturePassphrase(), reviews).use { review ->
                val plan = transaction.prepare(review.inspection)
                val item = plan.items.single()
                assertEquals(old.id, item.source.id)
                assertTrue(item.remapped)
                assertEquals(listOf(old.id), store.list().map { it.id })
                assertTrue(organization.entries().isEmpty())

                transaction.restore(plan)
                val imported = store.load(item.targetId)
                assertEquals(model, imported.modelSha256)
                assertEquals("花园 🌱", imported.exchanges.single().reply)
                assertEquals(fixtureTitle, organization.entries().getValue(imported.id).preferences)
            }
            assertEquals(2, store.list().size)
            assertArrayEquals(exactPrior, store.file(old.id).readBytes())
            assertTrue(reviews.listFiles()!!.isEmpty())
            assertArrayEquals(Base64.getDecoder().decode(INDEPENDENT_VECTOR), ciphertext.readBytes())
        } finally {
            assertTrue(root.deleteRecursively())
        }
    }

    @Test fun androidRejectsWrongPassphraseAndLateTamperingWithoutLeavingPlaintext() {
        val root = root()
        try {
            val reviews = File(root, "reviews").apply { check(mkdir()) }
            val store = LocalConversationStore(File(root, "chats"))
            val input = LocalConversation(modelSha256 = model, updatedAt = 72, exchanges = List(12) {
                LocalExchange("Offline garden $it", "花园 🌱 " + "x".repeat(100_000), LocalReplyState.COMPLETE)
            })
            store.saveNew(input)
            val original = store.file(input.id).readBytes()
            val tag = LocalHistoryOrganizationEntry(model, fixtureTitle)
            val record = store.backupRecord(store.list().single().copy(organization = tag), tag)
            val ciphertext = File(root, "example.sgbackup")

            ciphertext.outputStream().use { output ->
                LocalHistoryBackupProtection.write(listOf(input.id), fixturePassphrase(), output, { record })
            }
            assertTrue(ciphertext.length() > (1 shl 20))
            LocalHistoryBackupProtection.inspect(ciphertext, fixturePassphrase(), reviews).use { opened ->
                assertArrayEquals(record.transcriptHash(), opened.inspection.readRecord(input.id).transcriptHash())
            }
            refused(reviews, ciphertext, "incorrect fixture".toCharArray())
            RandomAccessFile(ciphertext, "rw").use {
                val at = it.length() - 1
                it.seek(at)
                val before = it.readUnsignedByte()
                it.seek(at)
                it.writeByte(before xor 1)
            }
            refused(reviews, ciphertext, fixturePassphrase())
            assertArrayEquals(original, store.file(input.id).readBytes())
            assertEquals(listOf(input.id), store.list().map { it.id })
        } finally {
            assertTrue(root.deleteRecursively())
        }
    }

    private fun refused(directory: File, ciphertext: File, passphrase: CharArray) {
        try {
            LocalHistoryBackupProtection.inspect(ciphertext, passphrase, directory).use {
                fail("Unauthenticated content produced a review")
            }
        } catch (failure: LocalHistoryBackupException) {
            assertEquals(LocalHistoryBackupProblem.AUTHENTICATION, failure.problem)
        }
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    companion object {
        // Independent single-segment JCE vector, with a PBKDF2 result computed by Python.
        private const val INDEPENDENT_VECTOR = "U0dCS0FFMDEAAQIDBAUGBwgJCgsMDQ4PKBAREhMUFRYXGBkaGxwdHh8gISIjJCUmJygpKissLS4vMDEyMzQ1NhEDxZl+mLAVUY+DhfMlnXTv0YZAeKUjHm0NPkToAQuST5At2GTodES24g42e3q2hb/9mmG5/T/ANzWG7/z4LxU9lL+DGfQsfeP/pk4y4Mnabtyftff4N4hoxFA5QJr+lCcMjGLM7XdJboovRwIn56Nb+97eUzz/gMbtGQ+99jclnfujiozMaDLoq2TNiWlPSNBPt6WNnA8av/LJOZsulx0A+dgs9jU2oDfhNRBPDwVlGOasOdhQXrG3GhlAE2ClEURFwbX46cbSyatAJd0XnMd8cJA1zgawxbYHemg/mNBk9tYN0Pn6/sjjdvKYVyjn1qKxdWmbJ90GYGGrKDCXwuwwB4VW7ZiG6jifLoKHCXiXf1bvVp3TKDGedU3u+bwwgh5wJ/yDSUmoB02kakU1KpzBynfntL6stcCNzMQ6LBTgirGL18kJS4596x81XHPBIhe3kfJ7QfzA9YnDkMc="
    }
}
