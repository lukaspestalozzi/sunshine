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

    /** Zoom of the samples inside the area and up to [NEAR_BAND] upwind (design D3). */
    val viewZoom: Int = min(MAX_ZOOM, floor(area.zoom).toInt() + 2)

    // Cells lie on every [sub]-th sample of their line, in the middle of their square.
    private val sub: Int = 2 * ceil(spacing / (2 * halfPixel(viewZoom))).toInt()
    private val viewStep = spacing / sub

    private val upperEdge = sun.elevation + SUN_UPPER_LIMB

    /** How far upwind the lines start, in metres. */
    internal var reach: Double = MAX_RANGE

    /** The tiles of the samples inside the area, from which [tiles] learns the lowest ground. */
    fun groundTiles(): Set<TileKey> {
        val keys = HashSet<TileKey>()
        for (k in 0 until lineCount) {
            if (lineCells[k] > 0) addTiles(keys, k, lineStart[k], viewStep, lineCells[k] * sub, viewZoom)
        }
        return keys
    }

    /**
     * Every tile [compute] reads, given the [ground] tiles (`null` = unavailable). Sets how far
     * upwind the lines start: terrain farther away cannot rise above the sun (design D4).
     */
    fun tiles(ground: Map<TileKey, HeightTile?>): Set<TileKey> {
        reach = reachAbove(lowestGround(ground))
        val keys = HashSet<TileKey>(ground.keys)
        for (k in 0 until lineCount) {
            if (lineCells[k] == 0) continue
            val start = lineStart[k]
            for (band in upwindBands()) {
                val count = ((band.far - band.near) / band.step).toInt()
                addTiles(keys, k, start - band.near - count * band.step, band.step, count, band.zoom)
            }
            addTiles(keys, k, start, viewStep, lineCells[k] * sub, viewZoom)
        }
        return keys
    }

    // The lowest height in the available ground tiles; the tile range's floor if there is none.
    private fun lowestGround(ground: Map<TileKey, HeightTile?>): Double {
        var lowest = Double.POSITIVE_INFINITY
        for (tile in ground.values) {
            if (tile == null) continue
            for (row in 0 until tile.size) {
                for (column in 0 until tile.size) {
                    val h = tile.height(row, column)
                    if (h < lowest) lowest = h
                }
            }
        }
        return if (lowest.isFinite()) lowest else LOWEST_HEIGHT
    }

    /** Distance beyond which terrain up to [heightBound] cannot rise above the sun's upper edge for eyes above [lowest]. */
    private fun reachAbove(lowest: Double): Double {
        if (upperEdge <= 0.0) return MAX_RANGE
        val t = kotlin.math.tan(Math.toRadians(upperEdge))
        val d = (-t + sqrt(t * t + 4 * CURVATURE * (heightBound - lowest))) / (2 * CURVATURE)
        return min(MAX_RANGE, d)
    }

    /** The states of [lines], from the [tiles] they sample (`null` = unavailable). */
    fun compute(
        tiles: Map<TileKey, HeightTile?>,
        lines: IntRange = 0 until lineCount,
    ): ShadeGridPart {
        val grids = HashMap<Int, TileGrid>()
        val grid = { zoom: Int -> grids.getOrPut(zoom) { TileGrid(zoom, tileSize, tiles) } }
        val hull = SunShadeHull(INITIAL_HULL)
        val states =
            Array(lines.count()) { index ->
                val k = lines.first + index
                val cells = ByteArray(lineCells[k])
                if (cells.isNotEmpty()) {
                    hull.clear()
                    val start = lineStart[k]
                    for (band in upwindBands()) {
                        sampleBand(grid(band.zoom), k, start - band.far, start - band.near, band.step) { s, h -> hull.push(s, h) }
                    }
                    var i = 0
                    sample(grid(viewZoom), k, start, viewStep, cells.size * sub) { s, h ->
                        if (i % sub == sub / 2) cells[i / sub] = state(hull, s, h) else hull.push(s, h)
                        i++
                    }
                }
                cells
            }
        return ShadeGridPart(lines, states)
    }

    /** The grid made of [parts], which together cover every line once. */
    fun assemble(parts: List<ShadeGridPart>): ShadeGrid {
        val states = arrayOfNulls<ByteArray>(lineCount)
        for (part in parts) part.lines.forEachIndexed { i, k -> states[k] = part.states[i] }
        return ShadeGrid(this, Array(lineCount) { k -> checkNotNull(states[k]) { "Line $k not computed" } })
    }

    /** Sun, shade or unknown at a cell's sample ([s], ground [h]); pushes the sample (design D1, D6). */
    private fun state(
        hull: SunShadeHull,
        s: Double,
        h: Double,
    ): Byte {
        if (h.isNaN()) {
            hull.push(s, h)
            return UNKNOWN
        }
        val tan = hull.pushObserver(s, h)
        val complete =
            hull.lastGap == Double.NEGATIVE_INFINITY ||
                run {
                    val d = s - hull.lastGap
                    tan >= (heightBound - (h + EYE_HEIGHT) - CURVATURE * d * d) / d
                }
        return when {
            upperEdge <= Math.toDegrees(atan(tan)) -> SHADE
            complete -> SUN
            else -> UNKNOWN
        }
    }

    /** Upwind bands, farthest first, cut at [reach] (design D3, D4). */
    private fun upwindBands(): List<Band> =
        listOf(
            Band(FAR_BAND, MAX_RANGE, 10),
            Band(MIDDLE_BAND, FAR_BAND, 11),
            Band(NEAR_BAND, MIDDLE_BAND, 12),
            Band(0.0, NEAR_BAND, viewZoom),
        ).filter { it.near < reach }
            .map { Band(it.near, min(it.far, reach), it.zoom, halfPixel(it.zoom)) }

    private class Band(
        val near: Double,
        val far: Double,
        val zoom: Int,
        val step: Double = 0.0,
    )

    /** Samples every [step] from [from] up to (excluding) [to] on line [k], nearest the sun first. */
    private inline fun sampleBand(
        grid: TileGrid,
        k: Int,
        from: Double,
        to: Double,
        step: Double,
        action: (s: Double, h: Double) -> Unit,
    ) {
        val count = ((to - from) / step).toInt()
        sample(grid, k, to - count * step, step, count, action)
    }

    /**
     * Heights at `s = from + i · step` (i < [count]) on line [k]: exact positions at knots every
     * [KNOT_EVERY] samples, linear in between, as in [HorizonTracer].
     */
    private inline fun sample(
        grid: TileGrid,
        k: Int,
        from: Double,
        step: Double,
        count: Int,
        action: (s: Double, h: Double) -> Unit,
    ) {
        forEachKnotPair(k, from, step, count, grid.zoom) { a, b, i0, stop, span ->
            for (i in i0 until stop) {
                val f = (i - i0).toDouble() / span
                action(from + i * step, grid.bilinear(a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f))
            }
        }
    }

    /**
     * For the samples `from + i · step` (i < [count]) of line [k], the pixel positions [a] and [b] at
     * [zoom] of consecutive knots `i0` and `i0 + span`; samples `i0 until stop` lie between them.
     */
    private inline fun forEachKnotPair(
        k: Int,
        from: Double,
        step: Double,
        count: Int,
        zoom: Int,
        action: (a: DoubleArray, b: DoubleArray, i0: Int, stop: Int, span: Int) -> Unit,
    ) {
        val a = DoubleArray(2)
        val b = DoubleArray(2)
        var i0 = 0
        while (i0 < count) {
            val i1 = min(i0 + KNOT_EVERY, count - 1)
            pixelOnLine(k, from + i0 * step, zoom, a)
            pixelOnLine(k, from + i1 * step, zoom, b)
            val stop = min(i0 + KNOT_EVERY, count)
            action(a, b, i0, stop, max(i1 - i0, 1))
            i0 = stop
        }
    }

    /** Adds the tiles of every sample on the knot-to-knot segments of a run of samples. */
    private fun addTiles(
        keys: MutableSet<TileKey>,
        k: Int,
        from: Double,
        step: Double,
        count: Int,
        zoom: Int,
    ) = forEachKnotPair(k, from, step, count, zoom) { a, b, _, _, _ ->
        // The samples and their bilinear neighbours lie in the pixel rectangle spanned by both knots.
        val x0 = floor(min(a[0], b[0])).toLong()
        val x1 = floor(max(a[0], b[0])).toLong() + 1
        val y0 = floor(min(a[1], b[1])).toLong()
        val y1 = floor(max(a[1], b[1])).toLong() + 1
        for (x in x0 / tileSize - 1..x1 / tileSize + 1) {
            for (y in y0 / tileSize - 1..y1 / tileSize + 1) {
                val left = x * tileSize
                val top = y * tileSize
                if (left <= x1 && left + tileSize > x0 && top <= y1 && top + tileSize > y0 && y >= 0) {
                    keys += tileKey(zoom, tileSize, left, top)
                }
            }
        }
    }

    /** Global pixel coordinates (pixel centres) at [zoom] of position [s] on line [k]. */
    private fun pixelOnLine(
        k: Int,
        s: Double,
        zoom: Int,
        out: DoubleArray,
    ) {
        pointOnLine(k, s, out)
        val worldPixels = (1L shl zoom).toDouble() * tileSize
        val sinLat = sin(Math.toRadians(out[0]))
        out[0] = (out[1] + 180.0) / 360.0 * worldPixels - 0.5
        out[1] = (1.0 - 0.5 * ln((1 + sinLat) / (1 - sinLat)) / PI) / 2.0 * worldPixels - 0.5
    }

    private fun halfPixel(zoom: Int): Double =
        PI * EARTH_RADIUS * cos(Math.toRadians(area.center.latitude)) / ((1L shl zoom).toDouble() * tileSize)

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

        internal const val SUN: Byte = 0
        internal const val SHADE: Byte = 1
        internal const val UNKNOWN: Byte = 2

        private const val MAX_ZOOM = 14
        private const val MAX_RANGE = 150_000.0
        private const val NEAR_BAND = 1_500.0
        private const val MIDDLE_BAND = 6_000.0
        private const val FAR_BAND = 25_000.0
        private const val KNOT_EVERY = 32
        private const val INITIAL_HULL = 1024

        // Lowest height a HeightTile can hold.
        private const val LOWEST_HEIGHT = -1000.0
    }
}

/** The states of some lines of a [SunShadeSweep]; [SunShadeSweep.assemble] joins them. */
class ShadeGridPart internal constructor(
    internal val lines: IntRange,
    internal val states: Array<ByteArray>,
)

/** Sun, shade or unknown for every cell of [sweep]'s area (sun-shade-overlay spec). */
class ShadeGrid internal constructor(
    private val sweep: SunShadeSweep,
    private val states: Array<ByteArray>,
) {
    val area: MapArea get() = sweep.area
    val sun: SunPosition get() = sweep.sun

    /** Whether some cell is unknown. */
    val hasUnknown: Boolean = states.any { line -> line.any { it == SunShadeSweep.UNKNOWN } }

    /** The state of the cell containing the point, or `null` outside the grid. */
    fun stateAt(
        latitude: Double,
        longitude: Double,
    ): Sunshine? {
        val (k, j) = sweep.cellOf(latitude, longitude) ?: return null
        return when (states[k][j]) {
            SunShadeSweep.SUN -> Sunshine.SUN
            SunShadeSweep.SHADE -> Sunshine.SHADE
            else -> Sunshine.UNKNOWN
        }
    }

    fun stateAt(point: GeoPoint): Sunshine? = stateAt(point.latitude, point.longitude)
}

/**
 * Upper convex hull of the samples of one line in (s, g = h − c·s²), where the spec's curved-earth
 * elevation tangent is a straight slope (design D1): from the eye (s_p, G_p = g_p + 1.7 m) to an
 * earlier sample j it is (g_j − G_p) / (s_p − s_j) − 2c·s_p. Samples must come in increasing s.
 */
internal class SunShadeHull(
    capacity: Int,
) {
    private var s = DoubleArray(capacity)
    private var g = DoubleArray(capacity)
    private var n = 0

    /** Position of the latest missing sample, or −∞. */
    var lastGap = Double.NEGATIVE_INFINITY
        private set

    fun clear() {
        n = 0
        lastGap = Double.NEGATIVE_INFINITY
    }

    /** Adds terrain sample ([position], height [h]); NaN records a gap. */
    fun push(
        position: Double,
        h: Double,
    ) {
        if (h.isNaN()) {
            lastGap = position
            return
        }
        val gp = h - CURVATURE * position * position
        popBelow(position, gp)
        append(position, gp)
    }

    /**
     * The tangent of the horizon of an eye [EYE_HEIGHT] above sample ([position], [h]) over all
     * earlier samples (−∞ if none), then adds the sample.
     */
    fun pushObserver(
        position: Double,
        h: Double,
    ): Double {
        val gp = h - CURVATURE * position * position
        // Vertices under the segment to the ground point are never the eye's tangent point either.
        popBelow(position, gp)
        val eye = gp + EYE_HEIGHT
        var best = Double.NEGATIVE_INFINITY
        if (n > 0) {
            var j = n - 1
            best = (g[j] - eye) / (position - s[j])
            while (j > 0) {
                val t = (g[j - 1] - eye) / (position - s[j - 1])
                if (t < best) break
                best = t
                j--
            }
        }
        append(position, gp)
        return best - 2 * CURVATURE * position
    }

    private fun popBelow(
        position: Double,
        gp: Double,
    ) {
        while (n >= 2 && (g[n - 1] - g[n - 2]) * (position - s[n - 2]) <= (gp - g[n - 2]) * (s[n - 1] - s[n - 2])) n--
    }

    private fun append(
        position: Double,
        gp: Double,
    ) {
        if (n == s.size) {
            s = s.copyOf(2 * n)
            g = g.copyOf(2 * n)
        }
        s[n] = position
        g[n] = gp
        n++
    }
}

/** Parabolic drop of the terrain line of sight per square metre of distance: (1 − k) / 2R. */
internal const val CURVATURE = (1 - REFRACTION) / (2 * EARTH_RADIUS)
