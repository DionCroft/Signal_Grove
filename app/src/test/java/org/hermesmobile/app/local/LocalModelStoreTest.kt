package org.hermesmobile.app.local

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalModelStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private fun fixture(arch: String = "lfm3"): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            fun u32(n: Int) = out.writeInt(Integer.reverseBytes(n))
            fun u64(n: Long) = out.writeLong(java.lang.Long.reverseBytes(n))
            fun text(s: String) { val b = s.toByteArray(); u64(b.size.toLong()); out.write(b) }
            fun field(key: String, value: String) { text(key); u32(8); text(value) }
            u32(0x46554747); u32(3); u64(1); u64(4)
            field("general.name", "Fixture"); field("general.architecture", arch)
            text("$arch.context_length"); u32(4); u32(2048)
            field("tokenizer.chat_template", "{{ bos_token }}{{ messages }}")
            text("weight"); u32(1); u64(1); u32(0); u64(0)
            while (bytes.size() % 32 != 0) out.writeByte(0)
            out.write(ByteArray(4))
        }
        return bytes.toByteArray()
    }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun store(root: File) = LocalModelStore(root) { Long.MAX_VALUE }

    @Test fun importsByContentHashAndVerifiesBeforeLoad() {
        val root = temp.newFolder(); val store = store(root); val bytes = fixture()
        val result = store.importModel(bytes.inputStream(), true, hash(bytes))
        assertEquals(hash(bytes), result.sha256)
        assertEquals("lfm3", result.metadata.architecture)
        assertArrayEquals(bytes, store.verifiedFile(result.sha256).readBytes())
        assertEquals(listOf(result), store.models())
        assertEquals(listOf(result.sha256 + ".gguf"), root.listFiles()!!.map { it.name })
    }
    @Test fun supportsOtherValidatedArchitecturesWithoutClaimingNativeCompatibility() {
        val store = store(temp.newFolder())
        assertEquals("qwen3", store.importModel(fixture("qwen3").inputStream(), true).metadata.architecture)
    }
    @Test fun duplicateImportDoesNotReplaceInstalledBytes() {
        val root = temp.newFolder(); val store = store(root); val bytes = fixture()
        val first = store.importModel(bytes.inputStream(), true)
        val before = store.verifiedFile(first.sha256).lastModified()
        assertEquals(first, store.importModel(bytes.inputStream(), true))
        assertEquals(1, store.models().size)
        assertEquals(before, store.verifiedFile(first.sha256).lastModified())
        assertFalse(root.listFiles()!!.any { it.name.endsWith(".part") })
    }
    @Test fun checksumFailureNeverBecomesInstalled() {
        val root = temp.newFolder(); val store = store(root)
        val e = assertThrows(LocalModelStorageException::class.java) { store.importModel(fixture().inputStream(), true, "0".repeat(64)) }
        assertEquals("checksum_mismatch", e.reason); assertTrue(root.listFiles()!!.isEmpty())
    }
    @Test fun malformedAndUnsupportedImportsLeaveNoInstalledOrPartialFile() {
        for (bytes in listOf(ByteArray(100), fixture("unknown"))) {
            val root = temp.newFolder(); val store = store(root)
            assertThrows(LocalModelFormatException::class.java) { store.importModel(bytes.inputStream(), true) }
            assertTrue(root.listFiles()!!.isEmpty())
        }
    }
    @Test fun licenceAcknowledgementIsRequiredBeforeAnyCopy() {
        val root = temp.newFolder(); val store = store(root)
        assertThrows(IllegalArgumentException::class.java) { store.importModel(fixture().inputStream(), false) }
        assertTrue(root.listFiles()!!.isEmpty())
    }
    @Test fun cancellationClosesInputAndRemovesOnlyItsOwnPartial() {
        val root = temp.newFolder(); val store = store(root)
        val retained = File(root, "another-download.part").also { it.writeText("retain") }
        var closed = false
        val input = object : java.io.ByteArrayInputStream(fixture()) { override fun close() { closed = true; super.close() } }
        assertThrows(CancellationException::class.java) { store.importModel(input, true, checkpoint = { throw CancellationException("cancel") }) }
        assertTrue(closed); assertEquals("retain", retained.readText())
        assertEquals(listOf(retained), root.listFiles()!!.toList())
        assertTrue(store.models().isEmpty())
    }
    @Test fun leavesSpaceReserveAndDoesNotCommitOnDiskFailure() {
        val root = temp.newFolder(); val store = LocalModelStore(root) { LocalModelStore.SPACE_RESERVE }
        val e = assertThrows(LocalModelStorageException::class.java) { store.importModel(fixture().inputStream(), true) }
        assertEquals("insufficient_space", e.reason); assertTrue(root.listFiles()!!.isEmpty())
    }
    @Test fun installedTamperingIsDetectedAndPreservedForExplicitDeletion() {
        val store = store(temp.newFolder()); val result = store.importModel(fixture().inputStream(), true)
        val file = store.verifiedFile(result.sha256); file.appendBytes(byteArrayOf(7))
        val e = assertThrows(LocalModelStorageException::class.java) { store.verifiedFile(result.sha256) }
        assertEquals("checksum_mismatch", e.reason); assertTrue(file.exists())
        store.delete(result.sha256); assertFalse(file.exists()); assertTrue(store.models().isEmpty())
    }
    @Test fun corruptExistingIdentityIsNeverOverwrittenByAnImport() {
        val root = temp.newFolder(); val store = store(root); val bytes = fixture()
        val existing = File(root, hash(bytes) + ".gguf").also { it.writeBytes(ByteArray(100)) }
        val e = assertThrows(LocalModelStorageException::class.java) { store.importModel(bytes.inputStream(), true) }
        assertEquals("existing_model_corrupt", e.reason); assertArrayEquals(ByteArray(100), existing.readBytes())
    }
    @Test fun deletionCannotEscapeThePrivateLibrary() {
        val root = temp.newFolder(); val store = store(root)
        val other = temp.newFile().also { it.writeText("preserve") }
        assertThrows(IllegalArgumentException::class.java) { store.delete("../" + other.name) }
        assertEquals("preserve", other.readText())
    }
}
