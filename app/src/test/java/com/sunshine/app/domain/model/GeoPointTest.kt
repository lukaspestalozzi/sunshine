package com.sunshine.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GeoPointTest {

    @Test
    fun `creates GeoPoint with valid coordinates`() {
        val point = GeoPoint(latitude = 46.8182, longitude = 8.2275)

        assertEquals(46.8182, point.latitude, 0.0001)
        assertEquals(8.2275, point.longitude, 0.0001)
    }

    @Test
    fun `throws exception for latitude below -90`() {
        assertThrows(IllegalArgumentException::class.java) {
            GeoPoint(latitude = -91.0, longitude = 0.0)
        }
    }

    @Test
    fun `throws exception for latitude above 90`() {
        assertThrows(IllegalArgumentException::class.java) {
            GeoPoint(latitude = 91.0, longitude = 0.0)
        }
    }

    @Test
    fun `throws exception for longitude below -180`() {
        assertThrows(IllegalArgumentException::class.java) {
            GeoPoint(latitude = 0.0, longitude = -181.0)
        }
    }

    @Test
    fun `throws exception for longitude above 180`() {
        assertThrows(IllegalArgumentException::class.java) {
            GeoPoint(latitude = 0.0, longitude = 181.0)
        }
    }

    @Test
    fun `accepts boundary values`() {
        val northPole = GeoPoint(latitude = 90.0, longitude = 0.0)
        val southPole = GeoPoint(latitude = -90.0, longitude = 0.0)
        val dateLine = GeoPoint(latitude = 0.0, longitude = 180.0)
        val antiDateLine = GeoPoint(latitude = 0.0, longitude = -180.0)

        assertEquals(90.0, northPole.latitude, 0.0)
        assertEquals(-90.0, southPole.latitude, 0.0)
        assertEquals(180.0, dateLine.longitude, 0.0)
        assertEquals(-180.0, antiDateLine.longitude, 0.0)
    }

    @Test
    fun `DEFAULT is in Swiss Alps`() {
        val defaultPoint = GeoPoint.DEFAULT

        // Swiss Alps approximate bounds
        assertEquals(46.8182, defaultPoint.latitude, 0.5)
        assertEquals(8.2275, defaultPoint.longitude, 0.5)
    }
}
