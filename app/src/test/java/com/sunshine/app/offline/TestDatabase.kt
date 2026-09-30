package com.sunshine.app.offline

import android.content.ContextWrapper
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/**
 * An in-memory [OfflineDatabase] on the plain JVM (design D5 of add-offline-regions): the stub
 * Context is never used once the driver and the journal mode are set.
 */
fun inMemoryOfflineDatabase(): OfflineDatabase =
    Room
        .inMemoryDatabaseBuilder(ContextWrapper(null), OfflineDatabase::class.java)
        .setDriver(BundledSQLiteDriver())
        .setJournalMode(OfflineDatabase.JOURNAL_MODE)
        .build()
