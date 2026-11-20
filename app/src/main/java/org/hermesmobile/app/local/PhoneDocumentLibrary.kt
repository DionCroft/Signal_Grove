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
    val id: String, val name: String, val fingerprint: String, val byteCount: Int, val lineCount: Int,
    val pageCount: Int = 0
)

internal data class PhoneDocumentText(val info: PhoneDocument, val sections: List<String>)

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
        validateName(name, pdf = false)
        requireWritable(previous)
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
        }.let(::normalize)
        validateSections(false, listOf(text))
        write(buildJsonObject {
            put("version", 1)
            put("name", name)
            put("text", text)
        }, previous, check)
    }

    @Synchronized fun savePdf(name: String, pages: List<String>, previous: PhoneDocument?, check: () -> Unit) {
        validateName(name, pdf = true)
        requireWritable(previous)
        val normalized = pages.map(::normalize)
        validateSections(true, normalized)
        write(buildJsonObject {
            put("version", 2)
            put("name", name)
            put("pages", JsonArray(normalized.map(::JsonPrimitive)))
        }, previous, check)
    }

    private fun requireWritable(previous: PhoneDocument?) {
        val records = files()
        if (previous == null && records.size >= MAX_DOCUMENTS) problem("document_limit")
        if (previous != null) requireCurrent(previous)
    }

    private fun write(fields: JsonObject, previous: PhoneDocument?, check: () -> Unit) {
        val target = record(previous?.id ?: UUID.randomUUID().toString())
        val value = fields.toString().toByteArray(Charsets.UTF_8)
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
            val version = fields.getValue("version").jsonPrimitive.int
            require(version in 1..2)
            val pdf = version == 2
            val name = fields.getValue("name").jsonPrimitive.content
            validateName(name, pdf)
            val sections = if (pdf) {
                fields.getValue("pages").jsonArray.map { element ->
                    element.jsonPrimitive.also { require(it.isString) }.content
                }
            } else {
                listOf(fields.getValue("text").jsonPrimitive.also { require(it.isString) }.content)
            }
            val bytes = validateSections(pdf, sections)
            val content = if (pdf) JsonArray(sections.map(::JsonPrimitive)).toString() else sections.single()
            val hash = MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            return PhoneDocumentText(PhoneDocument(safe.name.removeSuffix(".json"), name, hash, bytes,
                if (pdf) 0 else sections.single().count { it == '\n' } + 1,
                if (pdf) sections.size else 0), sections)
        } catch (_: Exception) {
            problem("document_store")
        }
    }

    private fun validateName(name: String, pdf: Boolean) {
        val supported = if (pdf) isPdf(name) else name.substringAfterLast('.', "").lowercase() in TEXT_EXTENSIONS
        if (!supported || name.length !in 1..160 || name.any {
                it in "/\\" || it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt()
            }) problem("document_format")
    }

    private fun normalize(text: String) = text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')

    private fun validateSections(pdf: Boolean, sections: List<String>): Int {
        if (pdf && sections.size !in 1..MAX_PDF_PAGES) problem("document_pdf_page_limit")
        if (sections.all(String::isBlank)) problem(if (pdf) "document_pdf_image_only" else "document_empty")
        var bytes = 0
        val encoder = Charsets.UTF_8.newEncoder()
        for (text in sections) {
            if (text.length > MAX_DOCUMENT_BYTES - bytes) problem("document_size")
            if (text.any { it.isISOControl() && it !in "\n\t" } || !encoder.canEncode(text)) problem("document_encoding")
            bytes += text.toByteArray(Charsets.UTF_8).size
            if (bytes > MAX_DOCUMENT_BYTES) problem("document_size")
        }
        return bytes
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
        const val MAX_PDF_PAGES = 64
        fun isPdf(name: String) = name.endsWith(".pdf", ignoreCase = true)
        private const val MAX_RECORD_BYTES = 3L * MAX_DOCUMENT_BYTES + 1024
        private val TEXT_EXTENSIONS = setOf("txt", "md", "markdown")
    }
}
