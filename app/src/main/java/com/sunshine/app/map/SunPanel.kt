package com.sunshine.app.map

import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sunshine.app.R
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * The sun information panel (sun-position spec, "Sun information panel"; design D1 of polish-ui):
 * the header with the selected time, `Date` and `Now`; the headline with the day's sun periods;
 * the `Sun hours` line when shown; the time tape with its [strip]; and the details, expanded while
 * [detailsExpanded].
 */
@Composable
fun SunPanel(
    selectedTime: ZonedDateTime,
    today: LocalDate,
    sun: SunInfo?,
    elevation: ElevationState,
    sunshine: SunshineUiState,
    sunHours: String?,
    strip: List<StripState>,
    detailsExpanded: Boolean,
    onDetailsToggled: (Boolean) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onSliderMoved: (Float) -> Unit,
    onNowClicked: () -> Unit,
    onTimeTyped: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
    sliderStep: Int = SLIDER_STEP_MINUTES,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = PANEL_ALPHA),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Tapping the time opens the clock dialog (time-selection spec, "Exact time").
                Text(
                    text = formatHeaderTime(selectedTime, today),
                    style = MaterialTheme.typography.titleSmall,
                    modifier =
                        Modifier
                            .weight(1f)
                            .clickable(role = Role.Button) { showTimePicker = true }
                            .padding(vertical = 12.dp),
                )
                TextButton(onClick = { showDatePicker = true }) { Text(stringResource(R.string.sun_panel_date)) }
                TextButton(onClick = onNowClicked) { Text(stringResource(R.string.sun_panel_now)) }
            }
            // The headline: the day's sun periods, the panel's largest text (point-sunshine spec,
            // "Sunshine in the information panel").
            Text(
                "${stringResource(R.string.sun_panel_sunshine)} ${formatSunshine(sunshine, selectedTime)}",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = HEADLINE_SIZE),
            )
            // Only in the mode `Sun hours` (sun-exposure-heatmap spec, "Sun hours in the information panel").
            sunHours?.let { Value(R.string.sun_panel_sun_hours, it) }
            TimeTape(selectedTime, sliderStep, strip, onSliderMoved)
            DetailsRow(detailsExpanded, onDetailsToggled)
            AnimatedVisibility(detailsExpanded) {
                Column {
                    // Altitude and sunshine belong to the location and day, so they do not wait for the sun values.
                    Value(R.string.sun_panel_altitude, formatAltitude(elevation))
                    if (sun != null) SunValues(sun, selectedTime)
                    Value(R.string.sun_panel_time_zone, selectedTime.zone.id)
                }
            }
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
    if (showTimePicker) {
        SunTimePickerDialog(
            time = selectedTime,
            onTimeSelected = { hour, minute ->
                showTimePicker = false
                onTimeTyped(hour, minute)
            },
            onDismiss = { showTimePicker = false },
        )
    }
}

// The row that expands and collapses the details, 48 dp high to touch.
@Composable
private fun DetailsRow(
    expanded: Boolean,
    onToggled: (Boolean) -> Unit,
) {
    val description = stringResource(if (expanded) R.string.sun_panel_hide_details else R.string.sun_panel_show_details)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button) { onToggled(!expanded) }
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.sun_panel_details), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Text(if (expanded) "▴" else "▾", style = MaterialTheme.typography.labelLarge)
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

// A 24-hour clock at the selected hour and minute (time-selection spec, "Exact time"; design D6 of
// polish-ui). The platform's dialog: Material 3's TimePicker is still an experimental API.
@Composable
private fun SunTimePickerDialog(
    time: ZonedDateTime,
    onTimeSelected: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val currentOnTimeSelected by rememberUpdatedState(onTimeSelected)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    DisposableEffect(Unit) {
        val dialog = TimePickerDialog(context, { _, hour, minute -> currentOnTimeSelected(hour, minute) }, time.hour, time.minute, true)
        // OK, Cancel, back and a tap outside all end in a dismissal.
        dialog.setOnDismissListener { currentOnDismiss() }
        dialog.show()
        onDispose {
            dialog.setOnDismissListener(null)
            dialog.dismiss()
        }
    }
}

private const val PANEL_ALPHA = 0.85f
private val HEADLINE_SIZE = 18.sp
