package com.sunshine.app.map

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.sunshine.app.R
import java.time.ZonedDateTime

/**
 * The overlay switch, below it the progress of the day's computation while it runs ([dayProgress]
 * from 0 to 1, or `null`), and while the overlay is on the [mode] control and the legend of the mode
 * (sun-shade-overlay spec, "Overlay toggle", "Overlay appearance", "Overlay of the whole day";
 * design D10 of add-sun-shade-overlay; sun-exposure-heatmap spec, "Overlay mode", "Heatmap legend";
 * design D6 of add-sun-exposure-heatmap). The heatmap's scale is shown once its [bands] are known.
 */
@Composable
fun OverlayControl(
    isOn: Boolean,
    dayProgress: Float?,
    mode: OverlayMode,
    bands: HeatmapBands?,
    onToggle: () -> Unit,
    onModeSelected: (OverlayMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ChipWithBar(
            chip = {
                FilterChip(
                    selected = isOn,
                    onClick = onToggle,
                    label = { Text(stringResource(R.string.overlay_toggle)) },
                )
            },
            bar = dayProgress?.let { progress -> { LinearProgressIndicator(progress = { progress }) } },
        )
        if (isOn) {
            ModeControl(mode, onModeSelected)
            Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = LEGEND_ALPHA), shape = MaterialTheme.shapes.small) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    when (mode) {
                        OverlayMode.SUN_AND_SHADE -> LegendRow(R.string.overlay_legend_shade) { drawRect(Color(SHADE_ARGB)) }
                        OverlayMode.SUN_HOURS -> bands?.let { BandLegend(it) }
                    }
                    LegendRow(R.string.overlay_legend_unknown) { drawHatching() }
                }
            }
        }
    }
}

/**
 * The notice for [state], if any: zoomed out, or computing while the overlay on screen, if any,
 * belongs to another time than [selectedTime].
 */
@StringRes
fun overlayNotice(
    state: OverlayUiState,
    selectedTime: ZonedDateTime,
): Int? =
    when (state) {
        OverlayUiState.ZoomedOut -> R.string.overlay_zoom_in
        is OverlayUiState.Computing -> if (state.kept?.time != selectedTime) R.string.overlay_computing else null
        OverlayUiState.Off, is OverlayUiState.Ready -> null
    }

/** The heatmap's notice for [state], if any: zoomed out, or computing (sun-exposure-heatmap spec, "Heatmap updates"). */
@StringRes
fun heatmapNotice(state: HeatmapUiState): Int? =
    when (state) {
        HeatmapUiState.ZoomedOut -> R.string.overlay_zoom_in
        is HeatmapUiState.Computing -> R.string.heatmap_computing
        HeatmapUiState.Off, is HeatmapUiState.Ready -> null
    }

/** The notice of the overlay in [mode]: [overlay]'s in `Sun & shade`, [heatmap]'s in `Sun hours`. */
@StringRes
fun notice(
    mode: OverlayMode,
    overlay: OverlayUiState,
    heatmap: HeatmapUiState,
    selectedTime: ZonedDateTime,
): Int? =
    when (mode) {
        OverlayMode.SUN_AND_SHADE -> overlayNotice(overlay, selectedTime)
        OverlayMode.SUN_HOURS -> heatmapNotice(heatmap)
    }

/** The legend's labels: `0 h`, `2 h`, … at the index of the band where each whole 2 hours begins. */
fun legendLabels(bands: HeatmapBands): List<Pair<Int, String>> =
    (0 until bands.count step BANDS_PER_LABEL).map { band -> band to "${band * BAND_MINUTES / MINUTES_PER_HOUR} h" }

@Composable
private fun ModeControl(
    mode: OverlayMode,
    onModeSelected: (OverlayMode) -> Unit,
) {
    val labels = listOf(R.string.heatmap_mode_sun_and_shade, R.string.heatmap_mode_sun_hours)
    SingleChoiceSegmentedButtonRow {
        OverlayMode.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = mode == option,
                onClick = { onModeSelected(option) },
                shape = SegmentedButtonDefaults.itemShape(index, OverlayMode.entries.size),
                label = { Text(stringResource(labels[index])) },
            )
        }
    }
}

// The bands' colours side by side, with the labels of legendLabels below the band where each begins.
@Composable
private fun BandLegend(bands: HeatmapBands) {
    Column {
        Canvas(Modifier.size(BAND_BAR_WIDTH, 12.dp)) {
            val width = size.width / bands.count
            bands.colours.forEachIndexed { band, colour ->
                drawRect(Color(colour), topLeft = Offset(band * width, 0f), size = Size(width, size.height))
            }
        }
        Box(Modifier.width(BAND_BAR_WIDTH + LAST_LABEL_ROOM)) {
            for ((band, label) in legendLabels(bands)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.offset(x = BAND_BAR_WIDTH * band / bands.count),
                )
            }
        }
    }
}

// The chip, and below it the bar at exactly the chip's width: the bar's own default width (240 dp)
// would otherwise set the width.
@Composable
private fun ChipWithBar(
    chip: @Composable () -> Unit,
    bar: (@Composable () -> Unit)?,
) {
    Layout(content = {
        chip()
        bar?.invoke()
    }) { measurables, constraints ->
        val chipPlaceable = measurables[0].measure(constraints)
        val barPlaceable = measurables.getOrNull(1)?.measure(Constraints.fixedWidth(chipPlaceable.width))
        layout(chipPlaceable.width, chipPlaceable.height + (barPlaceable?.height ?: 0)) {
            chipPlaceable.place(0, 0)
            barPlaceable?.place(0, chipPlaceable.height)
        }
    }
}

@Composable
private fun LegendRow(
    @StringRes label: Int,
    swatch: DrawScope.() -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(16.dp), onDraw = swatch)
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
    }
}

// The overlay's hatching: stripes HATCH_WIDTH dp wide every HATCH_PERIOD dp, as in renderOverlay.
private fun DrawScope.drawHatching() {
    val period = HATCH_PERIOD.dp.toPx()
    val width = HATCH_WIDTH.dp.toPx()
    clipRect {
        var offset = -size.height
        while (offset < size.width) {
            drawLine(
                Color(UNKNOWN_ARGB),
                Offset(offset, size.height),
                Offset(offset + size.height, 0f),
                // The bitmap's stripes are HATCH_WIDTH wide along x, i.e. width / √2 across.
                strokeWidth = width / kotlin.math.sqrt(2f),
            )
            offset += period
        }
    }
}

private const val LEGEND_ALPHA = 0.85f
private const val BAND_MINUTES = 30
private const val MINUTES_PER_HOUR = 60
private const val BANDS_PER_LABEL = 2 * MINUTES_PER_HOUR / BAND_MINUTES
private val BAND_BAR_WIDTH = 160.dp

// Room for the last label, which may start near the bar's right end.
private val LAST_LABEL_ROOM = 24.dp
