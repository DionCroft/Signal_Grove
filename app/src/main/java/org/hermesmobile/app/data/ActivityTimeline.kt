package org.hermesmobile.app.data

import kotlinx.serialization.json.*

data class ActivityEntry(val key:String,val events:List<ToolEvent>) {
    private val ordered=events.sortedWith(compareBy<ToolEvent> {it.sequence ?: Long.MIN_VALUE}.thenBy {it.receivedAt})
    val first get()=ordered.first()
    val latest get()=ordered.lastOrNull {it.kind in setOf("completed","failed","cancelled")} ?: ordered.last()
    val details:JsonObject get()=JsonObject(ordered.flatMap {runCatching {Json.parseToJsonElement(it.detailsJson).jsonObject.entries.toList()}.getOrDefault(emptyList())}.associate {it.key to it.value})
}
fun activityTimeline(events:List<ToolEvent>):List<ActivityEntry> = events.groupBy {e ->
    if(e.callId.isBlank()) "legacy:${e.key}" else "${e.sessionKey}|${e.runId}|${e.generation}|${e.category}|${e.callId}"
}.map {(key,rows) -> ActivityEntry(key,rows)}.sortedByDescending {it.events.maxOf {e -> e.receivedAt}}

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

internal fun activeActivity(conversation: Conversation, timeline: List<ActivityEntry>): ToolEvent? =
    timeline.asSequence().map { it.latest }.filter {
        conversation.runId.isNotBlank() && it.sessionKey == conversation.key &&
            it.runId == conversation.runId && it.kind == "started" &&
            (conversation.executionGeneration == null || it.generation == conversation.executionGeneration)
    }.maxByOrNull { it.receivedAt }
