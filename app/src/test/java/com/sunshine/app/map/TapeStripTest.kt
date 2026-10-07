package com.sunshine.app.map

import com.sunshine.core.AZIMUTH_COUNT
import com.sunshine.core.AZIMUTH_STEP
import com.sunshine.core.GeoPoint
import com.sunshine.core.HeightTile
import com.sunshine.core.HorizonProfile
import com.sunshine.core.MapArea
import com.sunshine.core.SunPeriods
import com.sunshine.core.SunPosition
import com.sunshine.core.SunShadeSweep
import com.sunshine.core.Sunshine
import com.sunshine.core.sunPeriods
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// The states of the time tape's strip (time-selection spec, "Time tape strip"; design D5 of polish-ui).
class TapeStripTest {
    @Test
    fun `night wins over the source, a missing state is not computed yet`() {
        val night = booleanArrayOf(true, true, false, false, false, false)
        val source = listOf(Sunshine.SUN, null, Sunshine.SUN, Sunshine.SHADE, Sunshine.UNKNOWN, null)

        val strip = tapeStrip(night) { source[it] }

        assertEquals(
            listOf(
                StripState.NIGHT,
                StripState.NIGHT,
                StripState.SUN,
                StripState.SHADE,
                StripState.UNKNOWN,
                StripState.NOT_COMPUTED,
            ),
            strip,
        )
    }

    @Test
    fun `a 10-minute day maps onto 5-minute tape steps`() {
        // 14:10 and 14:15 take the state of the heatmap's step at 14:10.
        assertEquals(85, sourceStep(tapeMinutes = 850f, sourceStepMinutes = 10))
        assertEquals(85, sourceStep(tapeMinutes = 855f, sourceStepMinutes = 10))
        assertEquals(86, sourceStep(tapeMinutes = 860f, sourceStepMinutes = 10))
        assertEquals(0, sourceStep(tapeMinutes = 0f, sourceStepMinutes = 10))
    }

    @Test
    fun `night is where the sun's upper edge is at or below the horizon, around sunrise 08 10 and sunset 16 43`() {
        val times = TapeScale(DECEMBER_21, ZURICH, step = 5).times()

        val night = nightSteps(INTERLAKEN, times)

        // 08:05 is index 97, 08:15 index 99; 16:40 is index 200, 16:45 index 201.
        assertTrue(night.indexOfFirst { !it } in 97..99, "first day step ${night.indexOfFirst { !it }}")
        assertTrue(night.indexOfLast { !it } in 199..201, "last day step ${night.indexOfLast { !it }}")
        assertEquals(night.indexOfLast { !it } - night.indexOfFirst { !it } + 1, night.count { !it })
    }

    @Test
    fun `the tracer's state at each step, unknown without a ground height`() {
        val times = TapeScale(DECEMBER_21, ZURICH, step = 5).times()
        // A 15° horizon all round: the sun clears it only around noon (about 20° high).
        val profile = horizon(15.0)

        assertEquals(Sunshine.SUN, tracerState(profile, INTERLAKEN, times[144]))
        assertEquals(Sunshine.SHADE, tracerState(profile, INTERLAKEN, times[114]))
        assertEquals(Sunshine.UNKNOWN, tracerState(null, INTERLAKEN, times[144]))
    }

    // Scenario "Overlay off in Interlaken": the strip is sun where the headline's periods say so and
    // terrain shade between them (±1 step). A horizon with a notch at noon gives two periods, as there.
    @Test
    fun `from the horizon the strip is sun within the day's sun periods and shade between them`() {
        val times = TapeScale(DECEMBER_21, ZURICH, step = 5).times()
        val angles = DoubleArray(AZIMUTH_COUNT) { if (it * AZIMUTH_STEP in 178.0..182.0) 40.0 else 10.0 }
        val profile = HorizonProfile(eyeHeight = 568.0, angles = angles, upper = angles.copyOf())
        val periods = (sunPeriods(profile, INTERLAKEN, DECEMBER_21, ZURICH) as SunPeriods.Known).periods

        val strip = tapeStrip(nightSteps(INTERLAKEN, times)) { tracerState(profile, INTERLAKEN, times[it]) }

        assertEquals(2, periods.size)
        val boundaries = periods.flatMap { listOf(it.start, it.end) }
        for ((i, time) in times.withIndex()) {
            if (strip[i] == StripState.NIGHT || boundaries.any { Duration.between(it, time).abs() < Duration.ofMinutes(5) }) continue
            val sunny = periods.any { !time.isBefore(it.start) && time.isBefore(it.end) }
            assertEquals(if (sunny) StripState.SUN else StripState.SHADE, strip[i], "$time")
        }
        assertEquals(setOf(StripState.NIGHT, StripState.SUN, StripState.SHADE), strip.toSet())
    }

    @Test
    fun `a strip from the horizon takes at most 20 ms`() {
        val times = TapeScale(DECEMBER_21, ZURICH, step = 5).times()
        val profile = horizon(15.0)

        fun strip() = tapeStrip(nightSteps(INTERLAKEN, times)) { tracerState(profile, INTERLAKEN, times[it]) }
        repeat(WARM_UP) { strip() }

        val millis = (1..RUNS).minOf { measureMillis { strip() } }

        assertTrue(millis <= 20.0, "$millis ms")
    }

    @Test
    fun `a strip from a computed day takes at most 5 ms`() =
        runTest {
            val area = MapArea(INTERLAKEN, zoom = 12.0, widthDp = 411.0, heightDp = 891.0)
            val grid = SunShadeSweep(area, SunPosition(0.0, -30.0, false)).let { it.night(it.groundTiles().associateWith { FLAT }) }
            val day = DayOverlay(area, DECEMBER_21, ZURICH, { _, _, _ -> grid }, StandardTestDispatcher(testScheduler))
            day.computeAll { day.steps[144] }
            val scale = TapeScale(DECEMBER_21, ZURICH, step = 5)
            val night = nightSteps(INTERLAKEN, scale.times())

            fun strip() = tapeStrip(night) { dayState(day, INTERLAKEN, it * 5f) }
            repeat(WARM_UP) { strip() }

            val millis = (1..RUNS).minOf { measureMillis { strip() } }

            assertTrue(millis <= 5.0, "$millis ms")
            assertEquals(StripState.SHADE, strip()[144])
        }

    private fun measureMillis(block: () -> Unit): Double {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1e6
    }

    private fun horizon(angle: Double) =
        HorizonProfile(eyeHeight = 568.0, angles = DoubleArray(AZIMUTH_COUNT) { angle }, upper = DoubleArray(AZIMUTH_COUNT) { angle })

    private companion object {
        val ZURICH: ZoneId = ZoneId.of("Europe/Zurich")
        val DECEMBER_21: LocalDate = LocalDate.of(2025, 12, 21)
        val INTERLAKEN = GeoPoint(46.6863, 7.8632)
        val FLAT: HeightTile = HeightTile.fromMetres(512, FloatArray(512 * 512) { 568f })
        const val WARM_UP = 20
        const val RUNS = 10
    }
}
