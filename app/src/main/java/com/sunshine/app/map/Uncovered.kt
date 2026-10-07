package com.sunshine.app.map

import com.sunshine.core.GeoPoint
import com.sunshine.core.MAP_TILE_DP
import com.sunshine.core.MapArea
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh

/** The [parts] of an area that an earlier area does not cover, and the [coveredShare] of it that it does. */
class Uncovered(
    val parts: List<MapArea>,
    val coveredShare: Double,
)

/**
 * The rectangles of [new] outside [old] (design D2 of overlay-pan-reuse): in [new]'s map frame, a
 * full-width strip above and below [old] and the pieces left and right of it, at most four, each a
 * [MapArea] at [new]'s zoom. A piece narrower than [cellDp] is widened to one cell into the covered
 * part. Without overlap the whole of [new] is uncovered.
 */
fun uncovered(
    new: MapArea,
    old: MapArea,
    cellDp: Double,
): Uncovered {
    val world = MAP_TILE_DP * 2.0.pow(new.zoom)
    val x = mercatorX(new.center.longitude) * world
    val y = mercatorY(new.center.latitude) * world
    val left = x - new.widthDp / 2
    val right = x + new.widthDp / 2
    val top = y - new.heightDp / 2
    val bottom = y + new.heightDp / 2
    // The old area in the new one's frame, clipped to it.
    val scale = 2.0.pow(new.zoom - old.zoom)
    val oldX = mercatorX(old.center.longitude) * world
    val oldY = mercatorY(old.center.latitude) * world
    val coveredLeft = max(left, oldX - old.widthDp * scale / 2)
    val coveredRight = min(right, oldX + old.widthDp * scale / 2)
    val coveredTop = max(top, oldY - old.heightDp * scale / 2)
    val coveredBottom = min(bottom, oldY + old.heightDp * scale / 2)
    if (coveredLeft >= coveredRight || coveredTop >= coveredBottom) return Uncovered(listOf(new), 0.0)

    fun area(
        west: Double,
        north: Double,
        east: Double,
        south: Double,
    ) = MapArea(
        GeoPoint(latitude((north + south) / 2 / world), ((west + east) / 2 / world) * 360.0 - 180.0),
        new.zoom,
        east - west,
        south - north,
    )
    val parts = ArrayList<MapArea>(4)
    if (coveredTop - top > EDGE_DP) parts += area(left, top, right, max(coveredTop, top + cellDp))
    if (bottom - coveredBottom > EDGE_DP) parts += area(left, min(coveredBottom, bottom - cellDp), right, bottom)
    if (coveredLeft - left > EDGE_DP) parts += area(left, coveredTop, max(coveredLeft, left + cellDp), coveredBottom)
    if (right - coveredRight > EDGE_DP) parts += area(min(coveredRight, right - cellDp), coveredTop, right, coveredBottom)
    val share = (coveredRight - coveredLeft) * (coveredBottom - coveredTop) / (new.widthDp * new.heightDp)
    return Uncovered(parts, share)
}

private fun mercatorX(longitude: Double) = (longitude + 180.0) / 360.0

private fun mercatorY(latitude: Double): Double {
    val sinLat = sin(Math.toRadians(latitude))
    return 0.5 - ln((1 + sinLat) / (1 - sinLat)) / (4 * PI)
}

private fun latitude(y: Double) = Math.toDegrees(atan(sinh(PI * (1 - 2 * y))))

// Uncovered strips thinner than this are rounding, not area.
private const val EDGE_DP = 1e-6
