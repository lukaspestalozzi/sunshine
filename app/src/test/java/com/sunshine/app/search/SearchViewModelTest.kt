package com.sunshine.app.search

import com.sunshine.app.map.CameraState
import com.sunshine.app.settings.Settings
import com.sunshine.core.GeoPoint
import com.sunshine.core.MapArea
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

// Oracles: the place-search spec's scenarios; Photon's answers recorded on 2026-10-13 (src/test/resources/photon/).
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val online = MutableStateFlow(true)
    private val settings = MutableStateFlow(Settings())

    /** The keys requested, in order. */
    private val requests = mutableListOf<SearchKey>()

    /** Photon's answer to a request: a body, or `null` for a failed request; may suspend. */
    private var fetching: suspend (SearchKey) -> String? = { EMPTY_ANSWER }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `saved matches at once after a change`() =
        search { viewModel, store ->
            store.save(SearchKey.of("eiger", INTERLAKEN), listOf(EIGER))

            viewModel.onTextChanged("eig")
            runCurrent()

            assertEquals(
                listOf("Eiger"),
                viewModel.titles(),
            )
            assertEquals(emptyList<SearchKey>(), requests)
        }

    @Test
    fun `two characters send no request`() =
        search { viewModel, _ ->
            viewModel.onTextChanged("ei")
            advanceTimeBy(1_000)

            assertEquals(emptyList<SearchKey>(), requests)
        }

    @Test
    fun `the request goes out 300 ms after typing pauses`() =
        search { viewModel, _ ->
            viewModel.onTextChanged("Eiger ")
            advanceTimeBy(299)
            assertEquals(emptyList<SearchKey>(), requests)

            advanceTimeBy(2)

            // The request of the spec's scenario: q=eiger&lat=46.7&lon=7.9.
            assertEquals(listOf(SearchKey("eiger", 467, 79)), requests)
        }

    @Test
    fun `typing on cancels the request and its answer is not shown`() =
        search { viewModel, _ ->
            val cancelled = mutableListOf<String>()
            fetching = { key ->
                if (key.text == "eig") {
                    try {
                        delay(5_000)
                        fixture("eig")
                    } catch (e: CancellationException) {
                        cancelled += key.text
                        throw e
                    }
                } else {
                    EMPTY_ANSWER
                }
            }
            viewModel.onTextChanged("eig")
            advanceTimeBy(1_000)

            viewModel.onTextChanged("eige")
            advanceTimeBy(10_000)

            assertEquals(listOf("eig", "eige"), requests.map { it.text })
            assertEquals(listOf("eig"), cancelled)
            assertEquals(SearchResults(noneFound = true), viewModel.results.value)
        }

    @Test
    fun `the answer replaces the saved matches, merged, and is saved`() =
        search { viewModel, store ->
            val street = Place("W1", "Eigerstrasse", "street", "Interlaken", null, "CH", GeoPoint(46.68377, 7.84393), null)
            store.save(SearchKey.of("strasse", INTERLAKEN), listOf(street, EIGER))
            fetching = { fixture("eig") }

            viewModel.onTextChanged("eig")
            runCurrent()
            // Nearest first: Eigerstrasse is 1.5 km away, the Eiger 16 km.
            assertEquals(
                listOf("Eigerstrasse", "Eiger"),
                viewModel.titles(),
            )

            advanceTimeBy(301)

            // Photon's order; the service road (19 m) and the locality Eigergletscher (142 m) merged.
            val expected =
                listOf("Eiger", "Eigergletscher", "Eigergletscher", "Eigerstrasse", "Eiget", "Eiger Mushroom", "Eiger Nordwand", "Eigen")
            assertEquals(
                expected,
                viewModel.titles(),
            )
            assertEquals(10, store.answer(SearchKey.of("eig", INTERLAKEN), maxAgeMillis = 1_000)?.size)
        }

    @Test
    fun `while the request runs the saved matches are listed with a searching notice`() =
        search { viewModel, store ->
            // Saved by another search, so that no saved answer for "eiger" exists.
            store.save(SearchKey.of("eig", INTERLAKEN), listOf(EIGER))
            fetching = {
                delay(5_000)
                fixture("eig")
            }

            viewModel.onTextChanged("eiger")
            advanceTimeBy(301)
            assertEquals(
                listOf("Eiger"),
                viewModel.titles(),
            )
            assertEquals(SearchNotice.SEARCHING, viewModel.results.value.notice)

            advanceTimeBy(5_000)

            // Photon's 10 results, merged to 8 as in the test above.
            assertEquals(8, viewModel.titles().size)
            assertEquals(null, viewModel.results.value.notice)
        }

    @Test
    fun `typing on during the request clears the searching notice until the next request`() =
        search { viewModel, _ ->
            fetching = {
                delay(5_000)
                EMPTY_ANSWER
            }
            viewModel.onTextChanged("eig")
            advanceTimeBy(301)
            assertEquals(SearchNotice.SEARCHING, viewModel.results.value.notice)

            viewModel.onTextChanged("eige")
            runCurrent()
            assertEquals(null, viewModel.results.value.notice)

            advanceTimeBy(301)
            assertEquals(SearchNotice.SEARCHING, viewModel.results.value.notice)
        }

    @Test
    fun `an empty answer shows no places found`() =
        search { viewModel, store ->
            // Saved by another search, so that no saved answer for "eiger" exists.
            store.save(SearchKey.of("eig", INTERLAKEN), listOf(EIGER))
            fetching = { EMPTY_ANSWER }

            viewModel.onTextChanged("eiger")
            runCurrent()
            assertEquals(
                listOf("Eiger"),
                viewModel.titles(),
            )
            advanceTimeBy(301)

            assertEquals(listOf(SearchKey.of("eiger", INTERLAKEN)), requests)
            assertEquals(SearchResults(noneFound = true), viewModel.results.value)
        }

    @Test
    fun `a saved answer 3 days old is reused without a request`() =
        search { viewModel, store ->
            store.save(SearchKey.of("eiger", INTERLAKEN), listOf(EIGER))
            advanceTimeBy(3 * DAY)

            viewModel.onTextChanged("Eiger")
            advanceTimeBy(301)

            assertEquals(emptyList<SearchKey>(), requests)
            assertEquals(
                listOf("Eiger"),
                viewModel.titles(),
            )
        }

    @Test
    fun `a saved answer 31 days old is replaced by a request`() =
        search { viewModel, store ->
            store.save(SearchKey.of("eiger", INTERLAKEN), listOf(EIGER))
            advanceTimeBy(31 * DAY)
            fetching = { fixture("eig") }

            viewModel.onTextChanged("eiger")
            advanceTimeBy(301)

            assertEquals(listOf(SearchKey.of("eiger", INTERLAKEN)), requests)
            assertEquals(10, store.answer(SearchKey.of("eiger", INTERLAKEN), maxAgeMillis = DAY)?.size)
        }

    @Test
    fun `a changed reuse period applies to the next search`() =
        search { viewModel, store ->
            store.save(SearchKey.of("eiger", INTERLAKEN), listOf(EIGER))
            advanceTimeBy(2 * DAY)
            viewModel.onTextChanged("eiger")
            advanceTimeBy(301)
            assertEquals(emptyList<SearchKey>(), requests)

            settings.value = Settings(reuseSearchesHours = 24)
            viewModel.onTextChanged("")
            viewModel.onTextChanged("eiger")
            advanceTimeBy(301)

            assertEquals(listOf(SearchKey.of("eiger", INTERLAKEN)), requests)
        }

    @Test
    fun `offline the saved places of any age are listed with a notice`() =
        search { viewModel, store ->
            store.save(SearchKey.of("eiger", INTERLAKEN), listOf(EIGER))
            advanceTimeBy(365 * DAY)
            online.value = false

            viewModel.onTextChanged("eig")
            advanceTimeBy(1_000)

            assertEquals(emptyList<SearchKey>(), requests)
            assertEquals(
                listOf("Eiger"),
                viewModel.titles(),
            )
            assertEquals(SearchNotice.OFFLINE, viewModel.results.value.notice)
        }

    @Test
    fun `a failed request keeps the saved matches with a notice until the text changes`() =
        search { viewModel, store ->
            store.save(SearchKey.of("eig", INTERLAKEN), listOf(EIGER))
            fetching = { null } // as PhotonClient answers HTTP 503

            viewModel.onTextChanged("eiger")
            advanceTimeBy(301)
            assertEquals(
                listOf("Eiger"),
                viewModel.titles(),
            )
            assertEquals(SearchNotice.UNAVAILABLE, viewModel.results.value.notice)

            viewModel.onTextChanged("eiger n")
            runCurrent()

            assertEquals(null, viewModel.results.value.notice)
        }

    @Test
    fun `no answer within 10 s counts as failed`() =
        search { viewModel, _ ->
            fetching = {
                delay(60_000)
                fixture("eig")
            }

            viewModel.onTextChanged("eiger")
            advanceTimeBy(300 + 9_999)
            assertEquals(SearchNotice.SEARCHING, viewModel.results.value.notice)
            advanceTimeBy(2)

            assertEquals(SearchResults(notice = SearchNotice.UNAVAILABLE, noneFound = true), viewModel.results.value)
        }

    @Test
    fun `the returning network searches the current text`() =
        search { viewModel, _ ->
            online.value = false
            viewModel.onTextChanged("eiger")
            advanceTimeBy(1_000)
            assertEquals(SearchNotice.OFFLINE, viewModel.results.value.notice)

            online.value = true
            runCurrent()
            assertEquals(null, viewModel.results.value.notice)
            advanceTimeBy(301)

            assertEquals(listOf(SearchKey.of("eiger", INTERLAKEN)), requests)
        }

    @Test
    fun `coordinates are one entry without a request, online and offline`() =
        search { viewModel, _ ->
            val entry = SearchEntry("46.6863° N, 7.8632° E", "coordinates", "0 m", INTERLAKEN, extent = null)

            viewModel.onTextChanged("46.6863, 7.8632")
            advanceTimeBy(1_000)
            assertEquals(SearchResults(listOf(entry)), viewModel.results.value)

            online.value = false
            runCurrent()
            assertEquals(SearchResults(listOf(entry), notice = SearchNotice.OFFLINE), viewModel.results.value)
            assertEquals(emptyList<SearchKey>(), requests)
        }

    @Test
    fun `typed LV95 is shown as typed`() =
        search { viewModel, _ ->
            viewModel.onTextChanged("1170652 2632479")
            runCurrent()

            // Converted back from WGS84 the northing would read 1'170'651 (design D11).
            assertEquals(
                listOf("2'632'479, 1'170'652"),
                viewModel.titles(),
            )
        }

    @Test
    fun `an empty field gives an empty list, and reopening empties the field`() =
        search { viewModel, store ->
            store.save(SearchKey.of("eig", INTERLAKEN), listOf(EIGER))
            viewModel.onTextChanged("eiger")
            runCurrent()

            viewModel.onTextChanged("")
            runCurrent()
            assertEquals(SearchResults(), viewModel.results.value)

            viewModel.onTextChanged("eiger")
            viewModel.onOpened(MapArea(INTERLAKEN, zoom = 12.0, widthDp = 400.0, heightDp = 800.0))
            runCurrent()
            assertEquals("", viewModel.text.value)
            assertEquals(SearchResults(), viewModel.results.value)
        }

    @Test
    fun `a chosen peak is shown at zoom 13`() =
        search { viewModel, store ->
            store.save(SearchKey.of("eig", INTERLAKEN), listOf(EIGER))
            viewModel.onTextChanged("eiger")
            runCurrent()

            val camera =
                viewModel.cameraFor(
                    viewModel.results.value.entries
                        .single(),
                )

            assertEquals(CameraState(GeoPoint(46.57763, 8.00547), zoom = 13.0), camera)
        }

    @Test
    fun `a closed page does not search when the network returns`() =
        search { viewModel, _ ->
            online.value = false
            viewModel.onTextChanged("eiger")
            runCurrent()

            viewModel.onClosed()
            online.value = true
            advanceTimeBy(1_000)

            assertEquals(emptyList<SearchKey>(), requests)
            assertEquals("", viewModel.text.value)
        }

    /** Runs [test] with a view model opened at Interlaken, zoom 12, on a 400 × 800 dp map. */
    private fun search(test: suspend TestScope.(SearchViewModel, PlaceStore) -> Unit) =
        runTest {
            val database = inMemoryPlacesDatabase(UnconfinedTestDispatcher(testScheduler))
            val store = PlaceStore(database.dao(), now = { testScheduler.currentTime })
            val viewModel =
                SearchViewModel(
                    store = store,
                    fetch = { key ->
                        requests += key
                        fetching(key)
                    },
                    isOnline = online,
                    settings = settings,
                )
            viewModel.onOpened(MapArea(INTERLAKEN, zoom = 12.0, widthDp = 400.0, heightDp = 800.0))
            try {
                test(viewModel, store)
            } finally {
                database.close()
            }
        }

    private fun SearchViewModel.titles() = results.value.entries.map { it.title }

    private fun fixture(name: String): String = checkNotNull(javaClass.getResource("/photon/$name.json")) { name }.readText()

    private companion object {
        const val EMPTY_ANSWER = """{"features":[]}"""
        const val DAY = 24 * 60 * 60 * 1000L
        val INTERLAKEN = GeoPoint(46.6863, 7.8632)
        val EIGER = Place("N31664302", "Eiger", "peak", "Grindelwald", "Bern/Berne", "CH", GeoPoint(46.57763, 8.00547), null)
    }
}
