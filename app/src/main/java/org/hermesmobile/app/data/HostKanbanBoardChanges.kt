package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.util.Collections
import java.util.Locale

enum class HostKanbanBoardAction(val method:String) {
    Create("POST"),Update("PATCH"),Archive("DELETE"),Delete("DELETE"),Switch("POST");
    val receiptAction get()="KanbanBoard"+name
    companion object {fun receipt(value:String)=entries.any {it.receiptAction==value}}
}
class HostKanbanBoardInvalid:Exception("Invalid board change.")
class HostKanbanBoardChanged:Exception("Reviewed board configuration changed.")
class HostKanbanBoardUncertain(val receipt:ManagementOperation?=null):Exception("Board outcome is unknown.")
class HostKanbanBoardReadbackPending(val receipt:ManagementOperation?=null):Exception("Board acknowledgement retained; readback pending.")
class HostKanbanBoardRefused(val receipt:ManagementOperation?=null):Exception("Board request was not attempted.")
private fun boardHash(value:String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") {"%02x".format(it)}

data class HostKanbanBoardDraft(val slug:String,val fields:Map<String,String>) {override fun toString()="HostKanbanBoardDraft(redacted)"}
data class HostKanbanBoardAck(val board:HostKanbanBoardDefinition?,val current:String?,val archivePath:String?,val effectReported:Boolean,val mappingMatchesReview:Boolean) {override fun toString()="HostKanbanBoardAck(redacted)"}

/** Private immutable exact-value request; durable summaries contain only hashes/field names/booleans. */
class HostKanbanBoardChange private constructor(val expected:HostKanbanInventory,val action:HostKanbanBoardAction,val slug:String,val values:JsonObject,val project:HostProjectInfo?) {
    val selected=expected.boards.singleOrNull {it.slug==slug}
    val anchor=expected.boards.single {it.slug=="default"}
    val receiptTarget="sha256:"+boardHash(slug)
    override fun toString()="HostKanbanBoardChange(redacted)"
    fun body()=values
    fun same(before:HostKanbanInventory)=before.owner==expected.owner && before.profileHomeId==expected.profileHomeId && before.current==expected.current && before.boards.map {it.definition()}.sortedBy {it.slug}==expected.boards.map {it.definition()}.sortedBy {it.slug}
    fun rootSame(after:HostKanbanInventory)=after.owner==expected.owner && after.profileHomeId==expected.profileHomeId && after.boards.singleOrNull {it.slug=="default"}?.dbPath==anchor.dbPath
    fun projectCurrent(snapshot:HostProjectSnapshot):Boolean=project==null || snapshot.owner==expected.owner && snapshot.homeId==expected.profileHomeId && snapshot.projects.singleOrNull {it.id==project.id}==project && !HostProjectChange.collision(snapshot,project.id)
    fun summary()=buildJsonObject {
        put("schema",1);put("access_home_sha256",expected.profileHomeId);put("board_sha256",boardHash(slug));put("anchor_db_sha256",boardHash(anchor.dbPath));put("requested_sha256",boardHash(values.toString()))
        put("fields",JsonArray(values.keys.sorted().map(::JsonPrimitive)));put("path_supplied",values.containsKey("default_workdir"));put("path_clear",values["default_workdir"]==JsonPrimitive(""))
        put("project_binding",project!=null);put("project_clear",values["project_id"]==JsonPrimitive(""));put("switch_requested",action==HostKanbanBoardAction.Switch)
        put("archived_entry",selected?.archived==true)
    }
    private fun current(j:JsonObject):String {
        val v=j["current"] as? JsonPrimitive ?: throw HostKanbanUnsupported();if(!v.isString || !HostKanbanProtocol.slug(v.content)) throw HostKanbanUnsupported();return v.content
    }
    private fun fallbackName()=slug.split(Regex("[-_]")).filter {it.isNotEmpty()}.joinToString(" ") {it.replaceFirstChar {c->c.uppercaseChar()}}.ifEmpty {slug}
    private fun requestedMetadata(board:HostKanbanBoardDefinition):Boolean {
        if(board.slug!=slug || action==HostKanbanBoardAction.Create && board.archived) return false
        for((key,value) in values) {
            if(key in setOf("slug","switch")) continue
            val wanted=value.jsonPrimitive.content
            val actual=when(key) {"name"->board.name;"description"->board.description;"icon"->board.icon;"color"->board.color;"project_id"->board.projectId;"default_workdir"->board.workdir;else->return false}
            when(key) {
                "name"->if(actual!=wanted.trim().ifEmpty {fallbackName()}) return false
                "project_id"->if(actual!=wanted.takeIf {it.isNotEmpty()}) return false
                "default_workdir"->if(wanted.isEmpty()) {
                    val inherited=if(action==HostKanbanBoardAction.Create) project?.primaryPath?.takeIf {it.isNotEmpty()} else null
                    if(actual!=inherited) return false
                } else if(actual.isNullOrBlank()) return false // Host normalization remains unverified on Android.
                else->if(actual!=wanted) return false
            }
        }
        if(action==HostKanbanBoardAction.Update && !values.containsKey("default_workdir")) {
            val inherited=if(project!=null) project.primaryPath?.takeIf {it.isNotEmpty()} ?: selected!!.workdir else selected!!.workdir
            if(board.workdir!=inherited) return false
        }
        return true
    }
    fun acknowledge(j:JsonObject):HostKanbanBoardAck=try {
        when(action) {
            HostKanbanBoardAction.Create,HostKanbanBoardAction.Update->{
                val b=HostKanbanProtocol.metadata(j["board"] as JsonObject);require(b.slug==slug)
                val c=if(action==HostKanbanBoardAction.Create) current(j) else null
                HostKanbanBoardAck(b,c,null,requestedMetadata(b),if(action==HostKanbanBoardAction.Create) true else b.dbPath==selected!!.dbPath)
            }
            HostKanbanBoardAction.Switch->{val c=current(j);require(c==slug);HostKanbanBoardAck(null,c,null,true,true)}
            HostKanbanBoardAction.Archive,HostKanbanBoardAction.Delete->{
                val result=j["result"] as JsonObject
                fun field(key:String,max:Int):String {val v=result[key] as JsonPrimitive;require(v.isString && v.content.length<=max && v.content.none(Char::isISOControl));return v.content}
                require(field("slug",64)==slug && field("action",32)==if(action==HostKanbanBoardAction.Archive) "archived" else "deleted")
                val path=field("new_path",8192);if(action==HostKanbanBoardAction.Archive) require(path.isNotBlank()) else require(path.isEmpty())
                HostKanbanBoardAck(null,current(j),path.takeIf {it.isNotEmpty()},true,true)
            }
        }
    } catch(_:Exception) {throw HostKanbanUnsupported()}
    fun acknowledgedSummary(ack:HostKanbanBoardAck)=JsonObject(summary()+buildJsonObject {
        put("host_acknowledged",true);put("effect_reported",ack.effectReported);put("mapping_matches_review",ack.mappingMatchesReview)
        put("new_board_verified",false);put("file_erasure_verified",false);put("runtime_stop_verified",false);put("path_mapping_verified",false);put("archive_path_reported",ack.archivePath!=null)
        ack.current?.let {put("reported_current_sha256",boardHash(it))}
    })
    fun observed(ack:HostKanbanBoardAck,after:HostKanbanInventory):Boolean {
        if(!rootSame(after) || !ack.effectReported || !ack.mappingMatchesReview) return false
        val row=after.boards.singleOrNull {it.slug==slug}
        return when(action) {
            HostKanbanBoardAction.Create,HostKanbanBoardAction.Update->row?.definition()==ack.board && (ack.current==null || after.current==ack.current)
            HostKanbanBoardAction.Archive->row!=null && row.archived && row.dbPath==selected!!.dbPath && after.current==ack.current
            HostKanbanBoardAction.Delete->row==null && after.current==ack.current
            HostKanbanBoardAction.Switch->row!=null && !row.archived && row.dbPath==selected!!.dbPath && after.current==slug
        }
    }
    companion object {
        private val fieldLimits=mapOf("name" to 1024,"description" to 16384,"icon" to 512,"color" to 128,"default_workdir" to 4096,"project_id" to 512)
        private fun safe(value:String,max:Int,multiline:Boolean)=value.length<=max && value.none {it.isISOControl() && !(multiline && it in "\n\r\t")} && !value.codePoints().anyMatch {it in 0xD800..0xDFFF}
        private fun check(expected:HostKanbanInventory) {GatewayCronInventory.requireProfile(expected.owner.profile);require(expected.profileHomeId.matches(Regex("[a-f0-9]{64}")));require(expected.boards.count {it.slug=="default" && !it.archived}==1)}
        private fun make(expected:HostKanbanInventory,action:HostKanbanBoardAction,slug:String,fields:Map<String,String>,projects:HostProjectSnapshot?):HostKanbanBoardChange {
            check(expected);require(HostKanbanProtocol.slug(slug));require(fields.keys.all {it in fieldLimits})
            for((key,value) in fields) require(safe(value,fieldLimits.getValue(key),key in setOf("name","description")))
            val selected=expected.boards.singleOrNull {it.slug==slug}
            if(action==HostKanbanBoardAction.Create) require(selected==null) else require(selected!=null)
            if(action in setOf(HostKanbanBoardAction.Archive,HostKanbanBoardAction.Delete)) require(slug!="default")
            if(action in setOf(HostKanbanBoardAction.Archive,HostKanbanBoardAction.Switch)) require(selected?.archived==false)
            if(action==HostKanbanBoardAction.Update) require(fields.isNotEmpty())
            var project:HostProjectInfo?=null
            fields["project_id"]?.takeIf {it.isNotEmpty()}?.let {id->
                val available=projects ?: throw HostKanbanBoardInvalid()
                require(available.owner==expected.owner && available.homeId==expected.profileHomeId && HostProjectProtocol.validId(id))
                project=available.projects.singleOrNull {it.id==id} ?: throw HostKanbanBoardInvalid()
                require(project?.archived==false && !HostProjectChange.collision(available,id))
            }
            val values=buildJsonObject {
                if(action==HostKanbanBoardAction.Create) {put("slug",slug);put("switch",false)}
                for((key,value) in fields.toSortedMap()) put(key,value)
            }
            return HostKanbanBoardChange(expected,action,slug,JsonObject(Collections.unmodifiableMap(LinkedHashMap(values))),project)
        }
        private inline fun build(block:()->HostKanbanBoardChange)=try {block()} catch(_:Exception) {throw HostKanbanBoardInvalid()}
        fun create(expected:HostKanbanInventory,draft:HostKanbanBoardDraft,projects:HostProjectSnapshot?=null)=build {
            val slug=draft.slug.trim().lowercase(Locale.ROOT)
            // Explicit blank display fields and no automatic activation; a raced existing slug can still be reused upstream.
            make(expected,HostKanbanBoardAction.Create,slug,mapOf("name" to "","description" to "","icon" to "","color" to "","default_workdir" to "","project_id" to "")+draft.fields,projects)
        }
        fun update(expected:HostKanbanInventory,slug:String,fields:Map<String,String>,projects:HostProjectSnapshot?=null)=build {make(expected,HostKanbanBoardAction.Update,slug,fields,projects)}
        fun action(expected:HostKanbanInventory,slug:String,action:HostKanbanBoardAction)=build {require(action in setOf(HostKanbanBoardAction.Archive,HostKanbanBoardAction.Delete,HostKanbanBoardAction.Switch));make(expected,action,slug,emptyMap(),null)}
        fun accessHome(summary:String):String {
            val j=Json.parseToJsonElement(summary).jsonObject;require(j["schema"]==JsonPrimitive(1))
            val v=j["access_home_sha256"] as JsonPrimitive;require(v.isString && v.content.matches(Regex("[a-f0-9]{64}")));return v.content
        }
    }
}
