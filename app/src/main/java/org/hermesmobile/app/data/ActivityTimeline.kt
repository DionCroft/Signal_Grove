package org.hermesmobile.app.data

import kotlinx.serialization.json.*

internal const val ACTIVITY_SERVER_EVENT_AT = "server_event_ms"
internal const val MCP_ORIGIN = "mcp_origin"
internal const val MCP_SERVER = "mcp_server"

internal fun activityIdentifier(value: JsonElement?): String? =
    (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { text ->
        text.isNotBlank() && text.length <= 256 &&
            text.none { it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() }
    }

private fun ToolEvent.activityDetails(): JsonObject =
    runCatching { Json.parseToJsonElement(detailsJson).jsonObject }.getOrElse { JsonObject(emptyMap()) }

internal val ToolEvent.serverEventAt: Long?
    get() = (activityDetails()[ACTIVITY_SERVER_EVENT_AT] as? JsonPrimitive)
        ?.takeUnless { it.isString }?.longOrNull?.takeIf { it >= 0 }

internal val ToolEvent.mcpServer: String?
    get() = if (category == MCP_ORIGIN && kind == MCP_ORIGIN && callId.isNotBlank())
        activityIdentifier(activityDetails()[MCP_SERVER]) else null

internal fun reportedSubagentOutcome(status: String): String = when (status) {
    "completed", "cancelled", "interrupted", "incomplete", "timeout", "stalled" -> status
    "failed", "error" -> "failed"
    else -> "unknown"
}

private val terminalActivityKinds = setOf(
    "completed", "failed", "cancelled", "interrupted", "incomplete", "timeout", "stalled", "unknown",
)

data class ActivityEntry(val key:String,val events:List<ToolEvent>) {
    private val ordered=events.sortedWith(compareBy<ToolEvent> {it.sequence ?: Long.MIN_VALUE}.thenBy {it.receivedAt})
    val first get()=ordered.first()
    val startReceipt get()=events.asSequence().filter { it.kind == "started" }.minByOrNull { it.receivedAt }
    val latest: ToolEvent
        get() {
            val event = ordered.lastOrNull { it.kind in terminalActivityKinds } ?: ordered.last()
            if (event.category != "subagent" || event.kind != "completed") return event
            // Earlier clients recorded any unrecognized child outcome as completed.
            return event.copy(kind = reportedSubagentOutcome(event.activityDetails().text("status")))
        }
    val details: JsonObject
        get() = JsonObject(ordered.flatMap { it.activityDetails().entries }.associate { it.key to it.value })
}
fun activityTimeline(events: List<ToolEvent>): List<ActivityEntry> = events.groupBy { event ->
    buildJsonArray {
        if (event.callId.isBlank()) {
            add(event.key)
        } else {
            add(event.sessionKey)
            add(event.runId)
            add(event.generation?.let { JsonPrimitive(it) } ?: JsonNull)
            add(event.category)
            add(event.callId)
            event.mcpServer?.let { add(it) }
        }
    }.toString()
}.map { (key, rows) -> ActivityEntry(key, rows) }
    .sortedByDescending { entry -> entry.events.maxOf { it.receivedAt } }

internal fun activityReport(conversation: Conversation, reports: List<RunReport>): RunReport? =
    reports.firstOrNull { report ->
        report.sessionKey == conversation.key && report.transport == conversation.transport &&
            report.runId == conversation.runId &&
            (conversation.pendingKey.isBlank() || report.pendingKey == conversation.pendingKey) &&
            (conversation.runId.isNotBlank() || conversation.pendingKey.isNotBlank())
    }

internal class ReportedRunProgress private constructor(
    private val submission: Conversation,
    private val admissionId: String,
    private val generation: Long,
    val tool: String?,
) {
    fun matches(conversation: Conversation): Boolean =
        conversation.status == "running" && !SessionOrigin.readOnly(conversation) &&
            submission.sameSubmission(conversation) && submission.remoteId == conversation.remoteId &&
            (conversation.admissionId.isBlank() || conversation.admissionId == admissionId) &&
            (conversation.executionGeneration == null || conversation.executionGeneration == generation)

    fun follows(previous: ReportedRunProgress?): Boolean =
        previous == null || (admissionId == previous.admissionId && generation >= previous.generation)

    fun withoutTool() = if (tool == null) this else ReportedRunProgress(submission, admissionId, generation, null)

    override fun toString() = "ReportedRunProgress(redacted)"

    companion object {
        fun from(conversation: Conversation, snapshot: JsonObject): ReportedRunProgress? {
            fun string(key: String) = (snapshot[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (conversation.transport != "runs" || conversation.runId.isBlank() || conversation.remoteId.isBlank() ||
                string("status") != "running" || string("run_id") != conversation.runId ||
                string("session_id") != conversation.remoteId) return null
            val id = string("admission_id")?.takeIf { it.isNotBlank() && it.length <= 256 && it.none(Char::isISOControl) }
                ?: return null
            val generation = (snapshot["execution_generation"] as? JsonPrimitive)
                ?.takeUnless { it.isString }?.longOrNull?.takeIf { it >= 0 } ?: return null
            val time = (snapshot["progress_at"] as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull
            if (time == null || !time.isFinite() || time < 0) return null
            val tool = if ("tool" in snapshot) {
                string("tool")?.takeIf { it.isNotBlank() && it.length <= 256 && it.none(Char::isISOControl) } ?: return null
            } else null
            return ReportedRunProgress(conversation, id, generation, tool).takeIf { it.matches(conversation) }
        }
    }
}

internal data class ActivityWait(val approval: Boolean, val state: String, val receivedAt: Long)

internal fun activityWaits(conversation: Conversation, controls: List<PendingControl>): List<ActivityWait> {
    if (conversation.runId.isBlank() || conversation.status !in RunStates.active ||
        SessionOrigin.readOnly(conversation)) return emptyList()
    return controls.asSequence()
        .filter { it.sessionKey == conversation.key && it.runId == conversation.runId && it.requestId.isNotBlank() }
        .filter { conversation.executionGeneration == null || it.generation == conversation.executionGeneration }
        .filter { it.kind == "approval" || it.kind == "clarify" }
        .filter { it.state == "pending" || it.state == "responding" || it.state == "unknown" }
        .map { ActivityWait(it.kind == "approval", it.state, it.receivedAt) }
        .toList()
}

internal fun activeActivity(conversation: Conversation, timeline: List<ActivityEntry>): ToolEvent? {
    if (conversation.runId.isBlank() || conversation.status != "running" ||
        SessionOrigin.readOnly(conversation)) return null

    return timeline.asSequence().map { it.latest }.filter { event ->
        event.sessionKey == conversation.key && event.runId == conversation.runId &&
            event.callId.isNotBlank() && event.kind == "started" &&
            (conversation.executionGeneration == null || event.generation == conversation.executionGeneration)
    }.maxByOrNull { it.receivedAt }
}
