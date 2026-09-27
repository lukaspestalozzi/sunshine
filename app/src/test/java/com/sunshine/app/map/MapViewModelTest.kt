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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
    fun `a time change drops the previous overlay at once`() =
        runTest {
            val gate = MutableStateFlow(true)
            val viewModel = overlayViewModel(mutableListOf(), before = { gate.first { it } })
            viewModel.onCameraMoved(CameraState(center = INTERLAKEN, zoom = 12.0))
            viewModel.onOverlayToggled()
            advanceTimeBy(SETTLE_MILLIS)
            gate.value = false

            viewModel.onSliderMoved(15 * 60f)

            assertEquals(OverlayUiState.Computing(kept = null), viewModel.overlay.value)
            gate.value = true
            assertEquals(ZonedDateTime.of(2025, 12, 21, 15, 0, 0, 0, ZURICH), (viewModel.overlay.value as OverlayUiState.Ready).time)
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
