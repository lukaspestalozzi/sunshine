package com.sunshine.app.ui.util

import kotlin.math.pow

/**
 * Shared heatmap color scale: blue (0%) → cyan → green → yellow → red (100%).
 * Used by both the map overlay (Android Canvas) and the Compose legend bar.
 *
 * @param fraction Value in [0, 1] representing position on the gradient.
 * @return (red, green, blue) as floats in [0, 1].
 */
@Suppress("MagicNumber") // Color interpolation breakpoints at 0.25 increments
fun heatmapGradient(fraction: Double): Triple<Float, Float, Float> {
    val f = fraction.coerceIn(0.0, 1.0)
    return when {
        f < 0.25 -> {
            val t = (f / 0.25).toFloat()
            Triple(0f, t, 1f)
        }
        f < 0.50 -> {
            val t = ((f - 0.25) / 0.25).toFloat()
            Triple(0f, 1f, 1f - t)
        }
        f < 0.75 -> {
            val t = ((f - 0.50) / 0.25).toFloat()
            Triple(t, 1f, 0f)
        }
        else -> {
            val t = ((f - 0.75) / 0.25).toFloat()
            Triple(1f, 1f - t, 0f)
        }
    }
}

/**
 * Gamma exponent for the heatmap color scale.
 * Values > 1 compress the lower range and expand the upper range,
 * giving more color differentiation where most data points fall (6-12h sun exposure).
 * With gamma 3.0, the 8-10h range spans 44-86% of the gradient (vs 58-91% at gamma 2.0).
 */
@Suppress("MagicNumber")
const val HEATMAP_GAMMA = 3.0

/**
 * Apply gamma correction to a linear fraction for the heatmap color scale.
 * With gamma 3.0, the midpoint (50% of max) maps to 12.5% of the gradient,
 * leaving 87.5% of the color spectrum for the upper half of the range.
 */
fun applyHeatmapGamma(linearFraction: Double): Double = linearFraction.coerceIn(0.0, 1.0).pow(HEATMAP_GAMMA)

/** Alpha for heatmap overlays — 60% opacity for both the map overlay and the legend. */
@Suppress("MagicNumber")
const val HEATMAP_ALPHA_FRACTION = 0.6f
