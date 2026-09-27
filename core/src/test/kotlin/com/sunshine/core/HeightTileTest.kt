package com.sunshine.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class HeightTileTest {
    @ParameterizedTest
    @ValueSource(floats = [-430f, 0f, 568.0f, 1634.43f, 8849f])
    fun `heights round-trip within an eighth of a metre`(metres: Float) {
        val tile = HeightTile.fromMetres(size = 1, metres = floatArrayOf(metres))

        assertEquals(metres.toDouble(), tile.height(0, 0), 0.125)
    }

    @Test
    fun `heights outside the representable range are rejected`() {
        assertThrows<IllegalArgumentException> { HeightTile.fromMetres(1, floatArrayOf(-1001f)) }
        assertThrows<IllegalArgumentException> { HeightTile.fromMetres(1, floatArrayOf(15_400f)) }
    }

    @Test
    fun `NaN is kept as no value`() {
        val tile = HeightTile.fromMetres(size = 2, metres = floatArrayOf(1f, Float.NaN, 3f, 4f))

        assertTrue(tile.height(0, 1).isNaN())
        assertEquals(3.0, tile.height(1, 0), 0.125)
    }

    @Test
    fun `the number of heights must match the size`() {
        assertThrows<IllegalArgumentException> { HeightTile.fromMetres(2, floatArrayOf(1f, 2f, 3f)) }
    }
}
