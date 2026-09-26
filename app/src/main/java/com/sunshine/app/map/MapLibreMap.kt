package com.sunshine.app.map

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * Full MapLibre map showing OpenTopoMap tiles. Starts at [initialCamera] and reports every camera
 * movement through [onCameraMoved].
 */
@Composable
fun MapLibreMap(
    initialCamera: CameraState,
    onCameraMoved: (CameraState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnCameraMoved by rememberUpdatedState(onCameraMoved)

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
        val observer = LifecycleEventObserver { _, event -> mapView.forward(event) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    DisposableEffect(context, mapView) {
        val callbacks = LowMemoryForwarder(mapView)
        context.registerComponentCallbacks(callbacks)
        onDispose { context.unregisterComponentCallbacks(callbacks) }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
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
private const val MAX_ZOOM = 17.0

// Missing tiles leave the background visible: blank, never substitute imagery.
private const val OPEN_TOPO_MAP_STYLE = """
{
  "version": 8,
  "sources": {
    "opentopomap": {
      "type": "raster",
      "tiles": ["https://tile.opentopomap.org/{z}/{x}/{y}.png"],
      "tileSize": 256,
      "maxzoom": 17
    }
  },
  "layers": [
    { "id": "background", "type": "background", "paint": { "background-color": "#E0E0E0" } },
    { "id": "opentopomap", "type": "raster", "source": "opentopomap" }
  ]
}
"""
