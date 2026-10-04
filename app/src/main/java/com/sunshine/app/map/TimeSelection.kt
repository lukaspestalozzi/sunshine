package com.sunshine.app.map

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.roundToInt

// The time slider covers the selected day from its start up to, but not including, the start of
// the next day, on the instant timeline: 23 or 25 hours on most daylight-saving transition days.

/**
 * Number of slider positions on [date]: one per [step] minutes of the day's real length. The step
 * is 5 minutes, or the `Sun & shade` step while that mode is shown (time-selection spec, "Choose
 * the time of day"); every allowed step divides 23, 24 and 25 hours.
 */
fun sliderPositions(
    date: LocalDate,
    zone: ZoneId,
    step: Int = SLIDER_STEP_MINUTES,
): Int {
    val length = Duration.between(date.atStartOfDay(zone), date.plusDays(1).atStartOfDay(zone))
    return (length.toMinutes() / step).toInt()
}

/** The time at slider value [minutes] (minutes since the start of [date]), snapped to the grid of [step] minutes. */
fun sliderTime(
    date: LocalDate,
    zone: ZoneId,
    minutes: Float,
    step: Int = SLIDER_STEP_MINUTES,
): ZonedDateTime {
    val position = (minutes / step).roundToInt().coerceIn(0, sliderPositions(date, zone, step) - 1)
    return date.atStartOfDay(zone).plusMinutes(position.toLong() * step)
}

/**
 * [time] rounded to the nearest step of [step] minutes of its day, counted on the real timeline
 * from the start of the day: half up, and at most the day's last step (design D4 of add-settings).
 */
fun roundToStep(
    time: ZonedDateTime,
    step: Int,
): ZonedDateTime {
    val date = time.toLocalDate()
    val start = date.atStartOfDay(time.zone)
    val elapsedMillis = Duration.between(start, time).toMillis()
    val stepMillis = step * MILLIS_PER_MINUTE.toLong()
    val position = ((elapsedMillis + stepMillis / 2) / stepMillis).coerceAtMost(sliderPositions(date, time.zone, step) - 1L)
    return start.plusMinutes(position * step)
}

/** Slider value for [time]: minutes since the start of its day; off the grid for "Now". */
fun sliderMinutes(time: ZonedDateTime): Float =
    Duration.between(time.toLocalDate().atStartOfDay(time.zone), time).toMillis() / MILLIS_PER_MINUTE

/** Same wall-clock time on [date]; a time in a spring-forward gap moves forward by the gap. */
fun ZonedDateTime.withDate(date: LocalDate): ZonedDateTime = ZonedDateTime.of(date, toLocalTime(), zone)

/** Material 3 date pickers work in milliseconds of UTC midnight, independent of the device zone. */
fun LocalDate.toDatePickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun datePickerMillisToDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC).toLocalDate()

const val SLIDER_STEP_MINUTES = 5
private const val MILLIS_PER_MINUTE = 60_000f
