package com.sunshine.app.settings

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sunshine.app.R
import com.sunshine.app.ui.PageTopBar
import kotlin.math.roundToInt

/**
 * The `Custom resolution` page (settings spec, "Custom resolution"; design D3 of add-settings): the
 * four values within their ranges. They are edited as a draft in [viewModel], which survives a
 * rotation, and stored when the system back leaves the page for the Settings page ([onBack]), so
 * that adjusting a value does not start a computation at every step.
 */
@Composable
fun CustomResolutionScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = settingsViewModelFactory),
) {
    LaunchedEffect(viewModel) { viewModel.onCustomOpened() }
    // The back arrow leaves the page like the system back: the values are stored (design D3 of add-settings).
    val leave = {
        viewModel.onCustomClosed()
        onBack()
    }
    BackHandler { leave() }
    val draft by viewModel.customDraft.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val values = draft ?: settings.custom
    val edit = viewModel::onCustomEdited
    Surface(modifier.fillMaxSize()) {
        // The top bar stays while the page scrolls (design D11 of polish-ui).
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
            PageTopBar(stringResource(R.string.settings_custom_resolution), leave)
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CellEntry(R.string.settings_custom_sun_shade_cell, values.sunShadeCellDp, Resolution.SUN_SHADE_CELLS) {
                    edit(values.copy(sunShadeCellDp = it))
                }
                StepEntry(R.string.settings_custom_sun_shade_step, values.sunShadeStepMinutes) {
                    edit(values.copy(sunShadeStepMinutes = it))
                }
                CellEntry(R.string.settings_custom_sun_hours_cell, values.sunHoursCellDp, Resolution.SUN_HOURS_CELLS) {
                    edit(values.copy(sunHoursCellDp = it))
                }
                StepEntry(R.string.settings_custom_sun_hours_step, values.sunHoursStepMinutes) {
                    edit(values.copy(sunHoursStepMinutes = it))
                }
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
