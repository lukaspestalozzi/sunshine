package com.sunshine.core

import kotlin.math.abs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// Missing data gives lower bounds, never a default height (terrain-horizon spec, "Incomplete horizon").
class IncompleteHorizonTest {
    @Test
    fun `a missing far tile behind a 30 degree ridge leaves the horizon complete`() {
        val terrain =
            SyntheticTerrain { lat, _ ->
                // A slope that rises at 30° as seen from the eye, 1–2 km to the south; bilinear
                // interpolation reproduces it exactly, unlike a step.
                val south = (OBSERVER.latitude - lat) * METRES_PER_DEGREE
                if (south in 1000.0..2000.0) 1.7 + south * TAN_30 else 0.0
            }
        val far = tilesFor(GeoPoint(OBSERVER.latitude - 40_000.0 / METRES_PER_DEGREE, OBSERVER.longitude), 10, 512)

        val profile = terrain.run(HorizonTracer(OBSERVER)) { it in far }!!

        assertEquals(30.0, profile.angleAt(180.0), 0.05)
        assertTrue(profile.isCompleteAt(180.0))
    }

    @Test
    fun `a missing tile at 20 km behind a 2 degree horizon leaves a lower bound`() {
        val terrain =
            SyntheticTerrain { lat, lon ->
                if (abs(lat - OBSERVER.latitude) > 0.01 || lon < OBSERVER.longitude) {
                    0.0
                } else {
                    val d = SyntheticTerrain.distance(OBSERVER.latitude, OBSERVER.longitude, lat, lon)
                    if (d in 8000.0..8300.0) 285.4 else 0.0
                }
            }
        val east = GeoPoint(OBSERVER.latitude, OBSERVER.longitude + 20_000.0 / (METRES_PER_DEGREE * COS_LAT))
        val missing = tilesFor(east, 11, 512)

        val profile = terrain.run(HorizonTracer(OBSERVER)) { it in missing }!!

        val bin = (90.0 / AZIMUTH_STEP).toInt()
        assertFalse(profile.complete[bin])
        assertEquals(2.0, profile.angles[bin], 0.05)
    }

    @Test
    fun `a missing ground tile gives no profile`() {
        val tracer = HorizonTracer(OBSERVER)
        val ground = tracer.groundTiles()

        val profile = SyntheticTerrain { _, _ -> 0.0 }.run(tracer) { it in ground }

        assertTrue(tracer.isDone)
        assertNull(profile)
    }

    private companion object {
        val OBSERVER = GeoPoint(46.6863, 7.8632)
        val METRES_PER_DEGREE = Math.toRadians(1.0) * SyntheticTerrain.EARTH_RADIUS
        val COS_LAT = kotlin.math.cos(Math.toRadians(OBSERVER.latitude))
        val TAN_30 = kotlin.math.tan(Math.toRadians(30.0))
    }
}
