package com.sunshine.app.map

import android.net.ConnectivityManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sunshine.app.network.NetworkMonitor

@Composable
fun MapScreen(viewModel: MapViewModel = viewModel(factory = mapViewModelFactory)) {
    val camera by viewModel.camera.collectAsStateWithLifecycle()

    MapLibreMap(
        initialCamera = camera,
        onCameraMoved = viewModel::onCameraMoved,
        modifier = Modifier.fillMaxSize(),
    )
}

private val mapViewModelFactory =
    viewModelFactory {
        initializer {
            val application = checkNotNull(this[APPLICATION_KEY]) { "MapViewModel needs the Application" }
            val connectivityManager =
                checkNotNull(application.getSystemService(ConnectivityManager::class.java)) {
                    "ConnectivityManager is not available"
                }
            MapViewModel(createSavedStateHandle(), NetworkMonitor(connectivityManager).isOnline)
        }
    }
