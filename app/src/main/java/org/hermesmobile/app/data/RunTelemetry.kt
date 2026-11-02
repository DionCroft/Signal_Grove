package org.hermesmobile.app.data

import kotlinx.serialization.json.*

/** Only public accounting/runtime metadata, never reasoning content or arbitrary payloads. */
object RunTelemetry {
    private val counts=setOf("input_tokens","output_tokens","total_tokens","cache_read_tokens","cache_write_tokens","reasoning_tokens")
    private fun count(j:JsonObject,key:String)=(j[key] as? JsonPrimitive)?.takeUnless {it.isString}?.longOrNull?.takeIf {it>=0}
    private fun id(j:JsonObject,key:String,max:Int=256)=(j[key] as? JsonPrimitive)?.takeIf {it.isString}?.content?.takeIf {it.isNotBlank() && it.length<=max && it.none(Char::isISOControl)}
    fun from(j:JsonObject):JsonObject=buildJsonObject {
        (j["usage"] as? JsonObject)?.let {raw ->
            val usage=buildJsonObject {
                counts.forEach {key -> count(raw,key)?.let {put(key,it)}}
                if(count(raw,"input_tokens")==null) count(raw,"prompt_tokens")?.let {put("input_tokens",it)}
                if(count(raw,"output_tokens")==null) count(raw,"completion_tokens")?.let {put("output_tokens",it)}
            }
            if(usage.isNotEmpty()) put("usage",usage)
        }
        (j["runtime"] as? JsonObject)?.let {raw ->
            val runtime=buildJsonObject {
                listOf("provider","model","route_source","model_lock").forEach {key -> id(raw,key)?.let {put(key,it)}}
                (raw["requested"] as? JsonObject)?.let {req ->
                    val requested=buildJsonObject {listOf("provider","model").forEach {key -> id(req,key)?.let {put(key,it)}}}
                    if(requested.isNotEmpty()) put("requested",requested)
                }
            }
            if(runtime.isNotEmpty()) put("runtime",runtime)
        }
    }
    fun parse(value:String)=runCatching {from(Json.parseToJsonElement(value).jsonObject)}.getOrDefault(buildJsonObject {})
    fun merge(old:String,next:JsonObject):String {
        val before=parse(old);val after=from(next)
        val merged=buildJsonObject {for(key in listOf("usage","runtime")) {
            val first=before[key] as? JsonObject ?: buildJsonObject {}
            val second=after[key] as? JsonObject ?: buildJsonObject {}
            val values=if(key=="runtime" && after.containsKey(key)) second else JsonObject(first+second)
            if(values.isNotEmpty()) put(key,values)
        }}
        return if(merged.isEmpty()) "" else merged.toString()
    }
}
