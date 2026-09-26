package com.sunshine.app.map

import android.net.ConnectivityManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sunshine.app.network.NetworkMonitor
import java.time.Clock
import kotlinx.coroutines.Dispatchers

@Composable
fun MapScreen(viewModel: MapViewModel = viewModel(factory = mapViewModelFactory)) {
    val camera by viewModel.camera.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()
    val selectedTime by viewModel.selectedTime.collectAsStateWithLifecycle()
    val sun by viewModel.sun.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        MapLibreMap(
            initialCamera = camera,
            onCameraMoved = viewModel::onCameraMoved,
            modifier = Modifier.fillMaxSize(),
        )
        sun?.let { SunLine(it.position) }
        Crosshair(Modifier.align(Alignment.Center))
        MapLabels(camera = camera, isOffline = isOffline) {
            // At most 360 dp wide so that, in landscape, the panel stays left of the centre crosshair.
            SunPanel(
                selectedTime = selectedTime,
                sun = sun,
                onDateSelected = viewModel::onDateSelected,
                onSliderMoved = viewModel::onSliderMoved,
                onNowClicked = viewModel::onNowClicked,
                modifier = Modifier.widthIn(max = 360.dp),
            )
        }
    }
}

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
