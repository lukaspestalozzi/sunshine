package com.sunshine.app.map

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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// Sun hours of a cell (sun-exposure-heatmap spec; design D2, D9 of add-sun-exposure-heatmap).
class SunHoursTest {
    @Test
    fun `sun and unknown steps are counted per pixel`() =
        runTest {
            // 29 daytime steps of sun from 10:00, unknown at 15:00, shade otherwise (spec "Counting steps").
            val sunny = (0 until 29).map { at(10, 0).plusMinutes(10L * it) }.toSet()
            val unknown = setOf(at(15, 0))
            val day =
                completeDay(DECEMBER_21, at(12, 0)) { time ->
                    when (time) {
                        in sunny -> SUN
                        in unknown -> UNKNOWN
                        else -> SHADE
                    }
                }

            val hours = day.sunHours()

            assertEquals(144, hours.steps)
            // One pixel per 8 dp cell: 20 × 30 dp → 3 × 4 pixels (design D9).
            assertEquals(3, hours.width)
            assertEquals(4, hours.height)
            assertEquals(setOf<Short>(29), hours.sun.toSet())
            assertEquals(setOf<Short>(1), hours.unknown.toSet())
        }

    @Test
    fun `a selected time off the slider grid is not counted`() =
        runTest {
            val day = completeDay(DECEMBER_21, at(12, 3)) { time -> if (time == at(12, 3)) SUN else SHADE }

            val hours = day.sunHours()

            assertEquals(setOf<Short>(0), hours.sun.toSet())
        }

    @Test
    fun `night steps without unknown cells add nothing, as their grids are not read`() =
        runTest {
            // A sun grid at every step, which cannot happen at night: only the daytime steps count.
            val day = completeDay(DECEMBER_21, at(12, 0)) { SUN }

            val hours = day.sunHours()

            assertTrue(day.nightSteps in 80..100, "${day.nightSteps}")
            assertEquals(setOf((144 - day.nightSteps).toShort()), hours.sun.toSet())
        }

    @Test
    fun `with every tile missing, every pixel is unknown at every step, night included`() =
        runTest {
            val day = completeDay(DECEMBER_21, at(12, 0)) { UNKNOWN }

            val hours = day.sunHours()

            assertEquals(setOf<Short>(144), hours.unknown.toSet())
            assertEquals(setOf<Short>(0), hours.sun.toSet())
        }

    @Test
    fun `a short day has 138 steps`() =
        runTest {
            val date = LocalDate.of(2025, 3, 30)
            val day = completeDay(date, ZonedDateTime.of(date.atTime(12, 0), ZURICH)) { SUN }

            assertEquals(138, day.sunHours().steps)
        }

    @Test
    fun `the counts are made once and stored on the day, 4 bytes per pixel`() =
        runTest {
            val day = completeDay(DECEMBER_21, at(12, 0)) { SHADE }
            val before = day.bytes

            val hours = day.sunHours()

            assertSame(hours, day.sunHours())
            assertSame(hours, day.counted)
            assertEquals(before + 4L * hours.sun.size, day.bytes)
        }

    // Design D3 of overlay-pan-reuse: a day reusing an earlier day counts as the per-pixel reference.
    @Test
    fun `a day of combined grids counts as one lookup per pixel and step`() =
        runTest {
            val base = heatmapDay(DECEMBER_21) { area, sun, cellDp -> grid(area, sun, cellDp, FLAT) }
            launch { base.computeRest { at(12, 0) } }
            base.compute(at(12, 0))
            advanceUntilIdle()
            // The uncovered parts have no terrain: unknown.
            val panned = AREA.copy(center = GeoPoint(AREA.center.latitude, AREA.center.longitude + 10 * 360.0 / (512 * 4096)))
            val day =
                DayOverlay(panned, DECEMBER_21, ZURICH, { area, sun, cellDp ->
                    grid(area, sun, cellDp, null)
                }, StandardTestDispatcher(testScheduler), stepMinutes = 10, cellDp = 8.0, base = base)
            launch { day.computeRest { at(12, 0) } }
            day.compute(at(12, 0))
            advanceUntilIdle()

            val hours = day.sunHours()

            val (sun, unknown) = referenceCounts(day)
            assertArrayEquals(sun, hours.sun)
            assertArrayEquals(unknown, hours.unknown)
            assertTrue(hours.sun.any { it > 0 } && hours.unknown.any { it > 0 }, "both the earlier day and the part are counted")
        }

    // The counts as made before design D3 of overlay-pan-reuse: one lookup per pixel and step.
    private fun referenceCounts(day: DayOverlay): Pair<ShortArray, ShortArray> {
        val raster = OverlayRaster(day.area, day.cellDp)
        val sun = ShortArray(raster.width * raster.height)
        val unknown = ShortArray(raster.width * raster.height)
        for (step in day.steps) {
            val grid = day.gridAt(step)!!
            if (day.isNight(step) && !grid.hasUnknown) continue
            for (y in 0 until raster.height) {
                for (x in 0 until raster.width) {
                    when (grid.stateAt(raster.latitudes[y], raster.longitudes[x])) {
                        Sunshine.SUN -> sun[y * raster.width + x]++
                        Sunshine.UNKNOWN -> unknown[y * raster.width + x]++
                        else -> Unit
                    }
                }
            }
        }
        return sun to unknown
    }

    // The grid of [area] over flat ground [tile], unknown without it: sun by day, shade at night.
    private fun grid(
        area: MapArea,
        sun: SunPosition,
        cellDp: Double,
        tile: HeightTile?,
    ): ShadeGrid {
        val sweep = SunShadeSweep(area, sun, cellDp)
        val tiles =
            object : AbstractMap<TileKey, HeightTile?>() {
                override val entries: Set<Map.Entry<TileKey, HeightTile?>> get() = throw UnsupportedOperationException()

                override fun get(key: TileKey): HeightTile? = tile

                override fun containsKey(key: TileKey) = true
            }
        if (sweep.isNight) return sweep.night(sweep.groundTiles().associateWith { tile })
        sweep.tiles(sweep.groundTiles().associateWith { tile })
        return sweep.assemble(listOf(sweep.compute(tiles)))
    }

    // A day of AREA whose grid at each step is [state] of that step, computed completely.
    private suspend fun TestScope.completeDay(
        date: LocalDate,
        selected: ZonedDateTime,
        state: (ZonedDateTime) -> ShadeGrid,
    ): DayOverlay {
        val times = heatmapDay(date) { _, _, _ -> SHADE }.steps + selected
        val bySun = times.associateBy { sunPosition(AREA.center, it.toInstant()) }
        val day = heatmapDay(date) { _, sun, _ -> state(bySun.getValue(sun)) }
        launch { day.computeRest { selected } }
        day.compute(selected)
        advanceUntilIdle()
        return day
    }

    // A day as the heatmap computes it: 8 dp cells every 10 minutes (design D9).
    private fun TestScope.heatmapDay(
        date: LocalDate,
        grid: suspend (MapArea, SunPosition, Double) -> ShadeGrid,
    ) = DayOverlay(AREA, date, ZURICH, grid, StandardTestDispatcher(testScheduler), stepMinutes = 10, cellDp = 8.0)

    private fun at(
        hour: Int,
        minute: Int,
    ): ZonedDateTime = ZonedDateTime.of(DECEMBER_21.atTime(hour, minute), ZURICH)

    private companion object {
        val ZURICH: ZoneId = ZoneId.of("Europe/Zurich")
        val DECEMBER_21: LocalDate = LocalDate.of(2025, 12, 21)
        val AREA = MapArea(GeoPoint(46.6863, 7.8632), zoom = 12.0, widthDp = 20.0, heightDp = 30.0)
        val FLAT: HeightTile = HeightTile.fromMetres(512, FloatArray(512 * 512) { 568f })
        val SHADE: ShadeGrid = night { FLAT }
        val UNKNOWN: ShadeGrid = night { null }

        // Flat ground everywhere with the sun high in the south: every cell is sun.
        val SUN: ShadeGrid =
            SunShadeSweep(AREA, SunPosition(180.0, 30.0, true)).let { sweep ->
                val tiles =
                    object : AbstractMap<TileKey, HeightTile?>() {
                        override val entries: Set<Map.Entry<TileKey, HeightTile?>> get() = throw UnsupportedOperationException()

                        override fun get(key: TileKey): HeightTile = FLAT

                        override fun containsKey(key: TileKey) = true
                    }
                sweep.assemble(listOf(sweep.compute(tiles)))
            }

        private fun night(tile: () -> HeightTile?): ShadeGrid =
            SunShadeSweep(AREA, SunPosition(0.0, -30.0, false)).let { it.night(it.groundTiles().associateWith { tile() }) }
    }
}
