package com.sunshine.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sunshine.app.data.local.database.entities.DownloadedRegionEntity
import com.sunshine.app.data.local.database.entities.ElevationEntity

/**
 * Room database for Sunshine app.
 */
@Database(
    entities = [
        ElevationEntity::class,
        DownloadedRegionEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class SunshineDatabase : RoomDatabase() {
    abstract fun elevationDao(): ElevationDao

    abstract fun downloadedRegionDao(): DownloadedRegionDao

    companion object {
        const val DATABASE_NAME = "sunshine_db"
    }
}
