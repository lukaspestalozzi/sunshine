package com.sunshine.app.elevation

import com.sunshine.core.GeoPoint
import com.sunshine.core.HeightTile
import com.sunshine.core.TileKey
import com.sunshine.core.interpolateElevation
import com.sunshine.core.tilesFor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Ground elevation of a location: known in metres, or explicitly unknown (never a default). */
sealed interface Elevation {
    data class Known(
        val metres: Double,
    ) : Elevation

    data object Unknown : Elevation
}

/** Elevation from Mapterhorn tiles at zoom 12 (design D8 of add-elevation-data), read through [tiles]. */
class ElevationRepository(
    private val tiles: TileCache,
) {
    /**
     * The elevation at [point], loading the tiles it needs that are not in memory. Tiles that load
     * are kept even when a neighbour fails, so a retry fetches only the missing ones.
     */
    suspend fun elevation(point: GeoPoint): Elevation {
        val keys = keys(point)
        if (keys.isEmpty()) return Elevation.Unknown
        val available =
            coroutineScope {
                keys.map { key -> async { tiles.tile(key)?.let { key to it } } }.awaitAll()
            }.filterNotNull().toMap()
        return if (available.size == keys.size) known(point, available) else Elevation.Unknown
    }

    /** The elevation at [point] if it can be answered from memory alone, else `null`. */
    fun cachedElevation(point: GeoPoint): Elevation? {
        val keys = keys(point)
        if (keys.isEmpty()) return Elevation.Unknown
        val inMemory = keys.mapNotNull { key -> tiles.cached(key)?.let { key to it } }.toMap()
        return if (inMemory.size == keys.size) known(point, inMemory) else null
    }

    private fun keys(point: GeoPoint) = tilesFor(point, MapterhornTiles.ZOOM, MapterhornTiles.TILE_SIZE)

    private fun known(
        point: GeoPoint,
        tiles: Map<TileKey, HeightTile>,
    ) = Elevation.Known(interpolateElevation(point, MapterhornTiles.ZOOM, MapterhornTiles.TILE_SIZE, tiles))
}
