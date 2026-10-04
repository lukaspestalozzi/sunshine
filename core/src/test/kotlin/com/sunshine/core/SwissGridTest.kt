package com.sunshine.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

// Oracles: swisstopo, "Approximate formulas for the transformation between Swiss projection
// coordinates and WGS84" (December 2016), section 1, and the formulas evaluated by hand.
class SwissGridTest {
    @Test
    fun `swisstopo's worked example`() {
        val lv95 = checkNotNull(toLv95(GeoPoint(dms(46, 2, 38.87), dms(8, 43, 49.79))))

        assertEquals(2_699_999.76, lv95.easting, 0.01)
        assertEquals(1_099_999.97, lv95.northing, 0.01)
        // Its reference values, which the formulas meet to better than 1 m.
        assertEquals(2_700_000.0, lv95.easting, 1.0)
        assertEquals(1_100_000.0, lv95.northing, 1.0)
    }

    @Test
    fun `Interlaken`() {
        val lv95 = checkNotNull(toLv95(GeoPoint(46.6863, 7.8632)))

        assertEquals(2_632_479.47, lv95.easting, 0.01)
        assertEquals(1_170_652.02, lv95.northing, 0.01)
    }

    @ParameterizedTest(name = "{0}, {1}")
    @CsvSource("45.81, 8.0", "47.81, 8.0", "46.8, 5.95", "46.8, 10.5")
    fun `defined on the edges of the area of use`(
        latitude: Double,
        longitude: Double,
    ) {
        assertNotNull(toLv95(GeoPoint(latitude, longitude)))
    }

    @ParameterizedTest(name = "{0}, {1}")
    @CsvSource("45.8099, 8.0", "47.8101, 8.0", "46.8, 5.9499", "46.8, 10.5001", "47.4211, 10.9853")
    fun `undefined outside the area of use`(
        latitude: Double,
        longitude: Double,
    ) {
        assertNull(toLv95(GeoPoint(latitude, longitude)))
    }

    private fun dms(
        degrees: Int,
        minutes: Int,
        seconds: Double,
    ) = degrees + minutes / 60.0 + seconds / 3600.0
}
