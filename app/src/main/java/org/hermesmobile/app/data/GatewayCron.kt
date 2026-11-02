package org.hermesmobile.app.data

import kotlinx.serialization.json.*

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
            val flag=value["enabled"];require(flag==null || flag==JsonNull || flag==JsonPrimitive(true) || flag==JsonPrimitive(false))
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
