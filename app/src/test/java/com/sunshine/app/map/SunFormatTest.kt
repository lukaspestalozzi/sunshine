package com.sunshine.app.map

import com.sunshine.core.SunPeriod
import com.sunshine.core.SunPeriods
import com.sunshine.core.Sunshine
import com.sunshine.core.WholeDay
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
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

    // The panel prefixes the label "Altitude" (strings.xml).
    @ParameterizedTest(name = "{0} m")
    @CsvSource("1634.43, 1634 m", "567.5, 568 m", "-0.4, 0 m", "-12.6, -13 m")
    fun `formats a known altitude in whole metres`(
        metres: Double,
        expected: String,
    ) {
        assertEquals(expected, formatAltitude(ElevationState.Known(metres)))
    }

    @Test
    fun `altitude does not depend on the device locale`() {
        val originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("de-CH"))
        try {
            assertEquals("2061 m", formatAltitude(ElevationState.Known(2061.31)))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun `shows a loading or unknown altitude explicitly`() {
        assertEquals("…", formatAltitude(ElevationState.Loading))
        assertEquals("unknown", formatAltitude(ElevationState.Unknown))
    }

    // point-sunshine spec, "Sunshine in the information panel".
    @Test
    fun `lists every sun period in chronological order`() {
        val periods =
            listOf(
                SunPeriod(zurich("2025-12-21T10:08:50"), zurich("2025-12-21T14:51:10")),
                SunPeriod(zurich("2025-12-21T15:11:20"), zurich("2025-12-21T15:52:10")),
            )

        assertEquals(
            "10:09–14:51, 15:11–15:52",
            formatSunshine(ready(SunPeriods.Known(periods)), zurich("2025-12-21T12:00")),
        )
    }

    @Test
    fun `shows a day without sun, an unknown day and a horizon being computed explicitly`() {
        val selected = zurich("2025-12-21T12:00")

        assertEquals("none this day", formatSunshine(ready(SunPeriods.Known(emptyList())), selected))
        assertEquals("unknown", formatSunshine(ready(SunPeriods.Unknown), selected))
        assertEquals("…", formatSunshine(SunshineUiState.Loading, selected))
    }

    @Test
    fun `appends the offset of a period time that differs from the selected time's`() {
        // 2025-03-30, Europe/Zurich: clocks go from UTC+1 to UTC+2 at 02:00.
        val period = SunPeriod(zurich("2025-03-30T01:00"), zurich("2025-03-30T03:30"))

        assertEquals("01:00 UTC+1–03:30", formatSunshine(ready(SunPeriods.Known(listOf(period))), zurich("2025-03-30T12:00")))
    }

    private fun ready(periods: SunPeriods) = SunshineUiState.Ready(periods, Sunshine.SUN)

    private fun zurich(time: String): ZonedDateTime = LocalDateTime.parse(time).atZone(ZoneId.of("Europe/Zurich"))
}
