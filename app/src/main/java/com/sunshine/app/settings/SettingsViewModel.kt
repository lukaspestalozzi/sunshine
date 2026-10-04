package com.sunshine.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** The Settings page's state and changes (settings spec, "Settings page"; design D10 of add-settings). */
class SettingsViewModel(
    private val store: SettingsStore,
) : ViewModel() {
    val settings: StateFlow<Settings> = store.settings

    fun onCoordinates(format: CoordinateFormat) = write { store.setCoordinates(format) }

    fun onKeepScreenOn(on: Boolean) = write { store.setKeepScreenOn(on) }

    fun onStartAt(startAt: StartAt) = write { store.setStartAt(startAt) }

    fun onOverlayOpacity(percent: Int) = write { store.setOverlayOpacity(percent) }

    fun onPreset(preset: Preset) = write { store.setPreset(preset) }

    fun onBrowsedLimit(mib: Int) = write { store.setBrowsedLimit(mib) }

    private fun write(change: suspend () -> Unit) {
        viewModelScope.launch { change() }
    }
}
