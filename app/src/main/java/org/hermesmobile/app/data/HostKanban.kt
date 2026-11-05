package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import java.util.Collections

class HostKanbanUnsupported:Exception("Unsupported Kanban response.")
class HostKanbanScopeChanged:Exception("Kanban access or reported board mapping changed.")

data class HostKanbanBoard(val slug:String,val name:String,val description:String?,val icon:String?,val color:String?,val dbPath:String,val workdir:String?,val projectId:String?,val projectName:String?,val archived:Boolean,val current:Boolean,val counts:Map<String,Long>,val total:Long?,val workspaceKind:String?,val createdAt:Long?) {
    fun sameMapping(other:HostKanbanBoard)=slug==other.slug && dbPath==other.dbPath && archived==other.archived
    override fun toString()="HostKanbanBoard(redacted)"
}
data class HostKanbanBoardDefinition(val slug:String,val name:String,val description:String?,val icon:String?,val color:String?,val dbPath:String,val workdir:String?,val projectId:String?,val archived:Boolean,val createdAt:Long?) {override fun toString()="HostKanbanBoardDefinition(redacted)"}
fun HostKanbanBoard.definition()=HostKanbanBoardDefinition(slug,name,description,icon,color,dbPath,workdir,projectId,archived,createdAt)

data class HostKanbanInventory(val owner:ManagementTarget,val profileHomeId:String,val boards:List<HostKanbanBoard>,val current:String,val observedAt:Long) {override fun toString()="HostKanbanInventory(redacted)"}
data class HostKanbanTask(val id:String,val title:String,val status:String,val assignee:String?,val tenant:String?,val priority:Long,val body:String?,val result:String?,val summary:String?,val createdAt:Long?,val startedAt:Long?,val completedAt:Long?,val currentRunId:Long?,val heartbeat:Long?,val fields:Map<String,String>,val reportedOverrideFields:Set<String> = emptySet()) {override fun toString()="HostKanbanTask(redacted)"}
data class HostKanbanColumn(val name:String,val tasks:List<HostKanbanTask>) {override fun toString()="HostKanbanColumn(redacted)"}
data class HostKanbanSnapshot(val owner:ManagementTarget,val profileHomeId:String,val board:HostKanbanBoard,val columns:List<HostKanbanColumn>,val tenants:List<String>,val assignees:List<String>,val latestEventId:Long,val hostNow:Long,val observedAt:Long) {override fun toString()="HostKanbanSnapshot(redacted)"}
data class HostKanbanComment(val id:Long,val author:String,val body:String,val createdAt:Long) {override fun toString()="HostKanbanComment(redacted)"}
data class HostKanbanRun(val id:Long,val status:String,val outcome:String?,val profile:String?,val step:String?,val startedAt:Long,val endedAt:Long?,val heartbeat:Long?,val summary:String?,val error:String?) {override fun toString()="HostKanbanRun(redacted)"}
data class HostKanbanEvent(val id:Long,val kind:String,val createdAt:Long,val runId:Long?) {override fun toString()="HostKanbanEvent(redacted)"}
data class HostKanbanAttachment(val id:Long,val filename:String,val type:String?,val size:Long,val author:String?,val createdAt:Long) {override fun toString()="HostKanbanAttachment(redacted)"}
data class HostKanbanLinkedTask(val id:String,val title:String,val status:String,val summary:String?=null,val result:String?=null) {override fun toString()="HostKanbanLinkedTask(redacted)"}
data class HostKanbanDetail(val owner:ManagementTarget,val profileHomeId:String,val board:HostKanbanBoard,val task:HostKanbanTask,val comments:List<HostKanbanComment>,val runs:List<HostKanbanRun>,val events:List<HostKanbanEvent>,val attachments:List<HostKanbanAttachment>,val parents:List<String>,val children:List<String>,val linked:List<HostKanbanLinkedTask>,val childResults:List<HostKanbanLinkedTask>,val observedAt:Long) {override fun toString()="HostKanbanDetail(redacted)"}

/** Whitelisted private projections only. No claim locks, idempotency keys, event payloads or run metadata. */
object HostKanbanProtocol {
    fun slug(value:String)=value.matches(Regex("[a-z0-9][a-z0-9_-]{0,63}"))
    fun id(value:String)=value.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.:-]{0,255}"))
    private fun <T> frozen(values:List<T>):List<T> = Collections.unmodifiableList(values)
    private fun <K,V> frozen(values:Map<K,V>):Map<K,V> = Collections.unmodifiableMap(values)
    private inline fun <T> decode(block:()->T):T=try {block()} catch(_:Exception) {throw HostKanbanUnsupported()}
    private fun text(j:JsonObject,key:String,max:Int,optional:Boolean=false,multiline:Boolean=false):String? {
        val v=j[key];if(v==null || v is JsonNull) {require(optional);return null}
        require(v is JsonPrimitive && v.isString)
        return v.content.also {s->require(s.length<=max && s.none {it.isISOControl() && !(multiline && it in "\n\r\t")} && !s.codePoints().anyMatch {it in 0xD800..0xDFFF})}
    }
    private fun number(j:JsonObject,key:String,optional:Boolean=false,min:Long=0,max:Long=Long.MAX_VALUE):Long? {
        val v=j[key];if(v==null || v is JsonNull) {require(optional);return null}
        require(v is JsonPrimitive && !v.isString)
        return v.longOrNull!!.also {require(it in min..max)}
    }
    private fun time(j:JsonObject,key:String,optional:Boolean=true)=number(j,key,optional,max=Long.MAX_VALUE/1000)
    private fun bool(j:JsonObject,key:String):Boolean {
        val v=j[key] ?: return false;require(v is JsonPrimitive && !v.isString && v.booleanOrNull!=null);return v.boolean
    }
    private fun rows(j:JsonObject,key:String,max:Int):List<JsonObject> {val a=j[key] as JsonArray;require(a.size<=max);return a.map {it as JsonObject}}
    private fun strings(j:JsonObject,key:String,max:Int,length:Int,ids:Boolean=false):List<String> {
        val a=j[key] as JsonArray;require(a.size<=max)
        val values=a.map {v->text(buildJsonObject {put("value",v)},"value",length)!!.also {if(ids) require(HostKanbanProtocol.id(it))}}
        require(values.distinct().size==values.size);return frozen(values)
    }
    private fun context(owner:ManagementTarget,home:String) {GatewayCronInventory.requireProfile(owner.profile);require(home.matches(Regex("[a-f0-9]{64}")))}
    fun metadata(j:JsonObject):HostKanbanBoardDefinition=decode {
        require(j.keys.containsAll(setOf("slug","name","description","icon","color","db_path","default_workdir","project_id","archived","created_at")))
        val slug=text(j,"slug",64)!!.also {require(HostKanbanProtocol.slug(it))}
        HostKanbanBoardDefinition(slug,text(j,"name",1024,multiline=true)!!,text(j,"description",16384,true,true),text(j,"icon",512,true),text(j,"color",128,true),text(j,"db_path",4096)!!.also {require(it.isNotBlank())},text(j,"default_workdir",4096,true),text(j,"project_id",512,true),bool(j,"archived"),time(j,"created_at"))
    }
    private fun board(j:JsonObject):HostKanbanBoard {
        val slug=text(j,"slug",64)!!.also {require(HostKanbanProtocol.slug(it))}
        val counts=j["counts"] as JsonObject;require(counts.size<=64)
        val projected=counts.mapValues {(key,v)->require(id(key));number(buildJsonObject {put("value",v)},"value")!!}
        return HostKanbanBoard(slug,text(j,"name",1024,true,true) ?: slug,text(j,"description",16384,true,true),text(j,"icon",512,true),text(j,"color",128,true),text(j,"db_path",4096)!!.also {require(it.isNotBlank())},text(j,"default_workdir",4096,true),text(j,"project_id",512,true),text(j,"project_name",1024,true,true),bool(j,"archived"),bool(j,"is_current"),frozen(projected),number(j,"total",true),text(j,"default_workspace_kind",128,true),time(j,"created_at"))
    }
    fun inventory(j:JsonObject,owner:ManagementTarget,home:String,observedAt:Long=System.currentTimeMillis()):HostKanbanInventory=decode {
        context(owner,home);val boards=rows(j,"boards",500).map(::board);require(boards.map {it.slug}.distinct().size==boards.size)
        val current=text(j,"current",64)!!.also {require(HostKanbanProtocol.slug(it))}
        HostKanbanInventory(owner,home,frozen(boards),current,observedAt)
    }
    private fun task(j:JsonObject):HostKanbanTask {
        val id=text(j,"id",256)!!.also {require(HostKanbanProtocol.id(it))};val status=text(j,"status",128)!!.also {require(HostKanbanProtocol.id(it))}
        val fields=linkedMapOf<String,String>()
        for((key,max) in listOf("workspace_kind" to 128,"workspace_path" to 4096,"branch_name" to 512,"project_id" to 512,"workflow_template_id" to 256,"current_step_key" to 256,"model_override" to 512,"provider_override" to 256,"reasoning_effort" to 128,"completion_contract" to 4096,"last_failure_error" to 16384)) text(j,key,max,true,true)?.let {fields[key]=it}
        for(key in listOf("max_runtime_seconds","consecutive_failures","max_retries","goal_max_turns","block_recurrences")) number(j,key,true)?.let {fields[key]=it.toString()}
        time(j,"current_run_started_at")?.let {fields["current_run_started_at"]=it.toString()}
        j["goal_mode"]?.takeUnless {it is JsonNull}?.let {fields["goal_mode"]=bool(j,"goal_mode").toString()}
        j["skills"]?.takeUnless {it is JsonNull}?.let {fields["skills"]=strings(j,"skills",256,512).joinToString("\n")}
        return HostKanbanTask(id,text(j,"title",4096,multiline=true)!!,status,text(j,"assignee",256,true),text(j,"tenant",512,true),number(j,"priority",false,Int.MIN_VALUE.toLong(),Int.MAX_VALUE.toLong())!!,text(j,"body",262144,true,true),text(j,"result",262144,true,true),text(j,"latest_summary",262144,true,true),time(j,"created_at"),time(j,"started_at"),time(j,"completed_at"),number(j,"current_run_id",true,1),time(j,"last_heartbeat_at"),frozen(fields),java.util.Collections.unmodifiableSet(setOf("model_override","provider_override","reasoning_effort").filterTo(linkedSetOf()) {it in j}))
    }
    fun taskAcknowledgement(j:JsonObject):HostKanbanTask=decode {task(j["task"] as JsonObject)}
    fun snapshot(j:JsonObject,owner:ManagementTarget,home:String,board:HostKanbanBoard,observedAt:Long=System.currentTimeMillis()):HostKanbanSnapshot=decode {
        context(owner,home);require(slug(board.slug) && !board.archived)
        val columns=rows(j,"columns",64).map {c->HostKanbanColumn(text(c,"name",128)!!.also {require(HostKanbanProtocol.id(it))},frozen(rows(c,"tasks",2000).map(::task)))}
        require(columns.map {it.name}.distinct().size==columns.size)
        val ids=columns.flatMap {it.tasks}.map {it.id};require(ids.size<=2000 && ids.distinct().size==ids.size)
        HostKanbanSnapshot(owner,home,board,frozen(columns),strings(j,"tenants",512,512),strings(j,"assignees",512,256),number(j,"latest_event_id")!!,time(j,"now",false)!!,observedAt)
    }
    fun detail(j:JsonObject,owner:ManagementTarget,home:String,board:HostKanbanBoard,expectedId:String,observedAt:Long=System.currentTimeMillis()):HostKanbanDetail=decode {
        context(owner,home);require(slug(board.slug) && !board.archived && id(expectedId))
        val task=task(j["task"] as JsonObject);require(task.id==expectedId)
        fun owned(row:JsonObject) {require(text(row,"task_id",256)==expectedId)}
        val comments=rows(j,"comments",2000).map {c->owned(c);HostKanbanComment(number(c,"id",min=1)!!,text(c,"author",512,multiline=true)!!,text(c,"body",262144,multiline=true)!!,time(c,"created_at",false)!!)}
        val runs=rows(j,"runs",1000).map {v->owned(v);HostKanbanRun(number(v,"id",min=1)!!,text(v,"status",128)!!,text(v,"outcome",128,true),text(v,"profile",256,true),text(v,"step_key",256,true),time(v,"started_at",false)!!,time(v,"ended_at"),time(v,"last_heartbeat_at"),text(v,"summary",262144,true,true),text(v,"error",65536,true,true))}
        val events=rows(j,"events",5000).map {v->owned(v);HostKanbanEvent(number(v,"id",min=1)!!,text(v,"kind",128)!!,time(v,"created_at",false)!!,number(v,"run_id",true,1))}
        val attachments=rows(j,"attachments",500).map {v->owned(v);HostKanbanAttachment(number(v,"id",min=1)!!,text(v,"filename",1024,multiline=true)!!,text(v,"content_type",256,true),number(v,"size")!!,text(v,"uploaded_by",512,true),time(v,"created_at",false)!!)}
        for(values in listOf(comments.map {it.id},runs.map {it.id},events.map {it.id},attachments.map {it.id})) require(values.distinct().size==values.size)
        val links=j["links"] as JsonObject;val parents=strings(links,"parents",2000,256,true);val children=strings(links,"children",2000,256,true)
        fun linkRows(key:String):List<HostKanbanLinkedTask> = frozen(rows(j,key,4000).map {v->HostKanbanLinkedTask(text(v,"id",256)!!.also {require(HostKanbanProtocol.id(it))},text(v,"title",4096,multiline=true)!!,text(v,"status",128)!!,text(v,"latest_summary",262144,true,true),text(v,"result",262144,true,true))}.also {values->require(values.map {it.id}.distinct().size==values.size)})
        val linked=linkRows("link_tasks");val results=linkRows("child_results")
        require(linked.all {it.id in parents || it.id in children} && results.all {it.id in children})
        HostKanbanDetail(owner,home,board,task,frozen(comments),frozen(runs),frozen(events),frozen(attachments),parents,children,linked,results,observedAt)
    }
}
