package com.sunshine.app.domain.model

/**
 * Represents elevation data along a line from observer toward sun.
 * Used for determining if terrain occludes the sun.
 */
data class TerrainProfile(
    /** The observer's location */
    val observer: GeoPoint,
    /** The observer's elevation in meters */
    val observerElevation: Double,
    /** The direction toward the sun (azimuth in degrees, 0=North) */
    val azimuth: Double,
    /** List of terrain points along the line of sight */
    val points: List<TerrainPoint>,
) {
    /**
     * Calculate the maximum horizon angle from the observer.
     * This is the highest angle at which terrain blocks the view.
     */
    fun calculateHorizonAngle(): Double {
        if (points.isEmpty()) return 0.0

        return points.maxOf { point ->
            point.angleFromObserver(observerElevation)
        }
    }

    /**
     * Check if terrain blocks the sun at the given elevation angle.
     */
    fun blocksSun(sunElevation: Double): Boolean = sunElevation <= calculateHorizonAngle()
}

/**
 * A point along a terrain profile.
 */
data class TerrainPoint(
    /** Distance from observer in meters */
    val distance: Double,
    /** Elevation above sea level in meters */
    val elevation: Double,
) {
    /**
     * Calculate the angle from the observer to this point, correcting for Earth curvature.
     * Positive angle means the point is above the observer's horizon.
     *
     * Earth curvature makes distant terrain appear lower than flat-earth geometry predicts.
     * At 50 km, the drop is ~168 m (with standard atmospheric refraction factor k = 7/6).
     *
     * @param observerElevation The observer's elevation in meters
     * @return Angle in degrees above/below observer's horizon
     */
    fun angleFromObserver(observerElevation: Double): Double {
        if (distance <= 0) return 0.0

        // Earth curvature correction: terrain drops by d²/(2·R·k) where k=7/6
        // accounts for standard atmospheric refraction on the light path to terrain
        val curvatureDrop =
            (distance * distance) / (2.0 * EARTH_RADIUS_METERS * REFRACTION_FACTOR)
        val correctedHeightDiff = elevation - observerElevation - curvatureDrop
        return Math.toDegrees(kotlin.math.atan2(correctedHeightDiff, distance))
    }

    companion object {
        /** Mean Earth radius in meters */
        const val EARTH_RADIUS_METERS = 6_371_000.0

        /** Standard atmospheric refraction factor for terrestrial line-of-sight (k = 7/6) */
        @Suppress("MagicNumber")
        const val REFRACTION_FACTOR = 7.0 / 6.0
    }
}
