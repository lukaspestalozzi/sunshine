package com.sunshine.app.map

import com.sunshine.core.MapArea
import com.sunshine.core.Sunshine

/**
 * The hours of sun of every pixel of [area]'s north-up raster ([OverlayRaster]) on one day, as
 * counts of slider steps (sun-exposure-heatmap spec, "Sun hours of a cell"; design D2 of
 * add-sun-exposure-heatmap): [sun] and [unknown] steps per pixel, row-major, out of [steps].
 */
class SunHours(
    val area: MapArea,
    val width: Int,
    val height: Int,
    val steps: Int,
    val sun: ShortArray,
    val unknown: ShortArray,
) {
    /** Bytes taken by the counts, e.g. to budget a cache of days. */
    val bytes: Long get() = 2L * (sun.size + unknown.size)
}

/**
 * Counts, for every pixel of [day]'s raster, the slider steps at which its cell is sun or unknown.
 * Night steps without unknown cells are skipped: every cell is shade there. [day] must be complete.
 */
fun countSunHours(day: DayOverlay): SunHours {
    val raster = OverlayRaster(day.area)
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
    return SunHours(day.area, width, raster.height, day.steps.size, sun, unknown)
}

/**
 * The heatmap of [hours] (design D4 of add-sun-exposure-heatmap): each pixel in the colour of its
 * band, with the unknown hatching on top where some step is unknown, and the hatching alone where
 * every step is.
 */
fun renderSunHours(
    hours: SunHours,
    bands: HeatmapBands,
): OverlayImage {
    val pixels =
        IntArray(hours.width * hours.height) { i ->
            val unknown = hours.unknown[i].toInt()
            val hatched = unknown > 0 && isHatched(i % hours.width, i / hours.width)
            when {
                hatched -> UNKNOWN_ARGB
                unknown == hours.steps -> 0
                else -> bands.colours[bands.bandOf(hours.sun[i].toInt())]
            }
        }
    return OverlayImage(hours.width, hours.height, pixels, hours.area.corners())
}
