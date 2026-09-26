package com.sunshine.core

import java.time.Instant
import org.shredzone.commons.suncalc.SunPosition as SunCalcPosition

/**
 * Where the sun's centre is, seen from a sea-level observer, ignoring terrain.
 *
 * @property azimuth compass direction in degrees, 0 = north, clockwise, in [0, 360).
 * @property elevation apparent elevation above the astronomical horizon in degrees. Standard
 *   refraction is included only while the geometric elevation is above 0°; at or below 0° this is
 *   the geometric elevation (design D2 of add-sun-position).
 * @property isAboveHorizon whether the geometric elevation is at least [SUNRISE_GEOMETRIC_ELEVATION],
 *   the criterion that defines sunrise and sunset.
 */
data class SunPosition(
    val azimuth: Double,
    val elevation: Double,
    val isAboveHorizon: Boolean,
)

/** Geometric elevation of the sun's centre at sunrise and sunset: upper edge on the horizon, standard refraction. */
const val SUNRISE_GEOMETRIC_ELEVATION = -0.833

fun sunPosition(
    point: GeoPoint,
    instant: Instant,
): SunPosition {
    val position =
        SunCalcPosition
            .compute()
            .on(instant)
            .at(point.latitude, point.longitude)
            .execute()
    return SunPosition(
        azimuth = position.azimuth,
        elevation = position.altitude,
        isAboveHorizon = position.trueAltitude >= SUNRISE_GEOMETRIC_ELEVATION,
    )
}
