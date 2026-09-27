package com.sunshine.app.elevation

import com.sunshine.core.GeoPoint
import com.sunshine.core.HeightTile
import com.sunshine.core.TileKey
import com.sunshine.core.interpolateElevation
import com.sunshine.core.terrariumHeights
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

/**
 * Elevation from Mapterhorn tiles (design D8). [fetch] loads a tile's bytes (`null` if unavailable)
 * and [decode] turns them into ARGB pixels (`null` if undecodable). Decoded tiles are kept in memory,
 * at most [MEMORY_TILES] of them, least recently used first out.
 */
class ElevationRepository(
    private val fetch: suspend (TileKey) -> ByteArray?,
    private val decode: (ByteArray) -> IntArray?,
) {
    private val tiles =
        object : LinkedHashMap<TileKey, HeightTile>(MEMORY_TILES, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<TileKey, HeightTile>) = size > MEMORY_TILES
        }

    /**
     * The elevation at [point], loading the tiles it needs that are not in memory. Tiles that load
     * are kept even when a neighbour fails, so a retry fetches only the missing ones.
     */
    suspend fun elevation(point: GeoPoint): Elevation {
        val keys = keys(point)
        if (keys.isEmpty()) return Elevation.Unknown
        val inMemory = inMemory(keys)
        val loaded =
            coroutineScope {
                (keys - inMemory.keys).map { key -> async { load(key)?.let { key to it } } }.awaitAll()
            }.filterNotNull().toMap()
        synchronized(tiles) { tiles.putAll(loaded) }
        val available = inMemory + loaded
        return if (available.size == keys.size) known(point, available) else Elevation.Unknown
    }

    /** The elevation at [point] if it can be answered from memory alone, else `null`. */
    fun cachedElevation(point: GeoPoint): Elevation? {
        val keys = keys(point)
        if (keys.isEmpty()) return Elevation.Unknown
        val inMemory = inMemory(keys)
        return if (inMemory.size == keys.size) known(point, inMemory) else null
    }

    private fun inMemory(keys: Set<TileKey>): Map<TileKey, HeightTile> =
        synchronized(tiles) { keys.mapNotNull { key -> tiles[key]?.let { key to it } }.toMap() }

    private suspend fun load(key: TileKey): HeightTile? {
        val pixels = fetch(key)?.let(decode) ?: return null
        if (pixels.size != MapterhornTiles.TILE_SIZE * MapterhornTiles.TILE_SIZE) return null
        return HeightTile.fromMetres(MapterhornTiles.TILE_SIZE, terrariumHeights(pixels))
    }

    private fun keys(point: GeoPoint) = tilesFor(point, MapterhornTiles.ZOOM, MapterhornTiles.TILE_SIZE)

    private fun known(
        point: GeoPoint,
        tiles: Map<TileKey, HeightTile>,
    ) = Elevation.Known(interpolateElevation(point, MapterhornTiles.ZOOM, MapterhornTiles.TILE_SIZE, tiles))

    private companion object {
        const val MEMORY_TILES = 8
        const val LOAD_FACTOR = 0.75f
    }
}
