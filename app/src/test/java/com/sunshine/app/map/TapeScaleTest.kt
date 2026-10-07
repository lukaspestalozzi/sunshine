package com.sunshine.app.map

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// The time tape's geometry (time-selection spec, "Choose the time of day"; design D3 of polish-ui).
class TapeScaleTest {
    private val day = TapeScale(DECEMBER_21, ZURICH, step = 5)

    @Test
    fun `seventy dp make an hour`() {
        assertEquals(70f, 60 * TapeScale.DP_PER_MINUTE, 1e-4f)
        assertEquals(780f, day.dragged(minutes = 720f, dxDp = -70f), 1e-3f)
        assertEquals(660f, day.dragged(minutes = 720f, dxDp = 70f), 1e-3f)
    }

    @Test
    fun `snapping goes to the nearest step, half up, within the day`() {
        assertEquals(720f, day.snap(722.4f))
        assertEquals(725f, day.snap(722.5f))
        assertEquals(0f, day.snap(-30f))
        assertEquals(1435f, day.snap(1500f))
        assertEquals(1430f, TapeScale(DECEMBER_21, ZURICH, step = 10).snap(1500f))
    }

    @Test
    fun `a tap 36 dp right of the needle at 12 00 selects 12 30`() {
        assertEquals(750f, day.snap(day.minutesAt(offsetDp = 36f, needleMinutes = 720f)))
    }

    @Test
    fun `drags and taps stop at the day's edges`() {
        assertEquals(0f, day.dragged(minutes = 120f, dxDp = 10_000f))
        assertEquals(1435f, day.dragged(minutes = 120f, dxDp = -10_000f))
    }

    @Test
    fun `steps and hour labels of the clock-change days`() {
        val short = TapeScale(LocalDate.of(2025, 3, 30), ZURICH, step = 5)
        val long = TapeScale(LocalDate.of(2025, 10, 26), ZURICH, step = 5)

        assertEquals(276, short.stepCount)
        assertEquals(300, long.stepCount)
        assertEquals(listOf(0, 1) + (3..23).toList(), short.hourTicks().map { it.label })
        assertEquals(listOf(0, 1, 2, 2) + (3..23).toList(), long.hourTicks().map { it.label })
        assertEquals((0 until 25).map { it * 60f }, long.hourTicks().map { it.minutes })
    }

    @Test
    fun `an exact time sits between the steps`() {
        val minutes = sliderMinutes(ZonedDateTime.of(2025, 12, 21, 9, 47, 0, 0, ZURICH))

        assertEquals(2f / 5, (minutes - 585f) / 5f, 1e-4f)
        assertEquals(585f, day.snap(minutes))
    }

    private companion object {
        val ZURICH: ZoneId = ZoneId.of("Europe/Zurich")
        val DECEMBER_21: LocalDate = LocalDate.of(2025, 12, 21)
    }
}
