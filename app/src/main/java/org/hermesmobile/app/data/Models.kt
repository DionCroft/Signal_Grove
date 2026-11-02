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
data class RemoteSession(val id: String, val title: String, val updatedAt: Long = 0)
data class SessionPage(val rows: List<RemoteSession>, val nextOffset: Int, val hasMore: Boolean)
data class MessageHistory(val sessionId: String, val rows: List<RemoteMessage>)
data class RemoteMessage(val role: String, val content: String, val timestamp: Long, val id: String = "")
data class HermesJob(val id: String, val name: String, val prompt: String, val schedule: String, val state: String,
    val enabled: Boolean = true, val nextRun: String = "", val lastRun: String = "")
@Entity(tableName = "cached_jobs", indices = [Index("scope")])
data class CachedJob(@PrimaryKey val key: String, val scope: String, val id: String, val name: String, val prompt: String,
    val schedule: String, val state: String, val enabled: Boolean, val nextRun: String, val lastRun: String, val fetchedAt: Long) {
    fun remote() = HermesJob(id,name,prompt,schedule,state,enabled,nextRun,lastRun)
}
data class Approval(val runId: String, val requestId: String, val description: String, val choices: List<String>, val generation: Long? = null)

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
    val status: String = "idle",
    @ColumnInfo(defaultValue = "0") val pinned: Boolean = false,
    @ColumnInfo(defaultValue = "0") val hidden: Boolean = false,
    @ColumnInfo(defaultValue = "0") val syncedAt: Long = 0,
    @ColumnInfo(defaultValue = "-1") val lastEventSeq: Long = -1,
    @ColumnInfo(defaultValue = "''") val admissionId: String = "",
    @ColumnInfo(defaultValue = "NULL") val executionGeneration: Long? = null
)
@Entity(tableName = "messages", indices = [Index("sessionKey")])
data class ChatMessage(@PrimaryKey val id: String, val sessionKey: String, val role: String, val content: String, val timestamp: Long = System.currentTimeMillis(), val status: String = "completed")
@Entity(tableName = "tasks")
data class LocalTask(@PrimaryKey val id: String, val title: String, val dueDate: String = "", val priority: Int = 1, val completed: Boolean = false, @ColumnInfo(defaultValue = "''") val notes: String = "")

@Entity(tableName = "tool_events", indices = [Index("sessionKey")])
data class ToolEvent(@PrimaryKey val key: String, val sessionKey: String, val runId: String, val tool: String,
    val kind: String, val preview: String, val durationMs: Long?, val receivedAt: Long)
@Entity(tableName = "pending_controls", indices = [Index("sessionKey")])
data class PendingControl(@PrimaryKey val key: String, val sessionKey: String, val runId: String, val requestId: String,
    val generation: Long?, val kind: String, val prompt: String, val choicesJson: String, val limited: Boolean,
    val state: String = "pending", val receivedAt: Long = System.currentTimeMillis()) {
    fun choices() = runCatching {Json.parseToJsonElement(choicesJson).jsonArray.mapNotNull {(it as? JsonPrimitive)?.contentOrNull}}.getOrDefault(emptyList())
    fun approval() = Approval(runId,requestId,prompt,choices(),generation)
}
@Entity(tableName="job_operations",indices=[Index("scope")])
data class JobOperation(@PrimaryKey val key: String,val scope: String,val operation: String,val jobId: String,val state: String,val createdAt: Long)
@Entity(tableName="notification_receipts")
data class NotificationReceipt(@PrimaryKey val key: String,val createdAt: Long)
object RunStates {
    val terminal=setOf("completed","failed","cancelled","interrupted","response_finished","incomplete")
    val active=setOf("running","queued","submitting","stopping","waiting_for_approval","waiting_for_input","response_submitted")
}

@Dao interface HermesDao {
    @Query("SELECT * FROM job_operations WHERE scope=:scope AND state IN ('sending','unknown') ORDER BY createdAt DESC") fun jobOperations(scope: String): Flow<List<JobOperation>>
    @Query("SELECT * FROM job_operations WHERE scope=:scope AND state IN ('sending','unknown')") suspend fun unresolvedOperations(scope: String): List<JobOperation>
    @Upsert suspend fun save(operation: JobOperation)
    @Query("UPDATE job_operations SET state='unknown' WHERE state='sending'") suspend fun recoverJobOperations()
    @Query("UPDATE job_operations SET state=:state WHERE `key`=:key") suspend fun jobOperationState(key: String,state: String)
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun claimNotification(receipt: NotificationReceipt): Long
    @Query("SELECT * FROM sessions WHERE scope=:scope AND runId!='' AND status NOT IN ('idle','completed','failed','cancelled','interrupted','response_finished','incomplete') ORDER BY updatedAt DESC LIMIT 20") suspend fun trackedRuns(scope: String): List<Conversation>

    @Query("SELECT * FROM sessions WHERE scope = :scope ORDER BY pinned DESC, updatedAt DESC") fun conversations(scope: String): Flow<List<Conversation>>
    @Query("SELECT * FROM sessions WHERE `key` = :key") suspend fun conversation(key: String): Conversation?
    @Upsert suspend fun save(conversation: Conversation)
    @Query("SELECT * FROM sessions WHERE scope = :scope AND remoteId = :remoteId LIMIT 1") suspend fun byRemoteId(scope: String, remoteId: String): Conversation?
    @Query("UPDATE sessions SET title = :title, updatedAt = MAX(updatedAt, :updatedAt) WHERE `key` = :key")
    suspend fun updateMetadata(key: String,title: String,updatedAt: Long)
    @Transaction suspend fun mergeRemoteSession(scope: String,remote: RemoteSession,fallbackTime: Long) {
        val current=byRemoteId(scope,remote.id)
        if(current==null) save(Conversation("$scope:${remote.id}",scope,remote.id,remote.title,remote.updatedAt.takeIf {it>0} ?: fallbackTime))
        else updateMetadata(current.key,remote.title.ifBlank {current.title},remote.updatedAt)
    }
    @Query("DELETE FROM messages WHERE sessionKey = :key") suspend fun deleteMessages(key: String)
    @Query("DELETE FROM sessions WHERE `key` = :key") suspend fun deleteSession(key: String)
    @Transaction suspend fun deleteConversation(key: String) { deleteMessages(key); deleteEvents(key); deleteControls(key); deleteSession(key) }
    @Query("SELECT * FROM tool_events WHERE sessionKey = :key ORDER BY receivedAt DESC LIMIT 200") fun events(key: String): Flow<List<ToolEvent>>
    @Upsert suspend fun save(event: ToolEvent)
    @Query("DELETE FROM tool_events WHERE sessionKey = :key") suspend fun deleteEvents(key: String)
    @Query("SELECT * FROM pending_controls WHERE sessionKey = :key AND state != 'resolved' ORDER BY receivedAt, `key`") fun controls(key: String): Flow<List<PendingControl>>
    @Query("SELECT * FROM pending_controls WHERE `key` = :key") suspend fun control(key: String): PendingControl?
    @Upsert suspend fun save(control: PendingControl)
    @Query("UPDATE pending_controls SET state = 'unknown' WHERE state = 'responding'") suspend fun recoverControls()
    @Query("DELETE FROM pending_controls WHERE `key` = :key") suspend fun deleteControl(key: String)
    @Query("DELETE FROM pending_controls WHERE sessionKey = :key") suspend fun deleteControls(key: String)
    @Query("SELECT * FROM pending_controls WHERE sessionKey = :key AND runId = :run") suspend fun runControls(key: String,run: String): List<PendingControl>
    @Query("DELETE FROM pending_controls WHERE sessionKey = :key AND runId = :run") suspend fun deleteRunControls(key: String,run: String)
    @Query("UPDATE pending_controls SET state = :state WHERE `key` = :key") suspend fun controlState(key: String,state: String)
    @Transaction suspend fun replaceControls(key: String,run: String,rows: List<PendingControl>,resetUnknown: Boolean) {
        if(conversation(key)?.status in RunStates.terminal) {deleteRunControls(key,run);return}
        val previous=runControls(key,run).associateBy {it.key}
        deleteRunControls(key,run)
        rows.forEach {row ->
            val old=previous[row.key]
            val state=if(old!=null && old.generation==row.generation && (old.state=="resolved" || old.state=="responding" || (old.state=="unknown" && !resetUnknown))) old.state else "pending"
            save(row.copy(state=state,receivedAt=old?.receivedAt ?: row.receivedAt))
        }
    }
    @Transaction suspend fun saveTurn(receipt: Conversation,message: ChatMessage,control: PendingControl?,event: ToolEvent?,settledId: String) {
        val current=conversation(receipt.key) ?: return
        save(receipt.copy(title=current.title,pinned=current.pinned,hidden=current.hidden,syncedAt=current.syncedAt))
        save(message)
        if(control!=null) save(control)
        if(event!=null) save(event)
        if(settledId.isNotBlank()) runControls(receipt.key,receipt.runId).filter {it.requestId==settledId}.forEach {deleteControl(it.key)}
        if(receipt.status in RunStates.terminal) deleteRunControls(receipt.key,receipt.runId)
    }
    @Transaction suspend fun replaceHistory(key: String, remoteId: String, messages: List<ChatMessage>) {
        val current = conversation(key) ?: return
        if(current.status !in (RunStates.terminal+"idle")) return
        deleteMessages(key); saveMessages(messages)
        save(current.copy(remoteId=remoteId,syncedAt=System.currentTimeMillis()))
    }
    @Query("SELECT * FROM cached_jobs WHERE scope = :scope ORDER BY name") fun cachedJobs(scope: String): Flow<List<CachedJob>>
    @Query("SELECT * FROM cached_jobs WHERE scope = :scope AND id = :id LIMIT 1") suspend fun cachedJob(scope: String,id: String): CachedJob?
    @Query("DELETE FROM cached_jobs WHERE scope = :scope") suspend fun deleteJobs(scope: String)
    @Upsert suspend fun saveJobs(jobs: List<CachedJob>)
    @Transaction suspend fun replaceJobs(scope: String, jobs: List<CachedJob>) { deleteJobs(scope); saveJobs(jobs) }
    @Query("SELECT * FROM messages WHERE sessionKey = :key ORDER BY timestamp, id") fun messages(key: String): Flow<List<ChatMessage>>
    @Query("SELECT * FROM messages WHERE sessionKey = :key ORDER BY timestamp, id") suspend fun history(key: String): List<ChatMessage>
    @Upsert suspend fun save(message: ChatMessage)
    @Upsert suspend fun saveMessages(messages: List<ChatMessage>)
    @Query("SELECT * FROM tasks ORDER BY completed, priority DESC, dueDate, title") fun tasks(): Flow<List<LocalTask>>
    @Transaction suspend fun importTasks(rows: List<LocalTask>) {rows.forEach {save(it)}}
    @Upsert suspend fun save(task: LocalTask)
    @Delete suspend fun delete(task: LocalTask)
    @Query("DELETE FROM messages") suspend fun clearMessages()
    @Query("DELETE FROM sessions") suspend fun clearSessions()
    @Query("DELETE FROM tool_events") suspend fun clearEvents()
    @Query("DELETE FROM pending_controls") suspend fun clearControls()
    @Transaction suspend fun clearChats() { clearMessages(); clearControls(); clearEvents(); clearSessions() }
}
@Database(entities = [Conversation::class, ChatMessage::class, LocalTask::class, CachedJob::class,ToolEvent::class,PendingControl::class,JobOperation::class,NotificationReceipt::class], version = 4, exportSchema = true)
abstract class HermesDatabase : RoomDatabase() { abstract fun dao(): HermesDao }
