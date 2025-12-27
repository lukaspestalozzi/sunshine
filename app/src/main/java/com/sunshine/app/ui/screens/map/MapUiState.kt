package com.sunshine.app.ui.screens.map

import com.sunshine.app.domain.model.GeoPoint
import com.sunshine.app.domain.model.SunPosition
import com.sunshine.app.domain.model.VisibilityResult
import java.time.LocalDate
import java.time.LocalTime

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
    val isLoadingVisibility: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    /** Whether terrain-aware visibility is available */
    val hasVisibilityData: Boolean get() = visibility != null

    /** Whether sun is visible considering terrain */
    val isSunVisibleWithTerrain: Boolean get() = visibility?.isSunVisible ?: sunPosition?.isAboveHorizon ?: false

    companion object {
        const val DEFAULT_ZOOM = 10.0
        const val MIN_ZOOM = 5.0
        const val MAX_ZOOM = 18.0
    }
}
