package com.sunshine.app.data.local.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sunshine.app.data.local.database.entities.ElevationEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for ElevationDao using Room in-memory database.
 */
@RunWith(AndroidJUnit4::class)
class ElevationDaoTest {
    private lateinit var database: SunshineDatabase
    private lateinit var elevationDao: ElevationDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room.inMemoryDatabaseBuilder(
                context,
                SunshineDatabase::class.java,
            ).allowMainThreadQueries().build()
        elevationDao = database.elevationDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndRetrieveElevation() =
        runBlocking {
            // Arrange
            val entity =
                ElevationEntity(
                    gridLat = 46.8182,
                    gridLon = 8.2275,
                    latitude = 46.8182,
                    longitude = 8.2275,
                    elevation = 1500.0,
                    source = "open-elevation",
                    fetchedAt = System.currentTimeMillis(),
                )

            // Act
            elevationDao.insert(entity)
            val retrieved = elevationDao.getElevation(46.8182, 8.2275)

            // Assert
            assertNotNull("Retrieved entity should not be null", retrieved)
            assertEquals(1500.0, retrieved!!.elevation, 0.1)
        }

    @Test
    fun getElevation_returnsNullForMissing() =
        runBlocking {
            // Act
            val result = elevationDao.getElevation(99.0, 99.0)

            // Assert
            assertNull("Should return null for missing elevation", result)
        }

    @Test
    fun insertAll_storesMultipleEntities() =
        runBlocking {
            // Arrange
            val entities =
                listOf(
                    ElevationEntity(
                        gridLat = 46.0,
                        gridLon = 8.0,
                        latitude = 46.0,
                        longitude = 8.0,
                        elevation = 500.0,
                        source = "open-elevation",
                        fetchedAt = System.currentTimeMillis(),
                    ),
                    ElevationEntity(
                        gridLat = 46.1,
                        gridLon = 8.1,
                        latitude = 46.1,
                        longitude = 8.1,
                        elevation = 600.0,
                        source = "open-elevation",
                        fetchedAt = System.currentTimeMillis(),
                    ),
                    ElevationEntity(
                        gridLat = 46.2,
                        gridLon = 8.2,
                        latitude = 46.2,
                        longitude = 8.2,
                        elevation = 700.0,
                        source = "open-elevation",
                        fetchedAt = System.currentTimeMillis(),
                    ),
                )

            // Act
            elevationDao.insertAll(entities)

            // Assert
            val retrieved1 = elevationDao.getElevation(46.0, 8.0)
            val retrieved2 = elevationDao.getElevation(46.1, 8.1)
            val retrieved3 = elevationDao.getElevation(46.2, 8.2)

            assertNotNull(retrieved1)
            assertNotNull(retrieved2)
            assertNotNull(retrieved3)
            assertEquals(500.0, retrieved1!!.elevation, 0.1)
            assertEquals(600.0, retrieved2!!.elevation, 0.1)
            assertEquals(700.0, retrieved3!!.elevation, 0.1)
        }

    @Test
    fun getElevationsInBounds_returnsCorrectEntities() =
        runBlocking {
            // Arrange
            val entities =
                listOf(
                    ElevationEntity(
                        gridLat = 46.5,
                        gridLon = 8.5,
                        latitude = 46.5,
                        longitude = 8.5,
                        elevation = 1000.0,
                        source = "open-elevation",
                        fetchedAt = System.currentTimeMillis(),
                    ),
                    ElevationEntity(
                        gridLat = 47.5,
                        gridLon = 9.5,
                        latitude = 47.5,
                        longitude = 9.5,
                        elevation = 2000.0,
                        source = "open-elevation",
                        fetchedAt = System.currentTimeMillis(),
                    ),
                    ElevationEntity(
                        gridLat = 50.0,
                        gridLon = 10.0,
                        latitude = 50.0,
                        longitude = 10.0,
                        elevation = 3000.0,
                        source = "open-elevation",
                        fetchedAt = System.currentTimeMillis(),
                    ),
                )
            elevationDao.insertAll(entities)

            // Act - query within bounds that include first two points
            val inBounds =
                elevationDao.getElevationsInBounds(
                    north = 48.0,
                    south = 46.0,
                    east = 10.0,
                    west = 8.0,
                )

            // Assert
            assertEquals(2, inBounds.size)
        }

    @Test
    fun countInBounds_returnsCorrectCount() =
        runBlocking {
            // Arrange
            val entities =
                listOf(
                    ElevationEntity(
                        gridLat = 46.5,
                        gridLon = 8.5,
                        latitude = 46.5,
                        longitude = 8.5,
                        elevation = 1000.0,
                        source = "open-elevation",
                        fetchedAt = System.currentTimeMillis(),
                    ),
                    ElevationEntity(
                        gridLat = 46.6,
                        gridLon = 8.6,
                        latitude = 46.6,
                        longitude = 8.6,
                        elevation = 1100.0,
                        source = "open-elevation",
                        fetchedAt = System.currentTimeMillis(),
                    ),
                )
            elevationDao.insertAll(entities)

            // Act
            val count =
                elevationDao.countInBounds(
                    north = 47.0,
                    south = 46.0,
                    east = 9.0,
                    west = 8.0,
                )

            // Assert
            assertEquals(2, count)
        }

    @Test
    fun insert_replacesExistingOnConflict() =
        runBlocking {
            // Arrange
            val original =
                ElevationEntity(
                    gridLat = 46.0,
                    gridLon = 8.0,
                    latitude = 46.0,
                    longitude = 8.0,
                    elevation = 500.0,
                    source = "open-elevation",
                    fetchedAt = System.currentTimeMillis(),
                )
            val updated =
                ElevationEntity(
                    gridLat = 46.0,
                    gridLon = 8.0,
                    latitude = 46.0,
                    longitude = 8.0,
                    elevation = 600.0,
                    source = "open-elevation",
                    fetchedAt = System.currentTimeMillis(),
                )

            // Act
            elevationDao.insert(original)
            elevationDao.insert(updated)
            val retrieved = elevationDao.getElevation(46.0, 8.0)

            // Assert
            assertEquals(600.0, retrieved!!.elevation, 0.1)
        }

    @Test
    fun deleteOlderThan_removesOldEntries() =
        runBlocking {
            // Arrange
            val oldTimestamp = System.currentTimeMillis() - 100000
            val newTimestamp = System.currentTimeMillis()

            val oldEntity =
                ElevationEntity(
                    gridLat = 46.0,
                    gridLon = 8.0,
                    latitude = 46.0,
                    longitude = 8.0,
                    elevation = 500.0,
                    source = "open-elevation",
                    fetchedAt = oldTimestamp,
                )
            val newEntity =
                ElevationEntity(
                    gridLat = 47.0,
                    gridLon = 9.0,
                    latitude = 47.0,
                    longitude = 9.0,
                    elevation = 600.0,
                    source = "open-elevation",
                    fetchedAt = newTimestamp,
                )
            elevationDao.insertAll(listOf(oldEntity, newEntity))

            // Act - delete entries older than (current - 50000)
            elevationDao.deleteOlderThan(System.currentTimeMillis() - 50000)

            // Assert
            assertNull(elevationDao.getElevation(46.0, 8.0))
            assertNotNull(elevationDao.getElevation(47.0, 9.0))
        }
}
