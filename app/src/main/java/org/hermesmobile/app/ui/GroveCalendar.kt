package org.hermesmobile.app.ui

import java.time.ZonedDateTime

internal enum class GroveLight { DAWN, DAY, DUSK, NIGHT }
internal enum class GroveSeason { SPRING, SUMMER, AUTUMN, WINTER }
internal enum class GroveSky { CLEAR, CLOUDY, FOG, RAIN, SNOW, STORM }
internal enum class GroveEvent { NONE, VISITOR, METEOR, FIREFLIES, RAINBOW }

internal data class GroveScene(
    val light: GroveLight,
    val season: GroveSeason,
    val sky: GroveSky,
    val moonPhase: Int,
    val event: GroveEvent
)

internal fun groveScene(now: ZonedDateTime, latitude: Double? = null, liveSky: GroveSky? = null): GroveScene {
    val date = now.toLocalDate()
    val month = if (latitude != null && latitude < 0) (date.monthValue + 5) % 12 + 1 else date.monthValue
    val season = when (month) {
        in 3..5 -> GroveSeason.SPRING
        in 6..8 -> GroveSeason.SUMMER
        in 9..11 -> GroveSeason.AUTUMN
        else -> GroveSeason.WINTER
    }
    val light = when (now.hour) {
        in 5..7 -> GroveLight.DAWN
        in 8..16 -> GroveLight.DAY
        in 17..19 -> GroveLight.DUSK
        else -> GroveLight.NIGHT
    }
    val day = date.toEpochDay()
    val sky = liveSky ?: when (groveRoll(day, 10)) {
        0 -> if (season == GroveSeason.WINTER) GroveSky.SNOW else GroveSky.RAIN
        1 -> if (season == GroveSeason.SUMMER) GroveSky.STORM else GroveSky.FOG
        2, 3 -> GroveSky.CLOUDY
        else -> GroveSky.CLEAR
    }
    // A date and half-hour bucket keep surprises consistent throughout a visit.
    val event = when {
        groveRoll(day * 48 + now.hour * 2 + now.minute / 30, 5) != 0 -> GroveEvent.NONE
        light == GroveLight.NIGHT && sky == GroveSky.CLEAR -> GroveEvent.METEOR
        light == GroveLight.DUSK && sky == GroveSky.CLEAR -> GroveEvent.FIREFLIES
        light == GroveLight.DAY && sky == GroveSky.RAIN -> GroveEvent.RAINBOW
        sky == GroveSky.CLEAR || sky == GroveSky.CLOUDY -> GroveEvent.VISITOR
        else -> GroveEvent.NONE
    }
    return GroveScene(light, season, sky, Math.floorMod(day, 28L).toInt() / 4, event)
}

private fun groveRoll(key: Long, bound: Int): Int {
    val mixed = (key xor (key ushr 16)) * 0x45d9f3bL
    return Math.floorMod(mixed xor (mixed ushr 16), bound.toLong()).toInt()
}
