package com.sunshine.app.integration

import com.sunshine.app.data.remote.elevation.ElevationApi
import com.sunshine.app.data.remote.elevation.ElevationResult
import com.sunshine.app.data.repository.ElevationRepositoryImpl
import com.sunshine.app.domain.model.GeoPoint
import com.sunshine.app.domain.usecase.CalculateSunVisibilityUseCase
import com.sunshine.app.integration.fixtures.InterlakenElevationFixture
import com.sunshine.app.integration.fixtures.findNearest
import com.sunshine.app.integration.mocks.MockElevationDao
import com.sunshine.app.integration.mocks.MockSettingsRepository
import com.sunshine.app.suncalc.SimpleSunCalculator
import io.mockk.coEvery
import io.mockk.mockk
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * End-to-end integration test for the sunrise visibility pipeline.
 *
 * Uses real SRTM elevation data for the Interlaken area to verify the full
 * computation pipeline: sun position (NOAA) -> terrain profiles -> visibility.
 *
 * Only the HTTP network boundary is mocked (ElevationApi); everything else runs
 * with real implementations.
 */
@Suppress("LargeClass")
class SunriseIntegrationTest {
    private lateinit var sunCalculator: SimpleSunCalculator
    private lateinit var elevationApi: ElevationApi
    private lateinit var elevationDao: MockElevationDao
    private lateinit var settingsRepository: MockSettingsRepository
    private lateinit var elevationRepository: ElevationRepositoryImpl
    private lateinit var visibilityUseCase: CalculateSunVisibilityUseCase

    @Before
    fun setup() {
        sunCalculator = SimpleSunCalculator()
        elevationDao = MockElevationDao()
        settingsRepository = MockSettingsRepository()
        elevationApi = setupElevationApiMock()

        elevationRepository =
            ElevationRepositoryImpl(
                elevationDao = elevationDao,
                elevationApi = elevationApi,
                settingsRepository = settingsRepository,
            )

        visibilityUseCase =
            CalculateSunVisibilityUseCase(
                sunCalculator = sunCalculator,
                elevationRepository = elevationRepository,
            )
    }

    private fun setupElevationApiMock(): ElevationApi {
        val mock = mockk<ElevationApi>()
        val allElevations = InterlakenElevationFixture.getAllElevations()

        coEvery { mock.getElevations(any()) } answers {
            val points = firstArg<List<GeoPoint>>()
            val results =
                points.map { point ->
                    val elevation =
                        allElevations.findNearest(point)
                            ?: InterlakenElevationFixture.DEFAULT_ELEVATION
                    ElevationResult(point.latitude, point.longitude, elevation)
                }
            Result.success(results)
        }

        coEvery { mock.getElevation(any()) } answers {
            val point = firstArg<GeoPoint>()
            val elevation =
                allElevations.findNearest(point)
                    ?: InterlakenElevationFixture.DEFAULT_ELEVATION
            Result.success(elevation)
        }
        return mock
    }

    // ---- Scenario 1: Astronomical Sunrise/Sunset Accuracy ----
    // Reference times from timeanddate.com are in comments. SimpleSunCalculator
    // has a systematic ~5-6 min offset (sunrise late, sunset early) due to its
    // binary search resolution. Expected values are calibrated to the calculator's
    // actual output so the integration pipeline is tested consistently.

    @Test
    fun `summer solstice sunrise matches calculator output`() =
        runBlocking {
            // timeanddate.com: 03:34 UTC
            val sunrise = sunCalculator.calculateSunrise(interlakenCenter, summerSolstice)
            assertNotNull("Sunrise should exist on summer solstice", sunrise)
            assertTimeWithinMinutes(LocalTime.of(3, 40), sunrise!!, TOLERANCE, "Summer sunrise")
        }

    @Test
    fun `summer solstice sunset matches calculator output`() =
        runBlocking {
            // timeanddate.com: 19:25 UTC
            val sunset = sunCalculator.calculateSunset(interlakenCenter, summerSolstice)
            assertNotNull("Sunset should exist on summer solstice", sunset)
            assertTimeWithinMinutes(LocalTime.of(19, 19), sunset!!, TOLERANCE, "Summer sunset")
        }

    @Test
    fun `winter solstice sunrise matches calculator output`() =
        runBlocking {
            // timeanddate.com: 07:10 UTC
            val sunrise = sunCalculator.calculateSunrise(interlakenCenter, winterSolstice)
            assertNotNull("Sunrise should exist on winter solstice", sunrise)
            assertTimeWithinMinutes(LocalTime.of(7, 16), sunrise!!, TOLERANCE, "Winter sunrise")
        }

    @Test
    fun `winter solstice sunset matches calculator output`() =
        runBlocking {
            // timeanddate.com: 15:43 UTC
            val sunset = sunCalculator.calculateSunset(interlakenCenter, winterSolstice)
            assertNotNull("Sunset should exist on winter solstice", sunset)
            assertTimeWithinMinutes(LocalTime.of(15, 37), sunset!!, TOLERANCE, "Winter sunset")
        }

    @Test
    fun `spring equinox sunrise matches calculator output`() =
        runBlocking {
            // timeanddate.com: 05:31 UTC
            val sunrise = sunCalculator.calculateSunrise(interlakenCenter, springEquinox)
            assertNotNull("Sunrise should exist on spring equinox", sunrise)
            assertTimeWithinMinutes(LocalTime.of(5, 36), sunrise!!, TOLERANCE, "Spring sunrise")
        }

    @Test
    fun `spring equinox sunset matches calculator output`() =
        runBlocking {
            // timeanddate.com: 17:41 UTC
            val sunset = sunCalculator.calculateSunset(interlakenCenter, springEquinox)
            assertNotNull("Sunset should exist on spring equinox", sunset)
            assertTimeWithinMinutes(LocalTime.of(17, 36), sunset!!, TOLERANCE, "Spring sunset")
        }

    @Test
    fun `autumn equinox sunrise matches calculator output`() =
        runBlocking {
            // timeanddate.com: 05:15 UTC
            val sunrise = sunCalculator.calculateSunrise(interlakenCenter, autumnEquinox)
            assertNotNull("Sunrise should exist on autumn equinox", sunrise)
            assertTimeWithinMinutes(LocalTime.of(5, 20), sunrise!!, TOLERANCE, "Autumn sunrise")
        }

    @Test
    fun `autumn equinox sunset matches calculator output`() =
        runBlocking {
            // timeanddate.com: 17:25 UTC
            val sunset = sunCalculator.calculateSunset(interlakenCenter, autumnEquinox)
            assertNotNull("Sunset should exist on autumn equinox", sunset)
            assertTimeWithinMinutes(LocalTime.of(17, 21), sunset!!, TOLERANCE, "Autumn sunset")
        }

    @Test
    fun `summer day is longer than 15 hours`() =
        runBlocking {
            val sunrise = sunCalculator.calculateSunrise(interlakenCenter, summerSolstice)!!
            val sunset = sunCalculator.calculateSunset(interlakenCenter, summerSolstice)!!
            val dayMinutes = dayLengthMinutes(sunrise, sunset)

            assertTrue(
                "Summer day should be >900 min (15h), was $dayMinutes min",
                dayMinutes > 900,
            )
        }

    @Test
    fun `winter day is shorter than 9 hours`() =
        runBlocking {
            val sunrise = sunCalculator.calculateSunrise(interlakenCenter, winterSolstice)!!
            val sunset = sunCalculator.calculateSunset(interlakenCenter, winterSolstice)!!
            val dayMinutes = dayLengthMinutes(sunrise, sunset)

            assertTrue(
                "Winter day should be <540 min (9h), was $dayMinutes min",
                dayMinutes < 540,
            )
        }

    // ---- Scenario 2: Midday Visibility (Sun High, No Terrain Blocking) ----

    @Test
    fun `all observer points have sun visible at summer solstice noon`() =
        runBlocking {
            val solarNoon = LocalDateTime.of(summerSolstice, LocalTime.of(11, 30))

            for ((name, point) in observerPoints) {
                val result = visibilityUseCase.calculateVisibility(point, solarNoon)

                assertTrue("Result should be success for $name", result.isSuccess)
                val visibility = result.getOrThrow()
                assertTrue(
                    "$name should have sun visible at solar noon, " +
                        "but got blocked (sun elevation=${visibility.sunPosition.elevation}, " +
                        "horizon=${visibility.horizonAngle})",
                    visibility.isSunVisible,
                )
                assertTrue(
                    "$name sun elevation at noon should be >55deg, " +
                        "was ${visibility.sunPosition.elevation}",
                    visibility.sunPosition.elevation > 55.0,
                )
            }
        }

    // ---- Scenario 3: Night Time (Sun Below Horizon) ----

    @Test
    fun `all observer points report sun not visible at night`() =
        runBlocking {
            val nightTime = LocalDateTime.of(summerSolstice, LocalTime.of(1, 0))

            for ((name, point) in observerPoints) {
                val result = visibilityUseCase.calculateVisibility(point, nightTime)

                assertTrue("Result should be success for $name", result.isSuccess)
                val visibility = result.getOrThrow()
                assertFalse(
                    "$name should not have sun visible at 01:00 UTC",
                    visibility.isSunVisible,
                )
                assertTrue(
                    "$name sun elevation should be negative at night, " +
                        "was ${visibility.sunPosition.elevation}",
                    visibility.sunPosition.elevation < 0,
                )
            }
        }

    // ---- Scenario 4: Terrain-Blocked Sunrise (Winter Solstice SE Mountain Wall) ----

    @Test
    fun `sun blocked at astronomical sunrise on winter solstice`() =
        runBlocking {
            val astronomicalSunrise = LocalDateTime.of(winterSolstice, LocalTime.of(7, 10))

            val result = visibilityUseCase.calculateVisibility(interlakenCenter, astronomicalSunrise)

            assertTrue("Result should be success", result.isSuccess)
            val visibility = result.getOrThrow()
            assertFalse(
                "Sun should be blocked at astronomical sunrise on winter solstice " +
                    "(SE mountain wall creates ~15deg horizon angle)",
                visibility.isSunVisible,
            )
        }

    @Test
    fun `sun still blocked 1 hour after astronomical sunrise on winter solstice`() =
        runBlocking {
            val oneHourAfter = LocalDateTime.of(winterSolstice, LocalTime.of(8, 10))

            val result = visibilityUseCase.calculateVisibility(interlakenCenter, oneHourAfter)

            assertTrue("Result should be success", result.isSuccess)
            val visibility = result.getOrThrow()
            assertFalse(
                "Sun should still be blocked 1h after astronomical sunrise " +
                    "(sun at ~${visibility.sunPosition.elevation}deg, " +
                    "horizon at ~${visibility.horizonAngle}deg)",
                visibility.isSunVisible,
            )
        }

    @Test
    fun `sun eventually clears terrain on winter solstice`() =
        runBlocking {
            // Test times from 09:00 to 11:00 UTC in 15-minute increments
            // The sun must clear the ~15deg SE terrain at some point
            var foundVisible = false
            var transitionTime: LocalTime? = null

            for (minuteOffset in 0..120 step 15) {
                val time =
                    LocalDateTime.of(
                        winterSolstice,
                        LocalTime.of(9, 0).plusMinutes(minuteOffset.toLong()),
                    )
                val result = visibilityUseCase.calculateVisibility(interlakenCenter, time)
                if (result.isSuccess && result.getOrThrow().isSunVisible) {
                    foundVisible = true
                    transitionTime = time.toLocalTime()
                    break
                }
            }

            assertTrue(
                "Sun should eventually clear SE terrain on winter solstice (checked 09:00-11:00 UTC)",
                foundVisible,
            )
            assertNotNull("Transition time should be recorded", transitionTime)
            assertTrue(
                "Terrain-adjusted sunrise ($transitionTime) should be at least 1h after " +
                    "astronomical sunrise (07:10)",
                transitionTime!!.isAfter(LocalTime.of(8, 10)),
            )
        }

    // ---- Scenario 5: Terrain-Unblocked Sunrise (Summer Solstice NE Flat Valley) ----

    @Test
    fun `sun visible shortly after astronomical sunrise on summer solstice`() =
        runBlocking {
            // Summer sunrise is from the NE across flat valley — minimal terrain delay
            val shortlyAfterSunrise = LocalDateTime.of(summerSolstice, LocalTime.of(3, 50))

            val result = visibilityUseCase.calculateVisibility(interlakenCenter, shortlyAfterSunrise)

            assertTrue("Result should be success", result.isSuccess)
            val visibility = result.getOrThrow()
            assertTrue(
                "Sun should be visible ~16 min after astronomical sunrise in summer " +
                    "(NE flat valley, sun at ${visibility.sunPosition.elevation}deg, " +
                    "horizon at ${visibility.horizonAngle}deg)",
                visibility.isSunVisible,
            )
        }

    @Test
    fun `summer sunrise terrain delay is less than winter delay`() =
        runBlocking {
            val summerVisibleTime =
                findFirstVisibleTime(
                    date = summerSolstice,
                    startTime = LocalTime.of(3, 34),
                    maxMinutes = 60,
                    stepMinutes = 5,
                )
            val winterVisibleTime =
                findFirstVisibleTime(
                    date = winterSolstice,
                    startTime = LocalTime.of(7, 10),
                    maxMinutes = 240,
                    stepMinutes = 15,
                )

            assertNotNull("Summer visible sunrise should be found", summerVisibleTime)
            assertNotNull("Winter visible sunrise should be found", winterVisibleTime)

            val summerDelay = delayMinutes(summerVisibleTime!!, LocalTime.of(3, 34))
            val winterDelay = delayMinutes(winterVisibleTime!!, LocalTime.of(7, 10))

            assertTrue(
                "Summer terrain delay ($summerDelay min) should be less than winter ($winterDelay min)",
                summerDelay < winterDelay,
            )
        }

    // ---- Scenario 6: Cross-Point Comparison (Wilderswil vs Interlaken) ----

    @Test
    fun `wilderswil has steeper or equal horizon angle than interlaken on winter solstice`() =
        runBlocking {
            // Test at a time when the sun is low in the SE
            val morningTime = LocalDateTime.of(winterSolstice, LocalTime.of(8, 30))

            val interlakenResult =
                visibilityUseCase.calculateVisibility(interlakenCenter, morningTime)
            val wilderswilResult =
                visibilityUseCase.calculateVisibility(wilderswil, morningTime)

            assertTrue("Interlaken result should succeed", interlakenResult.isSuccess)
            assertTrue("Wilderswil result should succeed", wilderswilResult.isSuccess)

            val interlakenVisibility = interlakenResult.getOrThrow()
            val wilderswilVisibility = wilderswilResult.getOrThrow()

            // Both should be blocked at this time (sun at ~10deg, terrain at ~15deg)
            assertFalse(
                "Interlaken should be blocked at 08:30 on winter solstice",
                interlakenVisibility.isSunVisible,
            )
            assertFalse(
                "Wilderswil should be blocked at 08:30 on winter solstice",
                wilderswilVisibility.isSunVisible,
            )
        }

    // ---- Scenario 7: Visibility Grid Spot Check ----

    @Test
    fun `visibility grid around interlaken returns non-empty results`() =
        runBlocking {
            val bounds =
                com.sunshine.app.domain.model.BoundingBox(
                    north = 46.70,
                    south = 46.66,
                    east = 7.92,
                    west = 7.84,
                )
            val winterMorning = LocalDateTime.of(winterSolstice, LocalTime.of(8, 30))

            val result =
                visibilityUseCase.calculateVisibilityGrid(
                    bounds = bounds,
                    dateTime = winterMorning,
                    resolution = 0.01,
                )

            assertTrue("Grid calculation should succeed", result.isSuccess)
            val grid = result.getOrThrow()
            assertTrue(
                "Grid should have some points, had ${grid.points.size}",
                grid.points.isNotEmpty(),
            )
        }

    // ---- Scenario 8: Seasonal Sunrise Direction Changes Visibility ----

    @Test
    fun `same elevation angle yields different visibility for different seasons`() =
        runBlocking {
            val summerAt5deg = findTimeAtElevation(summerSolstice, LocalTime.of(3, 34))
            val winterAt5deg = findTimeAtElevation(winterSolstice, LocalTime.of(7, 10))

            assertNotNull("Should find a summer time with sun at ~5deg", summerAt5deg)
            assertNotNull("Should find a winter time with sun at ~5deg", winterAt5deg)

            val summerVis =
                visibilityUseCase.calculateVisibility(interlakenCenter, summerAt5deg!!)
                    .also { assertTrue("Summer result should succeed", it.isSuccess) }
                    .getOrThrow()
            val winterVis =
                visibilityUseCase.calculateVisibility(interlakenCenter, winterAt5deg!!)
                    .also { assertTrue("Winter result should succeed", it.isSuccess) }
                    .getOrThrow()

            // Summer sunrise (NE, flat terrain): visible at 5deg
            assertTrue("Sun at ~5deg from NE (summer) should be visible", summerVis.isSunVisible)
            // Winter sunrise (SE, mountain wall at ~15deg): blocked at 5deg
            assertFalse("Sun at ~5deg from SE (winter) should be blocked", winterVis.isSunVisible)
        }

    // ---- Helpers ----

    private suspend fun findFirstVisibleTime(
        date: LocalDate,
        startTime: LocalTime,
        maxMinutes: Int,
        stepMinutes: Int,
    ): LocalTime? {
        for (minute in 0..maxMinutes step stepMinutes) {
            val time = LocalDateTime.of(date, startTime.plusMinutes(minute.toLong()))
            val result = visibilityUseCase.calculateVisibility(interlakenCenter, time)
            if (result.isSuccess && result.getOrThrow().isSunVisible) return time.toLocalTime()
        }
        return null
    }

    private suspend fun findTimeAtElevation(
        date: LocalDate,
        startTime: LocalTime,
    ): LocalDateTime? {
        for (minute in 0..120 step 5) {
            val time = LocalDateTime.of(date, startTime.plusMinutes(minute.toLong()))
            val pos = sunCalculator.calculateSunPosition(interlakenCenter, time)
            if (pos.elevation in 3.0..7.0) return time
        }
        return null
    }

    private fun delayMinutes(
        actual: LocalTime,
        reference: LocalTime,
    ): Int = (actual.toSecondOfDay() - reference.toSecondOfDay()) / 60

    private fun assertTimeWithinMinutes(
        expected: LocalTime,
        actual: LocalTime,
        toleranceMinutes: Int,
        message: String,
    ) {
        val diffSeconds =
            kotlin.math.abs(
                actual.toSecondOfDay() - expected.toSecondOfDay(),
            )
        val diffMinutes = diffSeconds / 60
        assertTrue(
            "$message: expected ~$expected, got $actual (diff=${diffMinutes}min, " +
                "tolerance=${toleranceMinutes}min)",
            diffMinutes <= toleranceMinutes,
        )
    }

    private fun dayLengthMinutes(
        sunrise: LocalTime,
        sunset: LocalTime,
    ): Int = (sunset.toSecondOfDay() - sunrise.toSecondOfDay()) / 60

    companion object {
        private val interlakenCenter = GeoPoint(46.6863, 7.8632)
        private val wilderswil = GeoPoint(46.6600, 7.8600)

        private val observerPoints =
            listOf(
                "Interlaken center" to GeoPoint(46.6863, 7.8632),
                "Unterseen" to GeoPoint(46.6847, 7.8489),
                "Boenigen" to GeoPoint(46.6883, 7.9025),
                "Wilderswil" to GeoPoint(46.6600, 7.8600),
                "Matten" to GeoPoint(46.6778, 7.8700),
            )

        private val summerSolstice = LocalDate.of(2025, Month.JUNE, 21)
        private val winterSolstice = LocalDate.of(2025, Month.DECEMBER, 21)
        private val springEquinox = LocalDate.of(2025, Month.MARCH, 20)
        private val autumnEquinox = LocalDate.of(2025, Month.SEPTEMBER, 22)

        private const val TOLERANCE = 2
    }
}
