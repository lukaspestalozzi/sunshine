package com.sunshine.app.search

import com.sunshine.core.Extent
import com.sunshine.core.GeoPoint
import com.sunshine.core.MAX_MAP_LATITUDE
import kotlin.math.abs
import org.json.JSONObject

/**
 * The places of a Photon answer in its order (design D10 of add-place-search), leaving out results
 * without a name or street and those beyond the map's latitude limit. Throws
 * [org.json.JSONException] for an answer that cannot be read.
 */
fun parsePhotonAnswer(json: String): List<Place> {
    val features = JSONObject(json).getJSONArray("features")
    return (0 until features.length()).mapNotNull { placeOf(features.getJSONObject(it)) }
}

private fun placeOf(feature: JSONObject): Place? {
    val properties = feature.getJSONObject("properties")
    val coordinates = feature.getJSONObject("geometry").getJSONArray("coordinates")
    val latitude = coordinates.getDouble(1)
    if (abs(latitude) > MAX_MAP_LATITUDE) return null
    val name = properties.text("name")
    val street = properties.text("street")
    val number = properties.text("housenumber")
    val title = name ?: street?.let { listOfNotNull(it, number).joinToString(" ") } ?: return null
    return Place(
        key = properties.getString("osm_type") + properties.getLong("osm_id"),
        title = title,
        label = if (name == null && number != null) ADDRESS else labelOf(properties),
        place = properties.text("city") ?: properties.text("county"),
        region = properties.text("state"),
        countryCode = properties.text("countrycode"),
        point = GeoPoint(latitude, coordinates.getDouble(0)),
        extent = properties.optJSONArray("extent")?.let { Extent(it.getDouble(0), it.getDouble(1), it.getDouble(2), it.getDouble(3)) },
    )
}

/** The OpenStreetMap tag value in words, the key for `yes` values, and `street` for streets. */
private fun labelOf(properties: JSONObject): String {
    val key = properties.getString("osm_key")
    val value = properties.getString("osm_value")
    return when {
        key == "highway" && properties.text("type") == "street" -> STREET
        value == "yes" -> key.replace('_', ' ')
        else -> value.replace('_', ' ')
    }
}

private fun JSONObject.text(key: String): String? = if (has(key) && !isNull(key)) getString(key).ifBlank { null } else null

private const val ADDRESS = "address"
private const val STREET = "street"
