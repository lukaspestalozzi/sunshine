package com.sunshine.app.map

import com.sunshine.core.MapArea
import com.sunshine.core.SunShadeSweep
import java.time.LocalDate

/**
 * Computed overlay days by visible area, date and cell size (design D14 of add-sun-shade-overlay,
 * D9 of add-sun-exposure-heatmap). [trim] drops the least recently used days while their states
 * take more than [maxBytes].
 */
class DayCache(
    private val maxBytes: Long,
) {
    // Access order: the eldest entry is the least recently used.
    private val days = LinkedHashMap<Triple<MapArea, LocalDate, Double>, DayOverlay>(16, 0.75f, true)

    /** Bytes of all cached days' states. */
    val bytes: Long
        @Synchronized get() = days.values.sumOf { it.bytes }

    /** The day of [area], [date] and [cellDp], which becomes the most recently used, or `null`. */
    @Synchronized
    fun get(
        area: MapArea,
        date: LocalDate,
        cellDp: Double = SunShadeSweep.CELL_DP,
    ): DayOverlay? = days[Triple(area, date, cellDp)]

    @Synchronized
    fun put(day: DayOverlay) {
        days[Triple(day.area, day.date, day.cellDp)] = day
    }

    @Synchronized
    fun remove(day: DayOverlay) {
        days.remove(Triple(day.area, day.date, day.cellDp), day)
    }

    /** Drops the least recently used days while the cache is over budget, never [keep]. */
    @Synchronized
    fun trim(keep: DayOverlay) {
        var total = days.values.sumOf { it.bytes }
        val eldest = days.values.iterator()
        while (total > maxBytes && eldest.hasNext()) {
            val day = eldest.next()
            if (day === keep) continue
            total -= day.bytes
            eldest.remove()
        }
    }
}
