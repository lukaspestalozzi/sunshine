package com.sunshine.core

import kotlin.math.sin
import kotlin.math.tan
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// Far-field bundling (design D5 of add-sun-shade-overlay).
class SunShadeBundlingTest {
    @Test
    fun `bundles are as wide as the lateral bound allows`() {
        for (zoom in listOf(11.0, 12.0, 13.0)) {
            val sweep = SunShadeSweep(MapArea(CENTER, zoom, 100.0, 100.0), SUN)
            for ((m, start) in listOf(sweep.farBundle to 25_000.0, sweep.middleBundle to 6_000.0)) {
                val bound = start * tan(Math.toRadians(0.125))
                // m = 1 shares nothing; lines farther apart than the bound are simply not bundled.
                assertTrue(m == 1 || m * sweep.spacing / 2 <= bound, "zoom $zoom: $m lines")
                assertTrue(m == 1 || 2 * m * sweep.spacing / 2 > bound, "zoom $zoom: $m lines could be more")
                assertEquals(0, m and (m - 1), "power of two")
            }
        }
        val zoom13 = SunShadeSweep(MapArea(CENTER, 13.0, 100.0, 100.0), SUN)
        assertEquals(8, zoom13.farBundle)
        assertEquals(1, zoom13.middleBundle)
    }

    @Test
    fun `bundling does not change the grid where far ridges are uniform sideways`() {
        val terrain = ridges(sideways = 0.0)
        for (upperEdge in listOf(3.5, 4.0, 4.5)) {
            val (bundled, unbundled) = bothWays(terrain, upperEdge)
            for (k in bundled.states.indices) {
                assertTrue(bundled.states[k].contentEquals(unbundled.states[k]), "$upperEdge°, line $k")
            }
            assertMixed(unbundled, upperEdge)
        }
    }

    @Test
    fun `bundling moves a cell's horizon by at most the sideways offset times the terrain's sideways slope`() {
        // Heights vary by ±30 % every 4.8 km sideways: slope 0.3 · 3400 m · 100 / (76.4 km per degree) = 1.34.
        val terrain = ridges(sideways = 0.3)
        val slope = 0.3 * 3400.0 * 100.0 / (METRES_PER_DEGREE * kotlin.math.cos(Math.toRadians(CENTER.latitude)))
        for (upperEdge in listOf(3.5, 4.0, 4.5)) {
            val sweep = SunShadeSweep(AREA, sun(upperEdge))
            // Largest offset of a member line from its bundle's centre line; far bands start at 25 km.
            val offset = (sweep.farBundle - 1) / 2.0 * sweep.spacing
            val delta = Math.toDegrees(kotlin.math.atan(offset * slope / 25_000.0)) + 0.001
            val (bundled, _) = bothWays(terrain, upperEdge)
            val (_, lower) = bothWays(terrain, upperEdge - delta)
            val (_, higher) = bothWays(terrain, upperEdge + delta)

            var differing = 0
            for (k in bundled.states.indices) {
                for (j in bundled.states[k].indices) {
                    when (bundled.states[k][j]) {
                        SunShadeSweep.SUN -> assertEquals(SunShadeSweep.SUN, higher.states[k][j], "$upperEdge°: line $k cell $j")
                        SunShadeSweep.SHADE -> assertEquals(SunShadeSweep.SHADE, lower.states[k][j], "$upperEdge°: line $k cell $j")
                    }
                    if (lower.states[k][j] != higher.states[k][j]) differing++
                }
            }
            assertTrue(differing > 0, "$upperEdge°: the bound is exercised")
        }
    }

    // Ridges 10 km (z11 band), 30 and 40 km (z10 band) south.
    private fun ridges(sideways: Double) =
        SyntheticTerrain { lat, lon ->
            val south = (CENTER.latitude - lat) * METRES_PER_DEGREE
            val wave = 1.0 + sideways * sin(lon * 100.0) + (0.3 - sideways) * sin(7.9 * 100.0)
            // Plus a 300 m hill in the area, so that its shadow and sunny ground both occur.
            val hill = 300.0 - 0.5 * SyntheticTerrain.distance(lat, lon, CENTER.latitude - 0.002, CENTER.longitude)
            when {
                south in 10_000.0..11_000.0 -> 800.0 * wave
                south in 30_000.0..31_000.0 -> 2000.0 * wave
                south in 40_000.0..41_000.0 -> 3400.0 * wave
                else -> 600.0 + hill.coerceAtLeast(0.0)
            }
        }

    private fun bothWays(
        terrain: SyntheticTerrain,
        upperEdge: Double,
    ): Pair<ShadeGridPart, ShadeGridPart> {
        val bundled = SunShadeSweep(AREA, sun(upperEdge))
        val unbundled = SunShadeSweep(AREA, sun(upperEdge)).apply { bundling = false }
        return bundled.compute(recording(terrain, mutableSetOf())) to unbundled.compute(recording(terrain, mutableSetOf()))
    }

    private fun assertMixed(
        part: ShadeGridPart,
        upperEdge: Double,
    ) {
        val states = part.states.flatMap { it.toList() }.toSet()
        assertTrue(SunShadeSweep.SUN in states && SunShadeSweep.SHADE in states, "$upperEdge°: $states")
    }

    private fun sun(upperEdge: Double) = SunPosition(175.0, upperEdge - SUN_UPPER_LIMB, true)

    @Test
    fun `computing the lines in chunks gives the same grid as all at once`() {
        val terrain = SyntheticTerrain { lat, lon -> 900.0 + 1500.0 * sin(lat * 200.0) * sin(lon * 150.0) }
        val sweep = SunShadeSweep(MapArea(CENTER, 13.0, 150.0, 150.0), SunPosition(200.0, 6.0, true))
        val tiles = recording(terrain, mutableSetOf())

        val whole = sweep.compute(tiles)
        val chunks = sweep.chunks(5)
        val parts = chunks.map { sweep.compute(tiles, it) }

        assertEquals(0 until sweep.lineCount, chunks.first().first..chunks.last().last)
        chunks.zipWithNext().forEach { (a, b) -> assertEquals(a.last + 1, b.first) }
        chunks.dropLast(1).forEach { assertEquals(0, (it.last + 1) % sweep.farBundle) }
        val assembled = sweep.assemble(parts)
        for (k in 0 until sweep.lineCount) {
            for (j in whole.states[k].indices) {
                val expected =
                    when (whole.states[k][j]) {
                        SunShadeSweep.SUN -> Sunshine.SUN
                        SunShadeSweep.SHADE -> Sunshine.SHADE
                        else -> Sunshine.UNKNOWN
                    }
                assertEquals(expected, assembled.cellState(k, j), "line $k cell $j")
            }
        }
    }

    private companion object {
        val CENTER = GeoPoint(46.6, 7.9)
        val AREA = MapArea(CENTER, 13.0, 150.0, 150.0)
        val SUN = SunPosition(180.0, 10.0, true)
        val METRES_PER_DEGREE = Math.toRadians(1.0) * SyntheticTerrain.EARTH_RADIUS
    }
}
