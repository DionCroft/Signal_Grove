package org.hermesmobile.app

import java.time.Instant
import java.time.ZoneId
import org.hermesmobile.app.data.LocalTask
import org.hermesmobile.app.data.duePhoneTasks
import org.junit.Assert.assertEquals
import org.junit.Test

class TodayDateTest {
    @Test fun dueDatesFollowTheLocalCalendarAndOmitCompletedOrInvalidTasks() {
        val instant = Instant.parse("2026-11-27T00:15:00Z")
        val tasks = listOf(
            LocalTask("first", "Earlier", "2026-11-26"),
            LocalTask("next", "After crossing midnight", "2026-11-27"),
            LocalTask("done", "Completed", "2026-11-26", completed = true),
            LocalTask("invalid", "Invalid ISO", "2026-13-01"),
            LocalTask("undated", "Undated")
        )
        assertEquals(listOf("first"), duePhoneTasks(tasks, instant, ZoneId.of("America/Los_Angeles")).map { it.id })
        assertEquals(listOf("first", "next"), duePhoneTasks(tasks, instant, ZoneId.of("Europe/London")).map { it.id })
    }
}
