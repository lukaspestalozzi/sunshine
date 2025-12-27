package com.sunshine.app.data.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sunshine.app.data.local.database.DownloadedRegionDao
import com.sunshine.app.data.local.database.entities.DownloadedRegionEntity
import com.sunshine.app.data.local.database.entities.DownloadStatus
import com.sunshine.app.domain.model.BoundingBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.osmdroid.config.Configuration
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * WorkManager worker that downloads map tiles for a specific region.
 * Runs in the background and survives app restarts.
 */
@Suppress("TooManyFunctions")
class TileDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {
    private val downloadedRegionDao: DownloadedRegionDao by inject()

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val regionId = inputData.getString(KEY_REGION_ID) ?: return@withContext Result.failure()
            val name = inputData.getString(KEY_NAME) ?: return@withContext Result.failure()
            val north = inputData.getDouble(KEY_NORTH, Double.NaN)
            val south = inputData.getDouble(KEY_SOUTH, Double.NaN)
            val east = inputData.getDouble(KEY_EAST, Double.NaN)
            val west = inputData.getDouble(KEY_WEST, Double.NaN)
            val minZoom = inputData.getInt(KEY_MIN_ZOOM, -1)
            val maxZoom = inputData.getInt(KEY_MAX_ZOOM, -1)

            if (hasInvalidInput(north, south, east, west, minZoom, maxZoom)) {
                return@withContext Result.failure()
            }

            try {
                processDownload(regionId, name, north, south, east, west, minZoom, maxZoom)
            } catch (e: Exception) {
                downloadedRegionDao.updateStatus(regionId, DownloadStatus.FAILED.name)
                Result.failure(
                    workDataOf(
                        KEY_REGION_ID to regionId,
                        KEY_ERROR to (e.message ?: "Unknown error"),
                    ),
                )
            }
        }

    private fun hasInvalidInput(
        north: Double,
        south: Double,
        east: Double,
        west: Double,
        minZoom: Int,
        maxZoom: Int,
    ): Boolean =
        north.isNaN() ||
            south.isNaN() ||
            east.isNaN() ||
            west.isNaN() ||
            minZoom < 0 ||
            maxZoom < 0

    @Suppress("LongParameterList")
    private suspend fun processDownload(
        regionId: String,
        name: String,
        north: Double,
        south: Double,
        east: Double,
        west: Double,
        minZoom: Int,
        maxZoom: Int,
    ): Result {
        val bounds = BoundingBox(north, south, east, west)
        val totalTiles = estimateTileCount(bounds, minZoom, maxZoom)

        val entity =
            DownloadedRegionEntity(
                regionId = regionId,
                name = name,
                north = north,
                south = south,
                east = east,
                west = west,
                minZoom = minZoom,
                maxZoom = maxZoom,
                totalTiles = totalTiles,
                downloadedTiles = 0,
                sizeBytes = 0,
                downloadedAt = System.currentTimeMillis(),
                status = DownloadStatus.DOWNLOADING.name,
            )
        downloadedRegionDao.insertOrUpdate(entity)

        val success =
            downloadTiles(
                regionId = regionId,
                bounds = bounds,
                minZoom = minZoom,
                maxZoom = maxZoom,
                totalTiles = totalTiles,
            )

        return if (success) {
            downloadedRegionDao.updateStatus(regionId, DownloadStatus.COMPLETED.name)
            Result.success(
                workDataOf(
                    KEY_REGION_ID to regionId,
                    KEY_STATUS to DownloadStatus.COMPLETED.name,
                ),
            )
        } else {
            downloadedRegionDao.updateStatus(regionId, DownloadStatus.FAILED.name)
            Result.failure(
                workDataOf(
                    KEY_REGION_ID to regionId,
                    KEY_STATUS to DownloadStatus.FAILED.name,
                ),
            )
        }
    }

    @Suppress("MagicNumber", "NestedBlockDepth", "LoopWithTooManyJumpStatements")
    private suspend fun downloadTiles(
        regionId: String,
        bounds: BoundingBox,
        minZoom: Int,
        maxZoom: Int,
        totalTiles: Long,
    ): Boolean {
        var downloadedCount = 0L
        var totalBytes = 0L

        for (zoom in minZoom..maxZoom) {
            val minTileX = lonToTileX(bounds.west, zoom)
            val maxTileX = lonToTileX(bounds.east, zoom)
            val minTileY = latToTileY(bounds.north, zoom)
            val maxTileY = latToTileY(bounds.south, zoom)

            for (x in minTileX..maxTileX) {
                for (y in minTileY..maxTileY) {
                    if (isStopped) {
                        downloadedRegionDao.updateStatus(regionId, DownloadStatus.PAUSED.name)
                        return false
                    }

                    val tileUrl = getTileUrl(zoom, x.toInt(), y.toInt())
                    val downloaded = downloadSingleTile(tileUrl)

                    if (downloaded > 0) {
                        downloadedCount++
                        totalBytes += downloaded

                        if (downloadedCount % PROGRESS_UPDATE_INTERVAL == 0L) {
                            updateDownloadProgress(regionId, downloadedCount, totalBytes, totalTiles)
                        }
                    }
                }
            }
        }

        downloadedRegionDao.updateProgress(
            regionId = regionId,
            downloadedTiles = downloadedCount,
            sizeBytes = totalBytes,
            status = DownloadStatus.COMPLETED.name,
        )

        return true
    }

    private suspend fun updateDownloadProgress(
        regionId: String,
        downloadedCount: Long,
        totalBytes: Long,
        totalTiles: Long,
    ) {
        downloadedRegionDao.updateProgress(
            regionId = regionId,
            downloadedTiles = downloadedCount,
            sizeBytes = totalBytes,
            status = DownloadStatus.DOWNLOADING.name,
        )
        setProgress(
            workDataOf(
                KEY_PROGRESS to (downloadedCount * PROGRESS_MULTIPLIER / totalTiles).toInt(),
                KEY_DOWNLOADED_TILES to downloadedCount,
                KEY_TOTAL_TILES to totalTiles,
            ),
        )
    }

    @Suppress("MagicNumber", "SwallowedException", "TooGenericExceptionCaught")
    private fun downloadSingleTile(tileUrl: String): Long =
        try {
            val connection = URL(tileUrl).openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", applicationContext.packageName)
            connection.connectTimeout = CONNECTION_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.connect()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val bytes = connection.inputStream.readBytes()
                saveTileToCache(tileUrl, bytes)
                bytes.size.toLong()
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }

    private fun saveTileToCache(
        tileUrl: String,
        bytes: ByteArray,
    ) {
        val cacheDir = Configuration.getInstance().osmdroidTileCache
        val fileName = tileUrl.hashCode().toString() + ".tile"
        val cacheFile = File(cacheDir, fileName)
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeBytes(bytes)
    }

    @Suppress("MagicNumber")
    private fun getTileUrl(
        zoom: Int,
        x: Int,
        y: Int,
    ): String {
        val servers = arrayOf("a", "b", "c")
        val server = servers[(x + y) % servers.size]
        return "https://$server.tile.opentopomap.org/$zoom/$x/$y.png"
    }

    @Suppress("MagicNumber")
    private fun estimateTileCount(
        bounds: BoundingBox,
        minZoom: Int,
        maxZoom: Int,
    ): Long {
        var total = 0L
        for (zoom in minZoom..maxZoom) {
            val minTileX = lonToTileX(bounds.west, zoom)
            val maxTileX = lonToTileX(bounds.east, zoom)
            val minTileY = latToTileY(bounds.north, zoom)
            val maxTileY = latToTileY(bounds.south, zoom)
            total += (maxTileX - minTileX + 1) * (maxTileY - minTileY + 1)
        }
        return total
    }

    @Suppress("MagicNumber")
    private fun lonToTileX(
        lon: Double,
        zoom: Int,
    ): Long {
        val n = 1L shl zoom
        return ((lon + 180.0) / 360.0 * n).toLong().coerceIn(0, n - 1)
    }

    @Suppress("MagicNumber")
    private fun latToTileY(
        lat: Double,
        zoom: Int,
    ): Long {
        val n = 1L shl zoom
        val latRad = Math.toRadians(lat)
        val y = (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n
        return y.toLong().coerceIn(0, n - 1)
    }

    companion object {
        const val KEY_REGION_ID = "region_id"
        const val KEY_NAME = "name"
        const val KEY_NORTH = "north"
        const val KEY_SOUTH = "south"
        const val KEY_EAST = "east"
        const val KEY_WEST = "west"
        const val KEY_MIN_ZOOM = "min_zoom"
        const val KEY_MAX_ZOOM = "max_zoom"
        const val KEY_PROGRESS = "progress"
        const val KEY_DOWNLOADED_TILES = "downloaded_tiles"
        const val KEY_TOTAL_TILES = "total_tiles"
        const val KEY_STATUS = "status"
        const val KEY_ERROR = "error"

        private const val PROGRESS_UPDATE_INTERVAL = 50L
        private const val PROGRESS_MULTIPLIER = 100
        private const val CONNECTION_TIMEOUT = 10_000
        private const val READ_TIMEOUT = 10_000
    }
}
