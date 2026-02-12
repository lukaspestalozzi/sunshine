package com.sunshine.app.domain.usecase

import com.sunshine.app.domain.model.BoundingBox
import com.sunshine.app.domain.model.GeoPoint
import com.sunshine.app.domain.model.TerrainPoint
import com.sunshine.app.domain.model.TerrainProfile
import com.sunshine.app.domain.model.VisibilityGrid
import com.sunshine.app.domain.model.VisibilityResult
import com.sunshine.app.domain.repository.ElevationRepository
import com.sunshine.app.suncalc.SunCalculator
import java.time.LocalDateTime
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Use case for calculating sun visibility at a point or grid of points.
 * Combines sun position calculation with terrain elevation data.
 *
 * Performance optimizations:
 * - Batch elevation fetching for terrain profiles
 * - Parallel grid calculations using coroutines
 */
class CalculateSunVisibilityUseCase(
    private val sunCalculator: SunCalculator,
    private val elevationRepository: ElevationRepository,
) {
    /**
     * Calculate sun visibility at a single point.
     */
    suspend fun calculateVisibility(
        location: GeoPoint,
        dateTime: LocalDateTime,
    ): Result<VisibilityResult> =
        runCatching {
            // Get sun position
            val sunPosition = sunCalculator.calculateSunPosition(location, dateTime)

            // If sun is below horizon, no need to check terrain
            if (!sunPosition.isAboveHorizon) {
                return@runCatching VisibilityResult.belowHorizon(location, sunPosition)
            }

            // Get observer elevation (track failures for degraded-data indicator)
            val observerElevationResult = elevationRepository.getElevation(location)
            val observerDegraded = observerElevationResult.isFailure
            val observerElevation =
                observerElevationResult.getOrElse { DEFAULT_OBSERVER_ELEVATION }

            // Get terrain profile in sun's direction (optimized with batch fetching)
            val profileResult =
                getTerrainProfileBatch(location, observerElevation, sunPosition.azimuth)
            val terrainProfile = profileResult.first
            val terrainDegraded = profileResult.second
            val isElevationDegraded = observerDegraded || terrainDegraded

            // Check if terrain blocks the sun (accounting for atmospheric refraction)
            val horizonAngle = terrainProfile.calculateHorizonAngle()
            val apparentElevation =
                sunPosition.elevation + atmosphericRefraction(sunPosition.elevation)
            val isSunVisible = apparentElevation > horizonAngle

            if (isSunVisible) {
                VisibilityResult.visible(
                    location = location,
                    sunPosition = sunPosition,
                    horizonAngle = horizonAngle,
                    isElevationDegraded = isElevationDegraded,
                )
            } else {
                val degreesUntilVisible = horizonAngle - apparentElevation
                VisibilityResult.blocked(
                    location = location,
                    sunPosition = sunPosition,
                    horizonAngle = horizonAngle,
                    degreesUntilVisible = degreesUntilVisible,
                    isElevationDegraded = isElevationDegraded,
                )
            }
        }

    /**
     * Calculate visibility grid for rendering as map overlay.
     * Uses parallel processing for improved performance.
     */
    suspend fun calculateVisibilityGrid(
        bounds: BoundingBox,
        dateTime: LocalDateTime,
        resolution: Double = VisibilityGrid.DEFAULT_RESOLUTION,
    ): Result<VisibilityGrid> =
        runCatching {
            // Generate grid points
            val gridPoints = generateGridPoints(bounds, resolution)

            // Calculate visibility in parallel for better performance
            val results =
                coroutineScope {
                    gridPoints.map { point ->
                        async {
                            val visibility =
                                calculateVisibility(point, dateTime)
                                    .getOrNull()
                                    ?.isSunVisible
                                    ?: false
                            point to visibility
                        }
                    }.awaitAll()
                }

            val points = results.toMap()

            VisibilityGrid(
                bounds = bounds,
                resolution = resolution,
                points = points,
            )
        }

    /**
     * Generate grid points for a bounding box.
     */
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

    /**
     * Get terrain profile using batch elevation fetching for better performance.
     * Fetches all sample points in a single API call instead of multiple sequential calls.
     *
     * @return Pair of (TerrainProfile, isDegraded) where isDegraded is true
     *         if the batch elevation lookup failed.
     */
    private suspend fun getTerrainProfileBatch(
        observer: GeoPoint,
        observerElevation: Double,
        azimuth: Double,
    ): Pair<TerrainProfile, Boolean> {
        // Generate all sample points first
        val samplePoints =
            SAMPLE_DISTANCES.map { distance ->
                distance to projectPoint(observer, azimuth, distance)
            }

        // Batch fetch elevations for all points
        val pointsList = samplePoints.map { it.second }
        val elevationsResult = elevationRepository.getElevations(pointsList)
        val isDegraded = elevationsResult.isFailure
        val elevations = elevationsResult.getOrElse { emptyMap() }

        // Build terrain profile with fetched elevations
        val terrainPoints =
            samplePoints.map { (distance, point) ->
                TerrainPoint(
                    distance = distance,
                    elevation = elevations[point] ?: observerElevation,
                )
            }

        val profile = TerrainProfile(
            observer = observer,
            observerElevation = observerElevation,
            azimuth = azimuth,
            points = terrainPoints,
        )
        return profile to isDegraded
    }

    /**
     * Project a point from origin in a given direction and distance.
     */
    @Suppress("MagicNumber") // Standard coordinate bounds (-90/90 lat, -180/180 lon)
    private fun projectPoint(
        origin: GeoPoint,
        azimuth: Double,
        distanceMeters: Double,
    ): GeoPoint {
        // Convert azimuth to radians (0 = North)
        val azimuthRad = Math.toRadians(azimuth)

        // Approximate degrees per meter at this latitude
        val latDegPerMeter = 1.0 / METERS_PER_DEGREE_LAT
        val lonDegPerMeter = 1.0 / (METERS_PER_DEGREE_LAT * cos(Math.toRadians(origin.latitude)))

        // Calculate offset
        val deltaLat = distanceMeters * cos(azimuthRad) * latDegPerMeter
        val deltaLon = distanceMeters * sin(azimuthRad) * lonDegPerMeter

        return GeoPoint(
            latitude = (origin.latitude + deltaLat).coerceIn(-90.0, 90.0),
            longitude = (origin.longitude + deltaLon).coerceIn(-180.0, 180.0),
        )
    }

    @Suppress("MagicNumber") // Terrain sampling distances are domain constants
    companion object {
        /** Default observer height above ground if elevation lookup fails */
        const val DEFAULT_OBSERVER_ELEVATION = 0.0

        /** Meters per degree of latitude (approximately) */
        const val METERS_PER_DEGREE_LAT = 111320.0

        /**
         * Sample distances for terrain profile (in meters).
         * Start close, increase logarithmically for efficiency.
         */
        val SAMPLE_DISTANCES =
            listOf(
                100.0,
                200.0,
                500.0,
                1000.0,
                2000.0,
                5000.0,
                10000.0,
                20000.0,
                50000.0,
            )

        /**
         * Atmospheric refraction correction in degrees (Meeus/Bennett formula).
         * Makes the sun appear higher than its geometric position,
         * especially near the horizon (~0.57deg at 0deg elevation).
         *
         * @param geometricElevationDeg geometric sun elevation in degrees
         * @return refraction offset in degrees (always >= 0)
         */
        @Suppress("MagicNumber") // Meeus/Bennett atmospheric refraction formula constants
        fun atmosphericRefraction(geometricElevationDeg: Double): Double {
            // Below -1deg the formula diverges; refraction is irrelevant there
            if (geometricElevationDeg < -1.0) return 0.0
            // Meeus/Bennett formula: R = 1.02 / tan(h + 10.3/(h + 5.11)) / 60
            // where h is elevation in degrees and R is refraction in degrees
            val h = geometricElevationDeg.coerceAtLeast(0.0)
            val tanArg = Math.toRadians(h + 10.3 / (h + 5.11))
            return 1.02 / tan(tanArg) / 60.0
        }
    }
}
