package com.sunshine.app.domain.model

import java.time.LocalTime

/**
 * Result of sun visibility calculation at a specific point.
 */
data class VisibilityResult(
    /** The location being checked */
    val location: GeoPoint,
    /** Whether the sun is currently visible (not blocked by terrain) */
    val isSunVisible: Boolean,
    /** Sun position at this location and time */
    val sunPosition: SunPosition,
    /** Horizon angle in the sun's direction (degrees above horizontal) */
    val horizonAngle: Double,
    /** If sun is blocked, how many degrees until it clears the terrain */
    val degreesUntilVisible: Double?,
    /** Estimated time when sun becomes visible (if currently blocked) */
    val nextSunTime: LocalTime?,
) {
    companion object {
        /**
         * Create a result for when sun is visible.
         */
        fun visible(
            location: GeoPoint,
            sunPosition: SunPosition,
            horizonAngle: Double,
        ) = VisibilityResult(
            location = location,
            isSunVisible = true,
            sunPosition = sunPosition,
            horizonAngle = horizonAngle,
            degreesUntilVisible = null,
            nextSunTime = null,
        )

        /**
         * Create a result for when sun is blocked by terrain.
         */
        fun blocked(
            location: GeoPoint,
            sunPosition: SunPosition,
            horizonAngle: Double,
            degreesUntilVisible: Double,
            nextSunTime: LocalTime? = null,
        ) = VisibilityResult(
            location = location,
            isSunVisible = false,
            sunPosition = sunPosition,
            horizonAngle = horizonAngle,
            degreesUntilVisible = degreesUntilVisible,
            nextSunTime = nextSunTime,
        )

        /**
         * Create a result for when sun is below horizon (night).
         */
        fun belowHorizon(
            location: GeoPoint,
            sunPosition: SunPosition,
        ) = VisibilityResult(
            location = location,
            isSunVisible = false,
            sunPosition = sunPosition,
            horizonAngle = 0.0,
            degreesUntilVisible = null,
            nextSunTime = null,
        )
    }
}

/**
 * Grid of visibility results for rendering as overlay.
 */
data class VisibilityGrid(
    /** Bounding box of the grid */
    val bounds: BoundingBox,
    /** Grid resolution in degrees */
    val resolution: Double,
    /** Map of grid points to visibility status */
    val points: Map<GeoPoint, Boolean>,
) {
    /**
     * Get visibility at the nearest grid point.
     */
    fun getVisibilityAt(point: GeoPoint): Boolean? {
        // Find nearest grid point
        val nearestLat = roundToGrid(point.latitude)
        val nearestLon = roundToGrid(point.longitude)
        val nearestPoint = GeoPoint(nearestLat, nearestLon)
        return points[nearestPoint]
    }

    private fun roundToGrid(value: Double): Double {
        val factor = 1.0 / resolution
        return kotlin.math.round(value * factor) / factor
    }

    companion object {
        /** Create an empty grid. */
        fun empty(bounds: BoundingBox) =
            VisibilityGrid(
                bounds = bounds,
                resolution = DEFAULT_RESOLUTION,
                points = emptyMap(),
            )

        // Default resolution for visibility grid (~100m)
        const val DEFAULT_RESOLUTION = 0.001
    }
}
