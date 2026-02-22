package com.sunshine.app.domain.model

import java.time.LocalDate

/**
 * Grid of total sun exposure hours for each point, used for heatmap overlay.
 * Each point maps to the number of hours the sun is terrain-visible on the given date.
 */
data class SunExposureGrid(
    val bounds: BoundingBox,
    val resolution: Double,
    val date: LocalDate,
    val points: Map<GeoPoint, Double>,
) {
    /** Maximum exposure hours in the grid, for normalizing the color scale. */
    val maxExposure: Double by lazy {
        points.values.maxOrNull() ?: 0.0
    }

    companion object {
        /** Default time step in minutes for scanning the day. */
        const val DEFAULT_TIME_STEP_MINUTES = 30
    }
}
