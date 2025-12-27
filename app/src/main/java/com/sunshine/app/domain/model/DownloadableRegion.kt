package com.sunshine.app.domain.model

/**
 * Represents a geographic region available for offline download.
 *
 * @property id Unique identifier for the region
 * @property name Human-readable name
 * @property description Brief description of the region
 * @property bounds Geographic bounding box
 * @property minZoom Minimum zoom level to download
 * @property maxZoom Maximum zoom level to download
 */
data class DownloadableRegion(
    val id: String,
    val name: String,
    val description: String,
    val bounds: BoundingBox,
    val minZoom: Int,
    val maxZoom: Int,
) {
    /**
     * Estimates the number of tiles for this region.
     * This is an approximation based on the zoom levels and bounds.
     */
    @Suppress("MagicNumber")
    fun estimateTileCount(): Long {
        var totalTiles = 0L
        for (zoom in minZoom..maxZoom) {
            val tilesX = calculateTileCount(bounds.west, bounds.east, zoom)
            val tilesY = calculateTileCount(bounds.south, bounds.north, zoom, isLatitude = true)
            totalTiles += tilesX * tilesY
        }
        return totalTiles
    }

    /**
     * Estimates download size in bytes (rough estimate: ~15KB per tile average).
     */
    @Suppress("MagicNumber")
    fun estimateSizeBytes(): Long = estimateTileCount() * BYTES_PER_TILE

    /**
     * Formats the estimated size as a human-readable string.
     */
    @Suppress("MagicNumber")
    fun formatEstimatedSize(): String {
        val bytes = estimateSizeBytes()
        return when {
            bytes < KB -> "$bytes B"
            bytes < MB -> "${bytes / KB} KB"
            bytes < GB -> "${bytes / MB} MB"
            else -> "${"%.1f".format(bytes.toDouble() / GB)} GB"
        }
    }

    companion object {
        private const val BYTES_PER_TILE = 15_000L
        private const val KB = 1024L
        private const val MB = KB * 1024
        private const val GB = MB * 1024

        @Suppress("MagicNumber")
        private fun calculateTileCount(
            minCoord: Double,
            maxCoord: Double,
            zoom: Int,
            isLatitude: Boolean = false,
        ): Long =
            if (isLatitude) {
                val minTile = latToTileY(maxCoord, zoom)
                val maxTile = latToTileY(minCoord, zoom)
                (maxTile - minTile + 1).coerceAtLeast(1)
            } else {
                val minTile = lonToTileX(minCoord, zoom)
                val maxTile = lonToTileX(maxCoord, zoom)
                (maxTile - minTile + 1).coerceAtLeast(1)
            }

        @Suppress("MagicNumber")
        private fun lonToTileX(
            lon: Double,
            zoom: Int,
        ): Long {
            val n = 1L shl zoom
            return ((lon + 180.0) / 360.0 * n).toLong().coerceIn(0, n - 1)
        }

        @Suppress("MagicNumber")
        private fun latToTileY(
            lat: Double,
            zoom: Int,
        ): Long {
            val n = 1L shl zoom
            val latRad = Math.toRadians(lat)
            val y = (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n
            return y.toLong().coerceIn(0, n - 1)
        }
    }
}
