package org.hermesmobile.app.local

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*

internal data class PhoneWritingTemplate(val id: String, val title: String, val instructions: String)

internal data class PhoneWritingNote(val id: String, val text: String, val atLimit: Boolean)

internal data class PhoneWritingLibrary(
    val templates: List<PhoneWritingTemplate> = emptyList(),
    val notes: List<PhoneWritingNote> = emptyList()
) {
    fun save(template: PhoneWritingTemplate, previous: PhoneWritingTemplate?): PhoneWritingLibrary {
        require(validTitle(template.title) && validInstructions(template.instructions))
        if (previous == null) {
            require(templates.size < LIMIT && templates.none { it.id == template.id })
            return copy(templates = templates + template)
        }
        require(previous in templates && template.id == previous.id)
        return copy(templates = templates.map { if (it.id == template.id) template else it })
    }

    fun save(note: PhoneWritingNote): PhoneWritingLibrary {
        require(validOutput(note.text))
        if (notes.any { it == note }) return this
        require(notes.size < LIMIT && notes.none { it.id == note.id })
        return copy(notes = listOf(note) + notes)
    }

    fun encode(): String = buildJsonObject {
        put("version", 1)
        put("templates", buildJsonArray {
            templates.forEach { template ->
                add(buildJsonObject {
                    put("id", template.id)
                    put("title", template.title)
                    put("instructions", template.instructions)
                })
            }
        })
        put("notes", buildJsonArray {
            notes.forEach { note ->
                add(buildJsonObject {
                    put("id", note.id)
                    put("text", note.text)
                    put("atLimit", note.atLimit)
                })
            }
        })
    }.toString()

    companion object {
        const val LIMIT = 16
        private val idFormat = Regex("[a-zA-Z0-9-]{1,64}")

        fun decode(value: String): PhoneWritingLibrary {
            require(value.length <= 1_000_000)
            val fields = Json.parseToJsonElement(value).jsonObject
            require(fields.getValue("version").jsonPrimitive.int == 1)
            val templates = fields.getValue("templates").jsonArray
            val notes = fields.getValue("notes").jsonArray
            require(templates.size <= LIMIT && notes.size <= LIMIT)
            val savedTemplates = templates.map { row ->
                val item = row.jsonObject
                PhoneWritingTemplate(item.text("id"), item.text("title"), item.text("instructions")).also {
                    require(idFormat.matches(it.id) && validTitle(it.title) && validInstructions(it.instructions))
                }
            }
            val savedNotes = notes.map { row ->
                val item = row.jsonObject
                PhoneWritingNote(item.text("id"), item.text("text"), item.getValue("atLimit").jsonPrimitive.boolean).also {
                    require(idFormat.matches(it.id) && validOutput(it.text))
                }
            }
            require(savedTemplates.distinctBy { it.id }.size == templates.size)
            require(savedNotes.distinctBy { it.id }.size == notes.size)
            return PhoneWritingLibrary(savedTemplates, savedNotes)
        }

        private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.also { require(it.isString) }.content
    }
}

internal data class PhoneWritingDraft(
    val instructions: String = "",
    val source: String = "",
    val title: String = "",
    val template: PhoneWritingTemplate? = null,
    val incoming: String? = null,
    val incomingId: String? = null,
    val intakeError: Boolean = false
) {
    val canReview: Boolean get() = validInstructions(instructions) && validWritingSource(source)
}

internal class PhoneWritingReview internal constructor(
    val id: String,
    val instructions: String,
    val source: String,
    val modelSha256: String,
    val modelName: String
) {
    fun messages() = listOf(LocalChatMessage("system", instructions), LocalChatMessage("user", source))
}

/** Process-memory drafts have no route to Hermes or either chat composer. */
internal class PhoneWritingWorkspace {
    private val mutableDraft = MutableStateFlow(PhoneWritingDraft())
    val draft = mutableDraft.asStateFlow()
    private val mutableReview = MutableStateFlow<PhoneWritingReview?>(null)
    val review = mutableReview.asStateFlow()
    private val mutableResult = MutableStateFlow<PhoneWritingNote?>(null)
    val result = mutableResult.asStateFlow()

    fun editInstructions(value: String) {
        if (value.length <= MAX_WRITING_INSTRUCTIONS) edit(mutableDraft.value.copy(instructions = value))
    }
    fun editSource(value: String) {
        if (value.length <= MAX_WRITING_SOURCE) edit(mutableDraft.value.copy(source = value))
    }
    fun editTitle(value: String) {
        if (value.length <= 64) mutableDraft.value = mutableDraft.value.copy(title = value)
    }
    fun choosePreset(instructions: String) {
        require(validInstructions(instructions))
        edit(mutableDraft.value.copy(instructions = instructions, template = null, title = ""))
    }
    fun chooseTemplate(template: PhoneWritingTemplate) = edit(mutableDraft.value.copy(
        instructions = template.instructions, title = template.title, template = template
    ))
    fun templateSaved(template: PhoneWritingTemplate, from: PhoneWritingDraft) {
        if (mutableDraft.value == from) mutableDraft.value = from.copy(template = template, title = template.title)
    }

    fun receive(text: String?) {
        val latest = mutableDraft.value
        val valid = text != null && validWritingSource(text) && latest.incoming == null
        // A second share cannot replace a draft or an earlier share awaiting review.
        edit(latest.copy(incoming = if (valid) text else latest.incoming,
            incomingId = if (valid) UUID.randomUUID().toString() else latest.incomingId, intakeError = !valid))
    }

    fun useIncoming() {
        val latest = mutableDraft.value
        val incoming = latest.incoming ?: return
        edit(latest.copy(source = incoming, incoming = null, incomingId = null, intakeError = false))
        dismissResult()
    }

    fun dismissIncoming() {
        mutableDraft.value = mutableDraft.value.copy(incoming = null, incomingId = null, intakeError = false)
    }

    fun prepare(model: InstalledLocalModel?) {
        val current = mutableDraft.value
        if (model == null || !current.canReview || current.incoming != null) return
        mutableReview.value = PhoneWritingReview(UUID.randomUUID().toString(), current.instructions, current.source,
            model.sha256, model.metadata.name)
    }

    fun consume(expected: PhoneWritingReview, loaded: InstalledLocalModel?): Boolean {
        if (review.value !== expected || loaded?.sha256 != expected.modelSha256) return false
        cancelReview()
        mutableResult.value = null
        return true
    }

    fun cancelReview() { mutableReview.value = null }
    fun dismissResult() { mutableResult.value = null }
    fun publish(note: PhoneWritingNote) { mutableResult.value = note }

    private fun edit(next: PhoneWritingDraft) {
        if (mutableDraft.value == next) return
        cancelReview()
        mutableDraft.value = next
    }
}

internal const val MAX_WRITING_INSTRUCTIONS = 2048
internal const val MAX_WRITING_SOURCE = 6144
internal fun validInstructions(value: String) = validWritingText(value, MAX_WRITING_INSTRUCTIONS)
internal fun validWritingSource(value: String) = validWritingText(value, MAX_WRITING_SOURCE)
internal fun validOutput(value: String) = validWritingText(value, 16384)
internal fun validTitle(value: String) = value.length <= 64 && validWritingText(value, 160) &&
    value.none { it in "\r\n\t" }
private fun validWritingText(value: String, maximum: Int) = value.length <= maximum && value.isNotBlank() &&
    value.none { it.isISOControl() && it !in "\n\r\t" } && Charsets.UTF_8.newEncoder().canEncode(value) &&
    value.toByteArray().size <= maximum
