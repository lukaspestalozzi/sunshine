package com.sunshine.app.di

import com.sunshine.app.suncalc.CommonsSunCalculator
import com.sunshine.app.suncalc.SunCalculator
import org.koin.dsl.module

/**
 * Koin module for sun calculation dependencies.
 *
 * Uses commons-suncalc library for accurate sun position and sunrise/sunset times.
 */
val sunCalcModule =
    module {
        single<SunCalculator> { CommonsSunCalculator() }
    }
