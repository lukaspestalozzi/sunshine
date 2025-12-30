package com.sunshine.app.util

import com.sunshine.app.data.repository.ElevationRepositoryImpl
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.HttpStatusCode
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Maps technical exceptions to user-friendly error messages.
 */
object ErrorMessageMapper {
    /**
     * Convert a throwable to a user-friendly message.
     */
    fun toUserMessage(throwable: Throwable): String =
        when (throwable) {
            is UnknownHostException ->
                "No internet connection. Using cached data if available."

            is SocketTimeoutException ->
                "Server is slow to respond. Please try again."

            is ElevationRepositoryImpl.OfflineModeException ->
                "Offline mode is enabled. Data not available for this location."

            is ClientRequestException -> handleClientException(throwable)

            is ServerResponseException ->
                "Server is temporarily unavailable. Please try again later."

            else -> throwable.message ?: "Something went wrong. Please try again."
        }

    private fun handleClientException(exception: ClientRequestException): String =
        when (exception.response.status) {
            HttpStatusCode.TooManyRequests -> "Too many requests. Please wait a moment."
            HttpStatusCode.NotFound -> "Elevation data not available for this location."
            else -> "Request failed. Please try again."
        }
}
