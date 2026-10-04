package com.sunshine.app.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sunshine.app.R
import kotlin.math.roundToInt

/**
 * The `Custom resolution` page (settings spec, "Custom resolution"; design D3 of add-settings): the
 * four values within their ranges. They are edited as a copy and stored when the page is left, so
 * that adjusting a value does not start a computation at every step. The system back returns to
 * the Settings page.
 */
@Composable
fun CustomResolutionScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = settingsViewModelFactory),
) {
    var values by remember { mutableStateOf(viewModel.settings.value.custom) }
    val current by rememberUpdatedState(values)
    DisposableEffect(viewModel) { onDispose { viewModel.onCustom(current) } }
    Surface(modifier.fillMaxSize()) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.settings_custom_resolution), style = MaterialTheme.typography.headlineSmall)
            CellEntry(R.string.settings_custom_sun_shade_cell, values.sunShadeCellDp, Resolution.SUN_SHADE_CELLS) {
                values = values.copy(sunShadeCellDp = it)
            }
            StepEntry(R.string.settings_custom_sun_shade_step, values.sunShadeStepMinutes) {
                values = values.copy(sunShadeStepMinutes = it)
            }
            CellEntry(R.string.settings_custom_sun_hours_cell, values.sunHoursCellDp, Resolution.SUN_HOURS_CELLS) {
                values = values.copy(sunHoursCellDp = it)
            }
            StepEntry(R.string.settings_custom_sun_hours_step, values.sunHoursStepMinutes) {
                values = values.copy(sunHoursStepMinutes = it)
            }
        }
    }
}

/** A cell size in whole dp within [range]. */
@Composable
private fun CellEntry(
    @StringRes label: Int,
    dp: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        LabelRow(label, stringResource(R.string.settings_dp, dp))
        Slider(
            value = dp.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            // One position per whole dp between the ends.
            steps = range.last - range.first - 1,
        )
    }
}

/** A step of 5, 10, 15, 20 or 30 minutes. */
@Composable
private fun StepEntry(
    @StringRes label: Int,
    minutes: Int,
    onChange: (Int) -> Unit,
) {
    val steps = Resolution.STEPS
    Column(Modifier.fillMaxWidth()) {
        LabelRow(label, stringResource(R.string.settings_minutes, minutes))
        Slider(
            value = steps.indexOf(minutes).coerceAtLeast(0).toFloat(),
            onValueChange = { onChange(steps[it.roundToInt()]) },
            valueRange = 0f..steps.lastIndex.toFloat(),
            steps = steps.size - 2,
        )
    }
}

@Composable
private fun LabelRow(
    @StringRes label: Int,
    value: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
