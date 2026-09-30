package com.sunshine.app.offline

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction

/** A stored Mapterhorn tile (design D5 of add-offline-regions): [found] false records a 404. */
@Entity(tableName = "dem_tile", primaryKeys = ["z", "x", "y"])
data class DemTileRow(
    val z: Int,
    val x: Int,
    val y: Int,
    val found: Boolean,
    val bytes: Long,
    val etag: String?,
    val lastModified: String?,
    val freshUntil: Long,
    val lastUsed: Long,
)

/** A region's claim on a DEM tile; a tile without any claim is browsed. */
@Entity(tableName = "region_dem_tile", primaryKeys = ["regionId", "z", "x", "y"])
data class RegionDemTileRow(
    val regionId: Long,
    val z: Int,
    val x: Int,
    val y: Int,
)

enum class RegionState { QUEUED, COMPLETE, DELETED }

/** An offline region: the visible area when it was downloaded (design D5, D7). */
@Entity(tableName = "region")
data class RegionRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val centreLat: Double,
    val centreLon: Double,
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
    val createdAt: Long,
    val completedAt: Long? = null,
    val mapRegionId: Long? = null,
    val mapBytes: Long = 0,
    val progress: Int = 0,
    val state: RegionState = RegionState.QUEUED,
)

@Dao
abstract class OfflineDao {
    @Query("SELECT * FROM dem_tile WHERE z = :z AND x = :x AND y = :y")
    abstract suspend fun tile(
        z: Int,
        x: Int,
        y: Int,
    ): DemTileRow?

    // Replaces a stored row; claims live in their own table, so they stay.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertTile(row: DemTileRow)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertClaim(claim: RegionDemTileRow)

    /** Stores [row] and, with [claim], the region's claim on it, together. */
    @Transaction
    open suspend fun upsertTile(
        row: DemTileRow,
        claim: RegionDemTileRow?,
    ) {
        upsertTile(row)
        if (claim != null) insertClaim(claim)
    }

    @Query("DELETE FROM region_dem_tile WHERE regionId = :regionId")
    abstract suspend fun deleteClaims(regionId: Long)

    @Query(
        "UPDATE dem_tile SET etag = :etag, lastModified = :lastModified, freshUntil = :freshUntil " +
            "WHERE z = :z AND x = :x AND y = :y",
    )
    abstract suspend fun updateValidators(
        z: Int,
        x: Int,
        y: Int,
        etag: String?,
        lastModified: String?,
        freshUntil: Long,
    )

    @Query("UPDATE dem_tile SET lastUsed = :lastUsed WHERE z = :z AND x = :x AND y = :y")
    abstract suspend fun updateLastUsed(
        z: Int,
        x: Int,
        y: Int,
        lastUsed: Long,
    )

    @Query("SELECT COALESCE(SUM(bytes), 0) FROM dem_tile WHERE $NOT_CLAIMED")
    abstract suspend fun browsedBytes(): Long

    @Query("SELECT COALESCE(SUM(bytes), 0) FROM dem_tile")
    abstract suspend fun totalBytes(): Long

    /** The least recently used browsed tiles that take space. */
    @Query("SELECT * FROM dem_tile WHERE bytes > 0 AND $NOT_CLAIMED ORDER BY lastUsed LIMIT :limit")
    abstract suspend fun oldestBrowsed(limit: Int): List<DemTileRow>

    /** Deletes the tile unless a region claims it; 1 if it was deleted. */
    @Query("DELETE FROM dem_tile WHERE z = :z AND x = :x AND y = :y AND $NOT_CLAIMED")
    abstract suspend fun deleteBrowsed(
        z: Int,
        x: Int,
        y: Int,
    ): Int

    @Query("DELETE FROM dem_tile WHERE z = :z AND x = :x AND y = :y")
    abstract suspend fun deleteTile(
        z: Int,
        x: Int,
        y: Int,
    )

    @Query("SELECT * FROM dem_tile WHERE found")
    abstract suspend fun foundTiles(): List<DemTileRow>

    private companion object {
        const val NOT_CLAIMED =
            "NOT EXISTS (SELECT 1 FROM region_dem_tile c WHERE c.z = dem_tile.z AND c.x = dem_tile.x AND c.y = dem_tile.y)"
    }
}

@Database(entities = [DemTileRow::class, RegionDemTileRow::class, RegionRow::class], version = 1, exportSchema = false)
abstract class OfflineDatabase : RoomDatabase() {
    abstract fun dao(): OfflineDao

    companion object {
        /** Explicit, because `AUTOMATIC` asks the Context, which JVM tests do not have (design D5). */
        val JOURNAL_MODE = JournalMode.WRITE_AHEAD_LOGGING

        fun create(context: Context): OfflineDatabase =
            Room
                .databaseBuilder(context, OfflineDatabase::class.java, "offline.db")
                .setJournalMode(JOURNAL_MODE)
                .build()
    }
}
