package com.sunshine.app.map

import com.sunshine.app.network.RateLimiters
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.maplibre.android.LibraryLoaderProvider
import org.maplibre.android.ModuleProvider
import org.maplibre.android.ModuleProviderImpl
import org.maplibre.android.http.HttpRequest
import org.maplibre.android.http.HttpResponder
import org.maplibre.android.module.http.HttpRequestImpl

/**
 * MapLibre's HTTP requests (design D3 of add-offline-regions). Browsing goes to [delegate] at once.
 * Requests of offline region downloads (`offlineUsage`) first wait for their server's rate limiter,
 * which they hold until the answer arrives. [REGION_STYLE_URL] is answered locally.
 * [executeRequest] never blocks MapLibre's calling thread.
 */
class SunshineHttpRequest(
    private val delegate: HttpRequest,
    private val limiters: RateLimiters,
    private val scope: CoroutineScope,
) : HttpRequest {
    @Volatile
    private var pending: Job? = null

    override fun executeRequest(
        httpRequest: HttpResponder,
        nativePtr: Long,
        resourceUrl: String,
        dataRange: String,
        etag: String,
        modified: String,
        offlineUsage: Boolean,
    ) {
        if (resourceUrl == REGION_STYLE_URL) {
            // Answered off MapLibre's thread, as the network would be.
            pending = scope.launch { httpRequest.onResponse(OK, null, null, null, null, null, null, OPEN_TOPO_MAP_STYLE.toByteArray()) }
            return
        }
        val host = resourceUrl.toHttpUrlOrNull()?.host
        if (!offlineUsage || host == null) {
            delegate.executeRequest(httpRequest, nativePtr, resourceUrl, dataRange, etag, modified, offlineUsage)
            return
        }
        val limiter = limiters.forHost(host)
        pending =
            scope.launch {
                limiter.acquire()
                val released = AtomicBoolean(false)
                val release = { if (released.compareAndSet(false, true)) limiter.release() }
                try {
                    ensureActive()
                } catch (cancelled: Throwable) {
                    release()
                    throw cancelled
                }
                delegate.executeRequest(ReleasingResponder(httpRequest, release), nativePtr, resourceUrl, dataRange, etag, modified, true)
            }
    }

    override fun cancelRequest() {
        pending?.cancel()
        delegate.cancelRequest()
    }

    /** Passes the answer on and frees the limiter's slot. */
    private class ReleasingResponder(
        private val responder: HttpResponder,
        private val release: () -> Unit,
    ) : HttpResponder {
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
            release()
            responder.onResponse(responseCode, eTag, lastModified, cacheControl, expires, retryAfter, xRateLimitReset, body)
        }

        override fun handleFailure(
            type: Int,
            errorMessage: String?,
        ) {
            release()
            responder.handleFailure(type, errorMessage)
        }
    }

    private companion object {
        const val OK = 200
    }
}

/** Gives MapLibre a [SunshineHttpRequest] around its own OkHttp request for every request. */
class SunshineModuleProvider(
    private val limiters: RateLimiters,
    private val scope: CoroutineScope,
) : ModuleProvider {
    private val defaults = ModuleProviderImpl()

    override fun createHttpRequest(): HttpRequest = SunshineHttpRequest(HttpRequestImpl(), limiters, scope)

    override fun createLibraryLoaderProvider(): LibraryLoaderProvider = defaults.createLibraryLoaderProvider()
}
