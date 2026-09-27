package com.sunshine.app.map

import com.sunshine.core.Sunshine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SunLineTest {
    // Screen coordinates: x to the right, y down; north is up on the north-up map.
    @ParameterizedTest(name = "azimuth {0}")
    @CsvSource("0, 0, -100", "90, 100, 0", "180, 0, 100", "270, -100, 0", "46.9, 73.02, -68.33")
    fun `line end points in the azimuth's screen direction`(
        azimuth: Double,
        x: Float,
        y: Float,
    ) {
        val end = sunLineEnd(azimuth, length = 100f)

        assertEquals(x, end.x, 0.01f)
        assertEquals(y, end.y, 0.01f)
    }

    // sun-position delta, "Sun direction line".
    @Test
    fun `solid in sun, dashed in shade or below the horizon, dotted while unknown or not computed`() {
        assertNull(sunLineDash(isAboveHorizon = true, sunshine = Sunshine.SUN))
        assertEquals(DASHED, sunLineDash(isAboveHorizon = true, sunshine = Sunshine.SHADE))
        assertEquals(DOTTED, sunLineDash(isAboveHorizon = true, sunshine = Sunshine.UNKNOWN))
        assertEquals(DOTTED, sunLineDash(isAboveHorizon = true, sunshine = null))
        assertEquals(DASHED, sunLineDash(isAboveHorizon = false, sunshine = Sunshine.UNKNOWN))
        assertEquals(DASHED, sunLineDash(isAboveHorizon = false, sunshine = null))
    }

    private companion object {
        val DASHED = SunLineDash(on = 8f, off = 6f)
        val DOTTED = SunLineDash(on = 2f, off = 6f)
    }
}
