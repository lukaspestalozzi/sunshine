package com.sunshine.app.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
import com.sunshine.app.data.download.TileDownloadWorker
import com.sunshine.app.data.local.database.DownloadedRegionDao
import com.sunshine.app.data.local.database.entities.DownloadStatus
import com.sunshine.app.domain.model.DownloadableRegion
import com.sunshine.app.domain.repository.DownloadProgress
import com.sunshine.app.domain.repository.DownloadState
import com.sunshine.app.domain.repository.TileDownloadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Implementation of TileDownloadRepository using WorkManager for background downloads.
 */
class TileDownloadRepositoryImpl(
    context: Context,
    private val downloadedRegionDao: DownloadedRegionDao,
) : TileDownloadRepository {
    private val workManager = androidx.work.WorkManager.getInstance(context)

    override fun startDownload(region: DownloadableRegion) {
        val downloadRequest = createDownloadRequest(region)
        workManager.enqueueUniqueWork(
            getWorkName(region.id),
            ExistingWorkPolicy.REPLACE,
            downloadRequest,
        )
    }

    private fun createDownloadRequest(region: DownloadableRegion) =
        OneTimeWorkRequestBuilder<TileDownloadWorker>()
            .setInputData(createWorkData(region))
            .setConstraints(createDownloadConstraints())
            .addTag(DOWNLOAD_WORK_TAG)
            .addTag(region.id)
            .build()

    private fun createWorkData(region: DownloadableRegion) =
        workDataOf(
            TileDownloadWorker.KEY_REGION_ID to region.id,
            TileDownloadWorker.KEY_NAME to region.name,
            TileDownloadWorker.KEY_NORTH to region.bounds.north,
            TileDownloadWorker.KEY_SOUTH to region.bounds.south,
            TileDownloadWorker.KEY_EAST to region.bounds.east,
            TileDownloadWorker.KEY_WEST to region.bounds.west,
            TileDownloadWorker.KEY_MIN_ZOOM to region.minZoom,
            TileDownloadWorker.KEY_MAX_ZOOM to region.maxZoom,
        )

    private fun createDownloadConstraints() =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresStorageNotLow(true)
            .build()

    override fun cancelDownload(regionId: String) {
        workManager.cancelUniqueWork(getWorkName(regionId))
    }

    override fun getDownloadProgress(): Flow<List<DownloadProgress>> =
        downloadedRegionDao.getAllDownloadedRegions().map { entities ->
            entities.map { entity ->
                DownloadProgress(
                    regionId = entity.regionId,
                    regionName = entity.name,
                    status = mapStatus(entity.status),
                    progress = calculateProgress(entity.downloadedTiles, entity.totalTiles),
                    downloadedTiles = entity.downloadedTiles,
                    totalTiles = entity.totalTiles,
                    sizeBytes = entity.sizeBytes,
                )
            }
        }

    override fun getDownloadProgress(regionId: String): Flow<DownloadProgress?> =
        downloadedRegionDao.getDownloadedRegionFlow(regionId).map { entity ->
            entity?.let {
                DownloadProgress(
                    regionId = it.regionId,
                    regionName = it.name,
                    status = mapStatus(it.status),
                    progress = calculateProgress(it.downloadedTiles, it.totalTiles),
                    downloadedTiles = it.downloadedTiles,
                    totalTiles = it.totalTiles,
                    sizeBytes = it.sizeBytes,
                )
            }
        }

    override suspend fun deleteDownload(regionId: String) {
        cancelDownload(regionId)
        downloadedRegionDao.deleteRegion(regionId)
    }

    override suspend fun isRegionDownloaded(regionId: String): Boolean {
        val region = downloadedRegionDao.getDownloadedRegion(regionId)
        return region?.status == DownloadStatus.COMPLETED.name
    }

    override fun getTotalStorageUsed(): Flow<Long> = downloadedRegionDao.getTotalStorageUsed()

    private fun getWorkName(regionId: String): String = "download_$regionId"

    @Suppress("MagicNumber")
    private fun calculateProgress(
        downloaded: Long,
        total: Long,
    ): Int =
        if (total > 0) {
            (downloaded * PROGRESS_MULTIPLIER / total).toInt().coerceIn(0, PROGRESS_MULTIPLIER)
        } else {
            0
        }

    private fun mapStatus(status: String): DownloadState =
        when (status) {
            DownloadStatus.PENDING.name -> DownloadState.PENDING
            DownloadStatus.DOWNLOADING.name -> DownloadState.DOWNLOADING
            DownloadStatus.COMPLETED.name -> DownloadState.COMPLETED
            DownloadStatus.FAILED.name -> DownloadState.FAILED
            DownloadStatus.PAUSED.name -> DownloadState.PAUSED
            else -> DownloadState.PENDING
        }

    companion object {
        private const val DOWNLOAD_WORK_TAG = "tile_download"
        private const val PROGRESS_MULTIPLIER = 100
    }
}
