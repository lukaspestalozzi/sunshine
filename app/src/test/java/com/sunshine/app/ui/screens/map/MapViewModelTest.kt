package com.sunshine.app.ui.screens.map

import app.cash.turbine.test
import com.sunshine.app.domain.model.BoundingBox
import com.sunshine.app.domain.model.GeoPoint
import com.sunshine.app.domain.model.SunPosition
import com.sunshine.app.domain.model.VisibilityGrid
import com.sunshine.app.domain.model.VisibilityResult
import com.sunshine.app.domain.usecase.CalculateSunVisibilityUseCase
import com.sunshine.app.suncalc.SunCalculator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {
    private lateinit var sunCalculator: SunCalculator
    private lateinit var visibilityUseCase: CalculateSunVisibilityUseCase
    private lateinit var viewModel: MapViewModel

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setup() {
        originalTimeZone = TimeZone.getDefault()
        // Use UTC for deterministic ViewModel tests; timezone conversion is tested separately
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Dispatchers.setMain(testDispatcher)
        sunCalculator = mockk()
        visibilityUseCase = mockk()

        // Default mock responses
        coEvery { sunCalculator.calculateSunPosition(any(), any()) } returns
            SunPosition(azimuth = 180.0, elevation = 45.0)
        coEvery { sunCalculator.calculateSunrise(any(), any()) } returns
            LocalTime.of(6, 30)
        coEvery { sunCalculator.calculateSunset(any(), any()) } returns
            LocalTime.of(20, 30)
        coEvery { visibilityUseCase.calculateVisibility(any(), any()) } returns
            Result.success(
                VisibilityResult.visible(
                    location = GeoPoint.DEFAULT,
                    sunPosition = SunPosition(azimuth = 180.0, elevation = 45.0),
                    horizonAngle = 0.0,
                ),
            )
        coEvery { visibilityUseCase.calculateVisibilityGrid(any(), any(), any()) } returns
            Result.success(
                VisibilityGrid(
                    bounds = BoundingBox(north = 47.0, south = 46.0, east = 9.0, west = 8.0),
                    resolution = 0.01,
                    points = emptyMap(),
                ),
            )
        coEvery { visibilityUseCase.calculateTerrainSunriseSunset(any(), any()) } returns
            Result.success(Pair(LocalTime.of(7, 0), LocalTime.of(20, 0)))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun `initial state has default values`() =
        runTest {
            viewModel = MapViewModel(sunCalculator, visibilityUseCase)

            assertEquals(GeoPoint.DEFAULT, viewModel.uiState.value.mapCenter)
            assertEquals(MapUiState.DEFAULT_ZOOM, viewModel.uiState.value.zoomLevel, 0.1)
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun `onDateSelected updates selected date`() =
        runTest {
            viewModel = MapViewModel(sunCalculator, visibilityUseCase)
            val newDate = LocalDate.of(2024, 7, 15)

            viewModel.onDateSelected(newDate)
            advanceUntilIdle()

            assertEquals(newDate, viewModel.uiState.value.selectedDate)
        }

    @Test
    fun `onTimeSelected updates selected time`() =
        runTest {
            viewModel = MapViewModel(sunCalculator, visibilityUseCase)
            val newTime = LocalTime.of(14, 30)

            viewModel.onTimeSelected(newTime)
            advanceUntilIdle()

            assertEquals(newTime, viewModel.uiState.value.selectedTime)
        }

    @Test
    fun `onMapCenterChanged updates map center`() =
        runTest {
            viewModel = MapViewModel(sunCalculator, visibilityUseCase)
            val newCenter = GeoPoint(latitude = 45.0, longitude = 7.0)

            viewModel.onMapCenterChanged(newCenter)
            advanceUntilIdle()

            assertEquals(newCenter, viewModel.uiState.value.mapCenter)
        }

    @Test
    fun `onZoomChanged updates zoom level within bounds`() =
        runTest {
            viewModel = MapViewModel(sunCalculator, visibilityUseCase)

            viewModel.onZoomChanged(15.0)

            assertEquals(15.0, viewModel.uiState.value.zoomLevel, 0.1)
        }

    @Test
    fun `onZoomChanged clamps zoom level to minimum`() =
        runTest {
            viewModel = MapViewModel(sunCalculator, visibilityUseCase)

            viewModel.onZoomChanged(1.0) // Below MIN_ZOOM

            assertEquals(MapUiState.MIN_ZOOM, viewModel.uiState.value.zoomLevel, 0.1)
        }

    @Test
    fun `onZoomChanged clamps zoom level to maximum`() =
        runTest {
            viewModel = MapViewModel(sunCalculator, visibilityUseCase)

            viewModel.onZoomChanged(25.0) // Above MAX_ZOOM

            assertEquals(MapUiState.MAX_ZOOM, viewModel.uiState.value.zoomLevel, 0.1)
        }

    @Test
    fun `onResetToNow updates date and time to current`() =
        runTest {
            viewModel = MapViewModel(sunCalculator, visibilityUseCase)

            // Set a different date/time first
            viewModel.onDateSelected(LocalDate.of(2020, 1, 1))
            viewModel.onTimeSelected(LocalTime.of(0, 0))
            advanceUntilIdle()

            // Reset to now
            viewModel.onResetToNow()
            advanceUntilIdle()

            // Should be close to current date/time
            assertEquals(LocalDate.now(), viewModel.uiState.value.selectedDate)
            // Time might be slightly different, just check it's reasonable
            assertNotNull(viewModel.uiState.value.selectedTime)
        }

    @Test
    fun `sun position is calculated on init`() =
        runTest {
            viewModel = MapViewModel(sunCalculator, visibilityUseCase)
            advanceUntilIdle()

            assertNotNull("Sun position should be calculated", viewModel.uiState.value.sunPosition)
        }

    @Test
    fun `visibility is calculated after sun position`() =
        runTest {
            viewModel = MapViewModel(sunCalculator, visibilityUseCase)
            advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                // Visibility may be calculated
                assertFalse("Should not be loading forever", state.isLoadingVisibility)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `error is set when sun calculation fails`() =
        runTest {
            coEvery { sunCalculator.calculateSunPosition(any(), any()) } throws
                RuntimeException("Calculation failed")

            viewModel = MapViewModel(sunCalculator, visibilityUseCase)
            advanceUntilIdle()

            assertNotNull("Error should be set", viewModel.uiState.value.error)
            assertTrue(
                "Error should contain message",
                viewModel.uiState.value.error!!.contains("failed") ||
                    viewModel.uiState.value.error!!.contains("Calculation"),
            )
        }

    @Test
    fun `onErrorDismissed clears error`() =
        runTest {
            coEvery { sunCalculator.calculateSunPosition(any(), any()) } throws
                RuntimeException("Calculation failed")

            viewModel = MapViewModel(sunCalculator, visibilityUseCase)
            advanceUntilIdle()

            assertNotNull("Error should be set initially", viewModel.uiState.value.error)

            viewModel.onErrorDismissed()

            assertNull("Error should be cleared", viewModel.uiState.value.error)
        }

    @Test
    fun `isSunVisibleWithTerrain returns visibility state`() =
        runTest {
            val visibleResult =
                VisibilityResult.visible(
                    location = GeoPoint.DEFAULT,
                    sunPosition = SunPosition(azimuth = 180.0, elevation = 45.0),
                    horizonAngle = 0.0,
                )
            coEvery { visibilityUseCase.calculateVisibility(any(), any()) } returns
                Result.success(visibleResult)

            viewModel = MapViewModel(sunCalculator, visibilityUseCase)
            advanceUntilIdle()

            assertTrue("Sun should be visible", viewModel.uiState.value.isSunVisibleWithTerrain)
        }

    @Test
    fun `hasVisibilityData returns true when visibility is set`() =
        runTest {
            viewModel = MapViewModel(sunCalculator, visibilityUseCase)
            advanceUntilIdle()

            assertTrue(
                "Should have visibility data after calculation",
                viewModel.uiState.value.hasVisibilityData,
            )
        }

    // ---- Rapid input and debouncing tests ----

    @Test
    fun `rapid map center changes all update state`() =
        runTest {
            viewModel = MapViewModel(sunCalculator, visibilityUseCase)
            advanceUntilIdle()

            val finalCenter = GeoPoint(latitude = 45.0, longitude = 7.0)
            // Simulate rapid panning - many quick center changes
            viewModel.onMapCenterChanged(GeoPoint(latitude = 46.0, longitude = 8.0))
            viewModel.onMapCenterChanged(GeoPoint(latitude = 45.5, longitude = 7.5))
            viewModel.onMapCenterChanged(finalCenter)
            advanceUntilIdle()

            assertEquals(
                "Final map center should be the last one set",
                finalCenter,
                viewModel.uiState.value.mapCenter,
            )
        }

    @Test
    fun `grid update is debounced on rapid zoom changes`() =
        runTest {
            viewModel = MapViewModel(sunCalculator, visibilityUseCase)
            advanceUntilIdle()

            // Rapid zoom changes (within debounce window)
            viewModel.onZoomChanged(13.0)
            advanceTimeBy(100)
            viewModel.onZoomChanged(14.0)
            advanceTimeBy(100)
            viewModel.onZoomChanged(15.0)
            advanceTimeBy(100)

            // At this point only 300ms have passed - debounce is 500ms
            // Grid should not have been recalculated yet for the zoom changes
            assertEquals(15.0, viewModel.uiState.value.zoomLevel, 0.1)

            // Advance past debounce window
            advanceUntilIdle()

            // Now the grid update should have completed
            assertFalse("Grid should not be loading", viewModel.uiState.value.isLoadingGrid)
        }

    @Test
    fun `onAdjustTime advances by specified hours`() =
        runTest {
            viewModel = MapViewModel(sunCalculator, visibilityUseCase)
            advanceUntilIdle()

            val initialTime = viewModel.uiState.value.selectedTime
            val initialDate = viewModel.uiState.value.selectedDate

            viewModel.onAdjustTime(1)
            advanceUntilIdle()

            val newTime = viewModel.uiState.value.selectedTime
            // One hour later (or date rolled over)
            val expected = java.time.LocalDateTime.of(initialDate, initialTime).plusHours(1)
            assertEquals(expected.toLocalTime(), newTime)
        }

    @Test
    fun `onAdjustTime handles day boundary crossing`() =
        runTest {
            viewModel = MapViewModel(sunCalculator, visibilityUseCase)
            // Set time to 23:30
            viewModel.onTimeSelected(LocalTime.of(23, 30))
            advanceUntilIdle()

            val dateBefore = viewModel.uiState.value.selectedDate
            viewModel.onAdjustTime(1)
            advanceUntilIdle()

            val dateAfter = viewModel.uiState.value.selectedDate
            val timeAfter = viewModel.uiState.value.selectedTime

            assertEquals("Date should advance by one day", dateBefore.plusDays(1), dateAfter)
            assertEquals("Time should be 00:30", LocalTime.of(0, 30), timeAfter)
        }

    @Test
    fun `visibility failure does not set error in ui state`() =
        runTest {
            // Visibility failures are non-critical per the ViewModel design
            coEvery { visibilityUseCase.calculateVisibility(any(), any()) } returns
                Result.failure(RuntimeException("Visibility failed"))

            viewModel = MapViewModel(sunCalculator, visibilityUseCase)
            advanceUntilIdle()

            // Sun position should still work
            assertNotNull("Sun position should be set", viewModel.uiState.value.sunPosition)
            // Error should NOT be set (visibility failure is non-critical)
            assertNull("Error should not be set for visibility failure", viewModel.uiState.value.error)
        }

    @Test
    fun `sun position updates sunrise and sunset times`() =
        runTest {
            coEvery { sunCalculator.calculateSunrise(any(), any()) } returns LocalTime.of(5, 45)
            coEvery { sunCalculator.calculateSunset(any(), any()) } returns LocalTime.of(21, 15)

            viewModel = MapViewModel(sunCalculator, visibilityUseCase)
            advanceUntilIdle()

            assertEquals(LocalTime.of(5, 45), viewModel.uiState.value.sunriseTime)
            assertEquals(LocalTime.of(21, 15), viewModel.uiState.value.sunsetTime)
        }

    @Test
    fun `grid not calculated when sun below horizon`() =
        runTest {
            coEvery { sunCalculator.calculateSunPosition(any(), any()) } returns
                SunPosition(azimuth = 0.0, elevation = -10.0)

            viewModel = MapViewModel(sunCalculator, visibilityUseCase)
            advanceUntilIdle()

            assertNull("Grid should be null when sun is below horizon", viewModel.uiState.value.visibilityGrid)
        }

    @Test
    fun `grid not calculated at low zoom levels`() =
        runTest {
            viewModel = MapViewModel(sunCalculator, visibilityUseCase)
            advanceUntilIdle()

            // Set zoom below the grid threshold (MIN_ZOOM_FOR_GRID = 12.0)
            viewModel.onZoomChanged(8.0)
            advanceUntilIdle()

            assertNull("Grid should be null at low zoom", viewModel.uiState.value.visibilityGrid)
        }

    // ---- Timezone conversion tests ----

    @Test
    fun `localToUtc converts CET to UTC correctly`() {
        withTimeZone("Europe/Zurich") {
            // January (standard time, no DST): CET = UTC+1
            val local = LocalDateTime.of(2024, 1, 15, 14, 0)
            val utc = MapViewModel.localToUtc(local)

            assertEquals(
                "14:00 CET should be 13:00 UTC",
                LocalDateTime.of(2024, 1, 15, 13, 0),
                utc,
            )
        }
    }

    @Test
    fun `localToUtc handles DST correctly`() {
        withTimeZone("Europe/Zurich") {
            // July (DST active): CEST = UTC+2
            val local = LocalDateTime.of(2024, 7, 15, 14, 0)
            val utc = MapViewModel.localToUtc(local)

            assertEquals(
                "14:00 CEST should be 12:00 UTC",
                LocalDateTime.of(2024, 7, 15, 12, 0),
                utc,
            )
        }
    }

    @Test
    fun `utcTimeToLocal converts UTC to CET correctly`() {
        withTimeZone("Europe/Zurich") {
            // January (standard time): CET = UTC+1
            val utcTime = LocalTime.of(5, 30)
            val utcDate = LocalDate.of(2024, 1, 15)
            val localTime = MapViewModel.utcTimeToLocal(utcTime, utcDate)

            assertEquals(
                "05:30 UTC should be 06:30 CET",
                LocalTime.of(6, 30),
                localTime,
            )
        }
    }

    @Test
    fun `utcTimeToLocal handles day boundary`() {
        withTimeZone("Europe/Zurich") {
            // 23:30 UTC → 00:30 CET next day
            val utcTime = LocalTime.of(23, 30)
            val utcDate = LocalDate.of(2024, 1, 15)
            val localTime = MapViewModel.utcTimeToLocal(utcTime, utcDate)

            assertEquals(
                "23:30 UTC should be 00:30 CET (next day)",
                LocalTime.of(0, 30),
                localTime,
            )
        }
    }

    @Test
    fun `localToUtc is identity in UTC timezone`() {
        withTimeZone("UTC") {
            val local = LocalDateTime.of(2024, 6, 15, 12, 0)
            val utc = MapViewModel.localToUtc(local)

            assertEquals("UTC → UTC should be identity", local, utc)
        }
    }

    @Test
    fun `terrain sunshine times are computed after sun position update`() =
        runTest {
            coEvery {
                visibilityUseCase.calculateTerrainSunriseSunset(any(), any())
            } returns Result.success(Pair(LocalTime.of(7, 15), LocalTime.of(19, 45)))

            viewModel = MapViewModel(sunCalculator, visibilityUseCase)
            advanceUntilIdle()

            assertEquals(
                "First sunshine time should be set",
                LocalTime.of(7, 15),
                viewModel.uiState.value.firstSunshineTime,
            )
            assertEquals(
                "Last sunshine time should be set",
                LocalTime.of(19, 45),
                viewModel.uiState.value.lastSunshineTime,
            )
        }

    @Test
    fun `terrain sunshine times are null when calculation fails`() =
        runTest {
            coEvery {
                visibilityUseCase.calculateTerrainSunriseSunset(any(), any())
            } returns Result.failure(RuntimeException("Calculation failed"))

            viewModel = MapViewModel(sunCalculator, visibilityUseCase)
            advanceUntilIdle()

            assertNull(
                "First sunshine time should be null on failure",
                viewModel.uiState.value.firstSunshineTime,
            )
            assertNull(
                "Last sunshine time should be null on failure",
                viewModel.uiState.value.lastSunshineTime,
            )
        }

    @Test
    fun `sun calculator receives UTC datetime not local`() =
        runTest {
            withTimeZone("Europe/Zurich") {
                val dateTimeSlot = slot<LocalDateTime>()
                coEvery {
                    sunCalculator.calculateSunPosition(any(), capture(dateTimeSlot))
                } returns SunPosition(azimuth = 180.0, elevation = 45.0)

                viewModel = MapViewModel(sunCalculator, visibilityUseCase)

                // Set a specific local time: 14:00 CET in January = 13:00 UTC
                viewModel.onDateSelected(LocalDate.of(2024, 1, 15))
                viewModel.onTimeSelected(LocalTime.of(14, 0))
                advanceUntilIdle()

                coVerify { sunCalculator.calculateSunPosition(any(), any()) }
                val capturedDateTime = dateTimeSlot.captured

                assertEquals(
                    "Calculator should receive UTC hour (13), not local (14)",
                    13,
                    capturedDateTime.hour,
                )
            }
        }

    @Test
    fun `sunrise sunset times are converted from UTC to local for display`() =
        runTest {
            withTimeZone("Europe/Zurich") {
                // Mock returns UTC times
                coEvery { sunCalculator.calculateSunrise(any(), any()) } returns
                    LocalTime.of(5, 0) // 5:00 UTC
                coEvery { sunCalculator.calculateSunset(any(), any()) } returns
                    LocalTime.of(17, 0) // 17:00 UTC

                viewModel = MapViewModel(sunCalculator, visibilityUseCase)

                // Set a January date (CET = UTC+1)
                viewModel.onDateSelected(LocalDate.of(2024, 1, 15))
                viewModel.onTimeSelected(LocalTime.of(12, 0))
                advanceUntilIdle()

                assertEquals(
                    "Sunrise 05:00 UTC should display as 06:00 CET",
                    LocalTime.of(6, 0),
                    viewModel.uiState.value.sunriseTime,
                )
                assertEquals(
                    "Sunset 17:00 UTC should display as 18:00 CET",
                    LocalTime.of(18, 0),
                    viewModel.uiState.value.sunsetTime,
                )
            }
        }

    /**
     * Runs [block] with the JVM default timezone temporarily set to [zoneId],
     * restoring the previous timezone even if the block throws.
     */
    private fun <T> withTimeZone(
        zoneId: String,
        block: () -> T,
    ): T {
        val previous = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(zoneId))
        try {
            return block()
        } finally {
            TimeZone.setDefault(previous)
        }
    }
}
