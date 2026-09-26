package com.sunshine.app.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.sunshine.core.SunPosition
import kotlin.math.cos
import kotlin.math.sin

/**
 * Line from the centre (the crosshair) towards the sun's azimuth on the north-up map, 30 % of the
 * shorter side long; solid while the sun is above the horizon, dashed below.
 */
@Composable
fun SunLine(
    position: SunPosition,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.fillMaxSize()) {
        val start = center
        val end = start + sunLineEnd(position.azimuth, LENGTH_FRACTION * size.minDimension)
        val dash = if (position.isAboveHorizon) null else PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
        listOf(Color.White to 6.dp.toPx(), SUN_COLOR to 3.dp.toPx()).forEach { (color, width) ->
            drawLine(color, start, end, width, StrokeCap.Butt, dash)
        }
    }
}

/** Offset of the line's end from its start, in screen coordinates (y down) with north up. */
fun sunLineEnd(
    azimuth: Double,
    length: Float,
): Offset {
    val radians = Math.toRadians(azimuth)
    return Offset((sin(radians) * length).toFloat(), (-cos(radians) * length).toFloat())
}

private const val LENGTH_FRACTION = 0.3f
private val SUN_COLOR = Color(0xFFF57C00)
