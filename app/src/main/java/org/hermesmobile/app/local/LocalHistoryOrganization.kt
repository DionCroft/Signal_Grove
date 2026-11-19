package org.hermesmobile.app.local

import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.CancellationException
import kotlinx.serialization.json.*

/** Display-only metadata: never inserted into model context or the source transcript. */
data class LocalHistoryPreferences(val title: String? = null, val pinned: Boolean = false, val archived: Boolean = false) {
    init {
        title?.let {
            require(it.isNotBlank() && it == it.trim() && it.codePointCount(0, it.length) <= 80 &&
                it.toByteArray(Charsets.UTF_8).size <= 320 && Charsets.UTF_8.newEncoder().canEncode(it) &&
                it.none { c -> c.isISOControl() || c == '\u2028' || c == '\u2029' })
        }
    }
}

data class LocalHistoryOrganizationEntry(val modelSha256: String, val preferences: LocalHistoryPreferences) {
    init { require(modelSha256.matches(Regex("[a-f0-9]{64}"))) }
}
class LocalHistoryOrganizationChangedException : IllegalArgumentException("local_history_organization_changed")
class LocalHistoryOrganizationException : IllegalStateException("local_history_organization_unavailable")

/** Separate bounded index so rename/pin/archive cannot invalidate branch source hashes.
 * The application controller must serialize mutations with its conversation operations.
 * This is not a multi-process database, a transcript migration or an auto-pruning task. */
class LocalHistoryOrganization(directory: File, private val conversations: LocalConversationStore) {
    private val directory = directory.canonicalFile
    init { check(this.directory.isDirectory || this.directory.mkdirs()) }
    internal fun index() = File(directory, "index.json").also { require(it.canonicalFile == it.absoluteFile) }

    @Synchronized fun entries(checkpoint: () -> Unit = {}): Map<String, LocalHistoryOrganizationEntry> =
        snapshot(checkpoint).first

    @Synchronized internal fun snapshot(checkpoint: () -> Unit): Pair<Map<String, LocalHistoryOrganizationEntry>, ByteArray?> {
        try {
            checkpoint()
            val file = index()
            if (!file.exists()) return emptyMap<String, LocalHistoryOrganizationEntry>() to null
            require(file.isFile && file.length() in 1L..MAX_BYTES.toLong())
            val bytes = file.inputStream().use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    checkpoint()
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= MAX_BYTES)
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val root = Json.parseToJsonElement(Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()).jsonObject
            require(root.keys == setOf("schema", "entries"))
            require(!root.getValue("schema").jsonPrimitive.isString && root.getValue("schema").jsonPrimitive.int == 1)
            val rows = root.getValue("entries").jsonObject
            require(rows.size <= LocalConversationStore.MAX_CHATS)
            val result = rows.mapValues { (id, element) ->
                checkpoint(); validId(id)
                val row = element.jsonObject
                require(row.keys == setOf("model", "title", "pinned", "archived"))
                val model = row.getValue("model").jsonPrimitive.also { require(it.isString) }.content
                val title = row.getValue("title").let { value ->
                    if (value == JsonNull) null else value.jsonPrimitive.also { require(it.isString) }.content
                }
                fun flag(name: String) = row.getValue(name).jsonPrimitive.also { require(!it.isString) }.boolean
                LocalHistoryOrganizationEntry(model, LocalHistoryPreferences(title, flag("pinned"), flag("archived")))
            }
            checkpoint()
            return result to bytes
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { throw LocalHistoryOrganizationException() }
    }

    /** Validated display catalog, including archived chats for limits and parent access. */
    @Synchronized fun history(checkpoint: () -> Unit = {}): List<LocalConversationSummary> {
        val all = conversations.list(checkpoint)
        val labels = entries(checkpoint)
        return all.map { row ->
            checkpoint()
            val entry = labels[row.id]
            if (entry != null && entry.modelSha256 != row.modelSha256) throw LocalHistoryOrganizationException()
            row.copy(title = entry?.preferences?.title ?: row.title, organization = entry)
        }.sortedWith(localHistoryOrder)
    }

    /** Compare the exact reviewed metadata; a stale review cannot replace a newer rename.
     * Verify the current local conversation/model before publishing any association. */
    @Synchronized fun update(id: String, modelSha256: String, expected: LocalHistoryOrganizationEntry?,
        preferences: LocalHistoryPreferences, checkpoint: () -> Unit = {}): LocalHistoryOrganizationEntry? {
        validId(id)
        require(modelSha256.matches(Regex("[a-f0-9]{64}")))
        val chat = conversations.load(id, checkpoint)
        if (chat.modelSha256 != modelSha256) throw LocalHistoryOrganizationChangedException()
        val before = entries(checkpoint)
        if (before[id] != expected || expected?.modelSha256?.let {it != modelSha256} == true) throw LocalHistoryOrganizationChangedException()
        val next = if (preferences == LocalHistoryPreferences()) null else LocalHistoryOrganizationEntry(modelSha256, preferences)
        if (next == expected) return next
        val changed = before.toMutableMap()
        if (next == null) changed.remove(id) else changed[id] = next
        require(changed.size <= LocalConversationStore.MAX_CHATS)
        publish(changed, checkpoint)
        return next
    }

    /** Explicit reset/orphan cleanup only. No automatic removal and no transcript deletion. */
    @Synchronized fun reset(id: String, expected: LocalHistoryOrganizationEntry, checkpoint: () -> Unit = {}) {
        validId(id)
        val before = entries(checkpoint)
        require(before[id] == expected)
        publish(before - id, checkpoint)
    }

    internal fun encode(rows: Map<String, LocalHistoryOrganizationEntry>): ByteArray {
        require(rows.size <= LocalConversationStore.MAX_CHATS)
        val bytes = buildJsonObject {
            put("schema", 1)
            putJsonObject("entries") { rows.toSortedMap().forEach { (id, entry) -> putJsonObject(id) {
                put("model", entry.modelSha256)
                put("title", entry.preferences.title?.let(::JsonPrimitive) ?: JsonNull)
                put("pinned", entry.preferences.pinned); put("archived", entry.preferences.archived)
            } } }
        }.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES)
        return bytes
    }

    private fun publish(rows: Map<String, LocalHistoryOrganizationEntry>, checkpoint: () -> Unit) {
        val bytes = encode(rows)
        checkpoint()
        val target = index()
        val partial = File(directory, "index.${UUID.randomUUID()}.part")
        check(partial.createNewFile())
        try {
            partial.outputStream().use { it.write(bytes); it.fd.sync() }
            checkpoint() // Last cancellation point; publication is one atomic commit.
            Files.move(partial.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { if (partial.exists() && !partial.delete()) throw LocalHistoryOrganizationException() }
    }

    private fun validId(id: String) { require(runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)) }
    companion object { internal const val MAX_BYTES = 64 * 1024 }
}

/** Pin changes display order only; activity timestamps and transcript bytes stay authoritative. */
internal val localHistoryOrder = compareByDescending<LocalConversationSummary> { it.organization?.preferences?.pinned == true }
    .thenByDescending { it.updatedAt }.thenBy { it.id }
