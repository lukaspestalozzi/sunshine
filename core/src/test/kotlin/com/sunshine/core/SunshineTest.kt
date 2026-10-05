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

    // Bounds 2° and 12.09°: eye at 500 m, a tile 20 km away unavailable (point-sunshine spec).
    @Test
    fun `an incomplete horizon decides sun above its upper bound`() {
        val profile = HorizonProfile(568.0, DoubleArray(AZIMUTH_COUNT) { 2.0 }, DoubleArray(AZIMUTH_COUNT) { 12.09 })

        assertEquals(Sunshine.SUN, sunshine(profile, 180.0, 20.0))
        assertEquals(Sunshine.UNKNOWN, sunshine(profile, 180.0, 5.0))
        assertEquals(Sunshine.SHADE, sunshine(profile, 180.0, 1.5))
    }

    @Test
    fun `between a complete and an incomplete bin the larger upper bound applies`() {
        // Bin 720 (180°) complete at 2°, bin 721 (180.25°) incomplete up to 12.09°.
        val upper = DoubleArray(AZIMUTH_COUNT) { if (it == 721) 12.09 else 2.0 }
        val profile = HorizonProfile(568.0, DoubleArray(AZIMUTH_COUNT) { 2.0 }, upper)

        assertEquals(Sunshine.SUN, sunshine(profile, 180.0, 8.0))
        assertEquals(Sunshine.UNKNOWN, sunshine(profile, 180.05, 8.0))
        assertEquals(Sunshine.SUN, sunshine(profile, 180.05, 12.1))
    }

    // An incomplete horizon without a known upper bound: anything above its angle may be blocked.
    private fun constantHorizon(
        angle: Double,
        complete: Boolean,
    ) = HorizonProfile(
        eyeHeight = 568.0,
        angles = DoubleArray(AZIMUTH_COUNT) { angle },
        upper = DoubleArray(AZIMUTH_COUNT) { if (complete) angle else 90.0 },
    )

    private companion object {
        val INTERLAKEN = GeoPoint(46.6863, 7.8632)

        // 2025-12-21 in UTC+1: 02:00 (night), 09:00, 10:30, 12:00, 14:30, 16:00.
        val INSTANTS =
            listOf("01:00", "08:00", "09:30", "11:00", "13:30", "15:00").map { Instant.parse("2025-12-21T$it:00Z") }
    }
}
