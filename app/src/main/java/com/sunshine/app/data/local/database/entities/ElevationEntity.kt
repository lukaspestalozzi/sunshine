package com.sunshine.app.data.local.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cached elevation point in the database.
 * Grid cells are indexed by their center coordinates truncated to ~30m resolution.
 */
@Entity(
    tableName = "elevation_cache",
    indices = [Index(value = ["gridLat", "gridLon"], unique = true)],
)
data class ElevationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    // Grid-aligned coordinates (truncated to resolution)
    val gridLat: Double,
    val gridLon: Double,
    // Actual coordinates queried
    val latitude: Double,
    val longitude: Double,
    // Elevation in meters
    val elevation: Double,
    // Data source identifier
    val source: String,
    // Timestamp when fetched
    val fetchedAt: Long,
)
