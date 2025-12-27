package com.sunshine.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sunshine.app.data.local.database.entities.ElevationEntity

/**
 * Room database for Sunshine app.
 */
@Database(
    entities = [ElevationEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class SunshineDatabase : RoomDatabase() {
    abstract fun elevationDao(): ElevationDao

    companion object {
        const val DATABASE_NAME = "sunshine_db"
    }
}
