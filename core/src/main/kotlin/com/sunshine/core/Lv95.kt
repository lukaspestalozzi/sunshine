package com.sunshine.core

/** A position in the Swiss projection LV95 (EPSG:2056), in metres. */
data class Lv95(
    val easting: Double,
    val northing: Double,
)

/**
 * [point] in LV95 by swisstopo's approximate formulas for WGS84 → LV95 (December 2016), better
 * than 1 m everywhere in Switzerland, or `null` outside LV95's area of use, 45.81–47.81° N and
 * 5.95–10.50° E (map-view spec, "Selected location crosshair"; design D6 of add-settings).
 */
fun toLv95(point: GeoPoint): Lv95? {
    if (point.latitude !in SOUTH..NORTH || point.longitude !in WEST..EAST) return null
    // Differences to Bern in units of 10000 arcseconds.
    val phi = (point.latitude * SECONDS_PER_DEGREE - BERN_LATITUDE_SECONDS) / UNIT_SECONDS
    val lambda = (point.longitude * SECONDS_PER_DEGREE - BERN_LONGITUDE_SECONDS) / UNIT_SECONDS
    val easting =
        2_600_072.37 +
            211_455.93 * lambda -
            10_938.51 * lambda * phi -
            0.36 * lambda * phi * phi -
            44.54 * lambda * lambda * lambda
    val northing =
        1_200_147.07 +
            308_807.95 * phi +
            3_745.25 * lambda * lambda +
            76.63 * phi * phi -
            194.56 * lambda * lambda * phi +
            119.79 * phi * phi * phi
    return Lv95(easting, northing)
}

// LV95's area of use (EPSG:2056: Switzerland and Liechtenstein).
private const val SOUTH = 45.81
private const val NORTH = 47.81
private const val WEST = 5.95
private const val EAST = 10.50

private const val SECONDS_PER_DEGREE = 3600.0
private const val BERN_LATITUDE_SECONDS = 169_028.66
private const val BERN_LONGITUDE_SECONDS = 26_782.5
private const val UNIT_SECONDS = 10_000.0
