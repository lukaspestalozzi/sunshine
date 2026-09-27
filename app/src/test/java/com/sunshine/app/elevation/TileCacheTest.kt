package com.sunshine.app.elevation

import com.sunshine.core.HeightTile
import com.sunshine.core.TileKey
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

// Design D6 (shared compact LRU) and D7 (zoom fallback above zoom 12) of add-terrain-horizon.
class TileCacheTest {
    private val fetched = mutableListOf<TileKey>()

    @Test
    fun `the least recently used tile is evicted when a 65th is loaded`() =
        runTest {
            val cache = cache()
            val keys = (0 until 65).map { TileKey(12, 2000 + it, 1445) }

            keys.forEach { cache.tile(it) }

            assertNull(cache.cached(keys.first()))
            assertNotNull(cache.cached(keys[1]))
            assertNotNull(cache.cached(keys.last()))
        }

    @Test
    fun `a tile in memory is not fetched again`() =
        runTest {
            val cache = cache()

            cache.tile(Z12)
            cache.tile(Z12)

            assertEquals(listOf(Z12), fetched)
        }

    @Test
    fun `a missing zoom-14 tile falls back to zoom 13, then to zoom 12, with one parent fetch`() =
        runTest {
            val cache = cache(missing = { it.zoom > 12 })
            val child = TileKey(14, 4 * Z12.x + 1, 4 * Z12.y + 2)
            val sibling = TileKey(14, child.x - 1, child.y)

            assertNotNull(cache.tile(child))
            assertNotNull(cache.tile(sibling))

            val parent = TileKey(13, child.x / 2, child.y / 2)
            assertEquals(listOf(child, parent, Z12, sibling), fetched)
        }

    @Test
    fun `a missing tile at zoom 12 or below is unavailable, without fallback`() =
        runTest {
            val cache = cache(missing = { true })

            assertNull(cache.tile(Z12))
            assertEquals(listOf(Z12), fetched)
        }

    @Test
    fun `an unavailable zoom-14 tile does not fall back`() =
        runTest {
            val child = TileKey(14, 4 * Z12.x, 4 * Z12.y)
            val cache = cache(unavailable = { it == child })

            assertNull(cache.tile(child))
            assertEquals(listOf(child), fetched)
        }

    @Test
    fun `a failed tile is fetched again on the next request`() =
        runTest {
            var available = false
            val cache = cache(unavailable = { !available })
            assertNull(cache.tile(Z12))

            available = true

            assertNotNull(cache.tile(Z12))
            assertEquals(listOf(Z12, Z12), fetched)
        }

    @Test
    fun `an upsampled child equals bilinear sampling of its parent quadrant`() =
        runTest {
            val cache = cache(missing = { it.zoom == 13 })
            val parent = cache.tile(Z12)!!
            // The south-east quadrant of Z12.
            val child = cache.tile(TileKey(13, 2 * Z12.x + 1, 2 * Z12.y + 1))!!

            for ((row, column) in listOf(10 to 20, 100 to 7, 300 to 411, 510 to 255)) {
                // Child pixel centres in parent pixel-centre coordinates.
                val parentRow = 256 + (row + 0.5) / 2 - 0.5
                val parentColumn = 256 + (column + 0.5) / 2 - 0.5
                assertEquals(bilinear(parent, parentRow, parentColumn), child.height(row, column), 0.125, "($row, $column)")
            }
        }

    private fun bilinear(
        tile: HeightTile,
        row: Double,
        column: Double,
    ): Double {
        val r = row.toInt()
        val c = column.toInt()
        val fr = row - r
        val fc = column - c
        val north = tile.height(r, c) * (1 - fc) + tile.height(r, c + 1) * fc
        val south = tile.height(r + 1, c) * (1 - fc) + tile.height(r + 1, c + 1) * fc
        return north * (1 - fr) + south * fr
    }

    private fun cache(
        missing: (TileKey) -> Boolean = { false },
        unavailable: (TileKey) -> Boolean = { false },
    ) = TileCache(
        fetch = { key ->
            fetched += key
            when {
                unavailable(key) -> DemTile.Unavailable
                missing(key) -> DemTile.Missing
                else -> DemTile.Found(byteArrayOf(key.zoom.toByte()))
            }
        },
        decode = { IntArray(SIZE * SIZE) { i -> terrarium(heightAt(i / SIZE, i % SIZE)) } },
    )

    private companion object {
        const val SIZE = 512
        val Z12 = TileKey(12, 2137, 1445)

        // Not bilinear, so that upsampling is not trivially exact.
        fun heightAt(
            row: Int,
            column: Int,
        ): Double = 500.0 + 2.0 * row + 3.0 * column + 0.01 * row * column + 0.002 * column * column

        // Terrarium: height + 32768 = R·256 + G + B/256.
        fun terrarium(metres: Double): Int {
            val v = ((metres + 32768.0) * 256).toLong()
            return (0xFF shl 24) or ((v shr 16).toInt() and 0xFF shl 16) or ((v shr 8).toInt() and 0xFF shl 8) or (v.toInt() and 0xFF)
        }
    }
}
