package com.sunshine.core

import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// The sweep against the point tracer (sun-shade-overlay spec, "Sunshine of a cell": ≥ 99.5 %).
class SunShadeOracleTest {
    @Test
    fun `parallel ridges agree with the point tracer`() = check(RIDGES)

    @Test
    fun `a cirque agrees with the point tracer`() = check(CIRQUE)

    private fun check(terrain: SyntheticTerrain) {
        val suns = listOf(SunPosition(150.0, 14.0, true), SunPosition(200.0, 24.0, true), SunPosition(250.0, 9.0, true))
        val sweeps = suns.map { sun -> SunShadeSweep(AREA, sun, heightBound = HEIGHT_BOUND) }
        val grids =
            sweeps.map { sweep ->
                sweep.tiles(sweep.groundTiles().associateWith(terrain::tile))
                sweep.assemble(listOf(sweep.compute(recording(terrain, mutableSetOf()))))
            }
        val random = Random(11)
        val point = DoubleArray(2)
        var agree = 0
        var total = 0
        val far = mutableListOf<String>()
        for (index in suns.indices) {
            val sweep = sweeps[index]
            val sun = suns[index]
            repeat(CELLS) {
                val k = random.nextInt(sweep.lineCount)
                if (sweep.lineCells[k] == 0) return@repeat
                val j = random.nextInt(sweep.lineCells[k])
                sweep.samplePoint(k, j, point)
                val profile = terrain.run(HorizonTracer(GeoPoint(point[0], point[1]), heightBound = HEIGHT_BOUND))!!
                val expected = sunshine(profile, sun.azimuth, sun.elevation + SUN_UPPER_LIMB)
                total++
                if (grids[index].cellState(k, j) == expected) {
                    agree++
                } else if (!nearEdge(grids[index], sweep, k, j, expected)) {
                    far += "sun ${sun.azimuth}°/${sun.elevation}°, line $k cell $j: $expected expected"
                }
            }
        }
        assertTrue(agree >= 0.995 * total, "$agree of $total cells agree")
        assertTrue(far.isEmpty(), "disagreements away from a shadow edge: $far")
    }

    // Whether a neighbouring cell has the [expected] state, i.e. the cell lies at a shadow edge.
    private fun nearEdge(
        grid: ShadeGrid,
        sweep: SunShadeSweep,
        k: Int,
        j: Int,
        expected: Sunshine,
    ): Boolean {
        for (dk in -1..1) {
            for (dj in -1..1) {
                val line = k + dk
                if (line !in 0 until sweep.lineCount) continue
                // Neighbouring lines start at other s; compare cells at the same position along the line.
                val cell = j + dj + ((sweep.lineStart[k] - sweep.lineStart[line]) / sweep.spacing).toInt()
                if (cell in 0 until sweep.lineCells[line] && grid.cellState(line, cell) == expected) return true
            }
        }
        return false
    }

    private companion object {
        val CENTER = GeoPoint(46.6, 7.9)
        val AREA = MapArea(CENTER, 12.0, 120.0, 160.0)
        const val CELLS = 70
        const val HEIGHT_BOUND = 2300.0
        val METRES_PER_DEGREE = Math.toRadians(1.0) * SyntheticTerrain.EARTH_RADIUS

        // East–west-ish ridges about 4.6 km apart, up to 800 m above 1000 m valleys.
        val RIDGES =
            SyntheticTerrain { lat, lon -> 1000.0 + 800.0 * abs(sin(lat * 150.0 + lon * 40.0)).let { it * it * it } }

        // A basin of 1200 m, 800 m across, rising to 2200 m walls 1.5 km further out, open to the north.
        val CIRQUE =
            SyntheticTerrain { lat, lon ->
                val north = (lat - CENTER.latitude) * METRES_PER_DEGREE
                val east = (lon - CENTER.longitude) * METRES_PER_DEGREE * kotlin.math.cos(Math.toRadians(CENTER.latitude))
                val r = kotlin.math.sqrt(north * north + east * east)
                val wall = ((r - 800.0) / 1500.0).coerceIn(0.0, 1.0)
                val opening = if (north > 0) (1.0 - north / 1500.0).coerceIn(0.0, 1.0) else 1.0
                1200.0 + 1000.0 * wall * opening
            }
    }
}
