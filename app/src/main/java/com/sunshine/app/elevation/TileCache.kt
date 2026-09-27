package com.sunshine.app.elevation

import com.sunshine.core.HeightTile
import com.sunshine.core.TileKey
import com.sunshine.core.terrariumHeights
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Decoded Mapterhorn tiles shared by point elevation and the horizon (design D6, D7 of
 * add-terrain-horizon): at most [MEMORY_TILES] compact tiles in memory, least recently used first
 * out. [fetch] loads a tile's bytes, [decode] turns them into ARGB pixels (`null` if undecodable).
 */
class TileCache(
    private val fetch: suspend (TileKey) -> DemTile,
    private val decode: (ByteArray) -> IntArray?,
) {
    private val tiles =
        object : LinkedHashMap<TileKey, HeightTile>(MEMORY_TILES, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<TileKey, HeightTile>) = size > MEMORY_TILES
        }

    // One lock per tile being loaded, so that concurrent requests fetch it once.
    private val loading = HashMap<TileKey, Mutex>()

    /** The tile if it is in memory, else `null`. */
    fun cached(key: TileKey): HeightTile? = synchronized(tiles) { tiles[key] }

    /**
     * The tile, loaded if it is not in memory; `null` if it is unavailable. A tile the server does
     * not publish above zoom [MIN_FALLBACK_ZOOM] is upsampled from its parent. Unavailable tiles
     * are not remembered, so a later request tries again.
     */
    suspend fun tile(key: TileKey): HeightTile? {
        cached(key)?.let { return it }
        val lock = synchronized(loading) { loading.getOrPut(key) { Mutex() } }
        return try {
            lock.withLock { cached(key) ?: load(key)?.also { synchronized(tiles) { tiles[key] = it } } }
        } finally {
            synchronized(loading) { if (!lock.isLocked) loading.remove(key, lock) }
        }
    }

    private suspend fun load(key: TileKey): HeightTile? =
        when (val result = fetch(key)) {
            is DemTile.Found -> decodeTile(result.bytes)
            DemTile.Missing -> if (key.zoom > MIN_FALLBACK_ZOOM) tile(parentOf(key))?.let { upsample(it, key) } else null
            DemTile.Unavailable -> null
        }

    private fun decodeTile(bytes: ByteArray): HeightTile? {
        val pixels = decode(bytes) ?: return null
        if (pixels.size != SIZE * SIZE) return null
        return HeightTile.fromMetres(SIZE, terrariumHeights(pixels))
    }

    private fun parentOf(key: TileKey) = TileKey(key.zoom - 1, key.x / 2, key.y / 2)

    /**
     * The quadrant of [parent] that [child] covers, sampled bilinearly at the child's pixel centres.
     * The outermost quarter pixel of the parent has no neighbour inside it and uses the edge value.
     */
    private fun upsample(
        parent: HeightTile,
        child: TileKey,
    ): HeightTile {
        val rowOffset = (child.y % 2) * SIZE / 2
        val columnOffset = (child.x % 2) * SIZE / 2
        val metres =
            FloatArray(SIZE * SIZE) { i ->
                val row = (rowOffset + (i / SIZE + 0.5) / 2 - 0.5).coerceIn(0.0, SIZE - 1.0)
                val column = (columnOffset + (i % SIZE + 0.5) / 2 - 0.5).coerceIn(0.0, SIZE - 1.0)
                val r = minOf(row.toInt(), SIZE - 2)
                val c = minOf(column.toInt(), SIZE - 2)
                val fr = row - r
                val fc = column - c
                val north = parent.height(r, c) + (parent.height(r, c + 1) - parent.height(r, c)) * fc
                val south = parent.height(r + 1, c) + (parent.height(r + 1, c + 1) - parent.height(r + 1, c)) * fc
                (north + (south - north) * fr).toFloat()
            }
        return HeightTile.fromMetres(SIZE, metres)
    }

    private companion object {
        const val SIZE = MapterhornTiles.TILE_SIZE
        const val MEMORY_TILES = 64
        const val LOAD_FACTOR = 0.75f

        // Mapterhorn publishes zooms 0–12 everywhere; finer zooms only where the sources allow.
        const val MIN_FALLBACK_ZOOM = 12
    }
}
