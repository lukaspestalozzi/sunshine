package com.sunshine.app.suncalc

import com.sunshine.app.domain.model.GeoPoint
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SimpleSunCalculatorTest {
    private lateinit var calculator: SimpleSunCalculator

    @Before
    fun setup() {
        calculator = SimpleSunCalculator()
    }

    // ---- Existing directional tests ----

    @Test
    fun `sun is below horizon at midnight in Swiss Alps`() =
        runBlocking {
            val location = bern
            val midnight = LocalDateTime.of(2024, Month.JUNE, 21, 0, 0)

            val position = calculator.calculateSunPosition(location, midnight)

            assertTrue("Sun should be below horizon at midnight", position.elevation < 0)
        }

    @Test
    fun `sun is above horizon at noon in Swiss Alps summer`() =
        runBlocking {
            val location = bern
            val noon = LocalDateTime.of(2024, Month.JUNE, 21, 12, 0)

            val position = calculator.calculateSunPosition(location, noon)

            assertTrue("Sun should be above horizon at noon", position.elevation > 0)
        }

    @Test
    fun `sun elevation is higher in summer than winter at noon`() =
        runBlocking {
            val location = bern
            val summerNoon = LocalDateTime.of(2024, Month.JUNE, 21, 12, 0)
            val winterNoon = LocalDateTime.of(2024, Month.DECEMBER, 21, 12, 0)

            val summerPosition = calculator.calculateSunPosition(location, summerNoon)
            val winterPosition = calculator.calculateSunPosition(location, winterNoon)

            assertTrue(
                "Sun should be higher in summer (${summerPosition.elevation}) " +
                    "than winter (${winterPosition.elevation})",
                summerPosition.elevation > winterPosition.elevation,
            )
        }

    @Test
    fun `azimuth is roughly east in morning and west in evening`() =
        runBlocking {
            val location = bern
            val morning = LocalDateTime.of(2024, Month.JUNE, 21, 8, 0)
            val evening = LocalDateTime.of(2024, Month.JUNE, 21, 18, 0)

            val morningPosition = calculator.calculateSunPosition(location, morning)
            val eveningPosition = calculator.calculateSunPosition(location, evening)

            assertTrue(
                "Morning sun should be in eastern half, was ${morningPosition.azimuth}",
                morningPosition.azimuth in 45.0..180.0,
            )

            assertTrue(
                "Evening sun should be in western half, was ${eveningPosition.azimuth}",
                eveningPosition.azimuth in 180.0..315.0,
            )
        }

    @Test
    fun `equator has sun nearly overhead at equinox noon`() =
        runBlocking {
            val equator = GeoPoint(latitude = 0.0, longitude = 0.0)
            val equinoxNoon = LocalDateTime.of(2024, Month.MARCH, 20, 12, 0)

            val position = calculator.calculateSunPosition(equator, equinoxNoon)

            assertTrue(
                "Sun should be high at equator during equinox noon, was ${position.elevation}",
                position.elevation > 70,
            )
        }

    // ---- Numerical accuracy tests (NOAA reference values) ----

    @Test
    fun `summer solstice max elevation at Bern matches expected`() =
        runBlocking {
            // Max elevation at summer solstice ≈ 90 - |lat - declination| = 90 - |46.95 - 23.44| ≈ 66.5°
            // Solar noon at Bern (lon 7.45°E) ≈ 11:30 UTC
            val location = GeoPoint(latitude = 46.95, longitude = 7.45)
            val solarNoon = LocalDateTime.of(2024, Month.JUNE, 21, 11, 30)

            val position = calculator.calculateSunPosition(location, solarNoon)

            assertEquals(
                "Summer solstice max elevation at Bern should be ~66.5°",
                66.5,
                position.elevation,
                3.0,
            )
        }

    @Test
    fun `winter solstice max elevation at Bern matches expected`() =
        runBlocking {
            // Max elevation at winter solstice ≈ 90 - |lat + declination| = 90 - |46.95 + 23.44| ≈ 19.6°
            val location = GeoPoint(latitude = 46.95, longitude = 7.45)
            val solarNoon = LocalDateTime.of(2024, Month.DECEMBER, 21, 11, 45)

            val position = calculator.calculateSunPosition(location, solarNoon)

            assertEquals(
                "Winter solstice max elevation at Bern should be ~19.6°",
                19.6,
                position.elevation,
                3.0,
            )
        }

    @Test
    fun `azimuth at solar noon is approximately south`() =
        runBlocking {
            // At solar noon in northern hemisphere, sun should be due south (180°)
            val location = GeoPoint(latitude = 46.95, longitude = 7.45)
            val solarNoon = LocalDateTime.of(2024, Month.JUNE, 21, 11, 30)

            val position = calculator.calculateSunPosition(location, solarNoon)

            assertEquals(
                "Azimuth at solar noon should be ~180° (due south), was ${position.azimuth}",
                180.0,
                position.azimuth,
                10.0,
            )
        }

    @Test
    fun `elevation varies continuously through the day`() =
        runBlocking {
            // Sun should rise, reach peak, then descend - test monotonicity
            val location = bern
            val date = LocalDate.of(2024, Month.JUNE, 21)

            // Morning: elevation should increase from 6:00 to 12:00 UTC
            val earlyMorning =
                calculator.calculateSunPosition(
                    location,
                    LocalDateTime.of(date, java.time.LocalTime.of(6, 0)),
                )
            val midMorning =
                calculator.calculateSunPosition(
                    location,
                    LocalDateTime.of(date, java.time.LocalTime.of(9, 0)),
                )
            val noon =
                calculator.calculateSunPosition(
                    location,
                    LocalDateTime.of(date, java.time.LocalTime.of(12, 0)),
                )

            assertTrue(
                "Elevation should increase from morning to noon",
                earlyMorning.elevation < midMorning.elevation,
            )
            assertTrue(
                "Elevation should increase from mid-morning to noon",
                midMorning.elevation < noon.elevation,
            )
        }

    // ---- Polar region tests ----

    @Test
    fun `midnight sun at high latitude during summer`() =
        runBlocking {
            // At 70°N on June 21, the sun should be above horizon even at midnight
            val arcticLocation = GeoPoint(latitude = 70.0, longitude = 25.0)
            val midnight = LocalDateTime.of(2024, Month.JUNE, 21, 0, 0)

            val position = calculator.calculateSunPosition(arcticLocation, midnight)

            assertTrue(
                "Sun should be above horizon at Arctic midnight in summer, was ${position.elevation}",
                position.elevation > 0,
            )
        }

    @Test
    fun `polar night at high latitude during winter`() =
        runBlocking {
            // At 70°N on December 21, the sun should be below horizon even at noon
            val arcticLocation = GeoPoint(latitude = 70.0, longitude = 25.0)
            val noon = LocalDateTime.of(2024, Month.DECEMBER, 21, 12, 0)

            val position = calculator.calculateSunPosition(arcticLocation, noon)

            assertTrue(
                "Sun should be below horizon at Arctic noon in winter, was ${position.elevation}",
                position.elevation < 0,
            )
        }

    // ---- Sunrise/sunset tests ----

    @Test
    fun `sunrise time is reasonable for Bern in summer`() =
        runBlocking {
            val location = bern
            val date = LocalDate.of(2024, Month.JUNE, 21)

            val sunrise = calculator.calculateSunrise(location, date)

            // Bern sunrise on June 21 is approximately 05:30 local (03:30 UTC)
            // The calculator works in UTC, so expect roughly 3:00-5:00 UTC
            assertNotNull("Sunrise should exist in summer", sunrise)
            assertTrue(
                "Sunrise hour should be between 3 and 6 UTC, was $sunrise",
                sunrise!!.hour in 3..5,
            )
        }

    @Test
    fun `sunset time is reasonable for Bern in summer`() =
        runBlocking {
            val location = bern
            val date = LocalDate.of(2024, Month.JUNE, 21)

            val sunset = calculator.calculateSunset(location, date)

            // Bern sunset on June 21 is approximately 21:30 local (19:30 UTC)
            // Expect roughly 19:00-21:00 UTC
            assertNotNull("Sunset should exist in summer", sunset)
            assertTrue(
                "Sunset hour should be between 18 and 22 UTC, was $sunset",
                sunset!!.hour in 18..21,
            )
        }

    @Test
    fun `sunrise is before sunset on a normal day`() =
        runBlocking {
            val location = bern
            val date = LocalDate.of(2024, Month.MARCH, 20)

            val sunrise = calculator.calculateSunrise(location, date)
            val sunset = calculator.calculateSunset(location, date)

            assertNotNull("Sunrise should exist at equinox", sunrise)
            assertNotNull("Sunset should exist at equinox", sunset)
            assertTrue(
                "Sunrise ($sunrise) should be before sunset ($sunset)",
                sunrise!! < sunset!!,
            )
        }

    @Test
    fun `day is longer in summer than winter`() =
        runBlocking {
            val location = bern

            val summerSunrise = calculator.calculateSunrise(location, summerSolstice)
            val summerSunset = calculator.calculateSunset(location, summerSolstice)
            val winterSunrise = calculator.calculateSunrise(location, winterSolstice)
            val winterSunset = calculator.calculateSunset(location, winterSolstice)

            val summerDay = dayLengthMinutes(summerSunrise!!, summerSunset!!)
            val winterDay = dayLengthMinutes(winterSunrise!!, winterSunset!!)

            assertTrue(
                "Summer day ($summerDay min) should be longer than winter ($winterDay min)",
                summerDay > winterDay,
            )
        }

    // ---- Southern hemisphere test ----

    @Test
    fun `southern hemisphere has opposite seasonal pattern`() =
        runBlocking {
            // Cape Town, South Africa (-33.9°)
            val capeTown = GeoPoint(latitude = -33.9, longitude = 18.4)
            val juneSolarNoon = LocalDateTime.of(2024, Month.JUNE, 21, 11, 0)
            val decSolarNoon = LocalDateTime.of(2024, Month.DECEMBER, 21, 11, 0)

            val junePosition = calculator.calculateSunPosition(capeTown, juneSolarNoon)
            val decPosition = calculator.calculateSunPosition(capeTown, decSolarNoon)

            assertTrue(
                "Sun should be higher in December (summer) than June (winter) in southern hemisphere",
                decPosition.elevation > junePosition.elevation,
            )
        }

    private fun dayLengthMinutes(
        sunrise: java.time.LocalTime,
        sunset: java.time.LocalTime,
    ): Int = (sunset.toSecondOfDay() - sunrise.toSecondOfDay()) / 60

    companion object {
        private val bern = GeoPoint(latitude = 46.8182, longitude = 8.2275)
        private val summerSolstice = LocalDate.of(2024, Month.JUNE, 21)
        private val winterSolstice = LocalDate.of(2024, Month.DECEMBER, 21)
    }
}
