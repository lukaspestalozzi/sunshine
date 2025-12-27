package com.sunshine.app.data.repository

import com.sunshine.app.domain.model.BoundingBox
import com.sunshine.app.domain.model.DownloadableRegion
import com.sunshine.app.domain.repository.RegionProvider

/**
 * Default implementation of RegionProvider with predefined European regions.
 * Regions are optimized for hiking/outdoor activities with appropriate zoom levels.
 */
@Suppress("MagicNumber")
class DefaultRegionProvider : RegionProvider {
    private val regions =
        listOf(
            createSwissAlps(),
            createAustrianAlps(),
            createDolomites(),
            createFrenchAlps(),
            createBavarianAlps(),
            createJulianAlps(),
            createPyrenees(),
            createNorwegianFjords(),
            createScottishHighlands(),
            createCarpathians(),
        )

    override fun getAvailableRegions(): List<DownloadableRegion> = regions

    override fun getRegionById(id: String): DownloadableRegion? = regions.find { it.id == id }

    private fun createSwissAlps() =
        DownloadableRegion(
            id = "swiss-alps",
            name = "Swiss Alps",
            description = "Complete Swiss Alpine region including major peaks and valleys",
            bounds = BoundingBox(north = 47.8, south = 45.8, east = 10.5, west = 5.9),
            minZoom = 8,
            maxZoom = 14,
        )

    private fun createAustrianAlps() =
        DownloadableRegion(
            id = "austrian-alps",
            name = "Austrian Alps",
            description = "Austrian Alpine region from Vorarlberg to Vienna Alps",
            bounds = BoundingBox(north = 47.8, south = 46.4, east = 17.2, west = 9.5),
            minZoom = 8,
            maxZoom = 14,
        )

    private fun createDolomites() =
        DownloadableRegion(
            id = "dolomites",
            name = "Dolomites",
            description = "Italian Dolomites UNESCO World Heritage region",
            bounds = BoundingBox(north = 47.1, south = 46.0, east = 12.5, west = 11.0),
            minZoom = 8,
            maxZoom = 14,
        )

    private fun createFrenchAlps() =
        DownloadableRegion(
            id = "french-alps",
            name = "French Alps",
            description = "French Alpine region including Mont Blanc massif",
            bounds = BoundingBox(north = 46.5, south = 44.5, east = 7.5, west = 5.5),
            minZoom = 8,
            maxZoom = 14,
        )

    private fun createBavarianAlps() =
        DownloadableRegion(
            id = "bavarian-alps",
            name = "Bavarian Alps",
            description = "German Alpine region along the Austrian border",
            bounds = BoundingBox(north = 47.8, south = 47.2, east = 13.1, west = 10.1),
            minZoom = 8,
            maxZoom = 14,
        )

    private fun createJulianAlps() =
        DownloadableRegion(
            id = "julian-alps",
            name = "Julian Alps",
            description = "Slovenian Julian Alps including Triglav National Park",
            bounds = BoundingBox(north = 46.6, south = 46.1, east = 14.2, west = 13.3),
            minZoom = 8,
            maxZoom = 14,
        )

    private fun createPyrenees() =
        DownloadableRegion(
            id = "pyrenees",
            name = "Pyrenees",
            description = "Pyrenees mountain range spanning Spain and France",
            bounds = BoundingBox(north = 43.3, south = 42.3, east = 3.2, west = -1.8),
            minZoom = 8,
            maxZoom = 14,
        )

    private fun createNorwegianFjords() =
        DownloadableRegion(
            id = "norwegian-fjords",
            name = "Norwegian Fjords",
            description = "Western Norway fjord region",
            bounds = BoundingBox(north = 62.5, south = 59.5, east = 8.5, west = 4.5),
            minZoom = 8,
            maxZoom = 13,
        )

    private fun createScottishHighlands() =
        DownloadableRegion(
            id = "scottish-highlands",
            name = "Scottish Highlands",
            description = "Scottish Highlands including Ben Nevis",
            bounds = BoundingBox(north = 58.5, south = 56.0, east = -3.0, west = -7.5),
            minZoom = 8,
            maxZoom = 13,
        )

    private fun createCarpathians() =
        DownloadableRegion(
            id = "carpathians",
            name = "Carpathian Mountains",
            description = "Romanian Carpathians including Transylvanian Alps",
            bounds = BoundingBox(north = 47.8, south = 45.0, east = 26.5, west = 22.0),
            minZoom = 8,
            maxZoom = 13,
        )
}
