package com.sunshine.app.ui.screens.map

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sunshine.app.R
import com.sunshine.app.ui.components.OsmMapView
import com.sunshine.app.ui.components.SunPositionIndicator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
                    actionLabel = "OK",
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

                // Crosshair dot at map center
                CrosshairDot(modifier = Modifier.align(Alignment.Center))

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
                    visibility != null && visibility.isSunVisible -> stringResource(R.string.sun_visible)
                    visibility != null && !visibility.isSunVisible && sunPosition.isAboveHorizon ->
                        stringResource(R.string.sun_blocked)
                    sunPosition.isAboveHorizon -> stringResource(R.string.sun_above_horizon)
                    else -> stringResource(R.string.sun_below_horizon)
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

            if (uiState.isElevationDegraded) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Decorative icon; adjacent text describes the warning
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.elevation_degraded),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Text(
                text = stringResource(R.string.elevation_format, sunPosition.elevation),
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = stringResource(R.string.azimuth_format, sunPosition.azimuth),
                style = MaterialTheme.typography.labelSmall,
            )

            // Show horizon angle if visibility data is available
            visibility?.let {
                Text(
                    text = stringResource(R.string.horizon_format, it.horizonAngle),
                    style = MaterialTheme.typography.labelSmall,
                )
                if (!it.isSunVisible && it.degreesUntilVisible != null) {
                    Text(
                        text = stringResource(R.string.degrees_until_visible, it.degreesUntilVisible),
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
                        text = stringResource(
                            R.string.sunrise_format,
                            sunrise.format(DateTimeFormatter.ofPattern("HH:mm")),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                uiState.sunsetTime?.let { sunset ->
                    Text(
                        text = stringResource(
                            R.string.sunset_format,
                            sunset.format(DateTimeFormatter.ofPattern("HH:mm")),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            // Terrain-aware first/last sunshine times
            if (uiState.firstSunshineTime != null || uiState.lastSunshineTime != null) {
                Spacer(modifier = Modifier.height(4.dp))
                uiState.firstSunshineTime?.let { first ->
                    Text(
                        text = stringResource(
                            R.string.first_sunshine_format,
                            first.format(DateTimeFormatter.ofPattern("HH:mm")),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                uiState.lastSunshineTime?.let { last ->
                    Text(
                        text = stringResource(
                            R.string.last_sunshine_format,
                            last.format(DateTimeFormatter.ofPattern("HH:mm")),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else if (uiState.isLoadingTerrainTimes) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.loading_sunshine_times),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            // Observer elevation and coordinates
            Spacer(modifier = Modifier.height(4.dp))
            visibility?.observerElevation?.let { elevation ->
                Text(
                    text = stringResource(R.string.altitude_format, elevation),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            val lat = uiState.mapCenter.latitude
            val lon = uiState.mapCenter.longitude
            Text(
                text = stringResource(
                    R.string.coordinates_format,
                    kotlin.math.abs(lat),
                    if (lat >= 0) "N" else "S",
                    kotlin.math.abs(lon),
                    if (lon >= 0) "E" else "W",
                ),
                style = MaterialTheme.typography.labelSmall,
            )

            // Loading indicators
            if (uiState.isLoadingVisibility) {
                Text(
                    text = stringResource(R.string.loading_terrain),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            if (uiState.isLoadingGrid) {
                Text(
                    text = stringResource(R.string.updating_overlay),
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
                    contentDescription = stringResource(R.string.select_date),
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
                        contentDescription = stringResource(R.string.reset_to_now),
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
                        text = stringResource(R.string.minus_one_hour),
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
                        text = stringResource(R.string.plus_one_hour),
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
                Text(stringResource(R.string.time_start), style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.weight(1f))
                Text(stringResource(R.string.time_midday), style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.weight(1f))
                Text(stringResource(R.string.time_end), style = MaterialTheme.typography.labelSmall)
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
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    ) {
        DatePicker(state = datePickerState)
    }
}

private const val CROSSHAIR_RADIUS = 6f
private const val CROSSHAIR_STROKE_WIDTH = 2f
private const val CROSSHAIR_FILL_COLOR = 0x66000000

@Composable
private fun CrosshairDot(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(12.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = CROSSHAIR_RADIUS * density
        val strokeWidth = CROSSHAIR_STROKE_WIDTH * density
        drawCircle(
            color = Color(CROSSHAIR_FILL_COLOR),
            radius = radius,
            center = center,
        )
        drawCircle(
            color = Color.White,
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth),
        )
    }
}
