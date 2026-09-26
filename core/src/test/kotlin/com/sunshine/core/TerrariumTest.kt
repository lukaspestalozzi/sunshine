package com.sunshine.core

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

class TerrariumTest {
    @Test
    fun `terrarium pixels decode to metres`() {
        val argb = intArrayOf(0xFF800000.toInt(), 0xFF823800.toInt(), 0xFF7FFF80.toInt())

        assertArrayEquals(floatArrayOf(0.0f, 568.0f, -0.5f), terrariumHeights(argb))
    }
}
