package org.hermesmobile.app.local

import java.io.File
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.CancellationException

data class GgufModelMetadata(val name: String, val architecture: String, val trainedContext: Int, val chatTemplate: String, val bytes: Long)
class LocalModelFormatException(val reason: String) : IllegalArgumentException(reason)

/** Bounded preflight, including tensor ranges. Native loading is a separate compatibility gate. */
object GgufModelReader {
    const val MAX_MODEL_BYTES = 4L * 1024 * 1024 * 1024
    private const val HEADER_LIMIT = 32L * 1024 * 1024
    private val architectures = setOf("lfm3", "lfm2", "llama", "qwen2", "qwen3", "gemma", "gemma2", "gemma3")
    // GGML block length / byte size at pinned llama.cpp b12255. Other types fail explicitly.
    private val tensorTypes = mapOf(0 to (1 to 4), 1 to (1 to 2), 2 to (32 to 18), 3 to (32 to 20),
        6 to (32 to 22), 7 to (32 to 24), 8 to (32 to 34), 9 to (32 to 40),
        10 to (256 to 84), 11 to (256 to 110), 12 to (256 to 144), 13 to (256 to 176),
        14 to (256 to 210), 15 to (256 to 292), 30 to (1 to 2))

    fun read(file: File, checkpoint: () -> Unit = {}): GgufModelMetadata {
        try {
            checkpoint()
            val length = file.length()
            if (!file.isFile || length !in 24..MAX_MODEL_BYTES) fail("model_size")
            DataInputStream(BufferedInputStream(file.inputStream(), 64 * 1024)).use { input ->
                val r = Reader(input, length, checkpoint)
                if (r.u32() != 0x46554747L || r.u32() != 3L) fail("gguf_version")
                val tensors = r.u64().bounded(1, 100_000).toInt()
                val fields = r.u64().bounded(1, 65_536).toInt()
                val keys = HashSet<String>()
                val values = HashMap<String, Any>()
                repeat(fields) {
                    val key = r.string(256)
                    if (!keys.add(key)) fail("duplicate_metadata")
                    val selected = key in setOf("general.name", "general.architecture", "general.alignment", "tokenizer.chat_template", "split.count") || key.endsWith(".context_length")
                    r.value(r.u32().toInt(), selected)?.let { values[key] = it }
                }
                val arch = values["general.architecture"] as? String ?: fail("missing_architecture")
                if (arch !in architectures) fail("unsupported_architecture")
                val context = (values["$arch.context_length"] as? Long)?.bounded(128, 1_048_576)?.toInt() ?: fail("missing_context")
                val template = values["tokenizer.chat_template"] as? String ?: fail("missing_template")
                if (template.isBlank() || template.toByteArray(Charsets.UTF_8).size > 65_536) fail("template_size")
                if ((values["split.count"] as? Long ?: 1) != 1L) fail("split_model_unsupported")
                val alignment = (values["general.alignment"] as? Long ?: 32).bounded(1, 4096)
                if (alignment and (alignment - 1) != 0L) fail("tensor_alignment")
                val ranges = ArrayList<Pair<Long, Long>>()
                val names = HashSet<String>()
                repeat(tensors) {
                    if (!names.add(r.string(256))) fail("duplicate_tensor")
                    val dimensions = r.u32().bounded(1, 4).toInt()
                    val shape = List(dimensions) { r.u64().bounded(1, 1_048_576) }
                    val type = tensorTypes[r.u32().toInt()] ?: fail("unsupported_tensor_type")
                    if (shape[0] % type.first != 0L) fail("tensor_block")
                    var bytes = Math.multiplyExact(shape[0] / type.first, type.second.toLong())
                    shape.drop(1).forEach { bytes = Math.multiplyExact(bytes, it) }
                    val offset = r.u64()
                    if (offset % alignment != 0L) fail("tensor_alignment")
                    ranges.add(offset to Math.addExact(offset, bytes))
                }
                val dataStart = Math.addExact(r.position, alignment - 1) / alignment * alignment
                var previousEnd = 0L
                ranges.sortedBy { it.first }.forEach { (start, end) ->
                    if (start < previousEnd || end > length - dataStart) fail("tensor_range")
                    previousEnd = end
                }
                val name = (values["general.name"] as? String).orEmpty().filter { !it.isISOControl() && Character.getType(it) != Character.FORMAT.toInt() }.take(120).ifBlank { arch }
                return GgufModelMetadata(name, arch, context, template, length)
            }
        } catch (e: CancellationException) { throw e }
        catch (e: LocalModelFormatException) { throw e }
        catch (_: Exception) { fail("malformed_gguf") }
    }

    private fun fail(reason: String): Nothing = throw LocalModelFormatException(reason)
    private fun Long.bounded(min: Long, max: Long): Long { if (this !in min..max) fail("metadata_limit"); return this }
    private class Reader(val input: DataInputStream, val length: Long, val checkpoint: () -> Unit) {
        var position = 0L
            private set
        private var operations = 0
        fun reserve(n: Long) {
            if (++operations % 256 == 0) checkpoint()
            if (n < 0 || n > minOf(length, HEADER_LIMIT) - position) fail("header_limit")
        }
        fun skip(n: Long) {
            reserve(n)
            var remaining = n
            while (remaining > 0) {
                val skipped = input.skip(remaining)
                if (skipped > 0) remaining -= skipped
                else { if (input.read() < 0) throw EOFException(); remaining-- }
            }
            position += n
        }
        fun u32(): Long { reserve(4); val value = Integer.reverseBytes(input.readInt()).toLong() and 0xffffffffL; position += 4; return value }
        fun u64(): Long { reserve(8); val value = java.lang.Long.reverseBytes(input.readLong()); position += 8; if (value < 0) fail("integer_overflow"); return value }
        fun string(max: Int): String {
            val size = u64().bounded(0, max.toLong()).toInt(); reserve(size.toLong())
            val bytes = ByteArray(size); input.readFully(bytes); position += size
            return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        }
        fun value(type: Int, keep: Boolean): Any? {
            if (type == 8) {
                if (keep) return string(65_536)
                val size = u64().bounded(0, 1_048_576); skip(size); return null
            }
            if (type == 9) {
                val inner = u32().toInt(); val count = u64().bounded(0, 1_048_576)
                if (inner == 9 || inner !in 0..12) fail("metadata_array_type")
                if (inner == 8) repeat(count.toInt()) { value(inner, false) }
                else skip(Math.multiplyExact(count, size(inner).toLong()))
                return null
            }
            if (keep && type == 4) return u32()
            if (keep && type == 10) return u64()
            if (keep && type == 5) { reserve(4); val value = Integer.reverseBytes(input.readInt()).toLong(); position += 4; return value }
            skip(size(type).toLong()); return null
        }
        private fun size(type: Int): Int = when (type) { 0, 1, 7 -> 1; 2, 3 -> 2; 4, 5, 6 -> 4; 10, 11, 12 -> 8; else -> fail("metadata_type") }
    }
}
