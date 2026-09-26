package com.sunshine.core

import java.time.OffsetDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

// Expected values: investigations/sun-position-references.md.
class SunPositionTest {
    @ParameterizedTest(name = "{0}")
    @CsvSource(
        "2025-12-21T12:00+01:00, 173.5, 19.7",
        "2025-06-21T15:00+02:00, 225.4, 60.6",
        "2025-12-21T08:20+01:00,      , 0.9",
        "2025-12-21T08:11+01:00,      , -0.7",
        "2025-12-21T02:00+01:00,  46.9, -60.1",
    )
    fun `sun position in Interlaken matches the reference values`(
        time: String,
        azimuth: Double?,
        elevation: Double,
    ) {
        val position = sunPosition(INTERLAKEN, OffsetDateTime.parse(time).toInstant())

        if (azimuth != null) assertEquals(azimuth, position.azimuth, AZIMUTH_TOLERANCE)
        assertEquals(elevation, position.elevation, ELEVATION_TOLERANCE)
    }

    // Sunrise is at 08:10:14 +01:00; at 08:11 the shown elevation is still negative.
    @ParameterizedTest(name = "{0}")
    @CsvSource("2025-12-21T08:11+01:00, true", "2025-12-21T08:05+01:00, false")
    fun `sun counts as above the horizon from sunrise on`(
        time: String,
        aboveHorizon: Boolean,
    ) {
        val position = sunPosition(INTERLAKEN, OffsetDateTime.parse(time).toInstant())

        assertEquals(aboveHorizon, position.isAboveHorizon)
    }

    private companion object {
        val INTERLAKEN = GeoPoint(46.6863, 7.8632)
        const val AZIMUTH_TOLERANCE = 0.2
        const val ELEVATION_TOLERANCE = 0.1
    }
}
