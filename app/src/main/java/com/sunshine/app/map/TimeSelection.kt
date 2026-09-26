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

/** Number of slider positions on [date]: one per 5 minutes of the day's real length. */
fun sliderPositions(
    date: LocalDate,
    zone: ZoneId,
): Int {
    val length = Duration.between(date.atStartOfDay(zone), date.plusDays(1).atStartOfDay(zone))
    return (length.toMinutes() / SLIDER_STEP_MINUTES).toInt()
}

/** The time at slider value [minutes] (minutes since the start of [date]), snapped to the 5-minute grid. */
fun sliderTime(
    date: LocalDate,
    zone: ZoneId,
    minutes: Float,
): ZonedDateTime {
    val step = (minutes / SLIDER_STEP_MINUTES).roundToInt().coerceIn(0, sliderPositions(date, zone) - 1)
    return date.atStartOfDay(zone).plusMinutes(step.toLong() * SLIDER_STEP_MINUTES)
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
