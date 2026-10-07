package com.sunshine.app.map

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.floor

/**
 * The time tape's scale of [date] in [zone] with steps of [step] minutes (time-selection spec,
 * "Choose the time of day"; design D3 of polish-ui). Positions are minutes since the start of the
 * day on the instant timeline, as [sliderMinutes].
 */
class TapeScale(
    private val date: LocalDate,
    private val zone: ZoneId,
    val step: Int,
) {
    /** Number of steps of the day. */
    val stepCount: Int = sliderPositions(date, zone, step)

    /** The day's last step, in minutes. */
    val lastMinutes: Float = (stepCount - 1f) * step

    /** The nearest step to [minutes], half up, within the day. */
    fun snap(minutes: Float): Float = (floor(minutes / step + HALF) * step).coerceIn(0f, lastMinutes)

    /** [minutes] after a drag of [dxDp] (positive: to the right, towards earlier times), within the day. */
    fun dragged(
        minutes: Float,
        dxDp: Float,
    ): Float = (minutes - dxDp / DP_PER_MINUTE).coerceIn(0f, lastMinutes)

    /** The minutes at [offsetDp] right of the needle, which points at [needleMinutes]. */
    fun minutesAt(
        offsetDp: Float,
        needleMinutes: Float,
    ): Float = needleMinutes + offsetDp / DP_PER_MINUTE

    /** The start of every hour of the day with its wall-clock hour, twice or never around a clock change. */
    fun hourTicks(): List<HourTick> {
        val start = date.atStartOfDay(zone)
        val end = date.plusDays(1).atStartOfDay(zone)
        val hours = Duration.between(start, end).toHours()
        return (0 until hours).map { h ->
            HourTick(h * MINUTES_PER_HOUR, start.plusHours(h).withZoneSameInstant(zone).hour)
        }
    }

    /** An hour's tick at [minutes] with its [label], the wall-clock hour. */
    data class HourTick(
        val minutes: Float,
        val label: Int,
    )

    companion object {
        /** 70 dp per hour (user decision, 2026-10-05). */
        const val DP_PER_MINUTE = 70f / 60f
        private const val MINUTES_PER_HOUR = 60f
        private const val HALF = 0.5f
    }
}
