package com.sunshine.app.map

import android.net.ConnectivityManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sunshine.app.network.NetworkMonitor
import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers

@Composable
fun MapScreen(viewModel: MapViewModel = viewModel(factory = mapViewModelFactory)) {
    val camera by viewModel.camera.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()
    val selectedTime by viewModel.selectedTime.collectAsStateWithLifecycle()
    val sun by viewModel.sun.collectAsStateWithLifecycle()

    MapScreenContent(
        camera = camera,
        isOffline = isOffline,
        selectedTime = selectedTime,
        sun = sun,
        onDateSelected = viewModel::onDateSelected,
        onSliderMoved = viewModel::onSliderMoved,
        onNowClicked = viewModel::onNowClicked,
    ) { modifier ->
        MapLibreMap(initialCamera = camera, onCameraMoved = viewModel::onCameraMoved, modifier = modifier)
    }
}

/** The map screen without state holders; [map] draws the map itself, so tests can replace the native MapLibre view. */
@Composable
fun MapScreenContent(
    camera: CameraState,
    isOffline: Boolean,
    selectedTime: ZonedDateTime,
    sun: SunInfo?,
    onDateSelected: (LocalDate) -> Unit,
    onSliderMoved: (Float) -> Unit,
    onNowClicked: () -> Unit,
    map: @Composable (Modifier) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        map(Modifier.fillMaxSize())
        sun?.let { SunLine(it.position) }
        Crosshair(Modifier.align(Alignment.Center).testTag(CROSSHAIR_TAG))
        MapLabels(camera = camera, isOffline = isOffline) {
            // At most 360 dp wide so that, in landscape, the panel stays left of the centre crosshair.
            SunPanel(
                selectedTime = selectedTime,
                sun = sun,
                onDateSelected = onDateSelected,
                onSliderMoved = onSliderMoved,
                onNowClicked = onNowClicked,
                modifier = Modifier.widthIn(max = 360.dp).testTag(SUN_PANEL_TAG),
            )
        }
    }
}

// Test tags for the layout assertions of MapScreenScreenshotTest.
const val CROSSHAIR_TAG = "crosshair"
const val SUN_PANEL_TAG = "sunPanel"
const val ATTRIBUTION_TAG = "attribution"

private val mapViewModelFactory =
    viewModelFactory {
        initializer {
            val application = checkNotNull(this[APPLICATION_KEY]) { "MapViewModel needs the Application" }
            val connectivityManager =
                checkNotNull(application.getSystemService(ConnectivityManager::class.java)) {
                    "ConnectivityManager is not available"
                }
            MapViewModel(
                savedState = createSavedStateHandle(),
                isOnline = NetworkMonitor(connectivityManager).isOnline,
                clock = Clock.systemDefaultZone(),
                computeDispatcher = Dispatchers.Default,
            )
        }
    }
