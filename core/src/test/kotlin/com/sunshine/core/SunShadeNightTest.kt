package com.sunshine.core

import kotlin.math.abs
import kotlin.math.sin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// Night steps from the ground tiles alone (sun-shade-overlay spec, "Overlay of the whole day";
// design D12 of add-sun-shade-overlay).
class SunShadeNightTest {
    private val terrain = SyntheticTerrain { lat, lon -> 900.0 + 1800.0 * abs(sin(lat * 150.0 + lon * 40.0)) }

    @Test
    fun `below -3_5 degrees every cell with ground is shade`() {
        val sweep = SunShadeSweep(AREA, sun(upperEdge = -3.6))
        assertTrue(sweep.isNight)

        val grid = sweep.night(sweep.groundTiles().associateWith(terrain::tile))

        for (point in AREA.corners() + AREA.center) assertEquals(Sunshine.SHADE, grid.stateAt(point), "$point")
        assertFalse(grid.hasUnknown)
    }

    @Test
    fun `a cell whose ground tile is missing is unknown`() {
        val sweep = SunShadeSweep(AREA, sun(upperEdge = -3.6))
        val ground = sweep.groundTiles()
        val missing = ground.first()

        val grid = sweep.night(ground.associateWith { if (it == missing) null else terrain.tile(it) })

        assertTrue(grid.hasUnknown)
        assertTrue(grid.sampleCells(400, kotlin.random.Random(1)).any { it.second == Sunshine.SHADE })
    }

    @Test
    fun `the night grid reads no tile beyond the ground tiles`() {
        val sweep = SunShadeSweep(AREA, sun(upperEdge = -3.6))
        val read = mutableSetOf<TileKey>()

        sweep.night(recording(terrain, read))

        assertTrue(sweep.groundTiles().containsAll(read), "read beyond the ground tiles: ${read - sweep.groundTiles()}")
    }

    @Test
    fun `the night grid equals the full sweep over 150 km`() {
        val landscapes = listOf(terrain, SyntheticTerrain { lat, lon -> 400.0 + 4000.0 * abs(sin(lat * 90.0) * sin(lon * 70.0)) })
        for (landscape in landscapes) {
            val sweep = SunShadeSweep(AREA, sun(upperEdge = -3.6))
            val night = sweep.night(sweep.groundTiles().associateWith(landscape::tile))
            val full = sweep.compute(recording(landscape, mutableSetOf()))
            assertEquals(150_000.0, sweep.reach)

            for (k in 0 until sweep.lineCount) {
                for (j in full.states[k].indices) {
                    assertEquals(Sunshine.SHADE, night.cellState(k, j), "line $k cell $j")
                    assertEquals(SunShadeSweep.SHADE, full.states[k][j], "line $k cell $j")
                }
            }
        }
    }

    @Test
    fun `the night grid is only for suns below -3_5 degrees`() {
        assertFalse(SunShadeSweep(AREA, sun(upperEdge = -3.4)).isNight)
    }

    private fun sun(upperEdge: Double) = SunPosition(120.0, upperEdge - SUN_UPPER_LIMB, false)

    private companion object {
        val AREA = MapArea(GeoPoint(46.6, 7.9), zoom = 12.0, widthDp = 80.0, heightDp = 120.0)
    }
}
