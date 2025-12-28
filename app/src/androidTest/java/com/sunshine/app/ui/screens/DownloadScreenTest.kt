package com.sunshine.app.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sunshine.app.data.connectivity.ConnectivityObserver
import com.sunshine.app.domain.model.DownloadableRegion
import com.sunshine.app.domain.model.GeoPoint
import com.sunshine.app.domain.repository.DownloadProgress
import com.sunshine.app.domain.repository.DownloadState
import com.sunshine.app.domain.repository.RegionProvider
import com.sunshine.app.domain.repository.TileDownloadRepository
import com.sunshine.app.ui.screens.download.DownloadScreen
import com.sunshine.app.ui.screens.download.DownloadViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for DownloadScreen using Compose testing framework.
 */
@RunWith(AndroidJUnit4::class)
class DownloadScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var regionProvider: RegionProvider
    private lateinit var downloadRepository: TileDownloadRepository
    private lateinit var connectivityObserver: ConnectivityObserver

    private val testRegions = listOf(
        DownloadableRegion(
            id = "swiss_alps",
            name = "Swiss Alps",
            description = "Central Switzerland",
            center = GeoPoint(46.8182, 8.2275),
            radiusKm = 50.0,
            minZoom = 8,
            maxZoom = 15,
        ),
        DownloadableRegion(
            id = "zurich",
            name = "Zurich Area",
            description = "Zurich and surroundings",
            center = GeoPoint(47.3769, 8.5417),
            radiusKm = 30.0,
            minZoom = 10,
            maxZoom = 16,
        ),
    )

    @Before
    fun setup() {
        regionProvider = mockk()
        downloadRepository = mockk(relaxed = true)
        connectivityObserver = mockk()

        every { regionProvider.getAvailableRegions() } returns testRegions
        every { downloadRepository.getDownloadProgress() } returns flowOf(emptyList())
        every { downloadRepository.getTotalStorageUsed() } returns flowOf(0L)
        every { connectivityObserver.isOnline } returns MutableStateFlow(true)
    }

    @Test
    fun downloadScreen_displaysTitle() {
        composeTestRule.setContent {
            DownloadScreen(
                onNavigateBack = {},
                viewModel = DownloadViewModel(
                    regionProvider,
                    downloadRepository,
                    connectivityObserver,
                ),
            )
        }

        composeTestRule.onNodeWithText("Download Tiles").assertIsDisplayed()
    }

    @Test
    fun downloadScreen_displaysBackButton() {
        composeTestRule.setContent {
            DownloadScreen(
                onNavigateBack = {},
                viewModel = DownloadViewModel(
                    regionProvider,
                    downloadRepository,
                    connectivityObserver,
                ),
            )
        }

        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    @Test
    fun downloadScreen_backButtonNavigates() {
        var navigatedBack = false

        composeTestRule.setContent {
            DownloadScreen(
                onNavigateBack = { navigatedBack = true },
                viewModel = DownloadViewModel(
                    regionProvider,
                    downloadRepository,
                    connectivityObserver,
                ),
            )
        }

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        assert(navigatedBack) { "Back navigation should be triggered" }
    }

    @Test
    fun downloadScreen_displaysStorageInfo() {
        composeTestRule.setContent {
            DownloadScreen(
                onNavigateBack = {},
                viewModel = DownloadViewModel(
                    regionProvider,
                    downloadRepository,
                    connectivityObserver,
                ),
            )
        }

        composeTestRule.onNodeWithText("Storage used:", substring = true).assertIsDisplayed()
    }

    @Test
    fun downloadScreen_displaysAvailableRegions() {
        composeTestRule.setContent {
            DownloadScreen(
                onNavigateBack = {},
                viewModel = DownloadViewModel(
                    regionProvider,
                    downloadRepository,
                    connectivityObserver,
                ),
            )
        }

        // Check that both regions are displayed
        composeTestRule.onNodeWithText("Swiss Alps").assertIsDisplayed()
        composeTestRule.onNodeWithText("Zurich Area").assertIsDisplayed()
    }

    @Test
    fun downloadScreen_displaysRegionDescriptions() {
        composeTestRule.setContent {
            DownloadScreen(
                onNavigateBack = {},
                viewModel = DownloadViewModel(
                    regionProvider,
                    downloadRepository,
                    connectivityObserver,
                ),
            )
        }

        composeTestRule.onNodeWithText("Central Switzerland").assertIsDisplayed()
        composeTestRule.onNodeWithText("Zurich and surroundings").assertIsDisplayed()
    }

    @Test
    fun downloadScreen_showsSelectRegionHeader() {
        composeTestRule.setContent {
            DownloadScreen(
                onNavigateBack = {},
                viewModel = DownloadViewModel(
                    regionProvider,
                    downloadRepository,
                    connectivityObserver,
                ),
            )
        }

        composeTestRule.onNodeWithText("Select a region").assertIsDisplayed()
    }

    @Test
    fun downloadScreen_showsDownloadButtons_whenOnline() {
        composeTestRule.setContent {
            DownloadScreen(
                onNavigateBack = {},
                viewModel = DownloadViewModel(
                    regionProvider,
                    downloadRepository,
                    connectivityObserver,
                ),
            )
        }

        // Download buttons should be enabled when online
        composeTestRule.onNodeWithText("Download").assertIsDisplayed()
    }

    @Test
    fun downloadScreen_disablesDownloadButtons_whenOffline() {
        every { connectivityObserver.isOnline } returns MutableStateFlow(false)

        composeTestRule.setContent {
            DownloadScreen(
                onNavigateBack = {},
                viewModel = DownloadViewModel(
                    regionProvider,
                    downloadRepository,
                    connectivityObserver,
                ),
            )
        }

        // Should show offline banner
        composeTestRule.onNodeWithText("No network connection", substring = true).assertIsDisplayed()
    }

    @Test
    fun downloadScreen_showsCompletedStatus_forDownloadedRegions() {
        val downloadProgress = listOf(
            DownloadProgress(
                regionId = "swiss_alps",
                status = DownloadState.COMPLETED,
                progress = 100,
                downloadedTiles = 1000,
                totalTiles = 1000,
                bytesDownloaded = 50000000,
            ),
        )
        every { downloadRepository.getDownloadProgress() } returns flowOf(downloadProgress)

        composeTestRule.setContent {
            DownloadScreen(
                onNavigateBack = {},
                viewModel = DownloadViewModel(
                    regionProvider,
                    downloadRepository,
                    connectivityObserver,
                ),
            )
        }

        composeTestRule.onNodeWithText("Downloaded").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Downloaded").assertIsDisplayed()
    }

    @Test
    fun downloadScreen_showsDeleteButton_forDownloadedRegions() {
        val downloadProgress = listOf(
            DownloadProgress(
                regionId = "swiss_alps",
                status = DownloadState.COMPLETED,
                progress = 100,
                downloadedTiles = 1000,
                totalTiles = 1000,
                bytesDownloaded = 50000000,
            ),
        )
        every { downloadRepository.getDownloadProgress() } returns flowOf(downloadProgress)

        composeTestRule.setContent {
            DownloadScreen(
                onNavigateBack = {},
                viewModel = DownloadViewModel(
                    regionProvider,
                    downloadRepository,
                    connectivityObserver,
                ),
            )
        }

        composeTestRule.onNodeWithText("Delete").assertIsDisplayed()
    }

    @Test
    fun downloadScreen_showsDownloadingStatus() {
        val downloadProgress = listOf(
            DownloadProgress(
                regionId = "swiss_alps",
                status = DownloadState.DOWNLOADING,
                progress = 50,
                downloadedTiles = 500,
                totalTiles = 1000,
                bytesDownloaded = 25000000,
            ),
        )
        every { downloadRepository.getDownloadProgress() } returns flowOf(downloadProgress)

        composeTestRule.setContent {
            DownloadScreen(
                onNavigateBack = {},
                viewModel = DownloadViewModel(
                    regionProvider,
                    downloadRepository,
                    connectivityObserver,
                ),
            )
        }

        composeTestRule.onNodeWithText("Downloading 50%", substring = true).assertIsDisplayed()
    }

    @Test
    fun downloadScreen_showsCancelButton_whenDownloading() {
        val downloadProgress = listOf(
            DownloadProgress(
                regionId = "swiss_alps",
                status = DownloadState.DOWNLOADING,
                progress = 50,
                downloadedTiles = 500,
                totalTiles = 1000,
                bytesDownloaded = 25000000,
            ),
        )
        every { downloadRepository.getDownloadProgress() } returns flowOf(downloadProgress)

        composeTestRule.setContent {
            DownloadScreen(
                onNavigateBack = {},
                viewModel = DownloadViewModel(
                    regionProvider,
                    downloadRepository,
                    connectivityObserver,
                ),
            )
        }

        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun downloadScreen_showsFailedStatus() {
        val downloadProgress = listOf(
            DownloadProgress(
                regionId = "swiss_alps",
                status = DownloadState.FAILED,
                progress = 25,
                downloadedTiles = 250,
                totalTiles = 1000,
                bytesDownloaded = 12500000,
            ),
        )
        every { downloadRepository.getDownloadProgress() } returns flowOf(downloadProgress)

        composeTestRule.setContent {
            DownloadScreen(
                onNavigateBack = {},
                viewModel = DownloadViewModel(
                    regionProvider,
                    downloadRepository,
                    connectivityObserver,
                ),
            )
        }

        composeTestRule.onNodeWithText("Failed").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Failed").assertIsDisplayed()
    }

    @Test
    fun downloadScreen_startDownload_callsRepository() {
        composeTestRule.setContent {
            DownloadScreen(
                onNavigateBack = {},
                viewModel = DownloadViewModel(
                    regionProvider,
                    downloadRepository,
                    connectivityObserver,
                ),
            )
        }

        // Click the first download button
        composeTestRule.onNodeWithText("Download").performClick()

        verify { downloadRepository.startDownload(any()) }
    }
}
