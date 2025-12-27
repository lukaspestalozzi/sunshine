package com.sunshine.app.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sunshine.app.data.local.database.entities.ElevationEntity

/**
 * Data Access Object for elevation cache.
 */
@Dao
interface ElevationDao {
    /**
     * Get elevation for a grid cell.
     */
    @Query("SELECT * FROM elevation_cache WHERE gridLat = :gridLat AND gridLon = :gridLon LIMIT 1")
    suspend fun getElevation(
        gridLat: Double,
        gridLon: Double,
    ): ElevationEntity?

    /**
     * Get all elevations within a bounding box.
     */
    @Query(
        """
        SELECT * FROM elevation_cache
        WHERE gridLat BETWEEN :south AND :north
        AND gridLon BETWEEN :west AND :east
        """,
    )
    suspend fun getElevationsInBounds(
        north: Double,
        south: Double,
        east: Double,
        west: Double,
    ): List<ElevationEntity>

    /**
     * Insert or update elevation data.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ElevationEntity>)

    /**
     * Insert a single elevation point.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ElevationEntity)

    /**
     * Count cached elevations in a region.
     */
    @Query(
        """
        SELECT COUNT(*) FROM elevation_cache
        WHERE gridLat BETWEEN :south AND :north
        AND gridLon BETWEEN :west AND :east
        """,
    )
    suspend fun countInBounds(
        north: Double,
        south: Double,
        east: Double,
        west: Double,
    ): Int

    /**
     * Delete old cache entries.
     */
    @Query("DELETE FROM elevation_cache WHERE fetchedAt < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long)
}
