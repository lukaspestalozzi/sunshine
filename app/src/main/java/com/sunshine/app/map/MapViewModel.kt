package com.sunshine.app.map

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sunshine.app.elevation.Elevation
import com.sunshine.app.elevation.ElevationRepository
import com.sunshine.app.settings.LastView
import com.sunshine.app.settings.Resolution
import com.sunshine.app.settings.Settings
import com.sunshine.app.settings.StartAt
import com.sunshine.core.DEFAULT_LOCATION
import com.sunshine.core.GeoPoint
import com.sunshine.core.HorizonProfile
import com.sunshine.core.MapArea
import com.sunshine.core.ShadeGrid
import com.sunshine.core.SunDay
import com.sunshine.core.SunPeriods
import com.sunshine.core.SunPosition
import com.sunshine.core.SunShadeSweep
import com.sunshine.core.Sunshine
import com.sunshine.core.sunDay
import com.sunshine.core.sunPeriods
import com.sunshine.core.sunPosition
import com.sunshine.core.sunshineAt
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random
import kotlin.time.measureTime
import kotlin.time.measureTimedValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CameraState(
    val center: GeoPoint,
    val zoom: Double,
)

/** The sun at the selected location: its position at the selected time and the selected day's events. */
data class SunInfo(
    val position: SunPosition,
    val day: SunDay,
)

/** Elevation of the selected location as shown on screen. */
sealed interface ElevationState {
    data object Loading : ElevationState

    data class Known(
        val metres: Double,
    ) : ElevationState

    data object Unknown : ElevationState
}

/** Terrain-aware sunshine at the selected location as shown on screen (point-sunshine spec). */
sealed interface SunshineUiState {
    /** The horizon is being computed. */
    data object Loading : SunshineUiState

    /** At [point]: the sun periods of the selected day and the sunshine state at the selected time. */
    data class Ready(
        val point: GeoPoint,
        val periods: SunPeriods,
        val atSelectedTime: Sunshine,
    ) : SunshineUiState

    /**
     * This state if it belongs to [center], else [Loading]: right after a camera move, the state may
     * still be the previous location's until the new computation starts (point-sunshine spec).
     */
    fun at(center: GeoPoint): SunshineUiState = if (this is Ready && point != center) Loading else this
}

/** The sun-shade overlay as shown on screen (sun-shade-overlay spec). */
sealed interface OverlayUiState {
    /** Switched off: nothing is computed. */
    data object Off : OverlayUiState

    /** Switched on below zoom [MIN_OVERLAY_ZOOM]: not shown. */
    data object ZoomedOut : OverlayUiState

    /**
     * A new grid is being computed. [kept] is the previous overlay, shown until the new one is ready,
     * also after a change of time or date (then with a notice, as it belongs to another time).
     */
    data class Computing(
        val kept: Ready?,
        /** The kept overlay has another cell size than the one being computed (a new shade resolution). */
        val resolutionChanged: Boolean = false,
    ) : OverlayUiState

    /**
     * The grid at [time] in cells of [cellDp], and its [image] (rendered off the main thread): of
     * the visible area, or of an earlier day's area while the visible area's day lacks [time]
     * ([source]; design D2 of polish-overlay).
     */
    data class Ready(
        val grid: ShadeGrid,
        val time: ZonedDateTime,
        val image: OverlayImage,
        val cellDp: Double = SunShadeSweep.CELL_DP,
        val source: OverlaySource = OverlaySource.OWN_DAY,
    ) : OverlayUiState
}

/** Where a shown `Sun & shade` overlay comes from (design D2 of polish-overlay). */
enum class OverlaySource { OWN_DAY, EARLIER_DAY }

/** The heatmap of the selected day as shown on screen (sun-exposure-heatmap spec). */
sealed interface HeatmapUiState {
    /** Not in the mode `Sun hours`, or the overlay is off. */
    data object Off : HeatmapUiState

    /** In the mode `Sun hours` below zoom [MIN_OVERLAY_ZOOM]: not shown. */
    data object ZoomedOut : HeatmapUiState

    /**
     * The day of the visible area and selected date is not complete or not counted yet. [kept] is the
     * previous heatmap, shown on its own area until the new one is ready.
     */
    data class Computing(
        val kept: Ready?,
    ) : HeatmapUiState

    /** The sun hours of [date] over their area, their [bands] and [image] (rendered off the main thread). */
    class Ready(
        val hours: SunHours,
        val date: LocalDate,
        val bands: HeatmapBands,
        val image: OverlayImage,
    ) : HeatmapUiState
}

/** What the overlay shows (sun-exposure-heatmap spec, "Overlay mode"; design D1 of add-sun-exposure-heatmap). */
enum class OverlayMode {
    /** Sun and shade at the selected time. */
    SUN_AND_SHADE,

    /** Hours of sun on the selected day. */
    SUN_HOURS,
}

/** An option of the three-way overlay toggle (sun-shade-overlay spec, "Overlay toggle"; design D7 of add-sun-exposure-heatmap). */
enum class OverlayOption {
    OFF,
    SUN_AND_SHADE,
    SUN_HOURS,
}

/** The toggle's option for the overlay switched on or off and its [mode]. */
fun overlayOption(
    isOn: Boolean,
    mode: OverlayMode,
): OverlayOption =
    when {
        !isOn -> OverlayOption.OFF
        mode == OverlayMode.SUN_AND_SHADE -> OverlayOption.SUN_AND_SHADE
        else -> OverlayOption.SUN_HOURS
    }

/** Lowest map zoom with an overlay (user decision, design D8 of add-sun-shade-overlay). */
const val MIN_OVERLAY_ZOOM = 11.0

/**
 * State of the map screen. Times are in the zone of [clock] as it is when the view model is created
 * (the device time zone in production).
 */
class MapViewModel(
    private val savedState: SavedStateHandle,
    isOnline: Flow<Boolean>,
    private val clock: Clock,
    private val elevationRepository: ElevationRepository,
    private val horizonProfile: suspend (GeoPoint) -> HorizonProfile?,
    private val overlayGrid: suspend (MapArea, SunPosition, Double) -> ShadeGrid,
    computeDispatcher: CoroutineDispatcher,
    private val dayDispatcher: CoroutineDispatcher? = null,
    private val dayCache: DayCache = DayCache(DEFAULT_DAY_CACHE_BYTES),
    private val log: (String) -> Unit = {},
    checkOverlayAgreement: Boolean = false,
    /** The stored settings (settings spec, "Stored settings"). */
    val settings: StateFlow<Settings> = MutableStateFlow(Settings()),
    /** Stores the map's view when the app goes to the background (map-view spec, "Default viewport"). */
    private val saveLastView: suspend (LastView) -> Unit = {},
) : ViewModel() {
    private val zone: ZoneId = clock.zone

    private val mutableCamera = MutableStateFlow(restoreCamera())
    val camera: StateFlow<CameraState> = mutableCamera.asStateFlow()

    private val mutableSelectedTime = MutableStateFlow(restoreSelectedTime())
    val selectedTime: StateFlow<ZonedDateTime> = mutableSelectedTime.asStateFlow()

    // Off the main thread; conflate() drops inputs that arrive while a computation runs, so a fast
    // pan never queues work. `null` only before the first result.
    val sun: StateFlow<SunInfo?> =
        combine(camera, selectedTime) { camera, time -> camera.center to time }
            .conflate()
            .map { (point, time) -> SunInfo(sunPosition(point, time.toInstant()), sunDay(point, time.toLocalDate(), zone)) }
            .flowOn(computeDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), initialValue = null)

    // Re-evaluated when the location changes (not on zoom alone) or the network returns, which may
    // load an unknown elevation. Latest wins: a new input cancels the previous lookup, including its
    // HTTP call, and waits for it to stop, so a previous location's value is never shown for the new
    // one. Hand-written instead of mapLatest, which is experimental.
    val elevation: StateFlow<ElevationState> =
        channelFlow {
            var lookup: Job? = null
            combine(camera.map { it.center }.distinctUntilChanged(), isOnline) { point, _ -> point }
                .collect { point ->
                    lookup?.cancelAndJoin()
                    lookup =
                        launch {
                            val cached = elevationRepository.cachedElevation(point)
                            if (cached != null) {
                                send(cached.toState())
                            } else {
                                send(ElevationState.Loading)
                                send(elevationRepository.elevation(point).toState())
                            }
                        }
                }
        }.flowOn(computeDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), initialValue = ElevationState.Loading)

    // The horizon of the camera centre, recomputed when the centre changes or the network returns
    // after an incomplete result. Latest wins, as for elevation. The camera must rest for
    // [SETTLE_MILLIS] first, so that panning starts no downloads (design D8).
    private val horizon: Flow<HorizonState> =
        channelFlow {
            var lookup: Job? = null
            val done = AtomicReference<HorizonState.Computed?>(null)
            combine(camera.map { it.center }.distinctUntilChanged(), isOnline) { point, online -> point to online }
                .collect { (point, online) ->
                    val previous = done.get()
                    if (previous?.point == point && (!online || previous.isComplete)) return@collect
                    lookup?.cancelAndJoin()
                    done.set(null)
                    lookup =
                        launch {
                            send(HorizonState.Loading)
                            delay(SETTLE_MILLIS)
                            val computed = HorizonState.Computed(point, horizonProfile(point))
                            done.set(computed)
                            send(computed)
                        }
                }
        }

    // Periods depend on the date only, so a new time of day costs one sun position (design D8).
    private var periodsOfDay: Triple<HorizonState.Computed, LocalDate, SunPeriods>? = null

    val sunshine: StateFlow<SunshineUiState> =
        combine(horizon, selectedTime) { horizon, time -> horizon to time }
            .conflate()
            .map { (horizon, time) -> if (horizon is HorizonState.Computed) sunshineState(horizon, time) else SunshineUiState.Loading }
            .flowOn(computeDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), initialValue = SunshineUiState.Loading)

    private val mutableOverlayOn = MutableStateFlow(savedState.get<Boolean>(KEY_OVERLAY_ON) ?: false)
    val isOverlayOn: StateFlow<Boolean> = mutableOverlayOn.asStateFlow()

    private val mutableOverlayMode =
        MutableStateFlow(savedState.get<String>(KEY_OVERLAY_MODE)?.let(OverlayMode::valueOf) ?: OverlayMode.SUN_AND_SHADE)
    val overlayMode: StateFlow<OverlayMode> = mutableOverlayMode.asStateFlow()

    // Size of the map in dp; `null` until the screen reports it.
    private val mapSize = MutableStateFlow<Pair<Double, Double>?>(null)

    // The progress of each mode's day; only the shown mode's day runs (design D9 of add-sun-exposure-heatmap).
    private val overlayDayProgress = MutableStateFlow<Float?>(null)
    private val heatmapDayProgress = MutableStateFlow<Float?>(null)

    /** Share of the day's slider steps computed while the day's computation runs, else `null` (design D11). */
    val dayProgress: StateFlow<Float?> =
        combine(mutableOverlayMode, overlayDayProgress, heatmapDayProgress) { mode, overlay, heatmap ->
            if (mode == OverlayMode.SUN_HOURS) heatmap else overlay
        }.stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)

    // The `Sun & shade` overlay is computed only while it is shown (design D9 of add-sun-exposure-heatmap).
    private val sunAndShadeShown = combine(mutableOverlayOn, mutableOverlayMode) { on, mode -> on && mode == OverlayMode.SUN_AND_SHADE }

    // The overlay switched on, and whether it shows the heatmap, whose day is computed only then.
    private val sunHoursShown =
        combine(mutableOverlayOn, mutableOverlayMode) { on, mode -> on to (on && mode == OverlayMode.SUN_HOURS) }

    // The cell sizes and steps of both modes (settings spec, "Shade resolution"); other settings,
    // such as the opacity, do not restart the overlay.
    private val resolution: Flow<Resolution> = settings.map { it.resolution }.distinctUntilChanged()

    /**
     * The time slider's step: the `Sun & shade` step while that mode is shown, else 5 minutes
     * (time-selection spec, "Choose the time of day"; design D4 of add-settings).
     */
    val sliderStep: StateFlow<Int> =
        combine(sunAndShadeShown, resolution) { shown, _ -> stepFor(shown) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = stepFor(isSunAndShadeShown()))

    // The overlay of the visible area at the selected time (design D8 of add-sun-shade-overlay).
    // Latest wins, as for the horizon. A camera move waits [SETTLE_MILLIS]; a time change starts at
    // once. Both keep the previous grid meanwhile. A reconnect recomputes only a grid with unknown
    // cells. With [dayDispatcher], the other steps of the day follow in the background (design D11).
    // Days are kept in [dayCache] (design D14): a camera rest, a date change or switching off stops
    // the day, and coming back to it resumes it; a day with unknown cells is computed anew while
    // online. A time change within the day takes its grid from the day when it is there.
    val overlay: StateFlow<OverlayUiState> =
        channelFlow {
            var lookup: Job? = null
            var day: DayOverlay? = null
            var dayJob: Job? = null
            // Written by the lookups, read by the collector, as for the horizon's `done`.
            val shown = AtomicReference<OverlayUiState.Ready?>(null)
            var requestedTime: ZonedDateTime? = null
            combine(camera, selectedTime, sunAndShadeShown, mapSize, combine(isOnline, resolution, ::Pair)) {
                camera,
                time,
                on,
                size,
                (online, resolution),
                ->
                OverlayInput(camera, time, on, size, online, resolution.sunShadeCellDp.toDouble(), resolution.sunShadeStepMinutes)
            }
                // While a sweep that cannot stop mid-chunk is being cancelled, keep only the latest input.
                .conflate()
                .collect { input ->
                    val area = input.area()
                    if (area == null) {
                        lookup?.cancelAndJoin()
                        dayJob?.cancelAndJoin()
                        day = null
                        shown.set(null)
                        requestedTime = null
                        send(if (input.on && input.camera.zoom < MIN_OVERLAY_ZOOM) OverlayUiState.ZoomedOut else OverlayUiState.Off)
                        return@collect
                    }
                    val current = shown.get()
                    // A new cell size or step: the kept overlay belongs to another shade resolution (sun-shade-overlay
                    // spec, "Overlay resolution"), even when its time lies on both grids.
                    val dayResolutionChanged = day?.let { it.cellDp != input.cellDp || it.stepMinutes != input.stepMinutes } == true
                    val resolutionChanged = current != null && (current.cellDp != input.cellDp || dayResolutionChanged)
                    val unchanged =
                        current != null &&
                            current.grid.area == area &&
                            current.time == input.time &&
                            requestedTime == input.time &&
                            day?.let { it.cellDp == input.cellDp && it.stepMinutes == input.stepMinutes } == true
                    if (unchanged && (!input.online || !current.grid.hasUnknown)) return@collect
                    // A new time or shade resolution is computed at once; only a camera move waits to rest.
                    val timeChanged = requestedTime != null && requestedTime != input.time
                    val immediate = timeChanged || dayResolutionChanged
                    requestedTime = input.time
                    lookup?.cancelAndJoin()
                    val date = input.time.toLocalDate()
                    val sameDay =
                        day?.let {
                            it.area == area && it.date == date && it.cellDp == input.cellDp && it.stepMinutes == input.stepMinutes
                        } == true &&
                            !unchanged
                    if (!sameDay) {
                        dayJob?.cancelAndJoin()
                        var cached = dayCache.get(area, date, input.cellDp, input.stepMinutes)
                        // A day with unknown cells is computed anew while online, as after a reconnect (D14).
                        if (cached != null && input.online && cached.hasUnknown) {
                            dayCache.remove(cached)
                            cached = null
                        }
                        val newDay =
                            cached ?: DayOverlay(
                                area,
                                date,
                                zone,
                                overlayGrid,
                                dayDispatcher ?: computeDispatcher,
                                input.stepMinutes,
                                input.cellDp,
                            )
                        day = newDay
                        dayJob =
                            dayDispatcher?.takeIf { newDay.computed.value < newDay.steps.size }?.let {
                                launch {
                                    val progress =
                                        launch {
                                            newDay.computed.collect {
                                                // Cached once it has a grid; each step may push older days out.
                                                if (it > 0) {
                                                    dayCache.put(newDay)
                                                    dayCache.trim(keep = newDay)
                                                }
                                                overlayDayProgress.value =
                                                    it.toFloat() / newDay.steps.size
                                            }
                                        }
                                    try {
                                        val took = measureTime { newDay.computeRest { mutableSelectedTime.value } }
                                        log(
                                            "Overlay day ${newDay.date}: ${newDay.computed.value} of ${newDay.steps.size} steps, " +
                                                "${newDay.nightSteps} at night, in ${took.inWholeMilliseconds} ms",
                                        )
                                    } finally {
                                        // Joined, so that no late progress value follows the null.
                                        withContext(NonCancellable) { progress.cancelAndJoin() }
                                        overlayDayProgress.value = null
                                    }
                                }
                            }
                    }
                    val selectedDay = checkNotNull(day)
                    val known = selectedDay.gridAt(input.time)
                    if (known != null) {
                        val ready = OverlayUiState.Ready(known, input.time, renderOverlay(known), input.cellDp)
                        shown.set(ready)
                        send(ready)
                        return@collect
                    }
                    // After a time or date change, an earlier day's grid of that time fills in meanwhile
                    // (sun-shade-overlay spec, "Overlay updates"; design D2 of polish-overlay).
                    val earlier =
                        dayCache
                            .takeIf { timeChanged }
                            ?.overlapping(area, input.time, input.cellDp, input.stepMinutes, except = selectedDay)
                            ?.gridAt(input.time)
                    if (earlier != null) {
                        val ready =
                            OverlayUiState.Ready(
                                earlier,
                                input.time,
                                renderOverlay(earlier),
                                input.cellDp,
                                OverlaySource.EARLIER_DAY,
                            )
                        shown.set(ready)
                        send(ready)
                    } else {
                        send(OverlayUiState.Computing(kept = current, resolutionChanged = resolutionChanged))
                    }
                    lookup =
                        launch {
                            if (!immediate) delay(SETTLE_MILLIS)
                            val (grid, computing) = measureTimedValue { selectedDay.compute(input.time) }
                            val (image, rendering) = measureTimedValue { renderOverlay(grid) }
                            log(
                                "Overlay ${area.widthDp.toInt()}×${area.heightDp.toInt()} dp at zoom ${area.zoom}: " +
                                    "grid ${computing.inWholeMilliseconds} ms, image ${rendering.inWholeMilliseconds} ms",
                            )
                            val ready = OverlayUiState.Ready(grid, input.time, image, input.cellDp)
                            shown.set(ready)
                            send(ready)
                        }
                }
        }.flowOn(computeDispatcher)
            // Eagerly, unlike the other states: the day lives in this flow and must survive the app
            // leaving the screen, where it keeps computing (user decision, design D11).
            .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = OverlayUiState.Off)

    // The heatmap of the current day in the mode `Sun hours` (design D5 of add-sun-exposure-heatmap):
    // counted once the day is complete, and kept on its area until the next one is ready. A time
    // change does not change the day, so the heatmap stays. Latest wins, as for the overlay.
    val heatmap: StateFlow<HeatmapUiState> =
        channelFlow {
            var job: Job? = null
            var day: DayOverlay? = null
            // Written by the jobs, read by the collector, as for the overlay's `shown`.
            val kept = AtomicReference<HeatmapUiState.Ready?>(null)
            combine(
                camera,
                selectedTime.map { it.toLocalDate() }.distinctUntilChanged(),
                sunHoursShown,
                mapSize,
                combine(isOnline, resolution, ::Pair),
            ) {
                camera,
                date,
                (on, shown),
                size,
                (online, resolution),
                ->
                HeatmapInput(camera, date, on, shown, size, online, resolution.sunHoursCellDp.toDouble(), resolution.sunHoursStepMinutes)
            }.conflate()
                .collect { input ->
                    val area = input.area()
                    if (area == null) {
                        job?.cancelAndJoin()
                        day = null
                        // Kept across a switch to `Sun & shade`, so that switching back is at once.
                        if (!input.on) kept.set(null)
                        send(if (input.shown && input.camera.zoom < MIN_OVERLAY_ZOOM) HeatmapUiState.ZoomedOut else HeatmapUiState.Off)
                        return@collect
                    }
                    val current = day
                    // Same area, date and resolution: only a reconnect matters, and only for a day with unknown cells.
                    if (current != null &&
                        current.area == area &&
                        current.date == input.date &&
                        current.cellDp == input.cellDp &&
                        current.stepMinutes == input.stepMinutes
                    ) {
                        if (!input.online || !current.hasUnknown) return@collect
                    }
                    job?.cancelAndJoin()
                    var cached = dayCache.get(area, input.date, input.cellDp, input.stepMinutes)
                    // A day with unknown cells is computed anew while online, as for the overlay (D14 of #5).
                    if (cached != null && input.online && cached.hasUnknown) {
                        dayCache.remove(cached)
                        cached = null
                    }
                    val newDay =
                        cached ?: DayOverlay(
                            area,
                            input.date,
                            zone,
                            overlayGrid,
                            dayDispatcher ?: computeDispatcher,
                            input.stepMinutes,
                            input.cellDp,
                        )
                    day = newDay
                    val previous = kept.get()
                    val counted = newDay.counted
                    if (counted != null && previous?.hours === counted) {
                        send(previous)
                        return@collect
                    }
                    if (counted == null) send(HeatmapUiState.Computing(kept = previous))
                    job = launch { kept.set(buildHeatmap(newDay, settle = cached == null)?.also { send(it) }) }
                }
        }.flowOn(computeDispatcher)
            // Eagerly, like the overlay: the day lives in this flow and keeps computing off screen.
            .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = HeatmapUiState.Off)

    // Computes [day]'s missing steps after the camera has rested, counts them and renders the heatmap
    // (design D2, D9 of add-sun-exposure-heatmap); `null` without a background dispatcher.
    private suspend fun buildHeatmap(
        day: DayOverlay,
        settle: Boolean,
    ): HeatmapUiState.Ready? =
        coroutineScope {
            if (day.computed.value < day.steps.size) {
                if (dayDispatcher == null) return@coroutineScope null
                if (settle) delay(SETTLE_MILLIS)
                val progress =
                    launch {
                        day.computed.collect {
                            // Cached once it has a grid; each step may push older days out.
                            if (it > 0) {
                                dayCache.put(day)
                                dayCache.trim(keep = day)
                            }
                            heatmapDayProgress.value = it.toFloat() / day.steps.size
                        }
                    }
                try {
                    // The grids run on the background dispatcher, as for the overlay's day.
                    val took = measureTime { day.computeAll { mutableSelectedTime.value } }
                    log(
                        "Overlay day ${day.date} at ${day.cellDp.toInt()} dp every ${day.stepMinutes} min: " +
                            "${day.computed.value} of ${day.steps.size} steps, ${day.nightSteps} at night, in ${took.inWholeMilliseconds} ms",
                    )
                } finally {
                    // Joined, so that no late progress value follows the null.
                    withContext(NonCancellable) { progress.cancelAndJoin() }
                    heatmapDayProgress.value = null
                }
            }
            val counted = day.counted
            val (hours, counting) = measureTimedValue { day.sunHours() }
            // The counts add to the day's bytes after its last step trimmed the cache.
            dayCache.trim(keep = day)
            val (ready, rendering) = measureTimedValue { heatmapOf(day, hours) }
            if (counted == null) {
                log(
                    "Sun hours ${hours.width}×${hours.height} px, ${hours.steps} steps: " +
                        "counts ${counting.inWholeMilliseconds} ms, image ${rendering.inWholeMilliseconds} ms",
                )
            }
            ready
        }

    private fun heatmapOf(
        day: DayOverlay,
        hours: SunHours,
    ): HeatmapUiState.Ready {
        val bands = HeatmapBands(sunDay(day.area.center, day.date, zone).dayLength)
        return HeatmapUiState.Ready(hours, day.date, bands, renderSunHours(hours, bands))
    }

    private class HeatmapInput(
        val camera: CameraState,
        val date: LocalDate,
        val on: Boolean,
        val shown: Boolean,
        val size: Pair<Double, Double>?,
        val online: Boolean,
        val cellDp: Double,
        val stepMinutes: Int,
    ) {
        // The area to compute, or null when the heatmap is not shown, zoomed out or the size unknown.
        fun area(): MapArea? {
            if (!shown || camera.zoom < MIN_OVERLAY_ZOOM || size == null) return null
            return MapArea(camera.center, camera.zoom, size.first, size.second)
        }
    }

    init {
        // A new shade resolution while `Sun & shade` is shown moves the selected time to a step of
        // the new day (time-selection spec, "Choose the time of day").
        viewModelScope.launch { resolution.drop(1).collect { select(mutableSelectedTime.value) } }
        // Debug builds: once an overlay has stayed for a while, log how many cells agree with the
        // point tracer (spec: ≥ 99.5 % at zoom ≥ 12). Cancelled by the next overlay state.
        if (checkOverlayAgreement) {
            viewModelScope.launch(computeDispatcher) {
                overlay.collectLatest { state ->
                    if (state is OverlayUiState.Ready) {
                        delay(AGREEMENT_DELAY_MILLIS)
                        log(overlayAgreement(state))
                    }
                }
            }
        }
    }

    // The cells' states against the point tracer at their sample points, with the map centre's sun.
    private suspend fun overlayAgreement(state: OverlayUiState.Ready): String {
        var agree = 0
        var checked = 0
        for ((point, shown) in state.grid.sampleCells(AGREEMENT_CELLS, Random(0))) {
            val profile = horizonProfile(point) ?: continue
            checked++
            if (sunshineAt(profile, state.grid.area.center, state.time.toInstant()) == shown) agree++
        }
        return "Overlay agreement with the point tracer: $agree of $checked cells (${if (checked > 0) agree * 100 / checked else 0} %)"
    }

    private class OverlayInput(
        val camera: CameraState,
        val time: ZonedDateTime,
        val on: Boolean,
        val size: Pair<Double, Double>?,
        val online: Boolean,
        val cellDp: Double,
        val stepMinutes: Int,
    ) {
        // The area to compute, or null when the overlay is off, zoomed out or the size unknown.
        fun area(): MapArea? {
            if (!on || camera.zoom < MIN_OVERLAY_ZOOM || size == null) return null
            return MapArea(camera.center, camera.zoom, size.first, size.second)
        }
    }

    // The monitor emits the current state as soon as it is collected; `false` only covers the
    // moment before that first emission.
    val isOffline: StateFlow<Boolean> =
        isOnline
            .map { online -> !online }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), initialValue = false)

    // Held here, not in Compose, so that waiting survives rotation and the Settings and Offline pages
    // (gps-location spec, "Wait for a position"; design D3 of add-gps-location).
    private val mutableLocationButton = MutableStateFlow(LocationButtonState.IDLE)
    val locationButton: StateFlow<LocationButtonState> = mutableLocationButton.asStateFlow()

    private val locationActionChannel = Channel<LocationAction>(Channel.BUFFERED)

    // After a launch with `Start at` `My location`, the first fresh position centres the map once,
    // unless the user moved it first; not after a rotation (map-view spec, "Default viewport";
    // design D7 of add-settings).
    private var pendingStartCentre =
        settings.value.startAt == StartAt.MY_LOCATION && savedState.get<Double>(KEY_LATITUDE) == null

    /** One-off actions of the location button, each delivered once to the screen. */
    val locationActions: Flow<LocationAction> = locationActionChannel.receiveAsFlow()

    /** A tap of the location button, with the [access] allowed now and whether location is switched on. */
    fun onLocationTapped(
        access: LocationAccess,
        locationOn: Boolean,
    ) = applyLocationStep(mutableLocationButton.value.onTap(access, locationOn))

    /** The answer to the permission dialog a tap asked for. */
    fun onLocationPermissionAnswered(
        access: LocationAccess,
        locationOn: Boolean,
        dialogAvailable: Boolean,
    ) = applyLocationStep(mutableLocationButton.value.onPermissionAnswer(access, locationOn, dialogAvailable))

    /** The map's position turned [stale] (old) or fresh. */
    fun onLocationStale(stale: Boolean) {
        // Logged in debug builds for the device checks of the stale state (task 1.1 of add-gps-location).
        log("Location stale=$stale")
        mutableLocationButton.value = mutableLocationButton.value.onStale(stale)
        if (!stale && pendingStartCentre) {
            pendingStartCentre = false
            locationActionChannel.trySend(LocationAction.Centre)
        }
    }

    /** The user started moving the map with a gesture. */
    fun onCameraGesture() {
        pendingStartCentre = false
    }

    private fun applyLocationStep(step: LocationStep) {
        mutableLocationButton.value = step.state
        step.actions.forEach { locationActionChannel.trySend(it) }
    }

    /** Called on every camera movement; saved so the viewport survives rotation and process death. */
    fun onCameraMoved(camera: CameraState) {
        mutableCamera.value = camera
        savedState[KEY_LATITUDE] = camera.center.latitude
        savedState[KEY_LONGITUDE] = camera.center.longitude
        savedState[KEY_ZOOM] = camera.zoom
    }

    /**
     * Stores the map's view, the one `Last view` opens at: when the app goes to the background and
     * when the map is left for another page (design D7 of add-settings).
     */
    fun storeLastView() {
        val camera = mutableCamera.value
        viewModelScope.launch { saveLastView(LastView(camera.center.latitude, camera.center.longitude, camera.zoom)) }
    }

    /** The map's size in dp, which with the camera gives the visible area. */
    fun onMapSizeChanged(
        widthDp: Double,
        heightDp: Double,
    ) {
        mapSize.value = widthDp to heightDp
    }

    /** Switches the overlay on or off; saved like the camera, so it survives rotation. */
    fun onOverlayToggled() {
        mutableOverlayOn.value = !mutableOverlayOn.value
        savedState[KEY_OVERLAY_ON] = mutableOverlayOn.value
        select(mutableSelectedTime.value)
    }

    /** Selects what the overlay shows; saved like the switch, so it survives rotation. */
    fun onOverlayModeSelected(mode: OverlayMode) {
        mutableOverlayMode.value = mode
        savedState[KEY_OVERLAY_MODE] = mode.name
        select(mutableSelectedTime.value)
    }

    /** Selects an [option] of the toggle: off, or a mode with the overlay on (design D7 of add-sun-exposure-heatmap). */
    fun onOverlaySelected(option: OverlayOption) {
        // The mode first, so that switching on never shows the other mode for a moment.
        when (option) {
            OverlayOption.OFF -> if (isOverlayOn.value) onOverlayToggled()
            OverlayOption.SUN_AND_SHADE -> onOverlayModeSelected(OverlayMode.SUN_AND_SHADE)
            OverlayOption.SUN_HOURS -> onOverlayModeSelected(OverlayMode.SUN_HOURS)
        }
        if (option != OverlayOption.OFF && !isOverlayOn.value) onOverlayToggled()
    }

    /** Keeps the wall-clock time of day (design D3). */
    fun onDateSelected(date: LocalDate) = select(selectedTime.value.withDate(date))

    /** [minutes] since the start of the selected day, snapped to the slider's grid. */
    fun onSliderMoved(minutes: Float) = select(sliderTime(selectedTime.value.toLocalDate(), zone, minutes, stepFor(isSunAndShadeShown())))

    /** Sets the current time once; the selected time does not follow the clock. */
    fun onNowClicked() = select(now())

    // Saved so the selected time survives rotation and process death; a new launch starts at now.
    // While `Sun & shade` is shown, the time is a step of its day (design D4 of add-settings).
    private fun select(time: ZonedDateTime) {
        val selected = if (isSunAndShadeShown()) roundToStep(time, stepFor(shown = true)) else time
        mutableSelectedTime.value = selected
        savedState[KEY_SELECTED_TIME] = selected.toInstant().toEpochMilli()
    }

    private fun isSunAndShadeShown(): Boolean = mutableOverlayOn.value && mutableOverlayMode.value == OverlayMode.SUN_AND_SHADE

    private fun stepFor(shown: Boolean): Int = if (shown) settings.value.resolution.sunShadeStepMinutes else SLIDER_STEP_MINUTES

    private fun now(): ZonedDateTime = Instant.now(clock).truncatedTo(ChronoUnit.MINUTES).atZone(zone)

    private fun restoreSelectedTime(): ZonedDateTime =
        savedState.get<Long>(KEY_SELECTED_TIME)?.let { Instant.ofEpochMilli(it).atZone(zone) } ?: now()

    // Saved state first (rotation, process restore), then `Start at` (design D7 of add-settings).
    private fun restoreCamera(): CameraState {
        val latitude = savedState.get<Double>(KEY_LATITUDE)
        val longitude = savedState.get<Double>(KEY_LONGITUDE)
        val zoom = savedState.get<Double>(KEY_ZOOM)
        if (latitude != null && longitude != null && zoom != null) {
            return CameraState(center = GeoPoint(latitude, longitude), zoom = zoom)
        }
        val start = settings.value
        val lastView = start.lastView.takeIf { start.startAt != StartAt.ALPS_OVERVIEW }
        return lastView?.let { CameraState(GeoPoint(it.latitude, it.longitude), it.zoom) }
            ?: CameraState(center = DEFAULT_LOCATION, zoom = DEFAULT_ZOOM)
    }

    private fun sunshineState(
        horizon: HorizonState.Computed,
        time: ZonedDateTime,
    ): SunshineUiState {
        val profile = horizon.profile ?: return SunshineUiState.Ready(horizon.point, SunPeriods.Unknown, Sunshine.UNKNOWN)
        val date = time.toLocalDate()
        val memo = periodsOfDay
        val periods =
            if (memo != null && memo.first === horizon && memo.second == date) {
                memo.third
            } else {
                val (periods, duration) = measureTimedValue { sunPeriods(profile, horizon.point, date, zone) }
                log("Sun periods of $date: ${duration.inWholeMilliseconds} ms")
                periods.also { periodsOfDay = Triple(horizon, date, it) }
            }
        return SunshineUiState.Ready(horizon.point, periods, sunshineAt(profile, horizon.point, time.toInstant()))
    }

    private sealed interface HorizonState {
        data object Loading : HorizonState

        /** The horizon at [point]; [profile] is `null` when the ground height is unknown. */
        class Computed(
            val point: GeoPoint,
            val profile: HorizonProfile?,
        ) : HorizonState {
            val isComplete: Boolean get() = profile != null && profile.complete.all { it }
        }
    }

    private fun Elevation.toState(): ElevationState =
        when (this) {
            is Elevation.Known -> ElevationState.Known(metres)
            Elevation.Unknown -> ElevationState.Unknown
        }

    private companion object {
        const val DEFAULT_ZOOM = 10.0
        const val STOP_TIMEOUT_MILLIS = 5_000L

        // Tests; the app passes a quarter of its heap limit (design D14).
        const val DEFAULT_DAY_CACHE_BYTES = 64L shl 20
        const val SETTLE_MILLIS = 300L
        const val KEY_LATITUDE = "camera_latitude"
        const val KEY_LONGITUDE = "camera_longitude"
        const val KEY_ZOOM = "camera_zoom"
        const val KEY_SELECTED_TIME = "selected_time_epoch_millis"
        const val KEY_OVERLAY_ON = "overlay_on"
        const val KEY_OVERLAY_MODE = "overlay_mode"

        const val AGREEMENT_DELAY_MILLIS = 3_000L
        const val AGREEMENT_CELLS = 200
    }
}
