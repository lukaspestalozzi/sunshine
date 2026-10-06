package com.sunshine.app.sunshine

import com.sunshine.app.elevation.TileLoads
import com.sunshine.core.GeoPoint
import com.sunshine.core.HeightTile
import com.sunshine.core.HorizonProfile
import com.sunshine.core.HorizonTracer
import com.sunshine.core.TileKey
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlin.time.measureTimedValue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Horizon profiles of locations (design D8 of add-terrain-horizon). [tile] loads a tile, `null` if
 * it is unavailable. The tracer's work runs on the caller's dispatcher. The last [CACHED_PROFILES]
 * complete profiles are kept; an incomplete one is computed again, as the network may be back.
 */
class SunshineRepository(
    private val tile: suspend (TileKey) -> HeightTile?,
    private val log: (String) -> Unit = {},
    private val loads: () -> TileLoads = { TileLoads(0, 0) },
    private val debug: DebugInfo = DebugInfo(),
    private val memoryTiles: () -> MemoryTiles? = { null },
) {
    private val profiles =
        object : LinkedHashMap<Pair<Long, Long>, HorizonProfile>(CACHED_PROFILES, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Long, Long>, HorizonProfile>) = size > CACHED_PROFILES
        }

    /**
     * The horizon profile at [point], or `null` when its ground height is unknown. Unless [record],
     * e.g. for the agreement check's cells, it is neither shown in the debug box nor cached.
     */
    suspend fun profile(
        point: GeoPoint,
        record: Boolean = true,
    ): HorizonProfile? {
        val key = cacheKey(point)
        synchronized(profiles) { profiles[key] }?.let { return it }
        val start = TimeSource.Monotonic.markNow()
        var loading = Duration.ZERO
        var tiles = 0
        var unavailable = 0
        val before = loads()
        val timedLoad: suspend (Set<TileKey>) -> Map<TileKey, HeightTile?> = { keys ->
            tiles += keys.size
            measureTimedValue { load(keys) }.also { loading += it.duration }.value.also { loaded ->
                unavailable +=
                    loaded.values.count { it == null }
            }
        }
        val tracer = HorizonTracer(point)
        tracer.start(timedLoad(tracer.groundTiles()))
        while (!tracer.isDone) tracer.advance(timedLoad(tracer.nextTiles()))
        val profile = tracer.profile()
        val total = start.elapsedNow()
        if (!record) return profile
        log("Horizon at $point: ${total.inWholeMilliseconds} ms, of which $tiles tiles ${loading.inWholeMilliseconds} ms")
        // Other features loading tiles at the same time can inflate the disk and network counts.
        val disk = loads().store - before.store
        val network = loads().network - before.network
        val sources = TileSources(0, (tiles - unavailable - disk - network).coerceAtLeast(0), disk, network, unavailable)
        val held = memoryTiles()
        debug.update { it.copy(horizon = HorizonTiming(total, loading), horizonTiles = sources, memoryTiles = held ?: it.memoryTiles) }
        if (profile != null && profile.complete.all { it }) synchronized(profiles) { profiles[key] = profile }
        return profile
    }

    /** Loads all [keys] concurrently. */
    private suspend fun load(keys: Set<TileKey>): Map<TileKey, HeightTile?> =
        coroutineScope { keys.map { key -> async { key to tile(key) } }.awaitAll().toMap() }

    // Locations about 1 m apart share a profile.
    private fun cacheKey(point: GeoPoint) = (point.latitude * KEY_SCALE).roundToLong() to (point.longitude * KEY_SCALE).roundToLong()

    private companion object {
        const val CACHED_PROFILES = 4
        const val LOAD_FACTOR = 0.75f
        const val KEY_SCALE = 1e5
    }
}
