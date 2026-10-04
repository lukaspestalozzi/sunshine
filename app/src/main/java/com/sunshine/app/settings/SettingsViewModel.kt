package com.sunshine.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The Settings page's state and changes (settings spec, "Settings page"; design D10 of add-settings). */
class SettingsViewModel(
    private val store: SettingsStore,
    regionNotComplete: Flow<Boolean> = flowOf(false),
    private val clearBrowsed: suspend () -> Unit = {},
) : ViewModel() {
    val settings: StateFlow<Settings> = store.settings

    /**
     * Whether `Clear browsed tiles` is enabled: only while every region is complete (offline-regions
     * spec, "Clear browsed tiles"); disabled until that is known.
     */
    val canClear: StateFlow<Boolean> =
        regionNotComplete.map { !it }.stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = false)

    fun onCoordinates(format: CoordinateFormat) = write { store.setCoordinates(format) }

    fun onKeepScreenOn(on: Boolean) = write { store.setKeepScreenOn(on) }

    fun onStartAt(startAt: StartAt) = write { store.setStartAt(startAt) }

    fun onOverlayOpacity(percent: Int) = write { store.setOverlayOpacity(percent) }

    fun onPreset(preset: Preset) = write { store.setPreset(preset) }

    fun onBrowsedLimit(mib: Int) = write { store.setBrowsedLimit(mib) }

    /** The values of `Custom resolution`, stored when its page is left. */
    fun onCustom(custom: Resolution) {
        if (custom != store.settings.value.custom) write { store.setCustom(custom) }
    }

    /** Clears the browsed tiles, then calls [done]. */
    fun onClearBrowsed(done: () -> Unit) =
        write {
            clearBrowsed()
            done()
        }

    private fun write(change: suspend () -> Unit) {
        viewModelScope.launch { change() }
    }
}
