package com.sunshine.app.map

import com.sunshine.core.GeoPoint
import com.sunshine.core.MapArea
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// The rectangles of an area that an earlier area does not cover (design D2 of overlay-pan-reuse).
class UncoveredTest {
    @Test
    fun `a pan by half a screen uncovers one rectangle of half the area`() {
        assertRects(listOf(Rect(0.0, -H / 2, W / 2, H / 2)), uncovered(OLD.panned(dx = W / 2), OLD, CELL_DP), OLD.panned(dx = W / 2))
        assertRects(listOf(Rect(-W / 2, -H / 2, 0.0, H / 2)), uncovered(OLD.panned(dx = -W / 2), OLD, CELL_DP), OLD.panned(dx = -W / 2))
        assertRects(listOf(Rect(-W / 2, 0.0, W / 2, H / 2)), uncovered(OLD.panned(dy = H / 2), OLD, CELL_DP), OLD.panned(dy = H / 2))
        assertRects(listOf(Rect(-W / 2, -H / 2, W / 2, 0.0)), uncovered(OLD.panned(dy = -H / 2), OLD, CELL_DP), OLD.panned(dy = -H / 2))
    }

    @Test
    fun `a diagonal pan uncovers a strip below and a piece on the right`() {
        val new = OLD.panned(dx = W / 2, dy = H / 2)

        val result = uncovered(new, OLD, CELL_DP)

        assertRects(listOf(Rect(-W / 2, 0.0, W / 2, H / 2), Rect(0.0, -H / 2, W / 2, 0.0)), result, new)
        assertEquals(0.25, result.coveredShare, 0.01)
    }

    @Test
    fun `zooming out by half a level uncovers four strips around the old area`() {
        val new = OLD.copy(zoom = OLD.zoom - 0.5)
        val w = W / 2 * 2.0.pow(-0.5)
        val h = H / 2 * 2.0.pow(-0.5)

        val result = uncovered(new, OLD, CELL_DP)

        assertRects(
            listOf(
                Rect(-W / 2, -H / 2, W / 2, -h),
                Rect(-W / 2, h, W / 2, H / 2),
                Rect(-W / 2, -h, -w, h),
                Rect(w, -h, W / 2, h),
            ),
            result,
            new,
        )
        assertEquals(0.5, result.coveredShare, 0.01)
    }

    @Test
    fun `without overlap the whole area is uncovered`() {
        val new = OLD.panned(dx = 2 * W)

        val result = uncovered(new, OLD, CELL_DP)

        assertEquals(listOf(new), result.parts)
        assertEquals(0.0, result.coveredShare)
    }

    @Test
    fun `a sliver narrower than one cell is widened to one cell into the covered part`() {
        val new = OLD.panned(dx = 0.5)

        assertRects(listOf(Rect(W / 2 - CELL_DP, -H / 2, W / 2, H / 2)), uncovered(new, OLD, CELL_DP), new)
    }

    @Test
    fun `the covered share of a pan by half a screen is one half`() {
        assertEquals(0.5, uncovered(OLD.panned(dx = W / 2), OLD, CELL_DP).coveredShare, 0.01)
        assertEquals(0.5, uncovered(OLD.panned(dy = -H / 2), OLD, CELL_DP).coveredShare, 0.01)
    }

    // A rectangle in dp relative to an area's centre, x east, y south.
    private data class Rect(
        val left: Double,
        val top: Double,
        val right: Double,
        val bottom: Double,
    )

    private fun assertRects(
        expected: List<Rect>,
        result: Uncovered,
        new: MapArea,
    ) {
        assertEquals(expected.size, result.parts.size, "${result.parts}")
        for ((rect, part) in expected.zip(result.parts)) {
            assertEquals(new.zoom, part.zoom)
            val (x, y) = offset(part.center, new)
            val actual = Rect(x - part.widthDp / 2, y - part.heightDp / 2, x + part.widthDp / 2, y + part.heightDp / 2)
            assertEquals(rect.left, actual.left, TOLERANCE_DP, "left of $actual")
            assertEquals(rect.top, actual.top, TOLERANCE_DP, "top of $actual")
            assertEquals(rect.right, actual.right, TOLERANCE_DP, "right of $actual")
            assertEquals(rect.bottom, actual.bottom, TOLERANCE_DP, "bottom of $actual")
        }
    }

    private companion object {
        const val W = 400.0
        const val H = 800.0
        const val CELL_DP = 2.0
        const val TOLERANCE_DP = 1e-6
        val OLD = MapArea(GeoPoint(46.6, 7.9), zoom = 12.0, widthDp = W, heightDp = H)

        fun world(zoom: Double) = 512.0 * 2.0.pow(zoom)

        fun mercatorY(latitude: Double): Double {
            val sinLat = sin(Math.toRadians(latitude))
            return 0.5 - ln((1 + sinLat) / (1 - sinLat)) / (4 * PI)
        }

        // This area moved [dx] dp east and [dy] dp south on its map.
        fun MapArea.panned(
            dx: Double = 0.0,
            dy: Double = 0.0,
        ): MapArea {
            val world = world(zoom)
            val y = mercatorY(center.latitude) * world + dy
            val latitude = Math.toDegrees(atan(sinh(PI * (1 - 2 * y / world))))
            return copy(center = GeoPoint(latitude, center.longitude + dx / world * 360.0))
        }

        // The dp east and south of [point] from [area]'s centre on its map.
        fun offset(
            point: GeoPoint,
            area: MapArea,
        ): Pair<Double, Double> {
            val world = world(area.zoom)
            return (point.longitude - area.center.longitude) / 360.0 * world to
                (mercatorY(point.latitude) - mercatorY(area.center.latitude)) * world
        }
    }
}
