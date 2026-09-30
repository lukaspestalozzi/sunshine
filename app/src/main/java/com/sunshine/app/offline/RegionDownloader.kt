package com.sunshine.app.offline

import com.sunshine.app.elevation.DemTile
import com.sunshine.core.GeoBounds
import com.sunshine.core.TileKey
import com.sunshine.core.regionDemTiles
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Progress of a region's map tiles, as MapLibre reports it. */
data class MapStatus(
    val completed: Long,
    val required: Long,
    val bytes: Long,
)

/** The map tiles of a region: MapLibre's offline region (design D7 of add-offline-regions). */
interface MapRegionPart {
    /**
     * Downloads the map tiles of [region] until all are stored, reporting to [onStatus], and returns
     * their size in bytes. Cancelling pauses the download; calling again resumes it.
     */
    suspend fun download(
        region: RegionRow,
        onStatus: suspend (MapStatus) -> Unit,
    ): Long
}

/**
 * Downloads the queued regions, oldest first and one at a time (design D7): the map part and the
 * DEM tiles in parallel. [fetchDem] fetches a DEM tile for a region, which claims it; a tile that
 * cannot be obtained is tried again after 5 s, doubling up to 5 min. A region is complete only
 * when both parts are. [onRegionBytes] receives the regions' map size (and whether to apply it at
 * once), [onProgress] the region's progress in percent.
 */
class RegionDownloader(
    private val dao: OfflineDao,
    private val map: MapRegionPart,
    private val fetchDem: suspend (TileKey, Long) -> DemTile,
    private val demTilesOf: (GeoBounds) -> List<TileKey> = { bounds -> regionDemTiles(bounds).flatMap { it.keys() } },
    private val onRegionBytes: (Long, Boolean) -> Unit = { _, _ -> },
    private val onProgress: (RegionRow, Int) -> Unit = { _, _ -> },
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val running = HashMap<Long, Job>()

    /** Downloads until no region is queued any more. */
    suspend fun downloadAll() {
        while (true) {
            val region = dao.nextRegionToDownload() ?: return
            coroutineScope {
                val job = launch { download(region) }
                synchronized(running) { running[region.id] = job }
                job.join()
                synchronized(running) { running.remove(region.id) }
            }
        }
    }

    /** Stops the download of a region that is being deleted (design D10); the next one goes on. */
    fun cancel(regionId: Long) {
        synchronized(running) { running[regionId] }?.cancel()
    }

    private suspend fun download(region: RegionRow) =
        coroutineScope {
            val keys = demTilesOf(GeoBounds(region.south, region.west, region.north, region.east))
            val progress = Progress(region, keys.size.toLong())
            val mapBytes = async { map.download(region) { status -> progress.onMap(status) } }
            for (key in keys) {
                if (dao.region(region.id)?.state != RegionState.QUEUED) {
                    mapBytes.cancel()
                    return@coroutineScope
                }
                fetchWithRetry(key, region.id)
                progress.onDemTile()
            }
            val bytes = mapBytes.await()
            dao.complete(region.id, completedAt = now(), mapBytes = bytes)
            onRegionBytes(dao.regionMapBytes(), true)
            onProgress(region, regionProgress(1, 1, complete = true))
        }

    private suspend fun fetchWithRetry(
        key: TileKey,
        region: Long,
    ) {
        var wait = FIRST_RETRY_MILLIS
        while (fetchDem(key, region) == DemTile.Unavailable) {
            delay(wait)
            wait = (wait * 2).coerceAtMost(LAST_RETRY_MILLIS)
        }
    }

    /** Writes the region's progress at most once per second. */
    private inner class Progress(
        private val region: RegionRow,
        private val demTiles: Long,
    ) {
        private var map = MapStatus(0, 0, 0)
        private var demObtained = 0L
        private var lastWrite: Long? = null

        suspend fun onMap(status: MapStatus) {
            synchronized(this) { map = status }
            write()
        }

        suspend fun onDemTile() {
            synchronized(this) { demObtained++ }
            write()
        }

        private suspend fun write() {
            val time = now()
            val (percent, bytes) =
                synchronized(this) {
                    val last = lastWrite
                    if (last != null && time - last < WRITE_EVERY_MILLIS) return
                    lastWrite = time
                    regionProgress(map.completed + demObtained, map.required + demTiles, complete = false) to map.bytes
                }
            dao.updateProgress(region.id, percent, bytes)
            onRegionBytes(dao.regionMapBytes(), false)
            onProgress(region, percent)
        }
    }

    private companion object {
        const val FIRST_RETRY_MILLIS = 5_000L
        const val LAST_RETRY_MILLIS = 300_000L
        const val WRITE_EVERY_MILLIS = 1_000L
    }
}
