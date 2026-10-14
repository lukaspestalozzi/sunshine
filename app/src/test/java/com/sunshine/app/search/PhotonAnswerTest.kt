package com.sunshine.app.search

import com.sunshine.core.Extent
import com.sunshine.core.GeoPoint
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

// Oracles: Photon's answers recorded on 2026-10-13 with the app's request form (src/test/resources/photon/),
// and the place-search spec, "Result entries".
class PhotonAnswerTest {
    @Test
    fun `a peak`() {
        val eiger = parsePhotonAnswer(fixture("eig")).first()

        assertEquals("N31664302", eiger.key)
        assertEquals("Eiger", eiger.title)
        assertEquals("peak", eiger.label)
        assertEquals("peak · Grindelwald, Bern/Berne, CH", eiger.line)
        assertEquals(GeoPoint(46.5776321, 8.0054694), eiger.point)
        assertNull(eiger.extent)
    }

    @Test
    fun `a lake with its extent`() {
        val lake = parsePhotonAnswer(fixture("bachalpsee")).first()

        assertEquals("Bachalpsee", lake.title)
        assertEquals(Extent(west = 8.018276, north = 46.671057, east = 8.026351, south = 46.6668713), lake.extent)
    }

    @Test
    fun `an address without a name`() {
        val address = parsePhotonAnswer(fixture("hoeheweg-37")).first()

        assertEquals("Höheweg 37", address.title)
        assertEquals("address", address.label)
    }

    @Test
    fun `a street`() {
        // highway=residential with Photon's type street.
        val street = parsePhotonAnswer(fixture("eig"))[3]

        assertEquals("Eigerstrasse", street.title)
        assertEquals("street", street.label)
    }

    @Test
    fun `a yes value is labelled by its key`() {
        // mountain_pass=yes.
        val pass = parsePhotonAnswer(fixture("gemmipass"))[4]

        assertEquals("Gem Pass", pass.title)
        assertEquals("mountain pass", pass.label)
    }

    @Test
    fun `underscores become spaces`() {
        val hut = parsePhotonAnswer(fixture("bluemlisalphuette")).single()

        assertEquals("Blüemlisalphütte", hut.title)
        assertEquals("alpine hut", hut.label)
    }

    @Test
    fun `a result without name and street is left out`() {
        assertEquals(emptyList<Place>(), parsePhotonAnswer(answer()))
    }

    @Test
    fun `a result beyond the map's latitude limit is left out`() {
        assertEquals(emptyList<Place>(), parsePhotonAnswer(answer("name" to "Antarctic peak", latitude = -86.0)))
    }

    @Test
    fun `the county stands in for a missing city`() {
        val peak = parsePhotonAnswer(answer("name" to "Hohgant", "county" to "Emmental", "state" to "Bern/Berne")).single()

        assertEquals("peak · Emmental, Bern/Berne", peak.line)
    }

    @Test
    fun `missing parts are left out`() {
        val peak = parsePhotonAnswer(answer("name" to "Ama Dablam", "countrycode" to "NP")).single()

        assertEquals("peak · NP", peak.line)
    }

    /** An answer of one peak with [properties] added, at [latitude], 8.0. */
    private fun answer(
        vararg properties: Pair<String, Any>,
        latitude: Double = 46.0,
    ): String {
        val all = mapOf("osm_type" to "N", "osm_id" to 1, "osm_key" to "natural", "osm_value" to "peak") + properties
        val feature = mapOf("properties" to all, "geometry" to mapOf("coordinates" to listOf(8.0, latitude)))
        return JSONObject(mapOf("features" to listOf(feature))).toString()
    }

    private fun fixture(name: String): String = checkNotNull(javaClass.getResource("/photon/$name.json")) { name }.readText()
}
