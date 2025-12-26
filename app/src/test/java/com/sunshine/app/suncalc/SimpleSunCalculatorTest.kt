package com.sunshine.app.suncalc

import com.sunshine.app.domain.model.GeoPoint
import java.time.LocalDateTime
import java.time.Month
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SimpleSunCalculatorTest {

    private lateinit var calculator: SimpleSunCalculator

    @Before
    fun setup() {
        calculator = SimpleSunCalculator()
    }

    @Test
    fun `sun is below horizon at midnight in Swiss Alps`() = runBlocking {
        val location = GeoPoint(latitude = 46.8182, longitude = 8.2275) // Bern, Switzerland
        val midnight = LocalDateTime.of(2024, Month.JUNE, 21, 0, 0)

        val position = calculator.calculateSunPosition(location, midnight)

        assertTrue("Sun should be below horizon at midnight", position.elevation < 0)
    }

    @Test
    fun `sun is above horizon at noon in Swiss Alps summer`() = runBlocking {
        val location = GeoPoint(latitude = 46.8182, longitude = 8.2275) // Bern, Switzerland
        val noon = LocalDateTime.of(2024, Month.JUNE, 21, 12, 0)

        val position = calculator.calculateSunPosition(location, noon)

        assertTrue("Sun should be above horizon at noon", position.elevation > 0)
    }

    @Test
    fun `sun elevation is higher in summer than winter at noon`() = runBlocking {
        val location = GeoPoint(latitude = 46.8182, longitude = 8.2275)
        val summerNoon = LocalDateTime.of(2024, Month.JUNE, 21, 12, 0)
        val winterNoon = LocalDateTime.of(2024, Month.DECEMBER, 21, 12, 0)

        val summerPosition = calculator.calculateSunPosition(location, summerNoon)
        val winterPosition = calculator.calculateSunPosition(location, winterNoon)

        assertTrue(
            "Sun should be higher in summer (${summerPosition.elevation}) than winter (${winterPosition.elevation})",
            summerPosition.elevation > winterPosition.elevation,
        )
    }

    @Test
    fun `azimuth is roughly east in morning and west in evening`() = runBlocking {
        val location = GeoPoint(latitude = 46.8182, longitude = 8.2275)
        val morning = LocalDateTime.of(2024, Month.JUNE, 21, 8, 0)
        val evening = LocalDateTime.of(2024, Month.JUNE, 21, 18, 0)

        val morningPosition = calculator.calculateSunPosition(location, morning)
        val eveningPosition = calculator.calculateSunPosition(location, evening)

        // Morning sun should be in eastern half (0-180° from North, roughly 45-135°)
        assertTrue(
            "Morning sun should be in eastern half, was ${morningPosition.azimuth}",
            morningPosition.azimuth in 45.0..180.0,
        )

        // Evening sun should be in western half (180-360° from North, roughly 225-315°)
        assertTrue(
            "Evening sun should be in western half, was ${eveningPosition.azimuth}",
            eveningPosition.azimuth in 180.0..315.0,
        )
    }

    @Test
    fun `equator has sun nearly overhead at equinox noon`() = runBlocking {
        val equator = GeoPoint(latitude = 0.0, longitude = 0.0)
        val equinoxNoon = LocalDateTime.of(2024, Month.MARCH, 20, 12, 0)

        val position = calculator.calculateSunPosition(equator, equinoxNoon)

        // Sun should be very high (close to 90°) at equator during equinox
        assertTrue(
            "Sun should be high at equator during equinox noon, was ${position.elevation}",
            position.elevation > 70,
        )
    }
}
