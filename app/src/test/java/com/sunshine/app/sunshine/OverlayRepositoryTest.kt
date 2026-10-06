package com.sunshine.app.sunshine

import com.sunshine.app.elevation.TileLoads
import com.sunshine.core.GeoPoint
import com.sunshine.core.HeightTile
import com.sunshine.core.MapArea
import com.sunshine.core.ShadeGrid
import com.sunshine.core.SunPosition
import com.sunshine.core.SunShadeSweep
import com.sunshine.core.Sunshine
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
    private var stored = 0

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

    // Design D5 of overlay-pan-reuse: kept while the areas intersect.
    @Test
    fun `a pan within a screen keeps the tiles, a pan by more than a screen drops them`() =
        runTest {
            val repository = repository()
            repository.grid(AREA, SUN)
            val ground = SunShadeSweep(AREA, SUN).groundTiles()

            requested.clear()
            val near = AREA.movedEast(0.6 * AREA.widthDp)
            repository.grid(near, SUN)
            assertTrue(requested.none { it in ground }, "a pan within a screen requests kept tiles again")

            // The kept tiles are now those of `near`; pan 1.2 screens away from it.
            requested.clear()
            val far = near.movedEast(1.2 * AREA.widthDp)
            repository.grid(far, SUN)
            val nearSweep = SunShadeSweep(near, SUN)
            val kept = nearSweep.tiles(nearSweep.groundTiles().associateWith(::heightTile))
            val overlap = SunShadeSweep(far, SUN).groundTiles() intersect kept
            assertTrue(overlap.isNotEmpty() && requested.containsAll(overlap), "a far pan reuses tiles")
        }

    // Design D5 of overlay-pan-reuse: the parts of a panned area and the next step share the tiles.
    @Test
    fun `a grid of an intersecting part at the same zoom requests no tile it has kept`() =
        runTest {
            val repository = repository()
            repository.grid(AREA, SUN)
            val kept = requested.toSet()
            requested.clear()

            // The east half of AREA panned by half its width: it touches AREA's east edge.
            val part = AREA.movedEast(0.75 * AREA.widthDp).copy(widthDp = AREA.widthDp / 2)
            repository.grid(part, SunPosition(SUN.azimuth + 0.1, SUN.elevation, true))

            assertTrue(requested.none { it in kept }, "requested again: ${requested.filter { it in kept }}")
        }

    // settings spec, "Debug info": the grid's tiles by where they came from (design D4 of polish-overlay).
    @Test
    fun `the tiles of a grid are counted by source, an unavailable one included`() =
        runTest {
            val debug = DebugInfo()
            val missingKey = SunShadeSweep(AREA, SUN).groundTiles().first()
            val repository = repository(missing = { it == missingKey }, debug = debug)

            repository.grid(AREA, SUN)

            val first = requested.toSet().size
            assertEquals(TileSources(kept = 0, memory = 0, disk = first - 1, network = 0, unavailable = 1), debug.values.value.gridTiles)

            requested.clear()
            repository.grid(AREA, SunPosition(230.0, 15.0, true))

            val sources = checkNotNull(debug.values.value.gridTiles)
            assertEquals(requested.toSet().size, sources.disk + sources.unavailable)
            assertTrue(sources.kept > 0, "nothing kept: $sources")
        }

    @Test
    fun `at night only the ground tiles are requested and every cell is shade`() =
        runTest {
            val night = SunPosition(10.0, -20.0, false)

            val grid = repository().grid(AREA, night)

            assertEquals(SunShadeSweep(AREA, night).groundTiles(), requested.toSet())
            for (point in AREA.corners() + AREA.center) assertEquals(Sunshine.SHADE, grid.stateAt(point), "$point")
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
        missing: (TileKey) -> Boolean = { false },
        debug: DebugInfo = DebugInfo(),
    ) = OverlayRepository(
        tile = { key ->
            gate?.await()
            requested += key
            inFlight++
            maxInFlight = maxOf(maxInFlight, inFlight)
            if (latency) delay(10)
            inFlight--
            if (missing(key)) {
                null
            } else {
                stored++
                heightTile(key)
            }
        },
        chunks = chunks,
        loads = { TileLoads(network = 0, store = stored) },
        debug = debug,
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
