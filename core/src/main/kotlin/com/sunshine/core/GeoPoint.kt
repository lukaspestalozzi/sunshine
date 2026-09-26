package com.sunshine.core

/** A WGS84 position in decimal degrees. NaN and infinite values fail the range checks. */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude must be within [-90, 90], was $latitude" }
        require(longitude in -180.0..180.0) { "Longitude must be within [-180, 180], was $longitude" }
    }
}

/** Where the map starts: the Swiss Alps. */
val DEFAULT_LOCATION = GeoPoint(latitude = 46.8182, longitude = 8.2275)
