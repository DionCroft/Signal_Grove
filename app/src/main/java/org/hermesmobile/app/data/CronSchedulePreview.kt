package org.hermesmobile.app.data

import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Local planning data for a paused creation review. The host owns the actual schedule. */
internal data class CronSchedulePreview(
    val times: List<ZonedDateTime> = emptyList(),
    val unavailable: Unavailable? = null,
    val missed: Missed? = null,
    val graceMinutes: Int? = null,
    val elapsed: Boolean = false,
) {
    enum class Unavailable { ProfileZone, InvalidZone, Unsupported, Past, NoOccurrence, GraceRequiresCron }
    enum class Missed { OneShot, Inherit, RunOnce, Skip }

    companion object {
        private const val MaximumOccurrences = 3
        private const val TickToleranceMinutes = 2L

        fun create(
            schedule: String,
            timezone: String,
            repeat: Int?,
            catchUp: Boolean?,
            graceMinutes: Int?,
            reference: Instant,
            displayZone: ZoneId,
        ): CronSchedulePreview {
            if (schedule.length > 512 || (repeat != null && repeat < 1) ||
                (graceMinutes != null && graceMinutes !in 0..1440)
            ) return CronSchedulePreview(unavailable = Unavailable.Unsupported)
            val pattern = parseHermesCronSchedule(schedule)
                ?: return CronSchedulePreview(unavailable = Unavailable.Unsupported)
            val recurring = pattern is HermesCronSchedule.Calendar || (pattern is HermesCronSchedule.Elapsed && pattern.recurring)
            val missed = when {
                !recurring -> Missed.OneShot
                catchUp == true -> Missed.RunOnce
                catchUp == false -> Missed.Skip
                else -> Missed.Inherit
            }
            val result = CronSchedulePreview(
                missed = missed,
                graceMinutes = graceMinutes?.coerceAtLeast(TickToleranceMinutes.toInt()),
                elapsed = pattern is HermesCronSchedule.Elapsed,
            )
            if (graceMinutes != null && pattern !is HermesCronSchedule.Calendar) {
                return result.copy(unavailable = Unavailable.GraceRequiresCron, graceMinutes = null)
            }
            val pinned = if (timezone.isBlank()) null else {
                if (timezone !in ZoneId.getAvailableZoneIds()) {
                    return result.copy(unavailable = Unavailable.InvalidZone)
                }
                ZoneId.of(timezone)
            }
            val count = (repeat ?: MaximumOccurrences).coerceAtMost(MaximumOccurrences)
            return when (pattern) {
                is HermesCronSchedule.ProfileLocal -> result.copy(unavailable = Unavailable.ProfileZone)
                is HermesCronSchedule.Absolute -> {
                    val instant = pattern.at.toInstant()
                    if (instant < reference.minusSeconds(TickToleranceMinutes * 60)) {
                        result.copy(unavailable = Unavailable.Past)
                    } else result.copy(times = listOf(pattern.at.toZonedDateTime()))
                }
                is HermesCronSchedule.Elapsed -> {
                    if (pattern.minutes == 0L) return result.copy(unavailable = Unavailable.Unsupported)
                    val zone = pinned ?: displayZone
                    val times = try {
                        List(if (pattern.recurring) count else 1) { index ->
                            reference.plusSeconds(Math.multiplyExact(pattern.minutes * 60, (index + 1).toLong()))
                                .atZone(zone).also { require(it.year in 1..9999) }
                        }
                    } catch (_: ArithmeticException) {
                        return result.copy(unavailable = Unavailable.Unsupported)
                    } catch (_: DateTimeException) {
                        return result.copy(unavailable = Unavailable.Unsupported)
                    } catch (_: IllegalArgumentException) {
                        return result.copy(unavailable = Unavailable.Unsupported)
                    }
                    result.copy(times = times)
                }
                is HermesCronSchedule.Calendar -> {
                    val calendar = CalendarSchedule.parse(pattern.expression.split(' '))
                        ?: return result.copy(unavailable = Unavailable.Unsupported)
                    if (pinned == null) return result.copy(unavailable = Unavailable.ProfileZone)
                    val times = buildList {
                        var after = reference
                        for (index in 0 until count) {
                            val next = calendar.next(after, pinned) ?: break
                            add(next.atZone(pinned))
                            after = next
                        }
                    }
                    if (times.isEmpty()) result.copy(unavailable = Unavailable.NoOccurrence)
                    else result.copy(times = times)
                }
            }
        }
    }
}

private val Months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
private val NearestWeekdayField = Regex("(?:([0-9]+)w|w([0-9]+))")

private data class CalendarSchedule(
    val minutes: Set<Int>,
    val hours: Set<Int>,
    val monthDays: Set<Int>,
    val months: Set<Int>,
    val weekdays: Set<Int>,
    val seconds: Set<Int>,
    val years: Set<Int>,
    val monthDayRestricted: Boolean,
    val weekDayRestricted: Boolean,
    val lastDay: Boolean,
    val nearestWeekday: Int?,
    val lastWeekdays: Set<Int>,
) {
    fun next(after: Instant, zone: ZoneId): Instant? {
        val base = LocalDateTime.ofInstant(after, zone)
        val deadline = base.toLocalDate().plusYears(50)
        var date = base.toLocalDate()
        while (date <= deadline && date.year <= 9999) {
            if (date.monthValue in months && (years.isEmpty() || date.year in years) && matchesDay(date)) {
                val start = if (date == base.toLocalDate()) base.toLocalTime().toSecondOfDay() + 1 else 0
                for (hour in hours) {
                    if (hour < start / 3600) continue
                    for (minute in minutes) {
                        val boundary = 3600 * hour + 60 * minute
                        if (boundary + 59 < start) continue
                        for (second in seconds) {
                            if (boundary + second < start) continue
                            val wall = date.atTime(LocalTime.of(hour, minute, second))
                            val offsets = zone.rules.getValidOffsets(wall).ifEmpty {
                                val gap = zone.rules.getTransition(wall)
                                listOf(gap.offsetBefore, gap.offsetAfter)
                            }
                            for (offset in offsets) {
                                val instant = wall.toInstant(offset)
                                if (instant > after) return instant
                            }
                        }
                    }
                }
            }
            date = date.plusDays(1)
        }
        return null
    }

    private fun matchesDay(date: LocalDate): Boolean {
        val day = date.dayOfMonth
        val monthLength = date.lengthOfMonth()
        val dayMatches = if (nearestWeekday != null) {
            day == nearestWorkingDay(date, nearestWeekday)
        } else day in monthDays || (lastDay && day == monthLength)
        val weekday = date.dayOfWeek.value % 7
        val weekdayMatches = weekday in weekdays || (weekday in lastWeekdays && day + 7 > monthLength)
        return when {
            monthDayRestricted && weekDayRestricted -> dayMatches || weekdayMatches
            monthDayRestricted -> dayMatches
            weekDayRestricted -> weekdayMatches
            else -> true
        }
    }

    companion object {
        fun parse(words: List<String>): CalendarSchedule? {
            if (words.size !in 5..7) return null
            val ranges = listOf(0..59, 0..23, 1..31, 1..12, 0..6, 0..59, 1970..2099)
            val parts = words + when (words.size) {
                5 -> listOf("0", "*")
                6 -> listOf("*")
                else -> emptyList()
            }
            val nearest = NearestWeekdayField.matchEntire(parts[2])
                ?.groupValues?.drop(1)?.first(String::isNotEmpty)?.toIntOrNull()
            if (nearest != null && (nearest !in ranges[2] || parts[4] != "*")) return null
            val lastWeekdays = if (parts[4].startsWith('l')) {
                parts[4].split(',').map { token ->
                    token.takeIf { it.startsWith('l') }?.drop(1)?.toIntOrNull()
                        ?.takeIf { it in 0..7 }?.rem(7) ?: return null
                }.toSet()
            } else emptySet()
            val monthDayTerms = parts[2].split(',')
            val lastDay = "l" in monthDayTerms
            val fields = parts.mapIndexed { index, part ->
                when {
                    index == 2 && nearest != null -> emptySet()
                    index == 2 && lastDay && part == "l" -> emptySet()
                    index == 4 && lastWeekdays.isNotEmpty() -> emptySet()
                    index == 6 && part == "*" -> emptySet()
                    else -> {
                        val text = if (index == 2 && lastDay) monthDayTerms.filter { it != "l" }.joinToString(",") else part
                        expand(text, ranges[index], index) ?: return null
                    }
                }
            }
            return CalendarSchedule(
                minutes = fields[0], hours = fields[1], monthDays = fields[2], months = fields[3],
                weekdays = fields[4], seconds = fields[5], years = fields[6],
                monthDayRestricted = "*" !in monthDayTerms, weekDayRestricted = "*" !in parts[4].split(','),
                lastDay = lastDay, nearestWeekday = nearest, lastWeekdays = lastWeekdays,
            )
        }

        private fun expand(text: String, range: IntRange, index: Int): Set<Int>? {
            if (text == "*") return range.toSet()
            fun number(token: String): Int? {
                val parsed = token.toIntOrNull() ?: when (index) {
                    3 -> Months.indexOf(token).takeIf { it >= 0 }?.plus(1)
                    4 -> HermesCronWeekdays.indexOf(token).takeIf { it >= 0 }
                    else -> null
                } ?: return null
                return (if (index == 4 && parsed == 7) 0 else parsed).takeIf { it in range }
            }
            val values = sortedSetOf<Int>()
            for (term in text.split(',')) {
                val stepParts = term.split('/')
                if (stepParts.size > 2) return null
                val step = if (stepParts.size == 2) stepParts[1].toIntOrNull()?.takeIf { it > 0 } ?: return null else 1
                val limits = stepParts[0].split('-')
                if (limits.size > 2) return null
                if (limits == listOf("*")) {
                    values.addAll(range.step(step))
                    continue
                }
                val start = number(limits[0]) ?: return null
                val end = when {
                    limits.size == 2 -> if (index == 2 && limits[1] == "l") 31 else number(limits[1]) ?: return null
                    stepParts.size == 2 -> range.last
                    else -> { values += start; continue }
                }
                when {
                    start < end -> values.addAll((start..end).step(step))
                    start == end -> values.addAll(range.step(step))
                    else -> {
                        val tail = (start..range.last).step(step)
                        values.addAll(tail)
                        val position = tail.last.toLong() - range.first
                        val skip = if (position + step > range.last - range.first + 1) step.toLong() - (range.last - tail.last) else 0
                        val head = range.first + skip
                        if (head <= end) values.addAll((head.toInt()..end).step(step))
                    }
                }
            }
            return values.takeIf { it.isNotEmpty() }
        }

        private fun nearestWorkingDay(date: LocalDate, requested: Int): Int {
            val target = requested.coerceAtMost(date.lengthOfMonth())
            return when (date.withDayOfMonth(target).dayOfWeek) {
                DayOfWeek.SATURDAY -> if (target == 1) 3 else target - 1
                DayOfWeek.SUNDAY -> if (target == date.lengthOfMonth()) target - 2 else target + 1
                else -> target
            }
        }
    }
}
