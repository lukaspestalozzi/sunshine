package com.sunshine.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Whether the sun shines at a location, taking terrain into account (point-sunshine spec). */
enum class Sunshine { SUN, SHADE, UNKNOWN }

/** Angle from the sun's centre to its upper edge (upper limb) in degrees. */
const val SUN_UPPER_LIMB = 0.266

/**
 * Sunshine at [point] at [instant]: the sun's upper edge against the horizon [profile] at the sun's
 * azimuth. An incomplete horizon decides shade only at or below its angle, its lower bound, and sun
 * only above its upper bound.
 */
fun sunshineAt(
    profile: HorizonProfile,
    point: GeoPoint,
    instant: Instant,
): Sunshine {
    val sun = sunPosition(point, instant)
    return sunshine(profile, sun.azimuth, sun.elevation + SUN_UPPER_LIMB)
}

internal fun sunshine(
    profile: HorizonProfile,
    azimuth: Double,
    upperEdge: Double,
): Sunshine =
    when {
        upperEdge <= profile.angleAt(azimuth) -> Sunshine.SHADE
        upperEdge > profile.upperAt(azimuth) || profile.isCompleteAt(azimuth) -> Sunshine.SUN
        else -> Sunshine.UNKNOWN
    }

/** A maximal interval of sunshine within a day. */
data class SunPeriod(
    val start: ZonedDateTime,
    val end: ZonedDateTime,
)

sealed interface SunPeriods {
    /** Every period of the day in chronological order; empty when the sun does not shine. */
    data class Known(
        val periods: List<SunPeriod>,
    ) : SunPeriods

    /** The sunshine state is unknown at some instant with the sun's upper edge above 0°. */
    data object Unknown : SunPeriods
}

/**
 * The sun periods of [date] in [zone] at [point] (point-sunshine spec, "Sun periods of the selected
 * day"). The state is sampled every 10 s over the local day, the window of [sunDay]; a boundary lies
 * midway between the last sample of one state and the first of the other, or at the day's start or
 * end. Periods shorter than 1 min are omitted.
 */
fun sunPeriods(
    profile: HorizonProfile,
    point: GeoPoint,
    date: LocalDate,
    zone: ZoneId,
): SunPeriods {
    val dayStart = date.atStartOfDay(zone)
    val dayEnd = date.plusDays(1).atStartOfDay(zone)
    val steps = Duration.between(dayStart, dayEnd).seconds.let { (it + STEP_SECONDS - 1) / STEP_SECONDS }
    val periods = mutableListOf<SunPeriod>()
    var start: ZonedDateTime? = null
    for (k in 0..steps) {
        val sunny =
            k < steps &&
                run {
                    val sun = sunPosition(point, dayStart.toInstant().plusSeconds(k * STEP_SECONDS))
                    val upperEdge = sun.elevation + SUN_UPPER_LIMB
                    val state = sunshine(profile, sun.azimuth, upperEdge)
                    if (state == Sunshine.UNKNOWN && upperEdge > 0.0) return SunPeriods.Unknown
                    state == Sunshine.SUN
                }
        // Midway between sample k - 1 and sample k; ZonedDateTime adds seconds on the time-line.
        val boundary =
            if (k == 0L) {
                dayStart
            } else if (k == steps) {
                dayEnd
            } else {
                dayStart.plusSeconds(k * STEP_SECONDS - STEP_SECONDS / 2)
            }
        if (sunny && start == null) {
            start = boundary
        } else if (!sunny && start != null) {
            if (Duration.between(start, boundary) >= MIN_PERIOD) periods += SunPeriod(start, boundary)
            start = null
        }
    }
    return SunPeriods.Known(periods)
}

private const val STEP_SECONDS = 10L
private val MIN_PERIOD = Duration.ofMinutes(1)
