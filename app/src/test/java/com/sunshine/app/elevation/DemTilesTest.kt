package com.sunshine.app.elevation

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.sunshine.app.network.RateLimiters
import com.sunshine.app.network.UserAgentInterceptor
import com.sunshine.app.offline.DemTileStore
import com.sunshine.app.offline.inMemoryOfflineDatabase
import com.sunshine.core.TileKey
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class DemTilesTest {
    @TempDir
    lateinit var directory: File

    private val database = inMemoryOfflineDatabase()
    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    private val requests = mutableListOf<Map<String, String?>>()
    private var respond: (HttpExchange) -> Unit = { it.send(200, TILE, "Cache-Control" to "max-age=604800") }
    private var now = 1_000_000L

    init {
        server.createContext("/") { exchange ->
            synchronized(requests) {
                requests +=
                    mapOf(
                        "path" to exchange.requestURI.path,
                        "User-Agent" to exchange.requestHeaders.getFirst("User-Agent"),
                        "If-Modified-Since" to exchange.requestHeaders.getFirst("If-Modified-Since"),
                        "If-None-Match" to exchange.requestHeaders.getFirst("If-None-Match"),
                    )
            }
            respond(exchange)
        }
        server.start()
    }

    @AfterEach
    fun close() {
        server.stop(0)
        database.close()
    }

    private val store by lazy { DemTileStore(database.dao(), directory, now = { now }) }

    private fun TestScope.tiles(
        limiters: RateLimiters = RateLimiters(now = { testScheduler.currentTime }),
        port: Int = server.address.port,
    ) = DemTiles(
        client = demHttpClient(UserAgentInterceptor(versionName = "0.1.0", applicationId = "com.sunshine.app")),
        store = store,
        limiters = limiters,
        url = { "http://127.0.0.1:$port/${it.zoom}/${it.x}/${it.y}.webp" },
        now = { now },
    )

    @Test
    fun `requests the tile with the app User-Agent and stores it`() =
        runTest {
            val tile = tiles().fetch(KEY)

            assertArrayEquals(TILE, (tile as DemTile.Found).bytes)
            assertEquals("/12/2137/1445.webp", requests.single()["path"])
            assertEquals("Sunshine/0.1.0 (Android; com.sunshine.app)", requests.single()["User-Agent"])
            assertArrayEquals(TILE, store.get(KEY)!!.bytes)
        }

    @Test
    fun `a fresh tile makes no request and counts as served from the store`() =
        runTest {
            val tiles = tiles()
            tiles.fetch(KEY)
            now += DAY

            val tile = tiles.fetch(KEY)

            assertArrayEquals(TILE, (tile as DemTile.Found).bytes)
            assertEquals(1, requests.size)
            assertEquals(TileLoads(network = 1, store = 1), tiles.loads())
        }

    @Test
    fun `cancelling a fetch cancels its HTTP call`() =
        runBlocking {
            val requestStarted = CountDownLatch(1)
            val callCanceled = CountDownLatch(1)
            val client =
                demHttpClient(UserAgentInterceptor(versionName = "0.1.0", applicationId = "com.sunshine.app"))
                    .newBuilder()
                    .addInterceptor { chain ->
                        requestStarted.countDown()
                        // A slow server: waits until the call is cancelled (at most 5 s).
                        repeat(500) {
                            if (chain.call().isCanceled()) callCanceled.countDown()
                            Thread.sleep(10)
                        }
                        throw IOException("timed out")
                    }.build()
            val tiles = DemTiles(client, store, RateLimiters(), url = { "http://127.0.0.1:1/${it.zoom}.webp" }, now = { now })
            val fetch = launch(Dispatchers.Default) { tiles.fetch(KEY) }
            assertTrue(requestStarted.await(5, TimeUnit.SECONDS))

            fetch.cancel()

            assertTrue(callCanceled.await(5, TimeUnit.SECONDS), "the HTTP call was not cancelled")
        }

    @Test
    fun `an expired tile is revalidated, and a 304 keeps it fresh for another 7 days`() =
        runTest {
            respond = { it.send(200, TILE, "Cache-Control" to "max-age=604800", "Last-Modified" to LAST_MODIFIED) }
            tiles().fetch(KEY)
            now += 10 * DAY
            respond = { it.send(304, null, "Cache-Control" to "max-age=604800") }

            val tile = tiles().fetch(KEY)

            assertArrayEquals(TILE, (tile as DemTile.Found).bytes)
            assertEquals(LAST_MODIFIED, requests.last()["If-Modified-Since"])
            assertEquals(now + 7 * DAY, store.get(KEY)!!.validators.freshUntil)
        }

    @Test
    fun `an expired tile is revalidated by its ETag when it has one`() =
        runTest {
            respond = { it.send(200, TILE, "Cache-Control" to "max-age=60", "ETag" to "\"abc\"", "Last-Modified" to LAST_MODIFIED) }
            tiles().fetch(KEY)
            now += DAY
            respond = { it.send(304, null) }

            tiles().fetch(KEY)

            assertEquals("\"abc\"", requests.last()["If-None-Match"])
            assertEquals(null, requests.last()["If-Modified-Since"])
        }

    @Test
    fun `a changed tile replaces the stored one and keeps its regions`() =
        runTest {
            tiles().fetch(KEY, forRegion = 1)
            now += 10 * DAY
            respond = { it.send(200, NEW_TILE, "Cache-Control" to "max-age=604800") }

            val tile = tiles().fetch(KEY)

            assertArrayEquals(NEW_TILE, (tile as DemTile.Found).bytes)
            assertArrayEquals(NEW_TILE, store.get(KEY)!!.bytes)
            assertEquals(0, store.browsedBytes()) // still region 1's
        }

    @Test
    fun `an expired tile is used when the server cannot be reached`() =
        runTest {
            tiles().fetch(KEY)
            now += 200 * DAY
            server.stop(0)

            val tile = tiles().fetch(KEY)

            assertArrayEquals(TILE, (tile as DemTile.Found).bytes)
        }

    @Test
    fun `a 404 is recorded and returned as missing offline`() =
        runTest {
            respond = { it.send(404, null, "Cache-Control" to "max-age=604800") }
            assertEquals(DemTile.Missing, tiles().fetch(KEY))
            now += 200 * DAY
            server.stop(0)

            assertEquals(DemTile.Missing, tiles().fetch(KEY))
        }

    @Test
    fun `a tile never stored is unavailable when the server cannot be reached or fails`() =
        runTest {
            respond = { it.send(500, null) }
            assertEquals(DemTile.Unavailable, tiles().fetch(KEY))
            assertEquals(null, store.get(KEY))

            server.stop(0)
            assertEquals(DemTile.Unavailable, tiles().fetch(KEY))
        }

    @Test
    fun `freshness comes from max-age, else Expires, else 7 days`() =
        runTest {
            respond = { it.send(200, TILE, "Cache-Control" to "max-age=600", "Expires" to "Thu, 01 Jan 2099 00:00:00 GMT") }
            tiles().fetch(TileKey(12, 1, 1))
            respond = { it.send(200, TILE, "Expires" to "Thu, 01 Oct 2026 00:00:00 GMT") }
            tiles().fetch(TileKey(12, 2, 1))
            respond = { it.send(200, TILE) }
            tiles().fetch(TileKey(12, 3, 1))

            assertEquals(now + 600_000, store.get(TileKey(12, 1, 1))!!.validators.freshUntil)
            assertEquals(1_790_812_800_000L, store.get(TileKey(12, 2, 1))!!.validators.freshUntil)
            assertEquals(now + 7 * DAY, store.get(TileKey(12, 3, 1))!!.validators.freshUntil)
        }

    @Test
    fun `a region fetch waits for the server's limiter and claims the tile`() =
        runTest {
            val limiters = RateLimiters(now = { testScheduler.currentTime })
            val limiter = limiters.forHost("127.0.0.1")
            limiter.acquire()
            limiter.acquire() // both slots taken
            val fetch = async { tiles(limiters).fetch(KEY, forRegion = 3) }
            testScheduler.advanceTimeBy(1000)
            assertEquals(0, requests.size)

            limiter.release()
            fetch.await()

            assertEquals(1, requests.size)
            assertEquals(0, store.browsedBytes())
        }

    @Test
    fun `a stored tile is claimed by a region without a request`() =
        runTest {
            tiles().fetch(KEY)

            tiles().fetch(KEY, forRegion = 5)

            assertEquals(1, requests.size)
            assertEquals(0, store.browsedBytes())
        }

    private fun HttpExchange.send(
        code: Int,
        body: ByteArray?,
        vararg headers: Pair<String, String>,
    ) {
        headers.forEach { (name, value) -> responseHeaders.add(name, value) }
        sendResponseHeaders(code, if (body == null) -1 else body.size.toLong())
        responseBody.use { if (body != null) it.write(body) }
    }

    private companion object {
        val KEY = TileKey(12, 2137, 1445)
        val TILE = byteArrayOf(1, 2, 3)
        val NEW_TILE = byteArrayOf(4, 5, 6, 7)
        const val DAY = 24L * 3600 * 1000
        const val LAST_MODIFIED = "Thu, 24 Sep 2026 18:43:19 GMT"
    }
}
