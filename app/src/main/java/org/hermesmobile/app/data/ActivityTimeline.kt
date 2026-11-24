package org.hermesmobile.app.data

import kotlinx.serialization.json.*

internal const val ACTIVITY_SERVER_EVENT_AT = "server_event_ms"

private fun ToolEvent.activityDetails(): JsonObject =
    runCatching { Json.parseToJsonElement(detailsJson).jsonObject }.getOrElse { JsonObject(emptyMap()) }

internal val ToolEvent.serverEventAt: Long?
    get() = (activityDetails()[ACTIVITY_SERVER_EVENT_AT] as? JsonPrimitive)
        ?.takeUnless { it.isString }?.longOrNull?.takeIf { it >= 0 }

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
