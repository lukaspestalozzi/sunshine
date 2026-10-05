package com.sunshine.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HorizonProfileTest {
    // Bin 360 (90°) is complete, bin 361 (90.25°) is not.
    private val profile =
        HorizonProfile(
            eyeHeight = 0.0,
            angles = DoubleArray(AZIMUTH_COUNT) { it.toDouble() },
            upper = DoubleArray(AZIMUTH_COUNT) { if (it == 361) 1000.0 else it.toDouble() },
        )

    @Test
    fun `angles are interpolated linearly between bins and wrap at north`() {
        assertEquals(360.5, profile.angleAt(90.125), 1e-9)
        assertEquals(1439.0 / 2, profile.angleAt(359.875), 1e-9)
    }

    @Test
    fun `completeness needs only the bins that contribute`() {
        assertTrue(profile.isCompleteAt(90.0))
        assertFalse(profile.isCompleteAt(90.1))
        assertFalse(profile.isCompleteAt(90.25))
    }

    @Test
    fun `the upper bound is the larger of the contributing bins`() {
        assertEquals(360.0, profile.upperAt(90.0), 1e-9)
        assertEquals(1000.0, profile.upperAt(90.1), 1e-9)
        assertEquals(362.0, profile.upperAt(90.5), 1e-9)
    }
}
