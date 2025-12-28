package com.sunshine.app.domain.usecase

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
}
