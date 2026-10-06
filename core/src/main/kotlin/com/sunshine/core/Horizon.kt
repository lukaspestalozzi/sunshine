package com.sunshine.core

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Horizon angle (degrees) for every [AZIMUTH_STEP] of azimuth, seen from an eye at [eyeHeight]
 * metres, with its [upper] bound. Where the upper bound lies above the angle, the bin is
 * incomplete: the ray met missing data, the angle is a lower bound, and terrain beyond the gap
 * could appear at most at the upper bound (terrain-horizon spec, "Incomplete horizon").
 */
class HorizonProfile(
    val eyeHeight: Double,
    val angles: DoubleArray,
    val upper: DoubleArray,
) {
    /** Whether each bin is complete, i.e. its upper bound equals its angle. */
    val complete: BooleanArray = BooleanArray(angles.size) { upper[it] <= angles[it] }

    /** Angle at [azimuth], interpolated linearly between the two neighbouring bins. */
    fun angleAt(azimuth: Double): Double {
        val (i, j, f) = bins(azimuth)
        return angles[i] + (angles[j] - angles[i]) * f
    }

    /** Upper bound at [azimuth]: the larger of the bins that [angleAt] uses (design D3 of polish-overlay). */
    fun upperAt(azimuth: Double): Double {
        val (i, j, f) = bins(azimuth)
        return if (f == 0.0) upper[i] else maxOf(upper[i], upper[j])
    }

    /** Whether the bins that [angleAt] uses at [azimuth] are complete. */
    fun isCompleteAt(azimuth: Double): Boolean {
        val (i, j, f) = bins(azimuth)
        return complete[i] && (f == 0.0 || complete[j])
    }

    private fun bins(azimuth: Double): Triple<Int, Int, Double> {
        val position = (((azimuth % 360.0) + 360.0) % 360.0) / AZIMUTH_STEP
        val i = floor(position).toInt() % AZIMUTH_COUNT
        return Triple(i, (i + 1) % AZIMUTH_COUNT, position - floor(position))
    }
}

/**
 * Traces the horizon of [observer] band by band (design D5 of add-terrain-horizon). The caller
 * supplies the tiles each step asks for; a `null` tile is unavailable. No I/O happens here.
 *
 * Usage: `start(groundTiles() -> tiles)`, then `advance(nextTiles() -> tiles)` until [isDone],
 * then [profile].
 */
class HorizonTracer(
    private val observer: GeoPoint,
    private val tileSize: Int = 512,
    private val heightBound: Double = heightBoundAt(observer),
) {
    private val sinLat = sin(Math.toRadians(observer.latitude))
    private val cosLat = cos(Math.toRadians(observer.latitude))

    // Largest slope (tan of the elevation angle) seen along each ray.
    private val maxSlope = DoubleArray(AZIMUTH_COUNT) { Double.NEGATIVE_INFINITY }

    // Distance of each ray's first missing sample, or infinity.
    private val gap = DoubleArray(AZIMUTH_COUNT) { Double.POSITIVE_INFINITY }
    private val active = BooleanArray(AZIMUTH_COUNT) { true }
    private var eyeHeight = Double.NaN
    private var groundUnknown = false
    private var band = 0

    val isDone: Boolean
        get() = groundUnknown || band >= BANDS.size || active.none { it }

    /** Tiles holding the observer's ground height (bilinear at zoom [GROUND_ZOOM]). */
    fun groundTiles(): Set<TileKey> = tilesFor(observer, GROUND_ZOOM, tileSize)

    fun start(tiles: Map<TileKey, HeightTile?>) {
        val available = tiles.mapNotNull { (key, tile) -> tile?.let { key to it } }.toMap()
        val keys = groundTiles()
        if (keys.isEmpty() || !available.keys.containsAll(keys)) {
            groundUnknown = true
            return
        }
        val ground = interpolateElevation(observer, GROUND_ZOOM, tileSize, available)
        if (ground.isNaN()) groundUnknown = true else eyeHeight = ground + EYE_HEIGHT
    }

    /** Tiles the active rays sample in the next band (from the knots of each ray, see [Knots]). */
    fun nextTiles(): Set<TileKey> {
        check(!eyeHeight.isNaN() && !isDone) { "start() first; not done" }
        val keys = HashSet<TileKey>()
        val zoom = BANDS[band].zoom
        val knots = Knots(BANDS[band])
        for (ray in 0 until AZIMUTH_COUNT) {
            if (!active[ray]) continue
            knots.trace(ray)
            for (j in 0 until knots.size - 1) {
                if (cannotRise(ray, knots.distance(j))) break
                // Samples between two knots lie on the segment between them; its pixels, plus the
                // bilinear neighbour, lie in the tile rectangle spanned by both ends.
                val x0 = floor(minOf(knots.x[j], knots.x[j + 1])).toLong()
                val x1 = floor(maxOf(knots.x[j], knots.x[j + 1])).toLong() + 1
                val y0 = floor(minOf(knots.y[j], knots.y[j + 1])).toLong()
                val y1 = floor(maxOf(knots.y[j], knots.y[j + 1])).toLong() + 1
                for (gx in longArrayOf(x0, x1)) {
                    for (gy in longArrayOf(y0, y1)) keys += tileKey(zoom, tileSize, gx, gy)
                }
            }
        }
        return keys
    }

    /** Samples the next band with [tiles] (as returned for [nextTiles]; `null` = unavailable). */
    fun advance(tiles: Map<TileKey, HeightTile?>) {
        val zoom = BANDS[band].zoom
        val grid = TileGrid(zoom, tileSize, tiles)
        forEachSample { ray, distance, x, y ->
            if (cannotRise(ray, distance)) {
                active[ray] = false
                return@forEachSample false
            }
            val h = grid.bilinear(x, y)
            if (h.isNaN()) {
                // The ray goes on: terrain beyond the gap still counts (design D3 of polish-overlay).
                if (distance < gap[ray]) gap[ray] = distance
                true
            } else {
                val slope = (h - drop(distance) - eyeHeight) / distance
                if (slope > maxSlope[ray]) maxSlope[ray] = slope
                true
            }
        }
        band++
    }

    /** The profile, or `null` when the observer's ground height is unknown. */
    fun profile(): HorizonProfile? {
        check(isDone) { "not done" }
        if (groundUnknown) return null
        val angles = DoubleArray(AZIMUTH_COUNT) { Math.toDegrees(atan(maxSlope[it])) }
        val upper =
            DoubleArray(AZIMUTH_COUNT) { ray ->
                val reach = if (gap[ray].isInfinite()) Double.NEGATIVE_INFINITY else boundBeyond(gap[ray])
                // Complete when nothing beyond the gap could rise above the slope found.
                if (reach <= maxSlope[ray]) angles[ray] else Math.toDegrees(atan(reach))
            }
        return HorizonProfile(eyeHeight, angles, upper)
    }

    /**
     * Exact early termination (design D3): terrain at distance d cannot appear steeper than
     * bound(d) = (heightBound - eye - drop(d)) / d. Beyond its maximum, bound(d) falls with d, so once
     * it is below the ray's largest slope, no further sample can change the ray. Before that maximum
     * (only when the eye is above heightBound, e.g. on the highest summit), bound(d) rises; as every
     * sample so far was at most its own bound, the ray's largest slope is then at most bound(d) and
     * the ray is never ended there.
     */
    private fun cannotRise(
        ray: Int,
        distance: Double,
    ): Boolean = (heightBound - eyeHeight - drop(distance)) / distance < maxSlope[ray]

    // The largest slope terrain beyond a gap at [distance] could have (see [cannotRise]).
    private fun boundBeyond(distance: Double): Double = slopeBound(distance, eyeHeight, heightBound)

    private fun drop(distance: Double): Double = distance * distance * (1 - REFRACTION) / (2 * EARTH_RADIUS)

    /**
     * Calls [sample] for every sample of the current band on every active ray, with global pixel
     * coordinates (pixel centres) at the band's zoom. [sample] returns false to end that ray.
     */
    private inline fun forEachSample(sample: (ray: Int, distance: Double, x: Double, y: Double) -> Boolean) {
        val knots = Knots(BANDS[band])
        for (ray in 0 until AZIMUTH_COUNT) {
            if (!active[ray]) continue
            knots.trace(ray)
            for (i in 0 until knots.samples) {
                val j = i / KNOT_EVERY
                val f = (i - j * KNOT_EVERY).toDouble() / KNOT_EVERY
                val x = knots.x[j] + (knots.x[j + 1] - knots.x[j]) * f
                val y = knots.y[j] + (knots.y[j + 1] - knots.y[j]) * f
                if (!sample(ray, knots.band.start + i * knots.step, x, y)) break
            }
        }
    }

    /**
     * Exact great-circle positions of one ray at every [KNOT_EVERY]-th sample of a band, in global
     * pixel coordinates. Samples in between are interpolated linearly: over 16 px the path deviates
     * from a straight line by far less than 0.001 px, and this saves the trigonometry per sample.
     */
    private inner class Knots(
        val band: Band,
    ) {
        val step = SAMPLE_STEP_PX * pixelSize(band.zoom)
        val samples = kotlin.math.ceil((band.end - band.start) / step).toInt()
        val size = (samples - 1) / KNOT_EVERY + 2
        val x = DoubleArray(size)
        val y = DoubleArray(size)
        private val worldPixels = (1L shl band.zoom).toDouble() * tileSize
        private val sinD = DoubleArray(size) { sin(distance(it) / EARTH_RADIUS) }
        private val cosD = DoubleArray(size) { cos(distance(it) / EARTH_RADIUS) }
        private val lon0 = Math.toRadians(observer.longitude)

        fun distance(knot: Int): Double = band.start + knot * KNOT_EVERY * step

        fun trace(ray: Int) {
            val theta = Math.toRadians(ray * AZIMUTH_STEP)
            val sinT = sin(theta)
            val cosT = cos(theta)
            for (j in 0 until size) {
                val sinLat2 = sinLat * cosD[j] + cosLat * sinD[j] * cosT
                // Not wrapped to ±180°: rays span at most 150 km, and tile lookups wrap x.
                val lon2 = lon0 + atan2(sinT * sinD[j] * cosLat, cosD[j] - sinLat * sinLat2)
                x[j] = (Math.toDegrees(lon2) + 180.0) / 360.0 * worldPixels - 0.5
                // Mercator: ln(tan φ + sec φ) = atanh(sin φ).
                y[j] = (1.0 - 0.5 * ln((1 + sinLat2) / (1 - sinLat2)) / PI) / 2.0 * worldPixels - 0.5
            }
        }
    }

    private fun pixelSize(zoom: Int): Double = 2 * PI * EARTH_RADIUS * cosLat / ((1L shl zoom).toDouble() * tileSize)

    private class Band(
        val start: Double,
        val end: Double,
        val zoom: Int,
    )

    private companion object {
        const val GROUND_ZOOM = 14
        const val SAMPLE_STEP_PX = 0.5
        const val KNOT_EVERY = 32
        val BANDS =
            listOf(
                Band(5.0, 375.0, 14),
                Band(375.0, 750.0, 14),
                Band(750.0, 1_500.0, 14),
                Band(1_500.0, 3_000.0, 12),
                Band(3_000.0, 6_000.0, 12),
                Band(6_000.0, 12_500.0, 11),
                Band(12_500.0, 25_000.0, 11),
                Band(25_000.0, 50_000.0, 10),
                Band(50_000.0, 100_000.0, 10),
                Band(100_000.0, 150_000.0, 10),
            )
    }
}

/** Bilinear heights from a band's tiles, remembering the last tile used. */
internal class TileGrid(
    val zoom: Int,
    private val tileSize: Int,
    private val tiles: Map<TileKey, HeightTile?>,
) {
    private val worldPixels = (1L shl zoom) * tileSize
    private var lastX = Long.MIN_VALUE
    private var lastY = Long.MIN_VALUE
    private var lastTile: HeightTile? = null

    // Global pixel of the cached tile's top-left pixel; its tile is [lastTile].
    private var originX = Long.MIN_VALUE
    private var originY = Long.MIN_VALUE

    fun bilinear(
        x: Double,
        y: Double,
    ): Double {
        val x0 = floor(x).toLong()
        val y0 = floor(y).toLong()
        val fx = x - x0
        val fy = y - y0
        val column = x0 - originX
        val row = y0 - originY
        if (column >= 0 && column < tileSize - 1 && row >= 0 && row < tileSize - 1) {
            // Fast path: all four neighbours lie in the cached tile.
            val tile = lastTile ?: return Double.NaN
            val c = column.toInt()
            val r = row.toInt()
            val nw = tile.height(r, c)
            val sw = tile.height(r + 1, c)
            val north = nw + (tile.height(r, c + 1) - nw) * fx
            val south = sw + (tile.height(r + 1, c + 1) - sw) * fx
            return north + (south - north) * fy
        }
        val nw = at(x0, y0)
        val ne = at(x0 + 1, y0)
        val sw = at(x0, y0 + 1)
        val se = at(x0 + 1, y0 + 1)
        val north = nw + (ne - nw) * fx
        val south = sw + (se - sw) * fx
        // Cache the tile of the north-west neighbour for the fast path.
        at(x0, y0)
        originX = x0 - Math.floorMod(x0, tileSize.toLong())
        originY = y0 - Math.floorMod(y0, tileSize.toLong())
        return north + (south - north) * fy
    }

    private fun at(
        gx: Long,
        gy: Long,
    ): Double {
        if (gy < 0 || gy >= worldPixels) return Double.NaN
        val wx = Math.floorMod(gx, worldPixels)
        val tx = wx / tileSize
        val ty = gy / tileSize
        if (tx != lastX || ty != lastY) {
            lastTile = tiles[TileKey(zoom, tx.toInt(), ty.toInt())]
            lastX = tx
            lastY = ty
        }
        val tile = lastTile ?: return Double.NaN
        return tile.height((gy % tileSize).toInt(), (wx % tileSize).toInt())
    }
}

/** The tile holding global pixel ([gx], [gy]) at [zoom]; x wraps around the world. */
internal fun tileKey(
    zoom: Int,
    tileSize: Int,
    gx: Long,
    gy: Long,
): TileKey {
    val worldPixels = (1L shl zoom) * tileSize
    return TileKey(zoom, (Math.floorMod(gx, worldPixels) / tileSize).toInt(), (gy / tileSize).toInt())
}

/**
 * The largest slope terrain up to [heightBound] could have at [distance] or beyond, seen from an eye
 * at [eyeHeight]: bound(d) = (heightBound - eye - c·d²) / d falls with d when the eye is below
 * heightBound; otherwise it peaks where d² = (eye - heightBound) / c. Shared by the point tracer and
 * the sweep, so that panel and overlay agree where data is missing (design D3 of polish-overlay).
 */
internal fun slopeBound(
    distance: Double,
    eyeHeight: Double,
    heightBound: Double,
): Double {
    val peak = if (eyeHeight > heightBound) sqrt((eyeHeight - heightBound) / CURVATURE) else 0.0
    val d = maxOf(distance, peak)
    return (heightBound - eyeHeight - CURVATURE * d * d) / d
}

/**
 * Highest terrain that can lie within 150 km of [point] (design D3): Mont Blanc (4810 m) in Europe
 * west of the Caucasus (35–72° N, 25° W–35° E; the nearest higher peak, Elbrus, is at 42.4° E),
 * Everest (8849 m) elsewhere.
 */
fun heightBoundAt(point: GeoPoint): Double = if (point.latitude in 35.0..72.0 && point.longitude in -25.0..35.0) 4810.0 else 8849.0

/** Azimuth resolution of a [HorizonProfile] in degrees, and the number of bins. */
const val AZIMUTH_STEP = 0.25
const val AZIMUTH_COUNT = 1440

/** Eye height above the ground in metres. */
const val EYE_HEIGHT = 1.7

internal const val EARTH_RADIUS = 6_371_000.0
internal const val REFRACTION = 0.13
