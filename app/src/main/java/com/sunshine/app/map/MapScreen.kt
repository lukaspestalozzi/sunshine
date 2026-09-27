package com.sunshine.app.map

import android.net.ConnectivityManager
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sunshine.app.BuildConfig
import com.sunshine.app.SunshineApp
import com.sunshine.app.network.NetworkMonitor
import com.sunshine.app.sunshine.debugLog
import java.time.Clock
import kotlinx.coroutines.Dispatchers

@Composable
fun MapScreen(viewModel: MapViewModel = viewModel(factory = mapViewModelFactory)) {
    val camera by viewModel.camera.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()
    val selectedTime by viewModel.selectedTime.collectAsStateWithLifecycle()
    val sun by viewModel.sun.collectAsStateWithLifecycle()
    val elevation by viewModel.elevation.collectAsStateWithLifecycle()
    val computedSunshine by viewModel.sunshine.collectAsStateWithLifecycle()
    val overlay by viewModel.overlay.collectAsStateWithLifecycle()
    val isOverlayOn by viewModel.isOverlayOn.collectAsStateWithLifecycle()
    val sunshine = computedSunshine.at(camera.center)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val startInset = WindowInsets.safeDrawing.asPaddingValues().calculateStartPadding(LocalLayoutDirection.current)
        val panelMaxWidth = sunPanelMaxWidth(maxWidth, maxHeight, startInset)
        // The map fills this box: its size and the camera give the overlay's visible area.
        LaunchedEffect(maxWidth, maxHeight) { viewModel.onMapSizeChanged(maxWidth.value.toDouble(), maxHeight.value.toDouble()) }
        MapLibreMap(
            initialCamera = camera,
            onCameraMoved = viewModel::onCameraMoved,
            modifier = Modifier.fillMaxSize(),
            overlay = overlay.image(),
        )
        sun?.let { SunLine(it.position, (sunshine as? SunshineUiState.Ready)?.atSelectedTime) }
        Crosshair(Modifier.align(Alignment.Center))
        MapLabels(
            camera = camera,
            isOffline = isOffline,
            notice = overlayNotice(overlay, selectedTime),
            topEnd = { OverlayControl(isOn = isOverlayOn, onToggle = viewModel::onOverlayToggled) },
        ) {
            SunPanel(
                selectedTime = selectedTime,
                sun = sun,
                elevation = elevation,
                sunshine = sunshine,
                onDateSelected = viewModel::onDateSelected,
                onSliderMoved = viewModel::onSliderMoved,
                onNowClicked = viewModel::onNowClicked,
                modifier = Modifier.widthIn(max = panelMaxWidth),
            )
        }
    }
}

/**
 * Widest the sun panel may be. It sits bottom-start; in landscape it must end left of the centre
 * crosshair so that it never covers it (sun-position spec, "Sun information panel"). In portrait it
 * keeps the full cap and sits below the crosshair, except in very short windows (split screen).
 */
fun sunPanelMaxWidth(
    screenWidth: Dp,
    screenHeight: Dp,
    startInset: Dp,
): Dp {
    if (screenWidth <= screenHeight) return SUN_PANEL_MAX_WIDTH
    val leftOfCrosshair = screenWidth / 2 - startInset - CROSSHAIR_CLEARANCE
    return leftOfCrosshair.coerceIn(0.dp, SUN_PANEL_MAX_WIDTH)
}

private val SUN_PANEL_MAX_WIDTH = 360.dp

// Label padding (8 dp) + half the 32 dp crosshair (16 dp) + a gap (8 dp).
private val CROSSHAIR_CLEARANCE = 32.dp

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
                elevationRepository = (application as SunshineApp).elevationRepository,
                horizonProfile = application.sunshineRepository::profile,
                overlayGrid = application.overlayRepository::grid,
                computeDispatcher = Dispatchers.Default,
                log = ::debugLog,
                checkOverlayAgreement = BuildConfig.DEBUG,
            )
        }
    }

/** The image to draw: the finished overlay, or the one kept while a new one is computed. */
private fun OverlayUiState.image(): OverlayImage? =
    when (this) {
        is OverlayUiState.Ready -> image
        is OverlayUiState.Computing -> kept?.image
        OverlayUiState.Off, OverlayUiState.ZoomedOut -> null
    }
