package com.sunshine.app.domain.model

/**
 * Represents a geographic bounding box.
 */
data class BoundingBox(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double,
) {
    init {
        require(north > south) { "North must be greater than south" }
        require(east > west) { "East must be greater than west" }
    }

    val center: GeoPoint
        get() =
            GeoPoint(
                latitude = (north + south) / 2,
                longitude = (east + west) / 2,
            )

    fun contains(point: GeoPoint): Boolean = point.latitude in south..north && point.longitude in west..east

    companion object {
        /** Swiss Alps bounding box */
        val SWISS_ALPS =
            BoundingBox(
                north = 47.8,
                south = 45.8,
                east = 10.5,
                west = 5.9,
            )
    }
}
