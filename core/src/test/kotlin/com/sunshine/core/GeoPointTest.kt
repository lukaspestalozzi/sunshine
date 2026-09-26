package com.sunshine.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class GeoPointTest {
    @ParameterizedTest
    @CsvSource("90.0, 0.0", "-90.0, 0.0", "0.0, 180.0", "0.0, -180.0")
    fun `accepts coordinates on the valid bounds`(
        latitude: Double,
        longitude: Double,
    ) {
        val point = GeoPoint(latitude, longitude)

        assertEquals(latitude, point.latitude)
        assertEquals(longitude, point.longitude)
    }

    @ParameterizedTest
    @ValueSource(doubles = [Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 90.0001, -90.0001])
    fun `rejects an invalid latitude`(latitude: Double) {
        assertThrows<IllegalArgumentException> { GeoPoint(latitude, 0.0) }
    }

    @ParameterizedTest
    @ValueSource(doubles = [Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 180.0001, -180.0001])
    fun `rejects an invalid longitude`(longitude: Double) {
        assertThrows<IllegalArgumentException> { GeoPoint(0.0, longitude) }
    }

    @Test
    fun `default location is in the Swiss Alps`() {
        assertEquals(GeoPoint(46.8182, 8.2275), DEFAULT_LOCATION)
    }
}
