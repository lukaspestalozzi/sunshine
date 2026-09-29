package com.sunshine.app.map

import java.time.Duration
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The heatmap's colour scale (sun-exposure-heatmap spec, "Colour scale"; design D3 of
 * add-sun-exposure-heatmap): bands of 30 minutes from 0 h up to [dayLength], coloured from the
 * shade tint through blue and yellow to light amber, interpolated in OKLab so that lightness rises
 * from band to band.
 */
class HeatmapBands(
    dayLength: Duration,
) {
    /** Number of bands: at least one. */
    val count: Int = max(1, ceil(dayLength.seconds / BAND_SECONDS).toInt())

    /** ARGB colour of each band, all at the shade tint's alpha. */
    val colours: IntArray =
        IntArray(count) { band ->
            val t = if (count == 1) 0.0 else band.toDouble() / (count - 1)
            ALPHA or oklabToRgb(interpolate(t))
        }

    /** The band of [sunMinutes] minutes of sun: 30 minutes per band, the last band at or beyond the day length. */
    fun bandOf(sunMinutes: Int): Int = min(sunMinutes / BAND_MINUTES, count - 1)

    private companion object {
        const val BAND_SECONDS = 30.0 * 60.0
        const val BAND_MINUTES = 30
        const val ALPHA = SHADE_ARGB and 0xFF000000.toInt()

        // Slate (the shade tint), blue, yellow, light amber; OKLab L 0.454, 0.584, 0.824, 0.918.
        val STOPS: List<DoubleArray> = listOf(0x455A64, 0x3F7FBF, 0xD9C84A, 0xFFE0A3).map(::rgbToOklab)

        // The OKLab colour at [t] in [0, 1], the stops evenly spaced.
        fun interpolate(t: Double): DoubleArray {
            val position = t * (STOPS.size - 1)
            val segment = min(position.toInt(), STOPS.size - 2)
            val f = position - segment
            val (a, b) = STOPS[segment] to STOPS[segment + 1]
            return DoubleArray(3) { a[it] + (b[it] - a[it]) * f }
        }

        // sRGB ↔ OKLab (Björn Ottosson, https://bottosson.github.io/posts/oklab/).
        fun rgbToOklab(rgb: Int): DoubleArray {
            val r = toLinear(rgb shr 16 and 0xFF)
            val g = toLinear(rgb shr 8 and 0xFF)
            val b = toLinear(rgb and 0xFF)
            val l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
            val m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
            val s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
            return doubleArrayOf(
                0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
                1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
                0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
            )
        }

        fun oklabToRgb(lab: DoubleArray): Int {
            val (lightness, a, b) = Triple(lab[0], lab[1], lab[2])
            val l = (lightness + 0.3963377774 * a + 0.2158037573 * b).pow(3)
            val m = (lightness - 0.1055613458 * a - 0.0638541728 * b).pow(3)
            val s = (lightness - 0.0894841775 * a - 1.2914855480 * b).pow(3)
            val r = toSrgb(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s)
            val g = toSrgb(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s)
            val bl = toSrgb(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s)
            return (r shl 16) or (g shl 8) or bl
        }

        fun toLinear(channel: Int): Double = (channel / 255.0).let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }

        fun toSrgb(linear: Double): Int {
            val c = linear.coerceIn(0.0, 1.0)
            val srgb = if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1 / 2.4) - 0.055
            return (srgb * 255).roundToInt()
        }
    }
}
