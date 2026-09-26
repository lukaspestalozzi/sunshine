package com.sunshine.app.map

import com.sunshine.core.GeoPoint
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class CoordinateFormatTest {
    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "46.68630 | 7.86320 | 46.6863° N, 7.8632° E",
            "-33.86880 | -70.64830 | 33.8688° S, 70.6483° W",
            "46.68635 | 7.86320 | 46.6864° N, 7.8632° E",
            "-0.00001 | 0.0 | 0.0000° N, 0.0000° E",
        ],
    )
    fun `formats decimal degrees with 4 decimals and hemisphere letters`(
        latitude: Double,
        longitude: Double,
        expected: String,
    ) {
        assertEquals(expected, formatCoordinates(GeoPoint(latitude, longitude)))
    }

    @Test
    fun `uses a decimal dot regardless of the device locale`() {
        val originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("de-CH"))
        try {
            assertEquals("46.6863° N, 7.8632° E", formatCoordinates(GeoPoint(46.68630, 7.86320)))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
