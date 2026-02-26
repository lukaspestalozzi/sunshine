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

        /**
         * Create a bounding box that encloses all given points, or null if the collection is empty.
         * Expands by [margin] degrees in each direction to satisfy the north>south / east>west invariants
         * even when all points share the same latitude or longitude.
         */
        @Suppress("MagicNumber") // Default margin is a tiny geographic epsilon
        fun fromPoints(
            points: Collection<GeoPoint>,
            margin: Double = 0.0001,
        ): BoundingBox? {
            if (points.isEmpty()) return null
            var north = Double.NEGATIVE_INFINITY
            var south = Double.POSITIVE_INFINITY
            var east = Double.NEGATIVE_INFINITY
            var west = Double.POSITIVE_INFINITY
            for (point in points) {
                if (point.latitude > north) north = point.latitude
                if (point.latitude < south) south = point.latitude
                if (point.longitude > east) east = point.longitude
                if (point.longitude < west) west = point.longitude
            }
            return BoundingBox(
                north = north + margin,
                south = south - margin,
                east = east + margin,
                west = west - margin,
            )
        }
    }
}
