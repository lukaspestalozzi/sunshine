package com.sunshine.app.di

import com.sunshine.app.suncalc.SimpleSunCalculator
import com.sunshine.app.suncalc.SunCalculator
import org.koin.dsl.module

/**
 * Koin module for sun calculation dependencies.
 *
 * This module is designed to be easily swappable to allow different
 * sun calculation implementations (local library, PeakFinder API, etc.)
 */
val sunCalcModule = module {
    // Default implementation - simple local calculator
    // To switch implementations, just change this binding
    single<SunCalculator> { SimpleSunCalculator() }
}
