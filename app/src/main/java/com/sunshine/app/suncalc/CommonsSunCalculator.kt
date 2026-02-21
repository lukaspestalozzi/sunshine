package com.sunshine.app.suncalc

import com.sunshine.app.domain.model.GeoPoint
import com.sunshine.app.domain.model.SunPosition
import com.sunshine.app.domain.service.SunCalculator
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
        // Use noon UTC as the anchor to avoid day-boundary edge cases.
        // SunTimes searches forward from the anchor, so noon ensures we find
        // the sunrise/sunset for the correct calendar day regardless of timezone offset.
        val zonedDateTime = date.atTime(NOON_HOUR, 0).atZone(ZoneOffset.UTC)

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
        val zonedDateTime = date.atTime(NOON_HOUR, 0).atZone(ZoneOffset.UTC)

        val times =
            SunTimes.compute()
                .on(zonedDateTime)
                .at(location.latitude, location.longitude)
                .execute()

        return times.set?.withZoneSameInstant(ZoneOffset.UTC)?.toLocalTime()
    }

    companion object {
        private const val NOON_HOUR = 12
    }
}
