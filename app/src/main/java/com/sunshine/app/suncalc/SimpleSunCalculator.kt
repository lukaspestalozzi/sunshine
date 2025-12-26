package com.sunshine.app.suncalc

import com.sunshine.app.domain.model.GeoPoint
import com.sunshine.app.domain.model.SunPosition
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Simple sun position calculator using standard astronomical algorithms.
 *
 * This is a basic implementation based on NOAA solar calculations.
 * For production use, consider using a well-tested library or the PeakFinder API.
 *
 * Reference: https://gml.noaa.gov/grad/solcalc/solareqns.PDF
 */
class SimpleSunCalculator : SunCalculator {

    override suspend fun calculateSunPosition(
        location: GeoPoint,
        dateTime: LocalDateTime,
    ): SunPosition {
        val julianDate = toJulianDate(dateTime)
        val julianCentury = (julianDate - 2451545.0) / 36525.0

        // Calculate solar coordinates
        val geomMeanLongSun = (280.46646 + julianCentury * (36000.76983 + 0.0003032 * julianCentury)) % 360
        val geomMeanAnomSun = 357.52911 + julianCentury * (35999.05029 - 0.0001537 * julianCentury)
        val eccentEarthOrbit = 0.016708634 - julianCentury * (0.000042037 + 0.0000001267 * julianCentury)

        val sunEqOfCtr = sin(geomMeanAnomSun.toRadians()) * (1.914602 - julianCentury * (0.004817 + 0.000014 * julianCentury)) +
            sin((2 * geomMeanAnomSun).toRadians()) * (0.019993 - 0.000101 * julianCentury) +
            sin((3 * geomMeanAnomSun).toRadians()) * 0.000289

        val sunTrueLong = geomMeanLongSun + sunEqOfCtr
        val sunAppLong = sunTrueLong - 0.00569 - 0.00478 * sin((125.04 - 1934.136 * julianCentury).toRadians())

        val meanObliqEcliptic = 23 + (26 + ((21.448 - julianCentury * (46.8150 + julianCentury * (0.00059 - julianCentury * 0.001813)))) / 60) / 60
        val obliqCorr = meanObliqEcliptic + 0.00256 * cos((125.04 - 1934.136 * julianCentury).toRadians())

        val sunDeclination = asin(sin(obliqCorr.toRadians()) * sin(sunAppLong.toRadians())).toDegrees()

        val varY = tan((obliqCorr / 2).toRadians()) * tan((obliqCorr / 2).toRadians())
        val eqOfTime = 4 * (varY * sin(2 * geomMeanLongSun.toRadians()) -
            2 * eccentEarthOrbit * sin(geomMeanAnomSun.toRadians()) +
            4 * eccentEarthOrbit * varY * sin(geomMeanAnomSun.toRadians()) * cos(2 * geomMeanLongSun.toRadians()) -
            0.5 * varY * varY * sin(4 * geomMeanLongSun.toRadians()) -
            1.25 * eccentEarthOrbit * eccentEarthOrbit * sin(2 * geomMeanAnomSun.toRadians())).toDegrees()

        // Calculate hour angle
        val timeOffset = eqOfTime + 4 * location.longitude
        val trueSolarTime = (dateTime.hour * 60 + dateTime.minute + dateTime.second / 60.0 + timeOffset) % 1440
        val hourAngle = if (trueSolarTime / 4 < 0) trueSolarTime / 4 + 180 else trueSolarTime / 4 - 180

        // Calculate elevation and azimuth
        val zenith = acos(
            sin(location.latitude.toRadians()) * sin(sunDeclination.toRadians()) +
                cos(location.latitude.toRadians()) * cos(sunDeclination.toRadians()) * cos(hourAngle.toRadians()),
        ).toDegrees()
        val elevation = 90 - zenith

        val azimuthDenom = cos(location.latitude.toRadians()) * sin(zenith.toRadians())
        var azimuth = if (azimuthDenom.absoluteValue() > 0.001) {
            val azimuthRad = acos(
                ((sin(location.latitude.toRadians()) * cos(zenith.toRadians())) - sin(sunDeclination.toRadians())) / azimuthDenom,
            )
            if (hourAngle > 0) {
                (azimuthRad.toDegrees() + 180) % 360
            } else {
                (540 - azimuthRad.toDegrees()) % 360
            }
        } else {
            0.0
        }

        return SunPosition(azimuth = azimuth, elevation = elevation)
    }

    override suspend fun calculateSunrise(
        location: GeoPoint,
        date: LocalDate,
    ): LocalTime? {
        return calculateSunEvent(location, date, isSunrise = true)
    }

    override suspend fun calculateSunset(
        location: GeoPoint,
        date: LocalDate,
    ): LocalTime? {
        return calculateSunEvent(location, date, isSunrise = false)
    }

    private fun calculateSunEvent(
        location: GeoPoint,
        date: LocalDate,
        isSunrise: Boolean,
    ): LocalTime? {
        // Simplified calculation - binary search for the actual time
        var low = 0
        var high = 720 // minutes from midnight to noon for sunrise, noon to midnight for sunset
        val startMinute = if (isSunrise) 0 else 720

        repeat(20) { // Binary search iterations
            val mid = (low + high) / 2
            val testTime = LocalDateTime.of(date, LocalTime.of((startMinute + mid) / 60, (startMinute + mid) % 60))
            val position = kotlinx.coroutines.runBlocking { calculateSunPosition(location, testTime) }

            if (isSunrise) {
                if (position.elevation < 0) low = mid else high = mid
            } else {
                if (position.elevation > 0) low = mid else high = mid
            }
        }

        val resultMinutes = startMinute + (low + high) / 2
        return if (resultMinutes in 0..1439) {
            LocalTime.of(resultMinutes / 60, resultMinutes % 60)
        } else {
            null
        }
    }

    private fun toJulianDate(dateTime: LocalDateTime): Double {
        val epochSeconds = dateTime.toEpochSecond(ZoneOffset.UTC)
        return epochSeconds / 86400.0 + 2440587.5
    }

    private fun Double.toRadians(): Double = this * PI / 180
    private fun Double.toDegrees(): Double = this * 180 / PI
    private fun Double.absoluteValue(): Double = if (this < 0) -this else this
}
