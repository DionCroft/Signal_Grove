package org.hermesmobile.app.data

import java.time.LocalDate

enum class TaskFilter { ALL, OPEN, COMPLETED, OVERDUE }
fun filteredTasks(tasks: List<LocalTask>,query: String,filter: TaskFilter,today: LocalDate = LocalDate.now()): List<LocalTask> =
    tasks.filter {t ->
        val overdue=!t.completed && t.dueDate.isNotBlank() && runCatching {LocalDate.parse(t.dueDate).isBefore(today)}.getOrDefault(false)
        (query.isBlank() || t.title.contains(query,true) || t.notes.contains(query,true)) && when(filter) {
            TaskFilter.ALL -> true;TaskFilter.OPEN -> !t.completed;TaskFilter.COMPLETED -> t.completed;TaskFilter.OVERDUE -> overdue
        }
    }.sortedWith(compareBy<LocalTask> {it.completed}.thenBy {it.dueDate.ifBlank {"9999-99-99"}}.thenByDescending {it.priority}.thenBy {it.title})
