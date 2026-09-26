package com.sunshine.core

/**
 * Heights in metres of Terrarium-encoded pixels (opaque ARGB): `R × 256 + G + B / 256 − 32768`.
 * The result is exact in a [Float]: the encoding's resolution is 1/256 m.
 */
fun terrariumHeights(argb: IntArray): FloatArray =
    FloatArray(argb.size) { i ->
        val pixel = argb[i]
        val red = pixel shr 16 and 0xFF
        val green = pixel shr 8 and 0xFF
        val blue = pixel and 0xFF
        red * 256f + green + blue / 256f - 32768f
    }
