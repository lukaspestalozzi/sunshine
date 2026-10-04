package com.sunshine.app.map

import com.sunshine.core.MapArea
import com.sunshine.core.SunShadeSweep
import java.time.LocalDate

/**
 * Computed overlay days by visible area, date, cell size and step (design D14 of
 * add-sun-shade-overlay, D9 of add-sun-exposure-heatmap, D3 of add-settings), so that the days of
 * another shade resolution stay until dropped. [trim] drops the least recently used days while
 * their states take more than [maxBytes].
 */
class DayCache(
    private val maxBytes: Long,
) {
    // Access order: the eldest entry is the least recently used.
    private val days = LinkedHashMap<Key, DayOverlay>(16, 0.75f, true)

    private data class Key(
        val area: MapArea,
        val date: LocalDate,
        val cellDp: Double,
        val stepMinutes: Int,
    )

    /** Bytes of all cached days' states. */
    val bytes: Long
        @Synchronized get() = days.values.sumOf { it.bytes }

    /** The day of [area], [date], [cellDp] and [stepMinutes], which becomes the most recently used, or `null`. */
    @Synchronized
    fun get(
        area: MapArea,
        date: LocalDate,
        cellDp: Double = SunShadeSweep.CELL_DP,
        stepMinutes: Int = SLIDER_STEP_MINUTES,
    ): DayOverlay? = days[Key(area, date, cellDp, stepMinutes)]

    @Synchronized
    fun put(day: DayOverlay) {
        days[day.key()] = day
    }

    @Synchronized
    fun remove(day: DayOverlay) {
        days.remove(day.key(), day)
    }

    private fun DayOverlay.key() = Key(area, date, cellDp, stepMinutes)

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
