package com.sunshine.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

// Expected values: swissALTI3D 2 m, investigations/dem-source-evaluation.md.
class ElevationInterpolationTest {
    @ParameterizedTest(name = "{0}")
    @CsvSource(
        "Interlaken,          46.6863,  7.8632,   568.0",
        "Kleine Scheidegg,    46.5853,  7.9610,  2061.3",
        "four-tile corner,    46.55886, 7.91016, 1132.1",
    )
    fun `elevation matches the reference on gentle terrain`(
        name: String,
        latitude: Double,
        longitude: Double,
        expected: Double,
    ) {
        assertEquals(expected, elevationFromFixture(GeoPoint(latitude, longitude)), GENTLE_TOLERANCE)
    }

    @Test
    fun `summit reads at most 10 m low`() {
        val elevation = elevationFromFixture(GeoPoint(46.53679, 7.96258))

        assertTrue(elevation in 4147.8..4158.8, "Jungfrau elevation was $elevation")
    }

    // One 2 x 2 tile at zoom 0: pixel centres at longitude -90 and 90, latitude +-66.51.
    @ParameterizedTest(name = "latitude {0}, longitude {1}")
    @CsvSource(
        "0.0, -90.0, 10.0",
        "0.0, -45.0, 12.5",
        "0.0,   0.0, 15.0",
    )
    fun `bilinear interpolation between pixel centres`(
        latitude: Double,
        longitude: Double,
        expected: Double,
    ) {
        val tile = HeightTile.fromMetres(size = 2, metres = floatArrayOf(0f, 10f, 20f, 30f))

        val elevation = interpolateElevation(GeoPoint(latitude, longitude), 0, 2, mapOf(TileKey(0, 0, 0) to tile))

        assertEquals(expected, elevation, 1e-9)
    }

    @Test
    fun `a missing tile is rejected`() {
        assertThrows<IllegalArgumentException> {
            interpolateElevation(GeoPoint(46.6863, 7.8632), ZOOM, SIZE, emptyMap())
        }
    }

    // Every pixel the fixture does not cover is NaN, so reading a wrong pixel fails visibly.
    private fun elevationFromFixture(point: GeoPoint): Double {
        val tiles =
            ELEVATION_FIXTURE_SAMPLES.groupBy { TileKey(ZOOM, it.x, it.y) }.mapValues { (_, samples) ->
                val heights = FloatArray(SIZE * SIZE) { Float.NaN }
                samples.forEach { heights[it.row * SIZE + it.column] = terrariumHeights(intArrayOf(it.argb))[0] }
                HeightTile.fromMetres(SIZE, heights)
            }
        return interpolateElevation(point, ZOOM, SIZE, tiles)
    }

    private companion object {
        const val ZOOM = 12
        const val SIZE = 512
        const val GENTLE_TOLERANCE = 1.0
    }
}
