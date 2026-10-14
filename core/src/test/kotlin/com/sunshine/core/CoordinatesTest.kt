package com.sunshine.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

// Oracles: the place-search spec, "Coordinates as search text".
class CoordinatesTest {
    @Test
    fun `decimal degrees with a comma`() {
        val typed = assertInstanceOf(Coordinates.Decimal::class.java, parseCoordinates("46.6863, 7.8632"))

        assertEquals(46.6863, typed.point.latitude, EXACT)
        assertEquals(7.8632, typed.point.longitude, EXACT)
    }

    @Test
    fun `decimal degrees with a space`() {
        val typed = assertInstanceOf(Coordinates.Decimal::class.java, parseCoordinates("46.6863 7.8632"))

        assertEquals(GeoPoint(46.6863, 7.8632), typed.point)
    }

    @Test
    fun `decimal degrees as the map shows them`() {
        val typed = assertInstanceOf(Coordinates.Decimal::class.java, parseCoordinates("46.6863° N, 7.8632° E"))

        assertEquals(GeoPoint(46.6863, 7.8632), typed.point)
    }

    @Test
    fun `negative decimal degrees are south and west`() {
        val typed = assertInstanceOf(Coordinates.Decimal::class.java, parseCoordinates("-33.8688 -70.6483"))

        assertEquals(GeoPoint(-33.8688, -70.6483), typed.point)
    }

    @Test
    fun `hemisphere letters decide the order`() {
        val typed = assertInstanceOf(Coordinates.Decimal::class.java, parseCoordinates("7.8632 E 46.6863 N"))

        assertEquals(GeoPoint(46.6863, 7.8632), typed.point)
    }

    @Test
    fun `lower-case letters`() {
        val typed = assertInstanceOf(Coordinates.Decimal::class.java, parseCoordinates("33.8688 s, 70.6483 w"))

        assertEquals(GeoPoint(-33.8688, -70.6483), typed.point)
    }

    @Test
    fun `degrees, minutes, seconds with ASCII marks`() {
        val typed = assertInstanceOf(Coordinates.Dms::class.java, parseCoordinates("46°41'10.7\" N 7°51'47.5\" E"))

        assertEquals(46.686306, typed.point.latitude, DMS_TOLERANCE)
        assertEquals(7.863194, typed.point.longitude, DMS_TOLERANCE)
    }

    @Test
    fun `degrees, minutes, seconds as the map shows them`() {
        val typed = assertInstanceOf(Coordinates.Dms::class.java, parseCoordinates("46°41′10.7″ N, 7°51′47.5″ E"))

        assertEquals(46.686306, typed.point.latitude, DMS_TOLERANCE)
        assertEquals(7.863194, typed.point.longitude, DMS_TOLERANCE)
    }

    @Test
    fun `swisstopo's LV95 reference point`() {
        val typed = assertInstanceOf(Coordinates.SwissGrid::class.java, parseCoordinates("2'700'000, 1'100'000"))

        assertEquals(Lv95(2_700_000.0, 1_100_000.0), typed.lv95)
        assertEquals(46.04413, typed.point.latitude, LV95_TOLERANCE)
        assertEquals(8.73050, typed.point.longitude, LV95_TOLERANCE)
    }

    @Test
    fun `LV95 northing first without separators`() {
        val typed = assertInstanceOf(Coordinates.SwissGrid::class.java, parseCoordinates("1170652 2632479"))

        assertEquals(Lv95(2_632_479.0, 1_170_652.0), typed.lv95)
        assertEquals(46.6863, typed.point.latitude, LV95_TOLERANCE)
        assertEquals(7.8632, typed.point.longitude, LV95_TOLERANCE)
    }

    @Test
    fun `LV95 with typographic apostrophes`() {
        val typed = assertInstanceOf(Coordinates.SwissGrid::class.java, parseCoordinates("2’632’479 1’170’652"))

        assertEquals(Lv95(2_632_479.0, 1_170_652.0), typed.lv95)
        assertEquals(46.6863, typed.point.latitude, LV95_TOLERANCE)
        assertEquals(7.8632, typed.point.longitude, LV95_TOLERANCE)
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = [
            "95.0, 7.0",
            "85.06, 0",
            "46.6863 N, 7.8632",
            "46.6863",
            "eiger",
            "46°60'10.7\" N 7°51'47.5\" E",
            "46°41'60\" N 7°51'47.5\" E",
            "46°41'10.7\" 7°51'47.5\" E",
            "2'000'000, 1'000'000",
        ],
    )
    fun `not coordinates`(text: String) {
        assertNull(parseCoordinates(text))
    }

    private companion object {
        const val EXACT = 1e-9
        const val DMS_TOLERANCE = 0.00001
        const val LV95_TOLERANCE = 0.00002
    }
}
