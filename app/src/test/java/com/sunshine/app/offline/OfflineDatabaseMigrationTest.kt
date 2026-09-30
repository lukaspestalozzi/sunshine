package com.sunshine.app.offline

import android.content.ContextWrapper
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class OfflineDatabaseMigrationTest {
    @TempDir
    lateinit var directory: File

    @Test
    fun `a version 1 database keeps its rows and gets the claims' index`() =
        runTest {
            val file = File(directory, "offline.db")
            // The schema of version 1, as Room generated it.
            BundledSQLiteDriver().open(file.path).use { connection ->
                VERSION_1.forEach { connection.execSQL(it) }
                connection.execSQL("INSERT INTO region_dem_tile VALUES (7, 12, 1, 1)")
                connection.execSQL("PRAGMA user_version = 1")
            }
            val context =
                object : ContextWrapper(null) {
                    override fun getDatabasePath(name: String) = File(name)
                }

            val database =
                Room
                    .databaseBuilder(context, OfflineDatabase::class.java, file.path)
                    .setDriver(BundledSQLiteDriver())
                    .setJournalMode(OfflineDatabase.JOURNAL_MODE)
                    .addMigrations(OfflineDatabase.ADD_CLAIM_INDEX)
                    .build()

            database.dao().deleteClaims(7) // opens and validates the migrated schema
            assertEquals(0, database.dao().browsedBytes())
            database.close()
        }

    private companion object {
        val VERSION_1 =
            listOf(
                "CREATE TABLE IF NOT EXISTS `dem_tile` (`z` INTEGER NOT NULL, `x` INTEGER NOT NULL, `y` INTEGER NOT NULL, " +
                    "`found` INTEGER NOT NULL, `bytes` INTEGER NOT NULL, `etag` TEXT, `lastModified` TEXT, " +
                    "`freshUntil` INTEGER NOT NULL, `lastUsed` INTEGER NOT NULL, PRIMARY KEY(`z`, `x`, `y`))",
                "CREATE TABLE IF NOT EXISTS `region_dem_tile` (`regionId` INTEGER NOT NULL, `z` INTEGER NOT NULL, " +
                    "`x` INTEGER NOT NULL, `y` INTEGER NOT NULL, PRIMARY KEY(`regionId`, `z`, `x`, `y`))",
                "CREATE TABLE IF NOT EXISTS `region` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `centreLat` REAL NOT NULL, " +
                    "`centreLon` REAL NOT NULL, `south` REAL NOT NULL, `west` REAL NOT NULL, `north` REAL NOT NULL, " +
                    "`east` REAL NOT NULL, `createdAt` INTEGER NOT NULL, `completedAt` INTEGER, `mapRegionId` INTEGER, " +
                    "`mapBytes` INTEGER NOT NULL, `progress` INTEGER NOT NULL, `state` TEXT NOT NULL)",
                "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '0bb1497fb4042ee2d89946b5b2ae8e91')",
            )
    }
}
