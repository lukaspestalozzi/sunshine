package com.sunshine.app.map

import com.sunshine.core.MapArea
import com.sunshine.core.ShadeGrid
import com.sunshine.core.SunPosition
import com.sunshine.core.SunShadeSweep
import com.sunshine.core.sunPosition
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The overlays of [area] at every slider position of [date] (design D11 of add-sun-shade-overlay).
 * [compute] gives the selected time on the caller's dispatcher; [computeRest] then fills in the
 * other steps on [background], nearest to the selected time first. One grid is computed at a time:
 * a selected time waiting in [compute] goes before the next background step.
 */
class DayOverlay(
    val area: MapArea,
    val date: LocalDate,
    zone: ZoneId,
    private val grid: suspend (MapArea, SunPosition) -> ShadeGrid,
    private val background: CoroutineDispatcher,
) {
    /** Every slider position of [date]: 5-minute steps over the day's real length. */
    val steps: List<ZonedDateTime> = List(sliderPositions(date, zone)) { sliderTime(date, zone, it * SLIDER_STEP_MINUTES.toFloat()) }

    /** Steps whose grid needs no terrain work, as the sun is below every horizon (design D12). */
    val nightSteps: Int = steps.count { SunShadeSweep.isNight(sunAt(it)) }

    private val stepInstants: Set<Instant> = steps.mapTo(HashSet()) { it.toInstant() }

    private val grids = ConcurrentHashMap<Instant, ShadeGrid>()

    private val mutableComputed = MutableStateFlow(0)

    /** Steps computed so far; an off-grid selected time does not count (design D11). */
    val computed: StateFlow<Int> = mutableComputed.asStateFlow()

    // Fair, so a selected time waiting for it comes before the next background step.
    private val lock = Mutex()

    // The background waits for the first selected time.
    private val started = CompletableDeferred<Unit>()

    /** The grid at [time] if it has been computed. */
    fun gridAt(time: ZonedDateTime): ShadeGrid? = grids[time.toInstant()]

    /** The grid at [time], computed on the caller's dispatcher unless it is known. */
    suspend fun compute(time: ZonedDateTime): ShadeGrid {
        val known = lock.withLock { gridAt(time) ?: grid(area, sunAt(time)).also { store(time, it) } }
        started.complete(Unit)
        return known
    }

    /** Computes the remaining steps on [background], nearest to [selected] first; returns when all are known. */
    suspend fun computeRest(selected: () -> ZonedDateTime) {
        started.await()
        while (true) {
            val next = nearestUncomputed(selected()) ?: return
            lock.withLock {
                if (gridAt(next) == null) store(next, withContext(background) { grid(area, sunAt(next)) })
            }
        }
    }

    private fun store(
        time: ZonedDateTime,
        computed: ShadeGrid,
    ) {
        grids[time.toInstant()] = computed
        if (time.toInstant() in stepInstants) mutableComputed.update { it + 1 }
    }

    // Ties go to the later step, so the order alternates later and earlier.
    private fun nearestUncomputed(time: ZonedDateTime): ZonedDateTime? =
        steps
            .filter { gridAt(it) == null }
            .minWithOrNull(compareBy({ abs(Duration.between(time, it).toMillis()) }, { it.isBefore(time) }))

    private fun sunAt(time: ZonedDateTime): SunPosition = sunPosition(area.center, time.toInstant())
}

/** The dispatcher of the day's background steps: half the cores, at least one (user decision, design D11). */
fun dayDispatcher(cores: Int = Runtime.getRuntime().availableProcessors()): CoroutineDispatcher =
    Dispatchers.Default.limitedParallelism(max(1, cores / 2))
