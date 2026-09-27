package com.sunshine.core

import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// Packed cell states (design D13 of add-sun-shade-overlay): 2 bits per cell.
class ShadeGridPackingTest {
    private val sweep = SunShadeSweep(MapArea(GeoPoint(46.6, 7.9), 12.0, 400.0, 850.0), SunPosition(180.0, 20.0, true))
    private val random = Random(5)
    private val states =
        Array(sweep.lineCount) { k -> ByteArray(sweep.lineCells[k]) { STATES[random.nextInt(STATES.size)] } }

    @Test
    fun `a phone screen of cells takes at most 23 KB`() {
        val grid = sweep.assemble(listOf(ShadeGridPart(0 until sweep.lineCount, states)))
        val cells = states.sumOf { it.size }

        assertTrue(cells in 80_000..100_000, "$cells cells")
        assertTrue(grid.stateBytes <= 23_000, "${grid.stateBytes} bytes")
    }

    @Test
    fun `every state comes back unchanged`() {
        val grid = sweep.assemble(listOf(ShadeGridPart(0 until sweep.lineCount, states)))

        for (k in states.indices) {
            for (j in states[k].indices) {
                val expected =
                    when (states[k][j]) {
                        SunShadeSweep.SUN -> Sunshine.SUN
                        SunShadeSweep.SHADE -> Sunshine.SHADE
                        else -> Sunshine.UNKNOWN
                    }
                assertEquals(expected, grid.cellState(k, j), "line $k cell $j")
            }
        }
        assertEquals(states.any { line -> line.any { it == SunShadeSweep.UNKNOWN } }, grid.hasUnknown)
    }

    private companion object {
        val STATES = byteArrayOf(SunShadeSweep.SUN, SunShadeSweep.SHADE, SunShadeSweep.UNKNOWN)
    }
}
