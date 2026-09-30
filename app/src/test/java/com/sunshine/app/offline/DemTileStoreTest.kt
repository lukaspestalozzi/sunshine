package com.sunshine.app.offline

import com.sunshine.core.TileKey
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class DemTileStoreTest {
    @TempDir
    lateinit var directory: File

    private val database = inMemoryOfflineDatabase()
    private var now = 0L

    @AfterEach
    fun close() = database.close()

    // A limit of 10 bytes and tiles of 4 bytes keep the tests small (task 4.1).
    private fun store() = DemTileStore(database.dao(), directory, browsedLimitBytes = 10, now = { now })

    @Test
    fun `a stored tile is read back byte-identical, and a 404 is stored as missing`() =
        runTest {
            val store = store()
            store.putFound(A, BYTES, validators(), region = null)
            store.putMissing(B, validators(), region = null)

            val found = store.get(A)!!
            assertTrue(found.isFound)
            assertArrayEquals(BYTES, found.bytes)
            assertFalse(store.get(B)!!.isFound)
            assertNull(store.get(C))
        }

    @Test
    fun `browsed tiles above the limit are evicted least recently used first`() =
        runTest {
            val store = store()
            at(1) { store.putFound(A, BYTES, validators(), region = null) }
            at(2) { store.putFound(B, BYTES, validators(), region = null) }
            at(3) { store.get(A) } // A is now more recently used than B
            at(4) { store.putFound(C, BYTES, validators(), region = null) } // 12 bytes > 10

            assertNull(store.get(B))
            assertEquals(8, store.browsedBytes())
            assertTrue(File(directory, "12/2/1.webp").exists().not())
        }

    @Test
    fun `region tiles are neither counted nor evicted`() =
        runTest {
            val store = store()
            at(1) { store.putFound(R, BIG, validators(), region = 7) }
            at(2) { store.putFound(A, BYTES, validators(), region = null) }
            at(3) { store.putFound(B, BYTES, validators(), region = null) }

            assertEquals(8, store.browsedBytes())
            assertArrayEquals(BIG, store.get(R)!!.bytes)
            assertEquals(BIG.size + 8L, store.totalBytes())
        }

    @Test
    fun `deleting a region drops only its claims, and its other tiles become browsed with their old use`() =
        runTest {
            val store = store()
            at(1) { store.putFound(A, BYTES, validators(), region = 1) }
            store.claim(A, region = 2)
            at(2) { store.putFound(B, BYTES, validators(), region = 1) }
            at(3) { store.putFound(C, BYTES, validators(), region = null) }
            at(4) { store.putFound(D, BYTES, validators(), region = null) }

            store.deleteRegion(1)

            // A stays with region 2. B becomes browsed; with C and D that is 12 bytes > 10, and B
            // is the least recently used, so it goes.
            assertArrayEquals(BYTES, store.get(A)!!.bytes)
            assertNull(store.get(B))
            assertEquals(8, store.browsedBytes())
        }

    @Test
    fun `startup deletes a file without a row and a row without a file`() =
        runTest {
            store().putFound(A, BYTES, validators(), region = null)
            store().putFound(B, BYTES, validators(), region = null)
            File(directory, "12/2/1.webp").delete() // B's file is gone
            val stray = File(directory, "12/9/9.webp").apply { parentFile.mkdirs() }
            stray.writeBytes(BYTES)

            val store = store()
            store.reconcile()

            assertArrayEquals(BYTES, store.get(A)!!.bytes)
            assertNull(store.get(B))
            assertFalse(stray.exists())
        }

    @Test
    fun `refreshing keeps the tile and its claims and sets the new validators`() =
        runTest {
            val store = store()
            store.putFound(A, BYTES, validators(freshUntil = 5), region = 3)

            store.refresh(A, validators(freshUntil = 99, lastModified = "Thu, 24 Sep 2026 18:43:19 GMT"))

            val tile = store.get(A)!!
            assertEquals(99, tile.validators.freshUntil)
            assertEquals("Thu, 24 Sep 2026 18:43:19 GMT", tile.validators.lastModified)
            assertEquals(0, store.browsedBytes()) // still the region's
        }

    private suspend fun at(
        time: Long,
        block: suspend () -> Unit,
    ) {
        now = time
        block()
    }

    private fun validators(
        freshUntil: Long = 1000,
        lastModified: String? = null,
    ) = Validators(etag = null, lastModified = lastModified, freshUntil = freshUntil)

    private companion object {
        val A = TileKey(12, 1, 1)
        val B = TileKey(12, 2, 1)
        val C = TileKey(12, 3, 1)
        val D = TileKey(12, 4, 1)
        val R = TileKey(12, 5, 1)
        val BYTES = byteArrayOf(1, 2, 3, 4)
        val BIG = ByteArray(40) { it.toByte() }
    }
}
