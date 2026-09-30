package com.sunshine.app.offline

import com.sunshine.core.TileKey
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class RegionDeleterTest {
    @TempDir
    lateinit var directory: File

    private val database = inMemoryOfflineDatabase()
    private val dao = database.dao()
    private val deletedMaps = mutableListOf<Long>()
    private val cancelled = mutableListOf<Long>()
    private val limits = mutableListOf<Pair<Long, Boolean>>()

    @AfterEach
    fun close() = database.close()

    private fun store() = DemTileStore(dao, directory, browsedLimitBytes = 1000)

    private fun deleter(store: DemTileStore) =
        RegionDeleter(
            dao,
            store,
            deleteMap = { deletedMaps += it },
            cancelDownload = { cancelled += it },
            onRegionBytes = { bytes, now -> limits += bytes to now },
        )

    private suspend fun region(mapBytes: Long) =
        dao.insertRegion(
            RegionRow(
                centreLat = 46.6,
                centreLon = 7.9,
                south = 46.5,
                west = 7.8,
                north = 46.7,
                east = 8.0,
                createdAt = 1,
                mapRegionId = 40 + mapBytes,
                mapBytes = mapBytes,
            ),
        )

    @Test
    fun `deleting a region stops its download, deletes its map region and keeps the tiles other regions use`() =
        runTest {
            val store = store()
            val a = region(mapBytes = 100)
            val b = region(mapBytes = 200)
            store.putFound(SHARED, BYTES, VALIDATORS, region = a)
            store.claim(SHARED, b)
            store.putFound(ONLY_A, BYTES, VALIDATORS, region = a)

            deleter(store).delete(a)

            assertEquals(listOf(a), cancelled)
            assertEquals(listOf(140L), deletedMaps)
            assertNull(dao.region(a))
            assertEquals(RegionState.QUEUED, dao.region(b)!!.state)
            assertArrayEquals(BYTES, store.get(SHARED)!!.bytes) // still b's
            assertEquals(BYTES.size.toLong(), store.browsedBytes()) // ONLY_A is browsed now
            assertEquals(200L to true, limits.last())
        }

    @Test
    fun `a deletion interrupted by the end of the process is finished at the next start`() =
        runTest {
            val store = store()
            val a = region(mapBytes = 100)
            store.putFound(ONLY_A, BYTES, VALIDATORS, region = a)
            dao.markDeleted(a) // the process ended here

            deleter(store).finishPending()

            assertEquals(listOf(140L), deletedMaps)
            assertNull(dao.region(a))
            assertEquals(BYTES.size.toLong(), store.browsedBytes())
        }

    @Test
    fun `claims of a region that no longer exists are dropped at start`() =
        runTest {
            val store = store()
            store.putFound(ONLY_A, BYTES, VALIDATORS, region = 99) // region 99 has no row

            deleter(store).finishPending()

            assertEquals(BYTES.size.toLong(), store.browsedBytes())
        }

    private companion object {
        val SHARED = TileKey(12, 1, 1)
        val ONLY_A = TileKey(12, 2, 1)
        val BYTES = byteArrayOf(1, 2, 3, 4)
        val VALIDATORS = Validators(etag = null, lastModified = null, freshUntil = 0)
    }
}
