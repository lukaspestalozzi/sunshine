package com.sunshine.app.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a downloaded region stored in the database.
 */
@Entity(tableName = "downloaded_regions")
data class DownloadedRegionEntity(
    @PrimaryKey
    val regionId: String,
    val name: String,
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double,
    val minZoom: Int,
    val maxZoom: Int,
    val totalTiles: Long,
    val downloadedTiles: Long,
    val sizeBytes: Long,
    val downloadedAt: Long,
    val status: String,
)

/**
 * Download status for a region.
 */
enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    PAUSED,
}
