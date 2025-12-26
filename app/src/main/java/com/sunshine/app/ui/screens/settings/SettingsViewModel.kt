package com.sunshine.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sunshine.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val offlineModeEnabled: Boolean = false,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            settingsRepository.offlineModeEnabled.collect { enabled ->
                _uiState.update { it.copy(offlineModeEnabled = enabled) }
            }
        }
    }

    fun onOfflineModeChanged(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setOfflineModeEnabled(enabled)
        }
    }
}
