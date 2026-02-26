package com.sunshine.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeatmapColorScaleTest {
    @Test
    fun `gamma preserves endpoints`() {
        assertEquals(0.0, applyHeatmapGamma(0.0), 0.001)
        assertEquals(1.0, applyHeatmapGamma(1.0), 0.001)
    }

    @Test
    fun `gamma compresses midpoint downward`() {
        // With gamma 3.0, 0.5 -> 0.125
        val result = applyHeatmapGamma(0.5)
        assertEquals(0.125, result, 0.001)
    }

    @Test
    fun `upper half of range gets more gradient space than lower half`() {
        val midGradient = applyHeatmapGamma(0.5)
        val upperRangeSpan = 1.0 - midGradient // gradient space for upper 50% of hours
        val lowerRangeSpan = midGradient // gradient space for lower 50% of hours
        assertTrue(
            "Upper range ($upperRangeSpan) should use more gradient than lower ($lowerRangeSpan)",
            upperRangeSpan > lowerRangeSpan,
        )
    }

    @Test
    fun `gamma clamps values outside 0-1`() {
        assertEquals(0.0, applyHeatmapGamma(-0.5), 0.001)
        assertEquals(1.0, applyHeatmapGamma(1.5), 0.001)
    }
}
