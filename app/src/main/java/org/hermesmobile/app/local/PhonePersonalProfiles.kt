package org.hermesmobile.app.local

import kotlinx.serialization.json.*

internal const val PHONE_PROFILE_MAX = 8
internal const val PHONE_PROFILE_NOTES_MAX = 8
internal const val PHONE_PROFILE_STYLE_BYTES = 512
internal const val PHONE_PROFILE_NOTE_BYTES = 1024
private const val PHONE_PROFILE_CONTEXT_BYTES = 4096
private val profileId = Regex("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")

internal fun validProfileText(value: String, limit: Int): Boolean =
    value.length <= limit && value.isNotBlank() && value.toByteArray().size <= limit &&
        Charsets.UTF_8.newEncoder().canEncode(value) && value.none { it.isISOControl() && it !in "\n\r\t" }

internal data class PhonePersonalNote(val id: String, val text: String) {
    init { require(profileId.matches(id) && validProfileText(text, PHONE_PROFILE_NOTE_BYTES)) }
}

internal data class PhonePersonalProfile(
    val id: String,
    val name: String,
    val style: String,
    val notes: List<PhonePersonalNote>
) {
    init {
        require(profileId.matches(id) && validTitle(name))
        require(style.isEmpty() || validProfileText(style, PHONE_PROFILE_STYLE_BYTES))
        require(notes.size <= PHONE_PROFILE_NOTES_MAX && notes.distinctBy { it.id }.size == notes.size)
    }
}

internal data class PhonePersonalProfiles(val version: Int = 1, val entries: List<PhonePersonalProfile> = emptyList()) {
    init {
        require(version == 1)
        require(entries.size <= PHONE_PROFILE_MAX && entries.distinctBy { it.id }.size == entries.size)
    }

    fun save(previous: PhonePersonalProfile?, replacement: PhonePersonalProfile): PhonePersonalProfiles =
        if (previous == null) copy(entries = entries + replacement)
        else {
            require(previous in entries && previous.id == replacement.id)
            copy(entries = entries.map { if (it == previous) replacement else it })
        }

    fun remove(expected: PhonePersonalProfile): PhonePersonalProfiles {
        require(expected in entries)
        return copy(entries = entries - expected)
    }

    fun encode(): String = buildJsonObject {
        put("version", version)
        put("profiles", buildJsonArray {
            entries.forEach { profile -> add(buildJsonObject {
                put("id", profile.id)
                put("name", profile.name)
                put("style", profile.style)
                put("notes", buildJsonArray {
                    profile.notes.forEach { note -> add(buildJsonObject {
                        put("id", note.id)
                        put("text", note.text)
                    }) }
                })
            }) }
        })
    }.toString()

    companion object {
        fun decode(value: String?): PhonePersonalProfiles {
            if (value == null) return PhonePersonalProfiles()
            require(value.length <= 450_000)
            val fields = Json.parseToJsonElement(value).jsonObject
            val version = fields.getValue("version").jsonPrimitive.int
            require(version == 1)
            val profiles = fields.getValue("profiles").jsonArray
            require(profiles.size <= PHONE_PROFILE_MAX)
            return PhonePersonalProfiles(version, profiles.map { item ->
                val profile = item.jsonObject
                val notes = profile.getValue("notes").jsonArray
                require(notes.size <= PHONE_PROFILE_NOTES_MAX)
                PhonePersonalProfile(profile.text("id"), profile.text("name"), profile.text("style"), notes.map {
                    val note = it.jsonObject
                    PhonePersonalNote(note.text("id"), note.text("text"))
                })
            })
        }

        private fun JsonObject.text(key: String): String =
            getValue(key).jsonPrimitive.also { require(it.isString) }.content
    }
}

internal data class PhoneProfileContext(val style: String?, val notes: List<PhonePersonalNote>) {
    val text = buildJsonObject {
        style?.let { put("writing_preferences", it) }
        if (notes.isNotEmpty()) put("notes", buildJsonArray { notes.forEach { add(it.text) } })
    }.toString()

    val fits: Boolean get() = text.toByteArray().size <= PHONE_PROFILE_CONTEXT_BYTES

    companion object {
        fun select(profile: PhonePersonalProfile, includeStyle: Boolean, noteIds: Set<String>): PhoneProfileContext? {
            val style = profile.style.takeIf { includeStyle && it.isNotBlank() }
            val notes = profile.notes.filter { it.id in noteIds }
            if (notes.size != noteIds.size || (style == null && notes.isEmpty())) return null
            return PhoneProfileContext(style, notes)
        }
    }
}

internal class PhoneProfileReview private constructor(
    val profile: PhonePersonalProfile,
    val context: PhoneProfileContext,
    val draft: String,
    val chat: LocalConversation?,
    val model: InstalledLocalModel,
    val settings: LocalGenerationSettings
) {
    val question = draft.trim()

    fun matches(state: LocalAiState, draft: String): Boolean =
        this.draft == draft && chat == state.chat && model == state.loaded && settings == state.loadedSettings

    companion object {
        fun create(profile: PhonePersonalProfile, context: PhoneProfileContext, draft: String,
            state: LocalAiState): PhoneProfileReview? {
            val model = state.loaded ?: return null
            val settings = state.loadedSettings ?: return null
            if (state.busy || !context.fits || !validProfileText(draft.trim(), 8192) ||
                state.chat?.let { it.modelSha256 != model.sha256 || it.exchanges.size >= LocalConversationStore.MAX_TURNS } == true) return null
            val selected = PhoneProfileContext.select(profile, context.style != null, context.notes.map { it.id }.toSet())
            if (selected != context) return null
            return PhoneProfileReview(profile, context, draft, state.chat, model, settings)
        }
    }
}
