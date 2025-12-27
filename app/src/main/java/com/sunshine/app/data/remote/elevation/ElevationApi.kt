package com.sunshine.app.data.remote.elevation

import com.sunshine.app.domain.model.GeoPoint
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Client for Open-Elevation API.
 * https://open-elevation.com/
 */
class ElevationApi(
    private val httpClient: HttpClient,
) {
    /**
     * Get elevations for multiple points in a single request.
     * Open-Elevation API supports batch queries.
     */
    suspend fun getElevations(points: List<GeoPoint>): Result<List<ElevationResult>> =
        runCatching {
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

    companion object {
        const val BASE_URL = "https://api.open-elevation.com/api/v1/lookup"

        /** Maximum points per request (API limit) */
        const val MAX_POINTS_PER_REQUEST = 100
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
