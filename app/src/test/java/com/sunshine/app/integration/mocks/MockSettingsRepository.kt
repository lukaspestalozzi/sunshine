package com.sunshine.app.integration.mocks

import com.sunshine.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [SettingsRepository] for JVM integration tests.
 * Defaults to offline mode disabled so the repository fetches from the API mock.
 */
class MockSettingsRepository : SettingsRepository {
    private val _offlineModeEnabled = MutableStateFlow(false)

    override val offlineModeEnabled: Flow<Boolean> = _offlineModeEnabled

    override suspend fun setOfflineModeEnabled(enabled: Boolean) {
        _offlineModeEnabled.value = enabled
    }
}
