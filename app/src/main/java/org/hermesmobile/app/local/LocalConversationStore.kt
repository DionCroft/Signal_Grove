package org.hermesmobile.app.local

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.CancellationException
import kotlinx.serialization.json.*

enum class LocalReplyState { GENERATING, COMPLETE, CANCELLED, TOKEN_LIMIT, FAILED, INTERRUPTED }
data class LocalExchange(val question: String, val reply: String = "", val state: LocalReplyState)
data class LocalConversation(val id: String = UUID.randomUUID().toString(), val modelSha256: String,
    val updatedAt: Long, val exchanges: List<LocalExchange> = emptyList())
data class LocalConversationSummary(val id: String, val modelSha256: String, val updatedAt: Long, val title: String, val lastState: LocalReplyState?)
class LocalHistoryException : IllegalStateException("local_history_unavailable")

/** Deliberately independent of Hermes database/session IDs, receipts and profile history. */
class LocalConversationStore(directory: File) {
    private val directory = directory.canonicalFile
    init { check(this.directory.isDirectory || this.directory.mkdirs()) }

    @Synchronized fun list(checkpoint: () -> Unit = {}): List<LocalConversationSummary> {
        val files = directory.listFiles() ?: throw LocalHistoryException()
        val installed = files.filter { it.name.matches(Regex("[a-f0-9-]{36}\\.json")) }
        if (installed.size > 50) throw LocalHistoryException()
        // Keep only small summaries, rather than retaining every transcript in RAM.
        return installed.map {
            checkpoint()
            val value = load(it.name.removeSuffix(".json"), checkpoint)
            LocalConversationSummary(value.id, value.modelSha256, value.updatedAt,
                value.exchanges.firstOrNull()?.question.orEmpty().filter { !it.isISOControl() }.take(80), value.exchanges.lastOrNull()?.state)
        }.sortedByDescending { it.updatedAt }
    }

    @Synchronized fun load(id: String, checkpoint: () -> Unit = {}): LocalConversation {
        val file = file(id)
        try {
            checkpoint()
            if (!file.isFile || file.length() !in 1L..MAX_BYTES.toLong()) throw LocalHistoryException()
            val json = Json.parseToJsonElement(file.readText()).jsonObject
            require(json["schema"]?.jsonPrimitive?.int == 1 && json["id"]?.jsonPrimitive?.content == id)
            val value = LocalConversation(id, json.getValue("model").jsonPrimitive.content,
                json.getValue("updated").jsonPrimitive.long, json.getValue("exchanges").jsonArray.map { item ->
                    val row = item.jsonObject
                    LocalExchange(row.getValue("question").jsonPrimitive.content, row.getValue("reply").jsonPrimitive.content,
                        LocalReplyState.valueOf(row.getValue("state").jsonPrimitive.content))
                })
            validate(value)
            checkpoint()
            return value
        } catch (e: CancellationException) { throw e }
        catch (e: LocalHistoryException) { throw e }
        catch (_: Exception) { throw LocalHistoryException() }
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
        if (!target.exists() && (directory.listFiles()?.count { it.name.endsWith(".json") } ?: throw LocalHistoryException()) >= 50) throw LocalHistoryException()
        val json = buildJsonObject {
            put("schema", 1); put("id", value.id); put("model", value.modelSha256); put("updated", value.updatedAt)
            putJsonArray("exchanges") { value.exchanges.forEach { row -> add(buildJsonObject {
                put("question", row.question); put("reply", row.reply); put("state", row.state.name)
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
        require(value.modelSha256.matches(Regex("[a-f0-9]{64}")) && value.updatedAt >= 0 && value.exchanges.size <= 30)
        require(value.exchanges.all { it.question.isNotBlank() && it.question.toByteArray().size <= 8192 && it.reply.toByteArray().size <= 131072 })
        require(value.exchanges.withIndex().all { (i, row) -> row.state != LocalReplyState.GENERATING || i == value.exchanges.lastIndex })
    }
    companion object { private const val MAX_BYTES = 4 * 1024 * 1024 }
}
