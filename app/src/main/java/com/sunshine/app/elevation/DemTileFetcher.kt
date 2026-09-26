package com.sunshine.app.elevation

import com.sunshine.core.TileKey
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request

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

/** Loads DEM tile bytes; `null` when the tile cannot be obtained (elevation-data "Unknown elevation"). */
class DemTileFetcher(
    private val client: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher,
) {
    /**
     * The tile from the network or the cache. After a network failure or a server error, a cached
     * copy is served even if stale, so tiles seen before also work offline. A missing tile (404)
     * gives `null`.
     */
    suspend fun fetch(key: TileKey): ByteArray? =
        withContext(ioDispatcher) {
            when (val result = request(key, cacheControl = null)) {
                is Result.Tile -> result.bytes
                Result.Missing -> null
                Result.Failed -> (request(key, CacheControl.FORCE_CACHE) as? Result.Tile)?.bytes
            }
        }

    private fun request(
        key: TileKey,
        cacheControl: CacheControl?,
    ): Result {
        val request =
            Request
                .Builder()
                .url(MapterhornTiles.url(key))
                .apply { if (cacheControl != null) cacheControl(cacheControl) }
                .build()
        return try {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> Result.Tile(response.body!!.bytes())
                    response.code >= SERVER_ERROR -> Result.Failed
                    else -> Result.Missing
                }
            }
        } catch (_: IOException) {
            Result.Failed
        }
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
