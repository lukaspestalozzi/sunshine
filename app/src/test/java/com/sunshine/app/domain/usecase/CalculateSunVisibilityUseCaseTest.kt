package com.sunshine.app.domain.usecase

import com.sunshine.app.domain.model.BoundingBox
import com.sunshine.app.domain.model.GeoPoint
import com.sunshine.app.domain.model.SunPosition
import com.sunshine.app.domain.repository.ElevationRepository
import com.sunshine.app.suncalc.SunCalculator
import io.mockk.coEvery
import io.mockk.mockk
import java.time.LocalDateTime
import java.time.Month
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CalculateSunVisibilityUseCaseTest {
    private lateinit var sunCalculator: SunCalculator
    private lateinit var elevationRepository: ElevationRepository
    private lateinit var useCase: CalculateSunVisibilityUseCase

    private val testLocation = GeoPoint(latitude = 46.8182, longitude = 8.2275)
    private val testDateTime = LocalDateTime.of(2024, Month.JUNE, 21, 12, 0)

    @Before
    fun setup() {
        sunCalculator = mockk()
        elevationRepository = mockk()
        useCase = CalculateSunVisibilityUseCase(sunCalculator, elevationRepository)
    }

    @Test
    fun `returns sun visible when sun is above terrain`() =
        runBlocking {
            // Arrange
            val sunPosition = SunPosition(azimuth = 180.0, elevation = 60.0)
            coEvery { sunCalculator.calculateSunPosition(any(), any()) } returns sunPosition
            coEvery { elevationRepository.getElevation(any()) } returns Result.success(1000.0)
            coEvery {
                elevationRepository.getElevations(any())
            } returns
                Result.success(
                    // All terrain points at same elevation as observer (flat terrain)
                    mapOf(),
                )

            // Act
            val result = useCase.calculateVisibility(testLocation, testDateTime)

            // Assert
            assertTrue("Result should be success", result.isSuccess)
            val visibility = result.getOrNull()
            assertNotNull("Visibility should not be null", visibility)
            assertTrue("Sun should be visible with flat terrain", visibility!!.isSunVisible)
        }

    @Test
    fun `returns sun blocked when terrain is higher than sun elevation`() =
        runBlocking {
            // Arrange: Sun at 10° elevation, but terrain creating high horizon angle
            val sunPosition = SunPosition(azimuth = 180.0, elevation = 10.0)
            coEvery { sunCalculator.calculateSunPosition(any(), any()) } returns sunPosition
            coEvery { elevationRepository.getElevation(any()) } returns Result.success(500.0)

            // Mock getElevations to return high terrain for ANY requested points
            // The use case calls projectPoint() to generate sample points, so we use coEvery with any()
            // Return a map with very high elevations for each point requested
            coEvery { elevationRepository.getElevations(any()) } answers {
                val requestedPoints = firstArg<List<GeoPoint>>()
                // Return extremely high elevations to create a steep horizon angle
                // At 100m distance, 1000m height difference = atan(1000/100) ≈ 84° horizon
                val elevations = requestedPoints.associateWith { 5000.0 }
                Result.success(elevations)
            }

            // Act
            val result = useCase.calculateVisibility(testLocation, testDateTime)

            // Assert
            assertTrue("Result should be success", result.isSuccess)
            val visibility = result.getOrNull()
            assertNotNull("Visibility should not be null", visibility)
            assertFalse("Sun should be blocked by high terrain", visibility!!.isSunVisible)
            assertNotNull("Degrees until visible should be set", visibility.degreesUntilVisible)
        }

    @Test
    fun `returns below horizon when sun is below horizon`() =
        runBlocking {
            // Arrange: Sun below horizon
            val sunPosition = SunPosition(azimuth = 0.0, elevation = -10.0)
            coEvery { sunCalculator.calculateSunPosition(any(), any()) } returns sunPosition

            // Act
            val result = useCase.calculateVisibility(testLocation, testDateTime)

            // Assert
            assertTrue("Result should be success", result.isSuccess)
            val visibility = result.getOrNull()
            assertNotNull("Visibility should not be null", visibility)
            assertFalse("Sun should not be visible when below horizon", visibility!!.isSunVisible)
        }

    @Test
    fun `uses default elevation when repository fails`() =
        runBlocking {
            // Arrange
            val sunPosition = SunPosition(azimuth = 180.0, elevation = 45.0)
            coEvery { sunCalculator.calculateSunPosition(any(), any()) } returns sunPosition
            coEvery { elevationRepository.getElevation(any()) } returns Result.failure(Exception("Network error"))
            coEvery { elevationRepository.getElevations(any()) } returns Result.failure(Exception("Network error"))

            // Act
            val result = useCase.calculateVisibility(testLocation, testDateTime)

            // Assert - should still succeed with default elevation
            assertTrue("Result should still succeed with defaults", result.isSuccess)
            val visibility = result.getOrNull()
            assertNotNull("Visibility should not be null", visibility)
        }

    @Test
    fun `visibility result contains correct sun position`() =
        runBlocking {
            // Arrange
            val sunPosition = SunPosition(azimuth = 135.5, elevation = 42.3)
            coEvery { sunCalculator.calculateSunPosition(any(), any()) } returns sunPosition
            coEvery { elevationRepository.getElevation(any()) } returns Result.success(1000.0)
            coEvery { elevationRepository.getElevations(any()) } returns Result.success(emptyMap())

            // Act
            val result = useCase.calculateVisibility(testLocation, testDateTime)

            // Assert
            val visibility = result.getOrNull()!!
            assertEquals("Azimuth should match", 135.5, visibility.sunPosition.azimuth, 0.01)
            assertEquals("Elevation should match", 42.3, visibility.sunPosition.elevation, 0.01)
        }

    @Test
    fun `visibility result contains correct location`() =
        runBlocking {
            // Arrange
            val sunPosition = SunPosition(azimuth = 180.0, elevation = 50.0)
            coEvery { sunCalculator.calculateSunPosition(any(), any()) } returns sunPosition
            coEvery { elevationRepository.getElevation(any()) } returns Result.success(500.0)
            coEvery { elevationRepository.getElevations(any()) } returns Result.success(emptyMap())

            // Act
            val result = useCase.calculateVisibility(testLocation, testDateTime)

            // Assert
            val visibility = result.getOrNull()!!
            assertEquals("Latitude should match", testLocation.latitude, visibility.location.latitude, 0.0001)
            assertEquals("Longitude should match", testLocation.longitude, visibility.location.longitude, 0.0001)
        }

    // ---- Grid calculation tests ----

    @Test
    fun `grid returns success with correct bounds and resolution`() =
        runBlocking {
            setupSunAboveHorizon()
            setupFlatTerrain()

            val bounds = smallBounds
            val result = useCase.calculateVisibilityGrid(bounds, testDateTime, resolution = 0.01)

            assertTrue("Grid result should be success", result.isSuccess)
            val grid = result.getOrNull()!!
            assertEquals(bounds, grid.bounds)
            assertEquals(0.01, grid.resolution, 0.0001)
        }

    @Test
    fun `grid produces correct number of points`() =
        runBlocking {
            setupSunAboveHorizon()
            setupFlatTerrain()

            // Bounds: 0.02° x 0.02° with resolution 0.01
            // Expected: 3 lat steps (46.0, 46.01, 46.02) x 3 lon steps (8.0, 8.01, 8.02) = 9
            val bounds = BoundingBox(north = 46.02, south = 46.0, east = 8.02, west = 8.0)
            val result = useCase.calculateVisibilityGrid(bounds, testDateTime, resolution = 0.01)

            val grid = result.getOrNull()!!
            assertEquals("Grid should have 9 points (3x3)", 9, grid.points.size)
        }

    @Test
    fun `grid points all visible with flat terrain and high sun`() =
        runBlocking {
            setupSunAboveHorizon()
            setupFlatTerrain()

            val result = useCase.calculateVisibilityGrid(smallBounds, testDateTime, resolution = 0.01)

            val grid = result.getOrNull()!!
            assertTrue("All grid points should be visible", grid.points.values.all { it })
        }

    @Test
    fun `grid points all not visible when sun below horizon`() =
        runBlocking {
            val sunBelow = SunPosition(azimuth = 0.0, elevation = -10.0)
            coEvery { sunCalculator.calculateSunPosition(any(), any()) } returns sunBelow

            val result = useCase.calculateVisibilityGrid(smallBounds, testDateTime, resolution = 0.01)

            val grid = result.getOrNull()!!
            assertTrue("All grid points should be false when sun is below horizon", grid.points.values.none { it })
        }

    @Test
    fun `grid handles single-point failure gracefully`() =
        runBlocking {
            // Alternate between success and failure for elevation lookups
            val sunPosition = SunPosition(azimuth = 180.0, elevation = 60.0)
            coEvery { sunCalculator.calculateSunPosition(any(), any()) } returns sunPosition
            coEvery { elevationRepository.getElevation(any()) } returns Result.success(1000.0)

            var callCount = 0
            coEvery { elevationRepository.getElevations(any()) } answers {
                callCount++
                if (callCount % 2 == 0) {
                    Result.failure(Exception("Intermittent failure"))
                } else {
                    Result.success(emptyMap())
                }
            }

            val result = useCase.calculateVisibilityGrid(smallBounds, testDateTime, resolution = 0.01)

            // Grid should still succeed - individual failures default to false
            assertTrue("Grid result should be success even with intermittent failures", result.isSuccess)
        }

    @Test
    fun `grid covers all corners of bounding box`() =
        runBlocking {
            setupSunAboveHorizon()
            setupFlatTerrain()

            val bounds = BoundingBox(north = 46.02, south = 46.0, east = 8.02, west = 8.0)
            val result = useCase.calculateVisibilityGrid(bounds, testDateTime, resolution = 0.01)
            val grid = result.getOrNull()!!

            val lats = grid.points.keys.map { it.latitude }.toSet()
            val lons = grid.points.keys.map { it.longitude }.toSet()

            assertTrue("Grid should include south boundary", lats.any { it <= 46.001 })
            assertTrue("Grid should include north boundary", lats.any { it >= 46.019 })
            assertTrue("Grid should include west boundary", lons.any { it <= 8.001 })
            assertTrue("Grid should include east boundary", lons.any { it >= 8.019 })
        }

    // ---- Atmospheric refraction tests ----

    @Test
    fun `refraction makes sun visible when geometric elevation is slightly below horizon angle`() =
        runBlocking {
            // Sun geometric elevation 2.0°, refraction at 2° ≈ 0.28° → apparent ≈ 2.28°
            // Terrain creates horizon angle of ~2.15° (below apparent, above geometric)
            // Without refraction: 2.0 < 2.15 → blocked
            // With refraction: 2.28 > 2.15 → visible
            val sunPosition = SunPosition(azimuth = 180.0, elevation = 2.0)
            coEvery { sunCalculator.calculateSunPosition(any(), any()) } returns sunPosition
            coEvery { elevationRepository.getElevation(any()) } returns Result.success(1000.0)
            coEvery { elevationRepository.getElevations(any()) } answers {
                val points = firstArg<List<GeoPoint>>()
                // Observer at 1000m. At 100m distance (closest sample),
                // atan2(3.75, 100) ≈ 2.15° horizon angle
                Result.success(points.associateWith { 1003.75 })
            }

            val result = useCase.calculateVisibility(testLocation, testDateTime)
            val visibility = result.getOrNull()!!

            assertTrue(
                "Refraction should make sun visible (apparent elev 2.28° > horizon 2.15°)",
                visibility.isSunVisible,
            )
        }

    @Test
    fun `refraction correction is negligible at high elevation`() {
        // At 60° elevation, refraction should be < 0.05°
        val refraction =
            CalculateSunVisibilityUseCase.atmosphericRefraction(60.0)
        assertTrue(
            "Refraction at 60° should be < 0.05°, was $refraction",
            refraction < 0.05,
        )
    }

    @Test
    fun `refraction correction is approximately 0_57 degrees at horizon`() {
        // Standard atmospheric refraction at 0° elevation ≈ 0.57°
        val refraction =
            CalculateSunVisibilityUseCase.atmosphericRefraction(0.0)
        assertEquals(
            "Refraction at horizon should be ~0.57°",
            0.57,
            refraction,
            0.1,
        )
    }

    @Test
    fun `refraction is zero for sun well below horizon`() {
        val refraction =
            CalculateSunVisibilityUseCase.atmosphericRefraction(-10.0)
        assertEquals(
            "Refraction below -1° should be 0",
            0.0,
            refraction,
            0.001,
        )
    }

    private fun setupSunAboveHorizon() {
        val sunAbove = SunPosition(azimuth = 180.0, elevation = 60.0)
        coEvery { sunCalculator.calculateSunPosition(any(), any()) } returns sunAbove
    }

    private fun setupFlatTerrain() {
        coEvery { elevationRepository.getElevation(any()) } returns Result.success(1000.0)
        coEvery { elevationRepository.getElevations(any()) } returns Result.success(emptyMap())
    }

    companion object {
        private val smallBounds =
            BoundingBox(
                north = 46.02,
                south = 46.0,
                east = 8.02,
                west = 8.0,
            )
    }
}
