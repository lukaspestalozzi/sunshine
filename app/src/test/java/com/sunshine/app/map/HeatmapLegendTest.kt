package com.sunshine.app.map

import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// The heatmap legend (sun-exposure-heatmap spec, "Heatmap legend"; design D7 of add-sun-exposure-heatmap).
class HeatmapLegendTest {
    @Test
    fun `a winter day is labelled every 2 hours up to 8 h, at the band where each begins, the unit once`() {
        val bands = HeatmapBands(Duration.ofHours(8).plusMinutes(33))

        assertEquals(listOf(0 to "0", 4 to "2", 8 to "4", 12 to "6", 16 to "8 h"), legendLabels(bands))
    }

    @Test
    fun `a summer day is labelled up to 14 h`() {
        val labels = legendLabels(HeatmapBands(Duration.ofHours(15).plusMinutes(51)))

        assertEquals(listOf("0", "2", "4", "6", "8", "10", "12", "14 h"), labels.map { it.second })
    }

    @Test
    fun `polar night has the single label 0 h`() {
        assertEquals(listOf(0 to "0 h"), legendLabels(HeatmapBands(Duration.ZERO)))
    }

    @Test
    fun `the legend draws every band colour opaque, keeping its hue`() {
        val bands = HeatmapBands(Duration.ofHours(15).plusMinutes(51))

        val colours = legendColours(bands)

        assertTrue(colours.all { it ushr 24 == 0xFF }, colours.joinToString { Integer.toHexString(it) })
        assertEquals(bands.colours.map { it and 0xFFFFFF }, colours.map { it and 0xFFFFFF })
    }
}
