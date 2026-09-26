package com.sunshine.app.map

import com.sunshine.core.WholeDay
import java.time.Duration
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SunFormatTest {
    @ParameterizedTest(name = "{1}")
    @CsvSource(
        delimiter = '|',
        value = [
            "2025-12-21T12:00+01:00[Europe/Zurich] | 2025-12-21 12:00 UTC+1",
            "2025-06-21T15:00+02:00[Europe/Zurich] | 2025-06-21 15:00 UTC+2",
            "2025-06-21T06:00+05:30[Asia/Kolkata]  | 2025-06-21 06:00 UTC+5:30",
            "2025-06-21T06:00Z[Etc/UTC]            | 2025-06-21 06:00 UTC",
            "2025-06-21T06:00-02:30[America/St_Johns] | 2025-06-21 06:00 UTC-2:30",
        ],
    )
    fun `formats the selected time with its UTC offset`(
        time: String,
        expected: String,
    ) {
        assertEquals(expected, formatSelectedTime(ZonedDateTime.parse(time)))
    }

    @Test
    fun `selected time does not depend on the device locale`() {
        val originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("de-CH"))
        try {
            assertEquals("2025-12-21 12:00 UTC+1", formatSelectedTime(ZonedDateTime.parse("2025-12-21T12:00+01:00[Europe/Zurich]")))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        delimiter = '|',
        value = ["173.49 | 173° S", "22.4 | 22° N", "22.5 | 23° NE", "359.6 | 0° N", "225.407 | 225° SW", "337.4 | 337° NW"],
    )
    fun `formats the azimuth in whole degrees with the compass direction`(
        azimuth: Double,
        expected: String,
    ) {
        assertEquals(expected, formatAzimuth(azimuth))
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = ["19.66 | 19.7°", "-0.7246 | -0.7°", "-0.04 | 0.0°", "0.05 | 0.1°"])
    fun `formats the elevation with one decimal`(
        elevation: Double,
        expected: String,
    ) {
        assertEquals(expected, formatElevation(elevation))
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        delimiter = '|',
        value = [
            "2025-12-21T07:11:45+01:00[Europe/Zurich] | 07:12",
            "2025-12-21T17:22:29+01:00[Europe/Zurich] | 17:22",
            "2025-12-21T17:22:30+01:00[Europe/Zurich] | 17:23",
        ],
    )
    fun `rounds event times to the nearest minute`(
        event: String,
        expected: String,
    ) {
        assertEquals(expected, formatEventTime(ZonedDateTime.parse(event), selected = WINTER_NOON))
    }

    @Test
    fun `appends the offset of an event whose offset differs from the selected time`() {
        val selected = ZonedDateTime.parse("2025-03-30T01:00+01:00[Europe/Zurich]")
        val sunrise = ZonedDateTime.parse("2025-03-30T07:11:45+02:00[Europe/Zurich]")

        assertEquals("07:12 UTC+2", formatEventTime(sunrise, selected))
    }

    @Test
    fun `shows an absent event as none this day`() {
        assertEquals("none this day", formatEventTime(null, WINTER_NOON))
    }

    @ParameterizedTest(name = "{1}")
    @CsvSource(delimiter = '|', value = ["PT8H33M17S | 8 h 33 min", "PT15H50M31S | 15 h 51 min", "PT0S | 0 h 0 min", "PT24H | 24 h 0 min"])
    fun `formats the day length in hours and minutes`(
        dayLength: Duration,
        expected: String,
    ) {
        assertEquals(expected, formatDayLength(dayLength))
    }

    @Test
    fun `describes a whole day on one side of the horizon`() {
        assertEquals("Sun above the horizon all day", formatWholeDay(WholeDay.ABOVE_HORIZON))
        assertEquals("Sun below the horizon all day", formatWholeDay(WholeDay.BELOW_HORIZON))
        assertNull(formatWholeDay(null))
    }

    private companion object {
        val WINTER_NOON: ZonedDateTime = ZonedDateTime.parse("2025-12-21T12:00+01:00[Europe/Zurich]")
    }
}
