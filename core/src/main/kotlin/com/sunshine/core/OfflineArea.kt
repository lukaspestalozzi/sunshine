package com.sunshine.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.tan

/** A latitude/longitude rectangle in decimal degrees (design D8 of add-offline-regions). */
data class GeoBounds(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    /** This rectangle grown on every side by [marginMetres] (offline-regions spec, "Region contents"). */
    fun extend(marginMetres: Double): GeoBounds {
        val latitudeMargin = marginMetres / METRES_PER_DEGREE
        val south = (south - latitudeMargin).coerceAtLeast(-MAX_MAP_LATITUDE)
        val north = (north + latitudeMargin).coerceAtMost(MAX_MAP_LATITUDE)
        val farthest = max(abs(south), abs(north))
        val longitudeMargin = marginMetres / (METRES_PER_DEGREE * cos(Math.toRadians(farthest)))
        return GeoBounds(south, (west - longitudeMargin).coerceAtLeast(-180.0), north, (east + longitudeMargin).coerceAtMost(180.0))
    }

    companion object {
        /** The rectangle of the visible [area]. */
        fun of(area: MapArea): GeoBounds {
            val corners = area.corners()
            return GeoBounds(
                south = corners.minOf { it.latitude },
                west = corners.minOf { it.longitude },
                north = corners.maxOf { it.latitude },
                east = corners.maxOf { it.longitude },
            )
        }
    }
}

/**
 * Whether this visible area crosses the 180° meridian. Such an area is not downloaded
 * (offline-regions spec, "Download the visible area"): its wrapped corners would span almost 360°.
 */
fun MapArea.crossesAntimeridian(): Boolean {
    val halfWidthDegrees = widthDp / 2 / (MAP_TILE_DP * 2.0.pow(zoom)) * 360
    return center.longitude - halfWidthDegrees < -180 || center.longitude + halfWidthDegrees > 180
}

/** The Web-Mercator tiles from [minX] to [maxX] and [minY] to [maxY] (inclusive) at [zoom]. */
data class TileRange(
    val zoom: Int,
    val minX: Int,
    val maxX: Int,
    val minY: Int,
    val maxY: Int,
) {
    val count: Long get() = (maxX - minX + 1).toLong() * (maxY - minY + 1)

    fun keys(): Sequence<TileKey> = (minY..maxY).asSequence().flatMap { y -> (minX..maxX).asSequence().map { x -> TileKey(zoom, x, y) } }
}

/** The tiles at [zoom] that intersect [bounds]. */
fun tileRange(
    bounds: GeoBounds,
    zoom: Int,
): TileRange {
    val tiles = 1 shl zoom

    fun x(longitude: Double) = floor((longitude + 180.0) / 360.0 * tiles).toInt().coerceIn(0, tiles - 1)

    fun y(latitude: Double): Int {
        val phi = Math.toRadians(latitude.coerceIn(-MAX_MAP_LATITUDE, MAX_MAP_LATITUDE))
        return floor((1 - ln(tan(phi) + 1 / cos(phi)) / PI) / 2 * tiles).toInt().coerceIn(0, tiles - 1)
    }
    return TileRange(zoom, x(bounds.west), x(bounds.east), y(bounds.north), y(bounds.south))
}

/**
 * The Mapterhorn tiles a region needs: what the horizon and the overlay read for any location in
 * [bounds] (offline-regions spec, "Region contents").
 */
fun regionDemTiles(bounds: GeoBounds): List<TileRange> = DEM_MARGINS.map { (zoom, margin) -> tileRange(bounds.extend(margin), zoom) }

/** How many OpenTopoMap tiles a region needs: map zooms 5 to 16 load tile zooms 6 to 17. */
fun regionMapTileCount(bounds: GeoBounds): Long = (MIN_MAP_TILE_ZOOM..MAX_MAP_TILE_ZOOM).sumOf { tileRange(bounds, it).count }

/** What downloading [bounds] takes, for the Offline page (offline-regions spec, "Download the visible area"). */
data class DownloadEstimate(
    val widthKm: Double,
    val heightKm: Double,
    val mapTiles: Long,
    val demTiles: Long,
    val minutes: Long,
)

fun downloadEstimate(bounds: GeoBounds): DownloadEstimate {
    val centreLatitude = (bounds.south + bounds.north) / 2
    val mapTiles = regionMapTileCount(bounds)
    val demTiles = regionDemTiles(bounds).sumOf { it.count }
    return DownloadEstimate(
        widthKm = (bounds.east - bounds.west) * METRES_PER_DEGREE * cos(Math.toRadians(centreLatitude)) / 1000,
        heightKm = (bounds.north - bounds.south) * METRES_PER_DEGREE / 1000,
        mapTiles = mapTiles,
        demTiles = demTiles,
        minutes = ceil(max(mapTiles, demTiles) / REQUESTS_PER_SECOND / SECONDS_PER_MINUTE).toLong(),
    )
}

/** Region downloads start at most this many requests per second and server. */
const val REQUESTS_PER_SECOND = 5.0

private const val SECONDS_PER_MINUTE = 60.0
private const val MAX_MAP_LATITUDE = 85.0511287798
private const val MAP_TILE_DP = 512.0
private const val METRES_PER_DEGREE = 2 * PI * EARTH_RADIUS / 360
private const val MIN_MAP_TILE_ZOOM = 6
private const val MAX_MAP_TILE_ZOOM = 17

// Zoom and margin in metres: the bands of the horizon and the overlay (terrain-horizon, sun-shade-overlay).
private val DEM_MARGINS = listOf(14 to 1_500.0, 13 to 1_500.0, 12 to 6_000.0, 11 to 25_000.0, 10 to 150_000.0)
