package com.sunshine.app.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ResolutionTest {
    @Test
    fun `presets have the values of the settings spec`() {
        assertEquals(Resolution(4, 10, 16, 15), Preset.FAST.resolution(Resolution.NORMAL))
        assertEquals(Resolution(2, 5, 8, 10), Preset.NORMAL.resolution(Resolution.NORMAL))
        assertEquals(Resolution(1, 5, 4, 10), Preset.DETAILED.resolution(Resolution.NORMAL))
    }

    @Test
    fun `Custom uses the custom values`() {
        val custom = Resolution(3, 20, 12, 30)

        assertEquals(custom, Preset.CUSTOM.resolution(custom))
    }

    @ParameterizedTest(name = "cell {0} dp -> {1} dp")
    @CsvSource("0, 1", "1, 1", "8, 8", "9, 8")
    fun `Sun and shade cells are clamped to 1 to 8 dp`(
        cell: Int,
        clamped: Int,
    ) {
        assertEquals(clamped, Resolution(cell, 5, 8, 10).clamped().sunShadeCellDp)
    }

    @ParameterizedTest(name = "cell {0} dp -> {1} dp")
    @CsvSource("0, 4", "4, 4", "32, 32", "40, 32")
    fun `Sun hours cells are clamped to 4 to 32 dp`(
        cell: Int,
        clamped: Int,
    ) {
        assertEquals(clamped, Resolution(2, 5, cell, 10).clamped().sunHoursCellDp)
    }

    @ParameterizedTest(name = "step {0} min -> {1} min")
    @CsvSource("7, 5", "12, 10", "45, 30", "1, 5", "20, 20")
    fun `steps become the nearest allowed value`(
        step: Int,
        clamped: Int,
    ) {
        val resolution = Resolution(2, step, 8, step).clamped()

        assertEquals(clamped, resolution.sunShadeStepMinutes)
        assertEquals(clamped, resolution.sunHoursStepMinutes)
    }
}
