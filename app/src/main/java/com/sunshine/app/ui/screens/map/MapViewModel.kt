package com.sunshine.app.ui.screens.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sunshine.app.domain.model.GeoPoint
import com.sunshine.app.domain.usecase.CalculateSunVisibilityUseCase
import com.sunshine.app.suncalc.SunCalculator
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.Job
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
                _uiState.update { it.copy(sunPosition = sunPosition, error = null) }

                // Start visibility calculation (non-blocking)
                updateVisibility(state.mapCenter, dateTime)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to calculate sun position") }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // Non-critical; we continue with basic sun position
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
}
