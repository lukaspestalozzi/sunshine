package com.sunshine.app.ui.screens.map

import com.sunshine.app.domain.model.BoundingBox
import com.sunshine.app.domain.model.GeoPoint
import com.sunshine.app.domain.model.SunPosition
import com.sunshine.app.domain.model.VisibilityGrid
import com.sunshine.app.domain.model.VisibilityResult
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.pow

/**
 * UI state for the Map screen.
 */
data class MapUiState(
    val mapCenter: GeoPoint = GeoPoint.DEFAULT,
    val zoomLevel: Double = DEFAULT_ZOOM,
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedTime: LocalTime = LocalTime.now(),
    val sunPosition: SunPosition? = null,
    val visibility: VisibilityResult? = null,
    val visibilityGrid: VisibilityGrid? = null,
    val sunriseTime: LocalTime? = null,
    val sunsetTime: LocalTime? = null,
    val isLoadingVisibility: Boolean = false,
    val isLoadingGrid: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    /** Whether terrain-aware visibility is available */
    val hasVisibilityData: Boolean get() = visibility != null

    /** Whether sun is visible considering terrain */
    val isSunVisibleWithTerrain: Boolean get() = visibility?.isSunVisible ?: sunPosition?.isAboveHorizon ?: false

    /** Whether grid overlay should be shown */
    val showGridOverlay: Boolean get() = visibilityGrid != null && sunPosition?.isAboveHorizon == true

    /** Calculate visible map bounds based on center and zoom */
    fun getVisibleBounds(): BoundingBox {
        // Approximate degrees visible at current zoom
        // At zoom 10, roughly 0.5 degrees visible; at zoom 15, roughly 0.015 degrees
        val zoomFactor = 2.0.pow(zoomLevel - 10.0)
        val degreesVisible = DEGREES_AT_ZOOM_10 / zoomFactor
        return BoundingBox(
            north = mapCenter.latitude + degreesVisible / 2,
            south = mapCenter.latitude - degreesVisible / 2,
            east = mapCenter.longitude + degreesVisible / 2,
            west = mapCenter.longitude - degreesVisible / 2,
        )
    }

    companion object {
        const val DEFAULT_ZOOM = 10.0
        const val MIN_ZOOM = 5.0
        const val MAX_ZOOM = 18.0

        // Approximate degrees visible at zoom level 10
        private const val DEGREES_AT_ZOOM_10 = 0.5
    }
}
