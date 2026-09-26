package com.sunshine.core

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

// Expected values: investigations/sun-position-references.md.
class SunDayTest {
    @ParameterizedTest(name = "{0} {2} {3}")
    @CsvSource(
        "Interlaken, Europe/Zurich, 2025-06-21, civilDawn, 04:55",
        "Interlaken, Europe/Zurich, 2025-06-21, sunrise,   05:34",
        "Interlaken, Europe/Zurich, 2025-06-21, sunset,    21:25",
        "Interlaken, Europe/Zurich, 2025-06-21, civilDusk, 22:06",
        "Interlaken, Europe/Zurich, 2025-12-21, civilDawn, 07:35",
        "Interlaken, Europe/Zurich, 2025-12-21, sunrise,   08:10",
        "Interlaken, Europe/Zurich, 2025-12-21, sunset,    16:43",
        "Interlaken, Europe/Zurich, 2025-12-21, civilDusk, 17:19",
        "Interlaken, Europe/Zurich, 2025-03-20, sunrise,   06:31",
        "Interlaken, Europe/Zurich, 2025-03-20, sunset,    18:41",
        "Interlaken, Europe/Zurich, 2025-09-22, sunrise,   07:15",
        "Interlaken, Europe/Zurich, 2025-09-22, sunset,    19:25",
        "Interlaken, Europe/Zurich, 2025-03-30, sunrise,   07:12",
        "Interlaken, Europe/Zurich, 2025-03-30, sunset,    19:55",
        "Interlaken, Europe/Zurich, 2025-10-26, sunrise,   07:02",
        "Interlaken, Europe/Zurich, 2025-10-26, sunset,    17:22",
        "Tokyo,      Asia/Tokyo,    2025-06-21, sunrise,   04:26",
        "Tokyo,      Asia/Tokyo,    2025-06-21, sunset,    19:00",
    )
    fun `sun events match the reference times on the selected day`(
        place: String,
        zone: String,
        date: LocalDate,
        event: String,
        expected: LocalTime,
    ) {
        val zoneId = ZoneId.of(zone)

        val actual = sunDay(PLACES.getValue(place), date, zoneId).event(event)

        assertNotNull(actual)
        assertEquals(date, actual.toLocalDate())
        val error = Duration.between(ZonedDateTime.of(date, expected, zoneId), actual).abs()
        assertTrue(error <= EVENT_TOLERANCE, "$event at $actual is $error away from $expected")
    }

    @Test
    fun `polar day has no events and the sun above the horizon all day`() {
        val day = sunDay(LONGYEARBYEN, LocalDate.of(2025, 6, 21), OSLO)

        assertEquals(listOf(null, null, null, null), day.events())
        assertEquals(WholeDay.ABOVE_HORIZON, day.wholeDay)
    }

    @Test
    fun `polar night has no events and the sun below the horizon all day`() {
        val day = sunDay(LONGYEARBYEN, LocalDate.of(2025, 12, 21), OSLO)

        assertEquals(listOf(null, null, null, null), day.events())
        assertEquals(WholeDay.BELOW_HORIZON, day.wholeDay)
    }

    @Test
    fun `a day can have a sunrise without a sunset`() {
        val day = sunDay(TROMSO, LocalDate.of(2025, 5, 16), OSLO)

        assertNotNull(day.sunrise)
        assertNull(day.sunset)
        assertNull(day.wholeDay)
    }

    @Test
    fun `a day can have its sunset before its sunrise`() {
        val day = sunDay(TROMSO, LocalDate.of(2025, 5, 17), OSLO)

        val sunrise = day.sunrise
        val sunset = day.sunset
        assertNotNull(sunrise)
        assertNotNull(sunset)
        assertTrue(sunset.isBefore(sunrise), "sunset $sunset should be before sunrise $sunrise")
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource("2025-06-21, 951", "2025-12-21, 513")
    fun `day length in Interlaken matches the reference`(
        date: LocalDate,
        expectedMinutes: Long,
    ) {
        val day = sunDay(PLACES.getValue("Interlaken"), date, ZoneId.of("Europe/Zurich"))

        val error = (day.dayLength - Duration.ofMinutes(expectedMinutes)).abs()
        assertTrue(error <= EVENT_TOLERANCE, "day length ${day.dayLength} is $error away from $expectedMinutes min")
    }

    @Test
    fun `polar day and night last the whole day and zero`() {
        assertEquals(Duration.ofHours(24), sunDay(LONGYEARBYEN, LocalDate.of(2025, 6, 21), OSLO).dayLength)
        assertEquals(Duration.ZERO, sunDay(LONGYEARBYEN, LocalDate.of(2025, 12, 21), OSLO).dayLength)
    }

    @Test
    fun `day length adds both parts when the sunset comes before the sunrise`() {
        val date = LocalDate.of(2025, 5, 17)
        val day = sunDay(TROMSO, date, OSLO)

        val beforeSunset = Duration.between(date.atStartOfDay(OSLO), day.sunset)
        val afterSunrise = Duration.between(day.sunrise, date.plusDays(1).atStartOfDay(OSLO))
        assertEquals(beforeSunset + afterSunrise, day.dayLength)
    }

    @Test
    fun `day length runs to the end of the day when the sun does not set`() {
        val date = LocalDate.of(2025, 5, 16)
        val day = sunDay(TROMSO, date, OSLO)

        assertEquals(Duration.between(day.sunrise, date.plusDays(1).atStartOfDay(OSLO)), day.dayLength)
    }

    private fun SunDay.events() = listOf(civilDawn, sunrise, sunset, civilDusk)

    private fun SunDay.event(name: String): ZonedDateTime? =
        when (name) {
            "civilDawn" -> civilDawn
            "sunrise" -> sunrise
            "sunset" -> sunset
            "civilDusk" -> civilDusk
            else -> error("Unknown event $name")
        }

    private companion object {
        val PLACES =
            mapOf(
                "Interlaken" to GeoPoint(46.6863, 7.8632),
                "Tokyo" to GeoPoint(35.6762, 139.6503),
            )
        val LONGYEARBYEN = GeoPoint(78.2232, 15.6267)
        val TROMSO = GeoPoint(69.6492, 18.9553)
        val OSLO: ZoneId = ZoneId.of("Europe/Oslo")
        val EVENT_TOLERANCE: Duration = Duration.ofMinutes(2)
    }
}
