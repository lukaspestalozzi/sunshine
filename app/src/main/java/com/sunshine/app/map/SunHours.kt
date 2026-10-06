package com.sunshine.app.map

import com.sunshine.core.MapArea
import com.sunshine.core.Sunshine
import kotlin.math.min

/**
 * The hours of sun of every pixel of [area]'s north-up raster at one pixel per [cellDp] dp
 * ([OverlayRaster]) on one day, as counts of steps of [stepMinutes] minutes (sun-exposure-heatmap
 * spec, "Sun hours of a cell"; design D2, D9 of add-sun-exposure-heatmap): [sun] and [unknown]
 * steps per pixel, row-major, out of [steps].
 */
class SunHours(
    val area: MapArea,
    val width: Int,
    val height: Int,
    val steps: Int,
    val sun: ShortArray,
    val unknown: ShortArray,
    val stepMinutes: Int,
    val cellDp: Double,
) {
    /** Bytes taken by the counts, e.g. to budget a cache of days. */
    val bytes: Long get() = 2L * (sun.size + unknown.size)
}

/**
 * Counts, for every pixel of [day]'s raster (one per cell), the steps at which its cell is sun or
 * unknown. Night steps without unknown cells are skipped: every cell is shade there. [day] must be
 * complete.
 */
fun countSunHours(day: DayOverlay): SunHours {
    val raster = OverlayRaster(day.area, day.cellDp)
    val width = raster.width
    val sun = ShortArray(width * raster.height)
    val unknown = ShortArray(width * raster.height)
    for (step in day.steps) {
        val grid = checkNotNull(day.gridAt(step)) { "step $step is not computed" }
        if (day.isNight(step) && !grid.hasUnknown) continue
        for (y in 0 until raster.height) {
            val latitude = raster.latitudes[y]
            for (x in 0 until width) {
                val i = y * width + x
                when (grid.stateAt(latitude, raster.longitudes[x])) {
                    Sunshine.SUN -> sun[i]++
                    Sunshine.UNKNOWN -> unknown[i]++
                    Sunshine.SHADE, null -> Unit
                }
            }
        }
    }
    return SunHours(day.area, width, raster.height, day.steps.size, sun, unknown, day.stepMinutes, day.cellDp)
}

/**
 * The heatmap of [hours] at one pixel per dp (design D4, D9 of add-sun-exposure-heatmap): each pixel
 * in the colour of the band of the count under it, with the unknown hatching on top where some step
 * is unknown, and the hatching alone where every step is.
 */
fun renderSunHours(
    hours: SunHours,
    bands: HeatmapBands,
): OverlayImage {
    val image = OverlayRaster(hours.area)
    // Each count's colour once, and each column's and row's count once (design D8 of polish-overlay).
    val colours =
        IntArray(hours.sun.size) { count ->
            if (hours.unknown[count].toInt() == hours.steps) 0 else bands.colours[bands.bandOf(hours.sun[count] * hours.stepMinutes)]
        }
    val columns = IntArray(image.width) { x -> countIndex(hours, x + 0.5, 0.5) }
    val rows = IntArray(image.height) { y -> countIndex(hours, 0.5, y + 0.5) }
    val pixels =
        IntArray(image.width * image.height) { i ->
            val x = i % image.width
            val y = i / image.width
            val count = rows[y] + columns[x]
            if (hours.unknown[count] > 0 && isHatched(x, y)) UNKNOWN_ARGB else colours[count]
        }
    return OverlayImage(image.width, image.height, pixels, hours.area.corners())
}

/** The index of the count under the point [xDp], [yDp] dp from the area's north-west corner. */
fun countIndex(
    hours: SunHours,
    xDp: Double,
    yDp: Double,
): Int {
    val column = min((xDp / hours.cellDp).toInt(), hours.width - 1)
    val row = min((yDp / hours.cellDp).toInt(), hours.height - 1)
    return row * hours.width + column
}
