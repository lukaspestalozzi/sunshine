package com.sunshine.app.di

import org.koin.dsl.module

/**
 * Koin module for domain layer dependencies (use cases).
 */
val domainModule =
    module {
        // Use cases will be added here as the app grows
        // Example:
        // factory { CalculateSunVisibilityUseCase(sunCalculator = get(), elevationRepository = get()) }
    }
