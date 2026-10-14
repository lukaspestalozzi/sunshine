package com.sunshine.core

import java.text.Normalizer
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** [name] in lower case without accents, as places are matched and merged (design D7 of add-place-search). */
fun normalizeName(name: String): String =
    Normalizer
        .normalize(name, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)

/**
 * Whether every word of [text] begins a word of [name], ignoring case and accents, where words are
 * runs of letters and digits (place-search spec, "Saved matches while typing"). Text without words
 * matches nothing.
 */
fun matchesName(
    text: String,
    name: String,
): Boolean {
    val textWords = words(text)
    val nameWords = words(name)
    return textWords.isNotEmpty() && textWords.all { word -> nameWords.any { it.startsWith(word) } }
}

/** The normalized words of [text]. */
fun words(text: String): List<String> = WORD.findAll(normalizeName(text)).map { it.value }.toList()

/**
 * [entries] in their order without those of the same [name], ignoring case and accents, within
 * [MERGE_METRES] of an entry already kept, at most [limit] of them (place-search spec, "Merged
 * duplicates"; design D9 of add-place-search). Stopping at [limit] keeps long candidate lists cheap.
 */
fun <T> merge(
    entries: List<T>,
    limit: Int,
    name: (T) -> String,
    point: (T) -> GeoPoint,
): List<T> {
    val kept = mutableListOf<T>()
    for (entry in entries) {
        if (kept.size == limit) break
        val entryName = normalizeName(name(entry))
        val duplicate =
            kept.any { normalizeName(name(it)) == entryName && distanceMetres(point(it), point(entry)) <= MERGE_METRES }
        if (!duplicate) kept += entry
    }
    return kept
}

/** Results of the same name within this distance are one place, in metres (user decision, 2026-10-13). */
private const val MERGE_METRES = 250.0

/** Great-circle distance from [from] to [to] in metres, on a sphere of radius [EARTH_RADIUS] (haversine). */
fun distanceMetres(
    from: GeoPoint,
    to: GeoPoint,
): Double {
    val lat1 = Math.toRadians(from.latitude)
    val lat2 = Math.toRadians(to.latitude)
    val halfDLat = (lat2 - lat1) / 2
    val halfDLon = Math.toRadians(to.longitude - from.longitude) / 2
    val h = sin(halfDLat) * sin(halfDLat) + cos(lat1) * cos(lat2) * sin(halfDLon) * sin(halfDLon)
    // Rounding can lift h just above 1 for nearly antipodal points.
    return 2 * EARTH_RADIUS * asin(sqrt(h).coerceAtMost(1.0))
}

/**
 * [metres] as a result entry shows it, rounded half up: `170 m` when it rounds to less than 1000 m
 * in tens of metres, else `1.5 km` when it rounds to less than 10.0 km in tenths, else `16 km`, with a
 * dot as decimal separator whatever the locale (place-search spec, "Result entries").
 */
fun formatDistance(metres: Double): String {
    val tensOfMetres = floor(metres / METRES_PER_TEN + HALF) * METRES_PER_TEN
    if (tensOfMetres < METRES_PER_KM) return "${tensOfMetres.toLong()} m"
    val tenthsOfKm = floor(metres / METRES_PER_TENTH_KM + HALF) / TENTHS_PER_KM
    if (tenthsOfKm < WHOLE_KM_FROM) return String.format(Locale.ROOT, "%.1f km", tenthsOfKm)
    return "${floor(metres / METRES_PER_KM + HALF).toLong()} km"
}

/** The area a result covers, its edges in degrees (Photon's extent). */
data class Extent(
    val west: Double,
    val north: Double,
    val east: Double,
    val south: Double,
)

/**
 * The largest zoom at which [extent] fits into a map of [widthDp] × [heightDp] centred on [center],
 * in Web Mercator with [MAP_TILE_DP]-wide tiles (design D14 of add-place-search). Infinite when the
 * extent has no size.
 */
fun fitZoom(
    center: GeoPoint,
    extent: Extent,
    widthDp: Double,
    heightDp: Double,
): Double {
    val x = mercatorX(center.longitude)
    val y = mercatorY(center.latitude)
    val dx = max(abs(mercatorX(extent.east) - x), abs(mercatorX(extent.west) - x))
    val dy = max(abs(mercatorY(extent.north) - y), abs(mercatorY(extent.south) - y))
    return min(log2(widthDp / 2 / (MAP_TILE_DP * dx)), log2(heightDp / 2 / (MAP_TILE_DP * dy)))
}

/**
 * The zoom at which the map shows a chosen result at [center] (place-search spec, "Choosing a
 * result"): with an [extent] that does not fit at zoom 13, the zoom that fits it, at least 5;
 * otherwise [currentZoom] clamped to 13–17.
 */
fun chosenZoom(
    currentZoom: Double,
    center: GeoPoint,
    extent: Extent?,
    widthDp: Double,
    heightDp: Double,
): Double {
    val fit = extent?.let { fitZoom(center, it, widthDp, heightDp) }
    return if (fit != null && fit < CHOSEN_MIN_ZOOM) {
        fit.coerceAtLeast(MAP_MIN_ZOOM)
    } else {
        currentZoom.coerceIn(CHOSEN_MIN_ZOOM, MAP_MAX_ZOOM)
    }
}

private const val CHOSEN_MIN_ZOOM = 13.0
private const val MAP_MIN_ZOOM = 5.0
private const val MAP_MAX_ZOOM = 17.0

// The position in a world of width and height 1, x from the 180° meridian eastward, y from the north edge.
private fun mercatorX(longitude: Double): Double = (longitude + HALF_TURN_DEGREES) / FULL_TURN_DEGREES

private fun mercatorY(latitude: Double): Double {
    val sinLatitude = sin(Math.toRadians(latitude))
    return HALF - ln((1 + sinLatitude) / (1 - sinLatitude)) / (4 * PI)
}

private const val HALF_TURN_DEGREES = 180.0
private const val FULL_TURN_DEGREES = 360.0

private const val HALF = 0.5
private const val METRES_PER_TEN = 10.0
private const val METRES_PER_KM = 1000.0
private const val METRES_PER_TENTH_KM = 100.0
private const val TENTHS_PER_KM = 10.0
private const val WHOLE_KM_FROM = 10.0

private val COMBINING_MARKS = Regex("""\p{M}+""")
private val WORD = Regex("""[\p{L}\p{N}]+""")
