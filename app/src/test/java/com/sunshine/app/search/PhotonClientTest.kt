package com.sunshine.app.search

import com.sunshine.core.GeoPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// Oracle: the place-search spec, "Online search requests".
class PhotonClientTest {
    @Test
    fun `the request for a text near Interlaken`() {
        val url = photonUrl(SearchKey.of("Eiger ", GeoPoint(46.6863, 7.8632)))

        assertEquals("https://photon.komoot.io/api/?q=eiger&lat=46.7&lon=7.9&limit=10", url.toString())
    }

    @Test
    fun `the text is normalized and encoded`() {
        val url = photonUrl(SearchKey.of("Blüemlisalp  Hütte", GeoPoint(46.6863, 7.8632)))

        assertEquals("blüemlisalp hütte", url.queryParameter("q"))
        assertEquals("q=bl%C3%BCemlisalp%20h%C3%BCtte", url.encodedQuery!!.substringBefore('&'))
    }

    @Test
    fun `half a tenth rounds up`() {
        val url = photonUrl(SearchKey.of("aconcagua", GeoPoint(-32.65, -70.65)))

        assertEquals("-32.6", url.queryParameter("lat"))
        assertEquals("-70.6", url.queryParameter("lon"))
    }
}
