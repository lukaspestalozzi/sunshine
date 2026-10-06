package com.sunshine.app.map

import com.sunshine.core.GeoPoint
import com.sunshine.core.MapArea
import java.time.Duration
import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// Drawing the heatmap (sun-exposure-heatmap spec, "Colour scale", "Unknown time in the heatmap";
// design D4 of add-sun-exposure-heatmap).
class RenderSunHoursTest {
    private val bands = HeatmapBands(Duration.ofHours(8).plusMinutes(33))

    @Test
    fun `a pixel unknown at every step is hatched only`() {
        val image = renderSunHours(hours(sun = 0, unknown = 144), bands)

        assertPixels(image) { x, y -> if (isHatched(x, y)) UNKNOWN_ARGB else 0 }
    }

    @Test
    fun `a pixel with some unknown steps is hatched over its band colour`() {
        // 29 steps of 10 min, 4 h 50 min: band 9.
        val image = renderSunHours(hours(sun = 29, unknown = 1), bands)

        assertPixels(image) { x, y -> if (isHatched(x, y)) UNKNOWN_ARGB else bands.colours[9] }
    }

    @Test
    fun `a fully known pixel has its band colour, shade all day the first band`() {
        assertPixels(renderSunHours(hours(sun = 29, unknown = 0), bands)) { _, _ -> bands.colours[9] }
        assertPixels(renderSunHours(hours(sun = 0, unknown = 0), bands)) { _, _ -> bands.colours[0] }
    }

    @Test
    fun `the image has one pixel per dp over the area, each from the 8 dp count under it`() {
        // The left 8 dp column of counts is shade all day, the other one 29 steps of sun.
        val sun = ShortArray(COLUMNS * ROWS) { if (it % COLUMNS == 0) 0 else 29 }
        val image = renderSunHours(SunHours(AREA, COLUMNS, ROWS, 144, sun, ShortArray(COLUMNS * ROWS), STEP_MINUTES, CELL_DP), bands)

        assertEquals(WIDTH, image.width)
        assertEquals(HEIGHT, image.height)
        assertEquals(AREA.corners(), image.corners)
        assertPixels(image) { x, _ -> if (x < 8) bands.colours[0] else bands.colours[9] }
    }

    // Design D8 of polish-overlay: colours per count and indices per row and column change no pixel.
    @Test
    fun `a phone-sized image equals the per-pixel reference`() {
        val area = MapArea(GeoPoint(46.6863, 7.8632), zoom = 12.0, widthDp = 411.0, heightDp = 891.0)
        val columns = 52
        val rows = 112
        val random = Random(7)
        // Sun all day, shade all day, partly and fully unknown cells.
        val unknown = ShortArray(columns * rows) { listOf(0, 0, 3, 144)[random.nextInt(4)].toShort() }
        val sun = ShortArray(columns * rows) { random.nextInt(0, 145 - unknown[it]).toShort() }
        val hours = SunHours(area, columns, rows, 144, sun, unknown, STEP_MINUTES, CELL_DP)

        assertArrayEquals(reference(hours).pixels, renderSunHours(hours, bands).pixels)
    }

    // The image as drawn before design D8 of polish-overlay: every value per pixel.
    private fun reference(hours: SunHours): OverlayImage {
        val raster = OverlayRaster(hours.area)
        val pixels =
            IntArray(raster.width * raster.height) { i ->
                val x = i % raster.width
                val y = i / raster.width
                val count = countIndex(hours, x + 0.5, y + 0.5)
                val unknown = hours.unknown[count].toInt()
                when {
                    unknown > 0 && isHatched(x, y) -> UNKNOWN_ARGB
                    unknown == hours.steps -> 0
                    else -> bands.colours[bands.bandOf(hours.sun[count] * hours.stepMinutes)]
                }
            }
        return OverlayImage(raster.width, raster.height, pixels, hours.area.corners())
    }

    private fun assertPixels(
        image: OverlayImage,
        expected: (Int, Int) -> Int,
    ) {
        for (y in 0 until image.height) {
            for (x in 0 until image.width) assertEquals(expected(x, y), image.pixels[y * image.width + x], "pixel $x, $y")
        }
    }

    // Counts of [sun] and [unknown] steps out of 144 at every 8 dp pixel.
    private fun hours(
        sun: Int,
        unknown: Int,
    ) = SunHours(
        AREA,
        COLUMNS,
        ROWS,
        144,
        ShortArray(COLUMNS * ROWS) { sun.toShort() },
        ShortArray(COLUMNS * ROWS) { unknown.toShort() },
        STEP_MINUTES,
        CELL_DP,
    )

    private companion object {
        const val WIDTH = 16
        const val HEIGHT = 12
        const val CELL_DP = 8.0
        const val STEP_MINUTES = 10

        // ⌈16 / 8⌉ × ⌈12 / 8⌉ counts.
        const val COLUMNS = 2
        const val ROWS = 2
        val AREA = MapArea(GeoPoint(46.6863, 7.8632), zoom = 12.0, widthDp = WIDTH.toDouble(), heightDp = HEIGHT.toDouble())
    }
}
