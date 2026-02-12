package com.sunshine.app.ui.screens.download

import android.net.NetworkRequest
import com.sunshine.app.data.connectivity.ConnectivityObserver
import com.sunshine.app.domain.model.BoundingBox
import com.sunshine.app.domain.model.DownloadableRegion
import com.sunshine.app.domain.repository.DownloadProgress
import com.sunshine.app.domain.repository.DownloadState
import com.sunshine.app.domain.repository.RegionProvider
import com.sunshine.app.domain.repository.TileDownloadRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadViewModelTest {
    private lateinit var regionProvider: RegionProvider
    private lateinit var downloadRepository: TileDownloadRepository
    private lateinit var connectivityObserver: ConnectivityObserver
    private lateinit var viewModel: DownloadViewModel

    private val testDispatcher = StandardTestDispatcher()
    private val progressFlow = MutableStateFlow<List<DownloadProgress>>(emptyList())
    private val storageFlow = MutableStateFlow(0L)
    private val onlineFlow = MutableStateFlow(true)

    private val testRegion = createTestRegion("swiss-alps", "Swiss Alps")
    private val testRegion2 = createTestRegion("austrian-alps", "Austrian Alps")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        setupMocks()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `initial state contains available regions`() =
        runTest {
            viewModel = createViewModel()

            val state = viewModel.uiState.value
            assertEquals(2, state.availableRegions.size)
            assertEquals("Swiss Alps", state.availableRegions[0].region.name)
            assertEquals("Austrian Alps", state.availableRegions[1].region.name)
        }

    @Test
    fun `initial regions have no download progress`() =
        runTest {
            viewModel = createViewModel()

            val state = viewModel.uiState.value
            state.availableRegions.forEach { regionWithStatus ->
                assertFalse(regionWithStatus.isDownloaded)
                assertFalse(regionWithStatus.isDownloading)
                assertEquals(0, regionWithStatus.progress)
            }
        }

    @Test
    fun `ui state updates when download progress changes`() =
        runTest {
            viewModel = createViewModel()
            val collector = launch { viewModel.uiState.collect() }
            advanceUntilIdle()

            progressFlow.value =
                listOf(
                    createProgress("swiss-alps", DownloadState.DOWNLOADING, progress = 50),
                )
            advanceUntilIdle()

            val swissAlps =
                viewModel.uiState.value.availableRegions
                    .first { it.region.id == "swiss-alps" }
            assertTrue("Region should be downloading", swissAlps.isDownloading)
            assertEquals(50, swissAlps.progress)

            collector.cancel()
        }

    @Test
    fun `ui state shows region as downloaded when completed`() =
        runTest {
            viewModel = createViewModel()
            val collector = launch { viewModel.uiState.collect() }
            advanceUntilIdle()

            progressFlow.value =
                listOf(
                    createProgress("swiss-alps", DownloadState.COMPLETED, progress = 100),
                )
            advanceUntilIdle()

            val swissAlps =
                viewModel.uiState.value.availableRegions
                    .first { it.region.id == "swiss-alps" }
            assertTrue("Region should be downloaded", swissAlps.isDownloaded)

            collector.cancel()
        }

    @Test
    fun `ui state reflects total storage used`() =
        runTest {
            viewModel = createViewModel()
            val collector = launch { viewModel.uiState.collect() }
            advanceUntilIdle()

            storageFlow.value = 1_048_576L
            advanceUntilIdle()

            assertEquals(1_048_576L, viewModel.uiState.value.totalStorageUsed)

            collector.cancel()
        }

    @Test
    fun `ui state reflects online status`() =
        runTest {
            viewModel = createViewModel()
            val collector = launch { viewModel.uiState.collect() }
            advanceUntilIdle()

            onlineFlow.value = false
            advanceUntilIdle()

            assertFalse("Should be offline", viewModel.uiState.value.isOnline)

            collector.cancel()
        }

    @Test
    fun `startDownload delegates to repository`() =
        runTest {
            viewModel = createViewModel()
            viewModel.startDownload(testRegion)
            verify { downloadRepository.startDownload(testRegion) }
        }

    @Test
    fun `cancelDownload delegates to repository`() =
        runTest {
            viewModel = createViewModel()
            viewModel.cancelDownload("swiss-alps")
            verify { downloadRepository.cancelDownload("swiss-alps") }
        }

    @Test
    fun `formatStorageSize formats bytes correctly`() =
        runTest {
            viewModel = createViewModel()

            assertEquals("500 B", viewModel.formatStorageSize(500L))
            assertEquals("1 KB", viewModel.formatStorageSize(1024L))
            assertEquals("5 MB", viewModel.formatStorageSize(5_242_880L))
            assertEquals("1.5 GB", viewModel.formatStorageSize(1_610_612_736L))
        }

    @Test
    fun `RegionWithStatus statusText reflects each download state`() {
        assertStatusText("Not downloaded", null)
        assertStatusText("Pending...", DownloadState.PENDING)
        assertStatusText("Downloading 45%", DownloadState.DOWNLOADING, progress = 45)
        assertStatusText("Downloaded", DownloadState.COMPLETED)
        assertStatusText("Failed", DownloadState.FAILED)
        assertStatusText("Paused", DownloadState.PAUSED)
    }

    private fun assertStatusText(
        expected: String,
        state: DownloadState?,
        progress: Int = 0,
    ) {
        val downloadProgress = state?.let { createProgress(testRegion.id, it, progress) }
        val regionWithStatus = RegionWithStatus(testRegion, downloadProgress)
        assertEquals(expected, regionWithStatus.statusText)
    }

    private fun createViewModel(): DownloadViewModel = DownloadViewModel(regionProvider, downloadRepository, connectivityObserver)

    private fun setupMocks() {
        mockkConstructor(NetworkRequest.Builder::class)
        every { anyConstructed<NetworkRequest.Builder>().addCapability(any()) } returns mockk(relaxed = true)
        every { anyConstructed<NetworkRequest.Builder>().build() } returns mockk()

        regionProvider =
            mockk {
                every { getAvailableRegions() } returns listOf(testRegion, testRegion2)
            }
        downloadRepository =
            mockk(relaxed = true) {
                every { getDownloadProgress() } returns progressFlow
                every { getTotalStorageUsed() } returns storageFlow
            }
        connectivityObserver =
            mockk {
                every { isOnline } returns onlineFlow
            }
    }

    @Suppress("LongParameterList")
    private fun createProgress(
        regionId: String,
        status: DownloadState,
        progress: Int = 0,
    ) = DownloadProgress(
        regionId = regionId,
        regionName = regionId,
        status = status,
        progress = progress,
        downloadedTiles = 0,
        totalTiles = 100,
        sizeBytes = 0,
    )

    companion object {
        private fun createTestRegion(
            id: String,
            name: String,
        ) = DownloadableRegion(
            id = id,
            name = name,
            description = "Test region",
            bounds = BoundingBox(north = 47.8, south = 45.8, east = 10.5, west = 5.9),
            minZoom = 8,
            maxZoom = 14,
        )
    }
}
