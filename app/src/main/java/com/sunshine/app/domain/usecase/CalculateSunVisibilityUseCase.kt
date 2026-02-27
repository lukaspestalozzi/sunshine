package com.sunshine.app.domain.usecase

import com.sunshine.app.domain.model.BoundingBox
import com.sunshine.app.domain.model.GeoPoint
import com.sunshine.app.domain.model.HorizonEntry
import com.sunshine.app.domain.model.HorizonProfile
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
@Suppress("TooManyFunctions") // 16 functions: visibility, grid, exposure, terrain, projection, and horizon profile logic
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
            sunPosition.elevation + atmosphericRefraction(sunPosition.elevation) +
                SUN_ANGULAR_SEMI_DIAMETER

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
     * Pre-computes a horizon profile for the observer, then iterates through time
     * comparing the sun's position against cached terrain horizon angles.
     *
     * @param location The observer's location
     * @param date The date (used to query astronomical sunrise/sunset in UTC)
     * @return Pair of (firstSunshine, lastSunshine) as UTC [LocalTime], or null if not applicable
     */
    @Suppress("LongMethod") // 22 lines: cohesive sunrise/sunset pipeline
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

            // Determine azimuth range from sun path at sunrise/sunset
            val sunAtRise = sunCalculator.calculateSunPosition(location, sunriseDateTime)
            val sunAtSet = sunCalculator.calculateSunPosition(location, sunsetDateTime)

            // Pre-compute horizon profile once — all elevation data fetched upfront
            val profile = computeHorizonProfile(location, sunAtRise.azimuth, sunAtSet.azimuth)

            val first = findTransitionTime(profile, location, sunriseDateTime, sunsetDateTime, true)
            val last = findTransitionTime(profile, location, sunriseDateTime, sunsetDateTime, false)

            Pair(first?.toLocalTime(), last?.toLocalTime())
        }

    /**
     * Pre-compute a terrain horizon profile for an observer covering the given azimuth range.
     * Fetches elevation data for all azimuths in one batch, computes horizon angle per azimuth.
     */
    private suspend fun computeHorizonProfile(
        location: GeoPoint,
        sunriseAzimuth: Double,
        sunsetAzimuth: Double,
    ): HorizonProfile {
        val observerElevation =
            elevationRepository.getElevation(location).getOrElse { DEFAULT_OBSERVER_ELEVATION }

        // Generate azimuth sample points covering sunrise and sunset ranges with margin
        val azimuths = generateProfileAzimuths(sunriseAzimuth, sunsetAzimuth)

        // Collect all (azimuth, distance, geoPoint) triples for batch fetching
        val allPoints =
            azimuths.flatMap { az ->
                SAMPLE_DISTANCES.map { dist -> Triple(az, dist, projectPoint(location, az, dist)) }
            }

        val geoPoints = allPoints.map { it.third }
        val elevResult = elevationRepository.getElevations(geoPoints)
        val elevations = elevResult.getOrElse { emptyMap() }

        // Build horizon entries per azimuth
        val entries =
            azimuths.map { az ->
                val terrainPoints =
                    SAMPLE_DISTANCES.map { dist ->
                        val gp = projectPoint(location, az, dist)
                        TerrainPoint(dist, elevations[gp] ?: observerElevation)
                    }
                val profile = TerrainProfile(location, observerElevation, az, terrainPoints)
                HorizonEntry(az, profile.calculateHorizonAngle())
            }

        return HorizonProfile(
            observer = location,
            observerElevation = observerElevation,
            entries = entries,
            isElevationDegraded = elevResult.isFailure,
        )
    }

    /**
     * Scan forward (or backward) through time to find the first (or last) moment
     * the sun's upper limb clears the pre-computed terrain horizon.
     * Uses 5-minute coarse scan then binary search to ~15-second precision.
     * No elevation API calls — only sun position lookups against cached profile.
     */
    @Suppress("LongMethod") // 24 lines: coarse scan + binary search is one cohesive algorithm
    private suspend fun findTransitionTime(
        profile: HorizonProfile,
        location: GeoPoint,
        start: LocalDateTime,
        end: LocalDateTime,
        searchForFirst: Boolean,
    ): LocalDateTime? {
        val step = PROFILE_SCAN_STEP_MINUTES
        // Coarse scan to find a transition interval
        val scanStart = if (searchForFirst) start else end
        val scanEnd = if (searchForFirst) end else start
        val delta = if (searchForFirst) step else -step

        var current = scanStart
        while (if (searchForFirst) !current.isAfter(scanEnd) else !current.isBefore(scanEnd)) {
            if (isSunAboveProfile(profile, location, current)) {
                val lo = if (searchForFirst) maxOf(current.minusMinutes(step), start) else current
                val hi = if (searchForFirst) current else minOf(current.plusMinutes(step), end)
                return binarySearchProfile(profile, location, lo, hi, searchForFirst)
            }
            current = current.plusMinutes(delta)
        }
        return null
    }

    /** Binary search against pre-computed horizon profile to ~15 second precision. */
    private suspend fun binarySearchProfile(
        profile: HorizonProfile,
        location: GeoPoint,
        low: LocalDateTime,
        high: LocalDateTime,
        searchForFirst: Boolean,
    ): LocalDateTime {
        var lo = low
        var hi = high
        while (ChronoUnit.SECONDS.between(lo, hi) > BINARY_SEARCH_PRECISION_SECONDS) {
            val mid = lo.plusSeconds(ChronoUnit.SECONDS.between(lo, hi) / 2)
            val visible = isSunAboveProfile(profile, location, mid)
            if (searchForFirst) {
                if (visible) hi = mid else lo = mid
            } else {
                if (visible) lo = mid else hi = mid
            }
        }
        return if (searchForFirst) hi else lo
    }

    /** Check if the sun's upper limb is above the pre-computed terrain horizon. */
    private suspend fun isSunAboveProfile(
        profile: HorizonProfile,
        location: GeoPoint,
        dateTime: LocalDateTime,
    ): Boolean {
        val sun = sunCalculator.calculateSunPosition(location, dateTime)
        if (sun.elevation < SUN_BELOW_HORIZON_THRESHOLD) return false
        val apparent = sun.elevation + atmosphericRefraction(sun.elevation) + SUN_ANGULAR_SEMI_DIAMETER
        return apparent > profile.getHorizonAngleAt(sun.azimuth)
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

        /** Sun's angular semi-diameter in degrees (~0.267°). Sunrise = upper limb clears terrain. */
        const val SUN_ANGULAR_SEMI_DIAMETER = 0.267

        /** Skip sun position checks when geometric elevation is deeply below horizon. */
        private const val SUN_BELOW_HORIZON_THRESHOLD = -2.0

        /** Full circle in degrees. */
        private const val FULL_CIRCLE = 360.0

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

        /** Coarse scan step for profile-based terrain sunrise/sunset search (minutes). */
        const val PROFILE_SCAN_STEP_MINUTES = 5L

        /** Binary search precision for terrain sunrise/sunset (seconds). */
        const val BINARY_SEARCH_PRECISION_SECONDS = 15L

        /** Azimuth margin around sunrise/sunset direction (degrees each side). */
        private const val AZIMUTH_MARGIN = 15.0

        /** Azimuth sampling step for horizon profile (degrees). */
        private const val AZIMUTH_STEP = 1.0

        /** Refine if elevation difference between consecutive points exceeds this (meters) */
        const val REFINEMENT_ELEVATION_THRESHOLD = 200.0

        /** Refine if distance gap exceeds this AND elevation diff > MIN (meters) */
        const val REFINEMENT_DISTANCE_THRESHOLD = 2000.0

        /** Minimum elevation diff to trigger refinement in large gaps (meters) */
        const val REFINEMENT_MIN_ELEVATION_DIFF = 50.0

        /** Generate azimuth sample values covering sunrise and sunset ranges with margin. */
        fun generateProfileAzimuths(
            sunriseAzimuth: Double,
            sunsetAzimuth: Double,
        ): List<Double> =
            buildList {
                for (center in listOf(sunriseAzimuth, sunsetAzimuth)) {
                    var az = center - AZIMUTH_MARGIN
                    while (az <= center + AZIMUTH_MARGIN) {
                        add(((az % FULL_CIRCLE) + FULL_CIRCLE) % FULL_CIRCLE)
                        az += AZIMUTH_STEP
                    }
                }
            }.distinct().sorted()

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
