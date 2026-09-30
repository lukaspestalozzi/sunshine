package com.sunshine.app.offline

import kotlin.math.abs

/**
 * MapLibre's ambient cache limit (design D2 of add-offline-regions): MapLibre counts the offline
 * regions against it, so it is the browsed map tiles' 512 MiB plus the regions' map size. [set]
 * applies a limit in bytes.
 */
class AmbientLimit(
    private val set: (Long) -> Unit,
) {
    private var regionBytes: Long? = null

    /**
     * The regions now take [bytes]. During a download the limit follows in steps of 16 MiB; [now]
     * applies it at once, for a region completed or deleted.
     */
    @Synchronized
    fun onRegionBytes(
        bytes: Long,
        now: Boolean = false,
    ) {
        val last = regionBytes
        if (now || last == null || abs(bytes - last) >= STEP_BYTES) {
            regionBytes = bytes
            set(BROWSED_MAP_LIMIT_BYTES + bytes)
        }
    }

    companion object {
        /** Browsed map tiles are kept up to this size (offline-regions spec, "Kept tiles"). */
        const val BROWSED_MAP_LIMIT_BYTES = 512L * 1024 * 1024
        private const val STEP_BYTES = 16L * 1024 * 1024
    }
}
