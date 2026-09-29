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
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The overlays of [area] at every step of [date] (design D11 of add-sun-shade-overlay), in cells of
 * [cellDp] dp every [stepMinutes] minutes: 2 dp and 5 minutes for `Sun & shade`, 8 dp and 10 minutes
 * for the heatmap (design D9 of add-sun-exposure-heatmap). [compute] gives the selected time on the
 * caller's dispatcher; [computeRest] then fills in the other steps on [background], nearest to the
 * selected time first. One grid is computed at a time: a selected time waiting in [compute] goes
 * before the next background step.
 */
class DayOverlay(
    val area: MapArea,
    val date: LocalDate,
    zone: ZoneId,
    private val grid: suspend (MapArea, SunPosition, Double) -> ShadeGrid,
    private val background: CoroutineDispatcher,
    val stepMinutes: Int = SLIDER_STEP_MINUTES,
    val cellDp: Double = SunShadeSweep.CELL_DP,
) {
    /** Every [stepMinutes] minutes over the day's real length: the slider positions for 5 minutes. */
    val steps: List<ZonedDateTime> =
        List((sliderPositions(date, zone) * SLIDER_STEP_MINUTES + stepMinutes - 1) / stepMinutes) {
            sliderTime(date, zone, it * stepMinutes.toFloat())
        }

    private val nightInstants: Set<Instant> = steps.filter { SunShadeSweep.isNight(sunAt(it)) }.mapTo(HashSet()) { it.toInstant() }

    /** Steps whose grid needs no terrain work, as the sun is below every horizon (design D12). */
    val nightSteps: Int = nightInstants.size

    private val stepInstants: Set<Instant> = steps.mapTo(HashSet()) { it.toInstant() }

    private val grids = ConcurrentHashMap<Instant, ShadeGrid>()

    private val mutableComputed = MutableStateFlow(0)

    /** Steps computed so far; an off-grid selected time does not count (design D11). */
    val computed: StateFlow<Int> = mutableComputed.asStateFlow()

    // Grids stored so far, off-grid times included: what computeRest waits on.
    private val stored = MutableStateFlow(0)

    private val mutableBytes = AtomicLong()

    /** Bytes of the stored grids' states (design D14) and of the sun hours once counted. */
    val bytes: Long get() = mutableBytes.get() + (counted?.bytes ?: 0L)

    /** The day's sun hours once [sunHours] has counted them (design D2 of add-sun-exposure-heatmap). */
    @Volatile
    var counted: SunHours? = null
        private set

    private val counting = Mutex()

    /** Whether some stored grid has unknown cells (design D14). */
    @Volatile
    var hasUnknown: Boolean = false
        private set

    // Fair, so a selected time waiting for it comes before the next background step.
    private val lock = Mutex()

    /** Whether the sun is below every horizon at [step], so that its grid needs no terrain work (design D12). */
    fun isNight(step: ZonedDateTime): Boolean = step.toInstant() in nightInstants

    /**
     * The day's sun hours, counted once on [background] and then kept (design D2 of
     * add-sun-exposure-heatmap). Every step must be computed.
     */
    suspend fun sunHours(): SunHours =
        counting.withLock {
            counted ?: withContext(background) { countSunHours(this@DayOverlay) }.also { counted = it }
        }

    /** The grid at [time] if it has been computed. */
    fun gridAt(time: ZonedDateTime): ShadeGrid? = grids[time.toInstant()]

    /** The grid at [time], computed on the caller's dispatcher unless it is known. */
    suspend fun compute(time: ZonedDateTime): ShadeGrid =
        lock.withLock {
            gridAt(time)
                ?: grid(area, sunAt(time), cellDp).also { store(time, it) }
        }

    /**
     * Computes the remaining steps on [background], nearest to [selected] first; returns when all
     * are known. It starts once the selected time is known, also when a cached day resumes (D14).
     */
    suspend fun computeRest(selected: () -> ZonedDateTime) {
        stored.first { gridAt(selected()) != null }
        computeAll(selected)
    }

    /**
     * Computes every missing step on [background], nearest to [selected] first, without waiting for
     * the selected time: the heatmap's day shows no single time (design D9 of add-sun-exposure-heatmap).
     */
    suspend fun computeAll(selected: () -> ZonedDateTime) {
        while (true) {
            val next = nearestUncomputed(selected()) ?: return
            lock.withLock {
                if (gridAt(next) == null) store(next, withContext(background) { grid(area, sunAt(next), cellDp) })
            }
        }
    }

    private fun store(
        time: ZonedDateTime,
        computed: ShadeGrid,
    ) {
        grids[time.toInstant()] = computed
        mutableBytes.addAndGet(computed.stateBytes.toLong())
        if (computed.hasUnknown) hasUnknown = true
        if (time.toInstant() in stepInstants) mutableComputed.update { it + 1 }
        stored.update { it + 1 }
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
