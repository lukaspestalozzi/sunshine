package com.sunshine.core

import kotlin.math.exp
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

// Early termination (design D3) must not change the profile, only skip work.
class HorizonTerminationTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("terrains")
    fun `early termination gives the same profile`(
        name: String,
        height: (Double, Double) -> Double,
    ) {
        val withTermination = SyntheticTerrain(height = height).run(HorizonTracer(OBSERVER))!!
        val without = SyntheticTerrain(height = height).run(HorizonTracer(OBSERVER, heightBound = Double.POSITIVE_INFINITY))!!

        assertArrayEquals(without.angles, withTermination.angles, 1e-9)
    }

    @Test
    fun `early termination stays exact for an eye above the height bound`() {
        // A summit at the bound, so the eye is 1.7 m above it, with a lower ridge 3 km to the south.
        val summit = { lat: Double, lon: Double ->
            val d = SyntheticTerrain.distance(OBSERVER.latitude, OBSERVER.longitude, lat, lon)
            val south = Math.toRadians(OBSERVER.latitude - lat) * SyntheticTerrain.EARTH_RADIUS
            if (d < 30.0) {
                3000.0
            } else if (south in 3000.0..3200.0) {
                2995.0
            } else {
                2000.0
            }
        }

        val withTermination = SyntheticTerrain(height = summit).run(HorizonTracer(OBSERVER, heightBound = 3000.0))!!
        val without = SyntheticTerrain(height = summit).run(HorizonTracer(OBSERVER, heightBound = Double.POSITIVE_INFINITY))!!

        assertArrayEquals(without.angles, withTermination.angles, 1e-9)
    }

    @Test
    fun `a high near ridge ends the rays before the far bands`() {
        val terrain = SyntheticTerrain(height = VALLEY)

        terrain.run(HorizonTracer(OBSERVER))

        assertTrue(terrain.requested.none { it.zoom == 10 }, "no zoom-10 tile needed behind 4 km walls")
    }

    @Test
    fun `the height bound is the Alps' highest peak in Europe and Everest elsewhere`() {
        assertEquals(4810.0, heightBoundAt(GeoPoint(46.7, 7.9)))
        assertEquals(4810.0, heightBoundAt(GeoPoint(59.9, 8.6)))
        assertEquals(8849.0, heightBoundAt(GeoPoint(27.9, 86.9)))
        assertEquals(8849.0, heightBoundAt(GeoPoint(43.35, 42.44)))
    }

    private companion object {
        val OBSERVER = GeoPoint(46.6863, 7.8632)

        // A valley: walls rising to 4000 m within 4 km on all sides.
        val VALLEY: (Double, Double) -> Double = { lat, lon ->
            val d = SyntheticTerrain.distance(OBSERVER.latitude, OBSERVER.longitude, lat, lon)
            (d / 1000.0).coerceAtMost(4.0) * 1000.0
        }

        @JvmStatic
        fun terrains() =
            listOf(
                arrayOf("valley", VALLEY),
                arrayOf("plain", { _: Double, _: Double -> 500.0 }),
                arrayOf(
                    "hills",
                    { lat: Double, lon: Double ->
                        600.0 + 1500.0 * exp(-((lat - 46.6) * (lat - 46.6) + (lon - 7.95) * (lon - 7.95)) / 0.002) +
                            2500.0 * exp(-((lat - 46.2) * (lat - 46.2) + (lon - 8.3) * (lon - 8.3)) / 0.01)
                    },
                ),
            )
    }
}
