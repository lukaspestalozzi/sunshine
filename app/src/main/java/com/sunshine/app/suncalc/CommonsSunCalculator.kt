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
 * Sun calculator implementation using the commons-suncalc library.
 * Version is managed in `gradle/libs.versions.toml` (key: `commonsSuncalc`).
 *
 * Uses [LibSunPosition.getTrueAltitude] (geometric elevation without refraction)
 * because [com.sunshine.app.domain.usecase.CalculateSunVisibilityUseCase] applies
 * its own Meeus/Bennett refraction correction.
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
            elevation = position.trueAltitude,
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
