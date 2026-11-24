package org.hermesmobile.app.network

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*

object ActivityProtocol {
    fun duration(value:JsonElement?):Long?=(value as? JsonPrimitive)?.takeUnless {it.isString}?.doubleOrNull?.takeIf {it>=0 && it.isFinite() && it<=31_536_000}?.times(1000)?.toLong()
    private fun text(j: JsonObject, key: String): String =
        (j[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?.takeIf { it.length <= 256 && it.none(Char::isISOControl) }.orEmpty()
    // Hermes may put tool arguments or serialized results in its preview.
    fun tool(j:JsonObject,kind:String)=ToolUpdate(text(j,"tool_name").ifBlank {text(j,"tool")},kind,"",duration(j["duration"]),
        callId=text(j,"tool_call_id"),details=buildJsonObject {put("error",kind=="failed")},generation=(j["execution_generation"] as? JsonPrimitive)?.longOrNull)
    fun subagent(j:JsonObject,complete:Boolean):ToolUpdate {
        val details=buildJsonObject {
            listOf("subagent_id","child_session_id","delegation_id","parent_id","model","status").forEach {key -> text(j,key).takeIf {it.isNotBlank()}?.let {put(key,it)}}
            listOf("task_count","task_index","depth","tool_count","input_tokens","output_tokens","reasoning_tokens","api_calls").forEach {key -> (j[key] as? JsonPrimitive)?.takeUnless {it.isString}?.longOrNull?.takeIf {it>=0}?.let {put(key,it)}}
            listOf("cost_usd","duration_seconds").forEach {key -> (j[key] as? JsonPrimitive)?.takeUnless {it.isString}?.doubleOrNull?.takeIf {it>=0 && it.isFinite()}?.let {put(key,it)}}
        }
        val identity=details.text("subagent_id").ifBlank {details.text("child_session_id")}.ifBlank {details.text("delegation_id").takeIf {it.isNotBlank() && details.containsKey("task_index")}?.plus(":"+details.text("task_index")).orEmpty()}
        return ToolUpdate("",if(!complete) "started" else if(j.text("status") in setOf("failed","error")) "failed" else if(j.text("status")=="cancelled") "cancelled" else "completed",
            "",duration(j["duration_seconds"]),identity,"subagent",details,(j["execution_generation"] as? JsonPrimitive)?.longOrNull)
    }
}
