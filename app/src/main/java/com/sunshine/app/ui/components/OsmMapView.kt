package com.sunshine.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.sunshine.app.domain.model.GeoPoint
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView

private const val MIN_ZOOM = 0
private const val MAX_ZOOM = 17
private const val TILE_SIZE = 256

/**
 * OpenTopoMap tile source for hiking/outdoor use.
 * Provides topographic styling with contour lines and hill shading.
 */
private val openTopoMapTileSource = OpenTopoMapTileSource()

private class OpenTopoMapTileSource : OnlineTileSourceBase(
    "OpenTopoMap",
    MIN_ZOOM,
    MAX_ZOOM,
    TILE_SIZE,
    ".png",
    arrayOf(
        "https://a.tile.opentopomap.org/",
        "https://b.tile.opentopomap.org/",
        "https://c.tile.opentopomap.org/",
    ),
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return "$baseUrl$zoom/$x/$y$mImageFilenameEnding"
    }
}

/**
 * Composable wrapper for osmdroid MapView.
 */
@Composable
fun OsmMapView(
    center: GeoPoint,
    zoomLevel: Double,
    onMapMoved: (GeoPoint) -> Unit,
    onZoomChanged: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mapView =
        remember {
            MapView(context).apply {
                setTileSource(openTopoMapTileSource)
                setMultiTouchControls(true)
                controller.setZoom(zoomLevel)
                controller.setCenter(OsmGeoPoint(center.latitude, center.longitude))
            }
        }

    // Keep callback references updated without recreating the listener
    val currentOnMapMoved = rememberUpdatedState(onMapMoved)
    val currentOnZoomChanged = rememberUpdatedState(onZoomChanged)

    // Set up map listener once when mapView is created
    DisposableEffect(mapView) {
        val listener =
            object : MapListener {
                override fun onScroll(event: ScrollEvent?): Boolean {
                    val newCenter = mapView.mapCenter
                    currentOnMapMoved.value(
                        GeoPoint(
                            latitude = newCenter.latitude,
                            longitude = newCenter.longitude,
                        ),
                    )
                    return true
                }

                override fun onZoom(event: ZoomEvent?): Boolean {
                    currentOnZoomChanged.value(mapView.zoomLevelDouble)
                    return true
                }
            }
        mapView.addMapListener(listener)

        onDispose {
            mapView.onDetach()
        }
    }

    // Update map position when center/zoom changes from ViewModel
    LaunchedEffect(center, zoomLevel) {
        val currentCenter = mapView.mapCenter
        if (currentCenter.latitude != center.latitude ||
            currentCenter.longitude != center.longitude
        ) {
            mapView.controller.setCenter(OsmGeoPoint(center.latitude, center.longitude))
        }

        if (mapView.zoomLevelDouble != zoomLevel) {
            mapView.controller.setZoom(zoomLevel)
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
    )
}
