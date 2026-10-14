package com.sunshine.core

import kotlin.math.abs

/** Coordinates typed as search text, in the kind they were typed in (place-search spec, "Coordinates as search text"). */
sealed interface Coordinates {
    val point: GeoPoint

    /** Decimal degrees, e.g. `46.6863, 7.8632` or `7.8632 E 46.6863 N`. */
    data class Decimal(
        override val point: GeoPoint,
    ) : Coordinates

    /** Degrees, minutes and seconds, e.g. `46°41′10.7″ N, 7°51′47.5″ E`. */
    data class Dms(
        override val point: GeoPoint,
    ) : Coordinates

    /**
     * Swiss grid LV95 as typed ([lv95]), e.g. `2'632'479, 1'170'652`, shown as typed rather than
     * converted back from [point] (design D11 of add-place-search).
     */
    data class SwissGrid(
        val lv95: Lv95,
        override val point: GeoPoint,
    ) : Coordinates
}

/**
 * The coordinates [text] holds, or `null` when it holds none or their values are out of range, so
 * that it is searched as a name (design D11 of add-place-search).
 */
fun parseCoordinates(text: String): Coordinates? {
    val trimmed = text.trim()
    return parseDecimal(trimmed) ?: parseDms(trimmed) ?: parseSwissGrid(trimmed)
}

/**
 * Two numbers separated by a comma, spaces or both, each optionally followed by `°`. Either both
 * have a hemisphere letter, in either order, or neither has one; then the latitude comes first.
 */
private fun parseDecimal(text: String): Coordinates.Decimal? {
    val match = DECIMAL.matchEntire(text) ?: return null
    val (first, firstLetter, second, secondLetter) = match.destructured
    val (latitude, longitude) =
        when {
            firstLetter.isEmpty() && secondLetter.isEmpty() -> first.toDouble() to second.toDouble()
            firstLetter.isEmpty() || secondLetter.isEmpty() -> return null
            // Signs and letters together are ambiguous.
            first.startsWith('-') || second.startsWith('-') -> return null
            else -> withHemispheres(first.toDouble(), firstLetter, second.toDouble(), secondLetter) ?: return null
        }
    return mapPoint(latitude, longitude)?.let(Coordinates::Decimal)
}

/**
 * Two values of whole degrees with `°`, whole minutes with `′` or `'`, and seconds with `″` or `"`
 * and an optional decimal part, each followed by a hemisphere letter.
 */
private fun parseDms(text: String): Coordinates.Dms? {
    val match = DMS.matchEntire(text) ?: return null
    val values = match.groupValues
    val first = degreesOf(values[1], values[2], values[3]) ?: return null
    val second = degreesOf(values[5], values[6], values[7]) ?: return null
    val (latitude, longitude) = withHemispheres(first, values[4], second, values[8]) ?: return null
    return mapPoint(latitude, longitude)?.let(Coordinates::Dms)
}

/**
 * Two numbers of 7 digits before an optional decimal part, digit groups optionally separated by `'`
 * or `’`: the one starting with `2` is the easting, the one starting with `1` the northing, in
 * either order. Converted by swisstopo's formulas, and accepted only inside LV95's area of use.
 */
private fun parseSwissGrid(text: String): Coordinates.SwissGrid? {
    val match = SWISS_GRID.matchEntire(text) ?: return null
    val (first, second) = match.destructured
    val (easting, northing) =
        when {
            first.startsWith(EASTING_DIGIT) && second.startsWith(NORTHING_DIGIT) -> first to second
            first.startsWith(NORTHING_DIGIT) && second.startsWith(EASTING_DIGIT) -> second to first
            else -> return null
        }
    val lv95 = Lv95(metres(easting), metres(northing))
    val point = fromLv95(lv95)
    // toLv95 is defined only inside the area of use.
    return if (toLv95(point) != null) Coordinates.SwissGrid(lv95, point) else null
}

private fun metres(value: String): Double = value.filterNot { it in GROUP_SEPARATORS }.toDouble()

/** Decimal degrees of [degrees], [minutes] and [seconds], or `null` when minutes or seconds reach 60. */
private fun degreesOf(
    degrees: String,
    minutes: String,
    seconds: String,
): Double? {
    val wholeMinutes = minutes.toInt()
    val decimalSeconds = seconds.toDouble()
    if (wholeMinutes >= MINUTES_PER_DEGREE || decimalSeconds >= SECONDS_PER_MINUTE) return null
    return degrees.toInt() + wholeMinutes / MINUTES_PER_DEGREE.toDouble() + decimalSeconds / (MINUTES_PER_DEGREE * SECONDS_PER_MINUTE)
}

/** Latitude and longitude of two unsigned values with their hemisphere letters, one of `N`/`S` and one of `E`/`W`. */
private fun withHemispheres(
    first: Double,
    firstLetter: String,
    second: Double,
    secondLetter: String,
): Pair<Double, Double>? {
    val firstIsLatitude = firstLetter.uppercase() in LATITUDE_LETTERS
    val secondIsLatitude = secondLetter.uppercase() in LATITUDE_LETTERS
    if (firstIsLatitude == secondIsLatitude) return null
    return if (firstIsLatitude) {
        signed(first, firstLetter) to signed(second, secondLetter)
    } else {
        signed(second, secondLetter) to signed(first, firstLetter)
    }
}

private fun signed(
    value: Double,
    letter: String,
): Double = if (letter.uppercase() in NEGATIVE_LETTERS) -value else value

/**
 * The point at [latitude], [longitude] when the map can be centred there (latitude within the
 * Web-Mercator limit, longitude within ±180°), else `null`. Checked before [GeoPoint] rejects it.
 */
private fun mapPoint(
    latitude: Double,
    longitude: Double,
): GeoPoint? = if (abs(latitude) <= MAX_MAP_LATITUDE && abs(longitude) <= MAX_LONGITUDE) GeoPoint(latitude, longitude) else null

private const val MAX_LONGITUDE = 180.0
private val LATITUDE_LETTERS = setOf("N", "S")
private val NEGATIVE_LETTERS = setOf("S", "W")

// One value: a number with a dot as decimal separator, an optional degree sign and an optional hemisphere letter.
private const val DECIMAL_VALUE = """([+-]?\d+(?:\.\d+)?)\s*°?\s*([NSEWnsew]?)"""
private val DECIMAL = Regex("""$DECIMAL_VALUE(?:\s*,\s*|\s+)$DECIMAL_VALUE""")

private const val MINUTES_PER_DEGREE = 60
private const val SECONDS_PER_MINUTE = 60.0

// One value: degrees, minutes, seconds and a hemisphere letter, with optional spaces between the parts.
private const val DMS_VALUE = """(\d{1,3})\s*°\s*(\d{1,2})\s*['′]\s*(\d{1,2}(?:\.\d+)?)\s*["″]\s*([NSEWnsew])"""
private val DMS = Regex("""$DMS_VALUE\s*,?\s*$DMS_VALUE""")

private const val EASTING_DIGIT = '2'
private const val NORTHING_DIGIT = '1'
private const val GROUP_SEPARATORS = "'’"

// One value: 7 digits in groups of 1, 3 and 3, optionally separated, and an optional decimal part.
private const val SWISS_GRID_VALUE = """(\d['’]?\d{3}['’]?\d{3}(?:\.\d+)?)"""
private val SWISS_GRID = Regex("""$SWISS_GRID_VALUE(?:\s*,\s*|\s+)$SWISS_GRID_VALUE""")
