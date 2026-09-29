package com.sunshine.app.map

import com.sunshine.core.GeoPoint
import com.sunshine.core.MapArea
import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// Drawing the heatmap (sun-exposure-heatmap spec, "Colour scale", "Unknown time in the heatmap";
// design D4 of add-sun-exposure-heatmap).
class RenderSunHoursTest {
    private val bands = HeatmapBands(Duration.ofHours(8).plusMinutes(33))

    @Test
    fun `a pixel unknown at every step is hatched only`() {
        val image = renderSunHours(hours(sun = 0, unknown = 288), bands)

        assertPixels(image) { x, y -> if (isHatched(x, y)) UNKNOWN_ARGB else 0 }
    }

    @Test
    fun `a pixel with some unknown steps is hatched over its band colour`() {
        // 57 steps of sun, 4 h 45 min: band 9.
        val image = renderSunHours(hours(sun = 57, unknown = 2), bands)

        assertPixels(image) { x, y -> if (isHatched(x, y)) UNKNOWN_ARGB else bands.colours[9] }
    }

    @Test
    fun `a fully known pixel has its band colour, shade all day the first band`() {
        assertPixels(renderSunHours(hours(sun = 57, unknown = 0), bands)) { _, _ -> bands.colours[9] }
        assertPixels(renderSunHours(hours(sun = 0, unknown = 0), bands)) { _, _ -> bands.colours[0] }
    }

    @Test
    fun `the image covers the area of the counts`() {
        val image = renderSunHours(hours(sun = 0, unknown = 0), bands)

        assertEquals(WIDTH, image.width)
        assertEquals(HEIGHT, image.height)
        assertEquals(AREA.corners(), image.corners)
    }

    private fun assertPixels(
        image: OverlayImage,
        expected: (Int, Int) -> Int,
    ) {
        for (y in 0 until image.height) {
            for (x in 0 until image.width) assertEquals(expected(x, y), image.pixels[y * image.width + x], "pixel $x, $y")
        }
    }

    // Counts of [sun] and [unknown] steps out of 288 at every pixel.
    private fun hours(
        sun: Int,
        unknown: Int,
    ) = SunHours(AREA, WIDTH, HEIGHT, 288, ShortArray(WIDTH * HEIGHT) { sun.toShort() }, ShortArray(WIDTH * HEIGHT) { unknown.toShort() })

    private companion object {
        const val WIDTH = 16
        const val HEIGHT = 12
        val AREA = MapArea(GeoPoint(46.6863, 7.8632), zoom = 12.0, widthDp = WIDTH.toDouble(), heightDp = HEIGHT.toDouble())
    }
}
