package com.sunshine.app.offline

import android.content.ContextWrapper
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RoomSmokeTest {
    @Test
    fun `inserts and reads a row on the JVM`() =
        runTest {
            val database =
                Room
                    .inMemoryDatabaseBuilder(ContextWrapper(null), SmokeDatabase::class.java)
                    .setDriver(BundledSQLiteDriver())
                    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                    .build()

            database.dao().insert(SmokeRow(1, "Interlaken"))

            assertEquals(SmokeRow(1, "Interlaken"), database.dao().get(1))
            database.close()
        }
}
