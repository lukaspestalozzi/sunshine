package com.sunshine.app.sunshine

import com.sunshine.app.elevation.TileLoads
import com.sunshine.core.HeightTile
import com.sunshine.core.MapArea
import com.sunshine.core.ShadeGrid
import com.sunshine.core.SunPosition
import com.sunshine.core.SunShadeSweep
import com.sunshine.core.TileKey
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.time.TimeSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive

/**
 * Sun-shade grids of the visible area (design D8 of add-sun-shade-overlay). [tile] loads a tile,
 * `null` if it is unavailable. The lines are computed in [chunks] coroutines on the caller's
 * dispatcher. The tiles of the last grid are kept for the next one while the area moves by less than
 * half a screen, so a new time at the same place loads only the tiles its new upwind lines need.
 */
class OverlayRepository(
    private val tile: suspend (TileKey) -> HeightTile?,
    private val chunks: Int = Runtime.getRuntime().availableProcessors(),
    private val loads: () -> TileLoads = { TileLoads(0, 0) },
    private val log: (String) -> Unit = {},
) {
    private var kept: Kept? = null

    private class Kept(
        val area: MapArea,
        val tiles: Map<TileKey, HeightTile>,
    )

    /** The grid of [area] with the sun at [sun]. */
    suspend fun grid(
        area: MapArea,
        sun: SunPosition,
    ): ShadeGrid =
        coroutineScope {
            val start = TimeSource.Monotonic.markNow()
            val sweep = SunShadeSweep(area, sun)
            val reusable = kept?.takeIf { it.area.isNear(area) }?.tiles.orEmpty()
            val before = loads()
            val ground = load(sweep.groundTiles(), reusable)
            val upwind = sweep.tiles(ground) - ground.keys
            val tiles = ground + load(upwind, reusable)
            val loaded = start.elapsedNow()
            val parts =
                sweep.chunks(chunks).map { lines ->
                    async {
                        ensureActive()
                        sweep.compute(tiles, lines)
                    }
                }
            val grid = sweep.assemble(parts.awaitAll())
            val reused = tiles.keys.count { it in reusable }
            // Other features loading tiles at the same time can inflate the disk and network counts.
            val disk = loads().disk - before.disk
            val network = loads().network - before.network
            log(
                "Overlay tiles: ${tiles.size} ($reused kept, $disk from disk, $network from network, " +
                    "${tiles.size - reused - disk - network} in memory or unavailable) in ${loaded.inWholeMilliseconds} ms; " +
                    "sweep ${(start.elapsedNow() - loaded).inWholeMilliseconds} ms on ${sweep.chunks(chunks).size} chunks",
            )
            // Unavailable tiles are not kept: the network may be back next time.
            kept = Kept(area, tiles.mapNotNull { (key, tile) -> tile?.let { key to it } }.toMap())
            grid
        }

    /** The [keys] from [reusable] or, concurrently, from [tile]. */
    private suspend fun load(
        keys: Set<TileKey>,
        reusable: Map<TileKey, HeightTile>,
    ): Map<TileKey, HeightTile?> =
        coroutineScope {
            keys.map { key -> async { key to (reusable[key] ?: tile(key)) } }.awaitAll().toMap()
        }

    // Same zoom, centre moved by at most half the smaller side of the screen.
    private fun MapArea.isNear(other: MapArea): Boolean {
        if (zoom != other.zoom) return false
        val north = Math.toRadians(other.center.latitude - center.latitude) * EARTH_RADIUS
        val east = Math.toRadians(other.center.longitude - center.longitude) * EARTH_RADIUS * cos(Math.toRadians(center.latitude))
        return hypot(north, east) <= min(widthDp, heightDp) / 2 * metresPerDp
    }

    private companion object {
        const val EARTH_RADIUS = 6_371_000.0
    }
}
