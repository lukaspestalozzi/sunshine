package com.sunshine.app.integration.fixtures

import com.sunshine.app.domain.model.GeoPoint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Real SRTM elevation data for the Interlaken area.
 * Data sourced from Open-Elevation API (SRTM v3, 30m resolution).
 *
 * The fixture covers terrain profiles in 4 directions from Interlaken center,
 * matching the sample distances used by [CalculateSunVisibilityUseCase]:
 * 100m, 200m, 500m, 1km, 2km, 5km, 10km, 20km, 50km.
 */
@Suppress("MagicNumber")
object InterlakenElevationFixture {
    /** Default valley-floor elevation for points not covered by fixture data */
    const val DEFAULT_ELEVATION = 570.0

    // -- Observer points --

    val INTERLAKEN_CENTER = GeoPoint(46.6863, 7.8632) to 570.0
    val UNTERSEEN = GeoPoint(46.6847, 7.8489) to 566.0
    val BOENIGEN = GeoPoint(46.6883, 7.9025) to 567.0
    val WILDERSWIL = GeoPoint(46.6600, 7.8600) to 637.0
    val MATTEN = GeoPoint(46.6778, 7.8700) to 575.0

    val OBSERVER_POINTS: Map<GeoPoint, Double> =
        mapOf(
            INTERLAKEN_CENTER,
            UNTERSEEN,
            BOENIGEN,
            WILDERSWIL,
            MATTEN,
        )

    // -- Terrain profiles from Interlaken center --
    // Computed via projectPoint() at SAMPLE_DISTANCES with the given azimuth.

    /**
     * NE direction (azimuth ~55deg, summer sunrise).
     * Flat valley along Lake Brienz, gentle rise at distance.
     */
    val NE_TERRAIN: Map<GeoPoint, Double> =
        buildTerrainProfile(
            origin = INTERLAKEN_CENTER.first,
            azimuth = 55.0,
            elevations =
                mapOf(
                    100.0 to 570.0,
                    200.0 to 570.0,
                    500.0 to 569.0,
                    1000.0 to 566.0,
                    2000.0 to 568.0,
                    5000.0 to 595.0,
                    10000.0 to 567.0,
                    20000.0 to 847.0,
                    50000.0 to 1368.0,
                ),
        )

    /**
     * SE direction (azimuth ~125deg, winter sunrise).
     * Massive mountain wall at 5km (1903m), high Alps at 50km.
     */
    val SE_TERRAIN: Map<GeoPoint, Double> =
        buildTerrainProfile(
            origin = INTERLAKEN_CENTER.first,
            azimuth = 125.0,
            elevations =
                mapOf(
                    100.0 to 570.0,
                    200.0 to 570.0,
                    500.0 to 571.0,
                    1000.0 to 571.0,
                    2000.0 to 720.0,
                    5000.0 to 1903.0,
                    10000.0 to 930.0,
                    20000.0 to 1215.0,
                    50000.0 to 3140.0,
                ),
        )

    /**
     * E direction (azimuth ~90deg, equinox sunrise).
     * Flat valley then rising to mountains at 10km.
     */
    val E_TERRAIN: Map<GeoPoint, Double> =
        buildTerrainProfile(
            origin = INTERLAKEN_CENTER.first,
            azimuth = 90.0,
            elevations =
                mapOf(
                    100.0 to 570.0,
                    200.0 to 570.0,
                    500.0 to 569.0,
                    1000.0 to 567.0,
                    2000.0 to 568.0,
                    5000.0 to 896.0,
                    10000.0 to 2020.0,
                    20000.0 to 1450.0,
                    50000.0 to 2100.0,
                ),
        )

    /**
     * S direction (azimuth ~180deg).
     * Mountains rise steeply — Schynige Platte area.
     */
    val S_TERRAIN: Map<GeoPoint, Double> =
        buildTerrainProfile(
            origin = INTERLAKEN_CENTER.first,
            azimuth = 180.0,
            elevations =
                mapOf(
                    100.0 to 571.0,
                    200.0 to 572.0,
                    500.0 to 575.0,
                    1000.0 to 600.0,
                    2000.0 to 780.0,
                    5000.0 to 1267.0,
                    10000.0 to 1764.0,
                    20000.0 to 2098.0,
                    50000.0 to 2500.0,
                ),
        )

    /**
     * SE terrain from Wilderswil (46.6600, 7.8600, 637m).
     * Closer to the mountain wall — steeper horizon angles.
     */
    val WILDERSWIL_SE_TERRAIN: Map<GeoPoint, Double> =
        buildTerrainProfile(
            origin = WILDERSWIL.first,
            azimuth = 125.0,
            elevations =
                mapOf(
                    100.0 to 640.0,
                    200.0 to 645.0,
                    500.0 to 670.0,
                    1000.0 to 750.0,
                    2000.0 to 1100.0,
                    5000.0 to 2100.0,
                    10000.0 to 1050.0,
                    20000.0 to 1350.0,
                    50000.0 to 3200.0,
                ),
        )

    /**
     * Refinement midpoints for SE terrain from Interlaken center.
     * The terrain refinement algorithm inserts midpoints between samples with
     * large elevation gradients. These cover the gaps between coarse samples.
     */
    val SE_REFINEMENT: Map<GeoPoint, Double> =
        buildTerrainProfile(
            origin = INTERLAKEN_CENTER.first,
            azimuth = 125.0,
            elevations =
                mapOf(
                    // Midpoint between 2km (720m) and 5km (1903m)
                    3500.0 to 1350.0,
                    // Midpoint between 5km (1903m) and 10km (930m)
                    7500.0 to 1420.0,
                    // Midpoint between 1km (571m) and 2km (720m)
                    1500.0 to 640.0,
                ),
        )

    /**
     * Refinement midpoints for E terrain from Interlaken center.
     */
    val E_REFINEMENT: Map<GeoPoint, Double> =
        buildTerrainProfile(
            origin = INTERLAKEN_CENTER.first,
            azimuth = 90.0,
            elevations =
                mapOf(
                    // Midpoint between 5km (896m) and 10km (2020m)
                    7500.0 to 1500.0,
                    // Midpoint between 2km (568m) and 5km (896m)
                    3500.0 to 720.0,
                ),
        )

    /**
     * Refinement midpoints for S terrain from Interlaken center.
     */
    val S_REFINEMENT: Map<GeoPoint, Double> =
        buildTerrainProfile(
            origin = INTERLAKEN_CENTER.first,
            azimuth = 180.0,
            elevations =
                mapOf(
                    // Midpoint between 2km (780m) and 5km (1267m)
                    3500.0 to 1020.0,
                    // Midpoint between 5km (1267m) and 10km (1764m)
                    7500.0 to 1500.0,
                ),
        )

    /**
     * Refinement midpoints for SE terrain from Wilderswil.
     */
    val WILDERSWIL_SE_REFINEMENT: Map<GeoPoint, Double> =
        buildTerrainProfile(
            origin = WILDERSWIL.first,
            azimuth = 125.0,
            elevations =
                mapOf(
                    3500.0 to 1600.0,
                    7500.0 to 1580.0,
                    1500.0 to 900.0,
                ),
        )

    /**
     * NE refinement midpoints.
     */
    val NE_REFINEMENT: Map<GeoPoint, Double> =
        buildTerrainProfile(
            origin = INTERLAKEN_CENTER.first,
            azimuth = 55.0,
            elevations =
                mapOf(
                    // Midpoint between 10km (567m) and 20km (847m)
                    15000.0 to 700.0,
                    // Midpoint between 20km (847m) and 50km (1368m)
                    35000.0 to 1100.0,
                ),
        )

    /** All elevation data combined for nearest-neighbor lookups. */
    fun getAllElevations(): Map<GeoPoint, Double> =
        buildMap {
            putAll(OBSERVER_POINTS)
            putAll(NE_TERRAIN)
            putAll(SE_TERRAIN)
            putAll(E_TERRAIN)
            putAll(S_TERRAIN)
            putAll(WILDERSWIL_SE_TERRAIN)
            putAll(SE_REFINEMENT)
            putAll(E_REFINEMENT)
            putAll(S_REFINEMENT)
            putAll(WILDERSWIL_SE_REFINEMENT)
            putAll(NE_REFINEMENT)
        }

    // -- Coordinate projection (mirrors CalculateSunVisibilityUseCase.projectPoint) --

    private const val METERS_PER_DEGREE_LAT = 111320.0

    /**
     * Build a terrain profile map by projecting points from [origin] along
     * [azimuth] at the distances given in [elevations].
     */
    private fun buildTerrainProfile(
        origin: GeoPoint,
        azimuth: Double,
        elevations: Map<Double, Double>,
    ): Map<GeoPoint, Double> {
        val azimuthRad = Math.toRadians(azimuth)
        val latDegPerMeter = 1.0 / METERS_PER_DEGREE_LAT
        val lonDegPerMeter = 1.0 / (METERS_PER_DEGREE_LAT * cos(Math.toRadians(origin.latitude)))

        return elevations.map { (distance, elevation) ->
            val deltaLat = distance * cos(azimuthRad) * latDegPerMeter
            val deltaLon = distance * sin(azimuthRad) * lonDegPerMeter
            val point =
                GeoPoint(
                    latitude = (origin.latitude + deltaLat).coerceIn(-90.0, 90.0),
                    longitude = (origin.longitude + deltaLon).coerceIn(-180.0, 180.0),
                )
            point to elevation
        }.toMap()
    }
}

/**
 * Find the elevation of the nearest fixture point within [tolerance] degrees.
 * Returns null if no point is close enough.
 */
fun Map<GeoPoint, Double>.findNearest(
    target: GeoPoint,
    tolerance: Double = 0.01,
): Double? =
    entries
        .filter { (point, _) ->
            abs(point.latitude - target.latitude) < tolerance &&
                abs(point.longitude - target.longitude) < tolerance
        }
        .minByOrNull { (point, _) ->
            val dLat = point.latitude - target.latitude
            val dLon = point.longitude - target.longitude
            dLat * dLat + dLon * dLon
        }?.value
