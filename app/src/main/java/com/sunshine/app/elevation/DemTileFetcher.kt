package com.sunshine.app.elevation

import com.sunshine.core.TileKey
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * HTTP client for DEM tiles (design D3): identified by [userAgent], with a disk cache in
 * [cacheDirectory]. Tiles follow the server's caching headers (7 days, then revalidated).
 */
fun demHttpClient(
    cacheDirectory: File,
    userAgent: Interceptor,
): OkHttpClient =
    OkHttpClient
        .Builder()
        .addInterceptor(userAgent)
        .cache(Cache(cacheDirectory, CACHE_BYTES))
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

    /** The tile cannot be obtained now: network or server failure, and no cached copy. */
    data object Unavailable : DemTile
}

/** Loads DEM tile bytes (elevation-data "Unknown elevation"). */
class DemTileFetcher(
    private val client: OkHttpClient,
) {
    /**
     * The tile from the network or the cache. After a network failure or a server error, a cached
     * copy is served even if stale, so tiles seen before also work offline. Cancelling the calling
     * coroutine cancels the HTTP call.
     */
    suspend fun fetch(key: TileKey): DemTile =
        when (val result = request(key, cacheControl = null)) {
            is Result.Tile -> DemTile.Found(result.bytes)
            Result.Missing -> DemTile.Missing
            Result.Failed ->
                (request(key, CacheControl.FORCE_CACHE) as? Result.Tile)?.let { DemTile.Found(it.bytes) }
                    ?: DemTile.Unavailable
        }

    private suspend fun request(
        key: TileKey,
        cacheControl: CacheControl?,
    ): Result =
        suspendCancellableCoroutine { continuation ->
            val request =
                Request
                    .Builder()
                    .url(MapterhornTiles.url(key))
                    .apply { if (cacheControl != null) cacheControl(cacheControl) }
                    .build()
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
        when {
            response.isSuccessful -> Result.Tile(response.body!!.bytes())
            response.code >= SERVER_ERROR -> Result.Failed
            else -> Result.Missing
        }

    private sealed interface Result {
        class Tile(
            val bytes: ByteArray,
        ) : Result

        data object Missing : Result

        data object Failed : Result
    }

    private companion object {
        const val SERVER_ERROR = 500
    }
}

private const val CACHE_BYTES = 100L * 1024 * 1024
private const val CONNECT_TIMEOUT_SECONDS = 10L
private const val READ_TIMEOUT_SECONDS = 20L
