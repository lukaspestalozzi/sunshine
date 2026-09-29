package com.sunshine.app.map

import androidx.lifecycle.SavedStateHandle
import com.sunshine.app.elevation.DemTile
import com.sunshine.app.elevation.ElevationRepository
import com.sunshine.app.elevation.TileCache
import com.sunshine.core.AZIMUTH_COUNT
import com.sunshine.core.DEFAULT_LOCATION
import com.sunshine.core.GeoPoint
import com.sunshine.core.HeightTile
import com.sunshine.core.HorizonProfile
import com.sunshine.core.MapArea
import com.sunshine.core.ShadeGrid
import com.sunshine.core.SunPeriods
import com.sunshine.core.SunPosition
import com.sunshine.core.SunShadeSweep
import com.sunshine.core.Sunshine
import com.sunshine.core.TileKey
import com.sunshine.core.sunPosition
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {
    private val isOnline = MutableStateFlow(true)
    private val clock = MutableClock(Instant.parse("2025-12-21T08:47:31Z"), ZURICH)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starts at the default location with zoom 10`() {
        val viewModel = newViewModel()

        assertEquals(CameraState(center = DEFAULT_LOCATION, zoom = 10.0), viewModel.camera.value)
    }

    @Test
    fun `a moved camera is restored from saved state, as after a rotation`() {
        val savedState = SavedStateHandle()
        val moved = CameraState(center = GeoPoint(46.6863, 7.8632), zoom = 13.5)
        newViewModel(savedState).onCameraMoved(moved)

        val recreated = newViewModel(savedState)

        assertEquals(moved, recreated.camera.value)
    }

    @Test
    fun `isOffline follows the network monitor`() =
        runTest {
            val viewModel = newViewModel()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.isOffline.collect {} }

            isOnline.value = false
            assertTrue(viewModel.isOffline.value)

            isOnline.value = true
            assertFalse(viewModel.isOffline.value)
        }

    @Test
    fun `selected time starts at the current time truncated to the minute`() {
        val viewModel = newViewModel()

        assertEquals(ZonedDateTime.of(2025, 12, 21, 9, 47, 0, 0, ZURICH), viewModel.selectedTime.value)
    }

    @Test
    fun `selected time is restored from saved state, as after a rotation`() {
        val savedState = SavedStateHandle()
        newViewModel(savedState).onDateSelected(LocalDate.of(2025, 6, 21))

        val recreated = newViewModel(savedState)

        assertEquals(ZonedDateTime.of(2025, 6, 21, 9, 47, 0, 0, ZURICH), recreated.selectedTime.value)
    }

    @Test
    fun `now returns to the current time and does not follow the clock afterwards`() {
        val viewModel = newViewModel()
        viewModel.onDateSelected(LocalDate.of(2025, 6, 21))

        viewModel.onNowClicked()
        val atNow = viewModel.selectedTime.value
        clock.instant = clock.instant.plusSeconds(5 * 60)

        assertEquals(ZonedDateTime.of(2025, 12, 21, 9, 47, 0, 0, ZURICH), atNow)
        assertEquals(atNow, viewModel.selectedTime.value)
    }

    @Test
    fun `changing the date keeps the wall-clock time`() {
        val viewModel = newViewModel()

        viewModel.onDateSelected(LocalDate.of(2025, 6, 21))

        assertEquals(ZonedDateTime.of(2025, 6, 21, 9, 47, 0, 0, ZURICH), viewModel.selectedTime.value)
    }

    @Test
    fun `the slider snaps to the 5-minute grid of the selected day`() {
        val viewModel = newViewModel()

        viewModel.onSliderMoved(722.4f)

        assertEquals(ZonedDateTime.of(2025, 12, 21, 12, 0, 0, 0, ZURICH), viewModel.selectedTime.value)
    }

    @Test
    fun `sun info follows the selected location and time`() =
        runTest {
            val viewModel = newViewModel(computeDispatcher = UnconfinedTestDispatcher(testScheduler))
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.sun.collect {} }

            viewModel.onSliderMoved(12 * 60f)
            viewModel.onCameraMoved(CameraState(center = GeoPoint(46.6863, 7.8632), zoom = 10.0))
            val interlaken = viewModel.sun.value
            viewModel.onCameraMoved(CameraState(center = GeoPoint(35.6762, 139.6503), zoom = 10.0))
            val tokyo = viewModel.sun.value

            assertNotNull(interlaken)
            assertNotNull(tokyo)
            assertEquals(173.5, interlaken.position.azimuth, 0.2)
            assertEquals(LocalDate.of(2025, 12, 21), interlaken.day.sunrise?.toLocalDate())
            assertNotEquals(interlaken.position.azimuth, tokyo.position.azimuth)
        }

    @Test
    fun `elevation is loading until its tile arrives, then known`() =
        runTest {
            val tile = CompletableDeferred<ByteArray?>()
            val viewModel =
                newViewModel(repository = repository { tile.await() }, computeDispatcher = UnconfinedTestDispatcher(testScheduler))
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.elevation.collect {} }

            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            assertEquals(ElevationState.Loading, viewModel.elevation.value)

            tile.complete(heightBytes(568))
            assertEquals(ElevationState.Known(568.0), viewModel.elevation.value)
        }

    @Test
    fun `elevation within tiles in memory is known without loading`() =
        runTest {
            val viewModel =
                newViewModel(repository = repository { heightBytes(568) }, computeDispatcher = UnconfinedTestDispatcher(testScheduler))
            val states = mutableListOf<ElevationState>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.elevation.collect { states += it } }
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            states.clear()

            viewModel.onCameraMoved(CameraState(center = GeoPoint(46.6870, 7.8640), zoom = 12.0))

            // Equal values are not re-emitted by a StateFlow, so "no Loading" is what can be observed.
            assertFalse(ElevationState.Loading in states)
            assertEquals(ElevationState.Known(568.0), viewModel.elevation.value)
        }

    @Test
    fun `elevation is unknown when its tile cannot be obtained`() =
        runTest {
            isOnline.value = false
            val viewModel = newViewModel(repository = repository { null }, computeDispatcher = UnconfinedTestDispatcher(testScheduler))
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.elevation.collect {} }

            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))

            assertEquals(ElevationState.Unknown, viewModel.elevation.value)
        }

    @Test
    fun `unknown elevation is loaded again when the network returns`() =
        runTest {
            isOnline.value = false
            var tile: ByteArray? = null
            val viewModel = newViewModel(repository = repository { tile }, computeDispatcher = UnconfinedTestDispatcher(testScheduler))
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.elevation.collect {} }
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            assertEquals(ElevationState.Unknown, viewModel.elevation.value)

            tile = heightBytes(568)
            isOnline.value = true

            assertEquals(ElevationState.Known(568.0), viewModel.elevation.value)
        }

    @Test
    fun `a previous location's elevation is never shown for the new location`() =
        runTest {
            val interlakenTile = CompletableDeferred<ByteArray?>()
            val repository =
                repository { key -> if (key == INTERLAKEN_TILE) interlakenTile.await() else heightBytes(1000) }
            val viewModel = newViewModel(repository = repository, computeDispatcher = UnconfinedTestDispatcher(testScheduler))
            val states = mutableListOf<ElevationState>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.elevation.collect { states += it } }
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))

            viewModel.onCameraMoved(CameraState(center = GeoPoint(46.0, 9.0), zoom = 12.0))
            interlakenTile.complete(heightBytes(568))

            assertFalse(ElevationState.Known(568.0) in states)
            assertEquals(ElevationState.Known(1000.0), viewModel.elevation.value)
        }

    @Test
    fun `zooming without moving does not retry a failed tile`() =
        runTest {
            var fetches = 0
            val repository =
                repository {
                    fetches++
                    null
                }
            val viewModel = newViewModel(repository = repository, computeDispatcher = UnconfinedTestDispatcher(testScheduler))
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.elevation.collect {} }
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            val fetchesAfterMove = fetches

            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 14.0))

            assertEquals(fetchesAfterMove, fetches)
            assertEquals(ElevationState.Unknown, viewModel.elevation.value)
        }

    @Test
    fun `a location whose tile never arrives does not block the next location`() =
        runTest {
            val repository =
                repository { key -> if (key == INTERLAKEN_TILE) awaitCancellation() else heightBytes(1000) }
            val viewModel = newViewModel(repository = repository, computeDispatcher = UnconfinedTestDispatcher(testScheduler))
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.elevation.collect {} }
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))

            viewModel.onCameraMoved(CameraState(center = GeoPoint(46.0, 9.0), zoom = 12.0))

            assertEquals(ElevationState.Known(1000.0), viewModel.elevation.value)
        }

    @Test
    fun `sunshine shows loading until the horizon is computed, then the periods`() =
        runTest {
            val profile = CompletableDeferred<HorizonProfile?>()
            val viewModel = newViewModel(horizon = { profile.await() }, computeDispatcher = UnconfinedTestDispatcher(testScheduler))
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.sunshine.collect {} }

            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            advanceTimeBy(SETTLE_MILLIS)
            assertEquals(SunshineUiState.Loading, viewModel.sunshine.value)

            profile.complete(horizonOf(10.0))
            val ready = viewModel.sunshine.value as SunshineUiState.Ready
            assertEquals(1, (ready.periods as SunPeriods.Known).periods.size)
        }

    @Test
    fun `a previous location's periods are never shown for the new location`() =
        runTest {
            val interlaken = CompletableDeferred<HorizonProfile?>()
            val viewModel =
                newViewModel(
                    horizon = { point -> if (point == INTERLAKEN) interlaken.await() else horizonOf(90.0) },
                    computeDispatcher = UnconfinedTestDispatcher(testScheduler),
                )
            val states = mutableListOf<SunshineUiState>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.sunshine.collect { states += it } }
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            advanceTimeBy(SETTLE_MILLIS)

            viewModel.onCameraMoved(CameraState(center = GeoPoint(46.0, 9.0), zoom = 12.0))
            interlaken.complete(horizonOf(10.0))
            advanceTimeBy(SETTLE_MILLIS)

            assertFalse(states.any { it is SunshineUiState.Ready && it.periods != SunPeriods.Known(emptyList()) })
            assertEquals(SunPeriods.Known(emptyList()), (viewModel.sunshine.value as SunshineUiState.Ready).periods)
        }

    @Test
    fun `no horizon is computed while the camera moves more often than every 300 ms`() =
        runTest {
            val requested = mutableListOf<GeoPoint>()
            val viewModel =
                newViewModel(
                    horizon = { point ->
                        requested += point
                        horizonOf(10.0)
                    },
                    computeDispatcher = UnconfinedTestDispatcher(testScheduler),
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.sunshine.collect {} }

            repeat(10) {
                viewModel.onCameraMoved(CameraState(center = GeoPoint(46.6863, 7.8632 + it * 0.001), zoom = 12.0))
                advanceTimeBy(200)
            }
            assertEquals(emptyList<GeoPoint>(), requested)

            advanceTimeBy(SETTLE_MILLIS)
            assertEquals(listOf(GeoPoint(46.6863, 7.8632 + 9 * 0.001)), requested)
        }

    @Test
    fun `a date change recomputes the periods but not the horizon`() =
        runTest {
            var computed = 0
            val viewModel =
                newViewModel(
                    horizon = {
                        computed++
                        horizonOf(10.0)
                    },
                    computeDispatcher = UnconfinedTestDispatcher(testScheduler),
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.sunshine.collect {} }
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            advanceTimeBy(SETTLE_MILLIS)
            val winter = (viewModel.sunshine.value as SunshineUiState.Ready).periods

            viewModel.onDateSelected(LocalDate.of(2025, 6, 21))

            val summer = (viewModel.sunshine.value as SunshineUiState.Ready).periods
            assertNotEquals(winter, summer)
            assertEquals(1, computed)
        }

    @Test
    fun `an incomplete horizon is computed again when the network returns, a complete one is not`() =
        runTest {
            var complete = false
            var computed = 0
            val viewModel =
                newViewModel(
                    horizon = {
                        computed++
                        horizonOf(10.0, complete)
                    },
                    computeDispatcher = UnconfinedTestDispatcher(testScheduler),
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.sunshine.collect {} }
            isOnline.value = false
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            advanceTimeBy(SETTLE_MILLIS)
            assertEquals(SunPeriods.Unknown, (viewModel.sunshine.value as SunshineUiState.Ready).periods)

            complete = true
            isOnline.value = true
            advanceTimeBy(SETTLE_MILLIS)
            assertEquals(2, computed)
            assertEquals(1, ((viewModel.sunshine.value as SunshineUiState.Ready).periods as SunPeriods.Known).periods.size)

            isOnline.value = false
            isOnline.value = true
            advanceTimeBy(SETTLE_MILLIS)
            assertEquals(2, computed)
        }

    @Test
    fun `the sunshine state follows the selected time`() =
        runTest {
            val viewModel = newViewModel(horizon = { horizonOf(10.0) }, computeDispatcher = UnconfinedTestDispatcher(testScheduler))
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.sunshine.collect {} }
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            advanceTimeBy(SETTLE_MILLIS)

            viewModel.onSliderMoved(12 * 60f)
            assertEquals(Sunshine.SUN, (viewModel.sunshine.value as SunshineUiState.Ready).atSelectedTime)

            viewModel.onSliderMoved(16 * 60f)
            assertEquals(Sunshine.SHADE, (viewModel.sunshine.value as SunshineUiState.Ready).atSelectedTime)
        }

    @Test
    fun `an unknown ground height makes the sunshine unknown`() =
        runTest {
            val viewModel = newViewModel(horizon = { null }, computeDispatcher = UnconfinedTestDispatcher(testScheduler))
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.sunshine.collect {} }

            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            advanceTimeBy(SETTLE_MILLIS)

            assertEquals(SunshineUiState.Ready(INTERLAKEN, SunPeriods.Unknown, Sunshine.UNKNOWN), viewModel.sunshine.value)
        }

    @Test
    fun `a result for another location than the camera centre is shown as loading`() {
        val ready = SunshineUiState.Ready(INTERLAKEN, SunPeriods.Unknown, Sunshine.UNKNOWN)

        assertEquals(ready, ready.at(INTERLAKEN))
        assertEquals(SunshineUiState.Loading, ready.at(GeoPoint(46.0, 9.0)))
        assertEquals(SunshineUiState.Loading, SunshineUiState.Loading.at(INTERLAKEN))
    }

    @Test
    fun `the overlay is off at start and nothing is computed`() =
        runTest {
            val areas = mutableListOf<MapArea>()
            val viewModel = overlayViewModel(areas)

            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            advanceTimeBy(SETTLE_MILLIS)

            assertEquals(OverlayUiState.Off, viewModel.overlay.value)
            assertEquals(emptyList<MapArea>(), areas)
        }

    @Test
    fun `below zoom 11 the overlay is zoomed out and nothing is computed`() =
        runTest {
            val areas = mutableListOf<MapArea>()
            val viewModel = overlayViewModel(areas)
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 10.5))

            viewModel.onOverlayToggled()
            advanceTimeBy(SETTLE_MILLIS)

            assertEquals(OverlayUiState.ZoomedOut, viewModel.overlay.value)
            assertEquals(emptyList<MapArea>(), areas)
        }

    @Test
    fun `switching the overlay on computes the visible area`() =
        runTest {
            val grid = CompletableDeferred<Unit>()
            val areas = mutableListOf<MapArea>()
            val viewModel = overlayViewModel(areas, before = { grid.await() })
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))

            viewModel.onOverlayToggled()
            assertEquals(OverlayUiState.Computing(kept = null), viewModel.overlay.value)

            advanceTimeBy(SETTLE_MILLIS)
            grid.complete(Unit)
            val ready = viewModel.overlay.value as OverlayUiState.Ready
            assertEquals(listOf(MapArea(INTERLAKEN, 12.0, MAP_WIDTH, MAP_HEIGHT)), areas)
            assertEquals(viewModel.selectedTime.value, ready.time)
        }

    @Test
    fun `after a pan the previous overlay is kept until the camera has rested`() =
        runTest {
            val areas = mutableListOf<MapArea>()
            val viewModel = overlayViewModel(areas)
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            viewModel.onOverlayToggled()
            advanceTimeBy(SETTLE_MILLIS)
            val previous = viewModel.overlay.value as OverlayUiState.Ready

            viewModel.onCameraMoved(CameraState(center = GeoPoint(46.69, 7.87), zoom = 12.0))
            assertEquals(OverlayUiState.Computing(kept = previous), viewModel.overlay.value)
            advanceTimeBy(SETTLE_MILLIS - 2)
            assertEquals(1, areas.size)

            advanceTimeBy(2)
            assertEquals(GeoPoint(46.69, 7.87), (viewModel.overlay.value as OverlayUiState.Ready).grid.area.center)
            assertEquals(2, areas.size)
        }

    @Test
    fun `a time change keeps the previous overlay until the new one is ready`() =
        runTest {
            val gate = MutableStateFlow(true)
            val viewModel = overlayViewModel(mutableListOf(), before = { gate.first { it } })
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            viewModel.onOverlayToggled()
            advanceTimeBy(SETTLE_MILLIS)
            val previous = viewModel.overlay.value as OverlayUiState.Ready
            gate.value = false

            viewModel.onSliderMoved(15 * 60f)

            assertEquals(OverlayUiState.Computing(kept = previous), viewModel.overlay.value)
            gate.value = true
            assertEquals(ZonedDateTime.of(2025, 12, 21, 15, 0, 0, 0, ZURICH), (viewModel.overlay.value as OverlayUiState.Ready).time)
        }

    @Test
    fun `a date change keeps the previous overlay until the new one is ready`() =
        runTest {
            val gate = MutableStateFlow(true)
            val viewModel = overlayViewModel(mutableListOf(), before = { gate.first { it } })
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            viewModel.onOverlayToggled()
            advanceTimeBy(SETTLE_MILLIS)
            val previous = viewModel.overlay.value as OverlayUiState.Ready
            gate.value = false

            viewModel.onDateSelected(LocalDate.of(2025, 6, 21))

            assertEquals(OverlayUiState.Computing(kept = previous), viewModel.overlay.value)
            gate.value = true
            assertEquals(ZonedDateTime.of(2025, 6, 21, 12, 0, 0, 0, ZURICH), (viewModel.overlay.value as OverlayUiState.Ready).time)
        }

    @Test
    fun `slider positions during a computation are dropped and the last one is computed`() =
        runTest {
            val suns = mutableListOf<SunPosition>()
            val gate = MutableStateFlow(true)
            val viewModel = overlayViewModel(mutableListOf(), suns, before = { gate.first { it } })
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            viewModel.onOverlayToggled()
            advanceTimeBy(SETTLE_MILLIS)
            gate.value = false
            suns.clear()

            for (minutes in listOf(600f, 660f, 720f, 780f)) viewModel.onSliderMoved(minutes)
            gate.value = true

            val ready = viewModel.overlay.value as OverlayUiState.Ready
            assertEquals(ZonedDateTime.of(2025, 12, 21, 13, 0, 0, 0, ZURICH), ready.time)
            assertEquals(ready.grid.sun, suns.last())
        }

    @Test
    fun `slider positions arriving while a sweep cannot stop yet start no extra sweeps`() =
        runTest {
            val gate = MutableStateFlow(true)
            var sweeps = 0
            val viewModel =
                overlayViewModel(
                    mutableListOf(),
                    before = {
                        sweeps++
                        // Like the CPU-bound sweep, which only stops between chunks.
                        withContext(NonCancellable) { gate.first { it } }
                    },
                )
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            viewModel.onOverlayToggled()
            advanceTimeBy(SETTLE_MILLIS)
            gate.value = false
            sweeps = 0

            for (minutes in listOf(600f, 660f, 720f, 780f)) viewModel.onSliderMoved(minutes)
            gate.value = true

            assertEquals(ZonedDateTime.of(2025, 12, 21, 13, 0, 0, 0, ZURICH), (viewModel.overlay.value as OverlayUiState.Ready).time)
            // 10:00 is running and cannot stop yet; 11:00 and 12:00 are dropped in favour of 13:00.
            assertEquals(2, sweeps)
        }

    @Test
    fun `the overlay is computed again when the network returns only if some cell is unknown`() =
        runTest {
            val areas = mutableListOf<MapArea>()
            val viewModel = overlayViewModel(areas)
            isOnline.value = false
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            viewModel.onOverlayToggled()
            advanceTimeBy(SETTLE_MILLIS)
            assertTrue((viewModel.overlay.value as OverlayUiState.Ready).grid.hasUnknown)

            isOnline.value = true
            advanceTimeBy(SETTLE_MILLIS)
            assertEquals(2, areas.size)
            assertFalse((viewModel.overlay.value as OverlayUiState.Ready).grid.hasUnknown)

            isOnline.value = false
            isOnline.value = true
            advanceTimeBy(SETTLE_MILLIS)
            assertEquals(2, areas.size)
        }

    @Test
    fun `the overlay switch is restored from saved state, as after a rotation`() {
        val savedState = SavedStateHandle()
        newViewModel(savedState).onOverlayToggled()

        assertTrue(newViewModel(savedState).isOverlayOn.value)
        assertFalse(newViewModel().isOverlayOn.value)
    }

    @Test
    fun `the overlay mode is sun and shade at launch and restored from saved state, as after a rotation`() {
        val savedState = SavedStateHandle()
        assertEquals(OverlayMode.SUN_AND_SHADE, newViewModel(savedState).overlayMode.value)

        newViewModel(savedState).onOverlayModeSelected(OverlayMode.SUN_HOURS)

        assertEquals(OverlayMode.SUN_HOURS, newViewModel(savedState).overlayMode.value)
        assertEquals(OverlayMode.SUN_AND_SHADE, newViewModel().overlayMode.value)
    }

    @Test
    fun `switching the mode neither cancels nor restarts the day`() =
        runTest {
            val suns = mutableListOf<SunPosition>()
            val gate = MutableStateFlow(false)
            // 12:00, 12:05 and 11:55 are computed; 12:10 waits.
            val viewModel = dayViewModel(suns, before = { if (suns.size == 3) gate.first { it } })
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            viewModel.onOverlayToggled()
            advanceTimeBy(SETTLE_MILLIS)
            assertEquals(3, suns.size)

            viewModel.onOverlayModeSelected(OverlayMode.SUN_HOURS)
            runCurrent()
            viewModel.onOverlayModeSelected(OverlayMode.SUN_AND_SHADE)
            runCurrent()
            gate.value = true
            advanceUntilIdle()

            assertEquals(288, suns.size)
            assertEquals(288, suns.toSet().size)
        }

    @Test
    fun `the heatmap is off in sun and shade or while the overlay is off, and zoomed out below 11`() =
        runTest {
            val viewModel = dayViewModel(mutableListOf())
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 10.5))
            assertEquals(HeatmapUiState.Off, viewModel.heatmap.value)

            viewModel.onOverlayModeSelected(OverlayMode.SUN_HOURS)
            assertEquals(HeatmapUiState.Off, viewModel.heatmap.value)

            viewModel.onOverlayToggled()
            assertEquals(HeatmapUiState.ZoomedOut, viewModel.heatmap.value)

            viewModel.onOverlayModeSelected(OverlayMode.SUN_AND_SHADE)
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            advanceUntilIdle()
            assertEquals(HeatmapUiState.Off, viewModel.heatmap.value)
        }

    @Test
    fun `in sun hours the heatmap is computing until the day is complete, then ready with the day's counts`() =
        runTest {
            val suns = mutableListOf<SunPosition>()
            val gate = MutableStateFlow(false)
            val viewModel = dayViewModel(suns, before = { if (suns.size == 3) gate.first { it } })
            viewModel.onOverlayModeSelected(OverlayMode.SUN_HOURS)
            isOnline.value = false
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            viewModel.onOverlayToggled()
            advanceTimeBy(SETTLE_MILLIS)
            assertEquals(HeatmapUiState.Computing(kept = null), viewModel.heatmap.value)

            gate.value = true
            advanceUntilIdle()

            // Offline, every cell is unknown at every step.
            val ready = viewModel.heatmap.value as HeatmapUiState.Ready
            assertEquals(MapArea(INTERLAKEN, 12.0, MAP_WIDTH, MAP_HEIGHT), ready.hours.area)
            assertEquals(LocalDate.of(2025, 12, 21), ready.date)
            assertEquals(288, ready.hours.steps)
            assertEquals(setOf<Short>(288), ready.hours.unknown.toSet())
            // Day length at Interlaken on 21 December: 8 h 33 min, 18 bands.
            assertEquals(18, ready.bands.count)
            assertEquals(ready.hours.width * ready.hours.height, ready.image.pixels.size)
        }

    @Test
    fun `a time change leaves the heatmap unchanged and counts nothing again`() =
        runTest {
            val logged = mutableListOf<String>()
            val viewModel = sunHoursViewModel(mutableListOf(), log = { logged += it })
            val ready = viewModel.heatmap.value as HeatmapUiState.Ready

            viewModel.onSliderMoved(14 * 60f)
            advanceUntilIdle()

            assertEquals(ready, viewModel.heatmap.value)
            assertEquals(1, logged.count { it.startsWith("Sun hours") }, "$logged")
        }

    @Test
    fun `after a pan the previous heatmap is kept until the new day is complete`() =
        runTest {
            val gate = MutableStateFlow(true)
            val viewModel = sunHoursViewModel(mutableListOf(), before = { gate.first { it } })
            val previous = viewModel.heatmap.value as HeatmapUiState.Ready
            gate.value = false

            viewModel.onCameraMoved(CameraState(center = GeoPoint(46.69, 7.87), zoom = 12.0))
            advanceTimeBy(SETTLE_MILLIS)
            assertEquals(HeatmapUiState.Computing(kept = previous), viewModel.heatmap.value)

            gate.value = true
            advanceUntilIdle()
            assertEquals(GeoPoint(46.69, 7.87), (viewModel.heatmap.value as HeatmapUiState.Ready).hours.area.center)
        }

    @Test
    fun `after a date change the previous heatmap is kept until the new day is complete`() =
        runTest {
            val gate = MutableStateFlow(true)
            val viewModel = sunHoursViewModel(mutableListOf(), before = { gate.first { it } })
            val previous = viewModel.heatmap.value as HeatmapUiState.Ready
            gate.value = false

            viewModel.onDateSelected(LocalDate.of(2025, 12, 22))
            advanceTimeBy(SETTLE_MILLIS)
            assertEquals(HeatmapUiState.Computing(kept = previous), viewModel.heatmap.value)

            gate.value = true
            advanceUntilIdle()
            assertEquals(LocalDate.of(2025, 12, 22), (viewModel.heatmap.value as HeatmapUiState.Ready).date)
        }

    @Test
    fun `switching back to a complete cached day shows its heatmap at once, without computing a grid`() =
        runTest {
            val suns = mutableListOf<SunPosition>()
            val viewModel = sunHoursViewModel(suns)
            val first = viewModel.heatmap.value as HeatmapUiState.Ready
            viewModel.onDateSelected(LocalDate.of(2025, 12, 22))
            advanceUntilIdle()
            suns.clear()

            viewModel.onDateSelected(LocalDate.of(2025, 12, 21))

            assertEquals(first.hours, (viewModel.heatmap.value as HeatmapUiState.Ready).hours)
            advanceUntilIdle()
            assertEquals(emptyList<SunPosition>(), suns)
        }

    @Test
    fun `switching to sun hours with a complete day counts it once, and switching back and forth is at once`() =
        runTest {
            val logged = mutableListOf<String>()
            val viewModel = dayViewModel(mutableListOf(), log = { logged += it })
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            viewModel.onOverlayToggled()
            advanceUntilIdle()
            assertTrue(logged.none { it.startsWith("Sun hours") }, "$logged")

            viewModel.onOverlayModeSelected(OverlayMode.SUN_HOURS)
            advanceUntilIdle()
            val ready = viewModel.heatmap.value as HeatmapUiState.Ready
            viewModel.onOverlayModeSelected(OverlayMode.SUN_AND_SHADE)
            assertEquals(HeatmapUiState.Off, viewModel.heatmap.value)
            viewModel.onOverlayModeSelected(OverlayMode.SUN_HOURS)

            assertEquals(ready, viewModel.heatmap.value)
            assertEquals(1, logged.count { it.startsWith("Sun hours") }, "$logged")
        }

    @Test
    fun `a day computed anew after a reconnect is counted anew`() =
        runTest {
            isOnline.value = false
            val viewModel = sunHoursViewModel(mutableListOf())
            assertEquals(setOf<Short>(288), (viewModel.heatmap.value as HeatmapUiState.Ready).hours.unknown.toSet())

            isOnline.value = true
            advanceUntilIdle()

            assertEquals(setOf<Short>(0), (viewModel.heatmap.value as HeatmapUiState.Ready).hours.unknown.toSet())
        }

    @Test
    fun `debug builds log the overlay's agreement with the point tracer once it has stayed`() =
        runTest {
            val logged = mutableListOf<String>()
            val viewModel =
                MapViewModel(
                    SavedStateHandle(),
                    isOnline,
                    clock,
                    repository { heightBytes(568) },
                    { horizonOf(-1.0) },
                    { area, sun -> flatGrid(area, sun, available = true) },
                    UnconfinedTestDispatcher(testScheduler),
                    log = { logged += it },
                    checkOverlayAgreement = true,
                )
            viewModel.onMapSizeChanged(MAP_WIDTH, MAP_HEIGHT)
            viewModel.onSliderMoved(12 * 60f)
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            viewModel.onOverlayToggled()
            advanceTimeBy(SETTLE_MILLIS + 3_000)

            // Flat terrain at noon is sun everywhere, as is a -1° horizon: all 200 agree.
            assertTrue(logged.any { it.startsWith("Overlay agreement with the point tracer: 200 of 200") }, "$logged")
        }

    @Test
    fun `after the selected time is ready, the day continues in the background`() =
        runTest {
            val suns = mutableListOf<SunPosition>()
            val viewModel = dayViewModel(suns)
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))

            viewModel.onOverlayToggled()
            advanceTimeBy(SETTLE_MILLIS)

            assertEquals(ZonedDateTime.of(2025, 12, 21, 12, 0, 0, 0, ZURICH), (viewModel.overlay.value as OverlayUiState.Ready).time)
            assertEquals(listOf(sunAt(12, 0), sunAt(12, 5), sunAt(11, 55)), suns.take(3))
            assertEquals(288, suns.size)
        }

    @Test
    fun `a time change to a computed step is ready at once, while the day is still computed`() =
        runTest {
            val suns = mutableListOf<SunPosition>()
            val gate = MutableStateFlow(false)
            // 12:00, 12:05 and 11:55 are computed; 12:10 waits.
            val viewModel = dayViewModel(suns, before = { if (suns.size == 3) gate.first { it } })
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            viewModel.onOverlayToggled()
            advanceTimeBy(SETTLE_MILLIS)
            assertEquals(3, suns.size)

            viewModel.onSliderMoved(11 * 60f + 55)

            assertEquals(ZonedDateTime.of(2025, 12, 21, 11, 55, 0, 0, ZURICH), (viewModel.overlay.value as OverlayUiState.Ready).time)
            assertEquals(3, suns.size)
            gate.value = true
            advanceUntilIdle()
            assertEquals(288, suns.size)
        }

    @Test
    fun `a camera rest starts the day over for the new area, with the selected time first`() =
        runTest {
            val areas = mutableListOf<MapArea>()
            val suns = mutableListOf<SunPosition>()
            val viewModel = dayViewModel(suns, areas)
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            viewModel.onOverlayToggled()
            advanceTimeBy(SETTLE_MILLIS)
            suns.clear()
            areas.clear()

            viewModel.onCameraMoved(CameraState(center = GeoPoint(46.69, 7.87), zoom = 12.0))
            advanceTimeBy(SETTLE_MILLIS)

            assertEquals(288, suns.size)
            assertEquals(sunPosition(GeoPoint(46.69, 7.87), ZonedDateTime.of(2025, 12, 21, 12, 0, 0, 0, ZURICH).toInstant()), suns.first())
            assertEquals(setOf(MapArea(GeoPoint(46.69, 7.87), 12.0, MAP_WIDTH, MAP_HEIGHT)), areas.toSet())
        }

    @Test
    fun `a date change starts the day over for the new date`() =
        runTest {
            val suns = mutableListOf<SunPosition>()
            val viewModel = dayViewModel(suns)
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            viewModel.onOverlayToggled()
            advanceTimeBy(SETTLE_MILLIS)
            suns.clear()

            viewModel.onDateSelected(LocalDate.of(2025, 12, 22))
            advanceTimeBy(SETTLE_MILLIS)

            assertEquals(ZonedDateTime.of(2025, 12, 22, 12, 0, 0, 0, ZURICH), (viewModel.overlay.value as OverlayUiState.Ready).time)
            assertEquals(sunPosition(INTERLAKEN, ZonedDateTime.of(2025, 12, 22, 12, 0, 0, 0, ZURICH).toInstant()), suns.first())
            assertEquals(288, suns.size)
        }

    @Test
    fun `a reconnect with unknown cells starts the day over`() =
        runTest {
            val suns = mutableListOf<SunPosition>()
            val viewModel = dayViewModel(suns)
            isOnline.value = false
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            viewModel.onOverlayToggled()
            advanceTimeBy(SETTLE_MILLIS)
            assertTrue((viewModel.overlay.value as OverlayUiState.Ready).grid.hasUnknown)
            suns.clear()

            isOnline.value = true
            advanceTimeBy(SETTLE_MILLIS)

            assertFalse((viewModel.overlay.value as OverlayUiState.Ready).grid.hasUnknown)
            assertEquals(sunAt(12, 0), suns.first())
            assertEquals(288, suns.size)
            viewModel.onSliderMoved(15 * 60f)
            assertFalse((viewModel.overlay.value as OverlayUiState.Ready).grid.hasUnknown)
        }

    @Test
    fun `debug builds log the day's steps, night steps and total time when it is finished`() =
        runTest {
            val logged = mutableListOf<String>()
            val viewModel = dayViewModel(mutableListOf(), log = { logged += it })
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))

            viewModel.onOverlayToggled()
            advanceTimeBy(SETTLE_MILLIS)

            assertTrue(logged.any { it.matches(Regex("Overlay day 2025-12-21: 288 of 288 steps, 177 at night, in \\d+ ms")) }, "$logged")
        }

    @Test
    fun `leaving the screen for more than 5 s keeps the computed day`() =
        runTest {
            val suns = mutableListOf<SunPosition>()
            val viewModel =
                MapViewModel(
                    SavedStateHandle(),
                    isOnline,
                    clock,
                    repository { heightBytes(568) },
                    { null },
                    { area, sun ->
                        suns += sun
                        val sweep = SunShadeSweep(area, SunPosition(0.0, -30.0, false))
                        sweep.night(sweep.groundTiles().associateWith { FLAT })
                    },
                    UnconfinedTestDispatcher(testScheduler),
                    dayDispatcher = StandardTestDispatcher(testScheduler),
                )
            viewModel.onMapSizeChanged(MAP_WIDTH, MAP_HEIGHT)
            viewModel.onSliderMoved(12 * 60f)
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            viewModel.onOverlayToggled()
            val screen = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.overlay.collect {} }
            advanceTimeBy(SETTLE_MILLIS)
            assertEquals(288, suns.size)

            // The app goes to the background for 10 s, then comes back.
            screen.cancel()
            advanceTimeBy(10_000)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.overlay.collect {} }
            advanceTimeBy(SETTLE_MILLIS)

            assertEquals(288, suns.size, "the day was computed again")
        }

    @Test
    fun `the day's progress is the share of computed steps while it runs, and null when finished`() =
        runTest {
            val suns = mutableListOf<SunPosition>()
            val gate = MutableStateFlow(false)
            // 12:00, 12:05 and 11:55 are computed; 12:10 waits.
            val viewModel = dayViewModel(suns, before = { if (suns.size == 3) gate.first { it } })
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            assertEquals(null, viewModel.dayProgress.value)

            viewModel.onOverlayToggled()
            advanceTimeBy(SETTLE_MILLIS)
            assertEquals(3f / 288, viewModel.dayProgress.value)

            gate.value = true
            advanceUntilIdle()
            assertEquals(288, suns.size)
            assertEquals(null, viewModel.dayProgress.value)
        }

    @Test
    fun `the day's progress starts over after a pan and is null when switched off`() =
        runTest {
            val suns = mutableListOf<SunPosition>()
            val gate = MutableStateFlow(false)
            val viewModel = dayViewModel(suns, before = { if (suns.size == 3) gate.first { it } })
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            viewModel.onOverlayToggled()
            advanceTimeBy(SETTLE_MILLIS)
            assertEquals(3f / 288, viewModel.dayProgress.value)

            viewModel.onCameraMoved(CameraState(center = GeoPoint(46.69, 7.87), zoom = 12.0))
            // Lets the old day's waiting step, on the test scheduler, see its cancellation.
            runCurrent()
            assertEquals(0f, viewModel.dayProgress.value)

            viewModel.onOverlayToggled()
            assertEquals(null, viewModel.dayProgress.value)
        }

    @Test
    fun `picking another date and then the first again shows the first day at once, without computing it again`() =
        runTest {
            val suns = mutableListOf<SunPosition>()
            val viewModel = dayViewModel(suns)
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            viewModel.onOverlayToggled()
            advanceTimeBy(SETTLE_MILLIS)
            viewModel.onDateSelected(LocalDate.of(2025, 12, 22))
            advanceTimeBy(SETTLE_MILLIS)
            assertEquals(2 * 288, suns.size)
            suns.clear()

            viewModel.onDateSelected(LocalDate.of(2025, 12, 21))

            assertEquals(ZonedDateTime.of(2025, 12, 21, 12, 0, 0, 0, ZURICH), (viewModel.overlay.value as OverlayUiState.Ready).time)
            advanceUntilIdle()
            assertEquals(emptyList<SunPosition>(), suns)
            assertEquals(null, viewModel.dayProgress.value)
        }

    @Test
    fun `switching the overlay off and on shows the day at once, without computing it again`() =
        runTest {
            val suns = mutableListOf<SunPosition>()
            val logged = mutableListOf<String>()
            val viewModel = dayViewModel(suns, log = { logged += it })
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            viewModel.onOverlayToggled()
            advanceTimeBy(SETTLE_MILLIS)
            suns.clear()
            logged.clear()

            viewModel.onOverlayToggled()
            viewModel.onOverlayToggled()

            assertEquals(ZonedDateTime.of(2025, 12, 21, 12, 0, 0, 0, ZURICH), (viewModel.overlay.value as OverlayUiState.Ready).time)
            advanceUntilIdle()
            assertEquals(emptyList<SunPosition>(), suns)
            assertEquals(null, viewModel.dayProgress.value)
            assertTrue(logged.none { it.startsWith("Overlay day") }, "$logged")
        }

    @Test
    fun `only the missing steps of a partly computed day are computed`() =
        runTest {
            val suns = mutableListOf<SunPosition>()
            val gate = MutableStateFlow(false)
            // 12:00, 12:05 and 11:55 are computed; 12:10 waits.
            val viewModel = dayViewModel(suns, before = { if (suns.size == 3) gate.first { it } })
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            viewModel.onOverlayToggled()
            advanceTimeBy(SETTLE_MILLIS)
            viewModel.onOverlayToggled()
            runCurrent()
            gate.value = true

            viewModel.onOverlayToggled()
            assertEquals(ZonedDateTime.of(2025, 12, 21, 12, 0, 0, 0, ZURICH), (viewModel.overlay.value as OverlayUiState.Ready).time)
            advanceUntilIdle()

            assertEquals(288, suns.size)
            assertEquals(288, suns.toSet().size)
        }

    @Test
    fun `a cached day with unknown cells is computed anew when picked online`() =
        runTest {
            val suns = mutableListOf<SunPosition>()
            val viewModel = dayViewModel(suns)
            isOnline.value = false
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            viewModel.onOverlayToggled()
            advanceTimeBy(SETTLE_MILLIS)
            viewModel.onDateSelected(LocalDate.of(2025, 12, 22))
            advanceTimeBy(SETTLE_MILLIS)
            isOnline.value = true
            advanceUntilIdle()
            suns.clear()

            viewModel.onDateSelected(LocalDate.of(2025, 12, 21))
            advanceUntilIdle()

            assertEquals(288, suns.size)
            assertFalse((viewModel.overlay.value as OverlayUiState.Ready).grid.hasUnknown)
        }

    @Test
    fun `with a small budget the least recently used day is dropped`() =
        runTest {
            val area = MapArea(INTERLAKEN, 12.0, MAP_WIDTH, MAP_HEIGHT)
            val dayBytes = 288L * cheapGrid(area, online = true).stateBytes
            val suns = mutableListOf<SunPosition>()
            val viewModel = dayViewModel(suns, dayCache = DayCache(maxBytes = dayBytes * 3 / 2))
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            viewModel.onOverlayToggled()
            advanceTimeBy(SETTLE_MILLIS)
            viewModel.onDateSelected(LocalDate.of(2025, 12, 22))
            advanceTimeBy(SETTLE_MILLIS)
            suns.clear()

            viewModel.onDateSelected(LocalDate.of(2025, 12, 21))
            advanceUntilIdle()

            assertEquals(288, suns.size, "21 December was not dropped")
        }

    @Test
    fun `switching the overlay off stops the day`() =
        runTest {
            val suns = mutableListOf<SunPosition>()
            val gate = MutableStateFlow(true)
            val viewModel = dayViewModel(suns, before = { if (suns.isNotEmpty()) gate.first { it } })
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            gate.value = false
            viewModel.onOverlayToggled()
            advanceTimeBy(SETTLE_MILLIS)
            assertEquals(1, suns.size)

            viewModel.onOverlayToggled()
            gate.value = true
            advanceUntilIdle()

            assertEquals(OverlayUiState.Off, viewModel.overlay.value)
            assertEquals(1, suns.size)
        }

    /**
     * A view model whose overlay grids come from flat 568 m terrain, or from no terrain at all while
     * offline. [before] runs before each grid, [areas] and [suns] record the requests.
     */
    private fun TestScope.overlayViewModel(
        areas: MutableList<MapArea>,
        suns: MutableList<SunPosition> = mutableListOf(),
        before: suspend () -> Unit = {},
    ): MapViewModel {
        val viewModel =
            newViewModel(
                overlayGrid = { area, sun ->
                    before()
                    areas += area
                    suns += sun
                    flatGrid(area, sun, available = isOnline.value)
                },
                computeDispatcher = UnconfinedTestDispatcher(testScheduler),
            )
        viewModel.onMapSizeChanged(MAP_WIDTH, MAP_HEIGHT)
        viewModel.onSliderMoved(12 * 60f)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.overlay.collect {} }
        return viewModel
    }

    /**
     * A view model that computes the whole day, the background steps on the test scheduler. Its grids
     * are cheap: shade over flat terrain, unknown while offline. [before] runs before each grid, [suns]
     * and [areas] record the requests.
     */
    private fun TestScope.dayViewModel(
        suns: MutableList<SunPosition>,
        areas: MutableList<MapArea> = mutableListOf(),
        before: suspend () -> Unit = {},
        log: (String) -> Unit = {},
        dayCache: DayCache = DayCache(maxBytes = Long.MAX_VALUE),
    ): MapViewModel {
        val viewModel =
            MapViewModel(
                SavedStateHandle(),
                isOnline,
                clock,
                repository { heightBytes(568) },
                { null },
                { area, sun ->
                    before()
                    areas += area
                    suns += sun
                    cheapGrid(area, isOnline.value)
                },
                UnconfinedTestDispatcher(testScheduler),
                dayDispatcher = StandardTestDispatcher(testScheduler),
                log = log,
                dayCache = dayCache,
            )
        viewModel.onMapSizeChanged(MAP_WIDTH, MAP_HEIGHT)
        viewModel.onSliderMoved(12 * 60f)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.overlay.collect {} }
        return viewModel
    }

    /** A [dayViewModel] in the mode `Sun hours` at Interlaken, zoom 12, whose first day is complete and counted. */
    private fun TestScope.sunHoursViewModel(
        suns: MutableList<SunPosition>,
        before: suspend () -> Unit = {},
        log: (String) -> Unit = {},
    ): MapViewModel {
        val viewModel = dayViewModel(suns, before = before, log = log)
        viewModel.onOverlayModeSelected(OverlayMode.SUN_HOURS)
        viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
        viewModel.onOverlayToggled()
        advanceUntilIdle()
        assertTrue(viewModel.heatmap.value is HeatmapUiState.Ready, "${viewModel.heatmap.value}")
        return viewModel
    }

    // Shade over flat terrain, unknown while offline: cheap, as no sweep runs.
    private fun cheapGrid(
        area: MapArea,
        online: Boolean,
    ): ShadeGrid {
        val sweep = SunShadeSweep(area, SunPosition(0.0, -30.0, false))
        return sweep.night(sweep.groundTiles().associateWith { if (online) FLAT else null })
    }

    private fun sunAt(
        hour: Int,
        minute: Int,
    ) = sunPosition(INTERLAKEN, ZonedDateTime.of(2025, 12, 21, hour, minute, 0, 0, ZURICH).toInstant())

    private fun flatGrid(
        area: MapArea,
        sun: SunPosition,
        available: Boolean,
    ): ShadeGrid {
        val sweep = SunShadeSweep(area, sun)
        val tiles =
            object : AbstractMap<TileKey, HeightTile?>() {
                override val entries: Set<Map.Entry<TileKey, HeightTile?>> get() = throw UnsupportedOperationException()

                override fun get(key: TileKey): HeightTile? = if (available) FLAT else null

                override fun containsKey(key: TileKey) = true
            }
        sweep.tiles(sweep.groundTiles().associateWith { tiles[it] })
        return sweep.assemble(listOf(sweep.compute(tiles)))
    }

    private fun newViewModel(
        savedState: SavedStateHandle = SavedStateHandle(),
        repository: ElevationRepository = repository { heightBytes(568) },
        horizon: suspend (GeoPoint) -> HorizonProfile? = { null },
        overlayGrid: suspend (MapArea, SunPosition) -> ShadeGrid = { _, _ -> awaitCancellation() },
        computeDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(),
    ) = MapViewModel(savedState, isOnline, clock, repository, horizon, overlayGrid, computeDispatcher)

    private fun horizonOf(
        angle: Double,
        complete: Boolean = true,
    ) = HorizonProfile(
        eyeHeight = 568.0,
        angles = DoubleArray(AZIMUTH_COUNT) { angle },
        complete = BooleanArray(AZIMUTH_COUNT) { complete },
    )

    /** Tiles whose bytes are the height of every pixel, as decimal text. */
    private fun repository(fetch: suspend (TileKey) -> ByteArray?) =
        ElevationRepository(
            TileCache(
                fetch = { key -> fetch(key)?.let { DemTile.Found(it) } ?: DemTile.Unavailable },
                decode = { bytes -> IntArray(512 * 512) { terrarium(bytes.decodeToString().toInt()) } },
            ),
        )

    private fun heightBytes(metres: Int) = metres.toString().encodeToByteArray()

    private fun terrarium(metres: Int): Int = (0xFF shl 24) or ((metres + 32768) shl 8)

    /** A clock the test can move; the view model reads the zone and "now" from it. */
    private class MutableClock(
        var instant: Instant,
        private val zone: ZoneId,
    ) : Clock() {
        override fun instant(): Instant = instant

        override fun getZone(): ZoneId = zone

        override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)
    }

    private companion object {
        val ZURICH: ZoneId = ZoneId.of("Europe/Zurich")
        val INTERLAKEN = GeoPoint(46.6863, 7.8632)
        val INTERLAKEN_TILE = TileKey(12, 2137, 1445)

        // Just past the 300 ms the camera must rest before a horizon is computed (design D8).
        const val SETTLE_MILLIS = 301L

        const val MAP_WIDTH = 60.0
        const val MAP_HEIGHT = 80.0
        val FLAT: HeightTile = HeightTile.fromMetres(512, FloatArray(512 * 512) { 568f })
    }
}
