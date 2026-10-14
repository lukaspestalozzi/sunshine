package com.sunshine.app.search

import android.content.ContextWrapper
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * An in-memory [PlacesDatabase] on the plain JVM, as `inMemoryOfflineDatabase`: the stub Context is
 * never used once the driver and the journal mode are set. With [queries] set to a test dispatcher,
 * queries run in virtual time.
 */
fun inMemoryPlacesDatabase(queries: CoroutineContext = EmptyCoroutineContext): PlacesDatabase =
    Room
        .inMemoryDatabaseBuilder(ContextWrapper(null), PlacesDatabase::class.java)
        .setDriver(BundledSQLiteDriver())
        .setJournalMode(PlacesDatabase.JOURNAL_MODE)
        .apply { if (queries != EmptyCoroutineContext) setQueryCoroutineContext(queries) }
        .build()
