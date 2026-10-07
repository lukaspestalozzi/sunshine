package com.sunshine.app.map

import com.sunshine.core.GeoPoint
import com.sunshine.core.HorizonProfile
import com.sunshine.core.SUN_UPPER_LIMB
import com.sunshine.core.Sunshine
import com.sunshine.core.sunPosition
import com.sunshine.core.sunshineAt
import java.time.ZonedDateTime
import kotlin.math.floor

/** The state of one step of the time tape's strip (time-selection spec, "Time tape strip"; design D5 of polish-ui). */
enum class StripState { NIGHT, SUN, SHADE, UNKNOWN, NOT_COMPUTED }

/**
 * The strip's [states] and its source's progress in whole [percent] (time-selection spec, "Time tape
 * strip"; design D5 of polish-ui): a day's computed share, rounded down, or the horizon's 0 or 100.
 */
data class TapeStrip(
    val states: List<StripState>,
    val percent: Int,
)

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

/** For each of [times], whether the sun's upper edge at [point] is at or below the astronomical horizon. */
fun nightSteps(
    point: GeoPoint,
    times: List<ZonedDateTime>,
): BooleanArray = BooleanArray(times.size) { sunPosition(point, times[it].toInstant()).elevation + SUN_UPPER_LIMB <= 0.0 }

/** The tracer's state at [point] and [time] from its horizon [profile]; unknown without a ground height (`null`). */
fun tracerState(
    profile: HorizonProfile?,
    point: GeoPoint,
    time: ZonedDateTime,
): Sunshine = profile?.let { sunshineAt(it, point, time.toInstant()) } ?: Sunshine.UNKNOWN

/**
 * The state of [day]'s cell at [point] at the tape's step at [tapeMinutes], from the day's step at or
 * before it; `null` while that step is not computed.
 */
fun dayState(
    day: DayOverlay,
    point: GeoPoint,
    tapeMinutes: Float,
): Sunshine? =
    day.steps
        .getOrNull(sourceStep(tapeMinutes, day.stepMinutes))
        ?.let(day::gridAt)
        ?.stateAt(point)
