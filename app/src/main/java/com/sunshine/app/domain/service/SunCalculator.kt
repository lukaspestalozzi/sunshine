package com.sunshine.app.domain.service

import com.sunshine.app.domain.model.GeoPoint
import com.sunshine.app.domain.model.SunPosition
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Interface for calculating sun position.
 * Designed as a pluggable component to allow different implementations.
 */
interface SunCalculator {
    /**
     * Calculate the sun's position (azimuth and elevation) for a given location and time.
     *
     * @param location The geographic location
     * @param dateTime The date and time in UTC
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
     * @return The sunrise time in UTC, or null if the sun doesn't rise (polar regions)
     */
    suspend fun calculateSunrise(
        location: GeoPoint,
        date: LocalDate,
    ): LocalTime?

    /**
     * Calculate sunset time for a given location and date.
     *
     * @param location The geographic location
     * @param date The date
     * @return The sunset time in UTC, or null if the sun doesn't set (polar regions)
     */
    suspend fun calculateSunset(
        location: GeoPoint,
        date: LocalDate,
    ): LocalTime?
}
