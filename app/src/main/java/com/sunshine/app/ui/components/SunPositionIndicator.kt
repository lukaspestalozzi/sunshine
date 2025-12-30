package com.sunshine.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.sunshine.app.domain.model.SunPosition
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// Sun indicator colors
private val SUN_VISIBLE_COLOR = Color(0xFFFFD700) // Gold
private val SUN_BLOCKED_COLOR = Color(0xFFFF8C00) // Dark orange
private val SUN_BELOW_HORIZON_COLOR = Color(0xFF6B7280) // Gray

/**
 * Displays a sun indicator at the edge of the map container showing the sun's direction.
 * The indicator is positioned based on the sun's azimuth angle.
 *
 * @param sunPosition Current sun position with azimuth and elevation
 * @param isVisible Whether the sun is currently visible (not blocked by terrain)
 * @param containerWidth Width of the container in dp
 * @param containerHeight Height of the container in dp
 * @param modifier Modifier for the indicator
 */
@Composable
fun SunPositionIndicator(
    sunPosition: SunPosition,
    isVisible: Boolean,
    containerWidth: Dp,
    containerHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    // Convert container dimensions to pixels
    val widthPx = with(density) { containerWidth.toPx() }
    val heightPx = with(density) { containerHeight.toPx() }

    // Calculate sun indicator position on the edge of the container
    val (offsetX, offsetY) =
        calculateEdgePosition(
            azimuth = sunPosition.azimuth,
            width = widthPx,
            height = heightPx,
        )

    // Indicator size
    val indicatorSize = 32.dp
    val halfSizePx = with(density) { indicatorSize.toPx() / 2 }

    // Determine color based on sun state
    val sunColor =
        when {
            !sunPosition.isAboveHorizon -> SUN_BELOW_HORIZON_COLOR
            isVisible -> SUN_VISIBLE_COLOR
            else -> SUN_BLOCKED_COLOR
        }

    Box(
        modifier =
            modifier
                .offset {
                    IntOffset(
                        x = (offsetX - halfSizePx).roundToInt(),
                        y = (offsetY - halfSizePx).roundToInt(),
                    )
                }
                .size(indicatorSize)
                .shadow(4.dp, CircleShape)
                .background(sunColor, CircleShape)
                .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "☀",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
    }
}

/**
 * Calculate the position on the edge of a rectangle for a given azimuth angle.
 * Azimuth is in degrees, where 0 = North, 90 = East, 180 = South, 270 = West.
 *
 * @param azimuth Sun azimuth in degrees (0-360)
 * @param width Container width in pixels
 * @param height Container height in pixels
 * @return Pair of (x, y) coordinates on the edge
 */
@Suppress("MagicNumber") // Trigonometry constants
private fun calculateEdgePosition(
    azimuth: Double,
    width: Float,
    height: Float,
): Pair<Float, Float> {
    // Convert azimuth to radians, adjusting for screen coordinates
    // Azimuth 0 = North (top), 90 = East (right), 180 = South (bottom), 270 = West (left)
    val radians = Math.toRadians(azimuth)

    // Calculate direction vector (sin for x, -cos for y because y increases downward)
    val dx = sin(radians).toFloat()
    val dy = -cos(radians).toFloat()

    // Center of container
    val centerX = width / 2
    val centerY = height / 2

    // Calculate intersection with rectangle edges
    // We need to find where the ray from center in direction (dx, dy) hits the edge
    val halfWidth = width / 2
    val halfHeight = height / 2

    // Calculate t values for each edge intersection
    val tRight = if (dx > 0) halfWidth / dx else Float.MAX_VALUE
    val tLeft = if (dx < 0) -halfWidth / dx else Float.MAX_VALUE
    val tBottom = if (dy > 0) halfHeight / dy else Float.MAX_VALUE
    val tTop = if (dy < 0) -halfHeight / dy else Float.MAX_VALUE

    // Find the smallest positive t (first intersection)
    val t = minOf(tRight, tLeft, tBottom, tTop)

    // Calculate intersection point
    val x = centerX + dx * t
    val y = centerY + dy * t

    // Clamp to ensure we're within bounds (with small margin)
    val margin = 16f
    return Pair(
        x.coerceIn(margin, width - margin),
        y.coerceIn(margin, height - margin),
    )
}
