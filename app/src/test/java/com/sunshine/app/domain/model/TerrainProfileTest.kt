package com.sunshine.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerrainProfileTest {

    // ---- Earth curvature correction tests ----

    @Test
    fun `curvature correction reduces horizon angle at 50km`() {
        // Observer at 1500m, terrain peak at 3000m, 50km away
        val point = TerrainPoint(distance = 50_000.0, elevation = 3000.0)
        val observerElevation = 1500.0

        val correctedAngle = point.angleFromObserver(observerElevation)

        // Flat-earth angle: atan2(1500, 50000) ≈ 1.718°
        val flatEarthAngle = Math.toDegrees(
            kotlin.math.atan2(1500.0, 50_000.0),
        )

        assertTrue(
            "Curvature-corrected angle ($correctedAngle) should be less than " +
                "flat-earth angle ($flatEarthAngle)",
            correctedAngle < flatEarthAngle,
        )
    }

    @Test
    fun `curvature correction is negligible at short distance`() {
        // At 100m, curvature drop ≈ 0.00067m — negligible
        val point = TerrainPoint(distance = 100.0, elevation = 1100.0)
        val observerElevation = 1000.0

        val correctedAngle = point.angleFromObserver(observerElevation)

        // Flat-earth: atan2(100, 100) ≈ 45°
        val flatEarthAngle = Math.toDegrees(
            kotlin.math.atan2(100.0, 100.0),
        )

        assertEquals(
            "At 100m, curvature correction should be negligible",
            flatEarthAngle,
            correctedAngle,
            0.01, // within 0.01°
        )
    }

    @Test
    fun `curvature drop magnitude at 50km matches expected`() {
        // d²/(2·R·k) where R=6371000, k=7/6, d=50000
        // = 50000² / (2 * 6371000 * 7/6) = 2.5e9 / 14865667 ≈ 168.2m
        val d = 50_000.0
        val expectedDrop = d * d /
            (2.0 * TerrainPoint.EARTH_RADIUS_METERS * TerrainPoint.REFRACTION_FACTOR)

        assertEquals(
            "Curvature drop at 50km should be ~168m (with k=7/6)",
            168.0,
            expectedDrop,
            2.0,
        )
    }

    @Test
    fun `horizon angle is negative for same-height terrain at long distance`() {
        // Observer at 1500m, terrain at 1500m, 50km away
        // Due to curvature, the terrain appears ~168m below → negative angle
        val point = TerrainPoint(distance = 50_000.0, elevation = 1500.0)
        val observerElevation = 1500.0

        val angle = point.angleFromObserver(observerElevation)

        assertTrue(
            "Same-height terrain at 50km should appear below horizon (angle=$angle°)",
            angle < 0,
        )
    }

    @Test
    fun `calculateHorizonAngle uses curvature-corrected angles`() {
        // Build a profile with one close point and one far point
        // Close point: 500m away, 100m higher than observer → large positive angle
        // Far point: 50km away, 100m higher than observer → angle reduced by curvature
        val profile = TerrainProfile(
            observer = GeoPoint(46.0, 8.0),
            observerElevation = 1000.0,
            azimuth = 180.0,
            points = listOf(
                TerrainPoint(distance = 500.0, elevation = 1100.0),
                TerrainPoint(distance = 50_000.0, elevation = 1100.0),
            ),
        )

        val horizonAngle = profile.calculateHorizonAngle()

        // Close point dominates: atan2(100, 500) ≈ 11.31°
        // Far point with curvature: atan2(100 - 168, 50000) ≈ atan2(-68, 50000) ≈ -0.08°
        val closeAngle = Math.toDegrees(kotlin.math.atan2(100.0, 500.0))
        assertEquals(
            "Horizon angle should be dominated by close terrain",
            closeAngle,
            horizonAngle,
            0.01,
        )
    }

    @Test
    fun `angle returns zero for zero distance`() {
        val point = TerrainPoint(distance = 0.0, elevation = 2000.0)
        assertEquals(0.0, point.angleFromObserver(1000.0), 0.001)
    }
}
