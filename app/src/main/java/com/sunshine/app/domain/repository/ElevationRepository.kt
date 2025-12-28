package com.sunshine.app.domain.repository

import com.sunshine.app.domain.model.BoundingBox
import com.sunshine.app.domain.model.GeoPoint

/**
 * Repository for elevation data.
 */
interface ElevationRepository {
    /**
     * Get elevation at a specific point.
     *
     * @param point The geographic point
     * @return The elevation in meters, or null if not available
     */
    suspend fun getElevation(point: GeoPoint): Result<Double>

    /**
     * Get elevations for multiple points in a single batch request.
     * More efficient than calling getElevation multiple times.
     *
     * @param points The list of geographic points
     * @return Map of points to elevations (may be partial if some fail)
     */
    suspend fun getElevations(points: List<GeoPoint>): Result<Map<GeoPoint, Double>>

    /**
     * Get elevations for a grid of points within a bounding box.
     *
     * @param bounds The bounding box
     * @param resolution Grid resolution in degrees
     * @return Map of points to elevations
     */
    suspend fun getElevationGrid(
        bounds: BoundingBox,
        resolution: Double,
    ): Result<Map<GeoPoint, Double>>

    /**
     * Check if elevation data is available offline for a region.
     */
    suspend fun isAvailableOffline(bounds: BoundingBox): Boolean
}
