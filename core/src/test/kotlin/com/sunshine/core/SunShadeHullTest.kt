package com.sunshine.core

import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// The hull pass and the sampling along lines (design D1, D3 of add-sun-shade-overlay).
class SunShadeHullTest {
    @Test
    fun `a ridge 1000 m above a plain casts shade 2741 m to the north`() {
        val terrain = SyntheticTerrain { lat, _ -> if (lat in RIDGE_SOUTH..RIDGE_LATITUDE) 1500.0 else 500.0 }
        val center = northOf(RIDGE_LATITUDE, 2741.0)
        val grid = terrain.grid(MapArea(center, 12.0, 100.0, 400.0), sun(180.0, upperEdge = 20.0))

        for (north in listOf(2400.0, 2600.0, 2700.0)) {
            assertEquals(Sunshine.SHADE, grid.stateAt(northOf(RIDGE_LATITUDE, north)), "$north m north")
        }
        for (north in listOf(2790.0, 2900.0, 3200.0)) {
            assertEquals(Sunshine.SUN, grid.stateAt(northOf(RIDGE_LATITUDE, north)), "$north m north")
        }
    }

    @Test
    fun `earth curvature lowers a peak 4000 m above the eye 100 km towards the sun to 1_90 degrees`() {
        val ridge = northOf(CENTER.latitude, -100_000.0).latitude
        val terrain =
            SyntheticTerrain { lat, _ ->
                val south = (ridge - lat) * METRES_PER_DEGREE
                if (south in 0.0..600.0) 4001.7 else 0.0
            }
        val area = MapArea(CENTER, 12.0, 100.0, 100.0)

        assertEquals(Sunshine.SHADE, terrain.grid(area, sun(180.0, upperEdge = 1.80)).stateAt(CENTER))
        assertEquals(Sunshine.SUN, terrain.grid(area, sun(180.0, upperEdge = 2.00)).stateAt(CENTER))
    }

    @Test
    fun `a flat plain is all sun at 30 degrees and all shade at -10 degrees`() {
        val terrain = SyntheticTerrain { _, _ -> 700.0 }
        val area = MapArea(CENTER, 12.0, 100.0, 150.0)

        for ((upperEdge, expected) in listOf(30.0 to Sunshine.SUN, -10.0 to Sunshine.SHADE)) {
            val grid = terrain.grid(area, sun(135.0, upperEdge))
            for (point in area.corners() + CENTER) {
                assertEquals(expected, grid.stateAt(point), "$point at $upperEdge")
            }
        }
    }

    @Test
    fun `the hull gives the curved-earth horizon of every sample, as brute force does`() {
        val random = Random(42)
        repeat(20) {
            val count = 400
            val s = DoubleArray(count)
            val h = DoubleArray(count)
            var position = random.nextDouble(-200_000.0, 0.0)
            for (i in 0 until count) {
                position += random.nextDouble(1.0, 400.0)
                s[i] = position
                h[i] = random.nextDouble(0.0, 3000.0)
            }
            val hull = SunShadeHull(count)
            for (i in 0 until count) {
                val eye = h[i] + EYE_HEIGHT
                var expected = Double.NEGATIVE_INFINITY
                for (j in 0 until i) {
                    val d = s[i] - s[j]
                    expected = maxOf(expected, (h[j] - eye - CURVATURE * d * d) / d)
                }
                val tan = hull.pushObserver(s[i], h[i])
                if (i > 0) assertEquals(expected, tan, 1e-9, "sample $i")
            }
        }
    }

    private fun sun(
        azimuth: Double,
        upperEdge: Double,
    ) = SunPosition(azimuth, upperEdge - SUN_UPPER_LIMB, upperEdge > 0)

    private fun northOf(
        latitude: Double,
        metres: Double,
    ) = GeoPoint(latitude + metres / METRES_PER_DEGREE, CENTER.longitude)

    private companion object {
        val CENTER = GeoPoint(46.6863, 7.8632)
        val METRES_PER_DEGREE = Math.toRadians(1.0) * SyntheticTerrain.EARTH_RADIUS
        val RIDGE_LATITUDE = CENTER.latitude
        val RIDGE_SOUTH = CENTER.latitude - 150.0 / METRES_PER_DEGREE
        val CURVATURE = (1 - 0.13) / (2 * SyntheticTerrain.EARTH_RADIUS)
        const val EYE_HEIGHT = 1.7
    }
}

/** The whole grid of [area] as the app computes it: ground tiles, plan, then every tile on demand. */
internal fun SyntheticTerrain.grid(
    area: MapArea,
    sun: SunPosition,
    missing: (TileKey) -> Boolean = { false },
): ShadeGrid {
    val sweep = SunShadeSweep(area, sun)
    sweep.tiles(sweep.groundTiles().associateWith { if (missing(it)) null else tile(it) })
    val tiles =
        object : AbstractMap<TileKey, HeightTile?>() {
            override val entries: Set<Map.Entry<TileKey, HeightTile?>> get() = throw UnsupportedOperationException()

            override fun get(key: TileKey): HeightTile? = if (missing(key)) null else tile(key)

            override fun containsKey(key: TileKey) = true
        }
    return sweep.assemble(listOf(sweep.compute(tiles)))
}
