package com.sunshine.app.sunshine

import com.sunshine.core.GeoPoint
import com.sunshine.core.HeightTile
import com.sunshine.core.TileKey
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// Design D8 of add-terrain-horizon, on synthetic terrain: a ring of 1000 m walls 1–1.3 km away.
class SunshineRepositoryTest {
    private val requested = mutableListOf<TileKey>()
    private val built = mutableMapOf<TileKey, HeightTile>()
    private var inFlight = 0
    private var maxInFlight = 0

    @Test
    fun `computes the horizon profile of a location`() =
        runTest {
            val profile = repository().profile(OBSERVER)

            assertNotNull(profile)
            assertEquals(1.7, profile!!.eyeHeight, 0.01)
            // atan((1000 - 1.7) / 1000), less up to a pixel of edge smoothing.
            assertEquals(44.9, profile.angleAt(180.0), 0.2)
            assertTrue(profile.complete.all { it })
        }

    @Test
    fun `the tiles of a band are requested concurrently`() =
        runTest {
            repository(latency = true).profile(OBSERVER)

            assertTrue(maxInFlight > 1, "at most $maxInFlight request at a time")
        }

    @Test
    fun `a missing tile leaves incomplete bins`() =
        runTest {
            val profile = repository(missing = { it.zoom == 12 }).profile(OBSERVER)!!

            assertTrue(profile.complete.any { !it })
        }

    @Test
    fun `a missing ground tile gives no profile`() =
        runTest {
            assertNull(repository(missing = { it.zoom == 14 }).profile(OBSERVER))
        }

    @Test
    fun `a location among the last four is answered without tile requests`() =
        runTest {
            val repository = repository()
            val points = (0 until 4).map { GeoPoint(OBSERVER.latitude, OBSERVER.longitude + it * 1e-4) }
            points.forEach { repository.profile(it) }
            requested.clear()

            repository.profile(points.first())

            assertEquals(emptyList<TileKey>(), requested)
        }

    @Test
    fun `an incomplete profile is computed again`() =
        runTest {
            val repository = repository(missing = { it.zoom == 12 })
            repository.profile(OBSERVER)
            requested.clear()

            repository.profile(OBSERVER)

            assertTrue(requested.isNotEmpty())
        }

    private fun repository(
        latency: Boolean = false,
        missing: (TileKey) -> Boolean = { false },
    ) = SunshineRepository(
        tile = { key ->
            requested += key
            inFlight++
            maxInFlight = maxOf(maxInFlight, inFlight)
            if (latency) delay(10)
            inFlight--
            if (missing(key)) null else built.getOrPut(key) { heightTile(key) }
        },
    )

    /** Terrain heights at the pixel centres of [key]. */
    private fun heightTile(key: TileKey): HeightTile {
        val n = ((1L shl key.zoom) * SIZE).toDouble()
        val lons = DoubleArray(SIZE) { c -> (key.x.toLong() * SIZE + c + 0.5) / n * 360.0 - 180.0 }
        val lats = DoubleArray(SIZE) { r -> Math.toDegrees(atan(sinh(PI * (1 - 2 * (key.y.toLong() * SIZE + r + 0.5) / n)))) }
        return HeightTile.fromMetres(SIZE, FloatArray(SIZE * SIZE) { i -> height(lats[i / SIZE], lons[i % SIZE]).toFloat() })
    }

    private fun height(
        latitude: Double,
        longitude: Double,
    ): Double {
        val north = (latitude - OBSERVER.latitude) * METRES_PER_DEGREE
        val east = (longitude - OBSERVER.longitude) * METRES_PER_DEGREE * cos(Math.toRadians(OBSERVER.latitude))
        return if (sqrt(north * north + east * east) in 1000.0..1300.0) 1000.0 else 0.0
    }

    private companion object {
        const val SIZE = 512
        val OBSERVER = GeoPoint(46.6863, 7.8632)
        val METRES_PER_DEGREE = Math.toRadians(1.0) * 6_371_000.0
    }
}
