package com.sunshine.core

import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// point-sunshine spec, "Sunshine at an instant": the sun's upper edge against the horizon.
class SunshineTest {
    @Test
    fun `sun while the upper edge is above a complete 10 degree horizon`() {
        val profile = constantHorizon(10.0, complete = true)

        for (instant in INSTANTS) {
            val expected = if (sunPosition(INTERLAKEN, instant).elevation + 0.266 > 10.0) Sunshine.SUN else Sunshine.SHADE
            assertEquals(expected, sunshineAt(profile, INTERLAKEN, instant), "$instant")
        }
        // The instants cover both states.
        assertEquals(setOf(Sunshine.SUN, Sunshine.SHADE), INSTANTS.map { sunshineAt(profile, INTERLAKEN, it) }.toSet())
    }

    @Test
    fun `an incomplete horizon decides shade below its bound and nothing above`() {
        val profile = constantHorizon(10.0, complete = false)

        for (instant in INSTANTS) {
            val expected = if (sunPosition(INTERLAKEN, instant).elevation + 0.266 > 10.0) Sunshine.UNKNOWN else Sunshine.SHADE
            assertEquals(expected, sunshineAt(profile, INTERLAKEN, instant), "$instant")
        }
    }

    private fun constantHorizon(
        angle: Double,
        complete: Boolean,
    ) = HorizonProfile(
        eyeHeight = 568.0,
        angles = DoubleArray(AZIMUTH_COUNT) { angle },
        complete = BooleanArray(AZIMUTH_COUNT) { complete },
    )

    private companion object {
        val INTERLAKEN = GeoPoint(46.6863, 7.8632)

        // 2025-12-21 in UTC+1: 02:00 (night), 09:00, 10:30, 12:00, 14:30, 16:00.
        val INSTANTS =
            listOf("01:00", "08:00", "09:30", "11:00", "13:30", "15:00").map { Instant.parse("2025-12-21T$it:00Z") }
    }
}
