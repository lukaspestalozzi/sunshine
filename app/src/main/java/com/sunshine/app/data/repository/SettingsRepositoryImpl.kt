package com.sunshine.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.sunshine.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepositoryImpl(
    private val context: Context,
) : SettingsRepository {
    private object PreferencesKeys {
        val OFFLINE_MODE = booleanPreferencesKey("offline_mode")
    }

    override val offlineModeEnabled: Flow<Boolean> =
        context.dataStore.data
            .map { preferences ->
                preferences[PreferencesKeys.OFFLINE_MODE] ?: false
            }

    override suspend fun setOfflineModeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.OFFLINE_MODE] = enabled
        }
    }
}
