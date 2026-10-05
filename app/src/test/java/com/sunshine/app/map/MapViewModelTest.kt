package com.sunshine.app.map

import androidx.lifecycle.SavedStateHandle
import com.sunshine.app.elevation.DemTile
import com.sunshine.app.elevation.ElevationRepository
import com.sunshine.app.elevation.TileCache
import com.sunshine.app.settings.LastView
import com.sunshine.app.settings.Preset
import com.sunshine.app.settings.Resolution
import com.sunshine.app.settings.Settings
import com.sunshine.app.settings.StartAt
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
import kotlin.math.ceil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import org.junit.jupiter.api.Assertions.assertSame
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
    fun `starts at the stored last view`() {
        val viewModel = newViewModel(settings = Settings(lastView = LastView(46.6863, 7.8632, 13.0)))

        assertEquals(CameraState(center = GeoPoint(46.6863, 7.8632), zoom = 13.0), viewModel.camera.value)
    }

    @Test
    fun `starts at the Alps overview when no view is stored`() {
        val viewModel = newViewModel(settings = Settings(startAt = StartAt.LAST_VIEW, lastView = null))

        assertEquals(CameraState(center = DEFAULT_LOCATION, zoom = 10.0), viewModel.camera.value)
    }

    @Test
    fun `starts at the Alps overview when chosen, whatever view is stored`() {
        val viewModel =
            newViewModel(settings = Settings(startAt = StartAt.ALPS_OVERVIEW, lastView = LastView(46.6863, 7.8632, 13.0)))

        assertEquals(CameraState(center = DEFAULT_LOCATION, zoom = 10.0), viewModel.camera.value)
    }

    @Test
    fun `My location also opens at the last view`() {
        val viewModel =
            newViewModel(settings = Settings(startAt = StartAt.MY_LOCATION, lastView = LastView(46.9480, 7.4474, 12.0)))

        assertEquals(CameraState(center = GeoPoint(46.9480, 7.4474), zoom = 12.0), viewModel.camera.value)
    }

    @Test
    fun `saved state wins over the stored last view, as after a rotation`() {
        val savedState = SavedStateHandle()
        val moved = CameraState(center = GeoPoint(46.5935, 7.9091), zoom = 14.0)
        newViewModel(savedState).onCameraMoved(moved)

        val recreated = newViewModel(savedState, settings = Settings(lastView = LastView(46.6863, 7.8632, 13.0)))

        assertEquals(moved, recreated.camera.value)
    }

    @Test
    fun `going to the background stores the camera as the last view`() {
        val stored = mutableListOf<LastView>()
        val viewModel = newViewModel(saveLastView = { stored += it })
        viewModel.onCameraMoved(CameraState(center = GeoPoint(46.6863, 7.8632), zoom = 13.0))

        viewModel.storeLastView()

        assertEquals(listOf(LastView(46.6863, 7.8632, 13.0)), stored)
    }

    @Test
    fun `My location centres once on the first fresh position after launch`() =
        runTest {
            val viewModel = newViewModel(settings = Settings(startAt = StartAt.MY_LOCATION))
            val actions = mutableListOf<LocationAction>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.locationActions.collect { actions += it } }

            viewModel.onLocationStale(false)
            viewModel.onLocationStale(true)
            viewModel.onLocationStale(false)

            assertEquals(listOf<LocationAction>(LocationAction.Centre), actions)
        }

    @Test
    fun `My location does not centre after the user moved the map`() =
        runTest {
            val viewModel = newViewModel(settings = Settings(startAt = StartAt.MY_LOCATION))
            val actions = mutableListOf<LocationAction>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.locationActions.collect { actions += it } }

            viewModel.onCameraGesture()
            viewModel.onLocationStale(false)

            assertEquals(emptyList<LocationAction>(), actions)
        }

    @Test
    fun `Last view never centres on a position`() =
        runTest {
            val viewModel = newViewModel(settings = Settings(startAt = StartAt.LAST_VIEW))
            val actions = mutableListOf<LocationAction>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.locationActions.collect { actions += it } }

            viewModel.onLocationStale(false)

            assertEquals(emptyList<LocationAction>(), actions)
        }

    @Test
    fun `My location does not centre again after a rotation`() =
        runTest {
            val savedState = SavedStateHandle()
            newViewModel(savedState).onCameraMoved(CameraState(center = GeoPoint(46.9480, 7.4474), zoom = 12.0))
            val recreated = newViewModel(savedState, settings = Settings(startAt = StartAt.MY_LOCATION))
            val actions = mutableListOf<LocationAction>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { recreated.locationActions.collect { actions += it } }

            recreated.onLocationStale(false)

            assertEquals(emptyList<LocationAction>(), actions)
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
    fun `a position arriving while waiting makes the button ready without centring, and a second tap centres`() =
        runTest {
            val viewModel = newViewModel()
            val actions = mutableListOf<LocationAction>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.locationActions.collect { actions += it } }

            viewModel.onLocationTapped(LocationAccess.PRECISE, locationOn = true)
            assertEquals(LocationButtonState.WAITING, viewModel.locationButton.value)

            viewModel.onLocationStale(false)
            assertEquals(LocationButtonState.READY, viewModel.locationButton.value)
            assertEquals(emptyList<LocationAction>(), actions)

            viewModel.onLocationTapped(LocationAccess.PRECISE, locationOn = true)
            assertEquals(listOf<LocationAction>(LocationAction.Centre), actions)
        }

    @Test
    fun `a tap without access asks, and a refusal for good shows the access notice`() =
        runTest {
            val viewModel = newViewModel()
            val actions = mutableListOf<LocationAction>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.locationActions.collect { actions += it } }

            viewModel.onLocationTapped(LocationAccess.NONE, locationOn = true)
            viewModel.onLocationPermissionAnswered(LocationAccess.NONE, locationOn = true, dialogAvailable = false)

            assertEquals(listOf(LocationAction.AskPermission, LocationAction.Notice(LocationNotice.ACCESS_OFF)), actions)
            assertEquals(LocationButtonState.IDLE, viewModel.locationButton.value)
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
    fun `selecting an option of the toggle switches the overlay and sets the mode`() {
        val viewModel = newViewModel()

        viewModel.onOverlaySelected(OverlayOption.SUN_HOURS)
        assertTrue(viewModel.isOverlayOn.value)
        assertEquals(OverlayMode.SUN_HOURS, viewModel.overlayMode.value)

        viewModel.onOverlaySelected(OverlayOption.SUN_AND_SHADE)
        assertTrue(viewModel.isOverlayOn.value)
        assertEquals(OverlayMode.SUN_AND_SHADE, viewModel.overlayMode.value)

        viewModel.onOverlaySelected(OverlayOption.OFF)
        assertFalse(viewModel.isOverlayOn.value)

        viewModel.onOverlaySelected(OverlayOption.OFF)
        assertFalse(viewModel.isOverlayOn.value, "selecting off again keeps it off")
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
            assertEquals(144, ready.hours.steps)
            assertEquals(setOf<Short>(144), ready.hours.unknown.toSet())
            // Day length at Interlaken on 21 December: 8 h 33 min, 18 bands.
            assertEquals(18, ready.bands.count)
            // Counts per 8 dp cell, the image at one pixel per dp (design D9).
            assertEquals(ceil(MAP_WIDTH / 8).toInt() * ceil(MAP_HEIGHT / 8).toInt(), ready.hours.sun.size)
            assertEquals(MAP_WIDTH.toInt() * MAP_HEIGHT.toInt(), ready.image.pixels.size)
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
    fun `counting a day trims the cache of days, as the counts add to the day's bytes`() =
        runTest {
            val area = MapArea(INTERLAKEN, 12.0, MAP_WIDTH, MAP_HEIGHT)
            val dayBytes = 144L * cheapGrid(area, online = true, cellDp = 8.0).stateBytes
            val probe = sunHoursViewModel(mutableListOf())
            val countBytes = (probe.heatmap.value as HeatmapUiState.Ready).hours.bytes
            val suns = mutableListOf<SunPosition>()
            // Two heatmap days and one day's counts fit; the second day's counts do not.
            val viewModel = dayViewModel(suns, dayCache = DayCache(maxBytes = 2 * dayBytes + 2 * countBytes - 1))
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            viewModel.onOverlaySelected(OverlayOption.SUN_HOURS)
            advanceUntilIdle()

            viewModel.onDateSelected(LocalDate.of(2025, 12, 22))
            advanceUntilIdle()
            suns.clear()
            viewModel.onDateSelected(LocalDate.of(2025, 12, 21))
            advanceUntilIdle()

            assertEquals(144, suns.size, "21 December was not dropped")
        }

    @Test
    fun `in sun hours the heatmap's own day computes 144 steps at 8 dp and no 2 dp grid`() =
        runTest {
            val cells = mutableListOf<Double>()
            val viewModel = dayViewModel(mutableListOf(), cells = cells)
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))

            viewModel.onOverlaySelected(OverlayOption.SUN_HOURS)
            advanceUntilIdle()

            assertEquals(144, cells.size)
            assertEquals(setOf(8.0), cells.toSet())
            assertEquals(144, (viewModel.heatmap.value as HeatmapUiState.Ready).hours.steps)
        }

    @Test
    fun `sun hours pauses the sun and shade day, and switching back resumes it`() =
        runTest {
            val suns = mutableListOf<SunPosition>()
            val cells = mutableListOf<Double>()
            val gate = MutableStateFlow(false)
            val viewModel =
                dayViewModel(suns, cells = cells, beforeCell = { cell ->
                    if (cell == 2.0 &&
                        cells.count { it == 2.0 } == 3
                    ) {
                        gate.first { it }
                    }
                })
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            viewModel.onOverlaySelected(OverlayOption.SUN_AND_SHADE)
            advanceTimeBy(SETTLE_MILLIS)
            assertEquals(3, cells.count { it == 2.0 })

            viewModel.onOverlaySelected(OverlayOption.SUN_HOURS)
            advanceUntilIdle()
            assertEquals(144, cells.count { it == 8.0 })
            assertEquals(3, cells.count { it == 2.0 }, "the sun and shade day did not pause")

            gate.value = true
            viewModel.onOverlaySelected(OverlayOption.SUN_AND_SHADE)
            advanceUntilIdle()
            val fine = suns.filterIndexed { i, _ -> cells[i] == 2.0 }
            assertEquals(288, fine.size)
            assertEquals(288, fine.toSet().size, "steps computed before the pause were computed again")
        }

    @Test
    fun `the progress in sun hours is the share of the heatmap's day`() =
        runTest {
            val cells = mutableListOf<Double>()
            val gate = MutableStateFlow(false)
            val viewModel = dayViewModel(mutableListOf(), cells = cells, beforeCell = { if (cells.size == 36) gate.first { it } })
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))

            viewModel.onOverlaySelected(OverlayOption.SUN_HOURS)
            advanceUntilIdle()

            assertEquals(0.25f, viewModel.dayProgress.value)
            gate.value = true
            advanceUntilIdle()
            assertEquals(null, viewModel.dayProgress.value)
        }

    @Test
    fun `the cache keeps both days of an area and date, one per cell size`() =
        runTest {
            val dayCache = DayCache(maxBytes = Long.MAX_VALUE)
            val viewModel = dayViewModel(mutableListOf(), dayCache = dayCache)
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            viewModel.onOverlaySelected(OverlayOption.SUN_AND_SHADE)
            advanceUntilIdle()
            viewModel.onOverlaySelected(OverlayOption.SUN_HOURS)
            advanceUntilIdle()

            val area = MapArea(INTERLAKEN, 12.0, MAP_WIDTH, MAP_HEIGHT)
            val december21 = LocalDate.of(2025, 12, 21)
            assertEquals(2.0, dayCache.get(area, december21, 2.0)?.cellDp)
            // The heatmap's day is kept with its own step, 10 minutes (design D3 of add-settings).
            assertEquals(8.0, dayCache.get(area, december21, 8.0, stepMinutes = 10)?.cellDp)
        }

    @Test
    fun `a day computed anew after a reconnect is counted anew`() =
        runTest {
            isOnline.value = false
            val viewModel = sunHoursViewModel(mutableListOf())
            assertEquals(setOf<Short>(144), (viewModel.heatmap.value as HeatmapUiState.Ready).hours.unknown.toSet())

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
                    { area, sun, _ -> flatGrid(area, sun, available = true) },
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
                    { area, sun, _ ->
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
    fun `an opacity change neither recomputes nor redraws the overlay`() =
        runTest {
            val suns = mutableListOf<SunPosition>()
            val settings = MutableStateFlow(Settings())
            val viewModel = dayViewModel(suns, settings = settings)
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            viewModel.onOverlayToggled()
            advanceUntilIdle()
            val overlay = viewModel.overlay.value
            suns.clear()

            settings.value = settings.value.copy(overlayOpacityPercent = 30)
            advanceUntilIdle()

            assertEquals(emptyList<SunPosition>(), suns)
            assertSame(overlay, viewModel.overlay.value)
        }

    @Test
    fun `an opacity change neither recomputes nor rebuilds the heatmap`() =
        runTest {
            val suns = mutableListOf<SunPosition>()
            val settings = MutableStateFlow(Settings())
            val viewModel = dayViewModel(suns, settings = settings)
            viewModel.onOverlayModeSelected(OverlayMode.SUN_HOURS)
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            viewModel.onOverlayToggled()
            advanceUntilIdle()
            val heatmap = viewModel.heatmap.value
            assertTrue(heatmap is HeatmapUiState.Ready, "$heatmap")
            suns.clear()

            settings.value = settings.value.copy(overlayOpacityPercent = 90)
            advanceUntilIdle()

            assertEquals(emptyList<SunPosition>(), suns)
            assertSame(heatmap, viewModel.heatmap.value)
        }

    @Test
    fun `with Fast the sun and shade day computes 144 steps in 4 dp cells`() =
        runTest {
            val suns = mutableListOf<SunPosition>()
            val cells = mutableListOf<Double>()
            val viewModel = dayViewModel(suns, cells = cells, settings = MutableStateFlow(Settings(preset = Preset.FAST)))
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))

            viewModel.onOverlayToggled()
            advanceUntilIdle()

            assertEquals(144, suns.size)
            assertEquals(setOf(4.0), cells.toSet())
        }

    @Test
    fun `with Fast the heatmap day computes 96 steps in 16 dp cells`() =
        runTest {
            val suns = mutableListOf<SunPosition>()
            val cells = mutableListOf<Double>()
            val viewModel = dayViewModel(suns, cells = cells, settings = MutableStateFlow(Settings(preset = Preset.FAST)))
            viewModel.onOverlayModeSelected(OverlayMode.SUN_HOURS)
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))

            viewModel.onOverlayToggled()
            advanceUntilIdle()

            assertEquals(96, suns.size)
            assertEquals(setOf(16.0), cells.toSet())
            assertEquals(15, (viewModel.heatmap.value as HeatmapUiState.Ready).hours.stepMinutes)
        }

    @Test
    fun `the slider moves in the sun and shade step only while that mode is shown`() {
        val viewModel = newViewModel(settings = Settings(preset = Preset.FAST))
        assertEquals(5, viewModel.sliderStep.value)

        viewModel.onOverlaySelected(OverlayOption.SUN_AND_SHADE)
        assertEquals(10, viewModel.sliderStep.value)
        viewModel.onSliderMoved(14 * 60f + 33)
        assertEquals(ZonedDateTime.of(2025, 12, 21, 14, 30, 0, 0, ZURICH), viewModel.selectedTime.value)

        viewModel.onOverlaySelected(OverlayOption.SUN_HOURS)
        assertEquals(5, viewModel.sliderStep.value)
    }

    @Test
    fun `selecting sun and shade rounds the selected time to its step`() {
        val viewModel = newViewModel(settings = Settings(preset = Preset.FAST))
        viewModel.onSliderMoved(14 * 60f + 35)
        assertEquals(ZonedDateTime.of(2025, 12, 21, 14, 35, 0, 0, ZURICH), viewModel.selectedTime.value)

        viewModel.onOverlaySelected(OverlayOption.SUN_AND_SHADE)

        assertEquals(ZonedDateTime.of(2025, 12, 21, 14, 40, 0, 0, ZURICH), viewModel.selectedTime.value)
    }

    @Test
    fun `Now rounds while sun and shade is shown, and leaving it keeps the time`() {
        // The clock reads 2025-12-21 09:47:31 in Zurich.
        val viewModel = newViewModel()
        viewModel.onOverlaySelected(OverlayOption.SUN_AND_SHADE)

        viewModel.onNowClicked()
        assertEquals(ZonedDateTime.of(2025, 12, 21, 9, 45, 0, 0, ZURICH), viewModel.selectedTime.value)

        viewModel.onOverlaySelected(OverlayOption.OFF)
        assertEquals(ZonedDateTime.of(2025, 12, 21, 9, 45, 0, 0, ZURICH), viewModel.selectedTime.value)

        viewModel.onNowClicked()
        assertEquals(ZonedDateTime.of(2025, 12, 21, 9, 47, 0, 0, ZURICH), viewModel.selectedTime.value)
    }

    @Test
    fun `a date change while sun and shade is shown keeps the time on a step`() {
        val viewModel = newViewModel(settings = Settings(preset = Preset.FAST))
        viewModel.onOverlaySelected(OverlayOption.SUN_AND_SHADE)
        viewModel.onSliderMoved(14 * 60f + 40)

        viewModel.onDateSelected(LocalDate.of(2025, 6, 21))

        assertEquals(ZonedDateTime.of(2025, 6, 21, 14, 40, 0, 0, ZURICH), viewModel.selectedTime.value)
    }

    @Test
    fun `a preset change while sun and shade is shown rounds the selected time to the new step`() {
        val settings = MutableStateFlow(Settings())
        val viewModel = newViewModel(settingsFlow = settings)
        viewModel.onOverlaySelected(OverlayOption.SUN_AND_SHADE)
        viewModel.onSliderMoved(12 * 60f + 5)

        settings.value = Settings(preset = Preset.FAST)

        assertEquals(ZonedDateTime.of(2025, 12, 21, 12, 10, 0, 0, ZURICH), viewModel.selectedTime.value)
        assertEquals(10, viewModel.sliderStep.value)
    }

    @Test
    fun `a preset change while the overlay is on computes the shown day anew, with the notice`() =
        runTest {
            val suns = mutableListOf<SunPosition>()
            val cells = mutableListOf<Double>()
            val settings = MutableStateFlow(Settings())
            val viewModel = dayViewModel(suns, cells = cells, settings = settings)
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            viewModel.onOverlayToggled()
            advanceUntilIdle()
            val states = mutableListOf<OverlayUiState>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.overlay.collect { states += it } }
            suns.clear()
            cells.clear()

            settings.value = Settings(preset = Preset.FAST)
            advanceUntilIdle()

            val computing = states.filterIsInstance<OverlayUiState.Computing>()
            assertTrue(computing.isNotEmpty() && computing.all { it.resolutionChanged }, "$states")
            assertEquals(144, suns.size)
            assertEquals(setOf(4.0), cells.toSet())
            assertTrue(viewModel.overlay.value is OverlayUiState.Ready, "${viewModel.overlay.value}")
        }

    @Test
    fun `a change of only the step shows the computing notice too`() =
        runTest {
            val suns = mutableListOf<SunPosition>()
            val settings = MutableStateFlow(Settings(preset = Preset.CUSTOM, custom = Resolution(2, 5, 8, 10)))
            val viewModel = dayViewModel(suns, settings = settings)
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            viewModel.onOverlayToggled()
            advanceUntilIdle()
            val states = mutableListOf<OverlayUiState>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.overlay.collect { states += it } }

            // 12:00 lies on both grids; the cell size stays 2 dp.
            settings.value = Settings(preset = Preset.CUSTOM, custom = Resolution(2, 10, 8, 10))
            advanceUntilIdle()

            val computing = states.filterIsInstance<OverlayUiState.Computing>()
            assertTrue(computing.isNotEmpty() && computing.all { it.resolutionChanged }, "$states")
        }

    @Test
    fun `back to a computed preset shows its day at once, without computing it again`() =
        runTest {
            val suns = mutableListOf<SunPosition>()
            val settings = MutableStateFlow(Settings())
            val viewModel = dayViewModel(suns, settings = settings)
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            viewModel.onOverlayToggled()
            advanceUntilIdle()
            settings.value = Settings(preset = Preset.FAST)
            advanceUntilIdle()
            suns.clear()

            settings.value = Settings(preset = Preset.NORMAL)

            assertTrue(viewModel.overlay.value is OverlayUiState.Ready, "${viewModel.overlay.value}")
            advanceUntilIdle()
            assertEquals(emptyList<SunPosition>(), suns)
        }

    @Test
    fun `a preset change in sun hours computes the heatmap's day anew`() =
        runTest {
            val suns = mutableListOf<SunPosition>()
            val cells = mutableListOf<Double>()
            val settings = MutableStateFlow(Settings())
            val viewModel = dayViewModel(suns, cells = cells, settings = settings)
            viewModel.onOverlayModeSelected(OverlayMode.SUN_HOURS)
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            viewModel.onOverlayToggled()
            advanceUntilIdle()
            suns.clear()
            cells.clear()

            settings.value = Settings(preset = Preset.DETAILED)
            advanceUntilIdle()

            assertEquals(144, suns.size)
            assertEquals(setOf(4.0), cells.toSet())
            assertTrue(viewModel.heatmap.value is HeatmapUiState.Ready, "${viewModel.heatmap.value}")
        }

    @Test
    fun `a preset change while the overlay is off starts no computation`() =
        runTest {
            val suns = mutableListOf<SunPosition>()
            val settings = MutableStateFlow(Settings())
            dayViewModel(suns, settings = settings).onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))

            settings.value = Settings(preset = Preset.DETAILED)
            advanceUntilIdle()

            assertEquals(emptyList<SunPosition>(), suns)
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
                overlayGrid = { area, sun, _ ->
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
     * are cheap: shade over flat terrain, unknown while offline. [before] and [beforeCell] (with the
     * grid's cell size) run before each grid; [suns], [areas] and [cells] record the requests.
     */
    private fun TestScope.dayViewModel(
        suns: MutableList<SunPosition>,
        areas: MutableList<MapArea> = mutableListOf(),
        before: suspend () -> Unit = {},
        log: (String) -> Unit = {},
        dayCache: DayCache = DayCache(maxBytes = Long.MAX_VALUE),
        cells: MutableList<Double> = mutableListOf(),
        beforeCell: suspend (Double) -> Unit = {},
        settings: StateFlow<Settings> = MutableStateFlow(Settings()),
    ): MapViewModel {
        val viewModel =
            MapViewModel(
                SavedStateHandle(),
                isOnline,
                clock,
                repository { heightBytes(568) },
                { null },
                { area, sun, cellDp ->
                    before()
                    beforeCell(cellDp)
                    areas += area
                    suns += sun
                    cells += cellDp
                    cheapGrid(area, isOnline.value, cellDp)
                },
                UnconfinedTestDispatcher(testScheduler),
                dayDispatcher = StandardTestDispatcher(testScheduler),
                log = log,
                dayCache = dayCache,
                settings = settings,
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
        cellDp: Double = 2.0,
    ): ShadeGrid {
        val sweep = SunShadeSweep(area, SunPosition(0.0, -30.0, false), cellDp)
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
        overlayGrid: suspend (MapArea, SunPosition, Double) -> ShadeGrid = { _, _, _ -> awaitCancellation() },
        computeDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(),
        settings: Settings = Settings(),
        saveLastView: suspend (LastView) -> Unit = {},
        settingsFlow: StateFlow<Settings> = MutableStateFlow(settings),
    ) = MapViewModel(
        savedState,
        isOnline,
        clock,
        repository,
        horizon,
        overlayGrid,
        computeDispatcher,
        settings = settingsFlow,
        saveLastView = saveLastView,
    )

    private fun horizonOf(
        angle: Double,
        complete: Boolean = true,
    ) = HorizonProfile(
        eyeHeight = 568.0,
        angles = DoubleArray(AZIMUTH_COUNT) { angle },
        upper = DoubleArray(AZIMUTH_COUNT) { if (complete) angle else 90.0 },
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
