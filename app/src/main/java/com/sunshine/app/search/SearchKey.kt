package com.sunshine.app.search

import com.sunshine.core.GeoPoint
import java.util.Locale
import kotlin.math.floor

/**
 * A search as it is requested and saved (design D2 of add-place-search): the normalized [text] and
 * the crosshair rounded half up to 0.1°, kept in tenths of a degree.
 */
data class SearchKey(
    val text: String,
    val latitudeTenths: Int,
    val longitudeTenths: Int,
) {
    companion object {
        fun of(
            text: String,
            crosshair: GeoPoint,
        ) = SearchKey(normalizeSearchText(text), tenths(crosshair.latitude), tenths(crosshair.longitude))
    }
}

/** [text] trimmed, every run of whitespace replaced by one space, in lower case. */
fun normalizeSearchText(text: String): String = text.trim().replace(WHITESPACE, " ").lowercase(Locale.ROOT)

private fun tenths(degrees: Double): Int = floor(degrees * TENTHS_PER_DEGREE + HALF).toInt()

private const val TENTHS_PER_DEGREE = 10
private const val HALF = 0.5
private val WHITESPACE = Regex("""\s+""")
