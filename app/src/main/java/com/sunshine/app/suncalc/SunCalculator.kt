package com.sunshine.app.suncalc

import com.sunshine.app.domain.model.GeoPoint
import com.sunshine.app.domain.model.SunPosition
import java.time.LocalDateTime

/**
 * Interface for calculating sun position.
 * Designed as a pluggable component to allow different implementations.
 */
interface SunCalculator {

    /**
     * Calculate the sun's position (azimuth and elevation) for a given location and time.
     *
     * @param location The geographic location
     * @param dateTime The date and time
     * @return The sun's position in the sky
     */
    suspend fun calculateSunPosition(
        location: GeoPoint,
        dateTime: LocalDateTime,
    ): SunPosition

    /**
     * Calculate sunrise time for a given location and date.
     *
     * @param location The geographic location
     * @param date The date
     * @return The sunrise time, or null if the sun doesn't rise (polar regions)
     */
    suspend fun calculateSunrise(
        location: GeoPoint,
        date: java.time.LocalDate,
    ): java.time.LocalTime?

    /**
     * Calculate sunset time for a given location and date.
     *
     * @param location The geographic location
     * @param date The date
     * @return The sunset time, or null if the sun doesn't set (polar regions)
     */
    suspend fun calculateSunset(
        location: GeoPoint,
        date: java.time.LocalDate,
    ): java.time.LocalTime?
}
