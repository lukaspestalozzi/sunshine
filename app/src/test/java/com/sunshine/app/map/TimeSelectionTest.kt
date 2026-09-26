package com.sunshine.app.map

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class TimeSelectionTest {
    @ParameterizedTest(name = "{0}")
    @CsvSource("2025-12-21, 288", "2025-03-30, 276", "2025-10-26, 300")
    fun `slider has one position per 5 minutes of the real day`(
        date: LocalDate,
        positions: Int,
    ) {
        assertEquals(positions, sliderPositions(date, ZURICH))
    }

    @Test
    fun `slider covers 00_00 to 23_55 on a normal day`() {
        val date = LocalDate.of(2025, 12, 21)

        assertEquals(LocalDateTime.of(2025, 12, 21, 0, 0), sliderTime(date, ZURICH, 0f).toLocalDateTime())
        assertEquals(LocalDateTime.of(2025, 12, 21, 23, 55), sliderTime(date, ZURICH, 287 * 5f).toLocalDateTime())
    }

    @ParameterizedTest(name = "{0} min -> {1}")
    @CsvSource("7.4, 5", "7.6, 10", "-3, 0", "1439.9, 1435", "5000, 1435")
    fun `slider snaps to the 5-minute grid within the day`(
        minutes: Float,
        snappedMinutes: Long,
    ) {
        val date = LocalDate.of(2025, 12, 21)

        assertEquals(date.atStartOfDay(ZURICH).plusMinutes(snappedMinutes), sliderTime(date, ZURICH, minutes))
    }

    @Test
    fun `on the spring-forward day the position after 01_55 is 03_00 summer time`() {
        val date = LocalDate.of(2025, 3, 30)

        assertEquals(OffsetDateTime.parse("2025-03-30T01:55+01:00"), sliderTime(date, ZURICH, 115f).toOffsetDateTime())
        assertEquals(OffsetDateTime.parse("2025-03-30T03:00+02:00"), sliderTime(date, ZURICH, 120f).toOffsetDateTime())
    }

    @Test
    fun `on the fall-back day 02_00 to 02_55 appear twice, summer time first`() {
        val date = LocalDate.of(2025, 10, 26)

        assertEquals(OffsetDateTime.parse("2025-10-26T02:00+02:00"), sliderTime(date, ZURICH, 120f).toOffsetDateTime())
        assertEquals(OffsetDateTime.parse("2025-10-26T02:55+02:00"), sliderTime(date, ZURICH, 175f).toOffsetDateTime())
        assertEquals(OffsetDateTime.parse("2025-10-26T02:00+01:00"), sliderTime(date, ZURICH, 180f).toOffsetDateTime())
        assertEquals(OffsetDateTime.parse("2025-10-26T02:55+01:00"), sliderTime(date, ZURICH, 235f).toOffsetDateTime())
    }

    @Test
    fun `slider value is the minutes since the start of the selected day`() {
        assertEquals(180f, sliderMinutes(ZonedDateTime.parse("2025-10-26T02:00+01:00[Europe/Zurich]")))
        assertEquals(589.5f, sliderMinutes(ZonedDateTime.parse("2025-12-21T09:49:30+01:00[Europe/Zurich]")))
    }

    @Test
    fun `changing the date keeps the wall-clock time`() {
        val time = ZonedDateTime.of(2025, 12, 21, 14, 35, 0, 0, ZURICH)

        assertEquals(ZonedDateTime.of(2025, 6, 21, 14, 35, 0, 0, ZURICH), time.withDate(LocalDate.of(2025, 6, 21)))
    }

    @Test
    fun `a time in the spring-forward gap moves forward by the gap`() {
        val time = ZonedDateTime.of(2025, 3, 29, 2, 30, 0, 0, ZURICH)

        assertEquals(OffsetDateTime.parse("2025-03-30T03:30+02:00"), time.withDate(LocalDate.of(2025, 3, 30)).toOffsetDateTime())
    }

    @ParameterizedTest
    @ValueSource(strings = ["America/Los_Angeles", "Pacific/Auckland"])
    fun `date picker milliseconds round-trip regardless of the device time zone`(zone: String) {
        val original = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
        try {
            val date = LocalDate.of(2025, 12, 21)

            assertEquals(date, datePickerMillisToDate(date.toDatePickerMillis()))
            assertEquals(1_766_275_200_000L, date.toDatePickerMillis())
        } finally {
            TimeZone.setDefault(original)
        }
    }

    private companion object {
        val ZURICH: ZoneId = ZoneId.of("Europe/Zurich")
    }
}
