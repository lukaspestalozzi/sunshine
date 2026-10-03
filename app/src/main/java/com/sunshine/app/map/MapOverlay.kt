package com.sunshine.app.map

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
 * and attributions") and to its right the Offline button ([onOfflineClicked]; offline-regions spec,
 * "Offline button") and the location button in its [locationButton] state ([onLocationClicked];
 * gps-location spec, "Location button"), below them the selected-location coordinates and the offline
 * notice, the [topEnd] controls and the [bottomPanel], kept clear of the system bars.
 */
@Composable
fun MapLabels(
    camera: CameraState,
    isOffline: Boolean,
    onAboutClicked: () -> Unit,
    onOfflineClicked: () -> Unit,
    locationButton: LocationButtonState,
    onLocationClicked: () -> Unit,
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
            // Not in one row with the coordinates: they did not fit beside the overlay toggle on narrow
            // phones (design D8 of add-sun-exposure-heatmap). The two buttons are narrower than the
            // coordinates (design D9 of add-offline-regions).
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = floatingSurfaceColor(), shape = MaterialTheme.shapes.medium) {
                    IconButton(onClick = onAboutClicked) {
                        Icon(painterResource(R.drawable.ic_info), contentDescription = stringResource(R.string.about_button))
                    }
                }
                Surface(color = floatingSurfaceColor(), shape = MaterialTheme.shapes.medium) {
                    IconButton(onClick = onOfflineClicked) {
                        Icon(painterResource(R.drawable.ic_offline), contentDescription = stringResource(R.string.offline_button))
                    }
                }
                Surface(color = floatingSurfaceColor(), shape = MaterialTheme.shapes.medium) {
                    IconButton(onClick = onLocationClicked) { LocationIcon(locationButton) }
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

/**
 * The location button's icon: `my_location` (filled) when ready, `location_searching` otherwise,
 * pulsing while waiting (design D6 of add-gps-location).
 */
@Composable
private fun LocationIcon(state: LocationButtonState) {
    val alpha =
        if (state == LocationButtonState.WAITING) {
            val pulse by rememberInfiniteTransition(label = "location pulse").animateFloat(
                initialValue = 1f,
                targetValue = PULSE_MIN_ALPHA,
                animationSpec = infiniteRepeatable(tween(PULSE_HALF_MILLIS), RepeatMode.Reverse),
                label = "location pulse alpha",
            )
            pulse
        } else {
            1f
        }
    val icon = if (state == LocationButtonState.READY) R.drawable.ic_my_location else R.drawable.ic_location_searching
    Icon(painterResource(icon), contentDescription = stringResource(R.string.location_button), Modifier.alpha(alpha))
}

private const val LABEL_ALPHA = 0.85f

// One pulse per second: half a second down to 0.3 and back.
private const val PULSE_MIN_ALPHA = 0.3f
private const val PULSE_HALF_MILLIS = 500
