package com.sunshine.app.domain.usecase

import com.sunshine.app.domain.model.BoundingBox
import com.sunshine.app.domain.model.GeoPoint
import com.sunshine.app.domain.model.SunPosition
import com.sunshine.app.domain.model.TerrainPoint
import com.sunshine.app.domain.model.TerrainProfile
import com.sunshine.app.domain.model.VisibilityGrid
import com.sunshine.app.domain.model.VisibilityResult
import com.sunshine.app.domain.repository.ElevationRepository
import com.sunshine.app.suncalc.SunCalculator
import java.time.LocalDateTime
import kotlin.math.abs
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
            val sunPosition = sunCalculator.calculateSunPosition(location, dateTime)
            if (!sunPosition.isAboveHorizon) {
                return@runCatching VisibilityResult.belowHorizon(location, sunPosition)
            }
            checkTerrainVisibility(location, sunPosition)
        }

    /**
     * Check whether terrain blocks the sun for an above-horizon sun position.
     */
    private suspend fun checkTerrainVisibility(
        location: GeoPoint,
        sunPosition: SunPosition,
    ): VisibilityResult {
        val observerElevationResult = elevationRepository.getElevation(location)
        val observerElevation =
            observerElevationResult.getOrElse { DEFAULT_OBSERVER_ELEVATION }

        val profileResult =
            getTerrainProfileBatch(location, observerElevation, sunPosition.azimuth)
        val terrainProfile =
            refineTerrainProfile(
                observer = location,
                azimuth = sunPosition.azimuth,
                initialProfile = profileResult.first,
            )
        val isElevationDegraded =
            observerElevationResult.isFailure || profileResult.second

        val horizonAngle = terrainProfile.calculateHorizonAngle()
        val apparentElevation =
            sunPosition.elevation + atmosphericRefraction(sunPosition.elevation)

        return if (apparentElevation > horizonAngle) {
            VisibilityResult.visible(location, sunPosition, horizonAngle, isElevationDegraded)
        } else {
            VisibilityResult.blocked(
                location = location,
                sunPosition = sunPosition,
                horizonAngle = horizonAngle,
                degreesUntilVisible = horizonAngle - apparentElevation,
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

        val profile =
            TerrainProfile(
                observer = observer,
                observerElevation = observerElevation,
                azimuth = azimuth,
                points = terrainPoints,
            )
        return profile to isDegraded
    }

    /**
     * Refine a terrain profile by inserting midpoints between consecutive sample
     * points that have a large elevation gradient or a large distance gap.
     * This catches narrow ridges that fall between the coarse logarithmic samples.
     */
    private suspend fun refineTerrainProfile(
        observer: GeoPoint,
        azimuth: Double,
        initialProfile: TerrainProfile,
    ): TerrainProfile {
        val midDistances = findRefinementGaps(initialProfile.points)
        if (midDistances.isEmpty()) return initialProfile

        val midGeoPoints = midDistances.map { it to projectPoint(observer, azimuth, it) }
        val midElevations =
            elevationRepository.getElevations(midGeoPoints.map { it.second })
                .getOrElse { emptyMap() }

        val newPoints =
            midGeoPoints.map { (distance, geoPoint) ->
                TerrainPoint(distance, midElevations[geoPoint] ?: initialProfile.observerElevation)
            }
        val mergedPoints = (initialProfile.points + newPoints).sortedBy { it.distance }
        return initialProfile.copy(points = mergedPoints)
    }

    /**
     * Find distances where midpoints should be inserted for terrain refinement.
     */
    private fun findRefinementGaps(points: List<TerrainPoint>): List<Double> {
        if (points.size < 2) return emptyList()
        return (0 until points.size - 1).mapNotNull { i ->
            val elevDiff = abs(points[i].elevation - points[i + 1].elevation)
            val distGap = points[i + 1].distance - points[i].distance
            val needsRefinement =
                elevDiff > REFINEMENT_ELEVATION_THRESHOLD ||
                    (distGap > REFINEMENT_DISTANCE_THRESHOLD && elevDiff > REFINEMENT_MIN_ELEVATION_DIFF)
            if (needsRefinement) (points[i].distance + points[i + 1].distance) / 2.0 else null
        }
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

        /** Refine if elevation difference between consecutive points exceeds this (meters) */
        const val REFINEMENT_ELEVATION_THRESHOLD = 200.0

        /** Refine if distance gap exceeds this AND elevation diff > MIN (meters) */
        const val REFINEMENT_DISTANCE_THRESHOLD = 2000.0

        /** Minimum elevation diff to trigger refinement in large gaps (meters) */
        const val REFINEMENT_MIN_ELEVATION_DIFF = 50.0

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
