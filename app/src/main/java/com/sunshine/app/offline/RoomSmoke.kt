package com.sunshine.app.offline

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

// Throwaway for task 1.1 of add-offline-regions: checks that Room builds and runs on the JVM.
@Entity
data class SmokeRow(
    @PrimaryKey val id: Long,
    val name: String,
)

@Dao
interface SmokeDao {
    @Insert
    suspend fun insert(row: SmokeRow)

    @Query("SELECT * FROM SmokeRow WHERE id = :id")
    suspend fun get(id: Long): SmokeRow?
}

@Database(entities = [SmokeRow::class], version = 1, exportSchema = false)
abstract class SmokeDatabase : RoomDatabase() {
    abstract fun dao(): SmokeDao
}
