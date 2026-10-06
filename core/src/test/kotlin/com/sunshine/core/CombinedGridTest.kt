package com.sunshine.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// A grid made of an earlier day's grid and the grids of the parts a pan uncovers (design D3 of overlay-pan-reuse).
class CombinedGridTest {
    private val base = RIDGES.grid(OLD, SUN)
    private val part = RIDGES.grid(PART, SUN)
    private val combined = CombinedGrid(NEW, base, listOf(part))

    @Test
    fun `a point takes the base's state inside the base's bounds and the part's elsewhere`() {
        val bounds = GeoBounds.of(OLD)
        var fromBase = 0
        var fromPart = 0
        for (point in raster(NEW, rows = 60, columns = 60)) {
            if (point.latitude in bounds.south..bounds.north && point.longitude in bounds.west..bounds.east) {
                assertEquals(base.stateAt(point), combined.stateAt(point), "$point")
                fromBase++
            } else {
                assertEquals(part.stateAt(point), combined.stateAt(point), "$point")
                fromPart++
            }
        }
        assertTrue(fromBase > 1000 && fromPart > 1000, "$fromBase from the base, $fromPart from the part")
    }

    @Test
    fun `no state outside the new area's grids`() {
        // 20 dp beyond the new area to the east, and 20 dp north of it.
        assertNull(combined.stateAt(shifted(NEW.center, 12.0, dx = NEW.widthDp / 2 + 20)))
        assertNull(combined.stateAt(shifted(NEW.center, 12.0, dy = -(NEW.heightDp / 2 + 20))))
        // The base's west half, outside the new area, is still the base's.
        val west = shifted(OLD.center, 12.0, dx = -30.0)
        assertEquals(base.stateAt(west), combined.stateAt(west))
    }

    @Test
    fun `the states of a raster equal stateAt at every point, outside the grids included`() {
        val corners = NEW.corners()
        val north = corners.maxOf { it.latitude }
        val south = corners.minOf { it.latitude }
        val west = corners.minOf { it.longitude }
        val east = corners.maxOf { it.longitude }
        val latitudes = DoubleArray(ROWS) { north + (south - north) * (it - 0.1 * ROWS) / (0.8 * ROWS) }
        val longitudes = DoubleArray(COLUMNS) { west + (east - west) * (it - 0.1 * COLUMNS) / (0.8 * COLUMNS) }

        val states = combined.statesAt(latitudes, longitudes)

        assertEquals(ROWS * COLUMNS, states.size)
        var outside = 0
        for (r in 0 until ROWS) {
            for (c in 0 until COLUMNS) {
                val expected = combined.stateAt(latitudes[r], longitudes[c])
                if (expected == null) outside++
                assertEquals(expected, states[r * COLUMNS + c], "row $r, column $c")
            }
        }
        assertTrue(outside in 1 until ROWS * COLUMNS, "$outside points outside the grids")
    }

    @Test
    fun `samples come from the base and the part in proportion to their share, each with its own sun centre`() {
        val samples = combined.sampleCells(1000, Random(3))

        assertEquals(1000, samples.size)
        val fromPart = samples.count { it.center == PART.center }
        assertEquals(500.0, fromPart.toDouble(), 100.0, "$fromPart of 1000 from the part")
        assertTrue(samples.all { it.center == PART.center || it.center == OLD.center })
        val bounds = GeoBounds.of(NEW)
        for (sample in samples) {
            assertEquals(combined.stateAt(sample.point), sample.state, "${sample.point}")
            assertTrue(sample.point.latitude in bounds.south..bounds.north && sample.point.longitude in bounds.west..bounds.east)
        }
    }

    @Test
    fun `unknown cells of any grid make the combination unknown somewhere`() {
        val unknownPart = RIDGES.grid(PART, SUN, SunShadeSweep.CELL_DP) { true }

        assertFalse(base.hasUnknown)
        assertFalse(part.hasUnknown)
        assertFalse(combined.hasUnknown)
        assertTrue(unknownPart.hasUnknown)
        assertTrue(CombinedGrid(NEW, base, listOf(unknownPart)).hasUnknown)
    }

    @Test
    fun `bytes are the sum of the grids', and depth counts the combinations`() {
        assertEquals(base.stateBytes + part.stateBytes, combined.stateBytes)
        assertEquals(0, base.depth)
        assertEquals(1, combined.depth)
        assertEquals(2, CombinedGrid(NEW, combined, listOf(part)).depth)
    }

    private fun raster(
        area: MapArea,
        rows: Int,
        columns: Int,
    ): List<GeoPoint> =
        (0 until rows).flatMap { r ->
            (0 until columns).map { c ->
                shifted(
                    area.center,
                    area.zoom,
                    dx = area.widthDp * ((c + 0.5) / columns - 0.5),
                    dy =
                        area.heightDp * ((r + 0.5) / rows - 0.5),
                )
            }
        }

    private companion object {
        val SUN = SunPosition(130.0, 12.0, true)
        val OLD = MapArea(GeoPoint(46.6, 7.9), zoom = 12.0, widthDp = 120.0, heightDp = 160.0)

        // OLD panned by half its width to the east, and the east half it uncovers.
        val NEW = OLD.panned(dx = 60.0)
        val PART = OLD.panned(dx = 90.0).copy(widthDp = 60.0)
        const val ROWS = 90
        const val COLUMNS = 90
        val RIDGES =
            SyntheticTerrain { lat, lon -> 1000.0 + 800.0 * abs(sin(lat * 150.0 + lon * 40.0)).let { it * it * it } }
    }
}

/** This area moved [dx] dp east and [dy] dp south on its map. */
internal fun MapArea.panned(
    dx: Double = 0.0,
    dy: Double = 0.0,
): MapArea = copy(center = shifted(center, zoom, dx, dy))

/** The point [dx] dp east and [dy] dp south of [point] on the map at [zoom]. */
internal fun shifted(
    point: GeoPoint,
    zoom: Double,
    dx: Double = 0.0,
    dy: Double = 0.0,
): GeoPoint {
    val world = 512.0 * 2.0.pow(zoom)
    val sinLat = sin(Math.toRadians(point.latitude))
    val y = (0.5 - ln((1 + sinLat) / (1 - sinLat)) / (4 * PI)) * world + dy
    val latitude = Math.toDegrees(atan(sinh(PI * (1 - 2 * y / world))))
    return GeoPoint(latitude, point.longitude + dx / world * 360.0)
}
