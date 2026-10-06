package com.sunshine.core

import kotlin.math.abs
import kotlin.math.sin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// The states of a whole raster in one pass equal those of single points (design D8 of polish-overlay).
class ShadeGridStatesTest {
    @Test
    fun `the states of a raster equal stateAt at every point, outside the grid included`() {
        val grid = RIDGES.grid(AREA, SunPosition(130.0, 12.0, true))

        assertSameStates(grid)
    }

    @Test
    fun `a night grid gives the same states as stateAt`() {
        val sweep = SunShadeSweep(AREA, SunPosition(0.0, -30.0, false))
        val grid = sweep.night(sweep.groundTiles().associateWith(RIDGES::tile))

        assertSameStates(grid)
    }

    private fun assertSameStates(grid: ShadeGrid) {
        // A raster reaching 10 % past the area on every side.
        val corners = AREA.corners()
        val north = corners.maxOf { it.latitude }
        val south = corners.minOf { it.latitude }
        val west = corners.minOf { it.longitude }
        val east = corners.maxOf { it.longitude }
        val latitudes = DoubleArray(ROWS) { north + (south - north) * (it - 0.1 * ROWS) / (0.8 * ROWS) }
        val longitudes = DoubleArray(COLUMNS) { west + (east - west) * (it - 0.1 * COLUMNS) / (0.8 * COLUMNS) }

        val states = grid.statesAt(latitudes, longitudes)

        assertEquals(ROWS * COLUMNS, states.size)
        var outside = 0
        for (r in 0 until ROWS) {
            for (c in 0 until COLUMNS) {
                val expected = grid.stateAt(latitudes[r], longitudes[c])
                if (expected == null) outside++
                assertEquals(expected, states[r * COLUMNS + c], "row $r, column $c")
            }
        }
        assertTrue(outside in 1 until ROWS * COLUMNS, "$outside points outside the grid")
    }

    private companion object {
        val AREA = MapArea(GeoPoint(46.6, 7.9), zoom = 12.0, widthDp = 120.0, heightDp = 160.0)
        const val ROWS = 90
        const val COLUMNS = 70
        val RIDGES =
            SyntheticTerrain { lat, lon -> 1000.0 + 800.0 * abs(sin(lat * 150.0 + lon * 40.0)).let { it * it * it } }
    }
}
