package com.sunshine.app.map

import com.sunshine.core.GeoPoint
import com.sunshine.core.HeightTile
import com.sunshine.core.MapArea
import com.sunshine.core.ShadeGrid
import com.sunshine.core.SunPosition
import com.sunshine.core.SunShadeSweep
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

// Sun hours of a cell (sun-exposure-heatmap spec; design D2 of add-sun-exposure-heatmap).
class SunHoursTest {
    @Test
    fun `sun and unknown steps are counted per pixel`() =
        runTest {
            // 57 daytime steps of sun from 10:00, unknown at 15:00 and 15:05, shade otherwise (spec "Counting steps").
            val sunny = (0 until 57).map { at(10, 0).plusMinutes(5L * it) }.toSet()
            val unknown = setOf(at(15, 0), at(15, 5))
            val day =
                completeDay(DECEMBER_21, at(12, 0)) { time ->
                    when (time) {
                        in sunny -> SUN
                        in unknown -> UNKNOWN
                        else -> SHADE
                    }
                }

            val hours = day.sunHours()

            assertEquals(288, hours.steps)
            assertEquals(AREA.widthDp.toInt() * AREA.heightDp.toInt(), hours.sun.size)
            assertEquals(setOf<Short>(57), hours.sun.toSet())
            assertEquals(setOf<Short>(2), hours.unknown.toSet())
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
            // A sun grid at every step, which cannot happen at night: only the 111 daytime steps count.
            val day = completeDay(DECEMBER_21, at(12, 0)) { SUN }

            val hours = day.sunHours()

            assertEquals(177, day.nightSteps)
            assertEquals(setOf<Short>(111), hours.sun.toSet())
        }

    @Test
    fun `with every tile missing, every pixel is unknown at every step, night included`() =
        runTest {
            val day = completeDay(DECEMBER_21, at(12, 0)) { UNKNOWN }

            val hours = day.sunHours()

            assertEquals(setOf<Short>(288), hours.unknown.toSet())
            assertEquals(setOf<Short>(0), hours.sun.toSet())
        }

    @Test
    fun `a short day has 276 steps`() =
        runTest {
            val date = LocalDate.of(2025, 3, 30)
            val day = completeDay(date, ZonedDateTime.of(date.atTime(12, 0), ZURICH)) { SUN }

            assertEquals(276, day.sunHours().steps)
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

    // A day of AREA whose grid at each step is [state] of that step, computed completely.
    private suspend fun TestScope.completeDay(
        date: LocalDate,
        selected: ZonedDateTime,
        state: (ZonedDateTime) -> ShadeGrid,
    ): DayOverlay {
        val times = DayOverlay(AREA, date, ZURICH, { _, _ -> SHADE }, StandardTestDispatcher(testScheduler)).steps + selected
        val bySun = times.associateBy { sunPosition(AREA.center, it.toInstant()) }
        val day = DayOverlay(AREA, date, ZURICH, { _, sun -> state(bySun.getValue(sun)) }, StandardTestDispatcher(testScheduler))
        launch { day.computeRest { selected } }
        day.compute(selected)
        advanceUntilIdle()
        return day
    }

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
