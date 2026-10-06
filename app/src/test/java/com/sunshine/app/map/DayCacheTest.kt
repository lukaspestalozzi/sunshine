package com.sunshine.app.map

import com.sunshine.core.GeoPoint
import com.sunshine.core.HeightTile
import com.sunshine.core.MapArea
import com.sunshine.core.ShadeGrid
import com.sunshine.core.SunPosition
import com.sunshine.core.SunShadeSweep
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.pow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// The cache of overlay days (sun-shade-overlay spec, "Overlay of the whole day"; design D14 of
// add-sun-shade-overlay).
@OptIn(ExperimentalCoroutinesApi::class)
class DayCacheTest {
    @Test
    fun `a day is found by its area and date`() =
        runTest {
            val cache = DayCache(maxBytes = Long.MAX_VALUE)
            val day = day(AREA, DECEMBER_21, grids = 1)

            cache.put(day)

            assertSame(day, cache.get(AREA, DECEMBER_21))
            assertNull(cache.get(AREA, DECEMBER_21.plusDays(1)))
            assertNull(cache.get(AREA.copy(zoom = 13.0), DECEMBER_21))
        }

    @Test
    fun `days of the same area, date and cell size with other steps are kept apart`() =
        runTest {
            val cache = DayCache(maxBytes = Long.MAX_VALUE)
            val every5 = day(AREA, DECEMBER_21, grids = 1)
            val every10 = day(AREA, DECEMBER_21, grids = 1, stepMinutes = 10)

            cache.put(every5)
            cache.put(every10)

            assertSame(every5, cache.get(AREA, DECEMBER_21, SunShadeSweep.CELL_DP, stepMinutes = 5))
            assertSame(every10, cache.get(AREA, DECEMBER_21, SunShadeSweep.CELL_DP, stepMinutes = 10))
            assertNull(cache.get(AREA, DECEMBER_21, SunShadeSweep.CELL_DP, stepMinutes = 15))
        }

    @Test
    fun `a day put again for the same area and date replaces the first`() =
        runTest {
            val cache = DayCache(maxBytes = Long.MAX_VALUE)
            cache.put(day(AREA, DECEMBER_21, grids = 1))
            val again = day(AREA, DECEMBER_21, grids = 1)

            cache.put(again)

            assertSame(again, cache.get(AREA, DECEMBER_21))
        }

    @Test
    fun `trim drops the least recently used days while the bytes exceed the budget`() =
        runTest {
            val cache = DayCache(maxBytes = 4L * GRID.stateBytes)
            val (a, b, c) = List(3) { day(AREA, DECEMBER_21.plusDays(it.toLong()), grids = 2) }
            cache.put(a)
            cache.put(b)
            cache.put(c)
            cache.get(a.area, a.date)

            cache.trim(keep = c)

            assertNull(cache.get(b.area, b.date))
            assertSame(a, cache.get(a.area, a.date))
            assertSame(c, cache.get(c.area, c.date))
        }

    @Test
    fun `trim never drops the day being shown, even when it alone exceeds the budget`() =
        runTest {
            val cache = DayCache(maxBytes = 1L * GRID.stateBytes)
            val other = day(AREA, DECEMBER_21.plusDays(1), grids = 1)
            val shown = day(AREA, DECEMBER_21, grids = 2)
            cache.put(shown)
            cache.put(other)

            cache.trim(keep = shown)

            assertSame(shown, cache.get(AREA, DECEMBER_21))
            assertNull(cache.get(other.area, other.date))
        }

    @Test
    fun `a day's sun hours count towards the budget, 4 bytes per pixel`() =
        runTest {
            val counted = day(AREA, DECEMBER_21, grids = 288)
            val other = day(AREA, DECEMBER_21.plusDays(1), grids = 288)
            val grids = 288L * GRID.stateBytes
            counted.sunHours()
            // One byte short of both days with the counts; without them, both would fit.
            val cache = DayCache(maxBytes = 2 * grids + 4L * PIXELS - 1)
            cache.put(counted)
            cache.put(other)

            assertEquals(grids + 4L * PIXELS, counted.bytes)
            cache.trim(keep = other)

            assertNull(cache.get(counted.area, counted.date))
            assertSame(other, cache.get(other.area, other.date))
        }

    @Test
    fun `a removed day is gone`() =
        runTest {
            val cache = DayCache(maxBytes = Long.MAX_VALUE)
            val day = day(AREA, DECEMBER_21, grids = 1)
            cache.put(day)

            cache.remove(day)

            assertNull(cache.get(AREA, DECEMBER_21))
            assertEquals(0L, cache.bytes)
        }

    // An earlier day fills in after a pan (sun-shade-overlay spec, "Overlay updates"; design D2 of polish-overlay).
    @Test
    fun `an overlapping day of the same date, cell size and step with the time is found`() =
        runTest {
            val cache = DayCache(maxBytes = Long.MAX_VALUE)
            val earlier = day(AREA, DECEMBER_21, grids = 2)
            val current = day(PANNED, DECEMBER_21, grids = 1)
            cache.put(earlier)
            cache.put(current)

            assertSame(earlier, cache.overlapping(PANNED, earlier.steps[1], SunShadeSweep.CELL_DP, 5, except = current))
        }

    @Test
    fun `days that do not fit are not found`() =
        runTest {
            val cache = DayCache(maxBytes = Long.MAX_VALUE)
            val time = day(AREA, DECEMBER_21, grids = 1).steps[1]
            val current = day(PANNED, DECEMBER_21, grids = 2)
            cache.put(day(AREA.copy(center = GeoPoint(46.6863, 8.8632)), DECEMBER_21, grids = 2))
            cache.put(day(AREA, DECEMBER_21.plusDays(1), grids = 2))
            cache.put(day(AREA, DECEMBER_21, grids = 2, cellDp = 4.0))
            cache.put(day(AREA, DECEMBER_21, grids = 2, stepMinutes = 10))
            cache.put(current)

            assertNull(cache.overlapping(PANNED, time, SunShadeSweep.CELL_DP, 5, except = current))
            // A day without that time.
            cache.put(day(AREA, DECEMBER_21, grids = 1))
            assertNull(cache.overlapping(PANNED, time, SunShadeSweep.CELL_DP, 5, except = current))
        }

    @Test
    fun `of two overlapping days the most recently used is found`() =
        runTest {
            val cache = DayCache(maxBytes = Long.MAX_VALUE)
            val first = day(AREA, DECEMBER_21, grids = 2)
            val second = day(AREA.copy(center = GeoPoint(46.6863, 7.8622)), DECEMBER_21, grids = 2)
            cache.put(first)
            cache.put(second)
            val time = first.steps[1]

            assertSame(second, cache.overlapping(PANNED, time, SunShadeSweep.CELL_DP, 5, except = null))
            cache.get(first.area, first.date)
            assertSame(first, cache.overlapping(PANNED, time, SunShadeSweep.CELL_DP, 5, except = null))
        }

    @Test
    fun `50 cached days are searched within 1 ms`() =
        runTest {
            val cache = DayCache(maxBytes = Long.MAX_VALUE)
            repeat(50) { cache.put(day(AREA.copy(center = GeoPoint(46.0 + it * 0.01, 7.0)), DECEMBER_21.plusDays(it % 2L), grids = 1)) }
            val time = day(AREA, DECEMBER_21, grids = 1).steps[1]
            repeat(WARM_UP) { cache.overlapping(PANNED, time, SunShadeSweep.CELL_DP, 5, except = null) }

            val start = System.nanoTime()
            repeat(RUNS) { cache.overlapping(PANNED, time, SunShadeSweep.CELL_DP, 5, except = null) }
            val millis = (System.nanoTime() - start) / 1e6 / RUNS

            assertTrue(millis <= 1.0, "$millis ms per lookup")
        }

    // The earlier day reused after a camera move (sun-shade-overlay spec, "Overlay of the whole day";
    // design D1 of overlay-pan-reuse).
    @Test
    fun `a day of the same date, cell size and step covering half the new area is reusable`() =
        runTest {
            val cache = DayCache(maxBytes = Long.MAX_VALUE)
            val earlier = day(AREA, DECEMBER_21, grids = 2)
            cache.put(earlier)
            cache.put(day(AREA, DECEMBER_21.plusDays(1), grids = 2))
            cache.put(day(AREA, DECEMBER_21, grids = 2, cellDp = 4.0))
            cache.put(day(AREA, DECEMBER_21, grids = 2, stepMinutes = 10))

            assertSame(earlier, cache.reusable(PANNED, DECEMBER_21, SunShadeSweep.CELL_DP, 5, online = true))
            assertNull(cache.reusable(PANNED, DECEMBER_21.plusDays(2), SunShadeSweep.CELL_DP, 5, online = true))
            assertNull(cache.reusable(PANNED, DECEMBER_21, 8.0, 5, online = true))
            assertNull(cache.reusable(PANNED, DECEMBER_21, SunShadeSweep.CELL_DP, 15, online = true))
        }

    @Test
    fun `a day of the same zoom or up to one level higher is reusable`() =
        runTest {
            // [old] zoom to [new] zoom around the same centre: whether the day at [old] is reused.
            suspend fun reused(
                old: Double,
                new: Double,
            ): Boolean {
                val cache = DayCache(maxBytes = Long.MAX_VALUE)
                cache.put(day(AREA.copy(zoom = old), DECEMBER_21, grids = 1))
                return cache.reusable(AREA.copy(zoom = new), DECEMBER_21, SunShadeSweep.CELL_DP, 5, online = true) != null
            }

            assertFalse(reused(old = 12.0, new = 12.5), "zoomed in")
            assertTrue(reused(old = 12.5, new = 12.0), "zoomed out by half a level")
            assertFalse(reused(old = 13.1, new = 12.0), "zoomed out by more than a level")
        }

    @Test
    fun `a day covering less than a quarter of the new area is not reusable`() =
        runTest {
            val cache = DayCache(maxBytes = Long.MAX_VALUE)
            cache.put(day(AREA, DECEMBER_21, grids = 1))

            // 0.8 of the width to the east: a fifth is covered.
            assertNull(cache.reusable(AREA.panned(dx = 16.0), DECEMBER_21, SunShadeSweep.CELL_DP, 5, online = true))
            assertSame(AREA, cache.reusable(AREA.panned(dx = 14.0), DECEMBER_21, SunShadeSweep.CELL_DP, 5, online = true)?.area)
        }

    @Test
    fun `a day with unknown cells is reusable only offline`() =
        runTest {
            val cache = DayCache(maxBytes = Long.MAX_VALUE)
            val unknown = day(AREA, DECEMBER_21, grids = 1, grid = UNKNOWN_GRID)
            cache.put(unknown)

            assertNull(cache.reusable(PANNED, DECEMBER_21, SunShadeSweep.CELL_DP, 5, online = true))
            assertSame(unknown, cache.reusable(PANNED, DECEMBER_21, SunShadeSweep.CELL_DP, 5, online = false))
        }

    @Test
    fun `of two reusable days the one covering the larger share is chosen`() =
        runTest {
            val cache = DayCache(maxBytes = Long.MAX_VALUE)
            val half = day(AREA, DECEMBER_21, grids = 1)
            val threeQuarters = day(PANNED.panned(dx = -5.0), DECEMBER_21, grids = 1)
            cache.put(threeQuarters)
            cache.put(half)

            // The half-covering day was used more recently.
            assertSame(threeQuarters, cache.reusable(PANNED, DECEMBER_21, SunShadeSweep.CELL_DP, 5, online = true))
        }

    @Test
    fun `the reused day becomes the most recently used`() =
        runTest {
            val cache = DayCache(maxBytes = 2L * GRID.stateBytes)
            val reused = day(AREA, DECEMBER_21, grids = 1)
            cache.put(reused)
            val other = day(AREA, DECEMBER_21.plusDays(1), grids = 1)
            cache.put(other)
            val current = day(PANNED, DECEMBER_21, grids = 1)

            assertSame(reused, cache.reusable(PANNED, DECEMBER_21, SunShadeSweep.CELL_DP, 5, online = true))
            cache.put(current)
            cache.trim(keep = current)

            assertSame(reused, cache.get(AREA, DECEMBER_21))
            assertNull(cache.get(other.area, other.date))
        }

    @Test
    fun `the day of the new area itself is not reused`() =
        runTest {
            val cache = DayCache(maxBytes = Long.MAX_VALUE)
            cache.put(day(PANNED, DECEMBER_21, grids = 1))

            assertNull(cache.reusable(PANNED, DECEMBER_21, SunShadeSweep.CELL_DP, 5, online = true))
        }

    @Test
    fun `50 cached days are searched for a reusable one within 1 ms`() =
        runTest {
            val cache = DayCache(maxBytes = Long.MAX_VALUE)
            repeat(50) { cache.put(day(AREA.panned(dx = it * 0.4), DECEMBER_21.plusDays(it % 2L), grids = 1)) }
            repeat(WARM_UP) { cache.reusable(PANNED, DECEMBER_21, SunShadeSweep.CELL_DP, 5, online = true) }

            val start = System.nanoTime()
            repeat(RUNS) { cache.reusable(PANNED, DECEMBER_21, SunShadeSweep.CELL_DP, 5, online = true) }
            val millis = (System.nanoTime() - start) / 1e6 / RUNS

            assertTrue(millis <= 1.0, "$millis ms per lookup")
        }

    // A day of [area] and [date] with the first [grids] slider steps computed.
    private suspend fun TestScope.day(
        area: MapArea,
        date: LocalDate,
        grids: Int,
        stepMinutes: Int = 5,
        cellDp: Double = SunShadeSweep.CELL_DP,
        grid: ShadeGrid = GRID,
    ): DayOverlay {
        val day = DayOverlay(area, date, ZURICH, { _, _, _ -> grid }, StandardTestDispatcher(testScheduler), stepMinutes, cellDp)
        for (step in day.steps.take(grids)) day.compute(step)
        assertEquals(grids.toLong() * grid.stateBytes, day.bytes)
        return day
    }

    // This area moved [dx] dp east at its zoom.
    private fun MapArea.panned(dx: Double) = copy(center = GeoPoint(center.latitude, center.longitude + dx * 360.0 / (512 * 2.0.pow(zoom))))

    private companion object {
        val ZURICH: ZoneId = ZoneId.of("Europe/Zurich")
        val DECEMBER_21: LocalDate = LocalDate.of(2025, 12, 21)
        val AREA = MapArea(GeoPoint(46.6863, 7.8632), zoom = 12.0, widthDp = 20.0, heightDp = 30.0)

        // AREA moved 10 dp east: they overlap by half their width.
        val PANNED = AREA.copy(center = GeoPoint(46.6863, 7.8632 + 10 * 360.0 / (512 * 4096)))
        const val WARM_UP = 1000
        const val RUNS = 1000

        // Count pixels: one per 2 dp cell of the 20 × 30 dp area (design D9 of add-sun-exposure-heatmap).
        const val PIXELS = 10 * 15
        val GRID: ShadeGrid =
            SunShadeSweep(AREA, SunPosition(0.0, -30.0, false)).let { sweep ->
                sweep.night(sweep.groundTiles().associateWith { HeightTile.fromMetres(512, FloatArray(512 * 512) { 568f }) })
            }
        val UNKNOWN_GRID: ShadeGrid =
            SunShadeSweep(AREA, SunPosition(0.0, -30.0, false)).let { sweep ->
                sweep.night(sweep.groundTiles().associateWith { null })
            }
    }
}
