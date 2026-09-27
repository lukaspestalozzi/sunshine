package com.sunshine.app.elevation

import com.sun.net.httpserver.HttpServer
import com.sunshine.app.network.UserAgentInterceptor
import com.sunshine.core.TileKey
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DemTileFetcherTest {
    @TempDir
    lateinit var cacheDirectory: File

    private val requests = mutableListOf<Request>()

    @Test
    fun `requests the Mapterhorn tile with the app User-Agent`() =
        runTest {
            val tile = fetcher { respond(it, 200) }.fetch(TileKey(12, 2137, 1445))

            assertArrayEquals(TILE_BYTES, (tile as DemTile.Found).bytes)
            assertEquals("https://tiles.mapterhorn.com/12/2137/1445.webp", requests.single().url.toString())
            assertEquals("Sunshine/0.1.0 (Android; com.sunshine.app)", requests.single().header("User-Agent"))
        }

    @Test
    fun `a missing tile is reported as missing without asking the cache`() =
        runTest {
            assertEquals(DemTile.Missing, fetcher { respond(it, 404) }.fetch(KEY))
            assertEquals(1, requests.size)
        }

    @Test
    fun `a server error falls back to the cache`() =
        runTest {
            val tile = fetcher { if (it.request().isForcedCache()) respond(it, 200) else respond(it, 500) }.fetch(KEY)

            assertArrayEquals(TILE_BYTES, (tile as DemTile.Found).bytes)
        }

    // Only a 404 means "not published"; a refusal such as 403 or 429 is a failure.
    @ParameterizedTest
    @ValueSource(ints = [401, 403, 429])
    fun `a client error other than 404 falls back to the cache`(code: Int) =
        runTest {
            val tile = fetcher { if (it.request().isForcedCache()) respond(it, 200) else respond(it, code) }.fetch(KEY)

            assertArrayEquals(TILE_BYTES, (tile as DemTile.Found).bytes)
        }

    @Test
    fun `a network failure falls back to the cache`() =
        runTest {
            val tile = fetcher { if (it.request().isForcedCache()) respond(it, 200) else throw IOException("offline") }.fetch(KEY)

            assertArrayEquals(TILE_BYTES, (tile as DemTile.Found).bytes)
        }

    // OkHttp answers 504 to a forced-cache request when the cache has no entry.
    @Test
    fun `failure and cache miss give unavailable`() =
        runTest {
            assertEquals(
                DemTile.Unavailable,
                fetcher { if (it.request().isForcedCache()) respond(it, 504) else respond(it, 500) }.fetch(KEY),
            )
            assertEquals(
                DemTile.Unavailable,
                fetcher { if (it.request().isForcedCache()) respond(it, 504) else throw IOException("offline") }.fetch(KEY),
            )
        }

    @Test
    fun `cancelling a fetch cancels its HTTP call`() =
        runBlocking {
            val requestStarted = CountDownLatch(1)
            val callCanceled = CountDownLatch(1)
            val fetcher =
                fetcher { chain ->
                    requestStarted.countDown()
                    // A slow server: waits until the call is cancelled (at most 5 s).
                    repeat(500) {
                        if (chain.call().isCanceled()) callCanceled.countDown()
                        Thread.sleep(10)
                    }
                    throw IOException("timed out")
                }
            val fetch = launch(Dispatchers.Default) { fetcher.fetch(KEY) }
            assertTrue(requestStarted.await(5, TimeUnit.SECONDS))

            fetch.cancel()

            assertTrue(callCanceled.await(5, TimeUnit.SECONDS), "the HTTP call was not cancelled")
        }

    // For the debug timing logs (task 6.2 of add-sun-shade-overlay): network and disk are told apart.
    // A local server stands in for Mapterhorn, so that OkHttp's disk cache really answers the repeat.
    @Test
    fun `counts tiles served from the network and from the disk cache`() =
        runTest {
            val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
            server.createContext("/") { exchange ->
                exchange.responseHeaders.add("Cache-Control", "max-age=600")
                exchange.sendResponseHeaders(200, TILE_BYTES.size.toLong())
                exchange.responseBody.use { it.write(TILE_BYTES) }
            }
            server.start()
            try {
                val local = "http://127.0.0.1:${server.address.port}"
                val client =
                    demHttpClient(cacheDirectory, UserAgentInterceptor(versionName = "0.1.0", applicationId = "com.sunshine.app"))
                        .newBuilder()
                        .addInterceptor { chain ->
                            chain.proceed(
                                chain
                                    .request()
                                    .newBuilder()
                                    .url("$local/${KEY.zoom}.webp")
                                    .build(),
                            )
                        }.build()
                val fetcher = DemTileFetcher(client)

                fetcher.fetch(KEY)
                assertEquals(TileLoads(network = 1, disk = 0), fetcher.loads())

                fetcher.fetch(KEY)
                assertEquals(TileLoads(network = 1, disk = 1), fetcher.loads())
            } finally {
                server.stop(0)
            }
        }

    // Stands in for the network behind the production client.
    private fun fetcher(network: (Interceptor.Chain) -> Response): DemTileFetcher {
        val client =
            demHttpClient(cacheDirectory, UserAgentInterceptor(versionName = "0.1.0", applicationId = "com.sunshine.app"))
                .newBuilder()
                .addInterceptor { chain ->
                    requests += chain.request()
                    network(chain)
                }.build()
        return DemTileFetcher(client)
    }

    private fun respond(
        chain: Interceptor.Chain,
        code: Int,
    ): Response =
        Response
            .Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("status $code")
            .body(TILE_BYTES.toResponseBody())
            .build()

    private fun Request.isForcedCache(): Boolean = cacheControl.onlyIfCached

    private companion object {
        val KEY = TileKey(12, 2137, 1445)
        val TILE_BYTES = byteArrayOf(1, 2, 3)
    }
}
