package com.sunshine.app.util

import com.sunshine.app.domain.repository.OfflineModeException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockk
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorMessageMapperTest {
    @Test
    fun `maps UnknownHostException to no internet message`() {
        val exception = UnknownHostException("Unable to resolve host")

        val message = ErrorMessageMapper.toUserMessage(exception)

        assertEquals("No internet connection. Using cached data if available.", message)
    }

    @Test
    fun `maps SocketTimeoutException to slow server message`() {
        val exception = SocketTimeoutException("Read timed out")

        val message = ErrorMessageMapper.toUserMessage(exception)

        assertEquals("Server is slow to respond. Please try again.", message)
    }

    @Test
    fun `maps OfflineModeException to offline message`() {
        val exception = OfflineModeException("Not cached")

        val message = ErrorMessageMapper.toUserMessage(exception)

        assertEquals("Offline mode is enabled. Data not available for this location.", message)
    }

    @Test
    fun `maps ServerResponseException to server unavailable message`() {
        val response = mockk<HttpResponse>(relaxed = true)
        every { response.status } returns HttpStatusCode.InternalServerError
        val exception = ServerResponseException(response, "Server error")

        val message = ErrorMessageMapper.toUserMessage(exception)

        assertEquals("Server is temporarily unavailable. Please try again later.", message)
    }

    @Test
    fun `maps ClientRequestException with TooManyRequests to rate limit message`() {
        val response = mockk<HttpResponse>(relaxed = true)
        every { response.status } returns HttpStatusCode.TooManyRequests
        val exception = ClientRequestException(response, "Rate limited")

        val message = ErrorMessageMapper.toUserMessage(exception)

        assertEquals("Too many requests. Please wait a moment.", message)
    }

    @Test
    fun `maps ClientRequestException with NotFound to location unavailable message`() {
        val response = mockk<HttpResponse>(relaxed = true)
        every { response.status } returns HttpStatusCode.NotFound
        val exception = ClientRequestException(response, "Not found")

        val message = ErrorMessageMapper.toUserMessage(exception)

        assertEquals("Elevation data not available for this location.", message)
    }

    @Test
    fun `maps ClientRequestException with other status to generic client error`() {
        val response = mockk<HttpResponse>(relaxed = true)
        every { response.status } returns HttpStatusCode.BadRequest
        val exception = ClientRequestException(response, "Bad request")

        val message = ErrorMessageMapper.toUserMessage(exception)

        assertEquals("Request failed. Please try again.", message)
    }

    @Test
    fun `maps unknown exception with message to that message`() {
        val exception = RuntimeException("Something specific broke")

        val message = ErrorMessageMapper.toUserMessage(exception)

        assertEquals("Something specific broke", message)
    }

    @Test
    fun `maps unknown exception without message to generic fallback`() {
        val exception = RuntimeException()

        val message = ErrorMessageMapper.toUserMessage(exception)

        assertEquals("Something went wrong. Please try again.", message)
    }

    @Test
    fun `maps IOException to its message as fallback`() {
        val exception = java.io.IOException("Connection reset")

        val message = ErrorMessageMapper.toUserMessage(exception)

        assertTrue(
            "IOException with message should use the message, got: $message",
            message.contains("Connection reset"),
        )
    }
}
