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

/**
 * Use case for calculating sun visibility at a point or grid of points.
 * Combines sun position calculation with terrain elevation data.
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

            // Get observer elevation
            val observerElevation =
                elevationRepository.getElevation(location)
                    .getOrElse { DEFAULT_OBSERVER_ELEVATION }

            // Get terrain profile in sun's direction
            val terrainProfile = getTerrainProfile(location, observerElevation, sunPosition.azimuth)

            // Check if terrain blocks the sun
            val horizonAngle = terrainProfile.calculateHorizonAngle()
            val isSunVisible = sunPosition.elevation > horizonAngle

            if (isSunVisible) {
                VisibilityResult.visible(location, sunPosition, horizonAngle)
            } else {
                val degreesUntilVisible = horizonAngle - sunPosition.elevation
                VisibilityResult.blocked(
                    location = location,
                    sunPosition = sunPosition,
                    horizonAngle = horizonAngle,
                    degreesUntilVisible = degreesUntilVisible,
                )
            }
        }

    /**
     * Calculate visibility grid for rendering as map overlay.
     */
    suspend fun calculateVisibilityGrid(
        bounds: BoundingBox,
        dateTime: LocalDateTime,
        resolution: Double = VisibilityGrid.DEFAULT_RESOLUTION,
    ): Result<VisibilityGrid> =
        runCatching {
            val points = mutableMapOf<GeoPoint, Boolean>()

            // Generate grid points
            var lat = bounds.south
            while (lat <= bounds.north) {
                var lon = bounds.west
                while (lon <= bounds.east) {
                    val point = GeoPoint(lat, lon)

                    // Calculate visibility at this point
                    val visibility =
                        calculateVisibility(point, dateTime)
                            .getOrNull()
                            ?.isSunVisible
                            ?: false

                    points[point] = visibility
                    lon += resolution
                }
                lat += resolution
            }

            VisibilityGrid(
                bounds = bounds,
                resolution = resolution,
                points = points,
            )
        }

    /**
     * Get terrain profile in a specific direction from observer.
     */
    private suspend fun getTerrainProfile(
        observer: GeoPoint,
        observerElevation: Double,
        azimuth: Double,
    ): TerrainProfile {
        val terrainPoints = mutableListOf<TerrainPoint>()

        // Sample terrain at increasing distances
        for (distance in SAMPLE_DISTANCES) {
            val samplePoint = projectPoint(observer, azimuth, distance)

            val elevation =
                elevationRepository.getElevation(samplePoint)
                    .getOrElse { observerElevation }

            terrainPoints.add(
                TerrainPoint(
                    distance = distance,
                    elevation = elevation,
                ),
            )
        }

        return TerrainProfile(
            observer = observer,
            observerElevation = observerElevation,
            azimuth = azimuth,
            points = terrainPoints,
        )
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
    }
}
