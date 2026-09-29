package com.sunshine.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// Geometry of the sun-shade grid (design D2 of add-sun-shade-overlay).
class SunShadeGeometryTest {
    @Test
    fun `the visible area of a zoom-12 portrait map at Lauterbrunnen is 5_24 km wide`() {
        val (nw, ne, _, sw) = AREA.corners()

        val width = SyntheticTerrain.distance(nw.latitude, nw.longitude, ne.latitude, ne.longitude)
        val height = SyntheticTerrain.distance(nw.latitude, nw.longitude, sw.latitude, sw.longitude)

        assertEquals(5240.0, width, 5240.0 * 0.01)
        assertEquals(850.0 / 400.0, height / width, 0.01)
        assertEquals(width / 400.0, AREA.metresPerDp, 0.01 * width / 400.0)
    }

    @Test
    fun `the gnomonic frame maps the centre to the origin and back`() {
        val frame = GnomonicFrame(AREA.center)
        val xy = DoubleArray(2)
        val ll = DoubleArray(2)

        frame.forward(AREA.center.latitude, AREA.center.longitude, xy)
        assertEquals(0.0, xy[0], 1e-6)
        assertEquals(0.0, xy[1], 1e-6)

        for ((x, y) in listOf(3000.0 to -5000.0, -150_000.0 to 20_000.0, 70_000.0 to 140_000.0)) {
            frame.inverse(x, y, ll)
            frame.forward(ll[0], ll[1], xy)
            assertEquals(x, xy[0], 1e-4)
            assertEquals(y, xy[1], 1e-4)
        }
    }

    @Test
    fun `lines are 2 dp apart, point at the sun from the centre and are great circles`() {
        val sweep = SunShadeSweep(AREA, SunPosition(azimuth = 200.0, elevation = 20.0, isAboveHorizon = true))
        val frame = GnomonicFrame(AREA.center)

        assertEquals(2 * AREA.metresPerDp, sweep.spacing, 1e-9)
        assertEquals(sweep.spacing, sweep.lineW(1) - sweep.lineW(0), 1e-6)

        // At the centre, the line towards the sun has the sun's azimuth.
        val ll = DoubleArray(2)
        frame.inverse(100 * sin(Math.toRadians(200.0)), 100 * cos(Math.toRadians(200.0)), ll)
        assertEquals(200.0, bearing(AREA.center.latitude, AREA.center.longitude, ll[0], ll[1]), 1e-3)

        // Three points of a line 5 km off-centre lie on one great circle.
        val line = sweep.lineCount / 2 + (5000 / sweep.spacing).toInt()
        val points =
            listOf(-100_000.0, 0.0, 3000.0).map { s ->
                sweep.pointOnLine(line, s, ll)
                unitVector(ll[0], ll[1])
            }
        val normal = cross(points[0], points[1])
        assertEquals(0.0, dot(normal, points[2]) / norm(normal), 1e-9)
    }

    @Test
    fun `with 8 dp cells the lines are 8 dp apart, about a quarter as many as with 2 dp`() {
        val sun = SunPosition(azimuth = 200.0, elevation = 20.0, isAboveHorizon = true)
        val fine = SunShadeSweep(AREA, sun)
        val coarse = SunShadeSweep(AREA, sun, cellDp = 8.0)

        assertEquals(8 * AREA.metresPerDp, coarse.spacing, 1e-9)
        assertEquals(coarse.spacing, coarse.lineW(1) - coarse.lineW(0), 1e-6)
        assertEquals(fine.lineCount / 4.0, coarse.lineCount.toDouble(), fine.lineCount / 4.0 * 0.05)
    }

    @Test
    fun `the grid covers every corner and the centre of the visible area`() {
        for (azimuth in listOf(0.0, 45.0, 135.0, 180.0, 271.0)) {
            val sweep = SunShadeSweep(AREA, SunPosition(azimuth, 20.0, true))
            for (point in AREA.corners() + AREA.center) {
                assertNotNull(sweep.cellOf(point.latitude, point.longitude), "azimuth $azimuth, $point")
            }
            assertTrue(sweep.lineCount > 0)
        }
    }

    @Test
    fun `sampled cells lie in their own cell and carry its state`() {
        val terrain = SyntheticTerrain { lat, lon -> 900.0 + 400.0 * sin(lat * 900.0) * cos(lon * 700.0) }
        val grid = terrain.grid(MapArea(AREA.center, 13.0, 80.0, 120.0), SunPosition(160.0, 15.0, true))

        val cells = grid.sampleCells(50, kotlin.random.Random(3))

        assertEquals(50, cells.size)
        for ((point, state) in cells) assertEquals(state, grid.stateAt(point), "$point")
    }

    private fun bearing(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val b = Math.toDegrees(kotlin.math.atan2(sin(dl) * cos(p2), cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)))
        return (b + 360.0) % 360.0
    }

    private fun unitVector(
        lat: Double,
        lon: Double,
    ): DoubleArray {
        val p = Math.toRadians(lat)
        val l = Math.toRadians(lon)
        return doubleArrayOf(cos(p) * cos(l), cos(p) * sin(l), sin(p))
    }

    private fun cross(
        a: DoubleArray,
        b: DoubleArray,
    ) = doubleArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])

    private fun dot(
        a: DoubleArray,
        b: DoubleArray,
    ) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

    private fun norm(a: DoubleArray) = kotlin.math.sqrt(dot(a, a)).also { assertTrue(abs(it) > 0) }

    private companion object {
        val AREA = MapArea(GeoPoint(46.5935, 7.9091), zoom = 12.0, widthDp = 400.0, heightDp = 850.0)
    }
}
