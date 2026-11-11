package org.hermesmobile.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.*
import java.security.MessageDigest

data class SavedConnection(val id:String,val name:String,val url:String,val debugHttp:Boolean,val hasCredential:Boolean)

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
data class RemoteSession(val id: String, val title: String, val updatedAt: Long = 0, val source:String=SessionOrigin.Api)
data class SessionPage(val rows: List<RemoteSession>, val nextOffset: Int, val hasMore: Boolean)
data class MessageHistory(val sessionId: String, val rows: List<RemoteMessage>)
data class RemoteMessage(val role: String, val content: String, val timestamp: Long, val id: String = "",val images:List<String> = emptyList())
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
    @ColumnInfo(defaultValue = "NULL") val executionGeneration: Long? = null,
    @ColumnInfo(defaultValue = "''") val channelKey: String = "",
    @ColumnInfo(defaultValue = "''") val pendingRequestHash: String = "",
    @ColumnInfo(defaultValue = "''") val detailsJson: String = ""
)
fun Conversation.sameSubmission(other: Conversation,allowAdmission: Boolean=false): Boolean =
    key==other.key && scope==other.scope && pendingKey==other.pendingKey && pendingAt==other.pendingAt &&
        (runId==other.runId || (allowAdmission && runId.isBlank()))
@Entity(tableName = "messages", indices = [Index("sessionKey")])
data class ChatMessage(@PrimaryKey val id: String, val sessionKey: String, val role: String, val content: String, val timestamp: Long = System.currentTimeMillis(), val status: String = "completed", @ColumnInfo(defaultValue="''") val telemetryJson:String="", @ColumnInfo(defaultValue="''") val imagesJson:String="")
@Entity(tableName = "tasks")
data class LocalTask(@PrimaryKey val id: String, val title: String, val dueDate: String = "", val priority: Int = 1, val completed: Boolean = false, @ColumnInfo(defaultValue = "''") val notes: String = "")

@Entity(tableName = "tool_events", indices = [Index("sessionKey")])
data class ToolEvent(@PrimaryKey val key: String, val sessionKey: String, val runId: String, val tool: String,
    val kind: String, val preview: String, val durationMs: Long?, val receivedAt: Long,
    @ColumnInfo(defaultValue="''") val callId:String="", @ColumnInfo(defaultValue="'tool'") val category:String="tool",
    @ColumnInfo(defaultValue="''") val detailsJson:String="", @ColumnInfo(defaultValue="NULL") val generation:Long?=null,
    @ColumnInfo(defaultValue="NULL") val sequence:Long?=null)
@Entity(tableName="run_reports",indices=[Index("sessionKey")])
data class RunReport(@PrimaryKey val key:String,val sessionKey:String,val runId:String,val pendingKey:String,
    val transport:String,val telemetryJson:String,val status:String,val updatedAt:Long)
@Entity(tableName = "pending_controls", indices = [Index("sessionKey")])
data class PendingControl(@PrimaryKey val key: String, val sessionKey: String, val runId: String, val requestId: String,
    val generation: Long?, val kind: String, val prompt: String, val choicesJson: String, val limited: Boolean,
    val state: String = "pending", val receivedAt: Long = System.currentTimeMillis()) {
    fun choices() = runCatching {Json.parseToJsonElement(choicesJson).jsonArray.mapNotNull {(it as? JsonPrimitive)?.contentOrNull}}.getOrDefault(emptyList())
    fun approval() = Approval(runId,requestId,prompt,choices(),generation)
}
@Entity(tableName="job_operations",indices=[Index("scope")])
data class JobOperation(@PrimaryKey val key: String,val scope: String,val operation: String,val jobId: String,val state: String,val createdAt: Long)
@Entity(tableName="server_operations",indices=[Index("scope"),Index("sessionKey")])
data class ServerOperation(@PrimaryKey val key: String,val scope: String,val sessionKey: String,val action: String,val target: String,
    val requestJson: String,val state: String,val responseJson: String,val createdAt: Long)
@Entity(tableName="management_operations",indices=[Index("resourceScope"),Index("apiScope"),Index(value=["url","projectHome"])])
data class ManagementOperation(@PrimaryKey val key:String,val resourceScope:String,val apiScope:String,val url:String,val profile:String,
    val actor:String,val action:String,val target:String,val requestSummary:String,val state:String,val resultSummary:String,val createdAt:Long,
    @ColumnInfo(defaultValue="''") val projectHome:String="")
@Entity(tableName="notification_receipts")
data class NotificationReceipt(@PrimaryKey val key: String,val createdAt: Long)
object RunStates {
    val authoritativeTerminal=setOf("completed","failed","cancelled","interrupted","incomplete")
    val responseEnded=setOf("response_finished","response_failed","response_cancelled","response_incomplete")
    // Local turn availability is separate from authoritative server execution status.
    val terminal=authoritativeTerminal+responseEnded
    fun usableHistory(message: ChatMessage)=message.role=="user" ||
        (message.role=="assistant" && message.content.isNotBlank() && message.status in setOf("completed","incomplete","response_finished","response_incomplete"))
    val active=setOf("running","queued","submitting","stopping","waiting_for_approval","waiting_for_input","response_submitted")
}

@Dao interface HermesDao {
    @Query("SELECT * FROM management_operations WHERE apiScope=:apiScope ORDER BY CASE WHEN state IN ('sending','unknown') THEN 0 ELSE 1 END,createdAt DESC LIMIT 100") fun managementOperations(apiScope:String):Flow<List<ManagementOperation>>
    @Query("SELECT * FROM management_operations WHERE resourceScope=:scope ORDER BY CASE WHEN state IN ('sending','unknown') THEN 0 ELSE 1 END,createdAt DESC LIMIT 100") fun managementResourceOperations(scope:String):Flow<List<ManagementOperation>>
    // Filter foreign settled history BEFORE paging; foreign pending work still shares the barrier.
    @Query("SELECT * FROM management_operations WHERE (resourceScope=:scope AND (apiScope=:apiScope OR state IN ('sending','unknown'))) OR (url=:url AND (action IN ('RelayLinkStart','RelayUnlink','CustomEndpointProbe','KanbanBoardCreate','KanbanBoardUpdate','KanbanBoardArchive','KanbanBoardDelete','KanbanBoardSwitch','KanbanTaskCreate','KanbanTaskMetadata','KanbanTaskPriority','KanbanTaskAssignee','KanbanTaskComment','KanbanTaskLink','KanbanTaskUnlink','KanbanTaskDelete','KanbanTaskModelOverride','KanbanTaskReasoningOverride','KanbanTaskStatus') OR (:projectHome!='' AND projectHome=:projectHome)) AND state IN ('sending','unknown')) ORDER BY CASE WHEN state IN ('sending','unknown') THEN 0 ELSE 1 END,createdAt DESC LIMIT 100") fun managementVisibleResourceOperations(scope:String,apiScope:String,url:String="",projectHome:String=""):Flow<List<ManagementOperation>>
    // Instance controls span the exact URL; project writes additionally share a verified home across aliases.
    @Query("SELECT * FROM management_operations WHERE (resourceScope=:scope OR (url=:url AND (action IN ('RelayLinkStart','RelayUnlink','CustomEndpointProbe','KanbanBoardCreate','KanbanBoardUpdate','KanbanBoardArchive','KanbanBoardDelete','KanbanBoardSwitch','KanbanTaskCreate','KanbanTaskMetadata','KanbanTaskPriority','KanbanTaskAssignee','KanbanTaskComment','KanbanTaskLink','KanbanTaskUnlink','KanbanTaskDelete','KanbanTaskModelOverride','KanbanTaskReasoningOverride','KanbanTaskStatus') OR (:projectHome!='' AND projectHome=:projectHome)))) AND state IN ('sending','unknown')") suspend fun unresolvedManagementOperations(scope:String,url:String="",projectHome:String=""):List<ManagementOperation>
    @Query("SELECT * FROM management_operations WHERE `key`=:key") suspend fun managementOperation(key:String):ManagementOperation?
    @Insert suspend fun insertManagementOperation(receipt:ManagementOperation)
    @Transaction suspend fun claimManagementOperation(receipt:ManagementOperation) {
        require(receipt.resourceScope==managementResourceScope(receipt.url,receipt.profile)) {"Invalid management resource identity."}
        require(if(HostProjectAction.receipt(receipt.action) || CanonicalWorkspaceAction.receipt(receipt.action)) receipt.projectHome.matches(Regex("[a-f0-9]{64}")) else receipt.projectHome.isEmpty()) {"Invalid project store identity."}
        val accessHome=if(HostKanbanBoardAction.receipt(receipt.action) || HostKanbanTaskAction.receipt(receipt.action) || HostKanbanStatusChange.receipt(receipt.action)) HostKanbanBoardChange.accessHome(receipt.requestSummary) else receipt.projectHome
        if(unresolvedManagementOperations(receipt.resourceScope,receipt.url,accessHome).isNotEmpty()) throw ManagementOutcomePending()
        insertManagementOperation(receipt)
    }
    @Query("UPDATE management_operations SET state='unknown' WHERE state='sending'") suspend fun recoverManagementOperations()
    @Query("UPDATE management_operations SET state=:state,resultSummary=:result WHERE `key`=:key AND state IN ('sending','unknown')") suspend fun finishManagementOperation(key:String,state:String,result:String)
    @Query("UPDATE management_operations SET resultSummary=:result WHERE `key`=:key AND action='OAuthStart' AND state='acknowledged'") suspend fun updateMcpOAuthObservation(key:String,result:String)
    @Query("UPDATE management_operations SET resultSummary=:result WHERE `key`=:key AND action='RelayLinkStart' AND state='acknowledged'") suspend fun updateRelayCodeSummary(key:String,result:String)
    @Transaction suspend fun observeRelayCodeState(key:String,status:String) {
        require(status in setOf("pending","inactive","refused"))
        val row=managementOperation(key) ?: return
        if(row.action!="RelayLinkStart" || row.state!="acknowledged") return
        val original=kotlinx.serialization.json.Json.parseToJsonElement(row.resultSummary).jsonObject
        if(original["code_issued"]==kotlinx.serialization.json.JsonPrimitive(true) && original["status"] in setOf(kotlinx.serialization.json.JsonPrimitive("unverified"),kotlinx.serialization.json.JsonPrimitive("pending"))) updateRelayCodeSummary(key,kotlinx.serialization.json.JsonObject(original+mapOf("status" to kotlinx.serialization.json.JsonPrimitive(status))).toString())
    }
    @Query("UPDATE management_operations SET resultSummary=:result WHERE `key`=:key AND action='ProviderOAuthStart' AND state='acknowledged'") suspend fun updateProviderOAuthObservation(key:String,result:String)
    @Query("UPDATE management_operations SET state='confirmed',resultSummary=:result WHERE `key`=:key AND state='acknowledged'") suspend fun confirmManagementOperation(key:String,result:String)
    @Query("SELECT * FROM management_operations WHERE action='CanonicalResume' AND state='confirmed' AND apiScope=:apiScope AND url=:url AND profile=:profile AND actor=:actor AND projectHome=:home AND target=:requested ORDER BY createdAt DESC,`key` DESC LIMIT 50")
    suspend fun canonicalViewerReceipts(apiScope:String,url:String,profile:String,actor:String,home:String,requested:String):List<ManagementOperation>
    @Query("UPDATE management_operations SET resultSummary=:result WHERE `key`=:key AND action='CanonicalResume' AND state='confirmed' AND resultSummary=:previous")
    suspend fun updateCanonicalViewerCheckpoint(key:String,previous:String,result:String):Int


    @Query("UPDATE management_operations SET state='reviewed' WHERE `key`=:key AND ((resourceScope=:scope AND (projectHome='' OR projectHome=:projectHome)) OR (url=:url AND (action IN ('RelayLinkStart','RelayUnlink','CustomEndpointProbe','KanbanBoardCreate','KanbanBoardUpdate','KanbanBoardArchive','KanbanBoardDelete','KanbanBoardSwitch','KanbanTaskCreate','KanbanTaskMetadata','KanbanTaskPriority','KanbanTaskAssignee','KanbanTaskComment','KanbanTaskLink','KanbanTaskUnlink','KanbanTaskDelete','KanbanTaskModelOverride','KanbanTaskReasoningOverride','KanbanTaskStatus') OR (:projectHome!='' AND projectHome=:projectHome)))) AND state='unknown'") suspend fun reviewManagementOperation(key:String,scope:String,url:String="",projectHome:String="")

    @Query("SELECT * FROM run_reports WHERE sessionKey=:key ORDER BY updatedAt DESC LIMIT 100") fun reports(key:String):Flow<List<RunReport>>
    @Query("SELECT * FROM run_reports WHERE `key`=:key") suspend fun report(key:String):RunReport?
    @Upsert suspend fun saveReport(report:RunReport)
    @Query("DELETE FROM run_reports WHERE sessionKey=:key") suspend fun deleteReports(key:String)
    @Query("DELETE FROM run_reports") suspend fun clearReports()
    suspend fun recordAccounting(c:Conversation,telemetry:String) {
        if(telemetry.isBlank() || (c.pendingKey.isBlank() && c.runId.isBlank())) return
        val key="${c.key}|${c.pendingKey.ifBlank {c.runId}}"
        val merged=RunTelemetry.merge(report(key)?.telemetryJson.orEmpty(),RunTelemetry.parse(telemetry))
        saveReport(RunReport(key,c.key,c.runId,c.pendingKey,c.transport,merged,c.status,System.currentTimeMillis()))
    }

    @Query("SELECT * FROM server_operations WHERE scope=:scope ORDER BY CASE WHEN state IN ('sending','unknown') THEN 0 ELSE 1 END, createdAt DESC LIMIT 100") fun serverOperations(scope: String): Flow<List<ServerOperation>>
    @Query("SELECT * FROM server_operations WHERE scope=:scope AND state IN ('sending','unknown')") suspend fun unresolvedServerOperations(scope: String): List<ServerOperation>
    @Insert suspend fun insert(operation: ServerOperation)
    @Transaction suspend fun claimServerOperation(receipt: ServerOperation) {
        require(unresolvedServerOperations(receipt.scope).isEmpty()) {"Review the uncertain previous server operation before continuing."}
        insert(receipt)
    }
    @Query("UPDATE server_operations SET state='unknown' WHERE state='sending'") suspend fun recoverServerOperations()
    @Query("UPDATE server_operations SET state=:state,responseJson=:response WHERE `key`=:key AND state IN ('sending','unknown')") suspend fun finishServerOperation(key: String,state: String,response: String)
    @Query("UPDATE server_operations SET state='reviewed' WHERE `key`=:key AND scope=:scope AND state='unknown'") suspend fun reviewServerOperation(key: String,scope: String)
    @Query("DELETE FROM server_operations WHERE sessionKey=:key") suspend fun deleteServerOperations(key: String)
    @Query("DELETE FROM server_operations") suspend fun clearServerOperations()
    @Transaction suspend fun saveSessionDetails(expected: Conversation,details: JsonObject) {
        val current=conversation(expected.key) ?: return
        if(current.scope!=expected.scope || current.remoteId!=expected.remoteId) return
        save(current.copy(detailsJson=SessionOrigin.mergeDetails(current.detailsJson,details)))
    }
    @Query("SELECT * FROM job_operations WHERE scope=:scope AND state IN ('sending','unknown') ORDER BY createdAt DESC") fun jobOperations(scope: String): Flow<List<JobOperation>>
    @Query("SELECT * FROM job_operations WHERE scope=:scope AND state IN ('sending','unknown')") suspend fun unresolvedOperations(scope: String): List<JobOperation>
    @Upsert suspend fun save(operation: JobOperation)
    @Query("UPDATE job_operations SET state='unknown' WHERE state='sending'") suspend fun recoverJobOperations()
    @Query("UPDATE job_operations SET state=:state WHERE `key`=:key") suspend fun jobOperationState(key: String,state: String)
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun claimNotification(receipt: NotificationReceipt): Long
    @Query("SELECT * FROM sessions WHERE scope=:scope AND runId!='' AND status NOT IN ('idle','completed','failed','cancelled','interrupted','response_finished','response_failed','response_cancelled','response_incomplete','incomplete') ORDER BY updatedAt DESC LIMIT 20") suspend fun trackedRuns(scope: String): List<Conversation>

    @Query("SELECT * FROM sessions WHERE scope = :scope ORDER BY pinned DESC, updatedAt DESC") fun conversations(scope: String): Flow<List<Conversation>>
    @Query("SELECT * FROM sessions WHERE `key` = :key") suspend fun conversation(key: String): Conversation?
    @Upsert suspend fun save(conversation: Conversation)
    @Query("SELECT * FROM sessions WHERE scope = :scope AND remoteId = :remoteId LIMIT 1") suspend fun byRemoteId(scope: String, remoteId: String): Conversation?
    @Query("UPDATE sessions SET title = :title, updatedAt = MAX(updatedAt, :updatedAt) WHERE `key` = :key")
    suspend fun updateMetadata(key: String,title: String,updatedAt: Long)
    @Query("UPDATE sessions SET title = :title, updatedAt = MAX(updatedAt, :updatedAt), detailsJson = :details WHERE `key` = :key")
    suspend fun updateRemoteMetadata(key:String,title:String,updatedAt:Long,details:String)
    @Transaction suspend fun mergeRemoteSession(scope: String,remote: RemoteSession,fallbackTime: Long) {
        val current=byRemoteId(scope,remote.id)
        val details=SessionOrigin.retain(current?.detailsJson.orEmpty(),remote.source)
        if(current==null) save(Conversation("$scope:${remote.id}",scope,remote.id,remote.title,remote.updatedAt.takeIf {it>0} ?: fallbackTime,detailsJson=details))
        else updateRemoteMetadata(current.key,remote.title.ifBlank {current.title},remote.updatedAt,details)
    }
    @Query("DELETE FROM messages WHERE sessionKey = :key") suspend fun deleteMessages(key: String)
    @Query("DELETE FROM sessions WHERE `key` = :key") suspend fun deleteSession(key: String)
    @Transaction suspend fun deleteConversation(key: String) { deleteMessages(key); deleteEvents(key); deleteControls(key); deleteServerOperations(key); deleteReports(key); deleteSession(key) }
    @Query("SELECT * FROM tool_events WHERE sessionKey = :key ORDER BY receivedAt DESC LIMIT 200") fun events(key: String): Flow<List<ToolEvent>>
    @Query("DELETE FROM tool_events WHERE sessionKey=:key AND `key` NOT IN (SELECT `key` FROM tool_events WHERE sessionKey=:key ORDER BY receivedAt DESC,sequence DESC LIMIT 500)") suspend fun pruneEvents(key:String)
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
    @Query("UPDATE pending_controls SET state = :state WHERE `key` = :key AND (state != 'resolved' OR :state = 'resolved')") suspend fun controlState(key: String,state: String)
    /** Generation-aware merge shared by SSE and GET; settled/ambiguous responses never auto-reopen. */
    @Transaction suspend fun mergeControl(row: PendingControl,resetUnknown: Boolean=false) {
        val old=control(row.key)
        if(old?.generation!=null && (row.generation==null || row.generation<old.generation)) return
        val same=old!=null && old.generation==row.generation
        val state=if(same && (old!!.state in setOf("resolved","responding") || (old.state=="unknown" && !resetUnknown))) old.state else "pending"
        save(row.copy(state=state,receivedAt=if(same) old!!.receivedAt else row.receivedAt))
    }
    @Transaction suspend fun replaceControls(expected: Conversation,rows: List<PendingControl>,resetUnknown: Boolean) {
        val current=conversation(expected.key) ?: return
        if(!current.sameSubmission(expected)) return
        if(current.status in RunStates.terminal) {deleteRunControls(expected.key,expected.runId);return}
        val incoming=rows.map {it.key}.toSet()
        // Keep tombstones: a later stream frame must not resurrect an absent settled prompt.
        runControls(expected.key,expected.runId).filter {it.key !in incoming}.forEach {controlState(it.key,"resolved")}
        rows.forEach {mergeControl(it,resetUnknown)}
    }
    @Transaction suspend fun claimControl(expected: PendingControl,receipt: Conversation): Boolean {
        val current=conversation(receipt.key) ?: return false
        if(!current.sameSubmission(receipt) || current.status in RunStates.terminal || control(expected.key)!=expected || expected.state!="pending") return false
        save(expected.copy(state="responding"));return true
    }
    @Transaction suspend fun finishControl(expected: PendingControl,state: String) {
        val current=control(expected.key) ?: return
        if(current.generation==expected.generation && current.runId==expected.runId && current.state=="responding") controlState(expected.key,state)
    }
    @Transaction suspend fun beginTurn(receipt: Conversation,user: ChatMessage,assistant: ChatMessage) {
        val previous=conversation(receipt.key)
        require(previous==null || (previous.scope==receipt.scope && previous.status in (RunStates.terminal+"idle"))) {"The previous turn must settle first."}
        require(receipt.pendingRequestHash.isNotBlank()) {"Save the exact request before admitting a turn."}
        save(if(previous==null) receipt else receipt.copy(title=previous.title,pinned=previous.pinned,hidden=previous.hidden,syncedAt=previous.syncedAt,detailsJson=previous.detailsJson))
        save(user);save(assistant)
    }
    @Transaction suspend fun recordAdmission(expected: Conversation,run: String): Conversation {
        val current=conversation(expected.key) ?: error("The request receipt is missing.")
        require(expected.sameSubmission(current,allowAdmission=true) && (current.runId.isBlank() || current.runId==run)) {"The admission belongs to an older turn."}
        return current.copy(runId=run).also {save(it)}
    }
    @Transaction suspend fun submissionUnknown(expected: Conversation) {
        val current=conversation(expected.key) ?: return
        if(!expected.sameSubmission(current,allowAdmission=true) || current.status in RunStates.terminal) return
        save(current.copy(status="unknown"))
    }
    /** A response belongs to one durable submission, even when a conversation is reused. */
    @Transaction suspend fun applyRunSnapshot(expected: Conversation,snapshot: JsonObject): Conversation? {
        val current=conversation(expected.key) ?: return null
        if(!current.sameSubmission(expected)) return null
        val status=snapshot.text("status").ifBlank {"unknown"}
        if(current.status in RunStates.terminal && status !in RunStates.terminal) return null
        val generation=(snapshot["execution_generation"] as? JsonPrimitive)?.longOrNull
        if(generation!=null && current.executionGeneration!=null && generation<current.executionGeneration) return null
        val fresh=current.copy(status=status,remoteId=snapshot.text("session_id").ifBlank {current.remoteId},
            admissionId=snapshot.text("admission_id").ifBlank {current.admissionId},executionGeneration=generation ?: current.executionGeneration)
        save(fresh)
        val answer=history(expected.key).let {rows -> if(expected.pendingKey.isNotBlank()) rows.firstOrNull {it.id=="${expected.pendingKey}_assistant"} else rows.lastOrNull {it.role=="assistant"}}
        if(answer!=null) {
            val telemetry=RunTelemetry.merge(answer.telemetryJson,snapshot)
            save(answer.copy(content=snapshot.text("output").ifBlank {answer.content},status=status,telemetryJson=telemetry))
            recordAccounting(fresh,telemetry)
        } else recordAccounting(fresh,RunTelemetry.merge("",snapshot))
        return fresh
    }
    @Transaction suspend fun saveTurn(receipt: Conversation,message: ChatMessage,control: PendingControl?,event: ToolEvent?,settledId: String,settledGeneration: Long?=null) {
        val current=conversation(receipt.key) ?: return
        if(!current.sameSubmission(receipt,allowAdmission=true) || (receipt.lastEventSeq>=0 && current.lastEventSeq>receipt.lastEventSeq)) return
        if(current.status in RunStates.terminal && receipt.status !in RunStates.terminal) return
        save(receipt.copy(title=current.title,pinned=current.pinned,hidden=current.hidden,syncedAt=current.syncedAt,detailsJson=current.detailsJson))
        val saved=this.message(message.id)
        val telemetry=RunTelemetry.merge(saved?.telemetryJson.orEmpty(),RunTelemetry.parse(message.telemetryJson))
        save(message.copy(telemetryJson=telemetry));recordAccounting(receipt,telemetry)
        if(control!=null && control.runId==receipt.runId) mergeControl(control)
        if(event!=null) {save(event);pruneEvents(receipt.key)}
        if(settledId.isNotBlank()) runControls(receipt.key,receipt.runId).filter {it.requestId==settledId && (it.generation==null || it.generation==settledGeneration)}.forEach {controlState(it.key,"resolved")}
        if(receipt.status in RunStates.terminal) deleteRunControls(receipt.key,receipt.runId)
    }
    @Transaction suspend fun replaceHistory(expected: Conversation, remoteId: String, messages: List<ChatMessage>) {
        val key=expected.key
        val current = conversation(key) ?: return
        if(!current.sameSubmission(expected)) return
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
    @Query("SELECT * FROM messages WHERE id=:id") suspend fun message(id:String):ChatMessage?
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
    @Transaction suspend fun clearChats() { clearMessages(); clearControls(); clearEvents(); clearServerOperations(); clearReports(); clearSessions() }
}
@Database(entities = [Conversation::class, ChatMessage::class, LocalTask::class, CachedJob::class,ToolEvent::class,PendingControl::class,JobOperation::class,NotificationReceipt::class,ServerOperation::class,RunReport::class,ManagementOperation::class], version = 11, exportSchema = true)
abstract class HermesDatabase : RoomDatabase() { abstract fun dao(): HermesDao }
