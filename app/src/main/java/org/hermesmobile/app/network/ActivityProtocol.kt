package org.hermesmobile.app.network

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*

object ActivityProtocol {
    fun duration(value:JsonElement?):Long?=(value as? JsonPrimitive)?.takeUnless {it.isString}?.doubleOrNull?.takeIf {it>=0 && it.isFinite() && it<=31_536_000}?.times(1000)?.toLong()
    private fun text(j: JsonObject, key: String): String =
        (j[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?.takeIf { it.length <= 256 && it.none(Char::isISOControl) }.orEmpty()
    private fun JsonObjectBuilder.putEventTime(event: JsonObject) {
        // Runs stamps the event at emission using Unix seconds.
        val seconds = (event["timestamp"] as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull ?: return
        val milliseconds = seconds * 1_000
        if (milliseconds >= 0 && milliseconds < Long.MAX_VALUE.toDouble()) {
            put(ACTIVITY_SERVER_EVENT_AT, milliseconds.toLong())
        }
    }
    // Hermes may put tool arguments or serialized results in its preview.
    fun tool(j:JsonObject,kind:String)=ToolUpdate(text(j,"tool_name").ifBlank {text(j,"tool")},kind,"",duration(j["duration"]),
        callId=text(j,"tool_call_id"),details=buildJsonObject {
            put("error", kind == "failed")
            putEventTime(j)
        },generation=(j["execution_generation"] as? JsonPrimitive)?.longOrNull)
    internal fun mcpOrigin(event: JsonObject): ToolUpdate? =
        identityMetadata(event, MCP_ORIGIN, MCP_SERVER)

    internal fun skillLoaded(event: JsonObject): ToolUpdate? =
        identityMetadata(event, SKILL_LOADED, SKILL_NAME)

    private fun identityMetadata(event: JsonObject, category: String, field: String): ToolUpdate? {
        activityIdentifier(event["run_id"]) ?: return null
        val tool = activityIdentifier(event["tool"]) ?: return null
        val call = activityIdentifier(event["tool_call_id"]) ?: return null
        val identity = activityIdentifier(event[field]) ?: return null
        if (category == SKILL_LOADED && (tool != "skill_view" || !isSkillIdentity(identity))) return null
        val generation = if ("execution_generation" in event) {
            (event["execution_generation"] as? JsonPrimitive)?.takeUnless { it.isString }
                ?.longOrNull?.takeIf { it >= 0 } ?: return null
        } else null
        val details = buildJsonObject {
            put(field, identity)
            putEventTime(event)
        }
        return ToolUpdate(tool, category, "", null, call, category, details, generation)
    }

    fun subagent(j:JsonObject,complete:Boolean):ToolUpdate {
        val outcome = if (complete) reportedSubagentOutcome(text(j, "status")) else "started"
        val details=buildJsonObject {
            listOf("subagent_id","child_session_id","delegation_id","parent_id","model").forEach {key -> text(j,key).takeIf {it.isNotBlank()}?.let {put(key,it)}}
            if (outcome != "started" && outcome != "unknown") put("status", outcome)
            putEventTime(j)
            listOf("task_count","task_index","depth","tool_count","input_tokens","output_tokens","reasoning_tokens","api_calls").forEach {key -> (j[key] as? JsonPrimitive)?.takeUnless {it.isString}?.longOrNull?.takeIf {it>=0}?.let {put(key,it)}}
            listOf("cost_usd","duration_seconds").forEach {key -> (j[key] as? JsonPrimitive)?.takeUnless {it.isString}?.doubleOrNull?.takeIf {it>=0 && it.isFinite()}?.let {put(key,it)}}
        }
        val identity=details.text("subagent_id").ifBlank {details.text("child_session_id")}.ifBlank {details.text("delegation_id").takeIf {it.isNotBlank() && details.containsKey("task_index")}?.plus(":"+details.text("task_index")).orEmpty()}
        return ToolUpdate("",outcome,
            "",duration(j["duration_seconds"]),identity,"subagent",details,(j["execution_generation"] as? JsonPrimitive)?.longOrNull)
    }
}
