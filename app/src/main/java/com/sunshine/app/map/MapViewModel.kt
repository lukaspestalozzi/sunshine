package com.sunshine.app.map

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sunshine.app.elevation.Elevation
import com.sunshine.app.elevation.ElevationRepository
import com.sunshine.core.DEFAULT_LOCATION
import com.sunshine.core.GeoPoint
import com.sunshine.core.HorizonProfile
import com.sunshine.core.SunDay
import com.sunshine.core.SunPeriods
import com.sunshine.core.SunPosition
import com.sunshine.core.Sunshine
import com.sunshine.core.sunDay
import com.sunshine.core.sunPeriods
import com.sunshine.core.sunPosition
import com.sunshine.core.sunshineAt
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CameraState(
    val center: GeoPoint,
    val zoom: Double,
)

/** The sun at the selected location: its position at the selected time and the selected day's events. */
data class SunInfo(
    val position: SunPosition,
    val day: SunDay,
)

/** Elevation of the selected location as shown on screen. */
sealed interface ElevationState {
    data object Loading : ElevationState

    data class Known(
        val metres: Double,
    ) : ElevationState

    data object Unknown : ElevationState
}

/** Terrain-aware sunshine at the selected location as shown on screen (point-sunshine spec). */
sealed interface SunshineUiState {
    /** The horizon is being computed. */
    data object Loading : SunshineUiState

    /** The sun periods of the selected day and the sunshine state at the selected time. */
    data class Ready(
        val periods: SunPeriods,
        val atSelectedTime: Sunshine,
    ) : SunshineUiState
}

/**
 * State of the map screen. Times are in the zone of [clock] as it is when the view model is created
 * (the device time zone in production).
 */
class MapViewModel(
    private val savedState: SavedStateHandle,
    isOnline: Flow<Boolean>,
    private val clock: Clock,
    private val elevationRepository: ElevationRepository,
    private val horizonProfile: suspend (GeoPoint) -> HorizonProfile?,
    computeDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val zone: ZoneId = clock.zone

    private val mutableCamera = MutableStateFlow(restoreCamera())
    val camera: StateFlow<CameraState> = mutableCamera.asStateFlow()

    private val mutableSelectedTime = MutableStateFlow(restoreSelectedTime())
    val selectedTime: StateFlow<ZonedDateTime> = mutableSelectedTime.asStateFlow()

    // Off the main thread; conflate() drops inputs that arrive while a computation runs, so a fast
    // pan never queues work. `null` only before the first result.
    val sun: StateFlow<SunInfo?> =
        combine(camera, selectedTime) { camera, time -> camera.center to time }
            .conflate()
            .map { (point, time) -> SunInfo(sunPosition(point, time.toInstant()), sunDay(point, time.toLocalDate(), zone)) }
            .flowOn(computeDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), initialValue = null)

    // Re-evaluated when the location changes (not on zoom alone) or the network returns, which may
    // load an unknown elevation. Latest wins: a new input cancels the previous lookup, including its
    // HTTP call, and waits for it to stop, so a previous location's value is never shown for the new
    // one. Hand-written instead of mapLatest, which is experimental.
    val elevation: StateFlow<ElevationState> =
        channelFlow {
            var lookup: Job? = null
            combine(camera.map { it.center }.distinctUntilChanged(), isOnline) { point, _ -> point }
                .collect { point ->
                    lookup?.cancelAndJoin()
                    lookup =
                        launch {
                            val cached = elevationRepository.cachedElevation(point)
                            if (cached != null) {
                                send(cached.toState())
                            } else {
                                send(ElevationState.Loading)
                                send(elevationRepository.elevation(point).toState())
                            }
                        }
                }
        }.flowOn(computeDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), initialValue = ElevationState.Loading)

    // The horizon of the camera centre, recomputed when the centre changes or the network returns
    // after an incomplete result. Latest wins, as for elevation. The camera must rest for
    // [SETTLE_MILLIS] first, so that panning starts no downloads (design D8).
    private val horizon: Flow<HorizonState> =
        channelFlow {
            var lookup: Job? = null
            val done = AtomicReference<HorizonState.Computed?>(null)
            combine(camera.map { it.center }.distinctUntilChanged(), isOnline) { point, online -> point to online }
                .collect { (point, online) ->
                    val previous = done.get()
                    if (previous?.point == point && (!online || previous.isComplete)) return@collect
                    lookup?.cancelAndJoin()
                    done.set(null)
                    lookup =
                        launch {
                            send(HorizonState.Loading)
                            delay(SETTLE_MILLIS)
                            val computed = HorizonState.Computed(point, horizonProfile(point))
                            done.set(computed)
                            send(computed)
                        }
                }
        }

    // Periods depend on the date only, so a new time of day costs one sun position (design D8).
    private var periodsOfDay: Triple<HorizonState.Computed, LocalDate, SunPeriods>? = null

    val sunshine: StateFlow<SunshineUiState> =
        combine(horizon, selectedTime) { horizon, time -> horizon to time }
            .conflate()
            .map { (horizon, time) -> if (horizon is HorizonState.Computed) sunshineState(horizon, time) else SunshineUiState.Loading }
            .flowOn(computeDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), initialValue = SunshineUiState.Loading)

    // The monitor emits the current state as soon as it is collected; `false` only covers the
    // moment before that first emission.
    val isOffline: StateFlow<Boolean> =
        isOnline
            .map { online -> !online }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), initialValue = false)

    /** Called on every camera movement; saved so the viewport survives rotation and process death. */
    fun onCameraMoved(camera: CameraState) {
        mutableCamera.value = camera
        savedState[KEY_LATITUDE] = camera.center.latitude
        savedState[KEY_LONGITUDE] = camera.center.longitude
        savedState[KEY_ZOOM] = camera.zoom
    }

    /** Keeps the wall-clock time of day (design D3). */
    fun onDateSelected(date: LocalDate) = select(selectedTime.value.withDate(date))

    /** [minutes] since the start of the selected day, snapped to the 5-minute grid. */
    fun onSliderMoved(minutes: Float) = select(sliderTime(selectedTime.value.toLocalDate(), zone, minutes))

    /** Sets the current time once; the selected time does not follow the clock. */
    fun onNowClicked() = select(now())

    // Saved so the selected time survives rotation and process death; a new launch starts at now.
    private fun select(time: ZonedDateTime) {
        mutableSelectedTime.value = time
        savedState[KEY_SELECTED_TIME] = time.toInstant().toEpochMilli()
    }

    private fun now(): ZonedDateTime = Instant.now(clock).truncatedTo(ChronoUnit.MINUTES).atZone(zone)

    private fun restoreSelectedTime(): ZonedDateTime =
        savedState.get<Long>(KEY_SELECTED_TIME)?.let { Instant.ofEpochMilli(it).atZone(zone) } ?: now()

    private fun restoreCamera(): CameraState {
        val latitude = savedState.get<Double>(KEY_LATITUDE)
        val longitude = savedState.get<Double>(KEY_LONGITUDE)
        val zoom = savedState.get<Double>(KEY_ZOOM)
        if (latitude == null || longitude == null || zoom == null) {
            return CameraState(center = DEFAULT_LOCATION, zoom = DEFAULT_ZOOM)
        }
        return CameraState(center = GeoPoint(latitude, longitude), zoom = zoom)
    }

    private fun sunshineState(
        horizon: HorizonState.Computed,
        time: ZonedDateTime,
    ): SunshineUiState {
        val profile = horizon.profile ?: return SunshineUiState.Ready(SunPeriods.Unknown, Sunshine.UNKNOWN)
        val date = time.toLocalDate()
        val memo = periodsOfDay
        val periods =
            if (memo != null && memo.first === horizon && memo.second == date) {
                memo.third
            } else {
                sunPeriods(profile, horizon.point, date, zone).also { periodsOfDay = Triple(horizon, date, it) }
            }
        return SunshineUiState.Ready(periods, sunshineAt(profile, horizon.point, time.toInstant()))
    }

    private sealed interface HorizonState {
        data object Loading : HorizonState

        /** The horizon at [point]; [profile] is `null` when the ground height is unknown. */
        class Computed(
            val point: GeoPoint,
            val profile: HorizonProfile?,
        ) : HorizonState {
            val isComplete: Boolean get() = profile != null && profile.complete.all { it }
        }
    }

    private fun Elevation.toState(): ElevationState =
        when (this) {
            is Elevation.Known -> ElevationState.Known(metres)
            Elevation.Unknown -> ElevationState.Unknown
        }

    private companion object {
        const val DEFAULT_ZOOM = 10.0
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val SETTLE_MILLIS = 300L
        const val KEY_LATITUDE = "camera_latitude"
        const val KEY_LONGITUDE = "camera_longitude"
        const val KEY_ZOOM = "camera_zoom"
        const val KEY_SELECTED_TIME = "selected_time_epoch_millis"
    }
}
