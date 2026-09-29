package com.sunshine.app.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sunshine.app.R

/** Crosshair marking the selected location; must share the map's bounds so it sits on the map centre. */
@Composable
fun Crosshair(modifier: Modifier = Modifier) {
    Canvas(modifier.size(32.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val arm = size.minDimension / 2
        listOf(Color.White to 5.dp.toPx(), Color.Black to 2.dp.toPx()).forEach { (color, width) ->
            drawLine(color, center.copy(x = center.x - arm), center.copy(x = center.x + arm), width, StrokeCap.Round)
            drawLine(color, center.copy(y = center.y - arm), center.copy(y = center.y + arm), width, StrokeCap.Round)
        }
    }
}

/**
 * The ⓘ button opening the About page with the attributions ([onAboutClicked]; map-view spec, "About
 * and attributions"), below it the selected-location coordinates and the offline notice, the
 * [topEnd] controls and the [bottomPanel], kept clear of the system bars.
 */
@Composable
fun MapLabels(
    camera: CameraState,
    isOffline: Boolean,
    onAboutClicked: () -> Unit,
    modifier: Modifier = Modifier,
    topEnd: @Composable () -> Unit = {},
    bottomPanel: @Composable () -> Unit = {},
) {
    Box(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(8.dp),
    ) {
        Column(
            modifier = Modifier.align(Alignment.TopStart),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Alone in the corner: in one row with the coordinates it did not fit beside the overlay
            // toggle on narrow phones (design D8 of add-sun-exposure-heatmap).
            Surface(color = floatingSurfaceColor(), shape = MaterialTheme.shapes.medium) {
                IconButton(onClick = onAboutClicked) {
                    Icon(painterResource(R.drawable.ic_info), contentDescription = stringResource(R.string.about_button))
                }
            }
            Label(text = formatCoordinates(camera.center))
            if (isOffline) {
                Label(
                    text = stringResource(R.string.map_offline_notice),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                )
            }
        }
        Box(Modifier.align(Alignment.TopEnd)) { topEnd() }
        Box(Modifier.align(Alignment.BottomStart)) { bottomPanel() }
    }
}

@Composable
private fun Label(
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.surface,
) {
    Surface(
        color = containerColor.copy(alpha = LABEL_ALPHA),
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

private const val LABEL_ALPHA = 0.85f
