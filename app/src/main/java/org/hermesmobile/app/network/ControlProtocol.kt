package org.hermesmobile.app.network

import org.hermesmobile.app.data.*
import kotlinx.serialization.json.*

data class ControlRequest(val runId: String,val requestId: String,val generation: Long?,val kind: String,
    val prompt: String,val choices: List<String>,val limited: Boolean = false) {
    fun stored(sessionKey: String) = PendingControl("$sessionKey|$runId|$requestId",sessionKey,runId,requestId,generation,kind,prompt,
        JsonArray(choices.map {JsonPrimitive(it)}).toString(),limited)
}
object ControlProtocol {
    fun prompt(j: JsonObject,run: String,kind: String,fallbackGeneration: Long? = null): ControlRequest {
        val original=buildList {
            listOf("description","command","question").forEach {key -> j.text(key).takeIf {it.isNotBlank()}?.let {add(it)}}
            j["edit"]?.takeUnless {it is JsonNull}?.let {add(it.toString())}
        }.distinct().joinToString("\n\n")
        // Make invisible formatting/control characters visible in consent text.
        val shown=buildString {original.forEach {c ->
            if((c.isISOControl() && c!='\n' && c!='\t') || Character.getType(c)==Character.FORMAT.toInt()) append("\\u%04X".format(c.code)) else append(c)
        }}
        return ControlRequest(run,j.text("prompt_id").ifBlank {j.text("request_id")},
            (j["execution_generation"] as? JsonPrimitive)?.longOrNull ?: fallbackGeneration,kind,shown.take(32000),
            (j["choices"] as? JsonArray)?.mapNotNull {(it as? JsonPrimitive)?.contentOrNull}?.take(100).orEmpty(),shown.length>32000)
    }
    fun snapshot(j: JsonObject,run: String): List<ControlRequest> {
        val generation=(j["execution_generation"] as? JsonPrimitive)?.longOrNull
        if(j.containsKey("pending_controls")) return j.items("pending_controls").filter {it.text("kind") in listOf("approval","clarify")}.map {prompt(it,run,it.text("kind"),generation)}
        val approval=j["approval"] as? JsonObject
        return if(j.text("status")=="waiting_for_approval" && approval!=null) listOf(prompt(approval,run,"approval",generation)) else emptyList()
    }
    fun matches(a: PendingControl,b: ControlRequest) = a.runId==b.runId && a.requestId.isNotBlank() && a.requestId==b.requestId && a.generation==b.generation && a.kind==b.kind && a.prompt==b.prompt && a.limited==b.limited
    fun responseBody(control: PendingControl,response: String): JsonObject {
        require(control.runId.isNotBlank() && control.requestId.isNotBlank()) {"The server did not provide an exact prompt identity. Refresh the run."}
        if(control.kind=="approval") {
            require(response in listOf("once","deny") && response in control.choices()) {"Unsupported approval decision."}
            require(!control.limited || response=="deny") {"The consent text was truncated; allowing is unavailable."}
        } else {
            require(control.kind=="clarify" && !control.limited && control.generation!=null && response.isNotBlank() && response.length<=16384) {"A current clarification identity and non-empty answer are required."}
        }
        return buildJsonObject {
            put("request_id",control.requestId)
            control.generation?.let {require(it>=0);put("execution_generation",it)}
            put(if(control.kind=="approval") "choice" else "answer",response)
        }
    }
}
