package com.sunshine.app.map

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sunshine.app.elevation.Elevation
import com.sunshine.app.elevation.ElevationRepository
import com.sunshine.core.DEFAULT_LOCATION
import com.sunshine.core.GeoPoint
import com.sunshine.core.HorizonProfile
import com.sunshine.core.MapArea
import com.sunshine.core.ShadeGrid
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
import kotlin.time.measureTimedValue
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

    /** At [point]: the sun periods of the selected day and the sunshine state at the selected time. */
    data class Ready(
        val point: GeoPoint,
        val periods: SunPeriods,
        val atSelectedTime: Sunshine,
    ) : SunshineUiState

    /**
     * This state if it belongs to [center], else [Loading]: right after a camera move, the state may
     * still be the previous location's until the new computation starts (point-sunshine spec).
     */
    fun at(center: GeoPoint): SunshineUiState = if (this is Ready && point != center) Loading else this
}

/** The sun-shade overlay as shown on screen (sun-shade-overlay spec). */
sealed interface OverlayUiState {
    /** Switched off: nothing is computed. */
    data object Off : OverlayUiState

    /** Switched on below zoom [MIN_OVERLAY_ZOOM]: not shown. */
    data object ZoomedOut : OverlayUiState

    /**
     * A new grid is being computed. [kept] is the previous overlay after a camera move (still right
     * for its area), and `null` after a change of time or date, whose previous overlay would be wrong.
     */
    data class Computing(
        val kept: Ready?,
    ) : OverlayUiState

    /** The grid of the visible area at [time], and its [image] (rendered off the main thread). */
    data class Ready(
        val grid: ShadeGrid,
        val time: ZonedDateTime,
        val image: OverlayImage,
    ) : OverlayUiState
}

/** Lowest map zoom with an overlay (user decision, design D8 of add-sun-shade-overlay). */
const val MIN_OVERLAY_ZOOM = 11.0

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
    private val overlayGrid: suspend (MapArea, SunPosition) -> ShadeGrid,
    computeDispatcher: CoroutineDispatcher,
    private val log: (String) -> Unit = {},
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

    private val mutableOverlayOn = MutableStateFlow(savedState.get<Boolean>(KEY_OVERLAY_ON) ?: false)
    val isOverlayOn: StateFlow<Boolean> = mutableOverlayOn.asStateFlow()

    // Size of the map in dp; `null` until the screen reports it.
    private val mapSize = MutableStateFlow<Pair<Double, Double>?>(null)

    // The overlay of the visible area at the selected time (design D8 of add-sun-shade-overlay).
    // Latest wins, as for the horizon. A camera move waits [SETTLE_MILLIS] and keeps the previous
    // grid meanwhile; a time change starts at once and drops it. A reconnect recomputes only a grid
    // with unknown cells.
    val overlay: StateFlow<OverlayUiState> =
        channelFlow {
            var lookup: Job? = null
            var shown: OverlayUiState.Ready? = null
            var requestedTime: ZonedDateTime? = null
            combine(camera, selectedTime, mutableOverlayOn, mapSize, isOnline) { camera, time, on, size, online ->
                OverlayInput(camera, time, on, size, online)
            }.collect { input ->
                val area = input.area()
                if (area == null) {
                    lookup?.cancelAndJoin()
                    shown = null
                    requestedTime = null
                    send(if (input.on && input.camera.zoom < MIN_OVERLAY_ZOOM) OverlayUiState.ZoomedOut else OverlayUiState.Off)
                    return@collect
                }
                val current = shown
                val unchanged = current != null && current.grid.area == area && current.time == input.time && requestedTime == input.time
                if (unchanged && (!input.online || !current!!.grid.hasUnknown)) return@collect
                val timeChanged = requestedTime != null && requestedTime != input.time
                requestedTime = input.time
                lookup?.cancelAndJoin()
                send(OverlayUiState.Computing(kept = current?.takeIf { it.time == input.time }))
                lookup =
                    launch {
                        if (!timeChanged) delay(SETTLE_MILLIS)
                        val sun = sunPosition(area.center, input.time.toInstant())
                        val (grid, computing) = measureTimedValue { overlayGrid(area, sun) }
                        val (image, rendering) = measureTimedValue { renderOverlay(grid) }
                        log(
                            "Overlay ${area.widthDp.toInt()}×${area.heightDp.toInt()} dp at zoom ${area.zoom}: " +
                                "grid ${computing.inWholeMilliseconds} ms, image ${rendering.inWholeMilliseconds} ms",
                        )
                        val ready = OverlayUiState.Ready(grid, input.time, image)
                        shown = ready
                        send(ready)
                    }
            }
        }.flowOn(computeDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), initialValue = OverlayUiState.Off)

    private class OverlayInput(
        val camera: CameraState,
        val time: ZonedDateTime,
        val on: Boolean,
        val size: Pair<Double, Double>?,
        val online: Boolean,
    ) {
        // The area to compute, or null when the overlay is off, zoomed out or the size unknown.
        fun area(): MapArea? {
            if (!on || camera.zoom < MIN_OVERLAY_ZOOM || size == null) return null
            return MapArea(camera.center, camera.zoom, size.first, size.second)
        }
    }

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

    /** The map's size in dp, which with the camera gives the visible area. */
    fun onMapSizeChanged(
        widthDp: Double,
        heightDp: Double,
    ) {
        mapSize.value = widthDp to heightDp
    }

    /** Switches the overlay on or off; saved like the camera, so it survives rotation. */
    fun onOverlayToggled() {
        mutableOverlayOn.value = !mutableOverlayOn.value
        savedState[KEY_OVERLAY_ON] = mutableOverlayOn.value
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
        val profile = horizon.profile ?: return SunshineUiState.Ready(horizon.point, SunPeriods.Unknown, Sunshine.UNKNOWN)
        val date = time.toLocalDate()
        val memo = periodsOfDay
        val periods =
            if (memo != null && memo.first === horizon && memo.second == date) {
                memo.third
            } else {
                val (periods, duration) = measureTimedValue { sunPeriods(profile, horizon.point, date, zone) }
                log("Sun periods of $date: ${duration.inWholeMilliseconds} ms")
                periods.also { periodsOfDay = Triple(horizon, date, it) }
            }
        return SunshineUiState.Ready(horizon.point, periods, sunshineAt(profile, horizon.point, time.toInstant()))
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
        const val KEY_OVERLAY_ON = "overlay_on"
    }
}
