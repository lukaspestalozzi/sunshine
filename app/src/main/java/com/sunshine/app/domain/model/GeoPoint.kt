package com.sunshine.app.domain.model

/**
 * Represents a geographic point with latitude and longitude.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude in MIN_LATITUDE..MAX_LATITUDE) { "Latitude must be between -90 and 90" }
        require(longitude in MIN_LONGITUDE..MAX_LONGITUDE) { "Longitude must be between -180 and 180" }
    }

    companion object {
        private const val MIN_LATITUDE = -90.0
        private const val MAX_LATITUDE = 90.0
        private const val MIN_LONGITUDE = -180.0
        private const val MAX_LONGITUDE = 180.0

        /** Default location: Swiss Alps (Bern) */
        @Suppress("MagicNumber") // Well-known geographic coordinates
        val DEFAULT = GeoPoint(latitude = 46.8182, longitude = 8.2275)
    }
}
