package com.sunshine.app.domain.repository

import com.sunshine.app.domain.model.DownloadableRegion

/**
 * Provides available regions for offline download.
 * This interface allows for future extensibility (e.g., fetching regions from a server).
 */
interface RegionProvider {
    /**
     * Returns all available regions for download.
     */
    fun getAvailableRegions(): List<DownloadableRegion>

    /**
     * Returns a region by its ID, or null if not found.
     */
    fun getRegionById(id: String): DownloadableRegion?
}
