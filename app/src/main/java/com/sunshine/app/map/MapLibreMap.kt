package com.sunshine.app.map

import android.content.ComponentCallbacks
import android.content.Context
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
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngQuad
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
 */
@Composable
fun MapLibreMap(
    initialCamera: CameraState,
    onCameraMoved: (CameraState) -> Unit,
    modifier: Modifier = Modifier,
    overlay: OverlayImage? = null,
    saturation: Float = 0f,
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

    LaunchedEffect(mapView, overlay) {
        mapView.getMapAsync { map -> map.getStyle { style -> style.showOverlay(overlay) } }
    }

    LaunchedEffect(mapView, saturation) {
        mapView.getMapAsync { map ->
            map.getStyle { style -> style.getLayer(TOPO_LAYER_ID)?.setProperties(PropertyFactory.rasterSaturation(saturation)) }
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

/**
 * The map tiles' saturation for the overlay toggle's [option]: greyscale while the overlay is on, so
 * that its colours stand out (map-view spec, "Map colours while the overlay is on"; design D10 of
 * add-sun-exposure-heatmap).
 */
fun mapSaturation(option: OverlayOption): Float = if (option == OverlayOption.OFF) 0f else GREYSCALE

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
