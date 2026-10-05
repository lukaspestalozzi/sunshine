package com.sunshine.core

import kotlin.math.abs
import kotlin.math.sin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// The tile plan and the exact upwind cut (design D4, D7 of add-sun-shade-overlay).
class SunShadePlanTest {
    @Test
    fun `the plan holds every tile the computation reads`() {
        val terrain = SyntheticTerrain { lat, lon -> 800.0 + 300.0 * sin(lat * 300) + 200.0 * sin(lon * 400) }
        for (azimuth in listOf(0.0, 63.0, 180.0, 244.0)) {
            val sweep = SunShadeSweep(AREA, SunPosition(azimuth, 12.0, true))
            val ground = sweep.groundTiles().associateWith { terrain.tile(it) }
            val planned = sweep.tiles(ground)

            val read = mutableSetOf<TileKey>()
            sweep.compute(recording(terrain, read))

            assertTrue(planned.containsAll(read), "azimuth $azimuth: unplanned ${read - planned}")
            assertTrue(planned.containsAll(ground.keys))
        }
    }

    @Test
    fun `the lines reach no farther upwind than terrain can shade`() {
        val plain = SyntheticTerrain { _, _ -> 500.0 }

        val high = SunShadeSweep(AREA, sun(upperEdge = 20.0)).also { it.tiles(it.groundTiles().associateWith(plain::tile)) }
        val low = SunShadeSweep(AREA, sun(upperEdge = -5.0)).also { it.tiles(it.groundTiles().associateWith(plain::tile)) }

        // (4810 − 500 − c·d²) / d = tan 20°  →  d = 11 815 m.
        assertEquals(11_815.0, high.reach, 5.0)
        assertTrue(high.reach <= 12_000.0)
        assertEquals(150_000.0, low.reach)
    }

    @Test
    fun `cutting the lines at the reach does not change the grid`() {
        val landscapes =
            listOf(
                SyntheticTerrain { lat, lon -> 600.0 + 1400.0 * ridges(lat, lon) },
                SyntheticTerrain { lat, lon -> 900.0 + 2200.0 * ridges(lon + 0.3, lat) + 300.0 * sin(lat * 900) },
            )
        for ((index, terrain) in landscapes.withIndex()) {
            for (upperEdge in listOf(8.0, 25.0)) {
                val sweep = SunShadeSweep(AREA, sun(upperEdge))
                sweep.tiles(sweep.groundTiles().associateWith(terrain::tile))
                val cut = sweep.compute(recording(terrain, mutableSetOf()))
                assertTrue(sweep.reach < 150_000.0)
                sweep.reach = 150_000.0
                val full = sweep.compute(recording(terrain, mutableSetOf()))

                for (k in 0 until sweep.lineCount) {
                    assertTrue(cut.states[k].contentEquals(full.states[k]), "landscape $index, $upperEdge°, line $k")
                }
                val states = cut.states.flatMap { it.toList() }.toSet()
                assertTrue(SunShadeSweep.SUN in states && SunShadeSweep.SHADE in states, "landscape $index, $upperEdge°: $states")
            }
        }
    }

    private fun ridges(
        lat: Double,
        lon: Double,
    ): Double = abs(sin(lat * 150.0 + lon * 40.0)).let { it * it * it }

    private fun sun(upperEdge: Double) = SunPosition(170.0, upperEdge - SUN_UPPER_LIMB, upperEdge > 0)

    private companion object {
        val AREA = MapArea(GeoPoint(46.6, 7.9), zoom = 12.0, widthDp = 120.0, heightDp = 200.0)
    }
}

/** Every tile of [terrain], recording the keys read into [read]. */
internal fun recording(
    terrain: SyntheticTerrain,
    read: MutableSet<TileKey>,
    missing: (TileKey) -> Boolean = { false },
): Map<TileKey, HeightTile?> =
    object : AbstractMap<TileKey, HeightTile?>() {
        override val entries: Set<Map.Entry<TileKey, HeightTile?>> get() = throw UnsupportedOperationException()

        override fun get(key: TileKey): HeightTile? {
            read += key
            return if (missing(key)) null else terrain.tile(key)
        }

        override fun containsKey(key: TileKey) = true
    }
