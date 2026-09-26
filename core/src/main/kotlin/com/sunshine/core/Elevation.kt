package com.sunshine.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/** A Web-Mercator (XYZ) tile. */
data class TileKey(
    val zoom: Int,
    val x: Int,
    val y: Int,
)

/** A square height tile: [size] × [size] heights in metres, row-major, one per pixel centre. */
class HeightTile(
    val size: Int,
    val heights: FloatArray,
) {
    init {
        require(heights.size == size * size) { "Expected ${size * size} heights, was ${heights.size}" }
    }
}

/**
 * The tiles whose samples the bilinear interpolation at [point] needs: 1, 2 or 4. Empty outside
 * the Web-Mercator latitude range (±85.0511°), where no tile exists.
 */
fun tilesFor(
    point: GeoPoint,
    zoom: Int,
    tileSize: Int,
): Set<TileKey> =
    samplesAround(point, zoom, tileSize)
        ?.samples
        ?.map { it.tile }
        ?.toSet()
        .orEmpty()

/**
 * Elevation in metres at [point]: bilinear interpolation of the four nearest pixel-centre heights.
 * Every tile of [tilesFor] (same [zoom] and [tileSize]) must be in [tiles]; a caller without all
 * of them has no elevation for the point.
 */
fun interpolateElevation(
    point: GeoPoint,
    zoom: Int,
    tileSize: Int,
    tiles: Map<TileKey, HeightTile>,
): Double {
    val neighbourhood = requireNotNull(samplesAround(point, zoom, tileSize)) { "No tiles at $point" }
    val (northWest, northEast, southWest, southEast) =
        neighbourhood.samples.map { sample ->
            val tile = requireNotNull(tiles[sample.tile]) { "Missing tile ${sample.tile}" }
            tile.heights[sample.row * tile.size + sample.column].toDouble()
        }
    val east = neighbourhood.eastWeight
    val south = neighbourhood.southWeight
    val northRow = northWest + (northEast - northWest) * east
    val southRow = southWest + (southEast - southWest) * east
    return northRow + (southRow - northRow) * south
}

/** One height sample: pixel [column]/[row] of [tile]. */
private data class Sample(
    val tile: TileKey,
    val column: Int,
    val row: Int,
)

/** The four samples around [point] with the bilinear weights of the east and south neighbours. */
private class Neighbourhood(
    val samples: List<Sample>,
    val eastWeight: Double,
    val southWeight: Double,
)

private fun samplesAround(
    point: GeoPoint,
    zoom: Int,
    tileSize: Int,
): Neighbourhood? {
    if (point.latitude !in -MAX_LATITUDE..MAX_LATITUDE) return null
    val worldPixels = (1L shl zoom) * tileSize
    // Global pixel coordinates relative to pixel centres.
    val x = (point.longitude + 180.0) / 360.0 * worldPixels - 0.5
    val latitude = Math.toRadians(point.latitude)
    val y = (1.0 - ln(tan(latitude) + 1.0 / cos(latitude)) / PI) / 2.0 * worldPixels - 0.5
    val west = floor(x).toLong()
    val north = floor(y).toLong()
    if (north < 0 || north + 1 >= worldPixels) return null
    val samples =
        listOf(north, north + 1).flatMap { row ->
            listOf(west, west + 1).map { column ->
                val wrapped = Math.floorMod(column, worldPixels)
                Sample(
                    tile = TileKey(zoom, (wrapped / tileSize).toInt(), (row / tileSize).toInt()),
                    column = (wrapped % tileSize).toInt(),
                    row = (row % tileSize).toInt(),
                )
            }
        }
    return Neighbourhood(samples, eastWeight = x - west, southWeight = y - north)
}

/** Latitude limit of the square Web-Mercator world. */
private const val MAX_LATITUDE = 85.0511287798
