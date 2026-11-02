package org.hermesmobile.app.data

import kotlinx.serialization.json.*

data class ActivityEntry(val key:String,val events:List<ToolEvent>) {
    private val ordered=events.sortedWith(compareBy<ToolEvent> {it.sequence ?: Long.MIN_VALUE}.thenBy {it.receivedAt})
    val first get()=ordered.first()
    val latest get()=ordered.lastOrNull {it.kind in setOf("completed","failed","cancelled")} ?: ordered.last()
    val details:JsonObject get()=JsonObject(ordered.flatMap {runCatching {Json.parseToJsonElement(it.detailsJson).jsonObject.entries.toList()}.getOrDefault(emptyList())}.associate {it.key to it.value})
    val previews get()=ordered.map {it.preview}.filter {it.isNotBlank()}.distinct()
}
fun activityTimeline(events:List<ToolEvent>):List<ActivityEntry> = events.groupBy {e ->
    if(e.callId.isBlank()) "legacy:${e.key}" else "${e.sessionKey}|${e.runId}|${e.generation}|${e.category}|${e.callId}"
}.map {(key,rows) -> ActivityEntry(key,rows)}.sortedByDescending {it.events.maxOf {e -> e.receivedAt}}
