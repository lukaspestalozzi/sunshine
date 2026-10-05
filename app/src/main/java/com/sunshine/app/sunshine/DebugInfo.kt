package com.sunshine.app.sunshine

import com.sunshine.app.settings.DebugSwitches
import java.util.Locale
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A horizon profile's time in total and for loading its tiles. */
data class HorizonTiming(
    val total: Duration,
    val tiles: Duration,
)

/** A grid's (or a heatmap's counting pass's) time and its image's. */
data class GridTiming(
    val grid: Duration,
    val image: Duration,
)

/** A day of either overlay mode computed to its end. */
data class DayTiming(
    val steps: Int,
    val nightSteps: Int,
    val total: Duration,
)

/** The DEM tiles of one computation by where they came from. */
data class TileSources(
    val kept: Int,
    val memory: Int,
    val disk: Int,
    val network: Int,
    val unavailable: Int,
) {
    val total: Int get() = kept + memory + disk + network + unavailable
}

/** The DEM tiles held in memory, of at most [max]. */
data class MemoryTiles(
    val held: Int,
    val max: Int,
)

/** The day of the shown overlay mode. */
data class DayState(
    val computed: Int,
    val steps: Int,
    val cellDp: Double,
    val stepMinutes: Int,
    val widthDp: Double,
    val heightDp: Double,
    val zoom: Double,
)

/** Where the shown `Sun & shade` overlay comes from (sun-shade-overlay spec, "Overlay updates"). */
enum class ShownSource(
    val label: String,
) {
    OWN_DAY("own day"),
    EARLIER_DAY("earlier day"),
    PREVIOUS_OVERLAY("previous overlay"),
}

/** The cache of days. */
data class CacheState(
    val days: Int,
    val bytes: Long,
    val maxBytes: Long,
)

/** The agreement check of the shown overlay against the point tracer. */
sealed interface Agreement {
    data object Checking : Agreement

    data class Result(
        val agree: Int,
        val checked: Int,
    ) : Agreement
}

/** The latest value of each kind shown by the debug box (settings spec, "Debug info"; design D4 of polish-overlay). */
data class DebugValues(
    val horizon: HorizonTiming? = null,
    val sunPeriods: Duration? = null,
    val grid: GridTiming? = null,
    val day: DayTiming? = null,
    val sunHours: GridTiming? = null,
    val gridTiles: TileSources? = null,
    val horizonTiles: TileSources? = null,
    val memoryTiles: MemoryTiles? = null,
    val dayState: DayState? = null,
    val shown: ShownSource? = null,
    val cache: CacheState? = null,
    val agreement: Agreement? = null,
)

/** Collects the debug values where they arise; recording is one state update, whether or not the box is shown. */
class DebugInfo {
    private val mutableValues = MutableStateFlow(DebugValues())
    val values: StateFlow<DebugValues> = mutableValues.asStateFlow()

    fun update(change: (DebugValues) -> DebugValues) = mutableValues.update(change)
}

/** The debug box's lines for the groups switched on in [switches], in the order of the settings spec. */
fun debugLines(
    values: DebugValues,
    switches: DebugSwitches,
): List<String> =
    buildList {
        if (switches.timings) {
            add("Horizon " + (values.horizon?.let { "${duration(it.total)} (tiles ${duration(it.tiles)})" } ?: NONE))
            add("Sun periods " + (values.sunPeriods?.let(::duration) ?: NONE))
            add("Grid " + (values.grid?.let { "${duration(it.grid)}, image ${duration(it.image)}" } ?: NONE))
            add("Day " + (values.day?.let { "${it.steps} steps (${it.nightSteps} night) ${duration(it.total)}" } ?: NONE))
            add("Sun hours " + (values.sunHours?.let { "${duration(it.grid)}, image ${duration(it.image)}" } ?: NONE))
        }
        if (switches.tiles) {
            add(
                "Grid tiles " +
                    (
                        values.gridTiles?.let {
                            "${it.total}: ${it.kept} kept, ${it.memory} memory, ${it.disk} disk, ${it.network} network, " +
                                "${it.unavailable} unavailable"
                        } ?: NONE
                    ),
            )
            add(
                "Horizon tiles " +
                    (
                        values.horizonTiles?.let {
                            "${it.total}: ${it.memory} memory, ${it.disk} disk, ${it.network} network, ${it.unavailable} unavailable"
                        } ?: NONE
                    ),
            )
            add("Memory " + (values.memoryTiles?.let { "${it.held} of ${it.max} tiles" } ?: NONE))
        }
        if (switches.dayState) {
            add(
                "Day " +
                    (values.dayState?.let { "${it.computed}/${it.steps} steps, ${number(it.cellDp)} dp / ${it.stepMinutes} min" } ?: NONE),
            )
            add(
                "Area " +
                    (
                        values.dayState?.let {
                            "${it.widthDp.roundToLong()}×${it.heightDp.roundToLong()} dp, zoom ${String.format(
                                Locale.ROOT,
                                "%.1f",
                                it.zoom,
                            )}"
                        } ?: NONE
                    ),
            )
            add("Shown " + (values.shown?.label ?: NONE))
            add("Cache " + (values.cache?.let { "${it.days} days, ${mebibytes(it.bytes)} of ${mebibytes(it.maxBytes)} MiB" } ?: NONE))
        }
        if (switches.agreementCheck) {
            add(
                "Agreement " +
                    when (val agreement = values.agreement) {
                        null -> NONE
                        Agreement.Checking -> "…"
                        is Agreement.Result ->
                            "${agreement.agree} of ${agreement.checked} cells " +
                                "(${if (agreement.checked > 0) agreement.agree * PERCENT / agreement.checked else 0} %)"
                    },
            )
        }
    }

// Whole milliseconds below 10 s, then seconds with one decimal (settings spec, "Debug info").
private fun duration(duration: Duration): String {
    val millis = duration.inWholeMilliseconds
    return if (millis < SECONDS_FROM_MILLIS) "$millis ms" else String.format(Locale.ROOT, "%.1f s", millis / MILLIS_PER_SECOND)
}

private fun number(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

private fun mebibytes(bytes: Long): Long = (bytes.toDouble() / MEBIBYTE).roundToLong()

private const val NONE = "–"
private const val PERCENT = 100
private const val SECONDS_FROM_MILLIS = 10_000L
private const val MILLIS_PER_SECOND = 1000.0
private const val MEBIBYTE = 1024.0 * 1024
