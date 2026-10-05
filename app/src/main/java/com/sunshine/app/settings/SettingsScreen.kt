package com.sunshine.app.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sunshine.app.R
import com.sunshine.app.SunshineApp
import com.sunshine.app.about.AboutSection
import com.sunshine.app.map.LOCATION_PERMISSIONS
import com.sunshine.app.map.LocationAccess
import com.sunshine.app.map.locationAccess
import kotlin.math.roundToInt

/**
 * The Settings page (settings spec, "Settings page"; design D10 of add-settings): the sections
 * Display, Map, Calculation, Storage, Debug and About. The system back returns to the map.
 */
@Composable
fun SettingsScreen(
    onCustomResolution: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = settingsViewModelFactory),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val canClear by viewModel.canClear.collectAsStateWithLifecycle()
    // Whether the last clear removed every browsed tile; `null` before any clear on this page.
    var cleared by remember { mutableStateOf<Boolean?>(null) }
    val context = LocalContext.current
    // Read again on every resume: the user may have changed it in the system settings meanwhile.
    var locationAllowed by remember { mutableStateOf(context.locationAccess() != LocationAccess.NONE) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { locationAllowed = context.locationAccess() != LocationAccess.NONE }
    // `My location` stays selected whatever the answer (gps-location spec, "Location permission").
    val askLocation =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            locationAllowed = context.locationAccess() != LocationAccess.NONE
        }
    val onStartAt = { startAt: StartAt ->
        viewModel.onStartAt(startAt)
        if (startAt == StartAt.MY_LOCATION && !locationAllowed) askLocation.launch(LOCATION_PERMISSIONS)
    }
    Surface(modifier.fillMaxSize()) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)

            Section(R.string.settings_display)
            ChoiceEntry(R.string.settings_coordinates, COORDINATE_LABELS, settings.coordinates, viewModel::onCoordinates)
            SwitchEntry(R.string.settings_keep_screen_on, settings.keepScreenOn, viewModel::onKeepScreenOn)

            Section(R.string.settings_map)
            ChoiceEntry(R.string.settings_start_at, START_AT_LABELS, settings.startAt, onStartAt)
            if (settings.startAt == StartAt.MY_LOCATION && !locationAllowed) {
                Text(stringResource(R.string.settings_location_off), style = MaterialTheme.typography.bodySmall)
            }
            OpacityEntry(settings.overlayOpacityPercent, viewModel::onOverlayOpacity)

            Section(R.string.settings_calculation)
            ChoiceEntry(R.string.settings_resolution, PRESET_LABELS, settings.preset, viewModel::onPreset)
            if (settings.preset == Preset.CUSTOM) {
                LinkEntry(R.string.settings_custom_resolution, onCustomResolution)
            }

            Section(R.string.settings_storage)
            ChoiceEntry(
                R.string.settings_browsed_limit,
                Settings.BROWSED_LIMITS_MIB.map { it to { stringResource(R.string.settings_mib, it) } },
                settings.browsedLimitMib,
                viewModel::onBrowsedLimit,
            )
            Text(stringResource(R.string.settings_browsed_limit_note), style = MaterialTheme.typography.bodySmall)
            // No dialog (user decision); disabled while a region is not complete (offline-regions spec,
            // "Clear browsed tiles").
            OutlinedButton(onClick = { viewModel.onClearBrowsed { cleared = it } }, enabled = canClear) {
                Text(stringResource(R.string.settings_clear_browsed))
            }
            when {
                !canClear -> Text(stringResource(R.string.settings_clear_unavailable), style = MaterialTheme.typography.bodySmall)
                cleared == true -> Text(stringResource(R.string.settings_cleared), style = MaterialTheme.typography.bodySmall)
                cleared == false -> Text(stringResource(R.string.settings_clear_failed), style = MaterialTheme.typography.bodySmall)
            }

            // In every build, each switch off by default (settings spec, "Debug info"; design D6 of polish-overlay).
            Section(R.string.settings_debug)
            val debug = settings.debug
            SwitchEntry(R.string.settings_debug_timings, debug.timings) { viewModel.onDebug(debug.copy(timings = it)) }
            SwitchEntry(R.string.settings_debug_tiles, debug.tiles) { viewModel.onDebug(debug.copy(tiles = it)) }
            SwitchEntry(R.string.settings_debug_day_state, debug.dayState) { viewModel.onDebug(debug.copy(dayState = it)) }
            SwitchEntry(R.string.settings_debug_agreement, debug.agreementCheck) { viewModel.onDebug(debug.copy(agreementCheck = it)) }

            Section(R.string.about_title)
            AboutSection()
        }
    }
}

@Composable
private fun Section(
    @StringRes title: Int,
) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp),
    )
}

/** An entry showing its value; a tap opens a dialog with one radio button per option. */
@Composable
private fun <T> ChoiceEntry(
    @StringRes label: Int,
    options: List<Pair<T, @Composable () -> String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val selectedLabel = options.first { it.first == selected }.second()
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = stringResource(label)) { open = true },
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
        Text(selectedLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(label)) },
            text = {
                Column {
                    for ((value, text) in options) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(selected = value == selected, role = Role.RadioButton) {
                                    open = false
                                    onSelect(value)
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = value == selected, onClick = null)
                            Text(text(), Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
}

@Composable
private fun SwitchEntry(
    @StringRes label: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** An entry that opens another page. */
@Composable
private fun LinkEntry(
    @StringRes label: Int,
    onClick: () -> Unit,
) {
    Text(
        stringResource(label),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
    )
}

/** A slider of 20 % to 90 % in steps of 10 %, stored when the drag ends. */
@Composable
private fun OpacityEntry(
    percent: Int,
    onChange: (Int) -> Unit,
) {
    var dragged by remember(percent) { mutableFloatStateOf(percent.toFloat()) }
    val first = Settings.OPACITIES.first
    val last = Settings.OPACITIES.last
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_overlay_opacity), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.settings_percent, dragged.roundToInt()), style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = dragged,
            onValueChange = { dragged = it },
            onValueChangeFinished = { onChange(dragged.roundToInt()) },
            valueRange = first.toFloat()..last.toFloat(),
            // Positions between the ends: 30 % to 80 %.
            steps = (last - first) / Settings.OPACITIES.step - 1,
        )
    }
}

private val COORDINATE_LABELS: List<Pair<CoordinateFormat, @Composable () -> String>> =
    listOf(
        CoordinateFormat.DECIMAL to { stringResource(R.string.settings_coordinates_decimal) },
        CoordinateFormat.DMS to { stringResource(R.string.settings_coordinates_dms) },
        CoordinateFormat.LV95 to { stringResource(R.string.settings_coordinates_lv95) },
    )

private val START_AT_LABELS: List<Pair<StartAt, @Composable () -> String>> =
    listOf(
        StartAt.LAST_VIEW to { stringResource(R.string.settings_start_last_view) },
        StartAt.MY_LOCATION to { stringResource(R.string.settings_start_my_location) },
        StartAt.ALPS_OVERVIEW to { stringResource(R.string.settings_start_alps) },
    )

private val PRESET_LABELS: List<Pair<Preset, @Composable () -> String>> =
    listOf(
        Preset.FAST to { stringResource(R.string.settings_resolution_fast) },
        Preset.NORMAL to { stringResource(R.string.settings_resolution_normal) },
        Preset.DETAILED to { stringResource(R.string.settings_resolution_detailed) },
        Preset.CUSTOM to { stringResource(R.string.settings_resolution_custom) },
    )

internal val settingsViewModelFactory =
    viewModelFactory {
        initializer {
            val application = checkNotNull(this[APPLICATION_KEY]) { "SettingsViewModel needs the Application" } as SunshineApp
            SettingsViewModel(
                store = application.settingsStore,
                regionNotComplete = application.regionNotComplete,
                clearBrowsed = application::clearBrowsedTiles,
            )
        }
    }
