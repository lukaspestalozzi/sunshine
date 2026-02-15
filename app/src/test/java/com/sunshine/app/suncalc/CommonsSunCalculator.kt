package com.sunshine.app.suncalc

import com.sunshine.app.domain.model.GeoPoint
import com.sunshine.app.domain.model.SunPosition
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import org.shredzone.commons.suncalc.SunPosition as LibSunPosition
import org.shredzone.commons.suncalc.SunTimes

/**
 * Sun calculator implementation using the commons-suncalc library (v3.11).
 *
 * Uses well-tested astronomical algorithms from the library as an alternative
 * to the custom NOAA implementation in [SimpleSunCalculator].
 */
class CommonsSunCalculator : SunCalculator {
    override suspend fun calculateSunPosition(
        location: GeoPoint,
        dateTime: LocalDateTime,
    ): SunPosition {
        val zonedDateTime = dateTime.atZone(ZoneOffset.UTC)

        val position =
            LibSunPosition.compute()
                .on(zonedDateTime)
                .at(location.latitude, location.longitude)
                .execute()

        return SunPosition(
            azimuth = position.azimuth,
            elevation = position.altitude,
        )
    }

    override suspend fun calculateSunrise(
        location: GeoPoint,
        date: LocalDate,
    ): LocalTime? {
        val zonedDateTime = date.atStartOfDay(ZoneOffset.UTC)

        val times =
            SunTimes.compute()
                .on(zonedDateTime)
                .at(location.latitude, location.longitude)
                .execute()

        return times.rise?.withZoneSameInstant(ZoneOffset.UTC)?.toLocalTime()
    }

    override suspend fun calculateSunset(
        location: GeoPoint,
        date: LocalDate,
    ): LocalTime? {
        val zonedDateTime = date.atStartOfDay(ZoneOffset.UTC)

        val times =
            SunTimes.compute()
                .on(zonedDateTime)
                .at(location.latitude, location.longitude)
                .execute()

        return times.set?.withZoneSameInstant(ZoneOffset.UTC)?.toLocalTime()
    }
}
