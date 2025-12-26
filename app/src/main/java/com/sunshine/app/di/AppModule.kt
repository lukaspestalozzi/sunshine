package com.sunshine.app.di

import com.sunshine.app.ui.screens.map.MapViewModel
import com.sunshine.app.ui.screens.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module for app-level dependencies (ViewModels).
 */
val appModule =
    module {
        viewModel { MapViewModel(sunCalculator = get()) }
        viewModel { SettingsViewModel(settingsRepository = get()) }
    }
