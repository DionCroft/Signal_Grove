package org.hermesmobile.app.data

import kotlinx.serialization.json.*

class ManagedCronJob private constructor(private val row:JsonObject) {
    val id=row.text("id")
    val name=row.text("name").ifBlank {id}
    val enabled=row["enabled"]?.let {if(it is JsonNull) null else it.jsonPrimitive.booleanOrNull ?: error("Invalid schedule state.")}
    val state=row.text("state").take(64)
    val schedule=row.text("schedule_display").ifBlank {(row["schedule"] as? JsonObject)?.text("display").orEmpty()}.take(2048)
    val nextRun=row.text("next_run_at").take(128)
    val lastRun=row.text("last_run_at").take(128)
    val createdAt=row.text("created_at").take(128)
    val noToolsAllowed=(row["enabled_toolsets"] as? JsonArray)?.isEmpty()==true
    val lastStatus=row.text("last_status").take(64)
    val hasRunError=row.text("last_error").isNotBlank() || row.text("last_delivery_error").isNotBlank() || row["last_failure"] is JsonObject
    val addressable get()=id!="unknown" && id.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.:-]{0,255}"))
    fun fields():Map<String,String> = buildMap {
        for(key in listOf("prompt","model","provider","script","workdir","timezone","paused_reason","deliver")) row.text(key).takeIf {it.isNotBlank()}?.let {put(key,it.take(8192))}
        for(key in listOf("no_agent","catch_up")) (row[key] as? JsonPrimitive)?.booleanOrNull?.let {put(key,it.toString())}
        (row["grace_minutes"] as? JsonPrimitive)?.intOrNull?.takeIf {it>=0}?.let {put("grace_minutes",it.toString())}
        for(key in listOf("skills","context_from","enabled_toolsets")) (row[key] as? JsonArray)?.let {values->put(key,values.take(64).mapNotNull {(it as? JsonPrimitive)?.contentOrNull?.take(256)}.joinToString("\n"))}
        (row["repeat"] as? JsonObject)?.let {repeat ->
            if(repeat["times"] is JsonNull) put("repeat","__unlimited__")
            else (repeat["times"] as? JsonPrimitive)?.intOrNull?.takeIf {it>0}?.let {put("repeat",it.toString())}
            (repeat["completed"] as? JsonPrimitive)?.intOrNull?.takeIf {it>=0}?.let {put("repeat_completed",it.toString())}
        }
    }
    override fun toString()="ManagedCronJob($id)"
    companion object {
        fun parse(value:JsonObject,profile:String):ManagedCronJob {
            require(profile.isNotBlank() && !profile.trim().equals("all",ignoreCase=true))
            val text=value.toString();require(text.length<=65536)
            val copy=Json.parseToJsonElement(text).jsonObject
            require(copy.text("profile")==profile) {"Schedule profile is missing or mismatched."}
            if(copy.containsKey("profile_name")) require(copy.text("profile_name")==profile) {"Schedule profile mismatch."}
            return ManagedCronJob(copy).also {job->require(job.id.isNotBlank() && job.id.length<=256 && job.id.none(Char::isISOControl));require(job.name.length<=512 && job.name.none(Char::isISOControl))}
        }
        fun inventory(value:JsonArray,profile:String):List<ManagedCronJob> {
            require(value.size<=500)
            return value.map {parse(it.jsonObject,profile)}.also {rows->require(rows.map {it.id}.distinct().size==rows.size)}
        }
    }
}
