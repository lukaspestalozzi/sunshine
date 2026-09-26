package com.sunshine.app.map

import com.sunshine.core.GeoPoint
import java.math.BigDecimal
import java.math.RoundingMode

/** Formats [point] as e.g. `46.8182° N, 8.2275° E`, independent of the device locale. */
fun formatCoordinates(point: GeoPoint): String {
    val latitude = formatDegrees(point.latitude, positive = 'N', negative = 'S')
    val longitude = formatDegrees(point.longitude, positive = 'E', negative = 'W')
    return "$latitude, $longitude"
}

// Rounds first, so a value that rounds to zero gets the positive hemisphere letter.
private fun formatDegrees(
    value: Double,
    positive: Char,
    negative: Char,
): String {
    val rounded = BigDecimal(value.toString()).setScale(DECIMALS, RoundingMode.HALF_UP)
    val hemisphere = if (rounded.signum() < 0) negative else positive
    return "${rounded.abs().toPlainString()}° $hemisphere"
}

private const val DECIMALS = 4
