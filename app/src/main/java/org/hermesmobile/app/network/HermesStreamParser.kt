package org.hermesmobile.app.network

import kotlinx.serialization.json.*

data class SseEvent(val name: String, val data: String, val id: String = "")
/** Incremental SSE framing. Okio's UTF-8 line reader handles arbitrary byte/chunk boundaries. */
class HermesStreamParser {
    private var event = "message"
    private var id = ""
    private val data = mutableListOf<String>()
    private var size = 0
    fun line(line: String): SseEvent? {
        if (line.isEmpty()) {
            val result = if (data.isEmpty()) null else SseEvent(event, data.joinToString("\n"), id)
            event = "message"; data.clear(); size = 0
            return result
        }
        if (line.startsWith(':')) return null
        val field = line.substringBefore(':')
        val value = if (':' in line) line.substringAfter(':').removePrefix(" ") else ""
        when (field) {
            "event" -> event = value
            "id" -> if ('\u0000' !in value) id = value
            "data" -> { size += value.length; require(size <= 2_000_000) { "Stream event is too large" }; data += value }
        }
        return null
    }
}

data class StreamUpdate(
    val delta: String = "", val finalText: String? = null, val status: String? = null,
    val activity: String? = null, val runId: String = "", val sessionId: String = "",
    val approval: org.hermesmobile.app.data.Approval? = null
)
object StreamDecoder {
    private val json = Json { ignoreUnknownKeys = true }
    fun decode(event: SseEvent): StreamUpdate {
        if (event.data == "[DONE]") return StreamUpdate(status = "response_finished")
        val j = json.parseToJsonElement(event.data).jsonObject
        fun text(key: String) = (j[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val name = if (event.name == "message") text("type").ifBlank { text("event") }.ifBlank { event.name } else event.name
        val run = text("run_id")
        val session = text("session_id")
        return when (name) {
            "assistant.delta", "message.delta", "response.output_text.delta" -> StreamUpdate(delta = text("delta"), runId = run, sessionId = session)
            "assistant.completed" -> StreamUpdate(finalText = text("content"), runId = run, sessionId = session)
            "run.completed" -> StreamUpdate(finalText = text("output").takeIf { it.isNotEmpty() }, status = "completed", runId = run, sessionId = session)
            "run.failed", "run.cancelled", "run.interrupted" -> StreamUpdate(status = name.removePrefix("run."), runId = run, sessionId = session)
            "run.started", "run.queued" -> StreamUpdate(status = "running", runId = run, sessionId = session)
            "approval.request" -> StreamUpdate(status = "waiting_for_approval", runId = run,
                approval = org.hermesmobile.app.data.Approval(run, text("request_id"), text("description").ifBlank { text("command") },
                    (j["choices"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()))
            "approval.responded" -> StreamUpdate(status = "running", runId = run)
            "tool.started", "tool.completed", "tool.failed" -> StreamUpdate(activity = name + ": " + text("tool_name").ifBlank { text("tool") }, runId = run)
            "assistant.commentary", "message.interim" -> StreamUpdate(activity = text("text"), runId = run)
            "error", "run.error" -> StreamUpdate(status = "failed", runId = run)
            else -> {
                val choice = (j["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
                val d = choice?.get("delta") as? JsonObject
                if (choice != null) StreamUpdate(delta = (d?.get("content") as? JsonPrimitive)?.contentOrNull.orEmpty(),
                    status = if ((choice["finish_reason"] as? JsonPrimitive)?.contentOrNull != null) "response_finished" else null)
                else StreamUpdate(runId = run, sessionId = session)
            }
        }
    }
}
