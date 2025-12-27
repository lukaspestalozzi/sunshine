package com.sunshine.app.data.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sunshine.app.data.local.database.DownloadedRegionDao
import com.sunshine.app.data.local.database.entities.DownloadStatus
import com.sunshine.app.data.local.database.entities.DownloadedRegionEntity
import com.sunshine.app.domain.model.BoundingBox
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.osmdroid.config.Configuration

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
            val params = extractInputParams() ?: return@withContext Result.failure()
            executeDownload(params)
        }

    private fun extractInputParams(): DownloadParams? {
        val regionId = inputData.getString(KEY_REGION_ID)
        val name = inputData.getString(KEY_NAME)
        val north = inputData.getDouble(KEY_NORTH, Double.NaN)
        val south = inputData.getDouble(KEY_SOUTH, Double.NaN)
        val east = inputData.getDouble(KEY_EAST, Double.NaN)
        val west = inputData.getDouble(KEY_WEST, Double.NaN)
        val minZoom = inputData.getInt(KEY_MIN_ZOOM, -1)
        val maxZoom = inputData.getInt(KEY_MAX_ZOOM, -1)

        val hasValidStrings = regionId != null && name != null
        val hasValidCoordinates = !hasInvalidCoordinates(north, south, east, west)
        val hasValidZoom = minZoom >= 0 && maxZoom >= 0

        return if (hasValidStrings && hasValidCoordinates && hasValidZoom) {
            DownloadParams(regionId!!, name!!, north, south, east, west, minZoom, maxZoom)
        } else {
            null
        }
    }

    private fun hasInvalidCoordinates(
        north: Double,
        south: Double,
        east: Double,
        west: Double,
    ): Boolean = north.isNaN() || south.isNaN() || east.isNaN() || west.isNaN()

    private suspend fun executeDownload(params: DownloadParams): Result =
        try {
            processDownload(params)
        } catch (e: IOException) {
            handleDownloadFailure(params.regionId, e.message)
        }

    private fun handleDownloadFailure(
        regionId: String,
        errorMessage: String?,
    ): Result {
        downloadedRegionDao.updateStatus(regionId, DownloadStatus.FAILED.name)
        return Result.failure(
            workDataOf(
                KEY_REGION_ID to regionId,
                KEY_ERROR to (errorMessage ?: "Unknown error"),
            ),
        )
    }

    private suspend fun processDownload(params: DownloadParams): Result {
        val bounds = BoundingBox(params.north, params.south, params.east, params.west)
        val totalTiles = estimateTileCount(bounds, params.minZoom, params.maxZoom)

        initializeDownloadRecord(params, totalTiles)

        val success = downloadAllTiles(params.regionId, bounds, params.minZoom, params.maxZoom, totalTiles)
        return createDownloadResult(params.regionId, success)
    }

    private fun initializeDownloadRecord(
        params: DownloadParams,
        totalTiles: Long,
    ) {
        val entity =
            DownloadedRegionEntity(
                regionId = params.regionId,
                name = params.name,
                north = params.north,
                south = params.south,
                east = params.east,
                west = params.west,
                minZoom = params.minZoom,
                maxZoom = params.maxZoom,
                totalTiles = totalTiles,
                downloadedTiles = 0,
                sizeBytes = 0,
                downloadedAt = System.currentTimeMillis(),
                status = DownloadStatus.DOWNLOADING.name,
            )
        downloadedRegionDao.insertOrUpdate(entity)
    }

    private fun createDownloadResult(
        regionId: String,
        success: Boolean,
    ): Result {
        val status = if (success) DownloadStatus.COMPLETED else DownloadStatus.FAILED
        downloadedRegionDao.updateStatus(regionId, status.name)
        return if (success) {
            Result.success(workDataOf(KEY_REGION_ID to regionId, KEY_STATUS to status.name))
        } else {
            Result.failure(workDataOf(KEY_REGION_ID to regionId, KEY_STATUS to status.name))
        }
    }

    @Suppress("NestedBlockDepth", "LoopWithTooManyJumpStatements")
    private suspend fun downloadAllTiles(
        regionId: String,
        bounds: BoundingBox,
        minZoom: Int,
        maxZoom: Int,
        totalTiles: Long,
    ): Boolean {
        var downloadedCount = 0L
        var totalBytes = 0L

        for (zoom in minZoom..maxZoom) {
            val result = downloadZoomLevel(regionId, bounds, zoom, downloadedCount, totalBytes, totalTiles)
            if (result == null) return false
            downloadedCount = result.first
            totalBytes = result.second
        }

        finalizeDownloadProgress(regionId, downloadedCount, totalBytes)
        return true
    }

    @Suppress("NestedBlockDepth", "LoopWithTooManyJumpStatements")
    private suspend fun downloadZoomLevel(
        regionId: String,
        bounds: BoundingBox,
        zoom: Int,
        startCount: Long,
        startBytes: Long,
        totalTiles: Long,
    ): Pair<Long, Long>? {
        var downloadedCount = startCount
        var totalBytes = startBytes
        val tileRange = calculateTileRange(bounds, zoom)

        for (x in tileRange.minX..tileRange.maxX) {
            for (y in tileRange.minY..tileRange.maxY) {
                if (isStopped) {
                    downloadedRegionDao.updateStatus(regionId, DownloadStatus.PAUSED.name)
                    return null
                }
                val downloaded = downloadSingleTile(getTileUrl(zoom, x.toInt(), y.toInt()))
                if (downloaded > 0) {
                    downloadedCount++
                    totalBytes += downloaded
                    if (downloadedCount % PROGRESS_UPDATE_INTERVAL == 0L) {
                        updateDownloadProgress(regionId, downloadedCount, totalBytes, totalTiles)
                    }
                }
            }
        }
        return Pair(downloadedCount, totalBytes)
    }

    private fun calculateTileRange(
        bounds: BoundingBox,
        zoom: Int,
    ) = TileRange(
        minX = lonToTileX(bounds.west, zoom),
        maxX = lonToTileX(bounds.east, zoom),
        minY = latToTileY(bounds.north, zoom),
        maxY = latToTileY(bounds.south, zoom),
    )

    private fun finalizeDownloadProgress(
        regionId: String,
        downloadedCount: Long,
        totalBytes: Long,
    ) {
        downloadedRegionDao.updateProgress(
            regionId = regionId,
            downloadedTiles = downloadedCount,
            sizeBytes = totalBytes,
            status = DownloadStatus.COMPLETED.name,
        )
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

    @Suppress("SwallowedException")
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
        } catch (e: IOException) {
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
            val range = calculateTileRange(bounds, zoom)
            total += (range.maxX - range.minX + 1) * (range.maxY - range.minY + 1)
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

    private data class DownloadParams(
        val regionId: String,
        val name: String,
        val north: Double,
        val south: Double,
        val east: Double,
        val west: Double,
        val minZoom: Int,
        val maxZoom: Int,
    )

    private data class TileRange(
        val minX: Long,
        val maxX: Long,
        val minY: Long,
        val maxY: Long,
    )

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
