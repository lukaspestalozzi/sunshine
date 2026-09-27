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
import com.sunshine.core.Sunshine
import kotlin.math.cos
import kotlin.math.sin

/**
 * Line from the centre (the crosshair) towards the sun's azimuth on the north-up map, 30 % of the
 * shorter side long, drawn by the sunshine state at the selected time ([sunshine], `null` while
 * not computed); see [sunLineDash].
 */
@Composable
fun SunLine(
    position: SunPosition,
    sunshine: Sunshine?,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.fillMaxSize()) {
        val start = center
        val end = start + sunLineEnd(position.azimuth, LENGTH_FRACTION * size.minDimension)
        val dash =
            sunLineDash(
                position.isAboveHorizon,
                sunshine,
            )?.let { PathEffect.dashPathEffect(floatArrayOf(it.on.dp.toPx(), it.off.dp.toPx())) }
        listOf(Color.White to 6.dp.toPx(), SUN_COLOR to 3.dp.toPx()).forEach { (color, width) ->
            drawLine(color, start, end, width, StrokeCap.Butt, dash)
        }
    }
}

/** A dash pattern in dp: [on] drawn, [off] left out. */
data class SunLineDash(
    val on: Float,
    val off: Float,
)

/**
 * `null` (solid) in sun; dashed in shade, which includes every time the sun is below the
 * astronomical horizon; dotted while the sunshine state is unknown or not yet computed
 * (sun-position spec, "Sun direction line").
 */
fun sunLineDash(
    isAboveHorizon: Boolean,
    sunshine: Sunshine?,
): SunLineDash? =
    when {
        !isAboveHorizon || sunshine == Sunshine.SHADE -> SunLineDash(on = 8f, off = 6f)
        sunshine == Sunshine.SUN -> null
        else -> SunLineDash(on = 2f, off = 6f)
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
