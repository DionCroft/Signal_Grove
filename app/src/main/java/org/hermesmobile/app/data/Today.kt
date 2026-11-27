package org.hermesmobile.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class TodayApproval(
    val scope: String,
    val sessionKey: String,
    val sessionTitle: String,
    val runId: String,
    val requestId: String,
    val lastConfirmedAt: Long
)

internal data class TodayRun(
    val scope: String,
    val sessionKey: String,
    val sessionTitle: String,
    val runId: String,
    val receiptKey: String,
    val lastConfirmedAt: Long
)

internal data class TodayRemoteSnapshot(
    val scope: String,
    val endedAt: Long,
    val confirmed: Map<String, Long>,
    val failed: Int
)

internal fun duePhoneTasks(tasks: List<LocalTask>, now: Instant, zone: ZoneId): List<LocalTask> {
    val today = now.atZone(zone).toLocalDate()
    return tasks.filter { task ->
        !task.completed && task.dueDate.isNotBlank() &&
            runCatching { !LocalDate.parse(task.dueDate).isAfter(today) }.getOrDefault(false)
    }.sortedWith(compareBy<LocalTask> { it.dueDate }.thenByDescending { it.priority }.thenBy { it.id })
}
