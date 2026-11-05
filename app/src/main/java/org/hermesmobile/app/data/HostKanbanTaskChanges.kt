package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.util.Collections
import java.util.Locale
import java.util.UUID

enum class HostKanbanTaskAction(val method:String) {
    Create("POST"),Metadata("PATCH"),Priority("PATCH"),Assignee("PATCH"),Comment("POST"),Link("POST"),Unlink("DELETE"),Delete("DELETE");
    val receiptAction get()="KanbanTask"+name
    companion object {fun receipt(value:String)=entries.any {it.receiptAction==value}}
}
class HostKanbanTaskInvalid:Exception("Invalid task change.")
class HostKanbanTaskChanged:Exception("Reviewed task or board changed.")
class HostKanbanTaskUncertain(val receipt:ManagementOperation?):Exception("Task outcome is unknown.")
class HostKanbanTaskReadbackPending(val receipt:ManagementOperation?):Exception("Task acknowledgement retained; readback pending.")
class HostKanbanTaskRefused(val receipt:ManagementOperation?):Exception("Task request was not attempted.")
private fun taskHash(value:String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") {"%02x".format(it)}

data class HostKanbanTaskDraft(
    val title:String,val body:String?=null,val assignee:String?=null,val tenant:String?=null,val priority:Int=0,
    val workspaceKind:String?=null,val workspacePath:String?=null,val parents:List<String> = emptyList(),val triage:Boolean=false,
    val maxRuntimeSeconds:Int?=null,val skills:List<String>?=null,val goalMode:Boolean=false,val goalMaxTurns:Int?=null,
    val model:String?=null,val provider:String?=null,val reasoning:String?=null,val projectId:String?=null
) {override fun toString()="HostKanbanTaskDraft(redacted)"}
data class HostKanbanTaskAck(val task:HostKanbanTask?,val effectReported:Boolean,val gated:Boolean?=null,val warning:String?=null) {override fun toString()="HostKanbanTaskAck(redacted)"}

/** One host write phase. Exact values and the create nonce stay private; only hashes/booleans enter Room. */
class HostKanbanTaskChange private constructor(
    val inventory:HostKanbanInventory,val snapshot:HostKanbanSnapshot,val action:HostKanbanTaskAction,
    val subject:HostKanbanDetail?,val related:HostKanbanDetail?,val parents:List<HostKanbanTask>,val values:JsonObject,val project:HostProjectInfo?
) {
    val owner get()=inventory.owner
    val home get()=inventory.profileHomeId
    val board get()=snapshot.board
    val id get()=subject?.task?.id
    val receiptTarget="sha256:"+taskHash(board.slug+"\u0000"+id.orEmpty())
    override fun toString()="HostKanbanTaskChange(redacted)"
    fun body()=values
    fun sameInventory(current:HostKanbanInventory)=current.owner==owner && current.profileHomeId==home && current.boards.singleOrNull {it.slug==board.slug}?.definition()==board.definition() && current.boards.singleOrNull {it.slug=="default"}?.dbPath==inventory.boards.single {it.slug=="default"}.dbPath
    fun sameDetail(wanted:HostKanbanDetail,current:HostKanbanDetail)=current.owner==owner && current.profileHomeId==home && current.board.definition()==board.definition() && current.copy(board=wanted.board,observedAt=0)==wanted.copy(observedAt=0)
    fun projectCurrent(current:HostProjectSnapshot)=project==null || current.owner==owner && current.homeId==home && current.projects.singleOrNull {it.id==project.id}==project && !HostProjectChange.collision(current,project.id)
    fun summary()=buildJsonObject {
        put("schema",1);put("access_home_sha256",home);put("board_sha256",taskHash(board.slug));put("db_sha256",taskHash(board.dbPath))
        id?.let {put("task_sha256",taskHash(it))};related?.let {put("parent_sha256",taskHash(it.task.id))}
        put("requested_sha256",taskHash(values.toString()));put("fields",JsonArray(values.keys.filter {it!="idempotency_key"}.sorted().map(::JsonPrimitive)))
        put("parent_count",parents.size);put("project_binding",project!=null);put("path_supplied",values.containsKey("workspace_path"));put("phase",action.name)
    }
    private fun createMatches(task:HostKanbanTask):Boolean {
        // A different reported triage choice remains an ACK, not a matching requested hold.
        if((task.status=="triage")!=values.getValue("triage").jsonPrimitive.boolean) return false
        if(task.title!=values.getValue("title").jsonPrimitive.content || task.priority!=values.getValue("priority").jsonPrimitive.long) return false
        for(key in listOf("body","assignee","tenant")) if(values.containsKey(key)) {
            val actual=when(key) {"body"->task.body;"assignee"->task.assignee;else->task.tenant}
            if(actual!=values.getValue(key).jsonPrimitive.content) return false
        }
        for((key,field) in listOf("model_override" to "model_override","provider_override" to "provider_override","reasoning_effort" to "reasoning_effort","workspace_path" to "workspace_path")) if(values.containsKey(key) && task.fields[field]!=values.getValue(key).jsonPrimitive.content) return false
        val wantedKind=values["workspace_kind"]?.jsonPrimitive?.content ?: "scratch"
        val resolvedKind=if(project?.primaryPath?.isNotBlank()==true && wantedKind=="scratch") "worktree" else wantedKind
        if(task.fields["workspace_kind"]!=resolvedKind || task.fields["project_id"]!=project?.id) return false
        for(key in listOf("max_runtime_seconds","goal_max_turns","goal_mode")) if(values.containsKey(key) && task.fields[key]!=values.getValue(key).jsonPrimitive.content) return false
        values["skills"]?.jsonArray?.let {if(task.fields["skills"]!=it.joinToString("\n") {v->v.jsonPrimitive.content}) return false}
        return true
    }
    fun acknowledge(j:JsonObject):HostKanbanTaskAck=try {
        when(action) {
            HostKanbanTaskAction.Create,HostKanbanTaskAction.Metadata,HostKanbanTaskAction.Priority,HostKanbanTaskAction.Assignee->{
                val task=HostKanbanProtocol.taskAcknowledgement(j);if(action!=HostKanbanTaskAction.Create) require(task.id==id)
                val matches=when(action) {
                    HostKanbanTaskAction.Create->createMatches(task)
                    HostKanbanTaskAction.Metadata->values.all {(key,v)->(if(key=="title") task.title else task.body)==v.jsonPrimitive.content}
                    HostKanbanTaskAction.Priority->task.priority==values.getValue("priority").jsonPrimitive.long
                    else->task.assignee==values.getValue("assignee").jsonPrimitive.content.takeIf {it.isNotEmpty()}
                }
                val warning=j["warning"]?.takeUnless {it is JsonNull}?.let {require(it is JsonPrimitive && it.isString && safe(it.content,16384,true));it.content}
                HostKanbanTaskAck(task,matches,warning=warning)
            }
            HostKanbanTaskAction.Delete->{require(j["deleted"]==JsonPrimitive(true) && j["task_id"]==JsonPrimitive(id));HostKanbanTaskAck(null,true)}
            HostKanbanTaskAction.Comment->{require(j["ok"]==JsonPrimitive(true));HostKanbanTaskAck(null,true)}
            HostKanbanTaskAction.Link->{require(j["ok"]==JsonPrimitive(true));val gated=j["gated"] as JsonPrimitive;require(!gated.isString && gated.booleanOrNull!=null);HostKanbanTaskAck(null,true,gated.boolean)}
            HostKanbanTaskAction.Unlink->{val ok=j["ok"] as JsonPrimitive;require(!ok.isString && ok.booleanOrNull!=null);HostKanbanTaskAck(null,ok.boolean)}
        }
    } catch(_:Exception) {throw HostKanbanUnsupported()}
    fun acknowledgedSummary(ack:HostKanbanTaskAck)=JsonObject(summary()+buildJsonObject {
        put("host_acknowledged",true);put("effect_reported",ack.effectReported);put("created_identity_verified",false);put("worker_stop_verified",false);put("file_erasure_verified",false);put("runtime_adoption_verified",false);put("path_mapping_verified",false);put("comment_identity_verified",false)
        ack.task?.let {put("reported_task_sha256",taskHash(it.id))};ack.gated?.let {put("child_gated_reported",it)};put("warning_reported",ack.warning!=null)
    })
    fun observed(ack:HostKanbanTaskAck,detail:HostKanbanDetail?,after:HostKanbanSnapshot?):Boolean {
        if(!ack.effectReported || action==HostKanbanTaskAction.Comment) return false // No comment ID/revision in the ACK.
        if(action==HostKanbanTaskAction.Delete) return after!=null && after.owner==owner && after.profileHomeId==home && after.board.definition()==board.definition() && after.columns.flatMap {it.tasks}.none {it.id==id}
        val d=detail ?: return false
        if(d.owner!=owner || d.profileHomeId!=home || d.board.definition()!=board.definition()) return false
        return when(action) {
            HostKanbanTaskAction.Create->d.task==ack.task && d.parents.toSet()==parents.map {it.id}.toSet()
            HostKanbanTaskAction.Metadata,HostKanbanTaskAction.Priority,HostKanbanTaskAction.Assignee->d.task==ack.task
            HostKanbanTaskAction.Link->d.task.id==id && related!!.task.id in d.parents
            HostKanbanTaskAction.Unlink->d.task.id==id && related!!.task.id !in d.parents
            else->false
        }
    }
    companion object {
        private fun safe(v:String,max:Int,multiline:Boolean=false)=v.length<=max && v.none {it.isISOControl() && !(multiline && it in "\r\n\t")} && !v.codePoints().anyMatch {it in 0xD800..0xDFFF}
        private fun text(v:String,max:Int,multiline:Boolean=false,nonblank:Boolean=false):String {require(safe(v,max,multiline) && (!nonblank || v.isNotBlank()));return v}
        private fun base(inventory:HostKanbanInventory,snapshot:HostKanbanSnapshot) {
            GatewayCronInventory.requireProfile(inventory.owner.profile);require(inventory.profileHomeId.matches(Regex("[a-f0-9]{64}")))
            require(snapshot.owner==inventory.owner && snapshot.profileHomeId==inventory.profileHomeId && !snapshot.board.archived)
            require(inventory.boards.count {it.slug=="default" && !it.archived}==1 && inventory.boards.singleOrNull {it.slug==snapshot.board.slug}?.definition()==snapshot.board.definition())
        }
        private fun detail(snapshot:HostKanbanSnapshot,subject:HostKanbanDetail) {require(subject.owner==snapshot.owner && subject.profileHomeId==snapshot.profileHomeId && subject.board.definition()==snapshot.board.definition() && HostKanbanProtocol.id(subject.task.id))}
        private inline fun build(block:()->HostKanbanTaskChange)=try {block()} catch(_:Exception) {throw HostKanbanTaskInvalid()}
        private fun make(inventory:HostKanbanInventory,snapshot:HostKanbanSnapshot,action:HostKanbanTaskAction,subject:HostKanbanDetail?,values:JsonObject,related:HostKanbanDetail?=null,parents:List<HostKanbanTask> = emptyList(),project:HostProjectInfo?=null):HostKanbanTaskChange {
            base(inventory,snapshot);subject?.let {detail(snapshot,it)};related?.let {detail(snapshot,it)}
            return HostKanbanTaskChange(inventory,snapshot,action,subject,related,Collections.unmodifiableList(ArrayList(parents)),JsonObject(Collections.unmodifiableMap(LinkedHashMap(values))),project)
        }
        fun create(inventory:HostKanbanInventory,snapshot:HostKanbanSnapshot,draft:HostKanbanTaskDraft,projects:HostProjectSnapshot?=null)=build {
            base(inventory,snapshot);val title=text(draft.title.trim(),4096,true,true)
            val parentIds=draft.parents.toList();require(parentIds.size<=128 && parentIds.distinct().size==parentIds.size && parentIds.all(HostKanbanProtocol::id))
            val cards=snapshot.columns.flatMap {it.tasks};val parents=parentIds.map {id->cards.singleOrNull {it.id==id} ?: throw HostKanbanTaskInvalid()}
            val assignee=draft.assignee?.trim()?.lowercase(Locale.ROOT)?.takeIf {it.isNotEmpty()}?.also(GatewayCronInventory::requireProfile)
            val model=draft.model?.trim()?.takeIf {it.isNotEmpty()}?.also {text(it,512,nonblank=true)};val provider=draft.provider?.trim()?.takeIf {it.isNotEmpty()}?.also {text(it,256,nonblank=true)};require(provider==null || model!=null)
            val effort=draft.reasoning?.trim()?.lowercase(Locale.ROOT)?.takeIf {it.isNotEmpty()}?.also {require(it in HostMoaLimits.efforts)}
            require(draft.workspaceKind==null || draft.workspaceKind in setOf("scratch","dir","worktree"));draft.maxRuntimeSeconds?.let {require(it>0)};draft.goalMaxTurns?.let {require(it>0)}
            val skills=draft.skills?.map {text(it.trim(),512,nonblank=true).also {v->require(',' !in v)}}?.distinct()?.also {require(it.size<=256)}
            val projectId=draft.projectId?.also {require(it.isEmpty() || HostProjectProtocol.validId(it))} ?: snapshot.board.projectId.takeUnless {draft.workspaceKind=="scratch"}
            val project=projectId?.takeIf {it.isNotEmpty()}?.let {id->val p=projects ?: throw HostKanbanTaskInvalid();require(p.owner==inventory.owner && p.homeId==inventory.profileHomeId && !HostProjectChange.collision(p,id));p.projects.singleOrNull {it.id==id && !it.archived} ?: throw HostKanbanTaskInvalid()}
            val values=buildJsonObject {
                put("title",title);draft.body?.let {put("body",text(it,262144,true))};assignee?.let {put("assignee",it)};draft.tenant?.let {put("tenant",text(it,512))};put("priority",draft.priority)
                draft.workspaceKind?.let {put("workspace_kind",it)};draft.workspacePath?.let {put("workspace_path",text(it,4096,nonblank=true))}
                put("parents",JsonArray(parentIds.map(::JsonPrimitive)));put("triage",draft.triage);put("idempotency_key",UUID.randomUUID().toString())
                draft.maxRuntimeSeconds?.let {put("max_runtime_seconds",it)};skills?.let {put("skills",JsonArray(it.map(::JsonPrimitive)))};put("goal_mode",draft.goalMode);draft.goalMaxTurns?.let {put("goal_max_turns",it)}
                model?.let {put("model_override",it)};provider?.let {put("provider_override",it)};effort?.let {put("reasoning_effort",it)};draft.projectId?.let {put("project_id",it)}
            }
            make(inventory,snapshot,HostKanbanTaskAction.Create,null,values,parents=parents,project=project)
        }
        fun metadata(inventory:HostKanbanInventory,snapshot:HostKanbanSnapshot,subject:HostKanbanDetail,title:String?=null,body:String?=null)=build {
            require(title!=null || body!=null);val values=buildJsonObject {title?.let {put("title",text(it.trim(),4096,true,true))};body?.let {put("body",text(it,262144,true))}}
            make(inventory,snapshot,HostKanbanTaskAction.Metadata,subject,values)
        }
        fun priority(inventory:HostKanbanInventory,snapshot:HostKanbanSnapshot,subject:HostKanbanDetail,priority:Int)=build {make(inventory,snapshot,HostKanbanTaskAction.Priority,subject,buildJsonObject {put("priority",priority)})}
        fun assignee(inventory:HostKanbanInventory,snapshot:HostKanbanSnapshot,subject:HostKanbanDetail,profile:String)=build {
            val name=profile.trim().lowercase(Locale.ROOT);if(name.isNotEmpty()) GatewayCronInventory.requireProfile(name)
            make(inventory,snapshot,HostKanbanTaskAction.Assignee,subject,buildJsonObject {put("assignee",name)})
        }
        fun comment(inventory:HostKanbanInventory,snapshot:HostKanbanSnapshot,subject:HostKanbanDetail,body:String,author:String="dashboard")=build {make(inventory,snapshot,HostKanbanTaskAction.Comment,subject,buildJsonObject {put("body",text(body.trim(),262144,true,true));put("author",text(author.trim(),512,true,true))})}
        fun dependency(inventory:HostKanbanInventory,snapshot:HostKanbanSnapshot,child:HostKanbanDetail,parent:HostKanbanDetail,remove:Boolean)=build {
            require(parent.task.id!=child.task.id && (parent.task.id in child.parents)==remove)
            if(!remove) require(child.task.status!="running")
            make(inventory,snapshot,if(remove) HostKanbanTaskAction.Unlink else HostKanbanTaskAction.Link,child,buildJsonObject {put("parent_id",parent.task.id);put("child_id",child.task.id)},parent)
        }
        fun delete(inventory:HostKanbanInventory,snapshot:HostKanbanSnapshot,subject:HostKanbanDetail)=build {make(inventory,snapshot,HostKanbanTaskAction.Delete,subject,JsonObject(emptyMap()))}
        fun accessHome(summary:String)=HostKanbanBoardChange.accessHome(summary)
    }
}
