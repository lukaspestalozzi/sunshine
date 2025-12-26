package com.sunshine.app.domain.model

/**
 * Represents the position of the sun in the sky.
 *
 * @property azimuth Compass direction of the sun (0° = North, 90° = East, 180° = South, 270° = West)
 * @property elevation Angle above the horizon (-90° to 90°, negative = below horizon)
 */
data class SunPosition(
    val azimuth: Double,
    val elevation: Double,
) {
    /** Whether the sun is above the horizon (not accounting for terrain) */
    val isAboveHorizon: Boolean get() = elevation > 0

    companion object {
        val BELOW_HORIZON = SunPosition(azimuth = 0.0, elevation = -90.0)
    }
}
