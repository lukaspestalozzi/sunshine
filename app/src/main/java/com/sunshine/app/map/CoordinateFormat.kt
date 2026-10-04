package com.sunshine.app.map

import com.sunshine.app.settings.CoordinateFormat
import com.sunshine.core.GeoPoint
import com.sunshine.core.toLv95
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Formats [point] in [format], independent of the device locale (map-view spec, "Selected location
 * crosshair"; design D6 of add-settings): `46.8182° N, 8.2275° E`, `46°49′05.5″ N, 8°13′39.0″ E`, or
 * `2'632'479, 1'170'652`. LV95 outside its area of use falls back to decimal degrees.
 */
fun formatCoordinates(
    point: GeoPoint,
    format: CoordinateFormat = CoordinateFormat.DECIMAL,
): String =
    when (format) {
        CoordinateFormat.DECIMAL -> pair(point, ::formatDecimal)
        CoordinateFormat.DMS -> pair(point, ::formatDms)
        CoordinateFormat.LV95 ->
            toLv95(point)?.let { "${grouped(it.easting.roundToLong())}, ${grouped(it.northing.roundToLong())}" }
                ?: pair(point, ::formatDecimal)
    }

private fun pair(
    point: GeoPoint,
    format: (Double, Char, Char) -> String,
): String = "${format(point.latitude, 'N', 'S')}, ${format(point.longitude, 'E', 'W')}"

// Rounds first, so a value that rounds to zero gets the positive hemisphere letter.
private fun formatDecimal(
    value: Double,
    positive: Char,
    negative: Char,
): String {
    val rounded = BigDecimal(value.toString()).setScale(DECIMALS, RoundingMode.HALF_UP)
    val hemisphere = if (rounded.signum() < 0) negative else positive
    return "${rounded.abs().toPlainString()}° $hemisphere"
}

// Counts whole tenths of a second, so that 59.96″ carries into the minutes exactly.
private fun formatDms(
    value: Double,
    positive: Char,
    negative: Char,
): String {
    val tenths =
        BigDecimal(value.toString())
            .multiply(BigDecimal(TENTHS_PER_DEGREE))
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
    val hemisphere = if (tenths < 0) negative else positive
    val magnitude = abs(tenths)
    val degrees = magnitude / TENTHS_PER_DEGREE
    val minutes = magnitude % TENTHS_PER_DEGREE / TENTHS_PER_MINUTE
    val secondTenths = magnitude % TENTHS_PER_MINUTE
    val seconds = "%02d.%d".format(Locale.ROOT, secondTenths / 10, secondTenths % 10)
    return "$degrees°${"%02d".format(Locale.ROOT, minutes)}′$seconds″ $hemisphere"
}

// Whole metres with an apostrophe every three digits, e.g. 2'632'479.
private fun grouped(metres: Long): String =
    metres
        .toString()
        .reversed()
        .chunked(3)
        .joinToString("'")
        .reversed()

private const val DECIMALS = 4
private const val TENTHS_PER_MINUTE = 600L
private const val TENTHS_PER_DEGREE = 36_000L
