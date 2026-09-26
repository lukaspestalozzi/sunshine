package com.sunshine.app

import android.app.Application
import com.sunshine.app.network.UserAgentInterceptor
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil

class SunshineApp : Application() {
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
            .addInterceptor(UserAgentInterceptor(BuildConfig.VERSION_NAME, BuildConfig.APPLICATION_ID))
            .build()

    private companion object {
        const val MAX_TILE_REQUESTS_PER_HOST = 20
    }
}
