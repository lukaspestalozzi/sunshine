package com.sunshine.app.map

import java.time.Duration
import kotlin.math.cbrt
import kotlin.math.pow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// Colour scale of the heatmap (sun-exposure-heatmap spec, "Colour scale"; design D3 of
// add-sun-exposure-heatmap).
class HeatmapColourTest {
    @Test
    fun `a winter day has 18 bands of 30 minutes`() {
        val bands = HeatmapBands(hm(8, 33))

        assertEquals(18, bands.count)
        assertEquals(10, bands.bandOf(minutes(hm(5, 20))))
        assertEquals(9, bands.bandOf(minutes(hm(4, 50))))
        assertEquals(0, bands.bandOf(0))
        // At or beyond the day length: the last band.
        assertEquals(17, bands.bandOf(minutes(hm(8, 40))))
    }

    @Test
    fun `a summer day has 32 bands, polar night one`() {
        assertEquals(32, HeatmapBands(hm(15, 51)).count)
        assertEquals(1, HeatmapBands(Duration.ZERO).count)
        assertEquals(0, HeatmapBands(Duration.ZERO).bandOf(0))
        // A day length of exactly 8 h has 16 bands.
        assertEquals(16, HeatmapBands(hm(8, 0)).count)
    }

    @Test
    fun `the first band is the shade tint, the last light amber, all opaque`() {
        // The overlay layer applies the chosen opacity (design D5 of add-settings).
        for (count in listOf(hm(8, 33), hm(15, 51))) {
            val colours = HeatmapBands(count).colours

            assertEquals(0xFF455A64.toInt(), colours.first())
            assertEquals(0xFFFFE0A3.toInt(), colours.last())
            assertTrue(colours.all { it ushr 24 == 0xFF }, colours.joinToString { Integer.toHexString(it) })
        }
        assertEquals(listOf(0xFF455A64.toInt()), HeatmapBands(Duration.ZERO).colours.toList())
    }

    @Test
    fun `lightness rises strictly from band to band`() {
        val lightness = HeatmapBands(hm(15, 51)).colours.map(::oklabLightness)

        for (i in 1 until lightness.size) {
            assertTrue(lightness[i] > lightness[i - 1], "band $i: ${lightness[i]} after ${lightness[i - 1]}")
        }
    }

    // OKLab L of an ARGB colour (Björn Ottosson's matrices), independent of the implementation.
    private fun oklabLightness(argb: Int): Double {
        fun linear(channel: Int): Double = (channel / 255.0).let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        val r = linear(argb shr 16 and 0xFF)
        val g = linear(argb shr 8 and 0xFF)
        val b = linear(argb and 0xFF)
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s
    }

    private fun hm(
        hours: Long,
        minutes: Long,
    ): Duration = Duration.ofHours(hours).plusMinutes(minutes)

    private fun minutes(duration: Duration): Int = duration.toMinutes().toInt()
}
