package com.sunshine.app.map

import com.sunshine.core.GeoPoint
import com.sunshine.core.SunPeriods
import com.sunshine.core.WholeDay
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

// Texts of the sun panel values, fixed by the sun-position and time-selection specs. All output is
// independent of the device locale.

/** E.g. `2025-12-21 12:00 UTC+1`. */
fun formatSelectedTime(time: ZonedDateTime): String = "${time.format(DATE_TIME)} ${formatUtcOffset(time.offset)}"

/** Whole degrees and the 8-point compass direction of the rounded value, e.g. `173° S`. */
fun formatAzimuth(azimuth: Double): String {
    val degrees = roundHalfUp(azimuth, decimals = 0).toInt() % FULL_CIRCLE
    // Each direction covers 45° centred on it: N is [337.5°, 22.5°). Doubled to stay in integers.
    val direction = COMPASS[(degrees * 2 + HALF_SECTOR_DOUBLED) / SECTOR_DOUBLED % COMPASS.size]
    return "$degrees° $direction"
}

/** One decimal, e.g. `19.7°`, `-0.7°`. */
fun formatElevation(elevation: Double): String = "${roundHalfUp(elevation, decimals = 1).toPlainString()}°"

/** Whole metres, e.g. `1634 m`; `…` while loading and `unknown` when unknown (elevation-data spec). */
fun formatAltitude(elevation: ElevationState): String =
    when (elevation) {
        is ElevationState.Known -> "${roundHalfUp(elevation.metres, decimals = 0).toPlainString()} m"
        ElevationState.Loading -> "…"
        ElevationState.Unknown -> "unknown"
    }

/** `HH:mm` rounded to the nearest minute, with the offset appended when it differs from [selected]'s. */
fun formatEventTime(
    event: ZonedDateTime?,
    selected: ZonedDateTime,
): String {
    if (event == null) return "none this day"
    val rounded = event.plusSeconds(HALF_MINUTE_SECONDS).truncatedTo(ChronoUnit.MINUTES)
    val time = rounded.format(TIME)
    return if (rounded.offset == selected.offset) time else "$time ${formatUtcOffset(rounded.offset)}"
}

/**
 * The sun periods (point-sunshine spec), e.g. `10:09–14:51, 15:11–15:52`, with times as in
 * [formatEventTime]; `none this day`, `…` while the horizon is computed, and `unknown`.
 */
fun formatSunshine(
    sunshine: SunshineUiState,
    selected: ZonedDateTime,
): String {
    val periods = (sunshine as? SunshineUiState.Ready)?.periods ?: return "…"
    return when {
        periods !is SunPeriods.Known -> "unknown"
        periods.periods.isEmpty() -> "none this day"
        else -> periods.periods.joinToString(", ") { "${formatEventTime(it.start, selected)}–${formatEventTime(it.end, selected)}" }
    }
}

/** E.g. `8 h 33 min`, rounded to the nearest minute. */
fun formatDayLength(dayLength: Duration): String {
    val minutes = dayLength.plusSeconds(HALF_MINUTE_SECONDS).toMinutes()
    return "${minutes / MINUTES_PER_HOUR} h ${minutes % MINUTES_PER_HOUR} min"
}

/**
 * The sun hours of the cell under the crosshair at [center] (sun-exposure-heatmap spec, "Sun hours in
 * the information panel"): `≈ 5 h 20 min`, `at least 5 h 20 min (10 min unknown)`, `unknown`, or
 * `…` unless [heatmap] is ready for the area around [center].
 */
fun formatSunHours(
    heatmap: HeatmapUiState,
    center: GeoPoint,
): String {
    val hours = (heatmap as? HeatmapUiState.Ready)?.hours?.takeIf { it.area.center == center } ?: return "…"
    // The crosshair is the centre of the area, so the raster's centre pixel.
    val pixel = hours.height / 2 * hours.width + hours.width / 2
    val sun = hours.sun[pixel] * SLIDER_STEP_MINUTES
    val unknown = hours.unknown[pixel] * SLIDER_STEP_MINUTES
    return when {
        hours.unknown[pixel].toInt() == hours.steps -> "unknown"
        unknown == 0 -> "≈ ${formatMinutes(sun)}"
        unknown < MINUTES_PER_HOUR -> "at least ${formatMinutes(sun)} ($unknown min unknown)"
        else -> "at least ${formatMinutes(sun)} (${formatMinutes(unknown)} unknown)"
    }
}

/** The whole-day text, or `null` when the day has a sunrise or a sunset. */
fun formatWholeDay(wholeDay: WholeDay?): String? =
    when (wholeDay) {
        WholeDay.ABOVE_HORIZON -> "Sun above the horizon all day"
        WholeDay.BELOW_HORIZON -> "Sun below the horizon all day"
        null -> null
    }

// E.g. `5 h 20 min`.
private fun formatMinutes(minutes: Int): String = "${minutes / MINUTES_PER_HOUR} h ${minutes % MINUTES_PER_HOUR} min"

/** `UTC`, `UTC+1`, `UTC+5:30`, `UTC-2:30`. */
private fun formatUtcOffset(offset: ZoneOffset): String {
    val totalSeconds = offset.totalSeconds
    if (totalSeconds == 0) return "UTC"
    val sign = if (totalSeconds > 0) '+' else '-'
    val hours = abs(totalSeconds) / SECONDS_PER_HOUR
    val minutes = abs(totalSeconds) % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    return if (minutes == 0) "UTC$sign$hours" else "UTC$sign$hours:%02d".format(Locale.ROOT, minutes)
}

private fun roundHalfUp(
    value: Double,
    decimals: Int,
): BigDecimal = BigDecimal(value.toString()).setScale(decimals, RoundingMode.HALF_UP)

private val DATE_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", Locale.ROOT)
private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
private val COMPASS = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
private const val FULL_CIRCLE = 360
private const val SECTOR_DOUBLED = 90
private const val HALF_SECTOR_DOUBLED = 45
private const val HALF_MINUTE_SECONDS = 30L
private const val MINUTES_PER_HOUR = 60
private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600
