package org.hermesmobile.app

import org.hermesmobile.app.data.CronSchedulePreview
import org.hermesmobile.app.data.CronSchedulePreview.Missed
import org.hermesmobile.app.data.CronSchedulePreview.Unavailable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class CronSchedulePreviewTest {
    private fun preview(
        schedule: String,
        from: String = "2026-01-01T00:00:00Z",
        zone: String = "UTC",
        repeat: Int? = null,
        catchUp: Boolean? = null,
        grace: Int? = null,
    ) = CronSchedulePreview.create(schedule, zone, repeat, catchUp, grace, Instant.parse(from), ZoneId.of("Europe/London"))

    private fun CronSchedulePreview.instants() = times.map { it.toInstant().toString() }

    @Test fun dailySlotsKeepWallTimeAcrossBothDstChanges() {
        val schedule = "0 9 * * *"
        val spring = preview(schedule, "2026-03-07T14:00:00Z", "America/Toronto", 2)
        val fall = preview(schedule, "2026-10-31T13:00:00Z", "America/Toronto", 2)
        assertEquals(listOf("2026-03-08T13:00:00Z", "2026-03-09T13:00:00Z"), spring.instants())
        assertEquals(listOf("2026-11-01T14:00:00Z", "2026-11-02T14:00:00Z"), fall.instants())
        assertTrue((spring.times + fall.times).all { it.hour == 9 && it.zone.id == "America/Toronto" })
    }

    @Test fun repeatedAndMissingWallHoursResolveToFutureInstants() {
        val fold = preview("30 1 * * *", "2026-10-25T01:10:00Z", "Europe/London", 1)
        assertEquals(listOf("2026-10-25T01:30:00Z"), fold.instants())
        assertEquals("Z", fold.times.single().offset.toString())
        val gap = preview("30 1 * * *", "2026-03-28T04:00:00Z", "Europe/London", 1)
        assertEquals(listOf("2026-03-29T01:30:00Z"), gap.instants())
        assertEquals(2, gap.times.single().hour)
    }

    @Test fun intervalsAndOneShotDelaysAdvanceByElapsedTime() {
        val start = "2026-03-29T00:30:00Z"
        val interval = preview("every 2h", start, "", 2)
        assertEquals(listOf("2026-03-29T02:30:00Z", "2026-03-29T04:30:00Z"), interval.instants())
        assertEquals(3, interval.times.first().hour)
        assertTrue(interval.elapsed)
        val delay = preview("in 2 hrs", start, "", 9)
        assertEquals(listOf("2026-03-29T02:30:00Z"), delay.instants())
        assertEquals(Missed.OneShot, delay.missed)
        assertEquals(listOf("2026-01-01T01:00:00Z"), preview("hour", repeat = 1).instants())
    }

    @Test fun absoluteOneShotUsesItsOffsetAndHonorsPastTolerance() {
        val at = "2026-01-01T08:30:00+0530"
        assertEquals(listOf("2026-01-01T03:00:00Z"), preview(at, repeat = 9).instants())
        assertEquals("+05:30", preview(at).times.single().offset.toString())
        assertEquals(listOf("2026-01-01T03:00:00Z"), preview(at, "2026-01-01T03:02:00Z").instants())
        assertEquals(Unavailable.Past, preview(at, "2026-01-01T03:02:01Z").unavailable)
        assertEquals(Unavailable.ProfileZone, preview("2026-01-01T09:00:00", zone = "Europe/Paris").unavailable)
    }

    @Test fun futureTimesRequireAnExplicitKnownZoneForCalendarSchedules() {
        assertEquals(Unavailable.ProfileZone, preview("0 9 * * *", zone = "").unavailable)
        assertEquals(Unavailable.InvalidZone, preview("0 9 * * *", zone = "Mars/Olympus").unavailable)
        assertEquals(Unavailable.InvalidZone, preview("0 9 * * *", zone = "+03:00").unavailable)
        assertTrue(preview("0 9 * * *", zone = "").times.isEmpty())
        assertNull(preview("in 1 day", zone = "").unavailable)
    }

    @Test fun naturalPhrasesAndStandardCalendarExpressionsMatchHermes() {
        assertEquals(listOf("2026-01-02T09:30:00Z", "2026-01-05T09:30:00Z"),
            preview("Every monday and Friday at 9:30 AM", repeat = 2).instants())
        assertEquals(listOf("2026-01-01T12:00:00Z"), preview("weekdays at noon", repeat = 1).instants())
        assertEquals(listOf("2026-01-01T00:05:00Z", "2026-01-01T00:20:00Z", "2026-01-01T00:35:00Z"),
            preview("5/15 * * * *").instants())
        assertEquals(listOf("2026-01-05T09:00:00Z", "2026-01-12T09:00:00Z", "2026-01-15T09:00:00Z"),
            preview("0 9 15 JAN MON").instants())
        assertEquals(listOf("2026-01-02T09:00:00Z", "2026-01-03T09:00:00Z", "2026-01-04T09:00:00Z"),
            preview("0 9 * * FRI-7").instants())
        assertEquals(listOf("2026-01-01T09:00:00Z"), preview("0 9 */1 * MON", repeat = 1).instants())
    }

    @Test fun calendarSpecialDaysSecondsAndExplicitYearsStayBounded() {
        assertEquals(listOf("2028-02-29T09:00:00Z"), preview("0 9 29 FEB *", repeat = 1).instants())
        assertEquals(listOf("2026-01-31T09:00:00Z"), preview("0 9 L * *", repeat = 1).instants())
        assertEquals(listOf("2026-01-30T09:00:00Z"), preview("0 9 * * L5", repeat = 1).instants())
        assertEquals(listOf("2026-02-27T09:00:00Z"), preview("0 9 31W FEB *", repeat = 1).instants())
        assertEquals(listOf("2027-01-01T09:00:10Z", "2027-01-01T09:00:40Z"),
            preview("0 9 * * * 10,40 2027", repeat = 2).instants())
        assertEquals(Unavailable.NoOccurrence, preview("0 9 * * * 0 2025").unavailable)
        assertEquals(Unavailable.NoOccurrence, preview("0 9 31 FEB *").unavailable)
    }

    @Test fun missesAndRepeatLimitsAreIndependentOfPreviewTime() {
        val skip = preview("0 9 * * *", repeat = 2, catchUp = false, grace = 0)
        assertEquals(2, skip.times.size)
        assertEquals(2, skip.graceMinutes)
        assertEquals(Missed.Skip, skip.missed)
        val run = preview("0 9 * * *", repeat = 100, catchUp = true, grace = 180)
        assertEquals(3, run.times.size)
        assertEquals(180, run.graceMinutes)
        assertEquals(Missed.RunOnce, run.missed)
        val inherited = preview("0 9 * * *")
        assertEquals(Missed.Inherit, inherited.missed)
        assertNull(inherited.graceMinutes)
        assertEquals(Unavailable.GraceRequiresCron, preview("every 1h", grace = 0).unavailable)
    }

    @Test fun invalidAndNonDeterministicInputsNeverProduceAnEstimate() {
        for (text in listOf("0m", "999999999999999999999d", "0 9 * FEB/0 *", "R 9 * * *", "0 24 * * *", "when ready")) {
            val result = preview(text)
            assertEquals(text, Unavailable.Unsupported, result.unavailable)
            assertTrue(text, result.times.isEmpty())
        }
    }
}
