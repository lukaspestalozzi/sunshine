package com.sunshine.app.network

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UserAgentInterceptorTest {
    @Test
    fun `replaces the User-Agent of tile requests with the app identification`() {
        var sentRequest: Request? = null
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor(UserAgentInterceptor(versionName = "0.1.0", applicationId = "com.sunshine.app"))
                .addInterceptor { chain ->
                    // Stands in for the network: records the request instead of sending it.
                    sentRequest = chain.request()
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("".toResponseBody())
                        .build()
                }.build()
        val tileRequest =
            Request
                .Builder()
                .url("https://tile.opentopomap.org/10/533/362.png")
                .header("User-Agent", "MapLibre Native/13.6.1")
                .build()

        client.newCall(tileRequest).execute().close()

        assertEquals("Sunshine/0.1.0 (Android; com.sunshine.app)", sentRequest?.header("User-Agent"))
    }
}
