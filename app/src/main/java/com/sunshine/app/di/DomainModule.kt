package com.sunshine.app.di

import com.sunshine.app.domain.usecase.CalculateSunVisibilityUseCase
import org.koin.dsl.module

/**
 * Koin module for domain layer dependencies (use cases).
 */
val domainModule =
    module {
        // Use cases
        factory {
            CalculateSunVisibilityUseCase(
                sunCalculator = get(),
                elevationRepository = get(),
            )
        }
    }
