package com.sunshine.app.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sunshine.app.R
import java.time.LocalDate
import java.time.ZonedDateTime

/** Selected time with its controls, and the sun values for the selected location and time. */
@Composable
fun SunPanel(
    selectedTime: ZonedDateTime,
    sun: SunInfo?,
    elevation: ElevationState,
    sunshine: SunshineUiState,
    sunHours: String?,
    onDateSelected: (LocalDate) -> Unit,
    onSliderMoved: (Float) -> Unit,
    onNowClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = PANEL_ALPHA),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${formatSelectedTime(selectedTime)}  ${selectedTime.zone.id}",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showDatePicker = true }) { Text(stringResource(R.string.sun_panel_date)) }
                TextButton(onClick = onNowClicked) { Text(stringResource(R.string.sun_panel_now)) }
            }
            val timeOfDay = stringResource(R.string.sun_panel_time_of_day)
            Slider(
                value = sliderMinutes(selectedTime),
                onValueChange = onSliderMoved,
                valueRange = 0f..(sliderPositions(selectedTime.toLocalDate(), selectedTime.zone) - 1) * SLIDER_STEP_MINUTES.toFloat(),
                modifier = Modifier.semantics { contentDescription = timeOfDay },
            )
            // Altitude and sunshine belong to the location and day, so they do not wait for the sun values.
            Value(R.string.sun_panel_altitude, formatAltitude(elevation))
            Value(R.string.sun_panel_sunshine, formatSunshine(sunshine, selectedTime))
            // Only in the mode `Sun hours` (sun-exposure-heatmap spec, "Sun hours in the information panel").
            sunHours?.let { Value(R.string.sun_panel_sun_hours, it) }
            if (sun != null) SunValues(sun, selectedTime)
        }
    }

    if (showDatePicker) {
        SunDatePickerDialog(
            date = selectedTime.toLocalDate(),
            onDateSelected = {
                showDatePicker = false
                onDateSelected(it)
            },
            onDismiss = { showDatePicker = false },
        )
    }
}

// FlowRow wraps the values on narrow screens.
@Composable
private fun SunValues(
    sun: SunInfo,
    selectedTime: ZonedDateTime,
) {
    val day = sun.day
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Value(R.string.sun_panel_azimuth, formatAzimuth(sun.position.azimuth))
        Value(R.string.sun_panel_elevation, formatElevation(sun.position.elevation))
        Value(R.string.sun_panel_civil_dawn, formatEventTime(day.civilDawn, selectedTime))
        Value(R.string.sun_panel_sunrise, formatEventTime(day.sunrise, selectedTime))
        Value(R.string.sun_panel_sunset, formatEventTime(day.sunset, selectedTime))
        Value(R.string.sun_panel_civil_dusk, formatEventTime(day.civilDusk, selectedTime))
        Value(R.string.sun_panel_day_length, formatDayLength(day.dayLength))
    }
    formatWholeDay(day.wholeDay)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun Value(
    label: Int,
    value: String,
) {
    Text("${stringResource(label)} $value", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun SunDatePickerDialog(
    date: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = date.toDatePickerMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            val selectedMillis = state.selectedDateMillis
            TextButton(
                onClick = { selectedMillis?.let { onDateSelected(datePickerMillisToDate(it)) } },
                enabled = selectedMillis != null,
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

private const val PANEL_ALPHA = 0.85f
