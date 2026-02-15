package com.sunshine.app.suncalc

import com.sunshine.app.domain.model.GeoPoint
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

/**
 * Comparison test between SimpleSunCalculator (custom NOAA) and CommonsSunCalculator
 * (commons-suncalc library). Evaluates accuracy differences against known reference
 * values from timeanddate.com and NOAA Solar Calculator.
 *
 * Reference values sourced from:
 * - NOAA Solar Calculator: https://gml.noaa.gov/grad/solcalc/
 * - timeanddate.com (Bern, Interlaken)
 *
 * All times are UTC. All angles in degrees.
 */
@Suppress("LargeClass")
class SunCalculatorComparisonTest {
    private lateinit var simple: SimpleSunCalculator
    private lateinit var commons: CommonsSunCalculator

    @Before
    fun setup() {
        simple = SimpleSunCalculator()
        commons = CommonsSunCalculator()
    }

    // =========================================================================
    // Section 1: Sun Position Accuracy vs NOAA Reference Values
    // =========================================================================
    // Reference: NOAA Solar Calculator for Bern (46.95°N, 7.45°E)
    // Summer solstice 2024-06-21 solar noon ~11:30 UTC
    //   Expected elevation: ~66.5°, azimuth: ~180° (due south)
    // Winter solstice 2024-12-21 solar noon ~11:45 UTC
    //   Expected elevation: ~19.6°, azimuth: ~180° (due south)

    @Test
    fun `compare summer solstice noon elevation - Bern`() =
        runBlocking {
            val location = bern
            val solarNoon = LocalDateTime.of(2024, Month.JUNE, 21, 11, 30)
            val expectedElevation = 66.5

            val simplePos = simple.calculateSunPosition(location, solarNoon)
            val commonsPos = commons.calculateSunPosition(location, solarNoon)

            printComparison(
                "Summer Solstice Noon Elevation (Bern)",
                "elevation",
                expectedElevation,
                simplePos.elevation,
                commonsPos.elevation,
            )
        }

    @Test
    fun `compare summer solstice noon azimuth - Bern`() =
        runBlocking {
            val location = bern
            val solarNoon = LocalDateTime.of(2024, Month.JUNE, 21, 11, 30)
            val expectedAzimuth = 180.0

            val simplePos = simple.calculateSunPosition(location, solarNoon)
            val commonsPos = commons.calculateSunPosition(location, solarNoon)

            printComparison(
                "Summer Solstice Noon Azimuth (Bern)",
                "azimuth",
                expectedAzimuth,
                simplePos.azimuth,
                commonsPos.azimuth,
            )
        }

    @Test
    fun `compare winter solstice noon elevation - Bern`() =
        runBlocking {
            val location = bern
            val solarNoon = LocalDateTime.of(2024, Month.DECEMBER, 21, 11, 45)
            val expectedElevation = 19.6

            val simplePos = simple.calculateSunPosition(location, solarNoon)
            val commonsPos = commons.calculateSunPosition(location, solarNoon)

            printComparison(
                "Winter Solstice Noon Elevation (Bern)",
                "elevation",
                expectedElevation,
                simplePos.elevation,
                commonsPos.elevation,
            )
        }

    @Test
    fun `compare winter solstice noon azimuth - Bern`() =
        runBlocking {
            val location = bern
            val solarNoon = LocalDateTime.of(2024, Month.DECEMBER, 21, 11, 45)
            val expectedAzimuth = 180.0

            val simplePos = simple.calculateSunPosition(location, solarNoon)
            val commonsPos = commons.calculateSunPosition(location, solarNoon)

            printComparison(
                "Winter Solstice Noon Azimuth (Bern)",
                "azimuth",
                expectedAzimuth,
                simplePos.azimuth,
                commonsPos.azimuth,
            )
        }

    // =========================================================================
    // Section 2: Sunrise/Sunset Accuracy vs timeanddate.com Reference
    // =========================================================================
    // Reference: timeanddate.com for Interlaken (46.6863°N, 7.8632°E)
    // All times UTC.

    @Test
    fun `compare summer solstice sunrise - Interlaken`() =
        runBlocking {
            // timeanddate.com: 03:34 UTC
            val expectedMinutes = toMinutes(3, 34)

            val simpleRise = simple.calculateSunrise(interlaken, summerSolstice)
            val commonsRise = commons.calculateSunrise(interlaken, summerSolstice)

            printTimeComparison(
                "Summer Solstice Sunrise (Interlaken)",
                expectedMinutes,
                simpleRise,
                commonsRise,
            )
        }

    @Test
    fun `compare summer solstice sunset - Interlaken`() =
        runBlocking {
            // timeanddate.com: 19:25 UTC
            val expectedMinutes = toMinutes(19, 25)

            val simpleSet = simple.calculateSunset(interlaken, summerSolstice)
            val commonsSet = commons.calculateSunset(interlaken, summerSolstice)

            printTimeComparison(
                "Summer Solstice Sunset (Interlaken)",
                expectedMinutes,
                simpleSet,
                commonsSet,
            )
        }

    @Test
    fun `compare winter solstice sunrise - Interlaken`() =
        runBlocking {
            // timeanddate.com: 07:10 UTC
            val expectedMinutes = toMinutes(7, 10)

            val simpleRise = simple.calculateSunrise(interlaken, winterSolstice)
            val commonsRise = commons.calculateSunrise(interlaken, winterSolstice)

            printTimeComparison(
                "Winter Solstice Sunrise (Interlaken)",
                expectedMinutes,
                simpleRise,
                commonsRise,
            )
        }

    @Test
    fun `compare winter solstice sunset - Interlaken`() =
        runBlocking {
            // timeanddate.com: 15:43 UTC
            val expectedMinutes = toMinutes(15, 43)

            val simpleSet = simple.calculateSunset(interlaken, winterSolstice)
            val commonsSet = commons.calculateSunset(interlaken, winterSolstice)

            printTimeComparison(
                "Winter Solstice Sunset (Interlaken)",
                expectedMinutes,
                simpleSet,
                commonsSet,
            )
        }

    @Test
    fun `compare spring equinox sunrise - Interlaken`() =
        runBlocking {
            // timeanddate.com: 05:31 UTC
            val expectedMinutes = toMinutes(5, 31)

            val simpleRise = simple.calculateSunrise(interlaken, springEquinox)
            val commonsRise = commons.calculateSunrise(interlaken, springEquinox)

            printTimeComparison(
                "Spring Equinox Sunrise (Interlaken)",
                expectedMinutes,
                simpleRise,
                commonsRise,
            )
        }

    @Test
    fun `compare spring equinox sunset - Interlaken`() =
        runBlocking {
            // timeanddate.com: 17:41 UTC
            val expectedMinutes = toMinutes(17, 41)

            val simpleSet = simple.calculateSunset(interlaken, springEquinox)
            val commonsSet = commons.calculateSunset(interlaken, springEquinox)

            printTimeComparison(
                "Spring Equinox Sunset (Interlaken)",
                expectedMinutes,
                simpleSet,
                commonsSet,
            )
        }

    // =========================================================================
    // Section 3: Full Day Position Comparison (hourly)
    // =========================================================================

    @Test
    fun `compare full day positions - summer solstice Bern`() =
        runBlocking {
            compareFullDay("Summer Solstice", LocalDate.of(2024, Month.JUNE, 21))
        }

    @Test
    fun `compare full day positions - winter solstice Bern`() =
        runBlocking {
            compareFullDay("Winter Solstice", LocalDate.of(2024, Month.DECEMBER, 21))
        }

    // =========================================================================
    // Section 4: Edge Cases
    // =========================================================================

    @Test
    fun `compare arctic midnight sun - summer`() =
        runBlocking {
            val arctic = GeoPoint(latitude = 70.0, longitude = 25.0)
            val midnight = LocalDateTime.of(2024, Month.JUNE, 21, 0, 0)

            val sp = simple.calculateSunPosition(arctic, midnight)
            val cp = commons.calculateSunPosition(arctic, midnight)

            println("\n=== Arctic Midnight Sun (70°N, June 21, 00:00 UTC) ===")
            println("Simple:  elevation=%.2f°, azimuth=%.2f°".format(sp.elevation, sp.azimuth))
            println("Commons: elevation=%.2f°, azimuth=%.2f°".format(cp.elevation, cp.azimuth))
            println("Both above horizon? Simple=${sp.isAboveHorizon}, Commons=${cp.isAboveHorizon}")
        }

    @Test
    fun `compare arctic polar night - winter`() =
        runBlocking {
            val arctic = GeoPoint(latitude = 70.0, longitude = 25.0)
            val noon = LocalDateTime.of(2024, Month.DECEMBER, 21, 12, 0)

            val sp = simple.calculateSunPosition(arctic, noon)
            val cp = commons.calculateSunPosition(arctic, noon)

            println("\n=== Arctic Polar Night (70°N, Dec 21, 12:00 UTC) ===")
            println("Simple:  elevation=%.2f°, azimuth=%.2f°".format(sp.elevation, sp.azimuth))
            println("Commons: elevation=%.2f°, azimuth=%.2f°".format(cp.elevation, cp.azimuth))
            println("Both below horizon? Simple=${!sp.isAboveHorizon}, Commons=${!cp.isAboveHorizon}")
        }

    @Test
    fun `compare equator equinox - nearly overhead`() =
        runBlocking {
            val equator = GeoPoint(latitude = 0.0, longitude = 0.0)
            val equinoxNoon = LocalDateTime.of(2024, Month.MARCH, 20, 12, 0)

            val sp = simple.calculateSunPosition(equator, equinoxNoon)
            val cp = commons.calculateSunPosition(equator, equinoxNoon)

            println("\n=== Equator Equinox Noon (0°, 0°, Mar 20, 12:00 UTC) ===")
            println("Simple:  elevation=%.2f°, azimuth=%.2f°".format(sp.elevation, sp.azimuth))
            println("Commons: elevation=%.2f°, azimuth=%.2f°".format(cp.elevation, cp.azimuth))
            println("Elevation difference: %.2f°".format(abs(sp.elevation - cp.elevation)))
        }

    @Test
    fun `compare southern hemisphere - Cape Town`() =
        runBlocking {
            val capeTown = GeoPoint(latitude = -33.9, longitude = 18.4)
            val decNoon = LocalDateTime.of(2024, Month.DECEMBER, 21, 11, 0)

            val sp = simple.calculateSunPosition(capeTown, decNoon)
            val cp = commons.calculateSunPosition(capeTown, decNoon)

            println("\n=== Southern Hemisphere: Cape Town (Dec 21, 11:00 UTC) ===")
            println("Simple:  elevation=%.2f°, azimuth=%.2f°".format(sp.elevation, sp.azimuth))
            println("Commons: elevation=%.2f°, azimuth=%.2f°".format(cp.elevation, cp.azimuth))
            println("Elevation difference: %.2f°".format(abs(sp.elevation - cp.elevation)))
        }

    // =========================================================================
    // Section 5: Aggregate Statistics
    // =========================================================================

    @Test
    fun `aggregate accuracy summary across all test scenarios`() =
        runBlocking {
            val samples = collectSamples()
            printAggregateSummary(samples)
        }

    // =========================================================================
    // Helpers
    // =========================================================================

    private fun printComparison(
        label: String,
        metric: String,
        expected: Double,
        simpleValue: Double,
        commonsValue: Double,
    ) {
        val simpleError = abs(simpleValue - expected)
        val commonsError = abs(commonsValue - expected)

        println("\n=== $label ===")
        println("Expected $metric: %.2f°".format(expected))
        println("Simple:  %.2f° (error: %.2f°)".format(simpleValue, simpleError))
        println("Commons: %.2f° (error: %.2f°)".format(commonsValue, commonsError))
        println(
            "Winner: %s (%.2f° closer)".format(
                if (commonsError < simpleError) "commons-suncalc" else "SimpleSunCalculator",
                abs(simpleError - commonsError),
            ),
        )
    }

    private fun printTimeComparison(
        label: String,
        expectedMinutes: Int,
        simpleTime: LocalTime?,
        commonsTime: LocalTime?,
    ) {
        val expectedTime = LocalTime.of(expectedMinutes / 60, expectedMinutes % 60)

        val simpleMinutes = simpleTime?.let { it.hour * 60 + it.minute }
        val commonsMinutes = commonsTime?.let { it.hour * 60 + it.minute }

        val simpleError = simpleMinutes?.let { abs(it - expectedMinutes) }
        val commonsError = commonsMinutes?.let { abs(it - expectedMinutes) }

        println("\n=== $label ===")
        println("Expected: $expectedTime")
        println("Simple:  $simpleTime (error: ${simpleError ?: "N/A"} min)")
        println("Commons: $commonsTime (error: ${commonsError ?: "N/A"} min)")

        if (simpleError != null && commonsError != null) {
            println(
                "Winner: %s (%d min closer)".format(
                    if (commonsError < simpleError) "commons-suncalc" else "SimpleSunCalculator",
                    abs(simpleError - commonsError),
                ),
            )
        }
    }

    private fun toMinutes(
        hour: Int,
        minute: Int,
    ): Int = hour * 60 + minute

    private fun azimuthDifference(
        a: Double,
        b: Double,
    ): Double {
        val diff = abs(a - b)
        return if (diff > 180) 360 - diff else diff
    }

    private suspend fun compareFullDay(
        label: String,
        date: LocalDate,
    ) {
        println("\n=== Full Day Comparison: $label, Bern ===")
        printFullDayHeader()

        var maxElevDiff = 0.0
        var maxAzDiff = 0.0

        for (hour in 0..23) {
            val dt = LocalDateTime.of(date, LocalTime.of(hour, 0))
            val sp = simple.calculateSunPosition(bern, dt)
            val cp = commons.calculateSunPosition(bern, dt)

            val elevDiff = abs(sp.elevation - cp.elevation)
            val azDiff = azimuthDifference(sp.azimuth, cp.azimuth)

            if (elevDiff > maxElevDiff) maxElevDiff = elevDiff
            if (azDiff > maxAzDiff) maxAzDiff = azDiff

            printHourlyRow(hour, sp, cp, elevDiff, azDiff)
        }

        println("-".repeat(90))
        println("Max elevation difference: %.2f°".format(maxElevDiff))
        println("Max azimuth difference:   %.2f°".format(maxAzDiff))
    }

    private fun printFullDayHeader() {
        println("Hour(UTC) | Simple elev | Commons elev | Δ elev | Simple az | Commons az | Δ az")
        println("-".repeat(90))
    }

    private fun printHourlyRow(
        hour: Int,
        sp: com.sunshine.app.domain.model.SunPosition,
        cp: com.sunshine.app.domain.model.SunPosition,
        elevDiff: Double,
        azDiff: Double,
    ) {
        println(
            "%02d:00     | %+7.2f°    | %+7.2f°     | %5.2f° | %7.2f°  | %7.2f°   | %5.2f°".format(
                hour,
                sp.elevation,
                cp.elevation,
                elevDiff,
                sp.azimuth,
                cp.azimuth,
                azDiff,
            ),
        )
    }

    private data class Sample(
        val label: String,
        val elevDiff: Double,
        val azDiff: Double,
    )

    private suspend fun collectSamples(): List<Sample> {
        val samples = mutableListOf<Sample>()
        for ((locName, loc) in sampleLocations) {
            for ((dateName, date) in sampleDates) {
                for (hour in 0..23 step 3) {
                    samples.add(compareSample(locName, dateName, loc, date, hour))
                }
            }
        }
        return samples
    }

    private suspend fun compareSample(
        locName: String,
        dateName: String,
        loc: GeoPoint,
        date: LocalDate,
        hour: Int,
    ): Sample {
        val dt = LocalDateTime.of(date, LocalTime.of(hour, 0))
        val sp = simple.calculateSunPosition(loc, dt)
        val cp = commons.calculateSunPosition(loc, dt)
        return Sample(
            "$locName/$dateName/${hour}h",
            abs(sp.elevation - cp.elevation),
            azimuthDifference(sp.azimuth, cp.azimuth),
        )
    }

    private fun printAggregateSummary(samples: List<Sample>) {
        val maxElevSample = samples.maxBy { it.elevDiff }
        val maxAzSample = samples.maxBy { it.azDiff }

        println("\n" + "=".repeat(70))
        println("AGGREGATE ACCURACY SUMMARY")
        println("=".repeat(70))
        println("Total samples: ${samples.size}")
        println()
        println("Elevation difference:")
        println("  Average: %.3f°".format(samples.map { it.elevDiff }.average()))
        println("  Maximum: %.3f° (at ${maxElevSample.label})".format(maxElevSample.elevDiff))
        println()
        println("Azimuth difference:")
        println("  Average: %.3f°".format(samples.map { it.azDiff }.average()))
        println("  Maximum: %.3f° (at ${maxAzSample.label})".format(maxAzSample.azDiff))
        println()

        val significantDiffs = samples.filter { it.elevDiff > 1.0 || it.azDiff > 1.0 }
        if (significantDiffs.isNotEmpty()) {
            println("Samples with >1° difference:")
            significantDiffs.forEach { println("  ${it.label}: elev=%.2f°, az=%.2f°".format(it.elevDiff, it.azDiff)) }
        } else {
            println("No samples with >1° difference.")
        }
        println("=".repeat(70))
    }

    companion object {
        private val bern = GeoPoint(latitude = 46.95, longitude = 7.45)
        private val interlaken = GeoPoint(46.6863, 7.8632)
        private val summerSolstice = LocalDate.of(2025, Month.JUNE, 21)
        private val winterSolstice = LocalDate.of(2025, Month.DECEMBER, 21)
        private val springEquinox = LocalDate.of(2025, Month.MARCH, 20)

        private val sampleLocations =
            listOf(
                "Bern" to bern,
                "Interlaken" to interlaken,
                "Equator" to GeoPoint(0.0, 0.0),
                "Cape Town" to GeoPoint(-33.9, 18.4),
                "Arctic" to GeoPoint(70.0, 25.0),
            )
        private val sampleDates =
            listOf(
                "Summer" to LocalDate.of(2024, Month.JUNE, 21),
                "Winter" to LocalDate.of(2024, Month.DECEMBER, 21),
                "Equinox" to LocalDate.of(2024, Month.MARCH, 20),
            )
    }
}
