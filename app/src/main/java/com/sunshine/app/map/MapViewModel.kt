package com.sunshine.app.map

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sunshine.core.DEFAULT_LOCATION
import com.sunshine.core.GeoPoint
import com.sunshine.core.SunDay
import com.sunshine.core.SunPosition
import com.sunshine.core.sunDay
import com.sunshine.core.sunPosition
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class CameraState(
    val center: GeoPoint,
    val zoom: Double,
)

/** The sun at the selected location: its position at the selected time and the selected day's events. */
data class SunInfo(
    val position: SunPosition,
    val day: SunDay,
)

/**
 * State of the map screen. Times are in the zone of [clock] as it is when the view model is created
 * (the device time zone in production).
 */
class MapViewModel(
    private val savedState: SavedStateHandle,
    isOnline: Flow<Boolean>,
    private val clock: Clock,
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

    private companion object {
        const val DEFAULT_ZOOM = 10.0
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val KEY_LATITUDE = "camera_latitude"
        const val KEY_LONGITUDE = "camera_longitude"
        const val KEY_ZOOM = "camera_zoom"
        const val KEY_SELECTED_TIME = "selected_time_epoch_millis"
    }
}
