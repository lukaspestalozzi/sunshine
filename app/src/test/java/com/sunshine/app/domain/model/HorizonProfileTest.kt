package com.sunshine.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class HorizonProfileTest {
    private val observer = GeoPoint(latitude = 46.8182, longitude = 8.2275)

    private fun profile(vararg entries: Pair<Double, Double>): HorizonProfile =
        HorizonProfile(
            observer = observer,
            observerElevation = 500.0,
            entries = entries.map { (az, angle) -> HorizonEntry(az, angle) },
        )

    @Test
    fun `returns zero for empty profile`() {
        val p = profile()
        assertEquals(0.0, p.getHorizonAngleAt(90.0), TOLERANCE)
    }

    @Test
    fun `returns constant for single entry profile`() {
        val p = profile(180.0 to 5.0)
        assertEquals(5.0, p.getHorizonAngleAt(90.0), TOLERANCE)
        assertEquals(5.0, p.getHorizonAngleAt(270.0), TOLERANCE)
    }

    @Test
    fun `exact match returns entry value`() {
        val p = profile(90.0 to 10.0, 180.0 to 20.0, 270.0 to 30.0)
        assertEquals(10.0, p.getHorizonAngleAt(90.0), TOLERANCE)
        assertEquals(20.0, p.getHorizonAngleAt(180.0), TOLERANCE)
        assertEquals(30.0, p.getHorizonAngleAt(270.0), TOLERANCE)
    }

    @Test
    fun `interpolates between two entries`() {
        val p = profile(90.0 to 10.0, 180.0 to 20.0)
        // Midpoint between 90° and 180° → average of 10 and 20
        assertEquals(15.0, p.getHorizonAngleAt(135.0), TOLERANCE)
    }

    @Test
    fun `interpolates with quarter fraction`() {
        val p = profile(100.0 to 0.0, 200.0 to 40.0)
        // 25% of the way from 100→200 is azimuth 125, angle = 0 + 0.25*40 = 10
        assertEquals(10.0, p.getHorizonAngleAt(125.0), TOLERANCE)
    }

    @Test
    fun `wraps around 360 boundary`() {
        val p = profile(350.0 to 10.0, 10.0 to 30.0)
        // Azimuth 0° is midpoint of the 350→10 span (20° span, 0 is 10° in)
        assertEquals(20.0, p.getHorizonAngleAt(0.0), TOLERANCE)
    }

    @Test
    fun `wraps for azimuth between last and first entry`() {
        val p = profile(90.0 to 10.0, 270.0 to 30.0)
        // Azimuth 0° is in the wrap-around span: 270→90 (180° span)
        // 0° is 90° past 270° → fraction 90/180 = 0.5 → 30 + 0.5*(10-30) = 20
        assertEquals(20.0, p.getHorizonAngleAt(0.0), TOLERANCE)
    }

    @Test
    fun `handles normalized negative azimuth equivalent`() {
        val p = profile(90.0 to 10.0, 270.0 to 30.0)
        // Azimuth 360° should equal 0°
        assertEquals(
            p.getHorizonAngleAt(0.0),
            p.getHorizonAngleAt(360.0),
            TOLERANCE,
        )
    }

    @Test
    fun `three entry profile interpolates correctly`() {
        val p = profile(0.0 to 5.0, 120.0 to 15.0, 240.0 to 25.0)
        // Midpoint between 0° and 120° = 60°, expect (5+15)/2 = 10
        assertEquals(10.0, p.getHorizonAngleAt(60.0), TOLERANCE)
        // Midpoint between 120° and 240° = 180°, expect (15+25)/2 = 20
        assertEquals(20.0, p.getHorizonAngleAt(180.0), TOLERANCE)
    }

    companion object {
        private const val TOLERANCE = 0.01
    }
}
