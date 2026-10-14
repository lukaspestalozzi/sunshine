package com.sunshine.app.search

import com.sunshine.core.Extent
import com.sunshine.core.GeoPoint

/** A place Photon returned, as listed and saved (place-search spec, "Result entries", "Saved places"). */
data class Place(
    /** OpenStreetMap type and id, e.g. `N31664302`: each object is saved once. */
    val key: String,
    val title: String,
    val label: String,
    val place: String?,
    val region: String?,
    val countryCode: String?,
    val point: GeoPoint,
    val extent: Extent?,
) {
    /** The entry's second line: the label, then place, region and country code, e.g. `peak · Grindelwald, Bern/Berne, CH`. */
    val line: String
        get() {
            val parts = listOfNotNull(place, region, countryCode)
            return if (parts.isEmpty()) label else "$label · ${parts.joinToString(", ")}"
        }
}
