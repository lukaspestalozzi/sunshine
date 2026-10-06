package com.sunshine.app.map

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sunshine.app.R
import com.sunshine.app.SunshineApp
import com.sunshine.app.network.NetworkMonitor
import com.sunshine.app.sunshine.debugLines
import com.sunshine.app.sunshine.debugLog
import com.sunshine.core.MapArea
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

@Composable
fun MapScreen(
    onSettingsClicked: () -> Unit,
    onOfflineClicked: (MapArea) -> Unit,
    viewModel: MapViewModel = viewModel(factory = mapViewModelFactory),
) {
    val camera by viewModel.camera.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()
    val selectedTime by viewModel.selectedTime.collectAsStateWithLifecycle()
    val sun by viewModel.sun.collectAsStateWithLifecycle()
    val elevation by viewModel.elevation.collectAsStateWithLifecycle()
    val computedSunshine by viewModel.sunshine.collectAsStateWithLifecycle()
    val overlay by viewModel.overlay.collectAsStateWithLifecycle()
    val isOverlayOn by viewModel.isOverlayOn.collectAsStateWithLifecycle()
    val dayProgress by viewModel.dayProgress.collectAsStateWithLifecycle()
    val overlayMode by viewModel.overlayMode.collectAsStateWithLifecycle()
    val heatmap by viewModel.heatmap.collectAsStateWithLifecycle()
    val locationButton by viewModel.locationButton.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val sliderStep by viewModel.sliderStep.collectAsStateWithLifecycle()
    val debugValues by viewModel.debugValues.collectAsStateWithLifecycle()
    val sunshine = computedSunshine.at(camera.center)

    val context = LocalContext.current
    val activity = checkNotNull(LocalActivity.current) { "MapScreen needs its Activity for the permission dialog" }
    val resources = LocalResources.current
    // Read again on every resume: the user may have changed it in the system settings meanwhile.
    var locationAllowed by remember { mutableStateOf(context.locationAccess() != LocationAccess.NONE) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { locationAllowed = context.locationAccess() != LocationAccess.NONE }
    // Only while the map is in front: the Settings and Offline pages replace this composable, and
    // Android drops the flag in the background (settings spec, "Keep screen on"; design D8 of add-settings).
    val view = LocalView.current
    DisposableEffect(view, settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    // In the background, and when the Settings or Offline page replaces the map, which may then go
    // to the background itself (map-view spec, "Default viewport").
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.storeLastView() }
    DisposableEffect(viewModel) { onDispose { viewModel.storeLastView() } }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val access = context.locationAccess()
            locationAllowed = access != LocationAccess.NONE
            // After a refusal, Android shows the dialog again only while it offers a rationale (design D4).
            val dialogAvailable = LOCATION_PERMISSIONS.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
            viewModel.onLocationPermissionAnswered(access, context.isLocationOn(), dialogAvailable)
        }
    val snackbarHostState = remember { SnackbarHostState() }
    val centreRequests = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    LaunchedEffect(viewModel) {
        viewModel.locationActions.collect { action ->
            when (action) {
                LocationAction.AskPermission -> permissionLauncher.launch(LOCATION_PERMISSIONS)
                LocationAction.Centre -> centreRequests.tryEmit(Unit)
                // Its own coroutine: a shown snackbar waits for its end, and the next action must not.
                is LocationAction.Notice -> launch { snackbarHostState.showNotice(action.notice, resources, context) }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val startInset = WindowInsets.safeDrawing.asPaddingValues().calculateStartPadding(LocalLayoutDirection.current)
        val panelMaxWidth = sunPanelMaxWidth(maxWidth, maxHeight, startInset)
        // The map fills this box: its size and the camera give the overlay's visible area.
        LaunchedEffect(maxWidth, maxHeight) { viewModel.onMapSizeChanged(maxWidth.value.toDouble(), maxHeight.value.toDouble()) }
        MapLibreMap(
            initialCamera = camera,
            onCameraMoved = viewModel::onCameraMoved,
            modifier = Modifier.fillMaxSize(),
            overlay =
                when (overlayMode) {
                    OverlayMode.SUN_AND_SHADE -> overlay.image()
                    OverlayMode.SUN_HOURS -> heatmap.image()
                },
            saturation = mapSaturation(overlayOption(isOverlayOn, overlayMode)),
            locationAllowed = locationAllowed,
            onLocationStale = viewModel::onLocationStale,
            centreRequests = centreRequests,
            onCameraGesture = viewModel::onCameraGesture,
            overlayOpacity = settings.overlayOpacityPercent / PERCENT,
        )
        sun?.let { SunLine(it.position, (sunshine as? SunshineUiState.Ready)?.atSelectedTime) }
        Crosshair(Modifier.align(Alignment.Center))
        MapLabels(
            camera = camera,
            coordinates = settings.coordinates,
            isOffline = isOffline,
            onSettingsClicked = onSettingsClicked,
            // The area visible now: the Offline page downloads this one (design D9 of add-offline-regions).
            onOfflineClicked = {
                onOfflineClicked(
                    MapArea(camera.center, camera.zoom, maxWidth.value.toDouble(), maxHeight.value.toDouble()),
                )
            },
            locationButton = locationButton,
            onLocationClicked = { viewModel.onLocationTapped(context.locationAccess(), context.isLocationOn()) },
            debugLines = if (settings.debug.any) debugLines(debugValues, settings.debug) else emptyList(),
            // Left of the crosshair, as the panel in landscape (settings spec, "Debug info").
            debugMaxWidth = widthLeftOfCrosshair(maxWidth, startInset),
            topEnd = {
                OverlayControl(
                    option = overlayOption(isOverlayOn, overlayMode),
                    notice = notice(overlayMode, overlay, heatmap, selectedTime),
                    dayProgress = dayProgress,
                    // The scale runs up to the day length at the map centre (design D3 of add-sun-exposure-heatmap).
                    bands = sun?.day?.dayLength?.let { remember(it) { HeatmapBands(it) } },
                    onOptionSelected = viewModel::onOverlaySelected,
                )
            },
        ) {
            SunPanel(
                selectedTime = selectedTime,
                sun = sun,
                elevation = elevation,
                sunshine = sunshine,
                sunHours =
                    formatSunHours(heatmap, camera.center).takeIf {
                        isOverlayOn && overlayMode == OverlayMode.SUN_HOURS && camera.zoom >= MIN_OVERLAY_ZOOM
                    },
                onDateSelected = viewModel::onDateSelected,
                onSliderMoved = viewModel::onSliderMoved,
                sliderStep = sliderStep,
                onNowClicked = viewModel::onNowClicked,
                modifier = Modifier.widthIn(max = panelMaxWidth),
            )
        }
        SnackbarHost(
            snackbarHostState,
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) { data ->
            // Swipe to dismiss (gps-location spec, "Location permission"); a fresh state per notice.
            key(data) {
                SwipeToDismissBox(
                    state = rememberSwipeToDismissBoxState(),
                    backgroundContent = {},
                    onDismiss = { data.dismiss() },
                ) { Snackbar(data) }
            }
        }
    }
}

internal val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

/** The location access the user allowed: precise (fine) wins over approximate (coarse). */
internal fun Context.locationAccess(): LocationAccess =
    when {
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> LocationAccess.PRECISE
        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED -> LocationAccess.APPROXIMATE
        else -> LocationAccess.NONE
    }

private fun Context.isLocationOn(): Boolean =
    checkNotNull(getSystemService(LocationManager::class.java)) { "LocationManager is not available" }.isLocationEnabled

/**
 * Shows [notice] for 10 s (`SnackbarDuration.Long`) with `Settings`, which opens the settings that
 * fix it (design D5 of add-gps-location).
 */
private suspend fun SnackbarHostState.showNotice(
    notice: LocationNotice,
    resources: Resources,
    context: Context,
) {
    val (message, settings) =
        when (notice) {
            LocationNotice.ACCESS_OFF -> R.string.location_notice_access_off to context.appSettings()
            LocationNotice.APPROXIMATE_ONLY -> R.string.location_notice_approximate to context.appSettings()
            LocationNotice.SWITCHED_OFF -> R.string.location_notice_switched_off to Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        }
    // The new notice replaces the shown one: repeated taps must not queue notices of 10 s each.
    currentSnackbarData?.dismiss()
    val result =
        showSnackbar(
            message = resources.getString(message),
            actionLabel = resources.getString(R.string.location_notice_settings),
            duration = SnackbarDuration.Long,
        )
    if (result == SnackbarResult.ActionPerformed) context.startActivity(settings)
}

private fun Context.appSettings(): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))

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
    return widthLeftOfCrosshair(screenWidth, startInset).coerceAtMost(SUN_PANEL_MAX_WIDTH)
}

/** Widest an element at the map's left edge may be so that it ends left of the centre crosshair. */
fun widthLeftOfCrosshair(
    screenWidth: Dp,
    startInset: Dp,
): Dp = (screenWidth / 2 - startInset - CROSSHAIR_CLEARANCE).coerceAtLeast(0.dp)

private val SUN_PANEL_MAX_WIDTH = 360.dp

// Label padding (8 dp) + half the 32 dp crosshair (16 dp) + a gap (8 dp).
private val CROSSHAIR_CLEARANCE = 32.dp

private const val MEBIBYTE = 1024L * 1024
private const val PERCENT = 100f

private val mapViewModelFactory =
    viewModelFactory {
        initializer {
            val application = checkNotNull(this[APPLICATION_KEY]) { "MapViewModel needs the Application" }
            val connectivityManager =
                checkNotNull(application.getSystemService(ConnectivityManager::class.java)) {
                    "ConnectivityManager is not available"
                }
            val activityManager =
                checkNotNull(application.getSystemService(ActivityManager::class.java)) { "ActivityManager is not available" }
            MapViewModel(
                savedState = createSavedStateHandle(),
                isOnline = NetworkMonitor(connectivityManager).isOnline,
                clock = Clock.systemDefaultZone(),
                elevationRepository = (application as SunshineApp).elevationRepository,
                horizonProfile = application.sunshineRepository::profile,
                overlayGrid = application.overlayRepository::grid,
                computeDispatcher = Dispatchers.Default,
                dayDispatcher = dayDispatcher(),
                // A quarter of the app's heap limit (user decision, design D14).
                dayCache = DayCache(maxBytes = activityManager.memoryClass * MEBIBYTE / 4),
                log = ::debugLog,
                settings = application.settingsStore.settings,
                saveLastView = application.settingsStore::setLastView,
                debug = application.debugInfo,
                checkProfile = { application.sunshineRepository.profile(it, record = false) },
            )
        }
    }

/** The heatmap to draw: the finished one, or the one kept while a new one is computed. */
private fun HeatmapUiState.image(): OverlayImage? =
    when (this) {
        is HeatmapUiState.Ready -> image
        is HeatmapUiState.Computing -> kept?.image
        HeatmapUiState.Off, HeatmapUiState.ZoomedOut -> null
    }

/** The image to draw: the finished overlay, or the one kept while a new one is computed. */
private fun OverlayUiState.image(): OverlayImage? =
    when (this) {
        is OverlayUiState.Ready -> image
        is OverlayUiState.Computing -> kept?.image
        OverlayUiState.Off, OverlayUiState.ZoomedOut -> null
    }
