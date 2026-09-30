package com.sunshine.app.map

import com.sunshine.app.network.RateLimiters
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.maplibre.android.http.HttpRequest
import org.maplibre.android.http.HttpResponder

class SunshineHttpRequestTest {
    /** Stands in for MapLibre's OkHttp request: records what reaches the network. */
    private class FakeDelegate : HttpRequest {
        val executed = mutableListOf<String>()
        var responder: HttpResponder? = null

        override fun executeRequest(
            httpRequest: HttpResponder,
            nativePtr: Long,
            resourceUrl: String,
            dataRange: String,
            etag: String,
            modified: String,
            offlineUsage: Boolean,
        ) {
            executed += resourceUrl
            responder = httpRequest
        }

        override fun cancelRequest() = Unit
    }

    /** Stands in for MapLibre's native side. */
    private class FakeResponder : HttpResponder {
        val codes = mutableListOf<Int>()
        var body: ByteArray? = null
        var failures = 0

        override fun onResponse(
            responseCode: Int,
            eTag: String?,
            lastModified: String?,
            cacheControl: String?,
            expires: String?,
            retryAfter: String?,
            xRateLimitReset: String?,
            body: ByteArray?,
        ) {
            codes += responseCode
            this.body = body
        }

        override fun handleFailure(
            type: Int,
            errorMessage: String?,
        ) {
            failures++
        }
    }

    private fun TestScope.request(
        delegate: FakeDelegate,
        limiters: RateLimiters,
    ) = SunshineHttpRequest(delegate, limiters, backgroundScope)

    private fun TestScope.limiters() = RateLimiters(now = { testScheduler.currentTime })

    private fun HttpRequest.execute(
        url: String,
        offlineUsage: Boolean,
        responder: HttpResponder = FakeResponder(),
    ) = executeRequest(responder, 0, url, "", "", "", offlineUsage)

    @Test
    fun `browsing requests go out at once, even while region requests hold the limiter`() =
        runTest {
            val limiters = limiters()
            repeat(2) { request(FakeDelegate(), limiters).execute(tile(it), offlineUsage = true) }
            runCurrent()
            val browsing = FakeDelegate()

            request(browsing, limiters).execute(tile(9), offlineUsage = false)

            assertEquals(listOf(tile(9)), browsing.executed)
        }

    @Test
    fun `region requests wait for the host's limiter and release it on a response or a failure`() =
        runTest {
            val limiters = limiters()
            val first = FakeDelegate()
            val second = FakeDelegate()
            val third = FakeDelegate()
            request(first, limiters).execute(tile(1), offlineUsage = true)
            request(second, limiters).execute(tile(2), offlineUsage = true)
            request(third, limiters).execute(tile(3), offlineUsage = true)
            advanceTimeBy(1000)

            assertEquals(listOf(tile(1)), first.executed)
            assertEquals(listOf(tile(2)), second.executed)
            assertEquals(emptyList<String>(), third.executed) // 2 in flight

            first.responder!!.onResponse(200, null, null, null, null, null, null, ByteArray(0))
            runCurrent()
            assertEquals(listOf(tile(3)), third.executed)

            val fourth = FakeDelegate()
            request(fourth, limiters).execute(tile(4), offlineUsage = true)
            advanceTimeBy(1000)
            assertEquals(emptyList<String>(), fourth.executed)
            second.responder!!.handleFailure(HttpRequest.CONNECTION_ERROR, "offline")
            advanceTimeBy(1000)
            assertEquals(listOf(tile(4)), fourth.executed)
        }

    @Test
    fun `a region request cancelled while it waits never reaches the network`() =
        runTest {
            val limiters = limiters()
            repeat(2) { request(FakeDelegate(), limiters).execute(tile(it), offlineUsage = true) }
            val waiting = FakeDelegate()
            val request = request(waiting, limiters)
            request.execute(tile(5), offlineUsage = true)
            runCurrent()

            request.cancelRequest()
            advanceTimeBy(10_000)

            assertEquals(emptyList<String>(), waiting.executed)
        }

    @Test
    fun `the region style is answered locally`() =
        runTest {
            val delegate = FakeDelegate()
            val responder = FakeResponder()

            request(delegate, limiters()).execute(REGION_STYLE_URL, offlineUsage = true, responder)
            runCurrent()

            assertEquals(listOf(200), responder.codes)
            assertArrayEquals(OPEN_TOPO_MAP_STYLE.toByteArray(), responder.body)
            assertEquals(emptyList<String>(), delegate.executed)
        }

    private fun tile(x: Int) = "https://tile.opentopomap.org/17/$x/46000.png"
}
