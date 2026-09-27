package com.sunshine.app.sunshine

import com.sunshine.core.GeoPoint
import com.sunshine.core.HeightTile
import com.sunshine.core.MapArea
import com.sunshine.core.ShadeGrid
import com.sunshine.core.SunPosition
import com.sunshine.core.SunShadeSweep
import com.sunshine.core.TileKey
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.sin
import kotlin.math.sinh
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

// Design D8 of add-sun-shade-overlay, on synthetic terrain.
class OverlayRepositoryTest {
    private val requested = mutableListOf<TileKey>()
    private val built = mutableMapOf<TileKey, HeightTile>()
    private var inFlight = 0
    private var maxInFlight = 0
    private var gate: CompletableDeferred<Unit>? = null

    @Test
    fun `every planned tile is requested once, concurrently`() =
        runTest {
            repository(latency = true).grid(AREA, SUN)

            val sweep = SunShadeSweep(AREA, SUN)
            val ground = sweep.groundTiles().associateWith(::heightTile)
            assertEquals(sweep.tiles(ground), requested.toSet())
            assertEquals(
                requested.size,
                requested.toSet().size,
                "requested twice: ${requested.groupBy { it }.filter { it.value.size > 1 }.keys}",
            )
            assertTrue(maxInFlight > 1, "at most $maxInFlight request at a time")
        }

    @Test
    fun `chunks are assembled into the grid of a single chunk`() =
        runTest {
            val chunked = repository(chunks = 4).grid(AREA, SUN)
            val single = repository(chunks = 1).grid(AREA, SUN)

            assertSameStates(single, chunked)
        }

    @Test
    fun `another time at the same area requests only tiles it has not kept`() =
        runTest {
            val repository = repository()
            repository.grid(AREA, SUN)
            val first = requested.toSet()
            requested.clear()

            repository.grid(AREA, SunPosition(230.0, 15.0, true))

            assertTrue(requested.none { it in first }, "requested again: ${requested.filter { it in first }}")
        }

    @Test
    fun `a small pan keeps the tiles, a pan by more than half a screen drops them`() =
        runTest {
            val repository = repository()
            repository.grid(AREA, SUN)
            val ground = SunShadeSweep(AREA, SUN).groundTiles()

            requested.clear()
            val near = AREA.movedEast(0.3 * AREA.widthDp)
            repository.grid(near, SUN)
            assertTrue(requested.none { it in ground }, "a small pan requests kept tiles again")

            // The kept tiles are now those of `near`; pan 0.6 screens away from it.
            requested.clear()
            val far = near.movedEast(0.6 * AREA.widthDp)
            repository.grid(far, SUN)
            val nearSweep = SunShadeSweep(near, SUN)
            val kept = nearSweep.tiles(nearSweep.groundTiles().associateWith(::heightTile))
            val overlap = SunShadeSweep(far, SUN).groundTiles() intersect kept
            assertTrue(overlap.isNotEmpty() && requested.containsAll(overlap), "a far pan reuses tiles")
        }

    @Test
    fun `a cancelled computation keeps nothing`() =
        runTest {
            val repository = repository()
            gate = CompletableDeferred()
            val job = async(start = CoroutineStart.UNDISPATCHED) { repository.grid(AREA, SUN) }
            job.cancel()
            gate!!.complete(Unit)
            assertThrows<kotlinx.coroutines.CancellationException> { job.await() }
            gate = null
            requested.clear()

            repository.grid(AREA, SUN)

            assertTrue(requested.containsAll(SunShadeSweep(AREA, SUN).groundTiles()))
        }

    private fun repository(
        chunks: Int = 3,
        latency: Boolean = false,
    ) = OverlayRepository(
        tile = { key ->
            gate?.await()
            requested += key
            inFlight++
            maxInFlight = maxOf(maxInFlight, inFlight)
            if (latency) delay(10)
            inFlight--
            heightTile(key)
        },
        chunks = chunks,
    )

    private fun assertSameStates(
        expected: ShadeGrid,
        actual: ShadeGrid,
    ) {
        val (nw, _, se, _) = AREA.corners()
        for (i in 0..20) {
            for (j in 0..20) {
                val lat = se.latitude + (nw.latitude - se.latitude) * i / 20
                val lon = nw.longitude + (se.longitude - nw.longitude) * j / 20
                assertEquals(expected.stateAt(lat, lon), actual.stateAt(lat, lon), "at $lat, $lon")
            }
        }
    }

    private fun MapArea.movedEast(dp: Double) =
        copy(
            center =
                GeoPoint(
                    center.latitude,
                    center.longitude + dp * metresPerDp / (METRES_PER_DEGREE * kotlin.math.cos(Math.toRadians(center.latitude))),
                ),
        )

    /** Ridges 700 m high about 4.6 km apart, above 900 m valleys, at the pixel centres of [key]. */
    private fun heightTile(key: TileKey): HeightTile =
        built.getOrPut(key) {
            val n = ((1L shl key.zoom) * SIZE).toDouble()
            val lons = DoubleArray(SIZE) { c -> (key.x.toLong() * SIZE + c + 0.5) / n * 360.0 - 180.0 }
            val lats = DoubleArray(SIZE) { r -> Math.toDegrees(atan(sinh(PI * (1 - 2 * (key.y.toLong() * SIZE + r + 0.5) / n)))) }
            HeightTile.fromMetres(
                SIZE,
                FloatArray(SIZE * SIZE) { i ->
                    val ridge = sin(lats[i / SIZE] * 150.0 + lons[i % SIZE] * 40.0)
                    (900.0 + 700.0 * ridge * ridge).toFloat()
                },
            )
        }

    private companion object {
        const val SIZE = 512
        val AREA = MapArea(GeoPoint(46.6, 7.9), zoom = 12.0, widthDp = 120.0, heightDp = 200.0)
        val SUN = SunPosition(170.0, 12.0, true)
        val METRES_PER_DEGREE = Math.toRadians(1.0) * 6_371_000.0
    }
}
