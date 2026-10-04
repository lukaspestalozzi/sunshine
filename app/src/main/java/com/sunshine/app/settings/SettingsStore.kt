package com.sunshine.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking

/**
 * The settings in a Preferences DataStore (design D2 of add-settings). [settings] starts with the
 * value read when the store was opened, so the first map shown already follows it.
 */
class SettingsStore private constructor(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
    initial: Settings,
) {
    val settings: StateFlow<Settings> = dataStore.data.map(::decode).stateIn(scope, SharingStarted.Eagerly, initial)

    suspend fun setCoordinates(format: CoordinateFormat) = edit { it[SettingsKeys.COORDINATES] = format.name }

    suspend fun setKeepScreenOn(on: Boolean) = edit { it[SettingsKeys.KEEP_SCREEN_ON] = on }

    suspend fun setStartAt(startAt: StartAt) = edit { it[SettingsKeys.START_AT] = startAt.name }

    suspend fun setOverlayOpacity(percent: Int) {
        require(percent in Settings.OPACITIES) { "Opacity $percent % is not allowed" }
        edit { it[SettingsKeys.OVERLAY_OPACITY] = percent }
    }

    suspend fun setPreset(preset: Preset) = edit { it[SettingsKeys.PRESET] = preset.name }

    /** Stores [custom] within the ranges of `Custom resolution`. */
    suspend fun setCustom(custom: Resolution) {
        val clamped = custom.clamped()
        edit {
            it[SettingsKeys.CUSTOM_SUN_SHADE_CELL] = clamped.sunShadeCellDp
            it[SettingsKeys.CUSTOM_SUN_SHADE_STEP] = clamped.sunShadeStepMinutes
            it[SettingsKeys.CUSTOM_SUN_HOURS_CELL] = clamped.sunHoursCellDp
            it[SettingsKeys.CUSTOM_SUN_HOURS_STEP] = clamped.sunHoursStepMinutes
        }
    }

    suspend fun setBrowsedLimit(mib: Int) {
        require(mib in Settings.BROWSED_LIMITS_MIB) { "Browsed limit $mib MiB is not allowed" }
        edit { it[SettingsKeys.BROWSED_LIMIT_MIB] = mib }
    }

    suspend fun setLastView(view: LastView) = edit { it.putAll(*SettingsKeys.lastView(view)) }

    private suspend fun edit(change: (MutablePreferences) -> Unit) {
        dataStore.edit(change)
    }

    companion object {
        /**
         * Opens the store of [file] and reads it once, blocking (design D2). A file that cannot be
         * parsed is replaced by an empty one, i.e. every default.
         */
        fun open(
            file: File,
            scope: CoroutineScope,
        ): SettingsStore {
            val dataStore =
                PreferenceDataStoreFactory.create(
                    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                    scope = scope,
                    produceFile = { file },
                )
            val initial = runBlocking(Dispatchers.IO) { decode(dataStore.data.first()) }
            return SettingsStore(dataStore, scope, initial)
        }
    }
}
