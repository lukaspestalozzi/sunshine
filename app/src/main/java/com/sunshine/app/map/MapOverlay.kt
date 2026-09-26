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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
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
 * Selected-location coordinates, offline notice, [bottomPanel] and attribution, kept clear of the
 * system bars. [bottomPanel] sits directly above the attribution, so it never covers it.
 */
@Composable
fun MapLabels(
    camera: CameraState,
    isOffline: Boolean,
    modifier: Modifier = Modifier,
    bottomPanel: @Composable () -> Unit = {},
) {
    Box(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(8.dp),
    ) {
        Column(
            modifier = Modifier.align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Label(text = formatCoordinates(camera.center))
            if (isOffline) {
                Label(
                    text = stringResource(R.string.map_offline_notice),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                )
            }
        }
        Column(
            modifier = Modifier.align(Alignment.BottomStart),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            bottomPanel()
            Label(
                text = stringResource(R.string.map_attribution),
                modifier = Modifier.testTag(ATTRIBUTION_TAG),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun Label(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelLarge,
    containerColor: Color = MaterialTheme.colorScheme.surface,
) {
    Surface(
        modifier = modifier,
        color = containerColor.copy(alpha = LABEL_ALPHA),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = text,
            style = style,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

private const val LABEL_ALPHA = 0.85f
