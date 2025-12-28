package com.sunshine.app.data.remote.elevation

import com.sunshine.app.domain.model.GeoPoint
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Client for Open-Elevation API.
 * https://open-elevation.com/
 *
 * Includes retry logic with exponential backoff for transient failures.
 */
class ElevationApi(
    private val httpClient: HttpClient,
) {
    /**
     * Get elevations for multiple points in a single request.
     * Open-Elevation API supports batch queries.
     * Includes retry logic for transient failures.
     */
    suspend fun getElevations(points: List<GeoPoint>): Result<List<ElevationResult>> =
        retryWithBackoff {
            val request =
                ElevationRequest(
                    locations =
                        points.map { point ->
                            LocationRequest(
                                latitude = point.latitude,
                                longitude = point.longitude,
                            )
                        },
                )

            val response: ElevationResponse =
                httpClient.post(BASE_URL) {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }.body()

            response.results.map { result ->
                ElevationResult(
                    latitude = result.latitude,
                    longitude = result.longitude,
                    elevation = result.elevation,
                )
            }
        }

    /**
     * Get elevation for a single point.
     */
    suspend fun getElevation(point: GeoPoint): Result<Double> =
        getElevations(listOf(point)).map { results ->
            results.firstOrNull()?.elevation
                ?: error("No elevation data returned")
        }

    /**
     * Retry an operation with exponential backoff.
     * Retries up to MAX_RETRIES times with increasing delays.
     */
    @Suppress("TooGenericExceptionCaught") // Need to catch all exceptions for retry logic
    private suspend fun <T> retryWithBackoff(operation: suspend () -> T): Result<T> {
        var lastException: Exception? = null

        repeat(MAX_RETRIES) { attempt ->
            try {
                return Result.success(operation())
            } catch (e: Exception) {
                lastException = e

                // Don't retry on last attempt
                if (attempt < MAX_RETRIES - 1) {
                    val delayMs = INITIAL_DELAY_MS * (1 shl attempt) // Exponential backoff
                    delay(delayMs)
                }
            }
        }

        return Result.failure(
            lastException ?: IllegalStateException("Retry failed without exception"),
        )
    }

    companion object {
        const val BASE_URL = "https://api.open-elevation.com/api/v1/lookup"

        /** Maximum points per request (API limit) */
        const val MAX_POINTS_PER_REQUEST = 100

        /** Maximum number of retry attempts */
        private const val MAX_RETRIES = 3

        /** Initial delay for exponential backoff (ms) */
        private const val INITIAL_DELAY_MS = 1000L
    }
}

/**
 * Result of elevation lookup.
 */
data class ElevationResult(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double,
)

// API request/response models

@Serializable
private data class ElevationRequest(
    val locations: List<LocationRequest>,
)

@Serializable
private data class LocationRequest(
    val latitude: Double,
    val longitude: Double,
)

@Serializable
private data class ElevationResponse(
    val results: List<ElevationResultDto>,
)

@Serializable
private data class ElevationResultDto(
    val latitude: Double,
    val longitude: Double,
    @SerialName("elevation")
    val elevation: Double,
)
