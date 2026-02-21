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
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay

private const val MIN_ZOOM = 0
private const val MAX_ZOOM = 17
private const val TILE_SIZE = 256
private const val COORDINATE_TOLERANCE = 0.00001
private const val ZOOM_TOLERANCE = 0.01

/**
 * The tile source name used for osmdroid's tile provider and cache directories.
 * Must be consistent everywhere: tile source registration, cache path, and download worker.
 */
const val OPEN_TOPO_MAP_SOURCE_NAME = "OpenTopoMap"

// Colors for visibility overlay - match Color.kt OverlaySunlit/OverlayShaded
private const val SUNLIT_COLOR = 0x8CFFEB3B.toInt() // 55% alpha yellow (OverlaySunlit)
private const val SHADED_COLOR = 0x8C6B7A8F.toInt() // 55% alpha gray-blue (OverlayShaded)

/**
 * OpenTopoMap tile source for hiking/outdoor use.
 * Provides topographic styling with contour lines and hill shading.
 */
private val openTopoMapTileSource = OpenTopoMapTileSource()

private class OpenTopoMapTileSource : OnlineTileSourceBase(
    OPEN_TOPO_MAP_SOURCE_NAME,
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
    // Use tolerance to avoid feedback loops from floating-point drift
    LaunchedEffect(center, zoomLevel) {
        val currentCenter = mapView.mapCenter
        val latDiff = kotlin.math.abs(currentCenter.latitude - center.latitude)
        val lonDiff = kotlin.math.abs(currentCenter.longitude - center.longitude)
        if (latDiff > COORDINATE_TOLERANCE || lonDiff > COORDINATE_TOLERANCE) {
            mapView.controller.setCenter(OsmGeoPoint(center.latitude, center.longitude))
        }

        val zoomDiff = kotlin.math.abs(mapView.zoomLevelDouble - zoomLevel)
        if (zoomDiff > ZOOM_TOLERANCE) {
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
 * Uses a single custom overlay that draws all grid cells in one pass for performance.
 */
private fun updateVisibilityOverlay(
    mapView: MapView,
    grid: VisibilityGrid?,
) {
    // Remove existing visibility overlay
    val existing = mapView.overlays.filterIsInstance<VisibilityGridOverlay>()
    mapView.overlays.removeAll(existing)

    if (grid != null) {
        mapView.overlays.add(VisibilityGridOverlay(grid))
    }

    mapView.invalidate()
}

/**
 * Single overlay that draws all visibility grid cells in one draw() call.
 * Much more performant than creating individual Polygon objects per cell.
 */
private class VisibilityGridOverlay(
    private val grid: VisibilityGrid,
) : Overlay() {
    private val sunlitPaint = Paint().apply {
        color = SUNLIT_COLOR
        style = Paint.Style.FILL
    }
    private val shadedPaint = Paint().apply {
        color = SHADED_COLOR
        style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas, projection: Projection) {
        val halfRes = grid.resolution / 2
        val screenRect = Rect()

        for ((point, isVisible) in grid.points) {
            val topLeft = OsmGeoPoint(point.latitude + halfRes, point.longitude - halfRes)
            val bottomRight = OsmGeoPoint(point.latitude - halfRes, point.longitude + halfRes)

            val tlPixel = projection.toPixels(topLeft, null)
            val brPixel = projection.toPixels(bottomRight, null)

            screenRect.set(tlPixel.x, tlPixel.y, brPixel.x, brPixel.y)
            canvas.drawRect(screenRect, if (isVisible) sunlitPaint else shadedPaint)
        }
    }
}
