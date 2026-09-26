package com.sunshine.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ElevationTilesTest {
    @Test
    fun `a point inside a tile needs only that tile`() {
        assertEquals(setOf(TileKey(12, 2137, 1445)), tilesFor(GeoPoint(46.6863, 7.8632), ZOOM, SIZE))
    }

    // Longitude 7.91016° lies 0.02 px east of the border between tiles x 2137 and 2138.
    @Test
    fun `samples on both sides of a vertical tile border need both tiles`() {
        assertEquals(
            setOf(TileKey(12, 2137, 1445), TileKey(12, 2138, 1445)),
            tilesFor(GeoPoint(46.6863, 7.91016), ZOOM, SIZE),
        )
    }

    @Test
    fun `samples around a tile corner need four tiles`() {
        assertEquals(
            setOf(TileKey(12, 2137, 1447), TileKey(12, 2138, 1447), TileKey(12, 2137, 1448), TileKey(12, 2138, 1448)),
            tilesFor(GeoPoint(46.55886, 7.91016), ZOOM, SIZE),
        )
    }

    @Test
    fun `no tiles outside the web mercator latitude range`() {
        assertEquals(emptySet<TileKey>(), tilesFor(GeoPoint(85.1, 7.0), ZOOM, SIZE))
        assertEquals(emptySet<TileKey>(), tilesFor(GeoPoint(-85.1, 7.0), ZOOM, SIZE))
    }

    @Test
    fun `samples at the antimeridian wrap to the first tile column`() {
        val tiles = tilesFor(GeoPoint(46.6863, 180.0), ZOOM, SIZE)

        assertEquals(setOf(4095, 0), tiles.map { it.x }.toSet())
    }

    // 179.9999° is 0.58 px (> half a pixel) west of the antimeridian: both samples are in the last column.
    @Test
    fun `samples just west of the antimeridian do not wrap`() {
        val tiles = tilesFor(GeoPoint(46.6863, 179.9999), ZOOM, SIZE)

        assertEquals(setOf(4095), tiles.map { it.x }.toSet())
    }

    private companion object {
        const val ZOOM = 12
        const val SIZE = 512
    }
}
