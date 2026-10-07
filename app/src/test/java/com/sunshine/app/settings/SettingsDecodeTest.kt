package com.sunshine.app.settings

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class SettingsDecodeTest {
    @Test
    fun `empty preferences give every default`() {
        val settings = decode(preferencesOf())

        assertEquals(CoordinateFormat.DECIMAL, settings.coordinates)
        assertEquals(false, settings.keepScreenOn)
        assertEquals(StartAt.LAST_VIEW, settings.startAt)
        assertEquals(60, settings.overlayOpacityPercent)
        assertEquals(Preset.NORMAL, settings.preset)
        assertEquals(Resolution.NORMAL, settings.custom)
        assertEquals(512, settings.browsedLimitMib)
        assertNull(settings.lastView)
        assertEquals(DebugSwitches(timings = false, tiles = false, dayState = false, agreementCheck = false), settings.debug)
        assertEquals(false, settings.detailsExpanded)
        assertEquals(false, settings.hintDismissed)
        assertEquals(Settings(), settings)
    }

    // settings spec, "Stored settings" (design D7 of polish-ui).
    @Test
    fun `the panel's details and the hint are read, and an unreadable value is false`() {
        val stored = decode(preferencesOf(SettingsKeys.DETAILS_EXPANDED to true, SettingsKeys.HINT_DISMISSED to true))
        val unreadable =
            decode(
                preferencesOf(
                    stringPreferencesKey(SettingsKeys.DETAILS_EXPANDED.name) to "yes",
                    SettingsKeys.HINT_DISMISSED to true,
                    SettingsKeys.COORDINATES to "LV95",
                ),
            )

        assertEquals(true, stored.detailsExpanded)
        assertEquals(true, stored.hintDismissed)
        assertEquals(false, unreadable.detailsExpanded)
        assertEquals(true, unreadable.hintDismissed)
        assertEquals(CoordinateFormat.LV95, unreadable.coordinates)
    }

    @Test
    fun `stored debug switches are read, each on its own`() {
        val settings = decode(preferencesOf(SettingsKeys.DEBUG_TIMINGS to true, SettingsKeys.DEBUG_AGREEMENT to true))

        assertEquals(DebugSwitches(timings = true, tiles = false, dayState = false, agreementCheck = true), settings.debug)
    }

    @Test
    fun `an unreadable debug switch is off and the other settings keep their values`() {
        val settings =
            decode(
                preferencesOf(
                    stringPreferencesKey(SettingsKeys.DEBUG_TILES.name) to "yes",
                    SettingsKeys.DEBUG_DAY_STATE to true,
                    SettingsKeys.COORDINATES to "LV95",
                ),
            )

        assertEquals(DebugSwitches(timings = false, tiles = false, dayState = true, agreementCheck = false), settings.debug)
        assertEquals(CoordinateFormat.LV95, settings.coordinates)
    }

    @Test
    fun `a disallowed opacity gives 60 and keeps the other values`() {
        val settings =
            decode(
                preferencesOf(
                    SettingsKeys.OVERLAY_OPACITY to 75,
                    SettingsKeys.COORDINATES to "LV95",
                    SettingsKeys.PRESET to "FAST",
                ),
            )

        assertEquals(60, settings.overlayOpacityPercent)
        assertEquals(CoordinateFormat.LV95, settings.coordinates)
        assertEquals(Preset.FAST, settings.preset)
    }

    @Test
    fun `an unknown name gives that setting's default only`() {
        val settings =
            decode(
                preferencesOf(
                    SettingsKeys.START_AT to "SOMEWHERE",
                    SettingsKeys.KEEP_SCREEN_ON to true,
                ),
            )

        assertEquals(StartAt.LAST_VIEW, settings.startAt)
        assertEquals(true, settings.keepScreenOn)
    }

    @Test
    fun `a value stored with another type gives that setting's default`() {
        val settings =
            decode(
                preferencesOf(
                    stringPreferencesKey("overlay_opacity_percent") to "75",
                    intPreferencesKey("coordinates") to 2,
                    SettingsKeys.PRESET to "FAST",
                ),
            )

        assertEquals(60, settings.overlayOpacityPercent)
        assertEquals(CoordinateFormat.DECIMAL, settings.coordinates)
        assertEquals(Preset.FAST, settings.preset)
    }

    @ParameterizedTest(name = "{0} MiB")
    @ValueSource(ints = [0, 100, 513, 4096])
    fun `a disallowed browsed limit gives 512 MiB`(mib: Int) {
        assertEquals(512, decode(preferencesOf(SettingsKeys.BROWSED_LIMIT_MIB to mib)).browsedLimitMib)
    }

    @Test
    fun `allowed values are kept`() {
        val settings =
            decode(
                preferencesOf(
                    SettingsKeys.OVERLAY_OPACITY to 20,
                    SettingsKeys.BROWSED_LIMIT_MIB to 2048,
                    SettingsKeys.START_AT to "MY_LOCATION",
                    SettingsKeys.COORDINATES to "DMS",
                ),
            )

        assertEquals(20, settings.overlayOpacityPercent)
        assertEquals(2048, settings.browsedLimitMib)
        assertEquals(StartAt.MY_LOCATION, settings.startAt)
        assertEquals(CoordinateFormat.DMS, settings.coordinates)
    }

    @Test
    fun `custom values outside their ranges fall back to Normal's one by one`() {
        val settings =
            decode(
                preferencesOf(
                    SettingsKeys.CUSTOM_SUN_SHADE_CELL to 3,
                    SettingsKeys.CUSTOM_SUN_SHADE_STEP to 25,
                    SettingsKeys.CUSTOM_SUN_HOURS_CELL to 40,
                    SettingsKeys.CUSTOM_SUN_HOURS_STEP to 20,
                ),
            )

        assertEquals(Resolution(3, 5, 8, 20), settings.custom)
    }

    @Test
    fun `a stored last view round-trips`() {
        val view = LastView(46.6863, 7.8632, 13.0)

        assertEquals(view, decode(preferencesOf(*SettingsKeys.lastView(view))).lastView)
    }

    @Test
    fun `an incomplete or impossible last view is no view`() {
        assertNull(decode(preferencesOf(SettingsKeys.LAST_LATITUDE to 46.0)).lastView)
        assertNull(decode(preferencesOf(*SettingsKeys.lastView(LastView(46.0, 7.0, 25.0)))).lastView)
    }
}
