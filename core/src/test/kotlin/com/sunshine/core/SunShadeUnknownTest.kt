package com.sunshine.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// Missing data (sun-shade-overlay spec, "Unknown cells"; design D6 of add-sun-shade-overlay).
class SunShadeUnknownTest {
    // The eye at 500 m; a ridge 5 km to the south at 2° (the highest terrain within 10 km); no data
    // 20–21 km to the south, where terrain could rise to atan((4810 − 500 − c·d²)/d) = 12.09°.
    private val terrain =
        SyntheticTerrain { lat, _ ->
            val south = (CENTER.latitude - lat) * METRES_PER_DEGREE
            when {
                south in 20_000.0..21_000.0 -> Double.NaN
                south in 5_000.0..5_100.0 -> RIDGE
                else -> GROUND
            }
        }

    @Test
    fun `missing terrain that could rise above the sun makes the cell unknown`() {
        assertEquals(Sunshine.UNKNOWN, terrain.grid(AREA, sun(5.0)).stateAt(CENTER))
    }

    @Test
    fun `missing terrain that cannot rise above the sun leaves the cell sunny`() {
        assertEquals(Sunshine.SUN, terrain.grid(AREA, sun(20.0)).stateAt(CENTER))
    }

    @Test
    fun `a sun below the angle already found is shade despite missing terrain`() {
        assertEquals(Sunshine.SHADE, terrain.grid(AREA, sun(1.5)).stateAt(CENTER))
    }

    // The cell's eye at 2500 m on a plateau, with low ground at 500 m in the north of the area, so
    // that the upwind cut (computed from the lowest ground) still reaches the gap at 20 km. From
    // 2500 m, terrain 20 km away could rise to at most 6.51° (sun-shade-overlay spec, "Higher eye,
    // same gap").
    @Test
    fun `a higher eye is sunny above what missing terrain could reach`() {
        val plateau =
            SyntheticTerrain { lat, _ ->
                val south = (CENTER.latitude - lat) * METRES_PER_DEGREE
                when {
                    south < -300.0 -> GROUND
                    south in 20_000.0..21_000.0 -> Double.NaN
                    south in 5_000.0..5_100.0 -> PLATEAU + RIDGE - GROUND
                    else -> PLATEAU
                }
            }

        assertEquals(Sunshine.SUN, plateau.grid(AREA, sun(8.0)).stateAt(CENTER))
        assertEquals(Sunshine.UNKNOWN, plateau.grid(AREA, sun(6.4)).stateAt(CENTER))
    }

    @Test
    fun `a whole missing tile 20 km towards the sun makes the cell unknown`() {
        val plain = SyntheticTerrain { lat, _ -> if ((CENTER.latitude - lat) * METRES_PER_DEGREE in 5_000.0..5_100.0) RIDGE else GROUND }
        val missing = { key: TileKey -> key.zoom <= 12 && coversLatitude(key, CENTER.latitude - 20_000.0 / METRES_PER_DEGREE) }

        assertEquals(Sunshine.UNKNOWN, plain.grid(AREA, sun(5.0), missing).stateAt(CENTER))
    }

    @Test
    fun `a missing ground tile makes its cells unknown, whatever the sun`() {
        val sweep = SunShadeSweep(AREA, sun(30.0))
        val missing = { key: TileKey -> key.zoom == sweep.viewZoom }

        val grid = SyntheticTerrain { _, _ -> GROUND }.grid(AREA, sun(30.0), missing)

        for (point in AREA.corners() + CENTER) assertEquals(Sunshine.UNKNOWN, grid.stateAt(point), "$point")
    }

    @Test
    fun `without any tile every cell is unknown`() {
        val grid = SyntheticTerrain { _, _ -> GROUND }.grid(AREA, sun(30.0)) { true }

        for (point in AREA.corners() + CENTER) assertEquals(Sunshine.UNKNOWN, grid.stateAt(point), "$point")
    }

    private fun sun(upperEdge: Double) = SunPosition(180.0, upperEdge - SUN_UPPER_LIMB, true)

    private fun coversLatitude(
        key: TileKey,
        latitude: Double,
    ): Boolean {
        val n = (1L shl key.zoom).toDouble()
        val north = Math.toDegrees(kotlin.math.atan(kotlin.math.sinh(Math.PI * (1 - 2 * key.y / n))))
        val south = Math.toDegrees(kotlin.math.atan(kotlin.math.sinh(Math.PI * (1 - 2 * (key.y + 1) / n))))
        return latitude in south..north
    }

    private companion object {
        val CENTER = GeoPoint(46.6863, 7.8632)
        val AREA = MapArea(CENTER, 12.0, 60.0, 60.0)
        val METRES_PER_DEGREE = Math.toRadians(1.0) * SyntheticTerrain.EARTH_RADIUS
        const val GROUND = 498.3
        const val PLATEAU = 2498.3

        // 2° seen from the eye at 500 m, 5 km away: 500 + 5000 · tan 2° + c · 5000².
        const val RIDGE = 676.3
    }
}
