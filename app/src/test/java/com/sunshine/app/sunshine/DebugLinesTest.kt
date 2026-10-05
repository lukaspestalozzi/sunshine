package com.sunshine.app.sunshine

import com.sunshine.app.settings.DebugSwitches
import kotlin.time.Duration.Companion.milliseconds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// The lines of the debug box (settings spec, "Debug info"; design D4 of polish-overlay).
class DebugLinesTest {
    @Test
    fun `before a first value every line shows a dash`() {
        val lines = debugLines(DebugValues(), ALL)

        assertEquals(
            listOf(
                "Horizon –",
                "Sun periods –",
                "Grid –",
                "Day –",
                "Sun hours –",
                "Grid tiles –",
                "Horizon tiles –",
                "Memory –",
                "Day –",
                "Area –",
                "Shown –",
                "Cache –",
                "Agreement –",
            ),
            lines,
        )
    }

    @Test
    fun `values are formatted, durations in ms below 10 s and in s with one decimal from 10 s`() {
        val values =
            DebugValues(
                horizon = HorizonTiming(total = 412.milliseconds, tiles = 380.milliseconds),
                sunPeriods = 12.milliseconds,
                grid = GridTiming(grid = 9_999.milliseconds, image = 21.milliseconds),
                day = DayTiming(steps = 288, nightSteps = 96, total = 41_250.milliseconds),
                sunHours = GridTiming(grid = 812.milliseconds, image = 30.milliseconds),
                gridTiles = TileSources(kept = 40, memory = 10, disk = 9, network = 4, unavailable = 1),
                horizonTiles = TileSources(kept = 0, memory = 50, disk = 2, network = 0, unavailable = 3),
                memoryTiles = MemoryTiles(held = 52, max = 64),
                dayState =
                    DayState(
                        computed = 72,
                        steps = 288,
                        cellDp = 2.0,
                        stepMinutes = 5,
                        widthDp = 400.0,
                        heightDp = 850.0,
                        zoom = 12.04,
                    ),
                shown = ShownSource.EARLIER_DAY,
                cache = CacheState(days = 3, bytes = 41L * MIB + MIB / 2, maxBytes = 96L * MIB),
                agreement = Agreement.Result(agree = 199, checked = 200),
            )

        assertEquals(
            listOf(
                "Horizon 412 ms (tiles 380 ms)",
                "Sun periods 12 ms",
                "Grid 9999 ms, image 21 ms",
                "Day 288 steps (96 night) 41.3 s",
                "Sun hours 812 ms, image 30 ms",
                "Grid tiles 64: 40 kept, 10 memory, 9 disk, 4 network, 1 unavailable",
                "Horizon tiles 55: 50 memory, 2 disk, 0 network, 3 unavailable",
                "Memory 52 of 64 tiles",
                "Day 72/288 steps, 2 dp / 5 min",
                "Area 400×850 dp, zoom 12.0",
                "Shown earlier day",
                "Cache 3 days, 42 of 96 MiB",
                "Agreement 199 of 200 cells (99 %)",
            ),
            debugLines(values, ALL),
        )
    }

    @Test
    fun `only the groups switched on are shown`() {
        val lines = debugLines(DebugValues(), DebugSwitches(tiles = true))

        assertEquals(listOf("Grid tiles –", "Horizon tiles –", "Memory –"), lines)
    }

    @Test
    fun `the sources of the shown overlay and a running check`() {
        val own = debugLines(DebugValues(shown = ShownSource.OWN_DAY), DebugSwitches(dayState = true))
        val previous = debugLines(DebugValues(shown = ShownSource.PREVIOUS_OVERLAY), DebugSwitches(dayState = true))
        val checking = debugLines(DebugValues(agreement = Agreement.Checking), DebugSwitches(agreementCheck = true))

        assertEquals("Shown own day", own[2])
        assertEquals("Shown previous overlay", previous[2])
        assertEquals(listOf("Agreement …"), checking)
    }

    @Test
    fun `no line while every switch is off`() {
        assertEquals(emptyList<String>(), debugLines(DebugValues(), DebugSwitches()))
    }

    @Test
    fun `the collector keeps the latest value of each kind`() {
        val info = DebugInfo()

        info.update { it.copy(sunPeriods = 5.milliseconds) }
        info.update { it.copy(sunPeriods = 7.milliseconds, shown = ShownSource.OWN_DAY) }

        assertEquals(DebugValues(sunPeriods = 7.milliseconds, shown = ShownSource.OWN_DAY), info.values.value)
    }

    private companion object {
        val ALL = DebugSwitches(timings = true, tiles = true, dayState = true, agreementCheck = true)
        const val MIB = 1024L * 1024
    }
}
