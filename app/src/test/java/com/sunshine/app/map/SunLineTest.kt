package com.sunshine.app.map

import org.junit.jupiter.api.Assertions.assertEquals
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
}
