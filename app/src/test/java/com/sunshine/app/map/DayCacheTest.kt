package com.sunshine.app.map

import com.sunshine.core.GeoPoint
import com.sunshine.core.HeightTile
import com.sunshine.core.MapArea
import com.sunshine.core.ShadeGrid
import com.sunshine.core.SunPosition
import com.sunshine.core.SunShadeSweep
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
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

    // A day of [area] and [date] with the first [grids] slider steps computed.
    private suspend fun TestScope.day(
        area: MapArea,
        date: LocalDate,
        grids: Int,
    ): DayOverlay {
        val day = DayOverlay(area, date, ZURICH, { _, _, _ -> GRID }, StandardTestDispatcher(testScheduler))
        for (step in day.steps.take(grids)) day.compute(step)
        assertEquals(grids.toLong() * GRID.stateBytes, day.bytes)
        return day
    }

    private companion object {
        val ZURICH: ZoneId = ZoneId.of("Europe/Zurich")
        val DECEMBER_21: LocalDate = LocalDate.of(2025, 12, 21)
        val AREA = MapArea(GeoPoint(46.6863, 7.8632), zoom = 12.0, widthDp = 20.0, heightDp = 30.0)

        // Count pixels: one per 2 dp cell of the 20 × 30 dp area (design D9 of add-sun-exposure-heatmap).
        const val PIXELS = 10 * 15
        val GRID: ShadeGrid =
            SunShadeSweep(AREA, SunPosition(0.0, -30.0, false)).let { sweep ->
                sweep.night(sweep.groundTiles().associateWith { HeightTile.fromMetres(512, FloatArray(512 * 512) { 568f }) })
            }
    }
}
