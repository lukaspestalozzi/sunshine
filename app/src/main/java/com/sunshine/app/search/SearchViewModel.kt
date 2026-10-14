package com.sunshine.app.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sunshine.app.map.CameraState
import com.sunshine.app.map.formatCoordinates
import com.sunshine.app.map.formatLv95
import com.sunshine.app.settings.CoordinateFormat
import com.sunshine.app.settings.Settings
import com.sunshine.core.Coordinates
import com.sunshine.core.Extent
import com.sunshine.core.GeoPoint
import com.sunshine.core.MapArea
import com.sunshine.core.chosenZoom
import com.sunshine.core.distanceMetres
import com.sunshine.core.formatDistance
import com.sunshine.core.merge
import com.sunshine.core.parseCoordinates
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** One line of the results: a place or typed coordinates (place-search spec, "Result entries"). */
data class SearchEntry(
    val title: String,
    val line: String,
    val distance: String,
    val point: GeoPoint,
    val extent: Extent?,
)

/**
 * Why the list holds saved places only (place-search spec, "Search without network or geocoder"),
 * or that Photon's answer is awaited (spec "Online search requests"; design D17 of add-place-search).
 */
enum class SearchNotice { OFFLINE, UNAVAILABLE, SEARCHING }

/** The list below the search field; [noneFound] shows `No places found`. */
data class SearchResults(
    val entries: List<SearchEntry> = emptyList(),
    val notice: SearchNotice? = null,
    val noneFound: Boolean = false,
)

/**
 * The search page's state (place-search spec; design D4, D8 of add-place-search). Each change of the
 * text or of the connection cancels the work of the previous one, its request included.
 */
class SearchViewModel(
    private val store: PlaceStore,
    /** Photon's answer body to a key, or `null` when the request failed. */
    private val fetch: suspend (SearchKey) -> String?,
    isOnline: Flow<Boolean>,
    private val settings: StateFlow<Settings>,
    private val log: (String) -> Unit = {},
) : ViewModel() {
    private val mutableText = MutableStateFlow("")

    /** The search field's text. */
    val text: StateFlow<String> = mutableText.asStateFlow()

    private val mutableResults = MutableStateFlow(SearchResults())
    val results: StateFlow<SearchResults> = mutableResults.asStateFlow()

    // The map when the page was opened: its centre is the crosshair.
    private val area = MutableStateFlow<MapArea?>(null)

    init {
        viewModelScope.launch {
            combine(mutableText, isOnline, area.filterNotNull()) { text, online, area -> Triple(text, online, area.center) }
                .collectLatest { (text, online, crosshair) -> search(text, online, crosshair) }
        }
    }

    /** The page opened over the map showing [area]; the field starts empty (place-search spec, "Search page"). */
    fun onOpened(area: MapArea) {
        mutableText.value = ""
        this.area.value = area
    }

    fun onTextChanged(text: String) {
        mutableText.value = text
    }

    /**
     * The page closed. The view model outlives it, so the field is emptied: a stale text would
     * otherwise be searched again when the connection changes (place-search spec, "Choosing a result").
     */
    fun onClosed() {
        mutableText.value = ""
    }

    /** Where the map goes when [entry] is chosen (place-search spec, "Choosing a result"). */
    fun cameraFor(entry: SearchEntry): CameraState {
        val area = checkNotNull(area.value) { "The search page was not opened" }
        return CameraState(entry.point, chosenZoom(area.zoom, entry.point, entry.extent, area.widthDp, area.heightDp))
    }

    private suspend fun search(
        text: String,
        online: Boolean,
        crosshair: GeoPoint,
    ) {
        val offline = if (online) null else SearchNotice.OFFLINE
        if (text.isBlank()) {
            mutableResults.value = SearchResults(notice = offline)
            return
        }
        parseCoordinates(text)?.let { coordinates ->
            mutableResults.value = SearchResults(listOf(coordinatesEntry(coordinates, crosshair)), notice = offline)
            return
        }
        val started = TimeSource.Monotonic.markNow()
        val matches = store.matches(text, crosshair, LIMIT).map { it.toEntry(crosshair) }
        mutableResults.value = SearchResults(matches, notice = offline, noneFound = !online && matches.isEmpty())
        log("Saved matches for '$text' in ${started.elapsedNow().inWholeMilliseconds} ms")
        if (!online || normalizeSearchText(text).length < MIN_CHARACTERS) return
        delay(PAUSE_MILLIS)
        val key = SearchKey.of(text, crosshair)
        val places =
            store.answer(key, settings.value.reuseSearchesHours * MILLIS_PER_HOUR)
                ?: run {
                    mutableResults.value = SearchResults(matches, notice = SearchNotice.SEARCHING)
                    requested(key)
                }
                ?: run {
                    mutableResults.value =
                        SearchResults(matches, notice = SearchNotice.UNAVAILABLE, noneFound = matches.isEmpty())
                    return
                }
        val shown = TimeSource.Monotonic.markNow()
        val entries = merge(places, LIMIT, name = { it.title }, point = { it.point }).map { it.toEntry(crosshair) }
        mutableResults.value = SearchResults(entries, noneFound = entries.isEmpty())
        log("Answer for '$text' listed in ${shown.elapsedNow().inWholeMilliseconds} ms")
    }

    /** Photon's places for [key], saved; `null` when the request failed or its answer could not be read. */
    private suspend fun requested(key: SearchKey): List<Place>? {
        val body = withTimeoutOrNull(TIMEOUT_MILLIS) { fetch(key) } ?: return null
        val places = runCatching { parsePhotonAnswer(body) }.getOrNull() ?: return null
        store.save(key, places)
        return places
    }

    private fun Place.toEntry(crosshair: GeoPoint) =
        SearchEntry(title, line, formatDistance(distanceMetres(crosshair, point)), point, extent)

    private fun coordinatesEntry(
        coordinates: Coordinates,
        crosshair: GeoPoint,
    ): SearchEntry {
        // The typed values in the map's format for their kind (place-search spec, "Coordinates as search text").
        val title =
            when (coordinates) {
                is Coordinates.Decimal -> formatCoordinates(coordinates.point, CoordinateFormat.DECIMAL)
                is Coordinates.Dms -> formatCoordinates(coordinates.point, CoordinateFormat.DMS)
                is Coordinates.SwissGrid -> formatLv95(coordinates.lv95)
            }
        val point = coordinates.point
        return SearchEntry(title, COORDINATES_LINE, formatDistance(distanceMetres(crosshair, point)), point, extent = null)
    }

    private companion object {
        const val LIMIT = 10
        const val MIN_CHARACTERS = 3
        const val PAUSE_MILLIS = 300L
        const val TIMEOUT_MILLIS = 10_000L
        const val MILLIS_PER_HOUR = 3_600_000L
        const val COORDINATES_LINE = "coordinates"
    }
}
