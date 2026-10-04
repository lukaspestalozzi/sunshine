package com.sunshine.app.map

import com.sunshine.app.settings.CoordinateFormat
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

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "46.8182 | 8.2275 | 46°49′05.5″ N, 8°13′39.0″ E",
            "46.6863 | 7.8632 | 46°41′10.7″ N, 7°51′47.5″ E",
            "46.99999 | -0.5 | 47°00′00.0″ N, 0°30′00.0″ W",
            "-33.8688 | -70.6483 | 33°52′07.7″ S, 70°38′53.9″ W",
        ],
    )
    fun `formats degrees, minutes and seconds to a tenth with carries`(
        latitude: Double,
        longitude: Double,
        expected: String,
    ) {
        assertEquals(expected, formatCoordinates(GeoPoint(latitude, longitude), CoordinateFormat.DMS))
    }

    @Test
    fun `formats swisstopo's worked example in LV95 as its reference values`() {
        val point = GeoPoint(46 + 2 / 60.0 + 38.87 / 3600, 8 + 43 / 60.0 + 49.79 / 3600)

        assertEquals("2'700'000, 1'100'000", formatCoordinates(point, CoordinateFormat.LV95))
    }

    @Test
    fun `formats Interlaken in LV95`() {
        assertEquals("2'632'479, 1'170'652", formatCoordinates(GeoPoint(46.6863, 7.8632), CoordinateFormat.LV95))
    }

    @Test
    fun `falls back to decimal degrees outside the Swiss grid`() {
        assertEquals("47.4211° N, 10.9853° E", formatCoordinates(GeoPoint(47.4211, 10.9853), CoordinateFormat.LV95))
    }

    @Test
    fun `uses a decimal dot regardless of the device locale`() {
        val originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("de-CH"))
        try {
            assertEquals("46.6863° N, 7.8632° E", formatCoordinates(GeoPoint(46.68630, 7.86320)))
            assertEquals("46°41′10.7″ N, 7°51′47.5″ E", formatCoordinates(GeoPoint(46.6863, 7.8632), CoordinateFormat.DMS))
            assertEquals("2'632'479, 1'170'652", formatCoordinates(GeoPoint(46.6863, 7.8632), CoordinateFormat.LV95))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
