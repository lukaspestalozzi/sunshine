package com.sunshine.app.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Repository for app settings.
 */
interface SettingsRepository {
    /** Whether offline mode is enabled */
    val offlineModeEnabled: Flow<Boolean>

    /** Set offline mode preference */
    suspend fun setOfflineModeEnabled(enabled: Boolean)
}
