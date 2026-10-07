package com.sunshine.app.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/** The format of the selected location's coordinates (map-view spec, "Selected location crosshair"). */
enum class CoordinateFormat { DECIMAL, DMS, LV95 }

/** Where the map opens at launch (map-view spec, "Default viewport"). */
enum class StartAt { LAST_VIEW, MY_LOCATION, ALPS_OVERVIEW }

/** The map centre and zoom level when the app last went to the background. */
data class LastView(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double,
)

/** The switches of the `Debug` section, each showing one group of the debug box (settings spec, "Debug info"). */
data class DebugSwitches(
    val timings: Boolean = false,
    val tiles: Boolean = false,
    val dayState: Boolean = false,
    val agreementCheck: Boolean = false,
) {
    /** Whether the debug box is shown. */
    val any: Boolean get() = timings || tiles || dayState || agreementCheck
}

/** Every setting and the last view, with the defaults of the settings spec, "Stored settings". */
data class Settings(
    val coordinates: CoordinateFormat = CoordinateFormat.DECIMAL,
    val keepScreenOn: Boolean = false,
    val startAt: StartAt = StartAt.LAST_VIEW,
    val overlayOpacityPercent: Int = 60,
    val preset: Preset = Preset.NORMAL,
    val custom: Resolution = Resolution.NORMAL,
    val browsedLimitMib: Int = 512,
    val lastView: LastView? = null,
    val debug: DebugSwitches = DebugSwitches(),
    /** Whether the panel's details are expanded (sun-position spec, "Sun information panel"); not on the Settings page. */
    val detailsExpanded: Boolean = false,
    /** Whether the first-run hint has been dismissed (map-view spec, "First-run hint"); not on the Settings page. */
    val hintDismissed: Boolean = false,
) {
    /** The cell sizes and steps in use. */
    val resolution: Resolution get() = preset.resolution(custom)

    companion object {
        val OPACITIES = 20..90 step 10
        val BROWSED_LIMITS_MIB = listOf(128, 256, 512, 1024, 2048)
    }
}

/** The keys of the settings in the Preferences DataStore. */
object SettingsKeys {
    val COORDINATES = stringPreferencesKey("coordinates")
    val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    val START_AT = stringPreferencesKey("start_at")
    val OVERLAY_OPACITY = intPreferencesKey("overlay_opacity_percent")
    val PRESET = stringPreferencesKey("shade_resolution")
    val CUSTOM_SUN_SHADE_CELL = intPreferencesKey("custom_sun_shade_cell_dp")
    val CUSTOM_SUN_SHADE_STEP = intPreferencesKey("custom_sun_shade_step_minutes")
    val CUSTOM_SUN_HOURS_CELL = intPreferencesKey("custom_sun_hours_cell_dp")
    val CUSTOM_SUN_HOURS_STEP = intPreferencesKey("custom_sun_hours_step_minutes")
    val BROWSED_LIMIT_MIB = intPreferencesKey("browsed_limit_mib")
    val LAST_LATITUDE = doublePreferencesKey("last_view_latitude")
    val LAST_LONGITUDE = doublePreferencesKey("last_view_longitude")
    val LAST_ZOOM = doublePreferencesKey("last_view_zoom")
    val DEBUG_TIMINGS = booleanPreferencesKey("debug_timings")
    val DEBUG_TILES = booleanPreferencesKey("debug_tiles")
    val DEBUG_DAY_STATE = booleanPreferencesKey("debug_day_state")
    val DEBUG_AGREEMENT = booleanPreferencesKey("debug_agreement_check")
    val DETAILS_EXPANDED = booleanPreferencesKey("details_expanded")
    val HINT_DISMISSED = booleanPreferencesKey("hint_dismissed")

    fun lastView(view: LastView): Array<Preferences.Pair<*>> =
        arrayOf(LAST_LATITUDE to view.latitude, LAST_LONGITUDE to view.longitude, LAST_ZOOM to view.zoom)
}

/**
 * The settings stored in [preferences]. Each value that is missing, cannot be read or is not
 * allowed is replaced by its default on its own (settings spec, "Stored settings"; design D2).
 */
fun decode(preferences: Preferences): Settings {
    val defaults = Settings()
    val normal = Resolution.NORMAL
    return Settings(
        coordinates = preferences.enum(SettingsKeys.COORDINATES) ?: defaults.coordinates,
        keepScreenOn = preferences.read(SettingsKeys.KEEP_SCREEN_ON) ?: defaults.keepScreenOn,
        startAt = preferences.enum(SettingsKeys.START_AT) ?: defaults.startAt,
        overlayOpacityPercent =
            preferences.read(SettingsKeys.OVERLAY_OPACITY)?.takeIf { it in Settings.OPACITIES }
                ?: defaults.overlayOpacityPercent,
        preset = preferences.enum(SettingsKeys.PRESET) ?: defaults.preset,
        custom =
            Resolution(
                sunShadeCellDp =
                    preferences.read(SettingsKeys.CUSTOM_SUN_SHADE_CELL)?.takeIf { it in Resolution.SUN_SHADE_CELLS }
                        ?: normal.sunShadeCellDp,
                sunShadeStepMinutes =
                    preferences.read(SettingsKeys.CUSTOM_SUN_SHADE_STEP)?.takeIf { it in Resolution.STEPS }
                        ?: normal.sunShadeStepMinutes,
                sunHoursCellDp =
                    preferences.read(SettingsKeys.CUSTOM_SUN_HOURS_CELL)?.takeIf { it in Resolution.SUN_HOURS_CELLS }
                        ?: normal.sunHoursCellDp,
                sunHoursStepMinutes =
                    preferences.read(SettingsKeys.CUSTOM_SUN_HOURS_STEP)?.takeIf { it in Resolution.STEPS }
                        ?: normal.sunHoursStepMinutes,
            ),
        browsedLimitMib =
            preferences.read(SettingsKeys.BROWSED_LIMIT_MIB)?.takeIf { it in Settings.BROWSED_LIMITS_MIB }
                ?: defaults.browsedLimitMib,
        lastView = preferences.lastView(),
        debug =
            DebugSwitches(
                timings = preferences.read(SettingsKeys.DEBUG_TIMINGS) ?: false,
                tiles = preferences.read(SettingsKeys.DEBUG_TILES) ?: false,
                dayState = preferences.read(SettingsKeys.DEBUG_DAY_STATE) ?: false,
                agreementCheck = preferences.read(SettingsKeys.DEBUG_AGREEMENT) ?: false,
            ),
        detailsExpanded = preferences.read(SettingsKeys.DETAILS_EXPANDED) ?: defaults.detailsExpanded,
        hintDismissed = preferences.read(SettingsKeys.HINT_DISMISSED) ?: defaults.hintDismissed,
    )
}

private fun Preferences.lastView(): LastView? {
    val latitude = read(SettingsKeys.LAST_LATITUDE) ?: return null
    val longitude = read(SettingsKeys.LAST_LONGITUDE) ?: return null
    val zoom = read(SettingsKeys.LAST_ZOOM) ?: return null
    val valid = latitude in -90.0..90.0 && longitude in -180.0..180.0 && zoom in MIN_ZOOM..MAX_ZOOM
    return if (valid) LastView(latitude, longitude, zoom) else null
}

/**
 * The value of [key], or `null` when it is missing or stored with another type. A plain `get`
 * casts unchecked, so a value of another type would fail only where it is used.
 */
private inline fun <reified T> Preferences.read(key: Preferences.Key<T>): T? = asMap()[key] as? T

private inline fun <reified E : Enum<E>> Preferences.enum(key: Preferences.Key<String>): E? =
    read(key)?.let { name -> enumValues<E>().firstOrNull { it.name == name } }

// The map's zoom range (map-view spec, "Topographic base map").
private const val MIN_ZOOM = 5.0
private const val MAX_ZOOM = 17.0
