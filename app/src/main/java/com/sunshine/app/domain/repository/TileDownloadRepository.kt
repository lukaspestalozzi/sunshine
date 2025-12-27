package com.sunshine.app.domain.repository

import com.sunshine.app.domain.model.DownloadableRegion
import kotlinx.coroutines.flow.Flow

/**
 * Represents the current state of a download operation.
 */
data class DownloadProgress(
    val regionId: String,
    val regionName: String,
    val status: DownloadState,
    val progress: Int,
    val downloadedTiles: Long,
    val totalTiles: Long,
    val sizeBytes: Long,
)

/**
 * Possible states for a download operation.
 */
enum class DownloadState {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    PAUSED,
}

/**
 * Repository for managing tile downloads.
 */
interface TileDownloadRepository {
    /**
     * Start downloading a region.
     */
    fun startDownload(region: DownloadableRegion)

    /**
     * Cancel an ongoing download.
     */
    fun cancelDownload(regionId: String)

    /**
     * Get the download progress for all regions.
     */
    fun getDownloadProgress(): Flow<List<DownloadProgress>>

    /**
     * Get download progress for a specific region.
     */
    fun getDownloadProgress(regionId: String): Flow<DownloadProgress?>

    /**
     * Delete downloaded tiles for a region.
     */
    suspend fun deleteDownload(regionId: String)

    /**
     * Check if a region is downloaded (completed).
     */
    suspend fun isRegionDownloaded(regionId: String): Boolean

    /**
     * Get total storage used by downloads.
     */
    fun getTotalStorageUsed(): Flow<Long>
}
