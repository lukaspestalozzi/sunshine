package com.sunshine.app.offline

import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RegionProgressTest {
    @Test
    fun `progress is the share of obtained tiles, rounded down`() {
        assertEquals(61, regionProgress(obtained = 4775, total = 7819, complete = false))
    }

    @Test
    fun `progress shows 100 only when the region is complete`() {
        // The map part is complete and one DEM tile of 413 is still missing.
        assertEquals(99, regionProgress(obtained = 7818, total = 7819, complete = false))
        assertEquals(100, regionProgress(obtained = 7819, total = 7819, complete = true))
    }

    @Test
    fun `progress before anything is known is 0`() {
        assertEquals(0, regionProgress(obtained = 0, total = 0, complete = false))
    }

    @Test
    fun `status lines of the region list`() {
        assertEquals("Downloading 63 %", formatRegionStatus(RegionStatus.Downloading(63)))
        assertEquals("Waiting", formatRegionStatus(RegionStatus.Waiting))
        assertEquals("Incomplete (63 %) · waiting for network", formatRegionStatus(RegionStatus.WaitingForNetwork(63)))
        assertEquals("Incomplete (40 %) · waiting for storage", formatRegionStatus(RegionStatus.WaitingForStorage(40)))
        assertEquals("2026-10-02 · 183 MiB", formatRegionStatus(RegionStatus.Complete(LocalDate.of(2026, 10, 2), mib(183.4))))
    }

    @Test
    fun `sizes are rounded half up to whole MiB`() {
        assertEquals("183 MiB", formatMebibytes(mib(183.4)))
        assertEquals("184 MiB", formatMebibytes(mib(183.5)))
        assertEquals("281 MiB", formatMebibytes(mib(280.6)))
    }

    private fun mib(value: Double) = (value * 1024 * 1024).toLong()
}
