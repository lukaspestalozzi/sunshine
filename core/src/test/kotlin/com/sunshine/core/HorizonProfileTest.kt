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
            complete = BooleanArray(AZIMUTH_COUNT) { it != 361 },
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
}
