package com.sunshine.app.search

import com.sunshine.core.Extent
import com.sunshine.core.GeoPoint
import com.sunshine.core.distanceMetres
import com.sunshine.core.matchesName
import com.sunshine.core.merge
import com.sunshine.core.normalizeName
import com.sunshine.core.words

/** Saved places and saved answers in `places.db` (place-search spec, "Saved places"; design D5, D7 of add-place-search). */
class PlaceStore(
    private val dao: PlacesDao,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Saves the [places] Photon answered to [key], in their order, replacing earlier copies. */
    suspend fun save(
        key: SearchKey,
        places: List<Place>,
    ) {
        val receivedAt = now()
        val answer = AnswerRow(key.text, key.latitudeTenths, key.longitudeTenths, places.joinToString(",") { it.key }, receivedAt)
        dao.save(answer, places.map { it.toRow(receivedAt) })
    }

    /** The places of the saved answer to [key] in Photon's order when it arrived less than [maxAgeMillis] ago, else `null`. */
    suspend fun answer(
        key: SearchKey,
        maxAgeMillis: Long,
    ): List<Place>? {
        val answer = dao.answer(key.text, key.latitudeTenths, key.longitudeTenths) ?: return null
        if (now() - answer.receivedAt >= maxAgeMillis) return null
        val keys = answer.placeKeys.split(',').filter { it.isNotEmpty() }
        // A key whose place is missing is skipped (design D5).
        val byKey = dao.places(keys).associateBy { it.key }
        return keys.mapNotNull { byKey[it]?.toPlace() }
    }

    /**
     * The saved places matching [text] (place-search spec, "Saved matches while typing"), nearest to
     * [crosshair] first, merged, at most [limit].
     */
    suspend fun matches(
        text: String,
        crosshair: GeoPoint,
        limit: Int,
    ): List<Place> {
        val longest = words(text).maxByOrNull { it.length } ?: return emptyList()
        val matching =
            dao
                .candidates(longest)
                .filter { matchesName(text, it.title) }
                .map { it.toPlace() }
                .sortedBy { distanceMetres(crosshair, it.point) }
        return merge(matching, limit, name = { it.title }, point = { it.point })
    }

    /** Removes every saved place and answer (place-search spec, "Clear saved places"). */
    suspend fun clear() = dao.clear()
}

private fun Place.toRow(receivedAt: Long) =
    PlaceRow(
        key = key,
        title = title,
        matchText = normalizeName(title),
        label = label,
        place = place,
        region = region,
        countryCode = countryCode,
        latitude = point.latitude,
        longitude = point.longitude,
        west = extent?.west,
        north = extent?.north,
        east = extent?.east,
        south = extent?.south,
        receivedAt = receivedAt,
    )

private fun PlaceRow.toPlace(): Place {
    val extent =
        if (west != null && north != null && east != null && south != null) Extent(west, north, east, south) else null
    return Place(key, title, label, place, region, countryCode, GeoPoint(latitude, longitude), extent)
}
