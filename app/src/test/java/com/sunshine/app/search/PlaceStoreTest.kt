package com.sunshine.app.search

import com.sunshine.core.GeoPoint
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

// Oracles: the place-search spec, "Saved places", "Saved matches while typing", "Reuse of saved answers".
class PlaceStoreTest {
    private var now = 0L
    private val database = inMemoryPlacesDatabase()
    private val store = PlaceStore(database.dao(), now = { now })

    @AfterEach
    fun close() = database.close()

    @Test
    fun `an answer keeps its places in their order`() =
        runTest {
            store.save(EIGER_KEY, listOf(EIGER, EIGERSTRASSE))

            assertEquals(listOf(EIGER, EIGERSTRASSE), store.answer(EIGER_KEY, maxAgeMillis = DAY))
        }

    @Test
    fun `a place returned again replaces its copy`() =
        runTest {
            val renamed = EIGER.copy(title = "Eiger (Mönch)")
            store.save(EIGER_KEY, listOf(EIGER))
            store.save(SearchKey.of("mönch", INTERLAKEN), listOf(renamed))

            assertEquals(listOf(renamed), store.answer(EIGER_KEY, maxAgeMillis = DAY))
        }

    @Test
    fun `saved matches nearest first`() =
        runTest {
            val peak = place("N1", "Grosser Mythen", "peak", GeoPoint(47.02988, 8.68853))
            val restaurant = place("N2", "Restaurant Grosser Mythen", "restaurant", GeoPoint(47.02970, 8.68883))
            store.save(SearchKey.of("mythen", INTERLAKEN), listOf(peak, restaurant, EIGER))

            // From the restaurant's terrace, the restaurant is nearer than the summit.
            assertEquals(listOf(restaurant, peak), store.matches("gross myth", GeoPoint(47.02970, 8.68883), limit = 10))
        }

    @Test
    fun `an answer is reused only while younger than the period`() =
        runTest {
            store.save(EIGER_KEY, listOf(EIGER))

            now = 30 * DAY - 1
            assertEquals(listOf(EIGER), store.answer(EIGER_KEY, maxAgeMillis = 30 * DAY))
            now = 30 * DAY
            assertNull(store.answer(EIGER_KEY, maxAgeMillis = 30 * DAY))
        }

    @Test
    fun `clear removes places and answers`() =
        runTest {
            store.save(EIGER_KEY, listOf(EIGER))

            store.clear()

            assertNull(store.answer(EIGER_KEY, maxAgeMillis = DAY))
            assertEquals(emptyList<Place>(), store.matches("eiger", INTERLAKEN, limit = 10))
        }

    @Test
    fun `saved matches among 20 000 places`() =
        runTest {
            // Every fifth name holds "Eiger"; the places spread over the Alps.
            val places =
                (0 until SYNTHETIC_PLACES).map { i ->
                    val name = if (i % 5 == 0) "Eiger Weg $i" else "Rothorn Alp $i"
                    place("N$i", name, "peak", GeoPoint(45.5 + (i % 100) * 0.02, 6.0 + (i / 100) * 0.04))
                }
            store.save(SearchKey.of("synthetic", INTERLAKEN), places)
            store.matches("eig", INTERLAKEN, limit = 10) // warm-up

            val times = (1..5).map { measureTimeMillis { assertEquals(10, store.matches("eig", INTERLAKEN, limit = 10).size) } }

            // The performance budget of the design: at most 50 ms on the phone; logged, not asserted, as CI machines vary.
            println("Saved matches for 'eig' among $SYNTHETIC_PLACES places: ${times.sorted()[2]} ms (median of 5)")
        }

    private companion object {
        const val SYNTHETIC_PLACES = 20_000
        const val DAY = 24 * 60 * 60 * 1000L
        val INTERLAKEN = GeoPoint(46.6863, 7.8632)
        val EIGER_KEY = SearchKey.of("eiger", INTERLAKEN)
        val EIGER = place("N31664302", "Eiger", "peak", GeoPoint(46.57763, 8.00547))
        val EIGERSTRASSE = place("W24413133", "Eigerstrasse", "street", GeoPoint(46.68377, 7.84393))

        fun place(
            key: String,
            title: String,
            label: String,
            point: GeoPoint,
        ) = Place(key, title, label, place = "Grindelwald", region = "Bern/Berne", countryCode = "CH", point = point, extent = null)
    }
}
