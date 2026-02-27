package com.sunshine.app.domain.model

/**
 * Pre-computed terrain horizon profile for an observer location.
 * Maps azimuth directions to the maximum terrain elevation angle,
 * enabling fast sun-vs-terrain comparisons without repeated elevation lookups.
 *
 * Used by terrain sunrise/sunset search: compute once, then iterate through
 * time steps comparing sun position against cached horizon angles.
 */
data class HorizonProfile(
    val observer: GeoPoint,
    val observerElevation: Double,
    val entries: List<HorizonEntry>,
    val isElevationDegraded: Boolean = false,
) {
    /** Entries sorted by azimuth for binary search. */
    private val sorted: List<HorizonEntry> by lazy { entries.sortedBy { it.azimuth } }

    /**
     * Get the terrain horizon angle at an arbitrary azimuth by linear interpolation
     * between the two nearest entries. Returns 0.0 if no entries exist.
     */
    fun getHorizonAngleAt(azimuth: Double): Double =
        when {
            sorted.isEmpty() -> 0.0
            sorted.size == 1 -> sorted.first().horizonAngle
            else -> {
                val normalized = normalizeAzimuth(azimuth)
                val idx = sorted.binarySearchInsertionPoint(normalized)
                val lower = if (idx == 0) sorted.last() else sorted[idx - 1]
                val upper = if (idx >= sorted.size) sorted.first() else sorted[idx]
                interpolate(normalized, lower, upper)
            }
        }

    private fun interpolate(
        azimuth: Double,
        lower: HorizonEntry,
        upper: HorizonEntry,
    ): Double {
        val lowerAz = lower.azimuth
        val upperAz = upper.azimuth
        val span = azimuthSpan(lowerAz, upperAz)
        if (span == 0.0) return lower.horizonAngle

        val offset = azimuthSpan(lowerAz, azimuth)
        val fraction = offset / span
        return lower.horizonAngle + fraction * (upper.horizonAngle - lower.horizonAngle)
    }

    companion object {
        private const val FULL_CIRCLE = 360.0

        private fun normalizeAzimuth(azimuth: Double): Double {
            val mod = azimuth % FULL_CIRCLE
            return if (mod < 0) mod + FULL_CIRCLE else mod
        }

        /** Angular distance from [from] to [to] going clockwise. */
        private fun azimuthSpan(
            from: Double,
            to: Double,
        ): Double {
            val diff = to - from
            return if (diff < 0) diff + FULL_CIRCLE else diff
        }

        /** Find insertion point for [target] in the sorted entries list. */
        private fun List<HorizonEntry>.binarySearchInsertionPoint(target: Double): Int {
            var lo = 0
            var hi = size
            while (lo < hi) {
                val mid = (lo + hi) / 2
                if (this[mid].azimuth < target) lo = mid + 1 else hi = mid
            }
            return lo
        }
    }
}

/**
 * A single azimuth→horizon-angle entry in a [HorizonProfile].
 */
data class HorizonEntry(
    /** Compass direction in degrees (0° = North, 90° = East). */
    val azimuth: Double,
    /** Maximum terrain elevation angle above horizontal at this azimuth (degrees). */
    val horizonAngle: Double,
)
