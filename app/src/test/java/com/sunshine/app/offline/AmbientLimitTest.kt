package com.sunshine.app.offline

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AmbientLimitTest {
    private val limits = mutableListOf<Long>()
    private val limit = AmbientLimit(set = { limits += it })

    @Test
    fun `without regions the limit is 512 MiB`() {
        limit.onRegionBytes(0)

        assertEquals(listOf(512 * MIB), limits)
    }

    @Test
    fun `regions add their size`() {
        limit.onRegionBytes((183 + 40) * MIB)

        assertEquals(listOf(735 * MIB), limits)
    }

    @Test
    fun `during a download the limit is set again after 16 MiB of progress, not before`() {
        limit.onRegionBytes(0)
        limit.onRegionBytes(15 * MIB)
        limit.onRegionBytes(16 * MIB)

        assertEquals(listOf(512 * MIB, 528 * MIB), limits)
    }

    @Test
    fun `a completed or deleted region sets the limit at once`() {
        limit.onRegionBytes(100 * MIB)
        limit.onRegionBytes(95 * MIB, now = true)

        assertEquals(listOf(612 * MIB, 607 * MIB), limits)
    }

    private companion object {
        const val MIB = 1024L * 1024
    }
}
