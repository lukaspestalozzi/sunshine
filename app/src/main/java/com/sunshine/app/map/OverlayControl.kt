package com.sunshine.app.map

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sunshine.app.R
import java.time.ZonedDateTime

/**
 * The three-way overlay toggle showing [option] and, while the overlay is on, the status card of
 * its width below it: the mode's name, its [notice] and the mode's legend (sun-shade-overlay spec,
 * "Overlay toggle", "Overlay status card", "Overlay appearance"; sun-exposure-heatmap spec, "Heatmap
 * legend"; design D7 of add-sun-exposure-heatmap). The day's progress is on the time tape (design D5
 * of polish-ui). The heatmap's scale is shown once its [bands] are known.
 */
@Composable
fun OverlayControl(
    option: OverlayOption,
    @StringRes notice: Int?,
    bands: HeatmapBands?,
    onOptionSelected: (OverlayOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.width(TOGGLE_WIDTH), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OverlayToggle(option, onOptionSelected)
        val (name, legend) =
            when (option) {
                OverlayOption.OFF -> return@Column
                OverlayOption.SUN_AND_SHADE -> R.string.heatmap_mode_sun_and_shade to @Composable { ShadeLegend() }
                OverlayOption.SUN_HOURS -> R.string.heatmap_mode_sun_hours to @Composable { bands?.let { BandLegend(it) } }
            }
        Surface(Modifier.fillMaxWidth(), color = floatingSurfaceColor(), shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(name), style = MaterialTheme.typography.labelLarge)
                notice?.let { Text(stringResource(it), style = MaterialTheme.typography.labelMedium) }
                legend()
                LegendRow(R.string.overlay_legend_unknown) { drawHatching() }
            }
        }
    }
}

/** The background of every floating element on the map (design D7 of add-sun-exposure-heatmap). */
@Composable
fun floatingSurfaceColor(): Color = MaterialTheme.colorScheme.surface.copy(alpha = FLOATING_ALPHA)

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
        is OverlayUiState.Computing -> if (state.kept?.time != selectedTime || state.resolutionChanged) R.string.overlay_computing else null
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

/**
 * The legend's labels: `0`, `2`, … at the index of the band where each whole 2 hours begins, with
 * the unit after the last one only, so that they fit side by side (design D7 of add-sun-exposure-heatmap).
 */
fun legendLabels(bands: HeatmapBands): List<Pair<Int, String>> {
    val starts = (0 until bands.count step BANDS_PER_LABEL).toList()
    return starts.map { band -> band to "${band * BAND_MINUTES / MINUTES_PER_HOUR}" + if (band == starts.last()) " h" else "" }
}

/** The bands' colours, opaque: the map's translucency would wash them out on the card. */
fun legendColours(bands: HeatmapBands): IntArray = IntArray(bands.count) { bands.colours[it] or OPAQUE }

// Icons only, at a fixed width, so that nothing wraps (design D7 of add-sun-exposure-heatmap).
@Composable
private fun OverlayToggle(
    option: OverlayOption,
    onOptionSelected: (OverlayOption) -> Unit,
) {
    Surface(color = floatingSurfaceColor(), shape = CircleShape) {
        SingleChoiceSegmentedButtonRow(Modifier.width(TOGGLE_WIDTH)) {
            OverlayOption.entries.forEachIndexed { index, entry ->
                val (icon, description) = TOGGLE_ICONS.getValue(entry)
                SegmentedButton(
                    selected = option == entry,
                    onClick = { onOptionSelected(entry) },
                    shape = SegmentedButtonDefaults.itemShape(index, OverlayOption.entries.size),
                    // No check mark: it would widen the selected button.
                    icon = {},
                    label = { Icon(painterResource(icon), contentDescription = stringResource(description)) },
                )
            }
        }
    }
}

// The bands' colours side by side, with the labels of legendLabels below the band where each begins.
@Composable
private fun BandLegend(bands: HeatmapBands) {
    Column {
        Canvas(Modifier.fillMaxWidth().height(12.dp)) {
            val width = size.width / bands.count
            legendColours(bands).forEachIndexed { band, colour ->
                drawRect(Color(colour), topLeft = Offset(band * width, 0f), size = Size(width, size.height))
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            for ((band, label) in legendLabels(bands)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.offset(x = maxWidth * band / bands.count),
                )
            }
        }
    }
}

@Composable
private fun ShadeLegend() = LegendRow(R.string.overlay_legend_shade) { drawRect(Color(SHADE_ARGB or OPAQUE)) }

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

private const val FLOATING_ALPHA = 0.85f
private const val OPAQUE = 0xFF shl 24
private const val BAND_MINUTES = 30
private const val MINUTES_PER_HOUR = 60
private const val BANDS_PER_LABEL = 2 * MINUTES_PER_HOUR / BAND_MINUTES
private val TOGGLE_WIDTH = 168.dp
private val TOGGLE_ICONS =
    mapOf(
        OverlayOption.OFF to (R.drawable.ic_layers_clear to R.string.overlay_option_off),
        OverlayOption.SUN_AND_SHADE to (R.drawable.ic_contrast to R.string.overlay_option_sun_and_shade),
        OverlayOption.SUN_HOURS to (R.drawable.ic_timelapse to R.string.overlay_option_sun_hours),
    )
