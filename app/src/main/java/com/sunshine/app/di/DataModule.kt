package com.sunshine.app.di

import com.sunshine.app.data.repository.SettingsRepositoryImpl
import com.sunshine.app.domain.repository.SettingsRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Koin module for data layer dependencies (repositories, data sources).
 */
val dataModule = module {
    single<SettingsRepository> { SettingsRepositoryImpl(context = androidContext()) }
}
