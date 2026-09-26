package com.sunshine.app.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Identifies the app to tile servers, as their usage policies require, e.g.
 * `Sunshine/0.1.0 (Android; com.sunshine.app)`. Replaces any User-Agent set by the map library.
 */
class UserAgentInterceptor(
    versionName: String,
    applicationId: String,
) : Interceptor {
    private val userAgent = "Sunshine/$versionName (Android; $applicationId)"

    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(
            chain
                .request()
                .newBuilder()
                .header("User-Agent", userAgent)
                .build(),
        )
}
