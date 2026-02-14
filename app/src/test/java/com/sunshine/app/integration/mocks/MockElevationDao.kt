package com.sunshine.app.integration.mocks

import com.sunshine.app.data.local.database.ElevationDao
import com.sunshine.app.data.local.database.entities.ElevationEntity

/**
 * HashMap-backed implementation of [ElevationDao] for JVM integration tests.
 * Avoids Room/Android dependency while exercising real caching logic.
 */
class MockElevationDao : ElevationDao {
    private val store = mutableMapOf<Pair<Double, Double>, ElevationEntity>()

    override suspend fun getElevation(
        gridLat: Double,
        gridLon: Double,
    ): ElevationEntity? = store[gridLat to gridLon]

    override suspend fun getElevationsInBounds(
        north: Double,
        south: Double,
        east: Double,
        west: Double,
    ): List<ElevationEntity> =
        store.values.filter { entity ->
            entity.gridLat in south..north && entity.gridLon in west..east
        }

    override suspend fun insertAll(entities: List<ElevationEntity>) {
        entities.forEach { insert(it) }
    }

    override suspend fun insert(entity: ElevationEntity) {
        store[entity.gridLat to entity.gridLon] = entity
    }

    override suspend fun countInBounds(
        north: Double,
        south: Double,
        east: Double,
        west: Double,
    ): Int =
        store.values.count { entity ->
            entity.gridLat in south..north && entity.gridLon in west..east
        }

    override suspend fun deleteOlderThan(olderThan: Long) {
        store.entries.removeAll { (_, entity) -> entity.fetchedAt < olderThan }
    }
}
