package com.sunshine.app.map

import com.sunshine.core.GeoPoint
import com.sunshine.core.MapArea
import com.sunshine.core.ShadeGrid
import com.sunshine.core.Sunshine
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh

/** A north-up ARGB image ([width] × [height], row-major) of the map area between [corners] (NW, NE, SE, SW). */
class OverlayImage(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
    val corners: List<GeoPoint>,
)

/**
 * The [grid] drawn north-up over its own area at one pixel per dp (design D9 of
 * add-sun-shade-overlay): shade tinted, sun clear, unknown hatched, each pixel from the cell under
 * its centre.
 */
fun renderOverlay(grid: ShadeGrid): OverlayImage {
    val raster = OverlayRaster(grid.area)
    val width = raster.width
    val pixels = IntArray(width * raster.height)
    for (y in 0 until raster.height) {
        val latitude = raster.latitudes[y]
        for (x in 0 until width) {
            pixels[y * width + x] =
                when (grid.stateAt(latitude, raster.longitudes[x])) {
                    Sunshine.SHADE -> SHADE_ARGB
                    Sunshine.UNKNOWN -> if (isHatched(x, y)) UNKNOWN_ARGB else TRANSPARENT
                    Sunshine.SUN, null -> TRANSPARENT
                }
        }
    }
    return OverlayImage(width, raster.height, pixels, grid.area.corners())
}

/**
 * The pixel centres of the north-up raster of [area] at one pixel per [pixelDp] dp: [latitudes] by
 * row, [longitudes] by column. The raster starts at the area's north-west corner and covers it
 * whole, so a last row or column may reach past the area. The overlay uses 1 dp, the heatmap's
 * counts one pixel per 8 dp cell (design D2, D9 of add-sun-exposure-heatmap).
 */
class OverlayRaster(
    area: MapArea,
    pixelDp: Double = 1.0,
) {
    val width: Int = ceil(area.widthDp / pixelDp - EDGE_TOLERANCE).toInt()
    val height: Int = ceil(area.heightDp / pixelDp - EDGE_TOLERANCE).toInt()
    val latitudes: DoubleArray
    val longitudes: DoubleArray

    init {
        val world = MAP_TILE_DP * 2.0.pow(area.zoom)
        val sinLat = sin(Math.toRadians(area.center.latitude))
        val left = (area.center.longitude + 180.0) / 360.0 * world - area.widthDp / 2.0
        val top = (0.5 - ln((1 + sinLat) / (1 - sinLat)) / (4 * PI)) * world - area.heightDp / 2.0
        latitudes = DoubleArray(height) { y -> Math.toDegrees(atan(sinh(PI * (1 - 2 * (top + (y + 0.5) * pixelDp) / world)))) }
        longitudes = DoubleArray(width) { x -> ((left + (x + 0.5) * pixelDp) / world * 360.0 % 360.0 + 360.0) % 360.0 - 180.0 }
    }
}

/** Whether pixel ([x], [y]) lies on a stripe of the unknown hatching. */
internal fun isHatched(
    x: Int,
    y: Int,
): Boolean = (x + y) % HATCH_PERIOD < HATCH_WIDTH

private const val MAP_TILE_DP = 512.0
private const val TRANSPARENT = 0

// An area 850.0000001 dp high still has 850 rows.
private const val EDGE_TOLERANCE = 1e-6

// #455A64: dark blue-grey. Opaque here; the overlay layer draws it at the chosen opacity, 60 % by
// default, which keeps the topographic map readable (design D5 of add-settings).
internal const val SHADE_ARGB = 0xFF455A64.toInt()

// #9E9E9E in diagonal stripes 2 dp wide every 8 dp, opaque like the shade tint.
internal const val UNKNOWN_ARGB = 0xFF9E9E9E.toInt()
internal const val HATCH_PERIOD = 8
internal const val HATCH_WIDTH = 2
