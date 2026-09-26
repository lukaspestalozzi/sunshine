package com.sunshine.core

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.shredzone.commons.suncalc.SunTimes

/** The sun is on the same side of the horizon for a whole day. */
enum class WholeDay { ABOVE_HORIZON, BELOW_HORIZON }

/**
 * The sun's events within one local day, from its start at 00:00 up to, but not including, the start
 * of the next day. Astronomical: sea-level observer, no terrain.
 *
 * An event is `null` when it does not occur within the day; it is never replaced by an event of
 * another day. Sunrise and sunset use [SUNRISE_GEOMETRIC_ELEVATION]; civil dawn and dusk use -6°.
 *
 * @property dayLength total time within the day during which the sun is above the horizon.
 * @property wholeDay set only when there is neither sunrise nor sunset within the day.
 */
data class SunDay(
    val civilDawn: ZonedDateTime?,
    val sunrise: ZonedDateTime?,
    val sunset: ZonedDateTime?,
    val civilDusk: ZonedDateTime?,
    val dayLength: Duration,
    val wholeDay: WholeDay?,
)

fun sunDay(
    point: GeoPoint,
    date: LocalDate,
    zone: ZoneId,
): SunDay {
    val start = date.atStartOfDay(zone)
    val end = date.plusDays(1).atStartOfDay(zone)
    val horizon = sunTimes(point, start, end, SunTimes.Twilight.VISUAL)
    val civil = sunTimes(point, start, end, SunTimes.Twilight.CIVIL)
    val wholeDay =
        when {
            horizon.isAlwaysUp -> WholeDay.ABOVE_HORIZON
            horizon.isAlwaysDown -> WholeDay.BELOW_HORIZON
            else -> null
        }
    return SunDay(
        civilDawn = civil.rise,
        sunrise = horizon.rise,
        sunset = horizon.set,
        civilDusk = civil.set,
        dayLength = dayLength(start, end, horizon.rise, horizon.set, wholeDay),
        wholeDay = wholeDay,
    )
}

private fun sunTimes(
    point: GeoPoint,
    start: ZonedDateTime,
    end: ZonedDateTime,
    twilight: SunTimes.Twilight,
): SunTimes =
    SunTimes
        .compute()
        .on(start)
        .at(point.latitude, point.longitude)
        .limit(Duration.between(start, end))
        .twilight(twilight)
        .execute()

private fun dayLength(
    start: ZonedDateTime,
    end: ZonedDateTime,
    sunrise: ZonedDateTime?,
    sunset: ZonedDateTime?,
    wholeDay: WholeDay?,
): Duration =
    when {
        sunrise != null && sunset != null ->
            if (sunrise.isBefore(sunset)) {
                Duration.between(sunrise, sunset)
            } else {
                Duration.between(start, sunset) + Duration.between(sunrise, end)
            }
        sunrise != null -> Duration.between(sunrise, end)
        sunset != null -> Duration.between(start, sunset)
        else ->
            when (checkNotNull(wholeDay) { "No sunrise, no sunset and no whole-day state on ${start.toLocalDate()}" }) {
                WholeDay.ABOVE_HORIZON -> Duration.between(start, end)
                WholeDay.BELOW_HORIZON -> Duration.ZERO
            }
    }
