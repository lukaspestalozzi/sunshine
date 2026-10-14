package com.sunshine.app.search

import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Requests to komoot's public Photon geocoder (design D16 of add-place-search): identified by
 * [userAgent], at most 10 s each, never retried, cancelled with the calling coroutine.
 */
class PhotonClient(
    userAgent: Interceptor,
    private val log: (String) -> Unit = {},
) {
    private val client =
        OkHttpClient
            .Builder()
            .addInterceptor(userAgent)
            .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()

    /** The body of Photon's HTTP 200 answer to [key], or `null` when the request failed. */
    suspend fun fetch(key: SearchKey): String? {
        val url = photonUrl(key)
        log("Search request $url")
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(Request.Builder().url(url).build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(
                        call: Call,
                        e: IOException,
                    ) {
                        continuation.resume(null)
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        val body =
                            try {
                                response.use { if (it.code == OK) it.body?.string() else null }
                            } catch (_: IOException) {
                                null // the body failed to arrive
                            }
                        continuation.resume(body)
                    }
                },
            )
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 10L
        const val OK = 200
    }
}

/** The request for [key] (place-search spec, "Online search requests"). */
fun photonUrl(key: SearchKey): HttpUrl =
    PHOTON_API
        .toHttpUrl()
        .newBuilder()
        .addQueryParameter("q", key.text)
        .addQueryParameter("lat", degrees(key.latitudeTenths))
        .addQueryParameter("lon", degrees(key.longitudeTenths))
        .addQueryParameter("limit", RESULTS.toString())
        .build()

private fun degrees(tenths: Int): String = String.format(Locale.ROOT, "%.1f", tenths / TENTHS_PER_DEGREE)

private const val PHOTON_API = "https://photon.komoot.io/api/"
private const val RESULTS = 10
private const val TENTHS_PER_DEGREE = 10.0
