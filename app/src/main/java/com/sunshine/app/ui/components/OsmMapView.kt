package com.sunshine.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.sunshine.app.domain.model.GeoPoint
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
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(zoomLevel)
            controller.setCenter(OsmGeoPoint(center.latitude, center.longitude))
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map ->
            // Update map when center or zoom changes
            val currentCenter = map.mapCenter
            if (currentCenter.latitude != center.latitude ||
                currentCenter.longitude != center.longitude
            ) {
                map.controller.setCenter(OsmGeoPoint(center.latitude, center.longitude))
            }

            if (map.zoomLevelDouble != zoomLevel) {
                map.controller.setZoom(zoomLevel)
            }

            // Set up listener for user interactions
            map.addMapListener(object : org.osmdroid.events.MapListener {
                override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                    val newCenter = map.mapCenter
                    onMapMoved(
                        GeoPoint(
                            latitude = newCenter.latitude,
                            longitude = newCenter.longitude,
                        ),
                    )
                    return true
                }

                override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                    onZoomChanged(map.zoomLevelDouble)
                    return true
                }
            })
        },
    )
}
