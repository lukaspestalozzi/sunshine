package com.sunshine.app.data.repository

import com.sunshine.app.data.local.database.ElevationDao
import com.sunshine.app.data.local.database.entities.ElevationEntity
import com.sunshine.app.data.remote.elevation.ElevationApi
import com.sunshine.app.data.remote.elevation.ElevationResult
import com.sunshine.app.domain.model.BoundingBox
import com.sunshine.app.domain.model.GeoPoint
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ElevationRepositoryImplTest {
    private lateinit var elevationDao: ElevationDao
    private lateinit var elevationApi: ElevationApi
    private lateinit var repository: ElevationRepositoryImpl

    private val testPoint = GeoPoint(latitude = 46.8182, longitude = 8.2275)

    private fun createElevationEntity(
        lat: Double,
        lon: Double,
        elevation: Double,
    ) = ElevationEntity(
        gridLat = lat,
        gridLon = lon,
        latitude = lat,
        longitude = lon,
        elevation = elevation,
        source = "open-elevation",
        fetchedAt = System.currentTimeMillis(),
    )

    @Before
    fun setup() {
        elevationDao = mockk(relaxed = true)
        elevationApi = mockk()
        repository = ElevationRepositoryImpl(elevationDao, elevationApi)
    }

    @Test
    fun `getElevation returns cached value when available`() =
        runBlocking {
            // Arrange
            val cachedEntity =
                ElevationEntity(
                    gridLat = 46.8182,
                    gridLon = 8.2275,
                    latitude = 46.8182,
                    longitude = 8.2275,
                    elevation = 550.0,
                    source = "open-elevation",
                    fetchedAt = System.currentTimeMillis(),
                )
            coEvery { elevationDao.getElevation(any(), any()) } returns cachedEntity

            // Act
            val result = repository.getElevation(testPoint)

            // Assert
            assertTrue("Result should be success", result.isSuccess)
            assertEquals(550.0, result.getOrNull()!!, 0.1)
            coVerify(exactly = 0) { elevationApi.getElevation(any()) }
        }

    @Test
    fun `getElevation fetches from API when not cached`() =
        runBlocking {
            // Arrange
            coEvery { elevationDao.getElevation(any(), any()) } returns null
            coEvery { elevationApi.getElevation(testPoint) } returns Result.success(1500.0)

            // Act
            val result = repository.getElevation(testPoint)

            // Assert
            assertTrue("Result should be success", result.isSuccess)
            assertEquals(1500.0, result.getOrNull()!!, 0.1)
            coVerify { elevationApi.getElevation(testPoint) }
        }

    @Test
    fun `getElevation caches API response`() =
        runBlocking {
            // Arrange
            coEvery { elevationDao.getElevation(any(), any()) } returns null
            coEvery { elevationApi.getElevation(testPoint) } returns Result.success(1500.0)
            val entitySlot = slot<ElevationEntity>()
            coEvery { elevationDao.insert(capture(entitySlot)) } returns Unit

            // Act
            repository.getElevation(testPoint)

            // Assert
            coVerify { elevationDao.insert(any()) }
            assertEquals(1500.0, entitySlot.captured.elevation, 0.1)
        }

    @Test
    fun `getElevation returns failure on API error`() =
        runBlocking {
            // Arrange
            coEvery { elevationDao.getElevation(any(), any()) } returns null
            coEvery { elevationApi.getElevation(testPoint) } returns Result.failure(Exception("Network error"))

            // Act
            val result = repository.getElevation(testPoint)

            // Assert
            assertTrue("Result should be failure", result.isFailure)
        }

    @Test
    fun `getElevations returns cached values`() =
        runBlocking {
            // Arrange
            val points = listOf(GeoPoint(46.8, 8.2), GeoPoint(46.9, 8.3))
            coEvery { elevationDao.getElevation(46.8, 8.2) } returns createElevationEntity(46.8, 8.2, 500.0)
            coEvery { elevationDao.getElevation(46.9, 8.3) } returns createElevationEntity(46.9, 8.3, 600.0)

            // Act
            val result = repository.getElevations(points)

            // Assert
            assertTrue("Result should be success", result.isSuccess)
            assertEquals(2, result.getOrNull()?.size)
            coVerify(exactly = 0) { elevationApi.getElevations(any()) }
        }

    @Test
    fun `getElevations fetches missing points from API`() =
        runBlocking {
            // Arrange
            val points =
                listOf(
                    GeoPoint(46.8, 8.2),
                    GeoPoint(46.9, 8.3),
                )

            // Only first point is cached
            val cachedEntity =
                ElevationEntity(
                    gridLat = 46.8,
                    gridLon = 8.2,
                    latitude = 46.8,
                    longitude = 8.2,
                    elevation = 500.0,
                    source = "open-elevation",
                    fetchedAt = System.currentTimeMillis(),
                )
            coEvery { elevationDao.getElevation(46.8, 8.2) } returns cachedEntity
            coEvery { elevationDao.getElevation(46.9, 8.3) } returns null

            // API returns missing point
            val apiResults =
                listOf(
                    ElevationResult(46.9, 8.3, 600.0),
                )
            coEvery { elevationApi.getElevations(any()) } returns Result.success(apiResults)

            // Act
            val result = repository.getElevations(points)

            // Assert
            assertTrue("Result should be success", result.isSuccess)
            val elevations = result.getOrNull()!!
            assertEquals(2, elevations.size)
        }

    @Test
    fun `getElevations returns partial results on API failure`() =
        runBlocking {
            // Arrange
            val points =
                listOf(
                    GeoPoint(46.8, 8.2),
                    GeoPoint(46.9, 8.3),
                )

            val cachedEntity =
                ElevationEntity(
                    gridLat = 46.8,
                    gridLon = 8.2,
                    latitude = 46.8,
                    longitude = 8.2,
                    elevation = 500.0,
                    source = "open-elevation",
                    fetchedAt = System.currentTimeMillis(),
                )
            coEvery { elevationDao.getElevation(46.8, 8.2) } returns cachedEntity
            coEvery { elevationDao.getElevation(46.9, 8.3) } returns null
            coEvery { elevationApi.getElevations(any()) } returns Result.failure(Exception("Network error"))

            // Act
            val result = repository.getElevations(points)

            // Assert - should return partial results with cached data
            assertTrue("Result should be success with partial data", result.isSuccess)
            assertEquals(1, result.getOrNull()?.size)
        }

    @Test
    fun `getElevations returns empty map for empty input`() =
        runBlocking {
            // Act
            val result = repository.getElevations(emptyList())

            // Assert
            assertTrue("Result should be success", result.isSuccess)
            assertTrue("Map should be empty", result.getOrNull()!!.isEmpty())
        }

    @Test
    fun `isAvailableOffline returns true when threshold met`() =
        runBlocking {
            // Arrange: Use tiny bounds so expected count is small
            // Resolution is 0.0003, so 0.0003 x 0.0003 box = ~4 expected points
            val bounds = BoundingBox(north = 46.001, south = 46.0, east = 8.001, west = 8.0)
            // Expected: ((0.001/0.0003)+1) * ((0.001/0.0003)+1) = 4*4 = 16 points
            // Need 80% = 13 points minimum
            coEvery { elevationDao.countInBounds(any(), any(), any(), any()) } returns 16

            // Act
            val result = repository.isAvailableOffline(bounds)

            // Assert - if cached count meets 80% threshold
            assertTrue("Should be available offline", result)
        }

    @Test
    fun `isAvailableOffline returns false when below threshold`() =
        runBlocking {
            // Arrange
            val bounds = BoundingBox(north = 47.0, south = 46.0, east = 9.0, west = 8.0)
            coEvery { elevationDao.countInBounds(any(), any(), any(), any()) } returns 1

            // Act
            val result = repository.isAvailableOffline(bounds)

            // Assert
            assertFalse("Should not be available offline with low cache count", result)
        }

    @Test
    fun `toGridCoordinate truncates to approximately 30m resolution`() {
        // Test the grid coordinate conversion
        val coordinate = 46.81823456

        val gridCoord = ElevationRepositoryImpl.toGridCoordinate(coordinate)

        // Should be truncated to 4 decimal places
        assertEquals(46.8182, gridCoord, 0.0001)
    }
}
