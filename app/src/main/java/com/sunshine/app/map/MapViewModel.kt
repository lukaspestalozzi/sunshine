package com.sunshine.app.map

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sunshine.core.DEFAULT_LOCATION
import com.sunshine.core.GeoPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class CameraState(
    val center: GeoPoint,
    val zoom: Double,
)

class MapViewModel(
    private val savedState: SavedStateHandle,
    isOnline: Flow<Boolean>,
) : ViewModel() {
    private val mutableCamera = MutableStateFlow(restoreCamera())
    val camera: StateFlow<CameraState> = mutableCamera.asStateFlow()

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
    }
}
