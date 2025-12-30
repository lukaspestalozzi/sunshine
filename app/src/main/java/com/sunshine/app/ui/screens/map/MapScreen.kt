package com.sunshine.app.ui.screens.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sunshine.app.R
import com.sunshine.app.ui.components.OsmMapView
import com.sunshine.app.ui.components.SunPositionIndicator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToDownload: () -> Unit,
    viewModel: MapViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show error in Snackbar when error state changes
    LaunchedEffect(uiState.error) {
        uiState.error?.let { errorMessage ->
            val result =
                snackbarHostState.showSnackbar(
                    message = errorMessage,
                    actionLabel = "Dismiss",
                    duration = SnackbarDuration.Short,
                )
            if (result == SnackbarResult.ActionPerformed || result == SnackbarResult.Dismissed) {
                viewModel.onErrorDismissed()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors =
                    TopAppBarDefaults.topAppBarColors(
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
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            // Map takes most of the space
            BoxWithConstraints(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
            ) {
                OsmMapView(
                    center = uiState.mapCenter,
                    zoomLevel = uiState.zoomLevel,
                    onMapMoved = viewModel::onMapCenterChanged,
                    onZoomChanged = viewModel::onZoomChanged,
                    visibilityGrid = uiState.visibilityGrid,
                    modifier = Modifier.fillMaxSize(),
                )

                // Sun position indicator at edge of map
                uiState.sunPosition?.let { sunPosition ->
                    SunPositionIndicator(
                        sunPosition = sunPosition,
                        isVisible = uiState.isSunVisibleWithTerrain,
                        containerWidth = maxWidth,
                        containerHeight = maxHeight,
                    )
                }

                // Sun position and visibility overlay
                if (uiState.sunPosition != null) {
                    SunPositionOverlay(
                        uiState = uiState,
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
                onAdjustTime = viewModel::onAdjustTime,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Suppress("CyclomaticComplexMethod") // UI conditional rendering naturally has many branches
@Composable
private fun SunPositionOverlay(
    uiState: MapUiState,
    modifier: Modifier = Modifier,
) {
    val sunPosition = uiState.sunPosition ?: return
    val visibility = uiState.visibility

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Visibility status (terrain-aware if available)
            val visibilityText =
                when {
                    visibility != null && visibility.isSunVisible -> "Sun: Visible"
                    visibility != null && !visibility.isSunVisible && sunPosition.isAboveHorizon ->
                        "Sun: Blocked by terrain"
                    sunPosition.isAboveHorizon -> "Sun: Above horizon"
                    else -> "Sun: Below horizon"
                }
            val visibilityColor =
                when {
                    uiState.isSunVisibleWithTerrain ->
                        MaterialTheme.colorScheme.primary
                    sunPosition.isAboveHorizon ->
                        MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.outline
                }

            Text(
                text = visibilityText,
                style = MaterialTheme.typography.labelMedium,
                color = visibilityColor,
            )

            Text(
                text = "Elevation: ${String.format(Locale.US, "%.1f", sunPosition.elevation)}°",
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = "Azimuth: ${String.format(Locale.US, "%.1f", sunPosition.azimuth)}°",
                style = MaterialTheme.typography.labelSmall,
            )

            // Show horizon angle if visibility data is available
            visibility?.let {
                Text(
                    text = "Horizon: ${String.format(Locale.US, "%.1f", it.horizonAngle)}°",
                    style = MaterialTheme.typography.labelSmall,
                )
                if (!it.isSunVisible && it.degreesUntilVisible != null) {
                    Text(
                        text = "${String.format(Locale.US, "%.1f", it.degreesUntilVisible)}° until visible",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }

            // Sunrise/Sunset times
            if (uiState.sunriseTime != null || uiState.sunsetTime != null) {
                Spacer(modifier = Modifier.height(4.dp))
                uiState.sunriseTime?.let { sunrise ->
                    Text(
                        text = "Sunrise: ${sunrise.format(DateTimeFormatter.ofPattern("HH:mm"))}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                uiState.sunsetTime?.let { sunset ->
                    Text(
                        text = "Sunset: ${sunset.format(DateTimeFormatter.ofPattern("HH:mm"))}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            // Loading indicators
            if (uiState.isLoadingVisibility) {
                Text(
                    text = "Loading terrain...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            if (uiState.isLoadingGrid) {
                Text(
                    text = "Updating overlay...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun TimeControlPanel(
    uiState: MapUiState,
    onDateSelected: (LocalDate) -> Unit,
    onTimeSelected: (java.time.LocalTime) -> Unit,
    onResetToNow: () -> Unit,
    onAdjustTime: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    // Date picker dialog
    if (showDatePicker) {
        DatePickerDialogContent(
            initialDate = uiState.selectedDate,
            onDateSelected = { date ->
                onDateSelected(date)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }

    Card(
        modifier = modifier.padding(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            // Date display - clickable to open date picker
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = "Select date",
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

            // Time display with playback controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // -1 hour button
                IconButton(onClick = { onAdjustTime(-1) }) {
                    Text(
                        text = "-1h",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Current time display
                Text(
                    text = uiState.selectedTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                    style = MaterialTheme.typography.headlineMedium,
                )

                Spacer(modifier = Modifier.width(16.dp))

                // +1 hour button
                IconButton(onClick = { onAdjustTime(1) }) {
                    Text(
                        text = "+1h",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Slider(
                value = uiState.selectedTime.toSecondOfDay().toFloat(),
                onValueChange = { seconds ->
                    val time = java.time.LocalTime.ofSecondOfDay(seconds.toLong())
                    onTimeSelected(time)
                },
                // 0 to 23:59:59
                valueRange = 0f..86399f,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialogContent(
    initialDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // Convert LocalDate to epoch millis for DatePicker
    val initialMillis =
        initialDate
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    val datePickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
        )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val selectedDate =
                            Instant
                                .ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                        onDateSelected(selectedDate)
                    }
                },
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    ) {
        DatePicker(state = datePickerState)
    }
}
