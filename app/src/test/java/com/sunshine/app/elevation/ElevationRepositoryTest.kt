package com.sunshine.app.elevation

import com.sunshine.core.GeoPoint
import com.sunshine.core.TileKey
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ElevationRepositoryTest {
    private val fetched = mutableListOf<TileKey>()

    @Test
    fun `elevation from loaded tiles is known`() =
        runTest {
            assertEquals(Elevation.Known(568.0), repository().elevation(INTERLAKEN))
        }

    @Test
    fun `a missing tile makes the elevation unknown`() =
        runTest {
            val repository = repository(fetch = { key -> if (key.x == 2138) null else TILE_BYTES })

            assertEquals(Elevation.Unknown, repository.elevation(TILE_BORDER))
        }

    @Test
    fun `an undecodable tile makes the elevation unknown`() =
        runTest {
            assertEquals(Elevation.Unknown, repository(decode = { null }).elevation(INTERLAKEN))
        }

    @Test
    fun `a decoded tile of the wrong size makes the elevation unknown`() =
        runTest {
            assertEquals(Elevation.Unknown, repository(decode = { IntArray(4) }).elevation(INTERLAKEN))
        }

    @Test
    fun `no tile exists outside the web mercator latitude range`() =
        runTest {
            assertEquals(Elevation.Unknown, repository().elevation(GeoPoint(85.1, 7.0)))
            assertEquals(emptyList<TileKey>(), fetched)
        }

    @Test
    fun `a tile in memory is not fetched again`() =
        runTest {
            val repository = repository()

            repository.elevation(INTERLAKEN)
            repository.elevation(GeoPoint(46.6870, 7.8640))

            assertEquals(listOf(TileKey(12, 2137, 1445)), fetched)
        }

    @Test
    fun `a loaded tile is kept when its neighbour fails, and only the missing tile is fetched again`() =
        runTest {
            var eastAvailable = false
            val repository = repository(fetch = { key -> if (key.x == 2138 && !eastAvailable) null else TILE_BYTES })
            assertEquals(Elevation.Unknown, repository.elevation(TILE_BORDER))
            fetched.clear()

            eastAvailable = true
            assertEquals(Elevation.Known(568.0), repository.elevation(TILE_BORDER))

            assertEquals(listOf(TileKey(12, 2138, 1445)), fetched)
        }

    @Test
    fun `the memory fast path answers only once the tiles are loaded`() =
        runTest {
            val repository = repository()

            assertNull(repository.cachedElevation(INTERLAKEN))
            repository.elevation(INTERLAKEN)
            assertEquals(Elevation.Known(568.0), repository.cachedElevation(INTERLAKEN))
        }

    @Test
    fun `at most eight tiles stay in memory`() =
        runTest {
            val repository = repository()
            val points = (0 until 9).map { GeoPoint(46.6863, 7.8632 + it * 0.1) } // 0.1° apart: a new tile each

            points.forEach { repository.elevation(it) }

            assertNull(repository.cachedElevation(points.first()))
            assertEquals(Elevation.Known(568.0), repository.cachedElevation(points.last()))
        }

    private fun repository(
        fetch: (TileKey) -> ByteArray? = { TILE_BYTES },
        decode: (ByteArray) -> IntArray? = { IntArray(512 * 512) { INTERLAKEN_ARGB } },
    ) = ElevationRepository(
        fetch = { key ->
            fetched += key
            fetch(key)
        },
        decode = decode,
    )

    private companion object {
        val INTERLAKEN = GeoPoint(46.6863, 7.8632)

        // Its samples lie in tiles x 2137 and 2138.
        val TILE_BORDER = GeoPoint(46.6863, 7.91016)
        val TILE_BYTES = byteArrayOf(1)

        // Terrarium for 568.0 m.
        const val INTERLAKEN_ARGB = 0xFF823800.toInt()
    }
}
