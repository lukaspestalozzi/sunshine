package com.sunshine.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The Settings page's state and changes (settings spec, "Settings page"; design D10 of add-settings). */
class SettingsViewModel(
    private val store: SettingsStore,
    regionNotComplete: Flow<Boolean> = flowOf(false),
    /** Clears the browsed tiles; `false` when some could not be removed. */
    private val clearBrowsed: suspend () -> Boolean = { true },
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

    private val mutableCustomDraft = MutableStateFlow<Resolution?>(null)

    /**
     * The values being edited on the `Custom resolution` page, held here so that they survive a
     * rotation; `null` while the page is not open (design D3 of add-settings).
     */
    val customDraft: StateFlow<Resolution?> = mutableCustomDraft.asStateFlow()

    /** The page was shown; a draft already open, e.g. before a rotation, is kept. */
    fun onCustomOpened() {
        if (mutableCustomDraft.value == null) mutableCustomDraft.value = store.settings.value.custom
    }

    fun onCustomEdited(custom: Resolution) {
        mutableCustomDraft.value = custom
    }

    /** The user left the page: the draft takes effect now, not at every step of the adjustment. */
    fun onCustomClosed() {
        val draft = mutableCustomDraft.value ?: return
        mutableCustomDraft.value = null
        if (draft != store.settings.value.custom) write { store.setCustom(draft) }
    }

    /** Clears the browsed tiles, then calls [done] with whether all of them were removed. */
    fun onClearBrowsed(done: (Boolean) -> Unit) = write { done(clearBrowsed()) }

    private fun write(change: suspend () -> Unit) {
        viewModelScope.launch { change() }
    }
}
