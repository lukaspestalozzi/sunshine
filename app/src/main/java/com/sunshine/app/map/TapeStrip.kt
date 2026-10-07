package com.sunshine.app.map

import com.sunshine.core.Sunshine
import kotlin.math.floor

/** The state of one step of the time tape's strip (time-selection spec, "Time tape strip"; design D5 of polish-ui). */
enum class StripState { NIGHT, SUN, SHADE, UNKNOWN, NOT_COMPUTED }

/**
 * The strip's states of the tape's steps: night where [night] says so, whatever the source; else
 * the source's [state] of the step, `null` while it is not computed yet.
 */
fun tapeStrip(
    night: BooleanArray,
    state: (step: Int) -> Sunshine?,
): List<StripState> =
    List(night.size) { step ->
        if (night[step]) {
            StripState.NIGHT
        } else {
            when (state(step)) {
                Sunshine.SUN -> StripState.SUN
                Sunshine.SHADE -> StripState.SHADE
                Sunshine.UNKNOWN -> StripState.UNKNOWN
                null -> StripState.NOT_COMPUTED
            }
        }
    }

/** The index of the source's step of [sourceStepMinutes] at or before [tapeMinutes], e.g. the heatmap's 10-minute day. */
fun sourceStep(
    tapeMinutes: Float,
    sourceStepMinutes: Int,
): Int = floor(tapeMinutes / sourceStepMinutes).toInt()
