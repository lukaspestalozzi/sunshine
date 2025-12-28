package com.sunshine.app.data.local.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.sunshine.app.data.local.database.entities.DownloadedRegionEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for DownloadedRegionDao using Room in-memory database.
 */
@RunWith(AndroidJUnit4::class)
class DownloadedRegionDaoTest {
    private lateinit var database: SunshineDatabase
    private lateinit var downloadedRegionDao: DownloadedRegionDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            SunshineDatabase::class.java,
        ).allowMainThreadQueries().build()
        downloadedRegionDao = database.downloadedRegionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndRetrieveRegion() = runBlocking {
        // Arrange
        val region = DownloadedRegionEntity(
            regionId = "swiss_alps",
            name = "Swiss Alps",
            centerLat = 46.8182,
            centerLon = 8.2275,
            radiusKm = 50.0,
            minZoom = 8,
            maxZoom = 15,
            totalTiles = 1000,
            downloadedTiles = 1000,
            sizeBytes = 50000000,
            status = "COMPLETED",
            downloadedAt = System.currentTimeMillis(),
        )

        // Act
        downloadedRegionDao.insertOrUpdate(region)
        val retrieved = downloadedRegionDao.getDownloadedRegion("swiss_alps")

        // Assert
        assertNotNull("Retrieved region should not be null", retrieved)
        assertEquals("swiss_alps", retrieved!!.regionId)
        assertEquals("Swiss Alps", retrieved.name)
        assertEquals(1000, retrieved.downloadedTiles)
    }

    @Test
    fun getDownloadedRegion_returnsNullForMissing() = runBlocking {
        // Act
        val result = downloadedRegionDao.getDownloadedRegion("nonexistent")

        // Assert
        assertNull("Should return null for missing region", result)
    }

    @Test
    fun isRegionDownloaded_returnsTrueForExisting() = runBlocking {
        // Arrange
        val region = DownloadedRegionEntity(
            regionId = "test_region",
            name = "Test Region",
            centerLat = 46.0,
            centerLon = 8.0,
            radiusKm = 10.0,
            minZoom = 8,
            maxZoom = 15,
            totalTiles = 100,
            downloadedTiles = 100,
            sizeBytes = 1000000,
            status = "COMPLETED",
            downloadedAt = System.currentTimeMillis(),
        )
        downloadedRegionDao.insertOrUpdate(region)

        // Act
        val exists = downloadedRegionDao.isRegionDownloaded("test_region")

        // Assert
        assertTrue("Region should be marked as downloaded", exists)
    }

    @Test
    fun isRegionDownloaded_returnsFalseForMissing() = runBlocking {
        // Act
        val exists = downloadedRegionDao.isRegionDownloaded("nonexistent")

        // Assert
        assertFalse("Missing region should not be marked as downloaded", exists)
    }

    @Test
    fun updateProgress_updatesCorrectFields() = runBlocking {
        // Arrange
        val region = DownloadedRegionEntity(
            regionId = "downloading_region",
            name = "Downloading Region",
            centerLat = 46.0,
            centerLon = 8.0,
            radiusKm = 10.0,
            minZoom = 8,
            maxZoom = 15,
            totalTiles = 1000,
            downloadedTiles = 0,
            sizeBytes = 0,
            status = "DOWNLOADING",
            downloadedAt = System.currentTimeMillis(),
        )
        downloadedRegionDao.insertOrUpdate(region)

        // Act
        downloadedRegionDao.updateProgress(
            regionId = "downloading_region",
            downloadedTiles = 500,
            sizeBytes = 25000000,
            status = "DOWNLOADING",
        )
        val updated = downloadedRegionDao.getDownloadedRegion("downloading_region")

        // Assert
        assertNotNull(updated)
        assertEquals(500, updated!!.downloadedTiles)
        assertEquals(25000000, updated.sizeBytes)
    }

    @Test
    fun updateStatus_updatesStatus() = runBlocking {
        // Arrange
        val region = DownloadedRegionEntity(
            regionId = "status_test",
            name = "Status Test",
            centerLat = 46.0,
            centerLon = 8.0,
            radiusKm = 10.0,
            minZoom = 8,
            maxZoom = 15,
            totalTiles = 100,
            downloadedTiles = 100,
            sizeBytes = 1000000,
            status = "DOWNLOADING",
            downloadedAt = System.currentTimeMillis(),
        )
        downloadedRegionDao.insertOrUpdate(region)

        // Act
        downloadedRegionDao.updateStatus("status_test", "COMPLETED")
        val updated = downloadedRegionDao.getDownloadedRegion("status_test")

        // Assert
        assertEquals("COMPLETED", updated!!.status)
    }

    @Test
    fun deleteRegion_removesRegion() = runBlocking {
        // Arrange
        val region = DownloadedRegionEntity(
            regionId = "to_delete",
            name = "To Delete",
            centerLat = 46.0,
            centerLon = 8.0,
            radiusKm = 10.0,
            minZoom = 8,
            maxZoom = 15,
            totalTiles = 100,
            downloadedTiles = 100,
            sizeBytes = 1000000,
            status = "COMPLETED",
            downloadedAt = System.currentTimeMillis(),
        )
        downloadedRegionDao.insertOrUpdate(region)

        // Act
        downloadedRegionDao.deleteRegion("to_delete")
        val result = downloadedRegionDao.getDownloadedRegion("to_delete")

        // Assert
        assertNull("Deleted region should not be found", result)
    }

    @Test
    fun getAllDownloadedRegions_emitsUpdates() = runBlocking {
        downloadedRegionDao.getAllDownloadedRegions().test {
            // Initially empty
            assertEquals(0, awaitItem().size)

            // Add first region
            val region1 = DownloadedRegionEntity(
                regionId = "region1",
                name = "Region 1",
                centerLat = 46.0,
                centerLon = 8.0,
                radiusKm = 10.0,
                minZoom = 8,
                maxZoom = 15,
                totalTiles = 100,
                downloadedTiles = 100,
                sizeBytes = 1000000,
                status = "COMPLETED",
                downloadedAt = System.currentTimeMillis(),
            )
            downloadedRegionDao.insertOrUpdate(region1)
            assertEquals(1, awaitItem().size)

            // Add second region
            val region2 = DownloadedRegionEntity(
                regionId = "region2",
                name = "Region 2",
                centerLat = 47.0,
                centerLon = 9.0,
                radiusKm = 20.0,
                minZoom = 8,
                maxZoom = 15,
                totalTiles = 200,
                downloadedTiles = 200,
                sizeBytes = 2000000,
                status = "COMPLETED",
                downloadedAt = System.currentTimeMillis(),
            )
            downloadedRegionDao.insertOrUpdate(region2)
            assertEquals(2, awaitItem().size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getTotalStorageUsed_calculatesCorrectly() = runBlocking {
        downloadedRegionDao.getTotalStorageUsed().test {
            // Initially zero
            assertEquals(0L, awaitItem())

            // Add completed region
            val region = DownloadedRegionEntity(
                regionId = "storage_test",
                name = "Storage Test",
                centerLat = 46.0,
                centerLon = 8.0,
                radiusKm = 10.0,
                minZoom = 8,
                maxZoom = 15,
                totalTiles = 100,
                downloadedTiles = 100,
                sizeBytes = 5000000,
                status = "COMPLETED",
                downloadedAt = System.currentTimeMillis(),
            )
            downloadedRegionDao.insertOrUpdate(region)
            assertEquals(5000000L, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getCompletedDownloads_onlyReturnsCompleted() = runBlocking {
        // Arrange
        val completedRegion = DownloadedRegionEntity(
            regionId = "completed",
            name = "Completed",
            centerLat = 46.0,
            centerLon = 8.0,
            radiusKm = 10.0,
            minZoom = 8,
            maxZoom = 15,
            totalTiles = 100,
            downloadedTiles = 100,
            sizeBytes = 1000000,
            status = "COMPLETED",
            downloadedAt = System.currentTimeMillis(),
        )
        val downloadingRegion = DownloadedRegionEntity(
            regionId = "downloading",
            name = "Downloading",
            centerLat = 47.0,
            centerLon = 9.0,
            radiusKm = 10.0,
            minZoom = 8,
            maxZoom = 15,
            totalTiles = 100,
            downloadedTiles = 50,
            sizeBytes = 500000,
            status = "DOWNLOADING",
            downloadedAt = System.currentTimeMillis(),
        )
        downloadedRegionDao.insertOrUpdate(completedRegion)
        downloadedRegionDao.insertOrUpdate(downloadingRegion)

        // Act & Assert
        downloadedRegionDao.getCompletedDownloads().test {
            val completed = awaitItem()
            assertEquals(1, completed.size)
            assertEquals("completed", completed[0].regionId)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
