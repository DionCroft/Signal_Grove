package org.hermesmobile.app.local

import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.json.*

data class PhoneDocument(
    val id: String, val name: String, val fingerprint: String, val byteCount: Int, val lineCount: Int
)

internal data class PhoneDocumentText(val info: PhoneDocument, val text: String)

/** Private extracted text. No provider URI, original file, grant, or separate persistent index. */
internal class PhoneDocumentLibrary(root: File) {
    private val directory = root.canonicalFile
    private val idFormat = Regex("[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}")
    private val abandoned = Regex("stage-[a-f0-9-]{36}\\.tmp")
    private var ready = false

    @Synchronized fun list(check: () -> Unit = {}): List<PhoneDocument> {
        return files().map {
            check()
            read(it).info
        }.sortedWith(compareBy(PhoneDocument::name, PhoneDocument::id))
    }

    @Synchronized fun selected(ids: List<String>, check: () -> Unit): List<PhoneDocumentText> {
        require(ids.size in 1..MAX_SCOPE && ids.toSet().size == ids.size)
        return ids.map { id ->
            check()
            read(record(id), missing = "document_changed")
        }
    }

    @Synchronized fun save(name: String, input: InputStream, previous: PhoneDocument?, check: () -> Unit) {
        validateName(name)
        val records = files()
        if (previous == null && records.size >= MAX_DOCUMENTS) problem("document_limit")
        if (previous != null) requireCurrent(previous)
        val bytes = ByteArray(MAX_DOCUMENT_BYTES + 1)
        var length = 0
        while (length < bytes.size) {
            check()
            val count = input.read(bytes, length, bytes.size - length)
            if (count < 0) break
            if (count == 0) continue
            length += count
        }
        if (length > MAX_DOCUMENT_BYTES) problem("document_size")
        val text = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes, 0, length)).toString()
        } catch (_: CharacterCodingException) {
            problem("document_encoding")
        }.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        validateText(text)
        val id = previous?.id ?: UUID.randomUUID().toString()
        val target = record(id)
        val value = buildJsonObject {
            put("version", 1)
            put("name", name)
            put("text", text)
        }.toString().toByteArray(Charsets.UTF_8)
        val stage = owned("stage-${UUID.randomUUID()}.tmp")
        kotlin.check(stage.createNewFile())
        try {
            stage.outputStream().use {
                it.write(value)
                it.fd.sync()
            }
            check()
            Files.move(stage.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(stage.toPath())
        }
    }

    @Synchronized fun remove(expected: PhoneDocument, check: () -> Unit) {
        requireCurrent(expected)
        check()
        Files.delete(record(expected.id).toPath())
    }

    private fun requireCurrent(expected: PhoneDocument) {
        files()
        if (read(record(expected.id), missing = "document_changed").info != expected) problem("document_changed")
    }

    private fun files(): List<File> {
        if (!directory.isDirectory && !directory.mkdirs()) problem("document_store")
        val files = directory.listFiles() ?: problem("document_store")
        if (!ready) {
            files.filter { abandoned.matches(it.name) }.forEach {
                Files.deleteIfExists(owned(it.name).toPath())
            }
            ready = true
        }
        val records = files.filter { it.name.endsWith(".json") && idFormat.matches(it.name.removeSuffix(".json")) }
        if (records.size > MAX_DOCUMENTS) problem("document_store")
        return records
    }

    private fun read(file: File, missing: String = "document_store"): PhoneDocumentText {
        val safe = owned(file.name)
        if (!safe.isFile) problem(missing)
        if (safe.length() !in 1..MAX_RECORD_BYTES) problem("document_store")
        try {
            val fields = Json.parseToJsonElement(safe.readText(Charsets.UTF_8)).jsonObject
            require(fields["version"]?.jsonPrimitive?.int == 1)
            val name = fields.getValue("name").jsonPrimitive.content
            val text = fields.getValue("text").jsonPrimitive.content
            validateName(name)
            validateText(text)
            val encoded = text.toByteArray(Charsets.UTF_8)
            require(encoded.size <= MAX_DOCUMENT_BYTES)
            val hash = MessageDigest.getInstance("SHA-256").digest(encoded).joinToString("") { "%02x".format(it) }
            return PhoneDocumentText(PhoneDocument(safe.name.removeSuffix(".json"), name, hash,
                encoded.size, text.count { it == '\n' } + 1), text)
        } catch (_: Exception) {
            problem("document_store")
        }
    }

    private fun validateName(name: String) {
        if (name.length !in 1..160 || name.any { it in "/\\" || it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() } ||
            name.substringAfterLast('.', "").lowercase() !in TEXT_EXTENSIONS) problem("document_format")
    }

    private fun validateText(text: String) {
        if (text.isBlank()) problem("document_empty")
        if (text.any { it.isISOControl() && it !in "\n\t" } || !Charsets.UTF_8.newEncoder().canEncode(text)) problem("document_encoding")
    }

    private fun record(id: String): File {
        require(idFormat.matches(id))
        return owned("$id.json")
    }

    private fun owned(name: String): File {
        val file = File(directory, name)
        check(file.parentFile == directory && file.canonicalFile == file && !Files.isSymbolicLink(file.toPath()))
        return file
    }

    private fun problem(reason: String): Nothing = throw LocalModelStorageException(reason)

    companion object {
        const val MAX_DOCUMENTS = 12
        const val MAX_SCOPE = 4
        const val MAX_DOCUMENT_BYTES = 128 * 1024
        private const val MAX_RECORD_BYTES = 3L * MAX_DOCUMENT_BYTES + 1024
        private val TEXT_EXTENSIONS = setOf("txt", "md", "markdown")
    }
}
