package com.sunshine.app.map

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sunshine.app.elevation.Elevation
import com.sunshine.app.elevation.ElevationRepository
import com.sunshine.core.DEFAULT_LOCATION
import com.sunshine.core.GeoPoint
import com.sunshine.core.HorizonProfile
import com.sunshine.core.MapArea
import com.sunshine.core.ShadeGrid
import com.sunshine.core.SunDay
import com.sunshine.core.SunPeriods
import com.sunshine.core.SunPosition
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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
    ) : OverlayUiState

    /** The grid of the visible area at [time], and its [image] (rendered off the main thread). */
    data class Ready(
        val grid: ShadeGrid,
        val time: ZonedDateTime,
        val image: OverlayImage,
    ) : OverlayUiState
}

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
            combine(camera, selectedTime, sunAndShadeShown, mapSize, isOnline) { camera, time, on, size, online ->
                OverlayInput(camera, time, on, size, online)
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
                    val unchanged =
                        current != null && current.grid.area == area && current.time == input.time && requestedTime == input.time
                    if (unchanged && (!input.online || !current.grid.hasUnknown)) return@collect
                    val timeChanged = requestedTime != null && requestedTime != input.time
                    requestedTime = input.time
                    lookup?.cancelAndJoin()
                    val date = input.time.toLocalDate()
                    val sameDay = day?.let { it.area == area && it.date == date } == true && !unchanged
                    if (!sameDay) {
                        dayJob?.cancelAndJoin()
                        var cached = dayCache.get(area, date)
                        // A day with unknown cells is computed anew while online, as after a reconnect (D14).
                        if (cached != null && input.online && cached.hasUnknown) {
                            dayCache.remove(cached)
                            cached = null
                        }
                        val newDay = cached ?: DayOverlay(area, date, zone, overlayGrid, dayDispatcher ?: computeDispatcher)
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
                        val ready = OverlayUiState.Ready(known, input.time, renderOverlay(known))
                        shown.set(ready)
                        send(ready)
                        return@collect
                    }
                    send(OverlayUiState.Computing(kept = current))
                    lookup =
                        launch {
                            if (!timeChanged) delay(SETTLE_MILLIS)
                            val (grid, computing) = measureTimedValue { selectedDay.compute(input.time) }
                            val (image, rendering) = measureTimedValue { renderOverlay(grid) }
                            log(
                                "Overlay ${area.widthDp.toInt()}×${area.heightDp.toInt()} dp at zoom ${area.zoom}: " +
                                    "grid ${computing.inWholeMilliseconds} ms, image ${rendering.inWholeMilliseconds} ms",
                            )
                            val ready = OverlayUiState.Ready(grid, input.time, image)
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
            combine(camera, selectedTime.map { it.toLocalDate() }.distinctUntilChanged(), sunHoursShown, mapSize, isOnline) {
                camera,
                date,
                (on, shown),
                size,
                online,
                ->
                HeatmapInput(camera, date, on, shown, size, online)
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
                    // Same area and date: only a reconnect matters, and only for a day with unknown cells.
                    if (current != null && current.area == area && current.date == input.date) {
                        if (!input.online || !current.hasUnknown) return@collect
                    }
                    job?.cancelAndJoin()
                    var cached = dayCache.get(area, input.date, HEATMAP_CELL_DP)
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
                            HEATMAP_STEP_MINUTES,
                            HEATMAP_CELL_DP,
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
    ) {
        // The area to compute, or null when the heatmap is not shown, zoomed out or the size unknown.
        fun area(): MapArea? {
            if (!shown || camera.zoom < MIN_OVERLAY_ZOOM || size == null) return null
            return MapArea(camera.center, camera.zoom, size.first, size.second)
        }
    }

    init {
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

    /** Called on every camera movement; saved so the viewport survives rotation and process death. */
    fun onCameraMoved(camera: CameraState) {
        mutableCamera.value = camera
        savedState[KEY_LATITUDE] = camera.center.latitude
        savedState[KEY_LONGITUDE] = camera.center.longitude
        savedState[KEY_ZOOM] = camera.zoom
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
    }

    /** Selects what the overlay shows; saved like the switch, so it survives rotation. */
    fun onOverlayModeSelected(mode: OverlayMode) {
        mutableOverlayMode.value = mode
        savedState[KEY_OVERLAY_MODE] = mode.name
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

    /** [minutes] since the start of the selected day, snapped to the 5-minute grid. */
    fun onSliderMoved(minutes: Float) = select(sliderTime(selectedTime.value.toLocalDate(), zone, minutes))

    /** Sets the current time once; the selected time does not follow the clock. */
    fun onNowClicked() = select(now())

    // Saved so the selected time survives rotation and process death; a new launch starts at now.
    private fun select(time: ZonedDateTime) {
        mutableSelectedTime.value = time
        savedState[KEY_SELECTED_TIME] = time.toInstant().toEpochMilli()
    }

    private fun now(): ZonedDateTime = Instant.now(clock).truncatedTo(ChronoUnit.MINUTES).atZone(zone)

    private fun restoreSelectedTime(): ZonedDateTime =
        savedState.get<Long>(KEY_SELECTED_TIME)?.let { Instant.ofEpochMilli(it).atZone(zone) } ?: now()

    private fun restoreCamera(): CameraState {
        val latitude = savedState.get<Double>(KEY_LATITUDE)
        val longitude = savedState.get<Double>(KEY_LONGITUDE)
        val zoom = savedState.get<Double>(KEY_ZOOM)
        if (latitude == null || longitude == null || zoom == null) {
            return CameraState(center = DEFAULT_LOCATION, zoom = DEFAULT_ZOOM)
        }
        return CameraState(center = GeoPoint(latitude, longitude), zoom = zoom)
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

        // The heatmap's own day (user decisions, design D9 of add-sun-exposure-heatmap).
        const val HEATMAP_CELL_DP = 8.0
        const val HEATMAP_STEP_MINUTES = 10
        const val AGREEMENT_DELAY_MILLIS = 3_000L
        const val AGREEMENT_CELLS = 200
    }
}
