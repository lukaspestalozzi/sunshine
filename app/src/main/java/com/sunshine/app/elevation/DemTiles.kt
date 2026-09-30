package com.sunshine.app.elevation

import com.sunshine.app.network.RateLimiters
import com.sunshine.app.offline.DemTileStore
import com.sunshine.app.offline.StoredTile
import com.sunshine.app.offline.Validators
import com.sunshine.core.TileKey
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** HTTP client for DEM tiles, identified by [userAgent]; the tiles are kept by [DemTileStore], not by an HTTP cache. */
fun demHttpClient(userAgent: Interceptor): OkHttpClient =
    OkHttpClient
        .Builder()
        .addInterceptor(userAgent)
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

/** The outcome of fetching a DEM tile. */
sealed interface DemTile {
    class Found(
        val bytes: ByteArray,
    ) : DemTile

    /** The server does not publish this tile (404): no data at this zoom. */
    data object Missing : DemTile

    /** The tile cannot be obtained now: network or server failure, and no stored copy. */
    data object Unavailable : DemTile
}

/** How many DEM tiles were served from the network and from the store so far. */
data class TileLoads(
    val network: Int,
    val store: Int,
)

/**
 * DEM tiles from the persistent [store], fetched and revalidated over [client] (design D6 of
 * add-offline-regions; elevation-data "DEM tile loading and cache"). [url] gives a tile's address,
 * [now] the wall clock in ms.
 */
class DemTiles(
    private val client: OkHttpClient,
    private val store: DemTileStore,
    private val limiters: RateLimiters,
    private val url: (TileKey) -> String = MapterhornTiles::url,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val fromNetwork = AtomicInteger()
    private val fromStore = AtomicInteger()

    /** Tiles served so far, for the debug timing logs (task 6.2 of add-sun-shade-overlay). */
    fun loads(): TileLoads = TileLoads(fromNetwork.get(), fromStore.get())

    /**
     * The tile. A fresh stored tile is returned without a request; an expired one is revalidated
     * and, if that fails, returned whatever its age. With [forRegion], the tile is claimed by that
     * region: a stored tile without a request, a new one through the server's rate limiter.
     * Cancelling the calling coroutine cancels the HTTP call.
     */
    suspend fun fetch(
        key: TileKey,
        forRegion: Long? = null,
    ): DemTile {
        val stored = store.get(key)
        if (stored != null && forRegion != null) {
            store.claim(key, forRegion)
            return served(stored)
        }
        if (stored != null && now() < stored.validators.freshUntil) return served(stored)
        return if (stored == null) fetchNew(key, forRegion) else revalidate(key, stored)
    }

    private suspend fun fetchNew(
        key: TileKey,
        forRegion: Long?,
    ): DemTile {
        val request = Request.Builder().url(url(key)).build()
        val result =
            if (forRegion == null) {
                call(request)
            } else {
                limiters.forHost(request.url.host).withPermit { call(request) }
            }
        return when (result) {
            is Result.Tile -> {
                store.putFound(key, result.bytes, result.validators, forRegion)
                fromNetwork.incrementAndGet()
                DemTile.Found(result.bytes)
            }
            is Result.NotPublished -> {
                store.putMissing(key, result.validators, forRegion)
                DemTile.Missing
            }
            Result.Failed, is Result.NotModified -> DemTile.Unavailable
        }
    }

    private suspend fun revalidate(
        key: TileKey,
        stored: StoredTile,
    ): DemTile {
        val request =
            Request
                .Builder()
                .url(url(key))
                .apply {
                    val (etag, lastModified) = stored.validators
                    when {
                        etag != null -> header("If-None-Match", etag)
                        lastModified != null -> header("If-Modified-Since", lastModified)
                    }
                }.build()
        return when (val result = call(request)) {
            is Result.NotModified -> {
                store.refresh(key, result.validators.keepingValidatorsOf(stored.validators))
                served(stored)
            }
            is Result.Tile -> {
                store.putFound(key, result.bytes, result.validators, region = null)
                fromNetwork.incrementAndGet()
                DemTile.Found(result.bytes)
            }
            is Result.NotPublished -> {
                store.putMissing(key, result.validators, region = null)
                DemTile.Missing
            }
            Result.Failed -> served(stored) // offline or failing: any age will do
        }
    }

    private fun served(stored: StoredTile): DemTile {
        val bytes = stored.bytes ?: return DemTile.Missing
        fromStore.incrementAndGet()
        return DemTile.Found(bytes)
    }

    private suspend fun call(request: Request): Result =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(
                        call: Call,
                        e: IOException,
                    ) {
                        continuation.resume(Result.Failed)
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        val result =
                            try {
                                response.use { toResult(it) }
                            } catch (_: IOException) {
                                Result.Failed // the body failed to arrive
                            }
                        continuation.resume(result)
                    }
                },
            )
        }

    private fun toResult(response: Response): Result =
        when (response.code) {
            OK -> Result.Tile(response.body!!.bytes(), validatorsOf(response))
            NOT_MODIFIED -> Result.NotModified(validatorsOf(response))
            NOT_FOUND -> Result.NotPublished(validatorsOf(response))
            else -> Result.Failed // including 401, 403 and 429: only a 404 means "not published"
        }

    /** Fresh for `max-age`, else until `Expires`, else for 7 days (offline-regions "Freshness of stored tiles"). */
    private fun validatorsOf(response: Response): Validators {
        val received = now()
        val maxAge = response.cacheControl.maxAgeSeconds
        val freshUntil =
            when {
                maxAge >= 0 -> received + TimeUnit.SECONDS.toMillis(maxAge.toLong())
                else -> response.headers.getDate("Expires")?.time ?: (received + DEFAULT_FRESHNESS_MILLIS)
            }
        return Validators(response.header("ETag"), response.header("Last-Modified"), freshUntil)
    }

    /** A 304 may omit the validators; the stored ones then stay. */
    private fun Validators.keepingValidatorsOf(stored: Validators) =
        copy(
            etag = etag ?: stored.etag,
            lastModified =
                lastModified ?: stored.lastModified,
        )

    private sealed interface Result {
        class Tile(
            val bytes: ByteArray,
            val validators: Validators,
        ) : Result

        class NotModified(
            val validators: Validators,
        ) : Result

        class NotPublished(
            val validators: Validators,
        ) : Result

        data object Failed : Result
    }

    private companion object {
        const val OK = 200
        const val NOT_MODIFIED = 304
        const val NOT_FOUND = 404
        val DEFAULT_FRESHNESS_MILLIS = TimeUnit.DAYS.toMillis(7)
    }
}

private const val CONNECT_TIMEOUT_SECONDS = 10L
private const val READ_TIMEOUT_SECONDS = 20L
