package com.sunshine.app.ui.screens.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sunshine.app.R
import com.sunshine.app.ui.components.OsmMapView
import org.koin.androidx.compose.koinViewModel
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToDownload: () -> Unit,
    viewModel: MapViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                actions = {
                    IconButton(onClick = onNavigateToDownload) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = stringResource(R.string.nav_download),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.nav_settings),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // Map takes most of the space
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                OsmMapView(
                    center = uiState.mapCenter,
                    zoomLevel = uiState.zoomLevel,
                    onMapMoved = viewModel::onMapCenterChanged,
                    onZoomChanged = viewModel::onZoomChanged,
                    modifier = Modifier.fillMaxSize(),
                )

                // Sun position indicator overlay
                uiState.sunPosition?.let { sunPos ->
                    SunPositionOverlay(
                        sunPosition = sunPos,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    )
                }
            }

            // Time controls at the bottom
            TimeControlPanel(
                uiState = uiState,
                onDateSelected = viewModel::onDateSelected,
                onTimeSelected = viewModel::onTimeSelected,
                onResetToNow = viewModel::onResetToNow,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SunPositionOverlay(
    sunPosition: com.sunshine.app.domain.model.SunPosition,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = if (sunPosition.isAboveHorizon) "Sun: Above horizon" else "Sun: Below horizon",
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = "Elevation: ${String.format("%.1f", sunPosition.elevation)}°",
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = "Azimuth: ${String.format("%.1f", sunPosition.azimuth)}°",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun TimeControlPanel(
    uiState: MapUiState,
    onDateSelected: (java.time.LocalDate) -> Unit,
    onTimeSelected: (java.time.LocalTime) -> Unit,
    onResetToNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.padding(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            // Date display
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = uiState.selectedDate.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onResetToNow) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset to now",
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Time slider
            Text(
                text = uiState.selectedTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                style = MaterialTheme.typography.headlineMedium,
            )

            Slider(
                value = uiState.selectedTime.toSecondOfDay().toFloat(),
                onValueChange = { seconds ->
                    val time = java.time.LocalTime.ofSecondOfDay(seconds.toLong())
                    onTimeSelected(time)
                },
                valueRange = 0f..86399f, // 0 to 23:59:59
                steps = 0,
            )

            Row {
                Text("00:00", style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.weight(1f))
                Text("12:00", style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.weight(1f))
                Text("23:59", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
