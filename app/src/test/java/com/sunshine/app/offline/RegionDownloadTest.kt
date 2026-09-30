package com.sunshine.app.offline

import com.sun.net.httpserver.HttpServer
import com.sunshine.app.elevation.DemTile
import com.sunshine.app.elevation.DemTiles
import com.sunshine.app.elevation.demHttpClient
import com.sunshine.app.network.RateLimiters
import com.sunshine.app.network.UserAgentInterceptor
import com.sunshine.core.TileKey
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class RegionDownloadTest {
    @TempDir
    lateinit var directory: File

    private val database = inMemoryOfflineDatabase()
    private val dao = database.dao()

    @AfterEach
    fun close() = database.close()

    /** The map part: records the regions it downloads, and finishes each when its gate opens. */
    private class FakeMap : MapRegionPart {
        val started = mutableListOf<Long>()
        val gates = HashMap<Long, CompletableDeferred<Unit>>()
        var running = 0
        var mostRunning = 0

        fun gate(region: Long) = gates.getOrPut(region) { CompletableDeferred() }

        override suspend fun download(
            region: RegionRow,
            onStatus: suspend (MapStatus) -> Unit,
        ): Long {
            started += region.id
            running++
            mostRunning = maxOf(mostRunning, running)
            try {
                onStatus(MapStatus(completed = 0, required = 10, bytes = 0))
                gate(region.id).await()
                onStatus(MapStatus(completed = 10, required = 10, bytes = 1000))
                return 1000
            } finally {
                running--
            }
        }
    }

    private suspend fun region(createdAt: Long) = dao.insertRegion(REGION.copy(createdAt = createdAt))

    private fun downloader(
        map: MapRegionPart,
        fetch: suspend (TileKey, Long) -> DemTile,
    ) = RegionDownloader(dao, map, fetch, demTilesOf = { DEM_KEYS }, now = { 0 })

    @Test
    fun `regions download oldest first, one at a time`() =
        runTest {
            val map = FakeMap()
            val newer = region(createdAt = 2)
            val older = region(createdAt = 1)
            map.gate(newer).complete(Unit)
            map.gate(older).complete(Unit)

            downloader(map) { _, _ -> DemTile.Found(byteArrayOf(1)) }.downloadAll()

            assertEquals(listOf(older, newer), map.started)
            assertEquals(1, map.mostRunning)
            assertEquals(RegionState.COMPLETE, dao.region(older)!!.state)
            assertEquals(RegionState.COMPLETE, dao.region(newer)!!.state)
        }

    @Test
    fun `a region is complete only when both the map and the DEM tiles are`() =
        runTest {
            val map = FakeMap()
            val id = region(createdAt = 1)
            val download = launch { downloader(map) { _, _ -> DemTile.Found(byteArrayOf(1)) }.downloadAll() }
            runCurrent()

            assertEquals(RegionState.QUEUED, dao.region(id)!!.state) // DEM done, map not yet

            map.gate(id).complete(Unit)
            download.join()
            val region = dao.region(id)!!
            assertEquals(RegionState.COMPLETE, region.state)
            assertEquals(100, region.progress)
            assertEquals(1000, region.mapBytes)
        }

    @Test
    fun `a failing tile is retried later, and the region stays incomplete meanwhile`() =
        runTest {
            // Queries in virtual time, so that the retry times are exact.
            val database = inMemoryOfflineDatabase(queries = StandardTestDispatcher(testScheduler))
            val dao = database.dao()
            val map = FakeMap()
            val id = dao.insertRegion(REGION)
            map.gate(id).complete(Unit)
            var serverFails = true
            val attempts = mutableListOf<Long>()
            val download =
                launch {
                    RegionDownloader(dao, map, { key, _ ->
                        if (key == DEM_KEYS[1]) attempts += testScheduler.currentTime
                        if (key == DEM_KEYS[1] && serverFails) DemTile.Unavailable else DemTile.Found(byteArrayOf(1))
                    }, demTilesOf = { DEM_KEYS }, now = { 0 }).downloadAll()
                }
            advanceTimeBy(20_000)

            assertEquals(RegionState.QUEUED, dao.region(id)!!.state)
            assertEquals(listOf(0L, 5_000L, 15_000L), attempts) // after 5 s, then 10 s

            serverFails = false
            download.join()
            assertEquals(RegionState.COMPLETE, dao.region(id)!!.state)
            database.close()
        }

    @Test
    fun `a region deleted while downloading stops, and the next one goes on`() =
        runTest {
            val map = FakeMap()
            val deleted = region(createdAt = 1)
            val next = region(createdAt = 2)
            map.gate(next).complete(Unit)
            val requested = mutableListOf<Pair<TileKey, Long>>()
            val downloader = downloader(map) { key, region -> DemTile.Found(byteArrayOf(1)).also { requested += key to region } }
            val download = launch { downloader.downloadAll() }
            runCurrent()

            dao.markDeleted(deleted)
            downloader.cancel(deleted)
            val requestsBefore = requested.count { it.second == deleted }
            download.join()

            assertEquals(requestsBefore, requested.count { it.second == deleted })
            assertEquals(RegionState.DELETED, dao.region(deleted)!!.state)
            assertEquals(RegionState.COMPLETE, dao.region(next)!!.state)
        }

    // DemTiles on a local server: what reaches the network.
    @Test
    fun `stored DEM tiles are claimed without a request, also after a restart`() =
        runTest {
            val requests = mutableListOf<String>()
            val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
            server.createContext("/") { exchange ->
                synchronized(requests) { requests += exchange.requestURI.path }
                exchange.sendResponseHeaders(200, 1)
                exchange.responseBody.use { it.write(1) }
            }
            server.start()
            try {
                val store = DemTileStore(dao, directory)
                val tiles =
                    DemTiles(
                        demHttpClient(UserAgentInterceptor("0.1.0", "com.sunshine.app")),
                        store,
                        RateLimiters(),
                        url = { "http://127.0.0.1:${server.address.port}/${it.zoom}/${it.x}/${it.y}.webp" },
                    )
                tiles.fetch(DEM_KEYS[0]) // browsed before the download
                val map = FakeMap()
                val id = region(createdAt = 1)
                map.gate(id).complete(Unit)

                // The first run ends after one more tile, as when the process is ended.
                var budget = 2
                val first =
                    RegionDownloader(dao, map, { key, region ->
                        check(budget-- > 0) { "process ended" }
                        tiles.fetch(key, region)
                    }, demTilesOf = { DEM_KEYS })
                runCatching { first.downloadAll() }
                RegionDownloader(dao, map, tiles::fetch, demTilesOf = { DEM_KEYS }).downloadAll()

                // Each tile once: the browsed one before the download, the others by the two runs.
                assertEquals(DEM_KEYS.map { "/${it.zoom}/${it.x}/${it.y}.webp" }.sorted(), requests.sorted())
                assertEquals(0, store.browsedBytes()) // all claimed
                assertEquals(RegionState.COMPLETE, dao.region(id)!!.state)
            } finally {
                server.stop(0)
            }
        }

    private companion object {
        val DEM_KEYS = listOf(TileKey(12, 1, 1), TileKey(12, 2, 1), TileKey(12, 3, 1))
        val REGION = RegionRow(centreLat = 46.6, centreLon = 7.9, south = 46.5, west = 7.8, north = 46.7, east = 8.0, createdAt = 1)
    }
}
