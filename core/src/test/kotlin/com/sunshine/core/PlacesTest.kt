package com.sunshine.core

import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

// Oracles: the place-search spec ("Saved matches while typing", "Result entries", "Merged duplicates",
// "Choosing a result") and Photon's answers recorded on 2026-10-13 (design of add-place-search).
class PlacesTest {
    @Test
    fun `words of the text begin words of the name`() {
        assertTrue(matchesName("gross myth", "Grosser Mythen"))
    }

    @Test
    fun `other words of the name are allowed`() {
        assertTrue(matchesName("gross myth", "Restaurant Grosser Mythen"))
    }

    @Test
    fun `order of the words does not matter`() {
        assertTrue(matchesName("myth gross", "Grosser Mythen"))
    }

    @Test
    fun `accents are ignored`() {
        assertTrue(matchesName("bluemlisalp", "Blüemlisalphütte"))
    }

    @Test
    fun `the inside of a word does not match`() {
        assertFalse(matchesName("rosser", "Grosser Mythen"))
    }

    @Test
    fun `punctuation separates words`() {
        assertTrue(matchesName("berne", "Bern/Berne"))
    }

    @Test
    fun `Interlaken to the Eiger`() {
        assertEquals(16_249.0, distanceMetres(INTERLAKEN, GeoPoint(46.57763, 8.00547)), 1.0)
    }

    @Test
    fun `Interlaken to Eigerstrasse`() {
        assertEquals(1497.0, distanceMetres(INTERLAKEN, GeoPoint(46.68377, 7.84393)), 1.0)
    }

    @ParameterizedTest(name = "{0} m reads {1}")
    @CsvSource("16249, 16 km", "1497, 1.5 km", "168.7, 170 m", "996, 1.0 km", "9960, 10 km")
    fun `distance texts`(
        metres: Double,
        text: String,
    ) {
        assertEquals(text, formatDistance(metres))
    }

    @Test
    fun `a dot as decimal separator whatever the locale`() {
        val default = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("1.5 km", formatDistance(1497.0))
        } finally {
            Locale.setDefault(default)
        }
    }

    @Test
    fun `five results of one place merge into the first`() {
        val results =
            listOf(
                Entry("Schynige Platte", "station", GeoPoint(46.65226, 7.91117)),
                Entry("Schynige Platte", "locality", GeoPoint(46.65208, 7.91137)),
                Entry("Schynige Platte", "locality", GeoPoint(46.65144, 7.90931)),
                Entry("Schynige Platte", "hotel", GeoPoint(46.65123, 7.90943)),
                Entry("Schynige Platte", "food court", GeoPoint(46.65108, 7.90929)),
            )

        assertEquals(listOf(results.first()), merged(results))
    }

    @Test
    fun `two places of one name 1240 m apart stay two`() {
        val results =
            listOf(
                Entry("Eigergletscher", "station", GeoPoint(46.57480, 7.97486)),
                Entry("Eigergletscher", "glacier", GeoPoint(46.56846, 7.98820)),
            )

        assertEquals(results, merged(results))
    }

    @Test
    fun `names merge ignoring case and accents`() {
        // 0.0009° of latitude is 100 m.
        val hut = Entry("Blüemlisalphütte", "alpine hut", GeoPoint(46.51016, 7.77164))
        val results = listOf(hut, Entry("bluemlisalphutte", "building", GeoPoint(46.51106, 7.77164)))

        assertEquals(listOf(hut), merged(results))
    }

    @Test
    fun `the same name 260 m apart stays two`() {
        // 0.00234° of latitude is 260 m.
        val results =
            listOf(
                Entry("Rothorn", "peak", GeoPoint(46.51016, 7.77164)),
                Entry("Rothorn", "peak", GeoPoint(46.51250, 7.77164)),
            )

        assertEquals(results, merged(results))
    }

    @Test
    fun `merging stops at the limit`() {
        val results = (1..12).map { Entry("Peak $it", "peak", GeoPoint(46.0, 8.0)) }

        assertEquals(results.take(10), merged(results))
    }

    @Test
    fun `zoom that fits an area around its point`() {
        assertEquals(8.14, fitZoom(GeoPoint(46.0, 8.0), LARGE_AREA, widthDp = 400.0, heightDp = 800.0), 0.01)
    }

    @Test
    fun `an area larger than zoom 13 shows is fitted`() {
        assertEquals(8.14, chosen(zoom = 12.0, extent = LARGE_AREA), 0.01)
    }

    @Test
    fun `an area smaller than zoom 13 shows is a point`() {
        val small = Extent(west = 7.895, north = 46.605, east = 7.905, south = 46.595)

        assertEquals(13.0, chosen(zoom = 10.0, extent = small, center = GeoPoint(46.6, 7.9)), 0.01)
    }

    @Test
    fun `an area too large for zoom 5 is shown at zoom 5`() {
        val world = Extent(west = -170.0, north = 80.0, east = 170.0, south = -80.0)

        assertEquals(5.0, chosen(zoom = 12.0, extent = world, center = GeoPoint(0.0, 0.0)), 0.01)
    }

    @ParameterizedTest(name = "from zoom {0} to {1}")
    @CsvSource("10, 13", "15, 15", "17, 17")
    fun `a point is shown at zoom 13 or closer`(
        zoom: Double,
        expected: Double,
    ) {
        assertEquals(expected, chosen(zoom = zoom, extent = null), 0.01)
    }

    private fun chosen(
        zoom: Double,
        extent: Extent?,
        center: GeoPoint = GeoPoint(46.0, 8.0),
    ) = chosenZoom(zoom, center, extent, widthDp = 400.0, heightDp = 800.0)

    private data class Entry(
        val name: String,
        val label: String,
        val point: GeoPoint,
    )

    private fun merged(entries: List<Entry>) = merge(entries, limit = 10, name = { it.name }, point = { it.point })

    private companion object {
        val INTERLAKEN = GeoPoint(46.6863, 7.8632)
        val LARGE_AREA = Extent(west = 7.5, north = 46.5, east = 8.5, south = 45.5)
    }
}
