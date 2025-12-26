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
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView

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
                setTileSource(TileSourceFactory.MAPNIK)
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
