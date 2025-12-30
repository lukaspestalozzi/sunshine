package com.sunshine.app.ui.components

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.sunshine.app.domain.model.GeoPoint
import com.sunshine.app.domain.model.VisibilityGrid
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon

private const val MIN_ZOOM = 0
private const val MAX_ZOOM = 17
private const val TILE_SIZE = 256

// Colors for visibility overlay
private const val SUNLIT_COLOR = 0x40FFEB3B // Semi-transparent yellow
private const val SHADED_COLOR = 0x404A5568 // Semi-transparent gray-blue

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
 * Composable wrapper for osmdroid MapView with visibility overlay.
 */
@Composable
fun OsmMapView(
    center: GeoPoint,
    zoomLevel: Double,
    onMapMoved: (GeoPoint) -> Unit,
    onZoomChanged: (Double) -> Unit,
    modifier: Modifier = Modifier,
    visibilityGrid: VisibilityGrid? = null,
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

    // Update visibility grid overlay
    LaunchedEffect(visibilityGrid) {
        updateVisibilityOverlay(mapView, visibilityGrid)
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
    )
}

/**
 * Update the visibility overlay on the map.
 * Clears existing overlay polygons and creates new ones based on the grid.
 */
private fun updateVisibilityOverlay(
    mapView: MapView,
    grid: VisibilityGrid?,
) {
    // Remove existing visibility overlays (keep the first overlay which is the tile layer)
    val overlaysToRemove = mapView.overlays.filterIsInstance<VisibilityPolygon>()
    mapView.overlays.removeAll(overlaysToRemove)

    if (grid == null) {
        mapView.invalidate()
        return
    }

    // Create polygons for each grid cell
    val resolution = grid.resolution
    val halfRes = resolution / 2

    for ((point, isVisible) in grid.points) {
        val polygon = createGridCellPolygon(point, halfRes, isVisible)
        mapView.overlays.add(polygon)
    }

    mapView.invalidate()
}

/**
 * Create a polygon for a single grid cell.
 */
private fun createGridCellPolygon(
    center: GeoPoint,
    halfSize: Double,
    isVisible: Boolean,
): VisibilityPolygon {
    val polygon = VisibilityPolygon()

    // Create rectangle corners
    val points =
        listOf(
            OsmGeoPoint(center.latitude - halfSize, center.longitude - halfSize),
            OsmGeoPoint(center.latitude - halfSize, center.longitude + halfSize),
            OsmGeoPoint(center.latitude + halfSize, center.longitude + halfSize),
            OsmGeoPoint(center.latitude + halfSize, center.longitude - halfSize),
            OsmGeoPoint(center.latitude - halfSize, center.longitude - halfSize), // Close polygon
        )

    polygon.points = points
    polygon.fillPaint.color = if (isVisible) SUNLIT_COLOR else SHADED_COLOR
    polygon.outlinePaint.color = Color.TRANSPARENT
    polygon.outlinePaint.strokeWidth = 0f

    return polygon
}

/**
 * Custom polygon class to identify visibility overlays for removal.
 */
private class VisibilityPolygon : Polygon()
