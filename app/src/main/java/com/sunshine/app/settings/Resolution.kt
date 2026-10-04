package com.sunshine.app.settings

import kotlin.math.abs

/**
 * The cell sizes (dp) and time steps (minutes) of both overlay modes (settings spec, "Shade
 * resolution"; design D3 of add-settings).
 */
data class Resolution(
    val sunShadeCellDp: Int,
    val sunShadeStepMinutes: Int,
    val sunHoursCellDp: Int,
    val sunHoursStepMinutes: Int,
) {
    /** Within the ranges of `Custom resolution`: cells in whole dp, steps the nearest allowed value. */
    fun clamped(): Resolution =
        Resolution(
            sunShadeCellDp = sunShadeCellDp.coerceIn(SUN_SHADE_CELLS),
            sunShadeStepMinutes = nearestStep(sunShadeStepMinutes),
            sunHoursCellDp = sunHoursCellDp.coerceIn(SUN_HOURS_CELLS),
            sunHoursStepMinutes = nearestStep(sunHoursStepMinutes),
        )

    companion object {
        val FAST = Resolution(4, 10, 16, 15)
        val NORMAL = Resolution(2, 5, 8, 10)
        val DETAILED = Resolution(1, 5, 4, 10)

        val SUN_SHADE_CELLS = 1..8
        val SUN_HOURS_CELLS = 4..32

        /** Steps that divide every day, DST days included, from its start. */
        val STEPS = listOf(5, 10, 15, 20, 30)

        private fun nearestStep(minutes: Int): Int = STEPS.minBy { abs(it - minutes) }
    }
}

/** The `Shade resolution` choices; `CUSTOM` uses the values of `Custom resolution`. */
enum class Preset {
    FAST,
    NORMAL,
    DETAILED,
    CUSTOM,
    ;

    fun resolution(custom: Resolution): Resolution =
        when (this) {
            FAST -> Resolution.FAST
            NORMAL -> Resolution.NORMAL
            DETAILED -> Resolution.DETAILED
            CUSTOM -> custom
        }
}
