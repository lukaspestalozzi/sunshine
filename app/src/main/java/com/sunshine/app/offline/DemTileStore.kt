package com.sunshine.app.offline

import com.sunshine.core.TileKey
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** When a stored tile must be revalidated, and with what (offline-regions spec, "Freshness of stored tiles"). */
data class Validators(
    val etag: String?,
    val lastModified: String?,
    val freshUntil: Long,
)

/** A stored Mapterhorn tile: its bytes, or none when the server does not publish it (404). */
class StoredTile(
    val bytes: ByteArray?,
    val validators: Validators,
)

/**
 * The persistent DEM tiles (design D5 of add-offline-regions): one file per tile under
 * [directory], indexed by [dao]. A tile a region claims is kept; the others are browsed tiles,
 * limited to [browsedLimitBytes] and evicted least recently used first. [now] is the wall clock
 * in ms.
 */
class DemTileStore(
    private val dao: OfflineDao,
    private val directory: File,
    browsedLimitBytes: Long = BROWSED_LIMIT_BYTES,
    private val now: () -> Long = System::currentTimeMillis,
) {
    @Volatile
    private var browsedLimitBytes: Long = browsedLimitBytes

    // Uses since the last flush; written in batches, so that reading a tile needs no write.
    private val uses = HashMap<TileKey, Long>()
    private var lastFlush = 0L

    /** The stored tile, or `null` if it is not stored. */
    suspend fun get(key: TileKey): StoredTile? {
        val row = dao.tile(key.zoom, key.x, key.y) ?: return null
        val bytes =
            if (row.found) {
                withContext(Dispatchers.IO) { fileOf(key).takeIf { it.exists() }?.readBytes() }
                    ?: return null.also { dao.deleteTile(key.zoom, key.x, key.y) } // the file is gone
            } else {
                null
            }
        use(key)
        return StoredTile(bytes, Validators(row.etag, row.lastModified, row.freshUntil))
    }

    /** Stores [bytes] as the tile [key], claimed by [region] if not `null` (browsed otherwise). */
    suspend fun putFound(
        key: TileKey,
        bytes: ByteArray,
        validators: Validators,
        region: Long?,
    ) {
        withContext(Dispatchers.IO) { write(key, bytes) }
        put(key, found = true, size = bytes.size.toLong(), validators, region)
    }

    /** Records that the server does not publish [key] (HTTP 404). */
    suspend fun putMissing(
        key: TileKey,
        validators: Validators,
        region: Long?,
    ) {
        withContext(Dispatchers.IO) { fileOf(key).delete() }
        put(key, found = false, size = 0, validators, region)
    }

    /** New validators for a tile that did not change (HTTP 304); its bytes and claims stay. */
    suspend fun refresh(
        key: TileKey,
        validators: Validators,
    ) = dao.updateValidators(key.zoom, key.x, key.y, validators.etag, validators.lastModified, validators.freshUntil)

    /** Adds [region]'s claim on a stored tile. */
    suspend fun claim(
        key: TileKey,
        region: Long,
    ) = dao.insertClaim(RegionDemTileRow(region, key.zoom, key.x, key.y))

    /** Drops [region]'s claims; tiles no other region claims become browsed and may be evicted. */
    suspend fun deleteRegion(region: Long) {
        dao.deleteClaims(region)
        evict()
    }

    suspend fun browsedBytes(): Long = dao.browsedBytes()

    /** A new limit for the browsed tiles, evicted down to it at once (design D9 of add-settings). */
    suspend fun setBrowsedLimit(bytes: Long) {
        browsedLimitBytes = bytes
        evict()
    }

    /**
     * Removes every browsed tile and browsed 404 record; region tiles stay (offline-regions spec,
     * "Clear browsed tiles"). The caller makes sure that no region download runs meanwhile.
     */
    suspend fun clearBrowsed() {
        flushUses()
        while (true) {
            val batch = dao.browsedTiles(EVICT_BATCH)
            if (batch.isEmpty()) return
            for (row in batch) {
                if (dao.deleteBrowsed(row.z, row.x, row.y) == 1) {
                    withContext(Dispatchers.IO) { fileOf(TileKey(row.z, row.x, row.y)).delete() }
                }
            }
        }
    }

    suspend fun totalBytes(): Long = dao.totalBytes()

    /** At startup: drops rows whose file is gone, and files and temporary files without a row. */
    suspend fun reconcile() {
        val rows = dao.foundTiles()
        val kept = HashSet<String>()
        for (row in rows) {
            val file = fileOf(TileKey(row.z, row.x, row.y))
            if (withContext(Dispatchers.IO) { file.exists() }) kept += file.path else dao.deleteTile(row.z, row.x, row.y)
        }
        withContext(Dispatchers.IO) {
            directory.walkBottomUp().filter { it.isFile && it.path !in kept }.forEach { it.delete() }
        }
    }

    private suspend fun put(
        key: TileKey,
        found: Boolean,
        size: Long,
        validators: Validators,
        region: Long?,
    ) {
        synchronized(uses) { uses.remove(key) }
        val row = DemTileRow(key.zoom, key.x, key.y, found, size, validators.etag, validators.lastModified, validators.freshUntil, now())
        dao.upsertTile(row, region?.let { RegionDemTileRow(it, key.zoom, key.x, key.y) })
        if (region == null) evict()
    }

    private suspend fun use(key: TileKey) {
        val time = now()
        val due =
            synchronized(uses) {
                uses[key] = time
                time - lastFlush >= FLUSH_EVERY_MILLIS
            }
        if (due) flushUses()
    }

    private suspend fun flushUses() {
        val batch =
            synchronized(uses) {
                lastFlush = now()
                uses.toMap().also { uses.clear() }
            }
        batch.forEach { (key, time) -> dao.updateLastUsed(key.zoom, key.x, key.y, time) }
    }

    /** Removes the least recently used browsed tiles until they fit [browsedLimitBytes]. */
    suspend fun evict() {
        flushUses()
        var excess = dao.browsedBytes() - browsedLimitBytes
        while (excess > 0) {
            val oldest = dao.oldestBrowsed(EVICT_BATCH)
            if (oldest.isEmpty()) return
            for (row in oldest) {
                if (excess <= 0) return
                // A region may have claimed it since the query; then it stays.
                if (dao.deleteBrowsed(row.z, row.x, row.y) == 1) {
                    withContext(Dispatchers.IO) { fileOf(TileKey(row.z, row.x, row.y)).delete() }
                    excess -= row.bytes
                }
            }
        }
    }

    /** Writes a temporary file and renames it, so a tile file is either complete or absent. */
    private fun write(
        key: TileKey,
        bytes: ByteArray,
    ) {
        val file = fileOf(key)
        file.parentFile!!.mkdirs()
        val temporary = File.createTempFile("tile", ".tmp", file.parentFile)
        temporary.writeBytes(bytes)
        check(temporary.renameTo(file)) { "Could not store $file" }
    }

    private fun fileOf(key: TileKey) = File(directory, "${key.zoom}/${key.x}/${key.y}.webp")

    companion object {
        /** Browsed DEM tiles are kept up to this size by default (offline-regions spec, "Kept tiles"). */
        const val BROWSED_LIMIT_BYTES = 512L * 1024 * 1024
        private const val FLUSH_EVERY_MILLIS = 5_000L
        private const val EVICT_BATCH = 50
    }
}
