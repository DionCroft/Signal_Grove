package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class GatewayScopeUnproven:Exception()

class GatewayCronJob private constructor(private val row:JsonObject) {
    val id=row.text("job_id")
    val name=row.text("name").ifBlank {id}
    val schedule=row.text("schedule")
    val state=row.text("state")
    val enabled=(row["enabled"] as? JsonPrimitive)?.booleanOrNull
    val nextRun=row.text("next_run_at")
    val lastStatus=row.text("last_status")
    val promptPreview=row.text("prompt_preview")
    val addressable get()=id!="unknown" && id.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.:-]{0,255}"))
    fun sameReview(other:GatewayCronJob)=configuration()==other.configuration()
    private fun configuration()=JsonObject(row.filterKeys {it in setOf("job_id","name","schedule","state","enabled","prompt_preview","skill","skills","model","provider","pinned","base_url","repeat","deliver","script","reasoning_effort","monitor_script","monitor_url","no_agent","enabled_toolsets","workdir","interpreter","continuity","context_from","attach_to_session","paused_reason")})
    fun fields():Map<String,String> = buildMap {
        for(key in listOf("model","provider","deliver","script","reasoning_effort","monitor_script","workdir","interpreter","paused_reason")) row.text(key).takeIf {it.isNotBlank()}?.let {put(key,it)}
        for(key in listOf("pinned","no_agent","continuity","attach_to_session")) (row[key] as? JsonPrimitive)?.booleanOrNull?.let {put(key,it.toString())}
        for(key in listOf("skills","context_from","enabled_toolsets")) (row[key] as? JsonArray)?.let {put(key,it.map {value->value.jsonPrimitive.content}.joinToString("\n"))}
        (row["repeat"] as? JsonPrimitive)?.takeUnless {it is JsonNull}?.let {put("repeat",it.content)}
    }
    val reportsToolsets get()=row["enabled_toolsets"] is JsonArray
    fun reviewUrl(key:String,showQuery:Boolean=false):String? {
        require(key in setOf("base_url","monitor_url"))
        val value=row.text(key);if(value.isBlank()) return null
        return value.toHttpUrlOrNull()?.newBuilder()?.username("")?.password("")?.fragment(null)?.apply {if(!showQuery) query(null)}?.build()?.toString().orEmpty()
    }
    override fun toString()="GatewayCronJob($id)"
    companion object {
        fun parse(value:JsonObject):GatewayCronJob {
            require(value.toString().length<=65536)
            fun string(key:String,max:Int,required:Boolean=false) {
                val v=value[key];require(v==null || v==JsonNull || v is JsonPrimitive && v.isString)
                val s=(v as? JsonPrimitive)?.contentOrNull.orEmpty();require(s.length<=max && (!required || s.isNotBlank()))
                if(key in setOf("job_id","name")) require(s.none(Char::isISOControl))
            }
            string("job_id",256,true);string("name",512);string("schedule",2048);string("state",64);string("prompt_preview",8192);string("next_run_at",128);string("last_status",64)
            for(key in listOf("model","provider","base_url","deliver","script","reasoning_effort","monitor_script","monitor_url","workdir","interpreter","paused_reason")) string(key,8192)
            for(key in listOf("enabled","pinned","no_agent","continuity","attach_to_session")) {val flag=value[key];require(flag==null || flag==JsonNull || flag==JsonPrimitive(true) || flag==JsonPrimitive(false))}
            for(key in listOf("skills","context_from","enabled_toolsets")) value[key]?.takeUnless {it is JsonNull}?.let {v->require(v is JsonArray && v.size<=64);v.forEach {item->require(item is JsonPrimitive && item.isString && item.content.isNotBlank() && item.content.length<=256 && item.content.none(Char::isISOControl))}}
            value["repeat"]?.takeUnless {it is JsonNull}?.let {require(it is JsonPrimitive && it.content.length<=256 && (it.isString || it.intOrNull!=null))}
            return GatewayCronJob(Json.parseToJsonElement(value.toString()).jsonObject)
        }
    }
}
data class GatewayCronInventory(val profile:String,val jobs:List<GatewayCronJob>,val gatewayRunning:Boolean?) {
    companion object {
        fun requireProfile(value:String) {SkillProtocol.profile(value);require(value.lowercase() !in setOf("hermes",".hermes"))}
        fun supportedProfile(value:String)=runCatching {requireProfile(value)}.isSuccess
        fun parse(value:JsonObject,profile:String):GatewayCronInventory=runCatching {
            requireProfile(profile)
            require(value["success"]==JsonPrimitive(true) && value["scoped"]==JsonPrimitive(profile)) {"Gateway profile scope not proven."}
            val rows=value["jobs"] as? JsonArray ?: error("Missing gateway jobs.");require(rows.size<=500)
            val count=value["count"] as? JsonPrimitive;require(count!=null && !count.isString && count.intOrNull==rows.size)
            val jobs=rows.map {GatewayCronJob.parse(it.jsonObject)};require(jobs.map {it.id}.distinct().size==jobs.size)
            val running=value["gateway_running"];require(running==null || running==JsonNull || running==JsonPrimitive(true) || running==JsonPrimitive(false))
            GatewayCronInventory(profile,java.util.Collections.unmodifiableList(jobs),(running as? JsonPrimitive)?.booleanOrNull)
        }.getOrElse {throw GatewayScopeUnproven()}
    }
}


enum class GatewayCronAction(val wire:String) { Pause("pause"), Resume("resume"), Delete("remove") }
class GatewayCronRequest(val expected:GatewayCronJob,val action:GatewayCronAction) {
    init {require(expected.addressable);require(when(action) {GatewayCronAction.Pause->expected.enabled==true;GatewayCronAction.Resume->expected.enabled==false && expected.state=="paused";GatewayCronAction.Delete->true})}
    val receiptAction get()="GatewayCron"+action.name
    val enabled:Boolean? get()=when(action) {GatewayCronAction.Pause->false;GatewayCronAction.Resume->true;GatewayCronAction.Delete->null}
    fun params(profile:String)=buildJsonObject {GatewayCronInventory.requireProfile(profile);put("action",action.wire);put("name",expected.id);put("profile",profile)}
    fun summary()=buildJsonObject {put("action",action.wire);put("previous_enabled",expected.enabled?.let(::JsonPrimitive) ?: JsonNull)}.toString()
    fun acknowledge(value:JsonObject):String {
        require(value["success"]==JsonPrimitive(true))
        if(action==GatewayCronAction.Delete) {val removed=value["removed_job"]!!.jsonObject;require(removed["id"]==JsonPrimitive(expected.id) && removed["name"]==JsonPrimitive(expected.name))}
        else {val job=GatewayCronJob.parse(value["job"]!!.jsonObject);require(job.id==expected.id && job.name==expected.name && job.enabled==enabled);if(action==GatewayCronAction.Pause) require(job.state=="paused")}
        return buildJsonObject {put("accepted",true);put("action",action.wire);put("job_id",expected.id);enabled?.let {put("enabled",it)}}.toString()
    }
    fun observed(inventory:GatewayCronInventory):Boolean {
        val current=inventory.jobs.singleOrNull {it.id==expected.id}
        return if(action==GatewayCronAction.Delete) current==null else current!=null && current.name==expected.name && current.enabled==enabled && (action!=GatewayCronAction.Pause || current.state=="paused")
    }
    override fun toString()="GatewayCronRequest(${action.name},${expected.id})"
    companion object {fun collision(job:GatewayCronJob,rows:List<GatewayCronJob>)=rows.any {it.id!=job.id && it.name.equals(job.id,ignoreCase=true)}}
}
