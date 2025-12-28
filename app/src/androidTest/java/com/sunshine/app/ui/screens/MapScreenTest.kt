package com.sunshine.app.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sunshine.app.domain.model.GeoPoint
import com.sunshine.app.domain.model.SunPosition
import com.sunshine.app.domain.model.VisibilityResult
import com.sunshine.app.domain.usecase.CalculateSunVisibilityUseCase
import com.sunshine.app.suncalc.SunCalculator
import com.sunshine.app.ui.screens.map.MapScreen
import com.sunshine.app.ui.screens.map.MapViewModel
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for MapScreen using Compose testing framework.
 */
@RunWith(AndroidJUnit4::class)
class MapScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var sunCalculator: SunCalculator
    private lateinit var visibilityUseCase: CalculateSunVisibilityUseCase

    @Before
    fun setup() {
        sunCalculator = mockk()
        visibilityUseCase = mockk()

        // Default mock responses
        coEvery { sunCalculator.calculateSunPosition(any(), any()) } returns
            SunPosition(azimuth = 180.0, elevation = 45.0)
        coEvery { visibilityUseCase.calculateVisibility(any(), any()) } returns
            Result.success(
                VisibilityResult.visible(
                    location = GeoPoint.DEFAULT,
                    sunPosition = SunPosition(azimuth = 180.0, elevation = 45.0),
                    horizonAngle = 0.0,
                ),
            )
    }

    @Test
    fun mapScreen_displaysAppTitle() {
        composeTestRule.setContent {
            MapScreen(
                onNavigateToSettings = {},
                onNavigateToDownload = {},
                viewModel = MapViewModel(sunCalculator, visibilityUseCase),
            )
        }

        composeTestRule.onNodeWithText("Sunshine").assertIsDisplayed()
    }

    @Test
    fun mapScreen_displaysTimeControls() {
        composeTestRule.setContent {
            MapScreen(
                onNavigateToSettings = {},
                onNavigateToDownload = {},
                viewModel = MapViewModel(sunCalculator, visibilityUseCase),
            )
        }

        // Time labels at the bottom
        composeTestRule.onNodeWithText("00:00").assertIsDisplayed()
        composeTestRule.onNodeWithText("12:00").assertIsDisplayed()
        composeTestRule.onNodeWithText("23:59").assertIsDisplayed()
    }

    @Test
    fun mapScreen_showsSettingsButton() {
        composeTestRule.setContent {
            MapScreen(
                onNavigateToSettings = {},
                onNavigateToDownload = {},
                viewModel = MapViewModel(sunCalculator, visibilityUseCase),
            )
        }

        composeTestRule.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }

    @Test
    fun mapScreen_showsDownloadButton() {
        composeTestRule.setContent {
            MapScreen(
                onNavigateToSettings = {},
                onNavigateToDownload = {},
                viewModel = MapViewModel(sunCalculator, visibilityUseCase),
            )
        }

        composeTestRule.onNodeWithContentDescription("Download Tiles").assertIsDisplayed()
    }

    @Test
    fun mapScreen_settingsButtonNavigates() {
        var navigated = false

        composeTestRule.setContent {
            MapScreen(
                onNavigateToSettings = { navigated = true },
                onNavigateToDownload = {},
                viewModel = MapViewModel(sunCalculator, visibilityUseCase),
            )
        }

        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        assert(navigated) { "Settings navigation should be triggered" }
    }

    @Test
    fun mapScreen_downloadButtonNavigates() {
        var navigated = false

        composeTestRule.setContent {
            MapScreen(
                onNavigateToSettings = {},
                onNavigateToDownload = { navigated = true },
                viewModel = MapViewModel(sunCalculator, visibilityUseCase),
            )
        }

        composeTestRule.onNodeWithContentDescription("Download Tiles").performClick()
        assert(navigated) { "Download navigation should be triggered" }
    }

    @Test
    fun mapScreen_showsResetToNowButton() {
        composeTestRule.setContent {
            MapScreen(
                onNavigateToSettings = {},
                onNavigateToDownload = {},
                viewModel = MapViewModel(sunCalculator, visibilityUseCase),
            )
        }

        composeTestRule.onNodeWithContentDescription("Reset to now").assertIsDisplayed()
    }

    @Test
    fun mapScreen_displaysSunPosition_whenCalculated() {
        composeTestRule.setContent {
            MapScreen(
                onNavigateToSettings = {},
                onNavigateToDownload = {},
                viewModel = MapViewModel(sunCalculator, visibilityUseCase),
            )
        }

        // Wait for async calculation
        composeTestRule.waitForIdle()

        // Sun position overlay should show elevation and azimuth
        composeTestRule.onNodeWithText("Elevation: 45.0°", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Azimuth: 180.0°", substring = true).assertIsDisplayed()
    }

    @Test
    fun mapScreen_showsSunVisible_whenAboveHorizon() {
        composeTestRule.setContent {
            MapScreen(
                onNavigateToSettings = {},
                onNavigateToDownload = {},
                viewModel = MapViewModel(sunCalculator, visibilityUseCase),
            )
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Sun: Visible", substring = true).assertIsDisplayed()
    }

    @Test
    fun mapScreen_showsSunBlockedByTerrain_whenTerrainBlocks() {
        // Setup mock for blocked visibility
        coEvery { visibilityUseCase.calculateVisibility(any(), any()) } returns
            Result.success(
                VisibilityResult.blocked(
                    location = GeoPoint.DEFAULT,
                    sunPosition = SunPosition(azimuth = 180.0, elevation = 10.0),
                    horizonAngle = 20.0,
                    degreesUntilVisible = 10.0,
                ),
            )

        composeTestRule.setContent {
            MapScreen(
                onNavigateToSettings = {},
                onNavigateToDownload = {},
                viewModel = MapViewModel(sunCalculator, visibilityUseCase),
            )
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Sun: Blocked by terrain", substring = true).assertIsDisplayed()
    }

    @Test
    fun mapScreen_showsBelowHorizon_whenSunIsDown() {
        // Setup mock for sun below horizon
        coEvery { sunCalculator.calculateSunPosition(any(), any()) } returns
            SunPosition(azimuth = 0.0, elevation = -10.0)
        coEvery { visibilityUseCase.calculateVisibility(any(), any()) } returns
            Result.success(
                VisibilityResult.belowHorizon(
                    location = GeoPoint.DEFAULT,
                    sunPosition = SunPosition(azimuth = 0.0, elevation = -10.0),
                ),
            )

        composeTestRule.setContent {
            MapScreen(
                onNavigateToSettings = {},
                onNavigateToDownload = {},
                viewModel = MapViewModel(sunCalculator, visibilityUseCase),
            )
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Sun: Below horizon", substring = true).assertIsDisplayed()
    }
}
