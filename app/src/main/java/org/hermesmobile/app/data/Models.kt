package org.hermesmobile.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.*
import java.security.MessageDigest

data class ServerConfig(val url: String, val token: String, val allowDebugHttp: Boolean = false) {
    val scope: String get() = MessageDigest.getInstance("SHA-256").digest((url.trimEnd('/') + "\u0000" + token).toByteArray()).joinToString("") { "%02x".format(it) }
}
data class HermesCapabilities(val model: String = "hermes-agent", val features: JsonObject = buildJsonObject {}, val endpoints: JsonObject = buildJsonObject {}) {
    fun has(name: String) = features[name]?.jsonPrimitive?.booleanOrNull == true
    val sessions get() = has("session_resources") && has("session_chat")
    val runs get() = endpoints.containsKey("runs") && has("run_events_sse")
    val nativeStream get() = has("session_chat_streaming")
    companion object {
        fun parse(j: JsonObject) = HermesCapabilities(j.text("model").ifBlank { "hermes-agent" }, j["features"] as? JsonObject ?: buildJsonObject {}, j["endpoints"] as? JsonObject ?: buildJsonObject {})
    }
}
fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
fun JsonObject.items(key: String) = (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
data class RemoteSession(val id: String, val title: String)
data class RemoteMessage(val role: String, val content: String, val timestamp: Long)
data class HermesJob(val id: String, val name: String, val prompt: String, val schedule: String, val state: String)
data class Approval(val runId: String, val requestId: String, val description: String, val choices: List<String>)

@Entity(tableName = "sessions")
data class Conversation(
    @PrimaryKey val key: String,
    val scope: String,
    val remoteId: String,
    val title: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val pendingInput: String = "",
    val pendingKey: String = "",
    val pendingAt: Long = 0,
    val transport: String = "",
    val runId: String = "",
    val status: String = "idle"
)
@Entity(tableName = "messages", indices = [Index("sessionKey")])
data class ChatMessage(@PrimaryKey val id: String, val sessionKey: String, val role: String, val content: String, val timestamp: Long = System.currentTimeMillis(), val status: String = "completed")
@Entity(tableName = "tasks")
data class LocalTask(@PrimaryKey val id: String, val title: String, val dueDate: String = "", val priority: Int = 1, val completed: Boolean = false)

@Dao interface HermesDao {
    @Query("SELECT * FROM sessions WHERE scope = :scope ORDER BY updatedAt DESC") fun conversations(scope: String): Flow<List<Conversation>>
    @Query("SELECT * FROM sessions WHERE `key` = :key") suspend fun conversation(key: String): Conversation?
    @Upsert suspend fun save(conversation: Conversation)
    @Query("SELECT * FROM messages WHERE sessionKey = :key ORDER BY timestamp, id") fun messages(key: String): Flow<List<ChatMessage>>
    @Query("SELECT * FROM messages WHERE sessionKey = :key ORDER BY timestamp, id") suspend fun history(key: String): List<ChatMessage>
    @Upsert suspend fun save(message: ChatMessage)
    @Upsert suspend fun saveMessages(messages: List<ChatMessage>)
    @Query("SELECT * FROM tasks ORDER BY completed, priority DESC, dueDate, title") fun tasks(): Flow<List<LocalTask>>
    @Upsert suspend fun save(task: LocalTask)
    @Delete suspend fun delete(task: LocalTask)
    @Query("DELETE FROM messages") suspend fun clearMessages()
    @Query("DELETE FROM sessions") suspend fun clearSessions()
    @Transaction suspend fun clearChats() { clearMessages(); clearSessions() }
}
@Database(entities = [Conversation::class, ChatMessage::class, LocalTask::class], version = 1, exportSchema = true)
abstract class HermesDatabase : RoomDatabase() { abstract fun dao(): HermesDao }
