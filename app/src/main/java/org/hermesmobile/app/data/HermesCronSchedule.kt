package org.hermesmobile.app.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.DateTimeException
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale

/** The typed input Hermes stores. Forecast support can be narrower than exact wire comparison. */
internal sealed interface HermesCronSchedule {
    data class Calendar(val expression: String) : HermesCronSchedule
    data class Absolute(val at: OffsetDateTime) : HermesCronSchedule
    data class ProfileLocal(val at: LocalDateTime) : HermesCronSchedule
    data class Elapsed(val minutes: Long, val recurring: Boolean) : HermesCronSchedule
}

private const val MaxTimestampLength = 64
private val RelativeClockWindow = Duration.ofSeconds(30)

internal fun HermesCronSchedule.matchesStoredSchedule(schedule: JsonObject, createdAt: String?): Boolean {
    val kind = schedule.scheduleString("kind")
    return when (this) {
        is HermesCronSchedule.Calendar -> {
            kind == "cron" && schedule.scheduleString("expr")?.let(::normalizedCron) == expression
        }
        is HermesCronSchedule.Absolute -> {
            kind == "once" && schedule.storedRunAt()?.toInstant() == at.toInstant().truncatedTo(ChronoUnit.MICROS)
        }
        is HermesCronSchedule.ProfileLocal -> {
            kind == "once" && schedule.storedRunAt()?.toLocalDateTime() == at.truncatedTo(ChronoUnit.MICROS)
        }
        is HermesCronSchedule.Elapsed -> {
            if (recurring) {
                kind == "interval" && (schedule["minutes"] as? JsonPrimitive)
                    ?.takeUnless { it.isString }?.longOrNull == minutes
            } else {
                if (kind != "once" || createdAt == null) return false
                val created = readOffsetTimestamp(createdAt)?.toInstant() ?: return false
                val planned = schedule.storedRunAt()?.toInstant() ?: return false
                val parsedAt = planned.minusSeconds(minutes * 60)
                val betweenReads = Duration.between(parsedAt, created)
                // Hermes anchors the delay and stamps created_at in separate clock reads.
                // A window shorter than a minute keeps a different delay from matching.
                !betweenReads.isNegative && betweenReads < RelativeClockWindow
            }
        }
    }
}

private fun JsonObject.scheduleString(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.storedRunAt() = scheduleString("run_at")?.let(::readOffsetTimestamp)

private fun normalizedCron(expression: String): String =
    expression.trim().lowercase(Locale.ROOT).split(ScheduleSpace).joinToString(" ")

private val DurationField = Regex("([0-9]*)\\s*(m|min|mins|minute|minutes|h|hr|hrs|hour|hours|d|day|days)")
private val ClockField = Regex("([0-9]{1,2})(?::([0-9]{2}))?(am|pm)?")
private val ScheduleSpace = Regex("\\s+")
private val CalendarField = Regex("[a-z0-9*,/\\-]+")
private val IsoDateStart = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}")
private val CompactOffset = Regex("([+-][0-9]{2})([0-9]{2})$")

internal fun parseHermesCronSchedule(value: String): HermesCronSchedule? {
    if (value.length > 512) return null
    val input = value.trim()
    val text = input.lowercase(Locale.ROOT)
    val every = text.startsWith("every ")
    val phrase = if (every) text.removePrefix("every ").trimStart() else text
    naturalCalendar(phrase)?.let { return HermesCronSchedule.Calendar(it.joinToString(" ")) }
    val fields = text.split(ScheduleSpace)
    if (!every && fields.size in 5..7 && fields.take(5).all(CalendarField::matches)) {
        return HermesCronSchedule.Calendar(fields.joinToString(" "))
    }
    if (IsoDateStart.containsMatchIn(text)) {
        val iso = if (input.length > 10) input.replaceRange(10, 11, "T") else input
        readOffsetTimestamp(iso)?.let { return HermesCronSchedule.Absolute(it) }
        val local = try {
            if (iso.length == 10) LocalDate.parse(iso).atStartOfDay() else LocalDateTime.parse(iso)
        } catch (_: DateTimeException) { return null }
        return local.takeIf { it.year >= 1 }?.let { HermesCronSchedule.ProfileLocal(it) }
    }
    val once = !every && text.startsWith("in ")
    val duration = if (once) text.removePrefix("in ").trimStart() else phrase
    val match = DurationField.matchEntire(duration) ?: return null
    val number = match.groupValues[1].ifEmpty { "1" }.toLongOrNull() ?: return null
    val unit = when (match.groupValues[2].first()) {
        'm' -> 1L
        'h' -> 60L
        else -> 1440L
    }
    if (number > Long.MAX_VALUE / 60 / unit) return null
    return HermesCronSchedule.Elapsed(number * unit, !once)
}

private fun readOffsetTimestamp(value: String): OffsetDateTime? {
    if (value.length > MaxTimestampLength) return null
    return try {
        OffsetDateTime.parse(CompactOffset.replace(value, "$1:$2")).takeIf { it.year in 1..9999 }
    } catch (_: DateTimeException) { null }
}

internal val HermesCronWeekdays = listOf("sun", "mon", "tue", "wed", "thu", "fri", "sat")
private val FullWeekdays = listOf("sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday")

private fun naturalCalendar(phrase: String): List<String>? {
    val words = phrase.replace(',', ' ').split(ScheduleSpace).filter(String::isNotBlank)
    val first = words.firstOrNull() ?: return null
    var clockStart = 1
    val dayField = when (first) {
        "day", "daily", "everyday" -> "*"
        "weekday", "weekdays" -> "1-5"
        "weekend", "weekends" -> "0,6"
        else -> {
            val days = linkedSetOf<Int>()
            clockStart = 0
            for (word in words) {
                val day = when (word) {
                    "tues" -> 2
                    "weds" -> 3
                    "thur", "thurs" -> 4
                    else -> FullWeekdays.indexOf(word).takeIf { it >= 0 } ?: HermesCronWeekdays.indexOf(word)
                }
                if (word != "and" && day < 0) break
                if (day >= 0) days += day
                clockStart++
            }
            if (days.isEmpty()) return null
            days.joinToString(",")
        }
    }
    if (words.getOrNull(clockStart) == "at") clockStart++
    val clock = words.drop(clockStart).joinToString("")
    val (hour, minute) = when (clock) {
        "noon", "midday" -> 12 to 0
        "midnight" -> 0 to 0
        else -> {
            val time = ClockField.matchEntire(clock) ?: return null
            val rawHour = time.groupValues[1].toInt()
            val suffix = time.groupValues[3]
            if (suffix.isNotEmpty() && rawHour !in 1..12) return null
            val hour24 = if (suffix.isEmpty()) rawHour else rawHour % 12 + if (suffix == "pm") 12 else 0
            val min = time.groupValues[2].ifEmpty { "0" }.toInt()
            if (hour24 > 23 || min > 59) return null
            hour24 to min
        }
    }
    return listOf(minute.toString(), hour.toString(), "*", "*", dayField)
}
