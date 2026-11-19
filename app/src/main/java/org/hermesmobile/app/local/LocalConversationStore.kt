package org.hermesmobile.app.local

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CancellationException
import kotlinx.serialization.json.*

enum class LocalReplyState { GENERATING, COMPLETE, CANCELLED, TOKEN_LIMIT, FAILED, INTERRUPTED }
data class LocalExchange(val question: String, val reply: String = "", val state: LocalReplyState, val generation: LocalGenerationSettings? = null)
enum class LocalBranchKind { FORK, REGENERATE, EDIT }
data class LocalBranchOrigin(val parentId: String, val parentUpdatedAt: Long, val exchangeIndex: Int,
    val kind: LocalBranchKind, val sourceSha256: String)
data class LocalConversation(val id: String = UUID.randomUUID().toString(), val modelSha256: String,
    val updatedAt: Long, val exchanges: List<LocalExchange> = emptyList(), val origin: LocalBranchOrigin? = null)
data class LocalConversationSummary(val id: String, val modelSha256: String, val updatedAt: Long, val title: String, val lastState: LocalReplyState?, val origin: LocalBranchOrigin? = null,
    val organization: LocalHistoryOrganizationEntry? = null)
class LocalHistoryException : IllegalStateException("local_history_unavailable")

/** Deliberately independent of Hermes database/session IDs, receipts and profile history. */
class LocalConversationStore(directory: File) {
    private val directory = directory.canonicalFile
    init { check(this.directory.isDirectory || this.directory.mkdirs()) }

    @Synchronized fun list(checkpoint: () -> Unit = {}): List<LocalConversationSummary> {
        val files = directory.listFiles() ?: throw LocalHistoryException()
        val installed = files.filter { it.name.matches(Regex("[a-f0-9-]{36}\\.json")) }
        if (installed.size > MAX_CHATS) throw LocalHistoryException()
        // Keep only small summaries, rather than retaining every transcript in RAM.
        return installed.map {
            checkpoint()
            val value = load(it.name.removeSuffix(".json"), checkpoint)
            LocalConversationSummary(value.id, value.modelSha256, value.updatedAt,
                value.exchanges.firstOrNull()?.question.orEmpty().filter { !it.isISOControl() }.take(80), value.exchanges.lastOrNull()?.state, value.origin)
        }.sortedByDescending { it.updatedAt }
    }

    @Synchronized fun load(id: String, checkpoint: () -> Unit = {}): LocalConversation = readSnapshot(id, checkpoint).first

    private fun readSnapshot(id: String, checkpoint: () -> Unit = {}): Pair<LocalConversation, ByteArray> {
        val file = file(id)
        try {
            checkpoint()
            if (!file.isFile || file.length() !in 1L..MAX_BYTES.toLong()) throw LocalHistoryException()
            val bytes = file.inputStream().use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    checkpoint()
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= MAX_BYTES)
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            require(bytes.size in 1..MAX_BYTES)
            val text = Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
            val json = Json.parseToJsonElement(text).jsonObject
            val schema = json.getValue("schema").jsonPrimitive
            require(!schema.isString && schema.int in 1..3 && json["id"]?.jsonPrimitive?.content == id)
            // Refuse unknown extensions rather than dropping them during a later save.
            require(json.keys == if (schema.int == 1) ROOT_KEYS else ROOT_KEYS + "origin")
            val originJson = if (schema.int >= 2) json.getValue("origin") else JsonNull
            require(schema.int != 2 || originJson != JsonNull)
            val origin = originJson.takeUnless { it == JsonNull }?.jsonObject?.let { row ->
                require(row.keys == ORIGIN_KEYS)
                require(!row.getValue("updated").jsonPrimitive.isString && !row.getValue("exchange").jsonPrimitive.isString)
                LocalBranchOrigin(row.getValue("parent").jsonPrimitive.content, row.getValue("updated").jsonPrimitive.long,
                    row.getValue("exchange").jsonPrimitive.int, LocalBranchKind.valueOf(row.getValue("kind").jsonPrimitive.content),
                    row.getValue("source").jsonPrimitive.content)
            }
            val value = LocalConversation(id, json.getValue("model").jsonPrimitive.content,
                json.getValue("updated").jsonPrimitive.long, json.getValue("exchanges").jsonArray.map { item ->
                    val row = item.jsonObject
                    require(row.keys == if (schema.int < 3) ROW_KEYS else ROW_KEYS + "generation")
                    require(ROW_KEYS.all { row.getValue(it) is JsonPrimitive && row.getValue(it).jsonPrimitive.isString })
                    LocalExchange(row.getValue("question").jsonPrimitive.content, row.getValue("reply").jsonPrimitive.content,
                        LocalReplyState.valueOf(row.getValue("state").jsonPrimitive.content),
                        if (schema.int == 3) row.getValue("generation").takeUnless { it == JsonNull }?.let(::readGeneration) else null)
                }, origin)
            require(json.getValue("model").jsonPrimitive.isString && json.getValue("id").jsonPrimitive.isString &&
                !json.getValue("updated").jsonPrimitive.isString)
            validate(value)
            checkpoint()
            return value to bytes
        } catch (e: CancellationException) { throw e }
        catch (e: LocalHistoryException) { throw e }
        catch (_: Exception) { throw LocalHistoryException() }
    }

    /** Explicit local action. Publish one independent child before its caller may generate.
     * The one application controller serializes writes; this is not a multi-process database.
     * Parent files are never changed, including cancelled/failed/partially generated replies. */
    @Synchronized fun createBranch(expected: LocalConversation, exchangeIndex: Int, kind: LocalBranchKind,
        now: Long, editedQuestion: String? = null, generation: LocalGenerationSettings? = null): LocalConversation {
        val (source, bytes) = readSnapshot(expected.id)
        require(source == expected && source.exchanges.none { it.state == LocalReplyState.GENERATING })
        require(exchangeIndex in source.exchanges.indices && now >= 0)
        val selected = source.exchanges[exchangeIndex]
        val rows = when (kind) {
            LocalBranchKind.FORK -> {
                require(editedQuestion == null && generation == null && selected.state in setOf(LocalReplyState.COMPLETE, LocalReplyState.TOKEN_LIMIT))
                source.exchanges.take(exchangeIndex + 1)
            }
            LocalBranchKind.REGENERATE -> {
                require(editedQuestion == null)
                source.exchanges.take(exchangeIndex) + LocalExchange(selected.question, state = LocalReplyState.GENERATING, generation = generation)
            }
            LocalBranchKind.EDIT -> {
                require(editedQuestion != null)
                source.exchanges.take(exchangeIndex) + LocalExchange(editedQuestion, state = LocalReplyState.GENERATING, generation = generation)
            }
        }
        val origin = LocalBranchOrigin(source.id, source.updatedAt, exchangeIndex, kind,
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        val child = LocalConversation(modelSha256 = source.modelSha256, updatedAt = now, exchanges = rows, origin = origin)
        saveNew(child)
        return child
    }

    /** Refuse an ID collision rather than overwriting an unrelated conversation. */
    @Synchronized fun saveNew(value: LocalConversation) {
        require(!file(value.id).exists())
        save(value)
    }

    /** Call once on a cold application start, before creating any native operation.
     * Ordinary list/load calls must not relabel a live generation as interrupted. */
    @Synchronized fun recoverInterrupted(checkpoint: () -> Unit = {}) {
        list(checkpoint).forEach { summary ->
            checkpoint()
            val value = load(summary.id, checkpoint)
            val recovered = value.copy(exchanges = value.exchanges.map { if (it.state == LocalReplyState.GENERATING) it.copy(state = LocalReplyState.INTERRUPTED) else it })
            if (recovered != value) save(recovered)
        }
    }

    @Synchronized fun save(value: LocalConversation) {
        validate(value)
        val target = file(value.id)
        if (!target.exists() && (directory.listFiles()?.count { it.name.endsWith(".json") } ?: throw LocalHistoryException()) >= MAX_CHATS) throw LocalHistoryException()
        val schema = if (value.exchanges.any { it.generation != null }) 3 else if (value.origin != null) 2 else 1
        val json = buildJsonObject {
            put("schema", schema); put("id", value.id); put("model", value.modelSha256); put("updated", value.updatedAt)
            if (schema == 3 && value.origin == null) put("origin", JsonNull)
            value.origin?.let { origin -> putJsonObject("origin") {
                put("parent", origin.parentId); put("updated", origin.parentUpdatedAt); put("exchange", origin.exchangeIndex)
                put("kind", origin.kind.name); put("source", origin.sourceSha256)
            } }
            putJsonArray("exchanges") { value.exchanges.forEach { row -> add(buildJsonObject {
                put("question", row.question); put("reply", row.reply); put("state", row.state.name)
                if (schema == 3) put("generation", row.generation?.let(::writeGeneration) ?: JsonNull)
            }) } }
        }.toString().toByteArray(Charsets.UTF_8)
        require(json.size <= MAX_BYTES)
        val partial = File(directory, "${value.id}.${UUID.randomUUID()}.part")
        check(partial.createNewFile())
        try {
            partial.outputStream().use { it.write(json); it.fd.sync() }
            Files.move(partial.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { if (partial.exists() && !partial.delete()) throw LocalHistoryException() }
    }

    @Synchronized fun delete(id: String) { val target = file(id); if (target.exists() && !target.delete()) throw LocalHistoryException() }
    private fun file(id: String): File {
        require(runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false))
        return File(directory, "$id.json").also { require(it.canonicalFile == it.absoluteFile) }
    }
    private fun validate(value: LocalConversation) {
        file(value.id)
        require(value.modelSha256.matches(Regex("[a-f0-9]{64}")) && value.updatedAt >= 0 && value.exchanges.size <= MAX_TURNS)
        value.origin?.let {
            file(it.parentId)
            require(it.parentId != value.id && it.parentUpdatedAt >= 0 && it.exchangeIndex in 0 until MAX_TURNS &&
                it.sourceSha256.matches(Regex("[a-f0-9]{64}")))
        }
        value.exchanges.forEach { it.generation?.validate() }
        require(value.exchanges.all { it.question.isNotBlank() && it.question.toByteArray().size <= 8192 && it.reply.toByteArray().size <= 131072 })
        require(value.exchanges.withIndex().all { (i, row) -> row.state != LocalReplyState.GENERATING || i == value.exchanges.lastIndex })
    }
    private fun readGeneration(element: JsonElement): LocalGenerationSettings {
        val row = element.jsonObject
        require(row.keys == setOf("context", "threads", "output", "runtime"))
        require(listOf("context", "threads", "output").all { !row.getValue(it).jsonPrimitive.isString })
        val runtime = row.getValue("runtime").takeUnless { it == JsonNull }?.jsonObject?.let {
            require(it.keys == setOf("source", "sampling") && it.values.all { value -> value is JsonPrimitive && value.isString })
            LocalRuntimePolicy(it.getValue("source").jsonPrimitive.content,
                LocalSamplingPolicy.valueOf(it.getValue("sampling").jsonPrimitive.content))
        }
        return LocalGenerationSettings(row.getValue("context").jsonPrimitive.int, row.getValue("threads").jsonPrimitive.int,
            row.getValue("output").jsonPrimitive.int, runtime).also { it.validate() }
    }
    private fun writeGeneration(value: LocalGenerationSettings) = buildJsonObject {
        put("context", value.contextTokens); put("threads", value.threads); put("output", value.maxOutputTokens)
        put("runtime", value.runtime?.let { runtime -> buildJsonObject {
            put("source", runtime.sourceSha256); put("sampling", runtime.sampling.name)
        } } ?: JsonNull)
    }
    companion object {
        const val MAX_CHATS = 50
        const val MAX_TURNS = 30
        private const val MAX_BYTES = 4 * 1024 * 1024
        private val ROW_KEYS = setOf("question", "reply", "state")
        private val ROOT_KEYS = setOf("schema", "id", "model", "updated", "exchanges")
        private val ORIGIN_KEYS = setOf("parent", "updated", "exchange", "kind", "source")
    }
}
