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
    /** True when elevation data was unavailable and results may be inaccurate */
    val isElevationDegraded: Boolean = false,
) {
    companion object {
        /**
         * Create a result for when sun is visible.
         */
        fun visible(
            location: GeoPoint,
            sunPosition: SunPosition,
            horizonAngle: Double,
            isElevationDegraded: Boolean = false,
        ) = VisibilityResult(
            location = location,
            isSunVisible = true,
            sunPosition = sunPosition,
            horizonAngle = horizonAngle,
            degreesUntilVisible = null,
            nextSunTime = null,
            isElevationDegraded = isElevationDegraded,
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
            isElevationDegraded: Boolean = false,
        ) = VisibilityResult(
            location = location,
            isSunVisible = false,
            sunPosition = sunPosition,
            horizonAngle = horizonAngle,
            degreesUntilVisible = degreesUntilVisible,
            nextSunTime = nextSunTime,
            isElevationDegraded = isElevationDegraded,
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
            isElevationDegraded = false,
        )
    }
}

/**
 * Integer grid index to avoid floating-point equality issues in map keys.
 */
data class GridIndex(val row: Int, val col: Int)

/**
 * Grid of visibility results for rendering as overlay.
 */
data class VisibilityGrid(
    /** Bounding box of the grid */
    val bounds: BoundingBox,
    /** Grid resolution in degrees */
    val resolution: Double,
    /** Map of grid points to visibility status (keyed by GeoPoint for rendering) */
    val points: Map<GeoPoint, Boolean>,
) {
    /** Internal index-based lookup for reliable point matching */
    private val indexedPoints: Map<GridIndex, Boolean> by lazy {
        points.entries.associate { (point, visible) ->
            toGridIndex(point) to visible
        }
    }

    /**
     * Get visibility at the nearest grid point.
     */
    fun getVisibilityAt(point: GeoPoint): Boolean? = indexedPoints[toGridIndex(point)]

    private fun toGridIndex(point: GeoPoint): GridIndex {
        val row = kotlin.math.round((point.latitude - bounds.south) / resolution).toInt()
        val col = kotlin.math.round((point.longitude - bounds.west) / resolution).toInt()
        return GridIndex(row, col)
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
