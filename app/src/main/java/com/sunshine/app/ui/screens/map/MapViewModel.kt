package com.sunshine.app.ui.screens.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sunshine.app.domain.model.GeoPoint
import com.sunshine.app.domain.model.VisibilityGrid
import com.sunshine.app.domain.usecase.CalculateSunVisibilityUseCase
import com.sunshine.app.suncalc.SunCalculator
import com.sunshine.app.util.ErrorMessageMapper
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MapViewModel(
    private val sunCalculator: SunCalculator,
    private val visibilityUseCase: CalculateSunVisibilityUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private var visibilityJob: Job? = null
    private var gridJob: Job? = null

    init {
        updateSunPosition()
    }

    fun onDateSelected(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
        updateSunPosition()
    }

    fun onTimeSelected(time: LocalTime) {
        _uiState.update { it.copy(selectedTime = time) }
        updateSunPosition()
    }

    fun onMapCenterChanged(center: GeoPoint) {
        _uiState.update { it.copy(mapCenter = center) }
        updateSunPosition()
    }

    fun onZoomChanged(zoom: Double) {
        val clampedZoom = zoom.coerceIn(MapUiState.MIN_ZOOM, MapUiState.MAX_ZOOM)
        _uiState.update { it.copy(zoomLevel = clampedZoom) }
        // Trigger grid update when zoom changes (affects resolution)
        scheduleGridUpdate()
    }

    fun onResetToNow() {
        _uiState.update {
            it.copy(
                selectedDate = LocalDate.now(),
                selectedTime = LocalTime.now(),
            )
        }
        updateSunPosition()
    }

    fun onAdjustTime(hours: Int) {
        _uiState.update { state ->
            val currentDateTime = LocalDateTime.of(state.selectedDate, state.selectedTime)
            val adjustedDateTime = currentDateTime.plusHours(hours.toLong())
            state.copy(
                selectedDate = adjustedDateTime.toLocalDate(),
                selectedTime = adjustedDateTime.toLocalTime(),
            )
        }
        updateSunPosition()
    }

    fun onErrorDismissed() {
        _uiState.update { it.copy(error = null) }
    }

    @Suppress("TooGenericExceptionCaught") // Calculator may throw various exceptions
    private fun updateSunPosition() {
        viewModelScope.launch {
            val state = _uiState.value
            val dateTime = LocalDateTime.of(state.selectedDate, state.selectedTime)

            try {
                val sunPosition =
                    sunCalculator.calculateSunPosition(
                        location = state.mapCenter,
                        dateTime = dateTime,
                    )

                // Calculate sunrise and sunset times
                val sunriseTime = sunCalculator.calculateSunrise(state.mapCenter, state.selectedDate)
                val sunsetTime = sunCalculator.calculateSunset(state.mapCenter, state.selectedDate)

                _uiState.update {
                    it.copy(
                        sunPosition = sunPosition,
                        sunriseTime = sunriseTime,
                        sunsetTime = sunsetTime,
                        error = null,
                    )
                }

                // Start visibility calculation (non-blocking)
                updateVisibility(state.mapCenter, dateTime)

                // Schedule grid update with debouncing
                scheduleGridUpdate()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = ErrorMessageMapper.toUserMessage(e)) }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun updateVisibility(
        location: GeoPoint,
        dateTime: LocalDateTime,
    ) {
        // Cancel any ongoing visibility calculation
        visibilityJob?.cancel()

        visibilityJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isLoadingVisibility = true) }

                try {
                    val visibility =
                        visibilityUseCase.calculateVisibility(location, dateTime)
                            .getOrNull()

                    _uiState.update {
                        it.copy(
                            visibility = visibility,
                            isLoadingVisibility = false,
                        )
                    }
                } catch (e: Exception) {
                    // Visibility calculation failure is not critical
                    // We still have basic sun position
                    _uiState.update { it.copy(isLoadingVisibility = false) }
                }
            }
    }

    private fun scheduleGridUpdate() {
        // Cancel any pending grid calculation
        gridJob?.cancel()

        gridJob =
            viewModelScope.launch {
                // Debounce: wait for user to stop interacting
                delay(GRID_DEBOUNCE_MS)
                updateVisibilityGrid()
            }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun updateVisibilityGrid() {
        val state = _uiState.value

        // Only calculate grid if sun is above horizon
        if (state.sunPosition?.isAboveHorizon != true) {
            _uiState.update { it.copy(visibilityGrid = null) }
            return
        }

        // Skip grid calculation at low zoom levels (too many points)
        if (state.zoomLevel < MIN_ZOOM_FOR_GRID) {
            _uiState.update { it.copy(visibilityGrid = null) }
            return
        }

        val bounds = state.getVisibleBounds()
        val dateTime = LocalDateTime.of(state.selectedDate, state.selectedTime)

        // Adjust resolution based on zoom level for performance
        val resolution = calculateGridResolution(state.zoomLevel)

        _uiState.update { it.copy(isLoadingGrid = true) }

        try {
            val grid =
                visibilityUseCase.calculateVisibilityGrid(bounds, dateTime, resolution)
                    .getOrNull()

            _uiState.update {
                it.copy(
                    visibilityGrid = grid,
                    isLoadingGrid = false,
                )
            }
        } catch (e: Exception) {
            // Grid calculation failure is not critical
            _uiState.update {
                it.copy(
                    visibilityGrid = null,
                    isLoadingGrid = false,
                )
            }
        }
    }

    companion object {
        // Debounce delay for grid calculation
        private const val GRID_DEBOUNCE_MS = 500L

        // Minimum zoom level to show grid (avoid too many points)
        private const val MIN_ZOOM_FOR_GRID = 12.0

        // Maximum grid points to calculate
        private const val MAX_GRID_POINTS = 400

        /**
         * Calculate grid resolution based on zoom level.
         * Higher zoom = finer resolution, but limit max points.
         */
        private fun calculateGridResolution(zoomLevel: Double): Double {
            // At zoom 12: ~0.005 (roughly 500m)
            // At zoom 15: ~0.001 (roughly 100m)
            // At zoom 18: ~0.0002 (roughly 20m)
            return when {
                zoomLevel >= 16 -> 0.0005
                zoomLevel >= 14 -> 0.001
                zoomLevel >= 12 -> 0.002
                else -> VisibilityGrid.DEFAULT_RESOLUTION
            }
        }
    }
}
