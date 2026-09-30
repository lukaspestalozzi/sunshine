package com.sunshine.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OfflineAreaTest {
    private val fixed = GeoBounds(south = 46.55, west = 7.85, north = 46.65, east = 7.95)

    @Test
    fun `map tiles of a fixed area`() {
        assertEquals(1998L, tileRange(fixed, 17).count)
        assertEquals(2752L, regionMapTileCount(fixed))
    }

    @Test
    fun `DEM tiles of a fixed area per zoom`() {
        val perZoom = regionDemTiles(fixed).associate { it.zoom to it.count }

        assertEquals(mapOf(14 to 63L, 13 to 20L, 12 to 20L, 11 to 30L, 10 to 169L), perZoom)
    }

    @Test
    fun `a margin of 150 km moves the southern edge by 150 km over 111_19 km per degree`() {
        val extended = GeoBounds(south = 46.6, west = 7.9, north = 46.6, east = 7.9).extend(150_000.0)

        assertEquals(46.6 - 1.3490, extended.south, 0.0001)
        assertEquals(46.6 + 1.3490, extended.north, 0.0001)
    }

    @Test
    fun `the visible area of a phone at zoom 11`() {
        val bounds = GeoBounds.of(MapArea(GeoPoint(46.5935, 7.9091), zoom = 11.0, widthDp = 400.0, heightDp = 850.0))

        assertEquals(46.4931, bounds.south, 0.0001)
        assertEquals(46.6937, bounds.north, 0.0001)
        assertEquals(7.8404, bounds.west, 0.0001)
        assertEquals(7.9778, bounds.east, 0.0001)

        val estimate = downloadEstimate(bounds)
        assertEquals(7406L, estimate.mapTiles)
        assertEquals(413L, estimate.demTiles)
        assertEquals(10.5, estimate.widthKm, 0.05)
        assertEquals(22.3, estimate.heightKm, 0.05)
        assertEquals(25L, estimate.minutes)
    }
}
