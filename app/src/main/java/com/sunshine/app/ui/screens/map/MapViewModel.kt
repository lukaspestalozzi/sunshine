package com.sunshine.app.ui.screens.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sunshine.app.domain.model.GeoPoint
import com.sunshine.app.domain.model.VisibilityGrid
import com.sunshine.app.domain.usecase.CalculateSunVisibilityUseCase
import com.sunshine.app.domain.service.SunCalculator
import com.sunshine.app.util.ErrorMessageMapper
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@Suppress("TooManyFunctions") // Heatmap mode adds scheduling/update methods alongside existing ones
class MapViewModel(
    private val sunCalculator: SunCalculator,
    private val visibilityUseCase: CalculateSunVisibilityUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private var sunPositionJob: Job? = null
    private var visibilityJob: Job? = null
    private var gridJob: Job? = null
    private var terrainTimesJob: Job? = null
    private var heatmapJob: Job? = null

    init {
        updateSunPosition()
    }

    fun onDateSelected(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
        updateSunPosition()
        if (_uiState.value.isHeatmapMode) {
            scheduleHeatmapUpdate()
        }
    }

    fun onTimeSelected(time: LocalTime) {
        _uiState.update { it.copy(selectedTime = time) }
        updateSunPosition()
    }

    fun onMapCenterChanged(center: GeoPoint) {
        _uiState.update { it.copy(mapCenter = center) }
        updateSunPosition()
        if (_uiState.value.isHeatmapMode) {
            scheduleHeatmapUpdate()
        }
    }

    fun onZoomChanged(zoom: Double) {
        val clampedZoom = zoom.coerceIn(MapUiState.MIN_ZOOM, MapUiState.MAX_ZOOM)
        _uiState.update { it.copy(zoomLevel = clampedZoom) }
        // Trigger grid update when zoom changes (affects resolution)
        scheduleGridUpdate()
        if (_uiState.value.isHeatmapMode) {
            scheduleHeatmapUpdate()
        }
    }

    fun onResetToNow() {
        _uiState.update {
            it.copy(
                selectedDate = LocalDate.now(),
                selectedTime = LocalTime.now(),
            )
        }
        updateSunPosition()
        if (_uiState.value.isHeatmapMode) {
            scheduleHeatmapUpdate()
        }
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
        if (_uiState.value.isHeatmapMode) {
            scheduleHeatmapUpdate()
        }
    }

    fun onErrorDismissed() {
        _uiState.update { it.copy(error = null) }
    }

    fun onToggleHeatmap() {
        val newMode = !_uiState.value.isHeatmapMode
        _uiState.update {
            it.copy(
                isHeatmapMode = newMode,
                sunExposureGrid = if (newMode) it.sunExposureGrid else null,
            )
        }
        if (newMode) {
            scheduleHeatmapUpdate()
        } else {
            heatmapJob?.cancel()
        }
    }

    @Suppress("TooGenericExceptionCaught") // Calculator may throw various exceptions
    private fun updateSunPosition() {
        sunPositionJob?.cancel()
        sunPositionJob =
            viewModelScope.launch {
                val state = _uiState.value
                val utcDateTime =
                    localToUtc(
                        LocalDateTime.of(state.selectedDate, state.selectedTime),
                    )

                try {
                    val sunPosition =
                        sunCalculator.calculateSunPosition(
                            location = state.mapCenter,
                            dateTime = utcDateTime,
                        )

                    val (sunrise, sunset) = fetchSunriseSunset(state)
                    _uiState.update {
                        it.copy(
                            sunPosition = sunPosition,
                            sunriseTime = sunrise,
                            sunsetTime = sunset,
                            error = null,
                        )
                    }

                    updateVisibility(state.mapCenter, utcDateTime)
                    scheduleGridUpdate()
                    scheduleTerrainTimesUpdate()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Failed to update sun position")
                    _uiState.update { it.copy(error = ErrorMessageMapper.toUserMessage(e)) }
                }
            }
    }

    /**
     * Fetch sunrise/sunset in UTC for the user's local date, then convert to local time.
     * Uses selectedDate (not UTC date) to avoid date-shift at midnight crossings.
     */
    private suspend fun fetchSunriseSunset(state: MapUiState): Pair<LocalTime?, LocalTime?> {
        val sunriseUtc = sunCalculator.calculateSunrise(state.mapCenter, state.selectedDate)
        val sunsetUtc = sunCalculator.calculateSunset(state.mapCenter, state.selectedDate)
        return Pair(
            sunriseUtc?.let { utcTimeToLocal(it, state.selectedDate) },
            sunsetUtc?.let { utcTimeToLocal(it, state.selectedDate) },
        )
    }

    @Suppress("TooGenericExceptionCaught")
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
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Visibility calculation failed")
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

    @Suppress("TooGenericExceptionCaught", "LongMethod")
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
        val dateTime = localToUtc(LocalDateTime.of(state.selectedDate, state.selectedTime))

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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Grid calculation failed")
            _uiState.update {
                it.copy(
                    visibilityGrid = null,
                    isLoadingGrid = false,
                )
            }
        }
    }

    private fun scheduleTerrainTimesUpdate() {
        terrainTimesJob?.cancel()
        terrainTimesJob =
            viewModelScope.launch {
                delay(TERRAIN_TIMES_DEBOUNCE_MS)
                updateTerrainTimes()
            }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun updateTerrainTimes() {
        val state = _uiState.value
        if (state.sunriseTime == null || state.sunsetTime == null) {
            applyTerrainTimes(first = null, last = null)
            return
        }
        _uiState.update { it.copy(isLoadingTerrainTimes = true) }
        try {
            val (firstUtc, lastUtc) = visibilityUseCase
                .calculateTerrainSunriseSunset(state.mapCenter, state.selectedDate)
                .getOrElse { Pair(null, null) }
            applyTerrainTimes(
                first = firstUtc?.let { utcTimeToLocal(it, state.selectedDate) },
                last = lastUtc?.let { utcTimeToLocal(it, state.selectedDate) },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Terrain sunshine times calculation failed")
            applyTerrainTimes(first = null, last = null)
        }
    }

    private fun applyTerrainTimes(first: LocalTime?, last: LocalTime?) {
        _uiState.update {
            it.copy(
                firstSunshineTime = first,
                lastSunshineTime = last,
                isLoadingTerrainTimes = false,
            )
        }
    }

    private fun scheduleHeatmapUpdate() {
        heatmapJob?.cancel()
        heatmapJob =
            viewModelScope.launch {
                delay(HEATMAP_DEBOUNCE_MS)
                updateHeatmap()
            }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun updateHeatmap() {
        val state = _uiState.value
        if (!state.isHeatmapMode) return

        if (state.zoomLevel < MIN_ZOOM_FOR_GRID) {
            _uiState.update { it.copy(sunExposureGrid = null) }
            return
        }

        val bounds = state.getVisibleBounds()
        val resolution = calculateHeatmapResolution(state.zoomLevel)

        _uiState.update { it.copy(isLoadingHeatmap = true) }

        try {
            val grid =
                visibilityUseCase.calculateSunExposureGrid(
                    bounds = bounds,
                    date = state.selectedDate,
                    resolution = resolution,
                ).getOrThrow()

            _uiState.update {
                it.copy(
                    sunExposureGrid = grid,
                    isLoadingHeatmap = false,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Heatmap calculation failed")
            _uiState.update {
                it.copy(
                    sunExposureGrid = null,
                    isLoadingHeatmap = false,
                )
            }
        }
    }

    companion object {
        /**
         * Convert a local-timezone [LocalDateTime] to its UTC equivalent.
         * The NOAA sun calculator assumes UTC input.
         */
        internal fun localToUtc(local: LocalDateTime): LocalDateTime =
            local.atZone(ZoneId.systemDefault())
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime()

        /**
         * Convert a UTC [LocalTime] (e.g. sunrise/sunset from the calculator)
         * back to the device's local timezone for display.
         */
        internal fun utcTimeToLocal(
            utcTime: LocalTime,
            utcDate: LocalDate,
        ): LocalTime =
            LocalDateTime.of(utcDate, utcTime)
                .atZone(ZoneOffset.UTC)
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalTime()

        // Debounce delay for grid calculation
        private const val GRID_DEBOUNCE_MS = 500L

        // Debounce delay for terrain sunshine times (longer since it's expensive)
        private const val TERRAIN_TIMES_DEBOUNCE_MS = 1000L

        // Debounce delay for heatmap (expensive: scans full day per grid point)
        private const val HEATMAP_DEBOUNCE_MS = 1000L

        // Heatmap resolution: coarser than real-time grid because each point scans all day
        private const val HEATMAP_RESOLUTION_HIGH = 0.001
        private const val HEATMAP_RESOLUTION_MEDIUM = 0.002
        private const val HEATMAP_RESOLUTION_LOW = 0.004

        // Minimum zoom level to show grid (avoid too many points)
        private const val MIN_ZOOM_FOR_GRID = 12.0

        // Grid resolution thresholds based on zoom level
        private const val ZOOM_LEVEL_HIGH = 16.0
        private const val ZOOM_LEVEL_MEDIUM = 14.0
        private const val ZOOM_LEVEL_LOW = 12.0
        private const val RESOLUTION_HIGH = 0.0005
        private const val RESOLUTION_MEDIUM = 0.001
        private const val RESOLUTION_LOW = 0.002

        /**
         * Calculate grid resolution based on zoom level.
         * Higher zoom = finer resolution, but limit max points.
         */
        private fun calculateGridResolution(zoomLevel: Double): Double =
            when {
                zoomLevel >= ZOOM_LEVEL_HIGH -> RESOLUTION_HIGH
                zoomLevel >= ZOOM_LEVEL_MEDIUM -> RESOLUTION_MEDIUM
                zoomLevel >= ZOOM_LEVEL_LOW -> RESOLUTION_LOW
                else -> VisibilityGrid.DEFAULT_RESOLUTION
            }

        /**
         * Heatmap resolution is coarser than real-time grid because
         * each point scans visibility across the entire day.
         */
        private fun calculateHeatmapResolution(zoomLevel: Double): Double =
            when {
                zoomLevel >= ZOOM_LEVEL_HIGH -> HEATMAP_RESOLUTION_HIGH
                zoomLevel >= ZOOM_LEVEL_MEDIUM -> HEATMAP_RESOLUTION_MEDIUM
                else -> HEATMAP_RESOLUTION_LOW
            }
    }
}
