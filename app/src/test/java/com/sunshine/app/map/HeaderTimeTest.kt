package com.sunshine.app.map

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

// The selected time in the panel's header (time-selection spec, "Time zone of the selected time";
// design D2 of polish-ui).
class HeaderTimeTest {
    @ParameterizedTest(name = "{1}")
    @CsvSource(
        delimiter = '|',
        value = [
            "2025-12-21T12:00+01:00[Europe/Zurich] | Sun 21 Dec · 12:00",
            "2025-06-21T15:00+02:00[Europe/Zurich] | Sat 21 Jun · 15:00",
            "2027-12-21T12:00+01:00[Europe/Zurich] | Tue 21 Dec 2027 · 12:00",
            "2025-03-30T03:30+02:00[Europe/Zurich] | Sun 30 Mar · 03:30 UTC+2",
            "2025-06-21T06:00+05:30[Asia/Kolkata]  | Sat 21 Jun · 06:00",
            "2025-01-05T09:05+01:00[Europe/Zurich] | Sun 5 Jan · 09:05",
        ],
    )
    fun `formats the selected time with the year and offset only where needed`(
        time: String,
        expected: String,
    ) {
        assertEquals(expected, formatHeaderTime(ZonedDateTime.parse(time), TODAY))
    }

    @Test
    fun `both 02 30 of the fall-back day show their offsets`() {
        val local = LocalDateTime.of(2025, 10, 26, 2, 30)
        val first = ZonedDateTime.ofLocal(local, ZURICH, ZoneOffset.ofHours(2))
        val second = first.withLaterOffsetAtOverlap()

        assertEquals("Sun 26 Oct · 02:30 UTC+2", formatHeaderTime(first, TODAY))
        assertEquals("Sun 26 Oct · 02:30 UTC+1", formatHeaderTime(second, TODAY))
    }

    @Test
    fun `the day after a clock change has no offset`() {
        assertEquals("Mon 27 Oct · 02:30", formatHeaderTime(ZonedDateTime.of(2025, 10, 27, 2, 30, 0, 0, ZURICH), TODAY))
    }

    @Test
    fun `names and digits do not follow the device locale`() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("de-CH"))
            assertEquals("Sun 21 Dec · 12:00", formatHeaderTime(ZonedDateTime.of(2025, 12, 21, 12, 0, 0, 0, ZURICH), TODAY))
        } finally {
            Locale.setDefault(saved)
        }
    }

    private companion object {
        val ZURICH: ZoneId = ZoneId.of("Europe/Zurich")
        val TODAY: LocalDate = LocalDate.of(2025, 12, 21)
    }
}
