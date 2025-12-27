package com.sunshine.app.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sunshine.app.data.local.database.entities.DownloadedRegionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for downloaded regions.
 */
@Dao
interface DownloadedRegionDao {
    /**
     * Get all downloaded regions as a Flow for reactive updates.
     */
    @Query("SELECT * FROM downloaded_regions ORDER BY downloadedAt DESC")
    fun getAllDownloadedRegions(): Flow<List<DownloadedRegionEntity>>

    /**
     * Get a specific downloaded region by ID.
     */
    @Query("SELECT * FROM downloaded_regions WHERE regionId = :regionId")
    suspend fun getDownloadedRegion(regionId: String): DownloadedRegionEntity?

    /**
     * Get a specific downloaded region by ID as a Flow.
     */
    @Query("SELECT * FROM downloaded_regions WHERE regionId = :regionId")
    fun getDownloadedRegionFlow(regionId: String): Flow<DownloadedRegionEntity?>

    /**
     * Check if a region has been downloaded.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_regions WHERE regionId = :regionId)")
    suspend fun isRegionDownloaded(regionId: String): Boolean

    /**
     * Get all completed downloads.
     */
    @Query("SELECT * FROM downloaded_regions WHERE status = 'COMPLETED'")
    fun getCompletedDownloads(): Flow<List<DownloadedRegionEntity>>

    /**
     * Insert or replace a downloaded region.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(region: DownloadedRegionEntity)

    /**
     * Update download progress.
     */
    @Query(
        """
        UPDATE downloaded_regions
        SET downloadedTiles = :downloadedTiles, sizeBytes = :sizeBytes, status = :status
        WHERE regionId = :regionId
        """,
    )
    suspend fun updateProgress(
        regionId: String,
        downloadedTiles: Long,
        sizeBytes: Long,
        status: String,
    )

    /**
     * Update download status.
     */
    @Query("UPDATE downloaded_regions SET status = :status WHERE regionId = :regionId")
    suspend fun updateStatus(
        regionId: String,
        status: String,
    )

    /**
     * Delete a downloaded region record.
     */
    @Query("DELETE FROM downloaded_regions WHERE regionId = :regionId")
    suspend fun deleteRegion(regionId: String)

    /**
     * Get total storage used by all downloaded regions.
     */
    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM downloaded_regions WHERE status = 'COMPLETED'")
    fun getTotalStorageUsed(): Flow<Long>
}
