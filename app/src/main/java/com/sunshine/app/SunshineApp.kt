package com.sunshine.app

import android.app.Application
import com.sunshine.app.elevation.DemTileFetcher
import com.sunshine.app.elevation.ElevationRepository
import com.sunshine.app.elevation.MapterhornTiles
import com.sunshine.app.elevation.TileCache
import com.sunshine.app.elevation.decodeArgb
import com.sunshine.app.elevation.demHttpClient
import com.sunshine.app.network.UserAgentInterceptor
import com.sunshine.app.sunshine.SunshineRepository
import com.sunshine.app.sunshine.debugLog
import java.io.File
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil

class SunshineApp : Application() {
    /** Shared by all screens and features, so decoded tiles and the HTTP cache are shared too (design D6). */
    private val tileCache: TileCache by lazy {
        val client = demHttpClient(File(cacheDir, "dem-tiles"), userAgent())
        TileCache(
            fetch = DemTileFetcher(client)::fetch,
            decode = { bytes -> decodeArgb(bytes, MapterhornTiles.TILE_SIZE) },
        )
    }

    val elevationRepository: ElevationRepository by lazy { ElevationRepository(tileCache) }

    val sunshineRepository: SunshineRepository by lazy { SunshineRepository(tile = tileCache::tile, log = ::debugLog) }

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        HttpRequestUtil.setOkHttpClient(mapHttpClient())
    }

    private fun mapHttpClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            // Same limit as MapLibre's own default client; OkHttp's default of 5 slows tile loading.
            .dispatcher(Dispatcher().apply { maxRequestsPerHost = MAX_TILE_REQUESTS_PER_HOST })
            .addInterceptor(userAgent())
            .build()

    private fun userAgent() = UserAgentInterceptor(BuildConfig.VERSION_NAME, BuildConfig.APPLICATION_ID)

    private companion object {
        const val MAX_TILE_REQUESTS_PER_HOST = 20
    }
}
