package com.sunshine.app.offline

import com.sunshine.app.settings.CoordinateFormat
import com.sunshine.app.settings.Settings
import com.sunshine.core.GeoPoint
import com.sunshine.core.MapArea
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineViewModelTest {
    private val regions = MutableStateFlow(emptyList<RegionSummary>())
    private val work = MutableStateFlow(DownloadWork.IDLE)
    private var storage = StorageUse(mapBytes = 0, demBytes = 0)
    private val downloads = mutableListOf<MapArea>()
    private val deletions = mutableListOf<Long>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        area: MapArea = PHONE_AT_11,
        settings: Settings = Settings(),
    ) = OfflineViewModel(
        area = area,
        regions = regions,
        work = work,
        storage = { storage },
        download = { downloads += it },
        delete = { deletions += it },
        zone = ZURICH,
        settings = flowOf(settings),
    )

    @Test
    fun `the storage note names the browsed tiles limit`() {
        assertEquals("512 MiB", viewModel().uiState.value.browsedLimit)
        assertEquals("1024 MiB", viewModel(settings = Settings(browsedLimitMib = 1024)).uiState.value.browsedLimit)
    }

    @Test
    fun `region names follow the coordinate format`() {
        regions.value = listOf(summary(id = 1, createdAt = 1))

        val name =
            viewModel(settings = Settings(coordinates = CoordinateFormat.LV95))
                .uiState.value.regions
                .single()
                .name

        assertEquals("2'636'053, 1'160'356", name)
    }

    @Test
    fun `shows what downloading the visible area takes`() {
        val state = viewModel().uiState.value

        assertEquals("10.5 × 22.3 km, 7406 map tiles and 413 elevation tiles, about 25 min", state.estimate)
        assertTrue(state.canDownload)
    }

    @Test
    fun `below zoom 11 the area cannot be downloaded`() {
        val state = viewModel(PHONE_AT_11.copy(zoom = 10.9)).uiState.value

        assertNull(state.estimate)
        assertFalse(state.canDownload)
        assertEquals(DownloadBlock.ZOOMED_OUT, state.blocked)
    }

    @Test
    fun `an area across the 180 degree meridian cannot be downloaded`() {
        val state = viewModel(MapArea(GeoPoint(-17.0, 179.99), zoom = 12.0, widthDp = 400.0, heightDp = 850.0)).uiState.value

        assertNull(state.estimate)
        assertFalse(state.canDownload)
        assertEquals(DownloadBlock.ACROSS_180, state.blocked)
    }

    @Test
    fun `downloading passes the area the page was opened with`() {
        viewModel().onDownload()

        assertEquals(listOf(PHONE_AT_11), downloads)
    }

    @Test
    fun `no regions`() {
        assertEquals(emptyList<RegionItem>(), viewModel().uiState.value.regions)
    }

    @Test
    fun `regions are listed newest first and named by their centre`() {
        regions.value = listOf(summary(id = 2, createdAt = 2), summary(id = 1, createdAt = 1)) // as the query orders them

        val items = viewModel().uiState.value.regions

        assertEquals(listOf(2L, 1L), items.map { it.id })
        assertEquals("46.5935° N, 7.9091° E", items.first().name)
    }

    @Test
    fun `status lines follow the download work`() {
        regions.value =
            listOf(
                summary(id = 3, createdAt = 3, progress = 0),
                summary(id = 2, createdAt = 2, progress = 63),
                summary(
                    id = 1,
                    createdAt = 1,
                    state = RegionState.COMPLETE,
                    completedAt = OCT_2_NOON,
                    mapBytes = mib(180.0),
                    demBytes = mib(3.4),
                ),
            )
        work.value = DownloadWork.RUNNING
        val viewModel = viewModel()

        assertEquals(
            listOf("Waiting", "Downloading 63 %", "2026-10-02 · 183 MiB"),
            viewModel.uiState.value.regions
                .map { it.status },
        )

        work.value = DownloadWork.WAITING_FOR_NETWORK
        assertEquals(
            "Incomplete (63 %) · waiting for network",
            viewModel.uiState.value.regions[1]
                .status,
        )

        work.value = DownloadWork.WAITING_FOR_STORAGE
        assertEquals(
            "Incomplete (63 %) · waiting for storage",
            viewModel.uiState.value.regions[1]
                .status,
        )
    }

    @Test
    fun `storage lines`() {
        storage = StorageUse(mapBytes = mib(395.2), demBytes = mib(280.6))

        val state = viewModel().uiState.value

        assertEquals("395 MiB", state.mapStorage)
        assertEquals("281 MiB", state.demStorage)
    }

    @Test
    fun `delete asks first, and removes the region from the list at once`() {
        regions.value = listOf(summary(id = 1, createdAt = 1))
        val viewModel = viewModel()

        viewModel.onDeleteRequested(1)
        assertEquals(
            "46.5935° N, 7.9091° E",
            viewModel.uiState.value.confirmDelete!!
                .name,
        )
        viewModel.onDeleteConfirmed()

        assertEquals(emptyList<RegionItem>(), viewModel.uiState.value.regions)
        assertNull(viewModel.uiState.value.confirmDelete)
        assertEquals(listOf(1L), deletions)
    }

    @Test
    fun `cancel leaves the region`() {
        regions.value = listOf(summary(id = 1, createdAt = 1))
        val viewModel = viewModel()

        viewModel.onDeleteRequested(1)
        viewModel.onDeleteCancelled()

        assertEquals(
            listOf(1L),
            viewModel.uiState.value.regions
                .map { it.id },
        )
        assertNull(viewModel.uiState.value.confirmDelete)
        assertEquals(emptyList<Long>(), deletions)
    }

    private fun summary(
        id: Long,
        createdAt: Long,
        state: RegionState = RegionState.QUEUED,
        progress: Int = 0,
        completedAt: Long? = null,
        mapBytes: Long = 0,
        demBytes: Long = 0,
    ) = RegionSummary(
        RegionRow(
            id = id,
            centreLat = 46.5935,
            centreLon = 7.9091,
            south = 46.4931,
            west = 7.8404,
            north = 46.6937,
            east = 7.9778,
            createdAt = createdAt,
            completedAt = completedAt,
            mapBytes = mapBytes,
            progress = progress,
            state = state,
        ),
        demBytes,
    )

    private fun mib(value: Double) = (value * 1024 * 1024).toLong()

    private companion object {
        val ZURICH: ZoneId = ZoneId.of("Europe/Zurich")
        val PHONE_AT_11 = MapArea(GeoPoint(46.5935, 7.9091), zoom = 11.0, widthDp = 400.0, heightDp = 850.0)
        val OCT_2_NOON =
            LocalDate
                .of(2026, 10, 2)
                .atTime(12, 0)
                .atZone(ZURICH)
                .toInstant()
                .toEpochMilli()
    }
}
