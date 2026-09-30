package com.sunshine.app.offline

import android.content.ContextWrapper
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * An in-memory [OfflineDatabase] on the plain JVM (design D5 of add-offline-regions): the stub
 * Context is never used once the driver and the journal mode are set. With [queries] set to a test
 * dispatcher, queries run in virtual time instead of on Room's own threads.
 */
fun inMemoryOfflineDatabase(queries: CoroutineContext = EmptyCoroutineContext): OfflineDatabase =
    Room
        .inMemoryDatabaseBuilder(ContextWrapper(null), OfflineDatabase::class.java)
        .setDriver(BundledSQLiteDriver())
        .setJournalMode(OfflineDatabase.JOURNAL_MODE)
        .apply { if (queries != EmptyCoroutineContext) setQueryCoroutineContext(queries) }
        .build()
