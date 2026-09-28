package com.sunshine.app.map

import com.sunshine.core.MapArea
import java.time.LocalDate

/**
 * Computed overlay days by visible area and date (design D14 of add-sun-shade-overlay). [trim]
 * drops the least recently used days while their states take more than [maxBytes].
 */
class DayCache(
    private val maxBytes: Long,
) {
    // Access order: the eldest entry is the least recently used.
    private val days = LinkedHashMap<Pair<MapArea, LocalDate>, DayOverlay>(16, 0.75f, true)

    /** Bytes of all cached days' states. */
    val bytes: Long
        @Synchronized get() = days.values.sumOf { it.bytes }

    /** The day of [area] and [date], which becomes the most recently used, or `null`. */
    @Synchronized
    fun get(
        area: MapArea,
        date: LocalDate,
    ): DayOverlay? = days[area to date]

    @Synchronized
    fun put(day: DayOverlay) {
        days[day.area to day.date] = day
    }

    @Synchronized
    fun remove(day: DayOverlay) {
        days.remove(day.area to day.date, day)
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
