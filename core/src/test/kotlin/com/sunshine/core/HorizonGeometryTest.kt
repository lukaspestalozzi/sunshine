package com.sunshine.core

import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// Synthetic terrain with exact expected angles (terrain-horizon spec, "Horizon profile").
class HorizonGeometryTest {
    @Test
    fun `a ridge 1000 m above the eye 5 km to the south`() {
        val terrain =
            SyntheticTerrain { lat, _ ->
                val south = (OBSERVER.latitude - lat) * METRES_PER_DEGREE
                if (south in 5000.0..5300.0) 1001.7 else 0.0
            }

        val profile = terrain.run(HorizonTracer(OBSERVER))!!

        assertEquals(11.29, profile.angleAt(180.0), 0.05)
    }

    @Test
    fun `a peak 4000 m above the eye 100 km to the east is lowered by earth curvature`() {
        val terrain =
            SyntheticTerrain { lat, lon ->
                if (abs(lat - OBSERVER.latitude) > 0.05 || lon < OBSERVER.longitude + 1.0) {
                    0.0
                } else {
                    val d = SyntheticTerrain.distance(OBSERVER.latitude, OBSERVER.longitude, lat, lon)
                    if (d in 100_000.0..100_600.0) 4001.7 else 0.0
                }
            }

        val profile = terrain.run(HorizonTracer(OBSERVER))!!

        assertEquals(1.90, profile.angleAt(90.0), 0.05)
    }

    @Test
    fun `on a flat plain the horizon is the curvature dip in every direction`() {
        val profile = SyntheticTerrain { _, _ -> 0.0 }.run(HorizonTracer(OBSERVER))!!
        // Largest of -(1.7 / d + d (1 - k) / 2R): at d = sqrt(2 * 1.7 * R / (1 - k)).
        val dip = -Math.toDegrees(sqrt(2 * 1.7 * (1 - 0.13) / SyntheticTerrain.EARTH_RADIUS))

        for (azimuth in listOf(0.0, 45.0, 90.0, 180.0, 271.25)) {
            assertEquals(dip, profile.angleAt(azimuth), 0.01, "azimuth $azimuth")
        }
        assertTrue(profile.complete.all { it })
    }

    @Test
    fun `the eye is 1_7 m above the zoom-14 ground height`() {
        val profile =
            SyntheticTerrain { lat, _ -> 100.0 + ((lat - OBSERVER.latitude) * 1000.0).coerceIn(-50.0, 50.0) }
                .run(HorizonTracer(OBSERVER))!!

        assertEquals(101.7, profile.eyeHeight, 0.01)
    }

    private companion object {
        val OBSERVER = GeoPoint(46.6863, 7.8632)
        val METRES_PER_DEGREE = Math.toRadians(1.0) * SyntheticTerrain.EARTH_RADIUS
    }
}
