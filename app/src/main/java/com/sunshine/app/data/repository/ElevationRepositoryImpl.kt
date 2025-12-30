package com.sunshine.app.data.repository

import com.sunshine.app.data.local.database.ElevationDao
import com.sunshine.app.data.local.database.entities.ElevationEntity
import com.sunshine.app.data.remote.elevation.ElevationApi
import com.sunshine.app.domain.model.BoundingBox
import com.sunshine.app.domain.model.GeoPoint
import com.sunshine.app.domain.repository.ElevationRepository
import com.sunshine.app.domain.repository.SettingsRepository
import kotlin.math.floor
import kotlinx.coroutines.flow.first

/**
 * Offline-first implementation of ElevationRepository.
 * Checks local cache first, fetches from API if not available.
 * Respects offline mode setting - when enabled, only returns cached data.
 */
class ElevationRepositoryImpl(
    private val elevationDao: ElevationDao,
    private val elevationApi: ElevationApi,
    private val settingsRepository: SettingsRepository,
) : ElevationRepository {
    override suspend fun getElevation(point: GeoPoint): Result<Double> {
        // Check cache first
        val gridLat = toGridCoordinate(point.latitude)
        val gridLon = toGridCoordinate(point.longitude)

        val cached = elevationDao.getElevation(gridLat, gridLon)
        if (cached != null) {
            return Result.success(cached.elevation)
        }

        // Check if offline mode is enabled
        if (settingsRepository.offlineModeEnabled.first()) {
            return Result.failure(OfflineModeException("Elevation data not cached for this location"))
        }

        // Fetch from API
        return elevationApi.getElevation(point)
            .onSuccess { elevation ->
                // Cache the result
                elevationDao.insert(
                    ElevationEntity(
                        gridLat = gridLat,
                        gridLon = gridLon,
                        latitude = point.latitude,
                        longitude = point.longitude,
                        elevation = elevation,
                        source = SOURCE_OPEN_ELEVATION,
                        fetchedAt = System.currentTimeMillis(),
                    ),
                )
            }
    }

    /** Exception thrown when offline mode blocks a network request */
    class OfflineModeException(message: String) : Exception(message)

    override suspend fun getElevations(points: List<GeoPoint>): Result<Map<GeoPoint, Double>> {
        if (points.isEmpty()) {
            return Result.success(emptyMap())
        }

        val (cached, pointsToFetch) = partitionCachedAndMissing(points)
        return fetchMissingAndMerge(cached, pointsToFetch)
    }

    private suspend fun partitionCachedAndMissing(points: List<GeoPoint>): Pair<MutableMap<GeoPoint, Double>, List<GeoPoint>> {
        val cached = mutableMapOf<GeoPoint, Double>()
        val missing = mutableListOf<GeoPoint>()

        for (point in points) {
            val gridLat = toGridCoordinate(point.latitude)
            val gridLon = toGridCoordinate(point.longitude)
            val cachedValue = elevationDao.getElevation(gridLat, gridLon)

            if (cachedValue != null) {
                cached[point] = cachedValue.elevation
            } else {
                missing.add(point)
            }
        }
        return cached to missing
    }

    private suspend fun fetchMissingAndMerge(
        cached: MutableMap<GeoPoint, Double>,
        pointsToFetch: List<GeoPoint>,
    ): Result<Map<GeoPoint, Double>> {
        if (pointsToFetch.isEmpty()) {
            return Result.success(cached)
        }

        // Check if offline mode is enabled
        if (settingsRepository.offlineModeEnabled.first()) {
            // In offline mode, return cached data only (don't fail if we have some data)
            return if (cached.isNotEmpty()) {
                Result.success(cached)
            } else {
                Result.failure(OfflineModeException("Elevation data not cached for these locations"))
            }
        }

        return fetchElevationsInBatches(pointsToFetch).fold(
            onSuccess = { fetched ->
                cached.putAll(fetched)
                Result.success(cached)
            },
            onFailure = { error ->
                // Return partial results if we have some cached data
                if (cached.isNotEmpty()) Result.success(cached) else Result.failure(error)
            },
        )
    }

    @Suppress("ReturnCount") // Multiple return paths for offline-first logic with partial results
    override suspend fun getElevationGrid(
        bounds: BoundingBox,
        resolution: Double,
    ): Result<Map<GeoPoint, Double>> {
        val result = mutableMapOf<GeoPoint, Double>()
        val pointsToFetch = mutableListOf<GeoPoint>()

        // Generate grid points
        val gridPoints = generateGridPoints(bounds, resolution)

        // Check cache for each point
        for (point in gridPoints) {
            val gridLat = toGridCoordinate(point.latitude)
            val gridLon = toGridCoordinate(point.longitude)

            val cached = elevationDao.getElevation(gridLat, gridLon)
            if (cached != null) {
                result[point] = cached.elevation
            } else {
                pointsToFetch.add(point)
            }
        }

        // Fetch missing points from API in batches
        if (pointsToFetch.isNotEmpty()) {
            // Check if offline mode is enabled
            if (settingsRepository.offlineModeEnabled.first()) {
                // In offline mode, return cached data only
                return if (result.isNotEmpty()) {
                    Result.success(result)
                } else {
                    Result.failure(OfflineModeException("Elevation grid data not cached for this area"))
                }
            }

            val batchResults = fetchElevationsInBatches(pointsToFetch)
            batchResults.onSuccess { elevations ->
                result.putAll(elevations)
            }.onFailure { error ->
                // If we have some cached data, return partial results
                if (result.isNotEmpty()) {
                    return Result.success(result)
                }
                return Result.failure(error)
            }
        }

        return Result.success(result)
    }

    override suspend fun isAvailableOffline(bounds: BoundingBox): Boolean {
        // Count expected grid points vs cached points
        val expectedCount = calculateExpectedGridPoints(bounds, DEFAULT_RESOLUTION)
        val cachedCount =
            elevationDao.countInBounds(
                north = bounds.north,
                south = bounds.south,
                east = bounds.east,
                west = bounds.west,
            )
        // Consider offline available if at least 80% of expected points are cached
        return cachedCount >= (expectedCount * OFFLINE_THRESHOLD)
    }

    private suspend fun fetchElevationsInBatches(points: List<GeoPoint>): Result<Map<GeoPoint, Double>> {
        val result = mutableMapOf<GeoPoint, Double>()
        val batches = points.chunked(ElevationApi.MAX_POINTS_PER_REQUEST)

        for (batch in batches) {
            elevationApi.getElevations(batch)
                .onSuccess { elevations ->
                    val entities =
                        elevations.map { elevation ->
                            val point = GeoPoint(elevation.latitude, elevation.longitude)
                            result[point] = elevation.elevation
                            ElevationEntity(
                                gridLat = toGridCoordinate(elevation.latitude),
                                gridLon = toGridCoordinate(elevation.longitude),
                                latitude = elevation.latitude,
                                longitude = elevation.longitude,
                                elevation = elevation.elevation,
                                source = SOURCE_OPEN_ELEVATION,
                                fetchedAt = System.currentTimeMillis(),
                            )
                        }
                    elevationDao.insertAll(entities)
                }
                .onFailure { error ->
                    return Result.failure(error)
                }
        }

        return Result.success(result)
    }

    private fun generateGridPoints(
        bounds: BoundingBox,
        resolution: Double,
    ): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        var lat = bounds.south

        while (lat <= bounds.north) {
            var lon = bounds.west
            while (lon <= bounds.east) {
                points.add(GeoPoint(lat, lon))
                lon += resolution
            }
            lat += resolution
        }

        return points
    }

    private fun calculateExpectedGridPoints(
        bounds: BoundingBox,
        resolution: Double,
    ): Int {
        val latCount = ((bounds.north - bounds.south) / resolution).toInt() + 1
        val lonCount = ((bounds.east - bounds.west) / resolution).toInt() + 1
        return latCount * lonCount
    }

    companion object {
        const val SOURCE_OPEN_ELEVATION = "open-elevation"

        /** Default grid resolution in degrees (~30m at equator) */
        const val DEFAULT_RESOLUTION = 0.0003

        /** Threshold for considering data offline-available */
        const val OFFLINE_THRESHOLD = 0.8

        /**
         * Convert coordinate to grid-aligned coordinate.
         * This truncates to ~30m resolution for caching.
         */
        @Suppress("MagicNumber") // Grid precision factor for 4 decimal places (~11m)
        fun toGridCoordinate(coordinate: Double): Double {
            val factor = 10000.0
            return floor(coordinate * factor) / factor
        }
    }
}
