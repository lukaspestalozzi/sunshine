package com.sunshine.app.map

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
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
import java.time.ZonedDateTime

/**
 * The overlay switch, below it the progress of the day's computation while it runs ([dayProgress]
 * from 0 to 1, or `null`), and while the overlay is on the legend (sun-shade-overlay spec, "Overlay
 * toggle", "Overlay appearance", "Overlay of the whole day"; design D10 of add-sun-shade-overlay).
 */
@Composable
fun OverlayControl(
    isOn: Boolean,
    dayProgress: Float?,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // As wide as the chip, so that the bar spans it.
        Column(Modifier.width(IntrinsicSize.Max)) {
            FilterChip(
                selected = isOn,
                onClick = onToggle,
                label = { Text(stringResource(R.string.overlay_toggle)) },
            )
            if (dayProgress != null) LinearProgressIndicator(progress = { dayProgress }, modifier = Modifier.fillMaxWidth())
        }
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
