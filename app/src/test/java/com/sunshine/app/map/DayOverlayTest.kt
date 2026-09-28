package com.sunshine.app.map

import com.sunshine.app.sunshine.OverlayRepository
import com.sunshine.core.GeoPoint
import com.sunshine.core.HeightTile
import com.sunshine.core.MapArea
import com.sunshine.core.ShadeGrid
import com.sunshine.core.SunPosition
import com.sunshine.core.SunShadeSweep
import com.sunshine.core.Sunshine
import com.sunshine.core.TileKey
import com.sunshine.core.sunPosition
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// The overlay of the whole day (sun-shade-overlay spec, "Overlay of the whole day"; design D11 of
// add-sun-shade-overlay).
@OptIn(ExperimentalCoroutinesApi::class)
class DayOverlayTest {
    private val suns = mutableListOf<SunPosition>()
    private val dispatchers = mutableListOf<ContinuationInterceptor?>()
    private val gate = MutableStateFlow(true)

    // Records each request and returns the same grid; [gate] holds a request, which cannot be cancelled
    // meanwhile, like a sweep between two chunks.
    private val fakeGrid: suspend (MapArea, SunPosition) -> ShadeGrid = { _, sun ->
        suns += sun
        dispatchers += currentCoroutineContext()[ContinuationInterceptor]
        withContext(NonCancellable) { gate.first { it } }
        GRID
    }

    @Test
    fun `the steps are the slider positions of the day, 23 or 25 hours on DST days`() {
        for ((date, count) in listOf(DECEMBER_21 to 288, LocalDate.of(2025, 3, 30) to 276, LocalDate.of(2025, 10, 26) to 300)) {
            val day = DayOverlay(AREA, date, ZURICH, fakeGrid, StandardTestDispatcher())

            assertEquals(List(count) { sliderTime(date, ZURICH, it * 5f) }, day.steps, "$date")
        }
    }

    @Test
    fun `the selected time comes first, then the nearest steps, later before earlier`() =
        runTest {
            val day = DayOverlay(AREA, DECEMBER_21, ZURICH, fakeGrid, StandardTestDispatcher(testScheduler))
            val rest = launch { day.computeRest { at(12, 0) } }
            advanceUntilIdle()
            assertEquals(emptyList<SunPosition>(), suns, "the day started before the selected time")

            day.compute(at(12, 0))
            advanceUntilIdle()

            assertTrue(rest.isCompleted)
            assertEquals(listOf(at(12, 0), at(12, 5), at(11, 55), at(12, 10), at(11, 50)).map(::sunAt), suns.take(5))
            assertEquals(day.steps.map(::sunAt).toSet(), suns.toSet())
            assertEquals(288, suns.size)
            for (step in day.steps) assertNotNull(day.gridAt(step), "$step")
        }

    @Test
    fun `a selected time off the slider grid is computed as its own entry`() =
        runTest {
            val day = DayOverlay(AREA, DECEMBER_21, ZURICH, fakeGrid, StandardTestDispatcher(testScheduler))
            launch { day.computeRest { at(8, 47) } }

            day.compute(at(8, 47))
            advanceUntilIdle()

            assertEquals(listOf(at(8, 47), at(8, 45), at(8, 50), at(8, 40), at(8, 55)).map(::sunAt), suns.take(5))
            assertEquals(289, suns.size)
            assertNotNull(day.gridAt(at(8, 47)))
        }

    @Test
    fun `background steps run on the background dispatcher, the selected time on the caller's`() =
        runTest {
            val background = StandardTestDispatcher(testScheduler, name = "background")
            val day = DayOverlay(AREA, DECEMBER_21, ZURICH, fakeGrid, background)
            launch { day.computeRest { at(12, 0) } }

            day.compute(at(12, 0))
            advanceUntilIdle()

            assertTrue(dispatchers.first() !== background)
            assertTrue(dispatchers.drop(1).all { it === background }, "${dispatchers.toSet()}")
        }

    @Test
    fun `the background dispatcher uses half the cores, at least one`() {
        assertEquals(1, peakParallelism(dayDispatcher(cores = 1)))
        assertEquals(1, peakParallelism(dayDispatcher(cores = 2)))
        assertEquals(1, peakParallelism(dayDispatcher(cores = 3)))
        // Dispatchers.Default has at least 2 threads.
        assertTrue(peakParallelism(dayDispatcher(cores = 8)) in 2..4)
    }

    @Test
    fun `a selected step not yet computed jumps the queue`() =
        runTest {
            val day = DayOverlay(AREA, DECEMBER_21, ZURICH, fakeGrid, StandardTestDispatcher(testScheduler))
            var selected = at(12, 0)
            launch { day.computeRest { selected } }
            day.compute(selected)
            gate.value = false
            runCurrent()
            assertEquals(sunAt(at(12, 5)), suns.last(), "a background step is running")

            selected = at(15, 0)
            launch { day.compute(selected) }
            runCurrent()
            gate.value = true
            advanceUntilIdle()

            assertEquals(listOf(at(12, 5), at(15, 0), at(15, 5), at(14, 55)).map(::sunAt), suns.subList(1, 5))
        }

    @Test
    fun `computed counts the finished slider steps, not an off-grid time`() =
        runTest {
            val day = DayOverlay(AREA, DECEMBER_21, ZURICH, fakeGrid, StandardTestDispatcher(testScheduler))
            val counts = mutableListOf<Int>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { day.computed.collect { counts += it } }
            launch { day.computeRest { at(8, 47) } }

            day.compute(at(8, 47))
            assertEquals(0, day.computed.value)
            advanceUntilIdle()

            assertEquals(288, day.computed.value)
            assertEquals((0..288).toList(), counts)
        }

    @Test
    fun `bytes add up the grids' states, and hasUnknown tells whether a grid has unknown cells`() =
        runTest {
            val known = DayOverlay(AREA, DECEMBER_21, ZURICH, fakeGrid, StandardTestDispatcher(testScheduler))
            known.compute(at(12, 0))
            known.compute(at(12, 5))
            assertEquals(2L * GRID.stateBytes, known.bytes)
            assertEquals(false, known.hasUnknown)

            val unknown = DayOverlay(AREA, DECEMBER_21, ZURICH, { _, _ -> UNKNOWN_GRID }, StandardTestDispatcher(testScheduler))
            unknown.compute(at(12, 0))
            assertEquals(true, unknown.hasUnknown)
        }

    @Test
    fun `resumed, the day computes only its missing steps, a selected time not yet known first`() =
        runTest {
            val requested = mutableListOf<SunPosition>()
            val hold = MutableStateFlow(true)
            val day =
                DayOverlay(AREA, DECEMBER_21, ZURICH, { _, sun ->
                    requested += sun
                    // The fourth grid waits and cannot be cancelled, like a sweep between chunks.
                    if (requested.size == 4) withContext(NonCancellable) { hold.first { it } }
                    GRID
                }, StandardTestDispatcher(testScheduler))
            var selected = at(12, 0)
            hold.value = false
            val first = launch { day.computeRest { selected } }
            day.compute(selected)
            runCurrent()
            first.cancel()
            hold.value = true
            advanceUntilIdle()
            assertEquals(listOf(at(12, 0), at(12, 5), at(11, 55), at(12, 10)).map(::sunAt), requested)
            // 12:10 was running when the day was cancelled: its grid is discarded, so it is still missing.
            assertEquals(3, day.computed.value)

            selected = at(15, 0)
            launch { day.computeRest { selected } }
            advanceUntilIdle()
            assertEquals(4, requested.size, "the resumed day started before the selected time")

            day.compute(selected)
            advanceUntilIdle()

            assertEquals(listOf(at(15, 0), at(15, 5), at(14, 55)).map(::sunAt), requested.subList(4, 7))
            val known = listOf(at(12, 0), at(12, 5), at(11, 55)).map(::sunAt)
            assertTrue(requested.drop(4).none { it in known }, "a known step was computed again")
            assertEquals(288, day.computed.value)
            assertEquals(288 + 1, requested.size)
        }

    @Test
    fun `cancelling stops between steps`() =
        runTest {
            val day = DayOverlay(AREA, DECEMBER_21, ZURICH, fakeGrid, StandardTestDispatcher(testScheduler))
            val rest = launch { day.computeRest { at(12, 0) } }
            day.compute(at(12, 0))
            gate.value = false
            runCurrent()
            val requested = suns.size

            rest.cancel()
            gate.value = true
            advanceUntilIdle()

            assertEquals(requested, suns.size)
            assertTrue(rest.isCompleted)
        }

    @Test
    fun `night steps are counted, and come from the ground tiles alone`() =
        runTest {
            val requested = mutableSetOf<TileKey>()
            val repository = OverlayRepository(tile = { key -> FLAT.also { requested += key } })
            val day = DayOverlay(AREA, DECEMBER_21, ZURICH, repository::grid, StandardTestDispatcher(testScheduler))

            val grid = day.compute(at(2, 0))

            assertEquals(SunShadeSweep(AREA, sunAt(at(2, 0))).groundTiles(), requested)
            for (point in AREA.corners() + AREA.center) assertEquals(Sunshine.SHADE, grid.stateAt(point), "$point")
            // The sun's upper edge is below −3.5° before 07:50 and from 17:05 on 21 December: 177 steps,
            // checked against an independent NOAA-formula computation.
            assertEquals(day.steps.count { sunAt(it).elevation + 0.266 < -3.5 }, day.nightSteps)
            assertEquals(177, day.nightSteps)
        }

    // Largest number of tasks running at once on [dispatcher], out of 8 that each take 50 ms.
    private fun peakParallelism(dispatcher: CoroutineDispatcher): Int {
        val running = AtomicInteger()
        val peak = AtomicInteger()
        runBlocking {
            repeat(8) {
                launch(dispatcher) {
                    peak.accumulateAndGet(running.incrementAndGet(), ::maxOf)
                    Thread.sleep(50)
                    running.decrementAndGet()
                }
            }
        }
        return peak.get()
    }

    private fun at(
        hour: Int,
        minute: Int,
    ): ZonedDateTime = ZonedDateTime.of(DECEMBER_21.atTime(hour, minute), ZURICH)

    private fun sunAt(time: ZonedDateTime) = sunPosition(AREA.center, time.toInstant())

    private companion object {
        val ZURICH: ZoneId = ZoneId.of("Europe/Zurich")
        val DECEMBER_21: LocalDate = LocalDate.of(2025, 12, 21)
        val AREA = MapArea(GeoPoint(46.6863, 7.8632), zoom = 12.0, widthDp = 20.0, heightDp = 30.0)
        val FLAT: HeightTile = HeightTile.fromMetres(512, FloatArray(512 * 512) { 568f })
        val GRID: ShadeGrid =
            SunShadeSweep(AREA, SunPosition(0.0, -30.0, false)).let { it.night(it.groundTiles().associateWith { FLAT }) }
        val UNKNOWN_GRID: ShadeGrid =
            SunShadeSweep(AREA, SunPosition(0.0, -30.0, false)).let { it.night(it.groundTiles().associateWith { null }) }
    }
}
