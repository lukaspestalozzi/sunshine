package com.sunshine.app.map

import android.Manifest
import android.content.ComponentCallbacks
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sunshine.core.GeoPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngQuad
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.engine.LocationEngineRequest
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.ImageSource

/**
 * Full MapLibre map showing OpenTopoMap tiles. Starts at [initialCamera] and reports every camera
 * movement through [onCameraMoved]. [overlay] is drawn on the terrain, directly above the map tiles;
 * `null` removes it. The tiles are drawn with [saturation] (−1 greyscale, 0 their own colours).
 * Once [locationAllowed], the device's position is drawn as a dot and [onLocationStale] reports
 * whether it is old; each element of [centreRequests] moves the map centre to a fresh position.
 */
@Composable
fun MapLibreMap(
    initialCamera: CameraState,
    onCameraMoved: (CameraState) -> Unit,
    modifier: Modifier = Modifier,
    overlay: OverlayImage? = null,
    saturation: Float = 0f,
    locationAllowed: Boolean = false,
    onLocationStale: (Boolean) -> Unit = {},
    centreRequests: Flow<Unit> = emptyFlow(),
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnCameraMoved by rememberUpdatedState(onCameraMoved)
    val currentOnLocationStale by rememberUpdatedState(onLocationStale)

    val mapView =
        remember {
            MapView(context, mapOptions(context, initialCamera)).apply {
                getMapAsync { map ->
                    map.setStyle(Style.Builder().fromJson(OPEN_TOPO_MAP_STYLE))
                    map.addOnCameraMoveListener { currentOnCameraMoved(map.cameraPosition.toCameraState()) }
                }
            }
        }

    DisposableEffect(lifecycle, mapView) {
        // The state the map view was brought to. Leaving the composition (the Settings or Offline page
        // replaces the map while the activity stays resumed) brings it down from there; otherwise it
        // keeps running, its location engine included (gps-location spec, "Location updates only
        // while visible").
        var state = Lifecycle.State.INITIALIZED
        val observer =
            LifecycleEventObserver { _, event ->
                mapView.forward(event)
                state = event.targetState
            }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (state.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (state.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            if (state.isAtLeast(Lifecycle.State.CREATED)) mapView.onDestroy()
        }
    }

    DisposableEffect(context, mapView) {
        val callbacks = LowMemoryForwarder(mapView)
        context.registerComponentCallbacks(callbacks)
        onDispose { context.unregisterComponentCallbacks(callbacks) }
    }

    LaunchedEffect(mapView, overlay) {
        mapView.getMapAsync { map -> map.getStyle { style -> style.showOverlay(overlay) } }
    }

    LaunchedEffect(mapView, saturation) {
        mapView.getMapAsync { map ->
            map.getStyle { style -> style.getLayer(TOPO_LAYER_ID)?.setProperties(PropertyFactory.rasterSaturation(saturation)) }
        }
    }

    LaunchedEffect(mapView, locationAllowed) {
        if (!locationAllowed) return@LaunchedEffect
        mapView.getMapAsync { map ->
            map.getStyle { style -> map.locationComponent.showPosition(context, style) { stale -> currentOnLocationStale(stale) } }
        }
    }

    LaunchedEffect(mapView, centreRequests) {
        centreRequests.collect { mapView.getMapAsync { map -> map.centreOnPosition() } }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

/**
 * Draws the device's position with MapLibre's default location engine (design D1 of add-gps-location):
 * a dot with its accuracy circle, grey once no position arrived for 30 s (design D2). The component
 * never moves the camera. [onStale] gets every change of the stale state.
 */
private fun LocationComponent.showPosition(
    context: Context,
    style: Style,
    onStale: (Boolean) -> Unit,
) {
    // The engine throws without access; MapScreen passes locationAllowed only with it. The check
    // stays in this function, where Android lint looks for it.
    val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    val coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) return
    if (!isLocationComponentActivated) {
        val options =
            LocationComponentOptions
                .builder(context)
                .enableStaleState(true)
                .staleStateTimeout(STALE_MILLIS)
                .build()
        val request =
            LocationEngineRequest
                .Builder(UPDATE_MILLIS)
                .setFastestInterval(UPDATE_MILLIS)
                .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
                .build()
        activateLocationComponent(
            LocationComponentActivationOptions
                .builder(context, style)
                .locationComponentOptions(options)
                .useDefaultLocationEngine(true)
                .locationEngineRequest(request)
                .build(),
        )
        addOnLocationStaleListener { stale -> onStale(stale) }
        // A new component starts stale and draws the engine's last known position grey; the
        // listener reports only changes, so the button learns the start state here.
        onStale(true)
    }
    isLocationComponentEnabled = true
    cameraMode = CameraMode.NONE
    renderMode = RenderMode.NORMAL
}

/** Moves the map centre to the component's position, keeping the zoom (gps-location spec, "Centre on the position"). */
private fun MapLibreMap.centreOnPosition() {
    if (!locationComponent.isLocationComponentActivated) return
    val position = locationComponent.lastKnownLocation ?: return
    animateCamera(CameraUpdateFactory.newLatLng(LatLng(position.latitude, position.longitude)))
}

/**
 * The map tiles' saturation for the overlay toggle's [option]: greyscale under the heatmap, so that
 * its colours stand out, and the tiles' own colours otherwise (map-view spec, "Map colours under the
 * heatmap"; design D10 of add-sun-exposure-heatmap).
 */
fun mapSaturation(option: OverlayOption): Float = if (option == OverlayOption.SUN_HOURS) GREYSCALE else 0f

/**
 * Shows [image] as a georeferenced raster right above the map tiles (design D9 of
 * add-sun-shade-overlay), or removes the overlay when [image] is `null`.
 */
private fun Style.showOverlay(image: OverlayImage?) {
    if (image == null) {
        getLayer(OVERLAY_ID)?.let { removeLayer(it) }
        getSource(OVERLAY_ID)?.let { removeSource(it) }
        return
    }
    val bitmap = Bitmap.createBitmap(image.pixels, image.width, image.height, Bitmap.Config.ARGB_8888)
    val (northWest, northEast, southEast, southWest) = image.corners.map { LatLng(it.latitude, it.longitude) }
    val quad = LatLngQuad(northWest, northEast, southEast, southWest)
    val source = getSourceAs<ImageSource>(OVERLAY_ID)
    if (source == null) {
        addSource(ImageSource(OVERLAY_ID, quad, bitmap))
        addLayerAbove(
            RasterLayer(OVERLAY_ID, OVERLAY_ID).withProperties(
                // Nearest keeps cell edges and the hatching crisp; no fade between overlays.
                PropertyFactory.rasterResampling(Property.RASTER_RESAMPLING_NEAREST),
                PropertyFactory.rasterFadeDuration(0f),
            ),
            TOPO_LAYER_ID,
        )
    } else {
        source.setCoordinates(quad)
        source.setImage(bitmap)
    }
}

private fun mapOptions(
    context: Context,
    camera: CameraState,
): MapLibreMapOptions =
    MapLibreMapOptions
        .createFromAttributes(context)
        .camera(
            CameraPosition
                .Builder()
                .target(LatLng(camera.center.latitude, camera.center.longitude))
                .zoom(camera.zoom)
                .build(),
        ).minZoomPreference(MIN_ZOOM)
        .maxZoomPreference(MAX_ZOOM)
        // North-up and flat, so up on the screen is north and the sun line's angle is the azimuth.
        .rotateGesturesEnabled(false)
        .tiltGesturesEnabled(false)
        .logoEnabled(false)
        .attributionEnabled(false)

private fun MapView.forward(event: Lifecycle.Event) {
    when (event) {
        Lifecycle.Event.ON_CREATE -> onCreate(null)
        Lifecycle.Event.ON_START -> onStart()
        Lifecycle.Event.ON_RESUME -> onResume()
        Lifecycle.Event.ON_PAUSE -> onPause()
        Lifecycle.Event.ON_STOP -> onStop()
        Lifecycle.Event.ON_DESTROY -> onDestroy()
        Lifecycle.Event.ON_ANY -> Unit
    }
}

private fun CameraPosition.toCameraState(): CameraState {
    val center = checkNotNull(target) { "MapLibre camera position has no target" }.wrap()
    return CameraState(center = GeoPoint(center.latitude, center.longitude), zoom = zoom)
}

private class LowMemoryForwarder(
    private val mapView: MapView,
) : ComponentCallbacks {
    // Deprecated since API 34, where the platform stops calling it; devices on API 29-33 (minSdk 29)
    // still do, and MapView uses it to release caches.
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onLowMemory() = mapView.onLowMemory()

    override fun onConfigurationChanged(newConfig: Configuration) = Unit
}

private const val MIN_ZOOM = 5.0
private const val OVERLAY_ID = "sun-shade-overlay"
private const val TOPO_LAYER_ID = "opentopomap"
private const val GREYSCALE = -1f
private const val MAX_ZOOM = 17.0

// At most one position per second (gps-location spec, "Location updates only while visible").
private const val UPDATE_MILLIS = 1_000L

// A position older than this is drawn grey (user decision, design D2 of add-gps-location).
private const val STALE_MILLIS = 30_000L
