package com.sunshine.app.elevation

import com.sunshine.app.network.UserAgentInterceptor
import com.sunshine.core.TileKey
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class DemTileFetcherTest {
    @TempDir
    lateinit var cacheDirectory: File

    private val requests = mutableListOf<Request>()

    @Test
    fun `requests the Mapterhorn tile with the app User-Agent`() =
        runTest {
            val bytes = fetcher { respond(it, 200) }.fetch(TileKey(12, 2137, 1445))

            assertArrayEquals(TILE_BYTES, bytes)
            assertEquals("https://tiles.mapterhorn.com/12/2137/1445.webp", requests.single().url.toString())
            assertEquals("Sunshine/0.1.0 (Android; com.sunshine.app)", requests.single().header("User-Agent"))
        }

    @Test
    fun `a missing tile gives null without asking the cache`() =
        runTest {
            assertNull(fetcher { respond(it, 404) }.fetch(KEY))
            assertEquals(1, requests.size)
        }

    @Test
    fun `a server error falls back to the cache`() =
        runTest {
            val bytes = fetcher { if (it.request().isForcedCache()) respond(it, 200) else respond(it, 500) }.fetch(KEY)

            assertArrayEquals(TILE_BYTES, bytes)
        }

    @Test
    fun `a network failure falls back to the cache`() =
        runTest {
            val bytes = fetcher { if (it.request().isForcedCache()) respond(it, 200) else throw IOException("offline") }.fetch(KEY)

            assertArrayEquals(TILE_BYTES, bytes)
        }

    // OkHttp answers 504 to a forced-cache request when the cache has no entry.
    @Test
    fun `failure and cache miss give null`() =
        runTest {
            assertNull(fetcher { if (it.request().isForcedCache()) respond(it, 504) else respond(it, 500) }.fetch(KEY))
            assertNull(fetcher { if (it.request().isForcedCache()) respond(it, 504) else throw IOException("offline") }.fetch(KEY))
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
        return DemTileFetcher(client, Dispatchers.IO)
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
