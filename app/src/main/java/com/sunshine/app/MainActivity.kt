package com.sunshine.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.sunshine.app.map.MapScreen
import com.sunshine.app.offline.OfflineScreen
import com.sunshine.app.settings.CustomResolutionScreen
import com.sunshine.app.settings.SettingsScreen
import com.sunshine.core.GeoPoint
import com.sunshine.core.MapArea

/** The app's screens, switched without a navigation library (design D8 of add-sun-exposure-heatmap, D9 of add-offline-regions, D10 of add-settings). */
private enum class Screen { MAP, SETTINGS, CUSTOM_RESOLUTION, OFFLINE }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                // The map's view model belongs to the activity, so the map is as it was on return.
                var screen by rememberSaveable { mutableStateOf(Screen.MAP) }
                // The area visible when the Offline page was opened: centre, zoom, width and height in dp.
                var offlineArea by rememberSaveable { mutableStateOf(arrayListOf<Double>()) }
                when (screen) {
                    Screen.MAP ->
                        MapScreen(
                            onSettingsClicked = { screen = Screen.SETTINGS },
                            onOfflineClicked = { area ->
                                offlineArea =
                                    arrayListOf(area.center.latitude, area.center.longitude, area.zoom, area.widthDp, area.heightDp)
                                screen = Screen.OFFLINE
                            },
                        )
                    Screen.SETTINGS -> {
                        BackHandler { screen = Screen.MAP }
                        SettingsScreen(onCustomResolution = { screen = Screen.CUSTOM_RESOLUTION })
                    }
                    Screen.CUSTOM_RESOLUTION -> {
                        BackHandler { screen = Screen.SETTINGS }
                        CustomResolutionScreen()
                    }
                    Screen.OFFLINE -> {
                        BackHandler { screen = Screen.MAP }
                        val (latitude, longitude, zoom, width, height) = offlineArea
                        OfflineScreen(MapArea(GeoPoint(latitude, longitude), zoom, width, height))
                    }
                }
            }
        }
    }
}
