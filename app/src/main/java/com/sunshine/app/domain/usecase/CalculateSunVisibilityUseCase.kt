package com.sunshine.app.domain.usecase

import com.sunshine.app.domain.model.BoundingBox
import com.sunshine.app.domain.model.GeoPoint
import com.sunshine.app.domain.model.SunExposureGrid
import com.sunshine.app.domain.model.SunPosition
import com.sunshine.app.domain.model.TerrainPoint
import com.sunshine.app.domain.model.TerrainProfile
import com.sunshine.app.domain.model.VisibilityGrid
import com.sunshine.app.domain.model.VisibilityResult
import com.sunshine.app.domain.repository.ElevationRepository
import com.sunshine.app.domain.service.SunCalculator
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Use case for calculating sun visibility at a point or grid of points.
 * Combines sun position calculation with terrain elevation data.
 *
 * Performance optimizations:
 * - Batch elevation fetching for terrain profiles
 * - Parallel grid calculations using coroutines
 */
@Suppress("TooManyFunctions") // 15 functions: visibility, grid, exposure, terrain, and projection logic
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

        return buildVisibilityResult(
            location = location,
            sunPosition = sunPosition,
            horizonAngle = horizonAngle,
            apparentElevation = apparentElevation,
            isElevationDegraded = isElevationDegraded,
            observerElevation = observerElevation,
        )
    }

    private fun buildVisibilityResult(
        location: GeoPoint,
        sunPosition: SunPosition,
        horizonAngle: Double,
        apparentElevation: Double,
        isElevationDegraded: Boolean,
        observerElevation: Double,
    ): VisibilityResult =
        if (apparentElevation > horizonAngle) {
            VisibilityResult.visible(
                location = location,
                sunPosition = sunPosition,
                horizonAngle = horizonAngle,
                isElevationDegraded = isElevationDegraded,
                observerElevation = observerElevation,
            )
        } else {
            VisibilityResult.blocked(
                location = location,
                sunPosition = sunPosition,
                horizonAngle = horizonAngle,
                degreesUntilVisible = horizonAngle - apparentElevation,
                isElevationDegraded = isElevationDegraded,
                observerElevation = observerElevation,
            )
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

            // Calculate visibility in parallel with bounded concurrency
            val semaphore = Semaphore(MAX_CONCURRENT_CALCULATIONS)
            val results =
                coroutineScope {
                    gridPoints.map { point ->
                        async {
                            semaphore.withPermit {
                                val visibility =
                                    calculateVisibility(point, dateTime)
                                        .getOrNull()
                                        ?.isSunVisible
                                        ?: false
                                point to visibility
                            }
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
     * Calculate sun exposure hours for each point in a grid over an entire day.
     * Scans from astronomical sunrise to sunset in fixed time steps, accumulating
     * the number of minutes the sun is terrain-visible at each grid point.
     *
     * @param bounds Area to compute
     * @param date The day to evaluate (UTC)
     * @param resolution Grid spacing in degrees
     * @param timeStepMinutes Time granularity for scanning
     * @return Grid mapping each point to total hours of sun exposure
     */
    @Suppress("LongMethod")
    suspend fun calculateSunExposureGrid(
        bounds: BoundingBox,
        date: LocalDate,
        resolution: Double = VisibilityGrid.DEFAULT_RESOLUTION,
        timeStepMinutes: Int = SunExposureGrid.DEFAULT_TIME_STEP_MINUTES,
    ): Result<SunExposureGrid> =
        runCatching {
            val center = bounds.center
            val sunriseUtc = sunCalculator.calculateSunrise(center, date)
            val sunsetUtc = sunCalculator.calculateSunset(center, date)

            val gridPoints = generateGridPoints(bounds, resolution, MAX_HEATMAP_GRID_POINTS)

            if (sunriseUtc == null || sunsetUtc == null || gridPoints.isEmpty()) {
                return@runCatching SunExposureGrid(
                    bounds = bounds,
                    resolution = resolution,
                    date = date,
                    points = gridPoints.associateWith { 0.0 },
                )
            }

            val start = LocalDateTime.of(date, sunriseUtc)
            // If sunset is before sunrise in UTC (e.g. western time zones crossing midnight),
            // the sunset falls on the next calendar day.
            val end =
                if (sunsetUtc.isBefore(sunriseUtc)) {
                    LocalDateTime.of(date.plusDays(1), sunsetUtc)
                } else {
                    LocalDateTime.of(date, sunsetUtc)
                }

            // Build list of time steps to scan
            val timeSteps = mutableListOf<LocalDateTime>()
            var current = start
            while (!current.isAfter(end)) {
                timeSteps.add(current)
                current = current.plusMinutes(timeStepMinutes.toLong())
            }

            // For each grid point, count visible time steps in parallel
            val semaphore = Semaphore(MAX_CONCURRENT_CALCULATIONS)
            val results =
                coroutineScope {
                    gridPoints.map { point ->
                        async {
                            semaphore.withPermit {
                                val visibleSteps =
                                    timeSteps.count { time ->
                                        isTerrainVisible(point, time)
                                    }
                                val hours = visibleSteps * timeStepMinutes / MINUTES_PER_HOUR
                                point to hours
                            }
                        }
                    }.awaitAll()
                }

            SunExposureGrid(
                bounds = bounds,
                resolution = resolution,
                date = date,
                points = results.toMap(),
            )
        }

    /**
     * Generate grid points for a bounding box, capped at [maxPoints].
     */
    private fun generateGridPoints(
        bounds: BoundingBox,
        resolution: Double,
        maxPoints: Int = MAX_GRID_POINTS,
    ): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        var lat = bounds.south
        while (lat <= bounds.north && points.size < maxPoints) {
            var lon = bounds.west
            while (lon <= bounds.east && points.size < maxPoints) {
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

    /**
     * Calculate terrain-aware first and last sunshine times for a location and date.
     * Scans between astronomical sunrise/sunset to find when the sun first clears
     * and last disappears behind surrounding terrain.
     *
     * @param location The observer's location
     * @param date The date (used to query astronomical sunrise/sunset in UTC)
     * @return Pair of (firstSunshine, lastSunshine) as UTC [LocalTime], or null if not applicable
     */
    suspend fun calculateTerrainSunriseSunset(
        location: GeoPoint,
        date: LocalDate,
    ): Result<Pair<LocalTime?, LocalTime?>> =
        runCatching {
            val sunriseUtc =
                sunCalculator.calculateSunrise(location, date)
                    ?: return@runCatching Pair(null, null)
            val sunsetUtc =
                sunCalculator.calculateSunset(location, date)
                    ?: return@runCatching Pair(null, null)

            val sunriseDateTime = LocalDateTime.of(date, sunriseUtc)
            val sunsetDateTime = LocalDateTime.of(date, sunsetUtc)

            val firstSunshine = findFirstVisible(location, sunriseDateTime, sunsetDateTime)
            val lastSunshine = findLastVisible(location, sunriseDateTime, sunsetDateTime)

            Pair(firstSunshine?.toLocalTime(), lastSunshine?.toLocalTime())
        }

    /**
     * Scan forward from [start] to [end] to find the first time the sun is terrain-visible.
     * Refines with binary search once a transition is found.
     */
    private suspend fun findFirstVisible(
        location: GeoPoint,
        start: LocalDateTime,
        end: LocalDateTime,
    ): LocalDateTime? {
        var current = start
        while (!current.isAfter(end)) {
            val visible = isTerrainVisible(location, current)
            if (visible) {
                // Binary search between (current - step) and current
                val searchStart = if (current == start) start else current.minusMinutes(SCAN_STEP_MINUTES)
                return binarySearchTransition(location, searchStart, current, searchForFirst = true)
            }
            current = current.plusMinutes(SCAN_STEP_MINUTES)
        }
        return null
    }

    /**
     * Scan backward from [end] to [start] to find the last time the sun is terrain-visible.
     * Refines with binary search once a transition is found.
     */
    private suspend fun findLastVisible(
        location: GeoPoint,
        start: LocalDateTime,
        end: LocalDateTime,
    ): LocalDateTime? {
        var current = end
        while (!current.isBefore(start)) {
            val visible = isTerrainVisible(location, current)
            if (visible) {
                // Binary search between current and (current + step)
                val searchEnd = if (current == end) end else current.plusMinutes(SCAN_STEP_MINUTES)
                return binarySearchTransition(location, current, searchEnd, searchForFirst = false)
            }
            current = current.minusMinutes(SCAN_STEP_MINUTES)
        }
        return null
    }

    /**
     * Binary search to find the transition point to ~1 minute accuracy.
     *
     * @param searchForFirst If true, finds the earliest visible time (low=blocked, high=visible).
     *                       If false, finds the latest visible time (low=visible, high=blocked).
     */
    private suspend fun binarySearchTransition(
        location: GeoPoint,
        low: LocalDateTime,
        high: LocalDateTime,
        searchForFirst: Boolean,
    ): LocalDateTime {
        var lo = low
        var hi = high
        while (ChronoUnit.MINUTES.between(lo, hi) > 1) {
            val midSeconds = ChronoUnit.SECONDS.between(lo, hi) / 2
            val mid = lo.plusSeconds(midSeconds)
            val visible = isTerrainVisible(location, mid)
            if (searchForFirst) {
                if (visible) hi = mid else lo = mid
            } else {
                if (visible) lo = mid else hi = mid
            }
        }
        return if (searchForFirst) hi else lo
    }

    /**
     * Quick check whether the sun is terrain-visible at a given time.
     * Returns false if the sun is below the horizon or blocked by terrain.
     */
    private suspend fun isTerrainVisible(
        location: GeoPoint,
        dateTime: LocalDateTime,
    ): Boolean =
        calculateVisibility(location, dateTime)
            .getOrNull()
            ?.isSunVisible
            ?: false

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

        /** Max concurrent visibility calculations to prevent resource exhaustion */
        private const val MAX_CONCURRENT_CALCULATIONS = 8

        /** Maximum grid points to prevent runaway calculations */
        private const val MAX_GRID_POINTS = 500

        /** Maximum grid points for heatmap (fewer because each point scans the whole day) */
        private const val MAX_HEATMAP_GRID_POINTS = 200

        /** Minutes per hour for converting step count to hours */
        private const val MINUTES_PER_HOUR = 60.0

        /** Scan step in minutes for terrain sunrise/sunset search */
        const val SCAN_STEP_MINUTES = 15L

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
