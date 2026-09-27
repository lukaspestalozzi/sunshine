package com.sunshine.core

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// point-sunshine spec, "Sun periods of the selected day".
class SunPeriodsTest {
    @Test
    fun `a constant 10 degree horizon gives one period between the crossings of the upper edge`() {
        val (rise, set) = crossings(10.0)

        val periods = known(sunPeriods(horizon { 10.0 }, INTERLAKEN, WINTER, ZURICH))

        assertEquals(1, periods.size)
        assertWithin(rise, periods[0].start, Duration.ofSeconds(20))
        assertWithin(set, periods[0].end, Duration.ofSeconds(20))
    }

    @Test
    fun `a notch in the horizon at noon splits the day into two periods`() {
        val profile = horizon { azimuth -> if (azimuth in 178.0..182.0) 40.0 else 10.0 }

        val periods = known(sunPeriods(profile, INTERLAKEN, WINTER, ZURICH))

        assertEquals(2, periods.size)
        assertTrue(periods[0].end < periods[1].start)
    }

    @Test
    fun `a sunny sliver shorter than a minute is omitted`() {
        // Horizons just below the day's highest upper edge: sun for about 40 s or 2 min.
        val noon = upperEdges(WINTER.atTime(11, 30).atZone(ZURICH), WINTER.atTime(12, 30).atZone(ZURICH)).sortedDescending()

        assertEquals(0, known(sunPeriods(horizon { noon[40] }, INTERLAKEN, WINTER, ZURICH)).size)
        assertEquals(1, known(sunPeriods(horizon { noon[120] }, INTERLAKEN, WINTER, ZURICH)).size)
    }

    @Test
    fun `an unknown state while the sun is up makes the day unknown`() {
        val profile = horizon(complete = { azimuth -> azimuth !in 170.0..190.0 }) { 5.0 }

        assertEquals(SunPeriods.Unknown, sunPeriods(profile, INTERLAKEN, WINTER, ZURICH))
    }

    @Test
    fun `missing data where the sun never is leaves the day known`() {
        val profile = horizon(complete = { azimuth -> azimuth in 30.0..330.0 }) { 10.0 }

        assertEquals(1, known(sunPeriods(profile, INTERLAKEN, WINTER, ZURICH)).size)
    }

    @Test
    fun `the day window is the local day, 23 h on the day the clocks go forward`() {
        val date = LocalDate.of(2025, 3, 30)

        val periods = known(sunPeriods(horizon { -90.0 }, INTERLAKEN, date, ZURICH))

        assertEquals(listOf(SunPeriod(date.atStartOfDay(ZURICH), date.plusDays(1).atStartOfDay(ZURICH))), periods)
        assertEquals(Duration.ofHours(23), Duration.between(periods[0].start, periods[0].end))
    }

    private fun known(periods: SunPeriods): List<SunPeriod> = (periods as SunPeriods.Known).periods

    private fun horizon(
        complete: (Double) -> Boolean = { true },
        angle: (Double) -> Double,
    ) = HorizonProfile(
        eyeHeight = 568.0,
        angles = DoubleArray(AZIMUTH_COUNT) { angle(it * AZIMUTH_STEP) },
        complete = BooleanArray(AZIMUTH_COUNT) { complete(it * AZIMUTH_STEP) },
    )

    /** Upper-edge elevations at 1 s steps from [from] to [to], from commons-suncalc. */
    private fun upperEdges(
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): List<Double> =
        (0 until Duration.between(from, to).seconds).map {
            sunPosition(INTERLAKEN, from.toInstant().plusSeconds(it)).elevation + SUN_UPPER_LIMB
        }

    /** First and last second of [WINTER] at which the upper edge is above [angle]. */
    private fun crossings(angle: Double): Pair<ZonedDateTime, ZonedDateTime> {
        val start = WINTER.atStartOfDay(ZURICH)
        val above = upperEdges(start, WINTER.plusDays(1).atStartOfDay(ZURICH)).withIndex().filter { it.value > angle }
        return start.plusSeconds(above.first().index.toLong()) to start.plusSeconds(above.last().index + 1L)
    }

    private fun assertWithin(
        expected: ZonedDateTime,
        actual: ZonedDateTime,
        tolerance: Duration,
    ) = assertTrue(Duration.between(expected, actual).abs() <= tolerance, "expected $expected ±$tolerance, was $actual")

    private companion object {
        val INTERLAKEN = GeoPoint(46.6863, 7.8632)
        val WINTER: LocalDate = LocalDate.of(2025, 12, 21)
        val ZURICH: ZoneId = ZoneId.of("Europe/Zurich")
    }
}
