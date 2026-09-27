package com.sunshine.app.map

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sunshine.app.R

/**
 * The overlay switch and, while it is on, the legend (sun-shade-overlay spec, "Overlay toggle",
 * "Overlay appearance"; design D10 of add-sun-shade-overlay).
 */
@Composable
fun OverlayControl(
    isOn: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterChip(
            selected = isOn,
            onClick = onToggle,
            label = { Text(stringResource(R.string.overlay_toggle)) },
        )
        if (isOn) {
            Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = LEGEND_ALPHA), shape = MaterialTheme.shapes.small) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LegendRow(R.string.overlay_legend_shade) { drawRect(Color(SHADE_ARGB)) }
                    LegendRow(R.string.overlay_legend_unknown) { drawHatching() }
                }
            }
        }
    }
}

/** The notice for [state], if any: zoomed out, or computing without an overlay to keep. */
@StringRes
fun overlayNotice(state: OverlayUiState): Int? =
    when (state) {
        OverlayUiState.ZoomedOut -> R.string.overlay_zoom_in
        is OverlayUiState.Computing -> if (state.kept == null) R.string.overlay_computing else null
        OverlayUiState.Off, is OverlayUiState.Ready -> null
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
