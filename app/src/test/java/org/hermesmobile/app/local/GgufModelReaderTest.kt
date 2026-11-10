package org.hermesmobile.app.local

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GgufModelReaderTest {
    @get:Rule val temp = TemporaryFolder()
    private fun DataOutputStream.u32(n: Long) = writeInt(Integer.reverseBytes(n.toInt()))
    private fun DataOutputStream.u64(n: Long) = writeLong(java.lang.Long.reverseBytes(n))
    private fun DataOutputStream.text(s: String) { val bytes = s.toByteArray(); u64(bytes.size.toLong()); write(bytes) }
    private fun file(arch: String = "lfm3", template: String? = "{{ bos_token }}{% for message in messages %}{{ message.content }}{% endfor %}",
        context: Long = 262144, tensorType: Int = 0, dimension: Long = 1, offset: Long = 0,
        truncate: Boolean = false, duplicateKey: Boolean = false, name: String = "Test model", alignment: Long = 32): File {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { w ->
            w.u32(0x46554747); w.u32(3); w.u64(1); w.u64(5L + (if (template != null) 1 else 0) + (if (duplicateKey) 1 else 0))
            fun field(key: String, value: String) { w.text(key); w.u32(8); w.text(value) }
            field("general.name", name); field("general.architecture", arch)
            w.text("$arch.context_length"); w.u32(4); w.u32(context)
            w.text("general.alignment"); w.u32(4); w.u32(alignment)
            w.text("tokenizer.ggml.tokens"); w.u32(9); w.u32(8); w.u64(3); w.text("hello"); w.text("你好"); w.text("🌱")
            if (template != null) field("tokenizer.chat_template", template)
            if (duplicateKey) field("general.name", "other")
            w.text("weight"); w.u32(1); w.u64(dimension); w.u32(tensorType.toLong()); w.u64(offset)
            while (bytes.size() % 32 != 0) w.writeByte(0)
            if (!truncate) w.write(ByteArray(256))
        }
        return temp.newFile().also { it.writeBytes(bytes.toByteArray()) }
    }
    private fun rejected(expected: String, file: File) {
        val error = assertThrows(LocalModelFormatException::class.java) { GgufModelReader.read(file) }
        assertEquals(expected, error.reason)
    }
    @Test fun readsBoundedMetadataAndSkipsTokenizerArrays() {
        val f = file(); val value = GgufModelReader.read(f)
        assertEquals("lfm3", value.architecture); assertEquals(262144, value.trainedContext); assertEquals(f.length(), value.bytes)
        assertTrue(value.chatTemplate.contains("messages"))
    }
    @Test fun recognizesOtherSupportedArchitectureWithoutClaimingNativeLoad() { assertEquals("qwen3", GgufModelReader.read(file(arch = "qwen3")).architecture) }
    @Test fun rejectsUnknownArchitecture() { rejected("unsupported_architecture", file(arch = "not-an-architecture")) }
    @Test fun rejectsMissingChatTemplate() { rejected("missing_template", file(template = null)) }
    @Test fun rejectsBlankTemplate() { rejected("template_size", file(template = "  ")) }
    @Test fun rejectsOversizedTemplate() { rejected("metadata_limit", file(template = "x".repeat(65537))) }
    @Test fun rejectsInvalidContext() { rejected("metadata_limit", file(context = 0)) }
    @Test fun rejectsDuplicateMetadata() { rejected("duplicate_metadata", file(duplicateKey = true)) }
    @Test fun stripsControlAndBidiTextFromDisplayName() { assertEquals("Testname", GgufModelReader.read(file(name = "Test\u202e\nname")).name) }
    @Test fun rejectsInvalidAlignment() { rejected("tensor_alignment", file(alignment = 3)) }
    @Test fun rejectsUnsupportedTensorEncoding() { rejected("unsupported_tensor_type", file(tensorType = 255)) }
    @Test fun rejectsMisalignedTensorOffsets() { rejected("tensor_alignment", file(offset = 1)) }
    @Test fun rejectsTruncatedTensorBody() { rejected("tensor_range", file(truncate = true)) }
    @Test fun rejectsTensorThatExtendsBeyondFile() { rejected("tensor_range", file(dimension = 1000)) }
    @Test fun rejectsImpossibleQuantizedRow() { rejected("tensor_block", file(tensorType = 2, dimension = 3)) }
    @Test fun permitsQ4BlocksWithValidRanges() { assertEquals("lfm3", GgufModelReader.read(file(tensorType = 2, dimension = 32)).architecture) }
    @Test fun rejectsBadMagicAndTruncatedHeaders() {
        rejected("gguf_version", file().also { val b = it.readBytes(); b[0] = 0; it.writeBytes(b) })
        rejected("model_size", temp.newFile().also { it.writeText("GGUF") })
    }
    @Test fun rejectsHugeUnsignedFieldCountsWithoutAllocation() {
        val f = file(); val b = f.readBytes(); for (i in 16..23) b[i] = 0x7f; f.writeBytes(b)
        rejected("metadata_limit", f)
    }
    @Test fun rejectsUnsupportedVersion() {
        val f = file(); val b = f.readBytes(); b[4] = 2; f.writeBytes(b); rejected("gguf_version", f)
    }
    @Test fun rejectsMalformedUtf8() {
        val f = file(); val b = f.readBytes(); val needle = "Test model".toByteArray(); val at = b.indices.first { it + needle.size <= b.size && b.copyOfRange(it, it + needle.size).contentEquals(needle) }
        b[at] = 0xff.toByte(); f.writeBytes(b); rejected("malformed_gguf", f)
    }
    @Test fun cancellationIsNotReportedAsAMalformedModel() {
        val model = file()
        assertThrows(CancellationException::class.java) { GgufModelReader.read(model) { throw CancellationException("cancelled") } }
    }
    @Test fun checksCancellationWhileScanningALargeVocabulary() {
        val model = file()
        // Increase an otherwise valid tokenizer array; the scan must checkpoint inside it.
        val bytes = model.readBytes()
        val key = "tokenizer.ggml.tokens".toByteArray()
        val keyAt = bytes.indices.first { it + key.size <= bytes.size && bytes.copyOfRange(it, it + key.size).contentEquals(key) }
        val arrayStart = keyAt + key.size
        val replacement = ByteArrayOutputStream()
        DataOutputStream(replacement).use { w -> w.u32(9); w.u32(8); w.u64(10000); repeat(10000) { w.text("token") } }
        // Existing array: type 4 + subtype 4 + count 8 + three length-prefixed UTF-8 strings.
        val oldSize = 16 + 8 * 3 + "hello你好🌱".toByteArray().size
        model.writeBytes(bytes.copyOfRange(0, arrayStart) + replacement.toByteArray() + bytes.copyOfRange(arrayStart + oldSize, bytes.size))
        var checkpoints = 0
        assertThrows(CancellationException::class.java) { GgufModelReader.read(model) { if (++checkpoints > 2) throw CancellationException("cancelled scan") } }
        assertEquals(3, checkpoints)
    }
}
