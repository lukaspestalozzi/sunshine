package com.sunshine.app

import android.app.Application
import com.sunshine.app.elevation.DemTiles
import com.sunshine.app.elevation.ElevationRepository
import com.sunshine.app.elevation.MapterhornTiles
import com.sunshine.app.elevation.TileCache
import com.sunshine.app.elevation.decodeArgb
import com.sunshine.app.elevation.demHttpClient
import com.sunshine.app.map.SunshineModuleProvider
import com.sunshine.app.network.RateLimiters
import com.sunshine.app.network.UserAgentInterceptor
import com.sunshine.app.offline.AmbientLimit
import com.sunshine.app.offline.DemTileStore
import com.sunshine.app.offline.DownloadNotification
import com.sunshine.app.offline.MapLibreRegionPart
import com.sunshine.app.offline.OfflineDatabase
import com.sunshine.app.offline.RegionDownloadWorker
import com.sunshine.app.offline.RegionDownloader
import com.sunshine.app.sunshine.OverlayRepository
import com.sunshine.app.sunshine.SunshineRepository
import com.sunshine.app.sunshine.debugLog
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.offline.OfflineManager

class SunshineApp : Application() {
    /** Work that outlives every screen, such as MapLibre's paced region requests. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** One rate limiter per tile server for region downloads (design D4 of add-offline-regions). */
    private val rateLimiters = RateLimiters()

    val offlineDatabase: OfflineDatabase by lazy { OfflineDatabase.create(this) }

    /** The persistent DEM tiles, browsed and of regions (design D5 of add-offline-regions). */
    private val demTileStore: DemTileStore by lazy { DemTileStore(offlineDatabase.dao(), File(filesDir, "dem")) }

    /** Browsed map tiles' limit, plus the regions' size (design D2 of add-offline-regions). */
    private val ambientLimit: AmbientLimit by lazy {
        AmbientLimit { bytes ->
            OfflineManager.getInstance(this).setMaximumAmbientCacheSize(
                bytes,
                object : OfflineManager.FileSourceCallback {
                    override fun onSuccess() = debugLog("Map tile limit set to ${bytes / MEBIBYTE} MiB")

                    override fun onError(message: String) = debugLog("Map tile limit not set: $message")
                },
            )
        }
    }

    /** Shared by all screens and features, so decoded and stored tiles are shared too (design D6). */
    private val demTiles: DemTiles by lazy { DemTiles(demHttpClient(userAgent()), demTileStore, rateLimiters) }

    private val tileCache: TileCache by lazy {
        TileCache(
            fetch = { key -> demTiles.fetch(key) },
            decode = { bytes -> decodeArgb(bytes, MapterhornTiles.TILE_SIZE) },
        )
    }

    /** Downloads the queued regions, one at a time (design D7 of add-offline-regions). */
    val regionDownloader: RegionDownloader by lazy {
        RegionDownloader(
            dao = offlineDatabase.dao(),
            map = MapLibreRegionPart(this, offlineDatabase.dao(), ::debugLog),
            fetchDem = demTiles::fetch,
            onRegionBytes = ambientLimit::onRegionBytes,
            onProgress = { _, percent -> DownloadNotification.update(this, percent) },
        )
    }

    val elevationRepository: ElevationRepository by lazy { ElevationRepository(tileCache) }

    val sunshineRepository: SunshineRepository by lazy { SunshineRepository(tile = tileCache::tile, log = ::debugLog) }

    val overlayRepository: OverlayRepository by lazy {
        OverlayRepository(tile = tileCache::tile, loads = demTiles::loads, log = ::debugLog)
    }

    override fun onCreate() {
        super.onCreate()
        // Before MapLibre starts, so that every map request goes through it (design D3 of add-offline-regions).
        MapLibre.setModuleProvider(SunshineModuleProvider(rateLimiters, appScope))
        MapLibre.getInstance(this)
        HttpRequestUtil.setOkHttpClient(mapHttpClient())
        // Before any map loads, or MapLibre starts with its default of 50 MB and trims the browsed
        // tiles to it (design D2). A single sum over the few region rows.
        ambientLimit.onRegionBytes(runBlocking(Dispatchers.IO) { offlineDatabase.dao().regionMapBytes() }, now = true)
        appScope.launch {
            // The DEM tiles' HTTP cache before add-offline-regions; the store replaces it (design D6).
            File(cacheDir, "dem-tiles").deleteRecursively()
            demTileStore.reconcile()
            // Regions left incomplete by a failed run go on (design D7).
            if (offlineDatabase.dao().nextRegionToDownload() != null) RegionDownloadWorker.enqueue(this@SunshineApp)
        }
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
        const val MEBIBYTE = 1024L * 1024
    }
}
