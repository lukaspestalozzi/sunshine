package com.sunshine.app.sunshine

import com.sunshine.app.elevation.TileLoads
import com.sunshine.core.GeoBounds
import com.sunshine.core.HeightTile
import com.sunshine.core.MapArea
import com.sunshine.core.ShadeGrid
import com.sunshine.core.SunPosition
import com.sunshine.core.SunShadeSweep
import com.sunshine.core.TileKey
import kotlin.time.TimeSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive

/**
 * Sun-shade grids of the visible area (design D8 of add-sun-shade-overlay). [tile] loads a tile,
 * `null` if it is unavailable. The lines are computed in [chunks] coroutines on the caller's
 * dispatcher. The tiles of the last grid are kept for the next one while the areas intersect, so a
 * new time at the same place loads only the tiles its new upwind lines need, and the parts of a
 * panned area share their tiles (design D5 of overlay-pan-reuse).
 */
class OverlayRepository(
    private val tile: suspend (TileKey) -> HeightTile?,
    private val chunks: Int = Runtime.getRuntime().availableProcessors(),
    private val loads: () -> TileLoads = { TileLoads(0, 0) },
    private val log: (String) -> Unit = {},
    private val debug: DebugInfo = DebugInfo(),
    private val memoryTiles: () -> MemoryTiles? = { null },
) {
    private var kept: Kept? = null

    private class Kept(
        val area: MapArea,
        val tiles: Map<TileKey, HeightTile>,
    )

    /** The grid of [area] with the sun at [sun], in cells of [cellDp] dp (design D9 of add-sun-exposure-heatmap). */
    suspend fun grid(
        area: MapArea,
        sun: SunPosition,
        cellDp: Double = SunShadeSweep.CELL_DP,
    ): ShadeGrid =
        coroutineScope {
            val start = TimeSource.Monotonic.markNow()
            val sweep = SunShadeSweep(area, sun, cellDp)
            val reusable = kept?.takeIf { it.area.intersects(area) }?.tiles.orEmpty()
            val before = loads()
            val ground = load(sweep.groundTiles(), reusable)
            // Below −3.5° every cell with ground is shade: no upwind tiles, no sweep (design D12). The
            // kept upwind tiles stay for the next daytime grid.
            if (sweep.isNight) {
                kept = Kept(area, reusable + ground.mapNotNull { (key, tile) -> tile?.let { key to it } })
                record(ground, reusable, before)
                log("Overlay at night: ${ground.size} ground tiles in ${start.elapsedNow().inWholeMilliseconds} ms")
                return@coroutineScope sweep.night(ground)
            }
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
            val sources = record(tiles, reusable, before)
            log(
                "Overlay tiles: ${tiles.size} (${sources.kept} kept, ${sources.disk} from store, ${sources.network} from network, " +
                    "${sources.memory} in memory, ${sources.unavailable} unavailable) in ${loaded.inWholeMilliseconds} ms; " +
                    "sweep ${(start.elapsedNow() - loaded).inWholeMilliseconds} ms on ${sweep.chunks(chunks).size} chunks",
            )
            // Unavailable tiles are not kept: the network may be back next time.
            kept = Kept(area, tiles.mapNotNull { (key, tile) -> tile?.let { key to it } }.toMap())
            grid
        }

    /**
     * Counts [tiles] by source for the debug box (settings spec, "Debug info"). Other features
     * loading tiles at the same time can inflate the disk and network counts at memory's expense.
     */
    private fun record(
        tiles: Map<TileKey, HeightTile?>,
        reusable: Map<TileKey, HeightTile>,
        before: TileLoads,
    ): TileSources {
        val kept = tiles.keys.count { it in reusable }
        val unavailable = tiles.values.count { it == null }
        val disk = loads().store - before.store
        val network = loads().network - before.network
        val memory = (tiles.size - kept - unavailable - disk - network).coerceAtLeast(0)
        val sources = TileSources(kept, memory, disk, network, unavailable)
        val held = memoryTiles()
        debug.update { it.copy(gridTiles = sources, memoryTiles = held ?: it.memoryTiles) }
        return sources
    }

    /** The [keys] from [reusable] or, concurrently, from [tile]. */
    private suspend fun load(
        keys: Set<TileKey>,
        reusable: Map<TileKey, HeightTile>,
    ): Map<TileKey, HeightTile?> =
        coroutineScope {
            keys.map { key -> async { key to (reusable[key] ?: tile(key)) } }.awaitAll().toMap()
        }

    // Same zoom, and the bounds overlap or touch.
    private fun MapArea.intersects(other: MapArea): Boolean {
        if (zoom != other.zoom) return false
        val a = GeoBounds.of(this)
        val b = GeoBounds.of(other)
        return a.south <= b.north && b.south <= a.north && a.west <= b.east && b.west <= a.east
    }
}
