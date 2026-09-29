package com.sunshine.app.map

import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// Labels of the heatmap legend (sun-exposure-heatmap spec, "Heatmap legend").
class HeatmapLegendTest {
    @Test
    fun `a winter day is labelled every 2 hours up to 8 h, at the band where each begins`() {
        val bands = HeatmapBands(Duration.ofHours(8).plusMinutes(33))

        assertEquals(listOf(0 to "0 h", 4 to "2 h", 8 to "4 h", 12 to "6 h", 16 to "8 h"), legendLabels(bands))
    }

    @Test
    fun `a summer day is labelled up to 14 h`() {
        val labels = legendLabels(HeatmapBands(Duration.ofHours(15).plusMinutes(51)))

        assertEquals(listOf("0 h", "2 h", "4 h", "6 h", "8 h", "10 h", "12 h", "14 h"), labels.map { it.second })
    }

    @Test
    fun `polar night has the single label 0 h`() {
        assertEquals(listOf(0 to "0 h"), legendLabels(HeatmapBands(Duration.ZERO)))
    }
}
