package org.hermesmobile.app

import org.hermesmobile.app.data.GroveWeatherArea
import org.hermesmobile.app.ui.*
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class LivingGroveTest {
    private fun time(value: String) = ZonedDateTime.parse(value)

    @Test fun deviceClockSelectsDawnDayDuskAndNight() {
        val day = time("2026-11-26T00:00:00+00:00")
        assertEquals(GroveLight.NIGHT, groveScene(day).light)
        assertEquals(GroveLight.DAWN, groveScene(day.plusHours(6)).light)
        assertEquals(GroveLight.DAY, groveScene(day.plusHours(12)).light)
        assertEquals(GroveLight.DUSK, groveScene(day.plusHours(18)).light)
    }

    @Test fun dateChangesTheSceneAndSouthernSeasonsFollowTown() {
        val winter = time("2026-12-01T21:00:00+00:00")
        assertEquals(GroveSeason.AUTUMN, groveScene(winter.minusDays(1)).season)
        assertEquals(GroveSeason.WINTER, groveScene(winter).season)
        assertEquals(GroveSeason.SUMMER, groveScene(winter, latitude = -33.75).season)
        assertEquals(GroveSeason.SPRING, groveScene(winter.minusDays(1), latitude = -33.75).season)
        val phases = (0..35).map { groveScene(winter.plusDays(it.toLong())).moonPhase }.toSet()
        assertEquals((0..6).toSet(), phases)
    }

    @Test fun offlineWeatherAndSurprisesAreStableAcrossReopening() {
        val winter = time("2026-12-01T21:00:00+00:00")
        val weather = (0..70).map { groveScene(winter.plusDays(it.toLong())).sky }.toSet()
        assertTrue(GroveSky.CLEAR in weather && GroveSky.SNOW in weather)
        val windows = List(50) { winter.plusDays(it.toLong()) }
        val events = windows.map { groveScene(it, liveSky = GroveSky.CLEAR).event }.toSet()
        assertEquals(setOf(GroveEvent.NONE, GroveEvent.METEOR), events)
        windows.forEach { start ->
            assertEquals(groveScene(start, liveSky = GroveSky.CLEAR), groveScene(start.plusMinutes(29), liveSky = GroveSky.CLEAR))
        }
    }

    @Test fun forecastAcceptsOnlyDocumentedWeatherAndRoundedArea() {
        val noon = time("2026-11-26T12:00:00+00:00")
        assertEquals(GroveSky.RAIN, groveScene(noon, liveSky = groveWeatherCode(63)).sky)
        assertEquals(GroveSky.SNOW, groveWeatherCode(75))
        assertEquals(GroveSky.STORM, groveWeatherCode(99))
        assertNull(groveWeatherCode(400))
        assertNull(GroveWeatherArea.create("Invalid", Double.NaN, 0.0))
        assertNull(GroveWeatherArea.decode("{\"latitude\":\"NaN\"}"))
        val area = requireNotNull(GroveWeatherArea.create("Aberdeen", 57.1497, -2.0943))
        assertEquals(area, GroveWeatherArea.decode(area.encode()))
        val url = forecastUrl(area)
        assertEquals("https", url.scheme)
        assertEquals("api.open-meteo.com", url.host)
        assertEquals("/v1/forecast", url.encodedPath)
        assertEquals("57.25", url.queryParameter("latitude"))
        assertEquals("-2.0", url.queryParameter("longitude"))
        assertEquals(setOf("latitude", "longitude", "current", "forecast_days"), url.queryParameterNames)
        assertFalse(url.toString().contains(area.name))
    }
}
