package com.sunshine.core

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt

/**
 * The visible part of a north-up Web-Mercator map with 512-dp tiles, as MapLibre shows it: the world
 * is 512 · 2^[zoom] dp wide.
 */
data class MapArea(
    val center: GeoPoint,
    val zoom: Double,
    val widthDp: Double,
    val heightDp: Double,
) {
    /** Ground distance of one dp at the centre, in metres. */
    val metresPerDp: Double
        get() = 2 * PI * EARTH_RADIUS * cos(Math.toRadians(center.latitude)) / worldDp

    private val worldDp: Double get() = MAP_TILE_DP * 2.0.pow(zoom)

    /** The corners north-west, north-east, south-east and south-west. */
    fun corners(): List<GeoPoint> {
        val x = (center.longitude + 180.0) / 360.0 * worldDp
        val sinLat = sin(Math.toRadians(center.latitude))
        val y = (0.5 - ln((1 + sinLat) / (1 - sinLat)) / (4 * PI)) * worldDp
        return listOf(-1 to -1, 1 to -1, 1 to 1, -1 to 1).map { (dx, dy) ->
            pointAt(x + dx * widthDp / 2, y + dy * heightDp / 2)
        }
    }

    private fun pointAt(
        x: Double,
        y: Double,
    ): GeoPoint {
        val longitude = ((x / worldDp * 360.0) % 360.0 + 360.0) % 360.0 - 180.0
        val latitude = Math.toDegrees(atan(sinh(PI * (1 - 2 * y / worldDp))))
        return GeoPoint(latitude.coerceIn(-MAX_MAP_LATITUDE, MAX_MAP_LATITUDE), longitude)
    }

    private companion object {
        const val MAP_TILE_DP = 512.0
        const val MAX_MAP_LATITUDE = 85.0511287798
    }
}

/**
 * Gnomonic projection centred on [center], in metres east ([0]) and north ([1]) on the tangent
 * plane: straight lines are great circles, as the rays of [HorizonTracer] (design D2).
 */
internal class GnomonicFrame(
    center: GeoPoint,
) {
    private val sinLat0 = sin(Math.toRadians(center.latitude))
    private val cosLat0 = cos(Math.toRadians(center.latitude))
    private val lon0 = Math.toRadians(center.longitude)

    /** [latitude]/[longitude] in degrees to plane coordinates in [out]. */
    fun forward(
        latitude: Double,
        longitude: Double,
        out: DoubleArray,
    ) {
        val lat = Math.toRadians(latitude)
        val dl = Math.toRadians(longitude) - lon0
        val cosC = sinLat0 * sin(lat) + cosLat0 * cos(lat) * cos(dl)
        out[0] = EARTH_RADIUS * cos(lat) * sin(dl) / cosC
        out[1] = EARTH_RADIUS * (cosLat0 * sin(lat) - sinLat0 * cos(lat) * cos(dl)) / cosC
    }

    /** Plane coordinates to latitude ([0]) and longitude ([1]) in degrees in [out]. */
    fun inverse(
        x: Double,
        y: Double,
        out: DoubleArray,
    ) {
        val rho = sqrt(x * x + y * y)
        if (rho < 1e-9) {
            out[0] = Math.toDegrees(atan2(sinLat0, cosLat0))
            out[1] = Math.toDegrees(lon0)
            return
        }
        val c = atan(rho / EARTH_RADIUS)
        val sinC = sin(c)
        val cosC = cos(c)
        out[0] = Math.toDegrees(kotlin.math.asin(cosC * sinLat0 + y * sinC * cosLat0 / rho))
        out[1] = Math.toDegrees(lon0 + atan2(x * sinC, rho * cosLat0 * cosC - y * sinLat0 * sinC))
    }
}

/**
 * Sun, shade or unknown for every cell of [area] with the sun at [sun] (sun-shade-overlay spec),
 * by a convex-hull sweep along great-circle lines towards the sun (design D1 of
 * add-sun-shade-overlay). No I/O: the caller supplies the tiles.
 *
 * Frame: the gnomonic plane of the map centre. Lines run towards the sun's azimuth at the centre;
 * `s` is the position along a line, growing downwind (away from the sun), and `w` the position
 * across the lines. Cells are [CELL_DP] dp squares; cell `j` of line `k` has its sample point at
 * `s = lineStart[k] + (j + 0.5) · spacing`.
 */
class SunShadeSweep(
    val area: MapArea,
    val sun: SunPosition,
    private val heightBound: Double = heightBoundAt(area.center),
    private val tileSize: Int = 512,
) {
    internal val frame = GnomonicFrame(area.center)

    /** Distance between neighbouring lines and between the cells of a line, in metres. */
    val spacing: Double = CELL_DP * area.metresPerDp

    /** Unit vector towards the sun in the plane (east, north). */
    private val ux = sin(Math.toRadians(sun.azimuth))
    private val uy = cos(Math.toRadians(sun.azimuth))

    private val wMin: Double
    val lineCount: Int
    internal val lineStart: DoubleArray
    internal val lineCells: IntArray

    init {
        // The outline of the visible area (corners and edge midpoints) in (s, w).
        val corners = area.corners()
        val outline =
            corners.indices.flatMap { i ->
                val a = corners[i]
                val b = corners[(i + 1) % corners.size]
                listOf(a, GeoPoint((a.latitude + b.latitude) / 2, midLongitude(a.longitude, b.longitude)))
            }
        val xy = DoubleArray(2)
        val s = DoubleArray(outline.size)
        val w = DoubleArray(outline.size)
        outline.forEachIndexed { i, p ->
            frame.forward(p.latitude, p.longitude, xy)
            s[i] = toS(xy[0], xy[1])
            w[i] = toW(xy[0], xy[1])
        }
        wMin = w.min() - spacing
        lineCount = ceil((w.max() + spacing - wMin) / spacing).toInt()
        lineStart = DoubleArray(lineCount)
        lineCells = IntArray(lineCount)
        for (k in 0 until lineCount) {
            // The part of each outline edge within the line's band of cells.
            val bandLow = lineW(k) - spacing / 2
            val bandHigh = lineW(k) + spacing / 2
            var lo = Double.POSITIVE_INFINITY
            var hi = Double.NEGATIVE_INFINITY
            for (i in outline.indices) {
                val j = (i + 1) % outline.size
                var t0 = 0.0
                var t1 = 1.0
                if (w[i] == w[j]) {
                    if (w[i] !in bandLow..bandHigh) continue
                } else {
                    val ta = (bandLow - w[i]) / (w[j] - w[i])
                    val tb = (bandHigh - w[i]) / (w[j] - w[i])
                    t0 = max(t0, min(ta, tb))
                    t1 = min(t1, max(ta, tb))
                    if (t0 > t1) continue
                }
                for (t in doubleArrayOf(t0, t1)) {
                    val cross = s[i] + (s[j] - s[i]) * t
                    lo = min(lo, cross)
                    hi = max(hi, cross)
                }
            }
            // One cell of margin on each side covers the curvature of the outline between its points.
            if (lo <= hi) {
                lineStart[k] = lo - spacing
                lineCells[k] = ceil((hi - lo) / spacing).toInt() + 2
            }
        }
    }

    /** Across-line position of line [k]. */
    internal fun lineW(k: Int): Double = wMin + (k + 0.5) * spacing

    /** Latitude and longitude into [out] of position [s] on line [k]. */
    internal fun pointOnLine(
        k: Int,
        s: Double,
        out: DoubleArray,
    ) {
        val w = lineW(k)
        frame.inverse(-s * ux + w * uy, -s * uy - w * ux, out)
    }

    /** The (line, cell) whose square contains the point, or `null` outside the grid. */
    internal fun cellOf(
        latitude: Double,
        longitude: Double,
    ): Pair<Int, Int>? {
        val xy = DoubleArray(2)
        frame.forward(latitude, longitude, xy)
        val k = floor((toW(xy[0], xy[1]) - wMin) / spacing).toInt()
        if (k !in 0 until lineCount) return null
        val j = floor((toS(xy[0], xy[1]) - lineStart[k]) / spacing).toInt()
        return if (j in 0 until lineCells[k]) k to j else null
    }

    private fun toS(
        x: Double,
        y: Double,
    ) = -(x * ux + y * uy)

    private fun toW(
        x: Double,
        y: Double,
    ) = x * uy - y * ux

    private fun midLongitude(
        a: Double,
        b: Double,
    ): Double = if (kotlin.math.abs(a - b) > 180.0) ((a + b + 360.0) / 2 + 180.0) % 360.0 - 180.0 else (a + b) / 2

    companion object {
        /** Width of a cell in dp (user decision, design D2). */
        const val CELL_DP = 2.0
    }
}
