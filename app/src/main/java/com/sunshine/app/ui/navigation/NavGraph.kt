@file:Suppress("MatchingDeclarationName") // File contains both Screen sealed class and NavHost

package com.sunshine.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sunshine.app.ui.screens.download.DownloadScreen
import com.sunshine.app.ui.screens.map.MapScreen
import com.sunshine.app.ui.screens.settings.SettingsScreen

sealed class Screen(
    val route: String,
) {
    data object Map : Screen("map")

    data object Settings : Screen("settings")

    data object Download : Screen("download")
}

@Composable
fun SunshineNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Map.route,
) {
    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(Screen.Map.route) {
            MapScreen(
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToDownload = { navController.navigate(Screen.Download.route) },
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(Screen.Download.route) {
            DownloadScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
