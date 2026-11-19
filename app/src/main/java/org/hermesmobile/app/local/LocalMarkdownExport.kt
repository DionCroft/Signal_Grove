package org.hermesmobile.app.local

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.time.Instant

enum class LocalMarkdownExportFailure { CHANGED, ACTIVE, EMPTY, INVALID, TOO_LARGE }
class LocalMarkdownExportException(val reason: LocalMarkdownExportFailure) : IllegalStateException("local_markdown_export_" + reason.name.lowercase())

/** A frozen selected-chat document. No paths, model weights, server state or other chats are read. */
class LocalMarkdownExport private constructor(val chatId: String, val modelSha256: String, val updatedAt: Long,
    val title: String?, val exchanges: Int, val sourceSha256: String, private val payload: ByteArray) {
    val byteCount: Int get() = payload.size
    val documentSha256: String = hash(payload)
    val suggestedFilename: String = "SignalGrove-phone-chat-${chatId.take(8)}.md"

    /** The caller owns the destination and its close/flush acknowledgement. Failure is never retried.
     * Copy each bounded chunk so a consumer cannot mutate the reviewed document's private bytes. */
    fun writeTo(output: OutputStream, checkpoint: () -> Unit = {}) {
        var offset = 0
        checkpoint()
        while (offset < payload.size) {
            checkpoint()
            val end = minOf(offset + 8192, payload.size)
            output.write(payload.copyOfRange(offset, end))
            offset = end
        }
        checkpoint()
    }

    companion object {
        const val MAX_BYTES = 12 * 1024 * 1024
        internal fun create(value: LocalConversation, sourceSha256: String, title: String?, checkpoint: () -> Unit): LocalMarkdownExport {
            if (value.exchanges.isEmpty()) throw LocalMarkdownExportException(LocalMarkdownExportFailure.EMPTY)
            if (value.exchanges.any {it.state == LocalReplyState.GENERATING}) throw LocalMarkdownExportException(LocalMarkdownExportFailure.ACTIVE)
            require(sourceSha256.matches(Regex("[a-f0-9]{64}")))
            val output = ByteArrayOutputStream()
            fun encoded(text: String): ByteArray = try {
                val bytes = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text))
                ByteArray(bytes.remaining()).also {bytes.get(it)}
            } catch (_: java.nio.charset.CharacterCodingException) {throw LocalMarkdownExportException(LocalMarkdownExportFailure.INVALID)}
            fun append(text: String) {
                checkpoint()
                val bytes = encoded(text)
                if (bytes.size > MAX_BYTES - output.size()) throw LocalMarkdownExportException(LocalMarkdownExportFailure.TOO_LARGE)
                output.write(bytes)
            }
            // Literal fenced blocks keep model/user Markdown, HTML and links as document text.
            // Choose a delimiter longer than every matching run, including malicious closing fences.
            fun literal(text: String) {
                var backticks = 0; var tildes = 0; var maxBackticks = 0; var maxTildes = 0
                text.forEachIndexed {index, c ->
                    if (index % 8192 == 0) checkpoint()
                    backticks = if (c == '`') backticks + 1 else 0
                    tildes = if (c == '~') tildes + 1 else 0
                    maxBackticks = maxOf(maxBackticks, backticks); maxTildes = maxOf(maxTildes, tildes)
                }
                val fence = if (maxBackticks <= maxTildes) "`".repeat(maxOf(3, maxBackticks + 1)) else "~".repeat(maxOf(3, maxTildes + 1))
                append(fence + "text\n"); append(text); append("\n" + fence + "\n\n")
            }
            if (title != null && encoded(title).size !in 1..4096) throw LocalMarkdownExportException(LocalMarkdownExportFailure.INVALID)
            append("# Signal Grove phone conversation\n\nMarkdown export format: 1\n\n")
            append("Conversation ID: ${value.id}\n\nModel SHA-256: ${value.modelSha256}\n\n")
            append("Saved activity (UTC): ${Instant.ofEpochMilli(value.updatedAt)}\n\nSource snapshot SHA-256: $sourceSha256\n\n")
            title?.let {append("## Title\n\n"); literal(it)}
            value.exchanges.forEachIndexed {index, row ->
                checkpoint()
                append("## Exchange ${index + 1}\n\n### User\n\n"); literal(row.question)
                append("### Assistant (${row.state.name})\n\n"); literal(row.reply)
                row.generation?.let {settings ->
                    settings.validate()
                    append("Generation settings: context=${settings.contextTokens}; threads=${settings.threads}; output_limit=${settings.maxOutputTokens}\n\n")
                    settings.runtime?.let {runtime -> append("Runtime source SHA-256: ${runtime.sourceSha256}\n\nSampling policy: ${runtime.sampling.name}\n\n")}
                } ?: append("Generation settings: not recorded\n\n")
            }
            checkpoint()
            return LocalMarkdownExport(value.id, value.modelSha256, value.updatedAt, title, value.exchanges.size,
                sourceSha256, output.toByteArray())
        }
        private fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)}
    }
}
