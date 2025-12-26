package com.sunshine.app.domain.model

/**
 * Represents a geographic point with latitude and longitude.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90" }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180" }
    }

    companion object {
        /** Default location: Swiss Alps (Bern) */
        val DEFAULT = GeoPoint(latitude = 46.8182, longitude = 8.2275)
    }
}
