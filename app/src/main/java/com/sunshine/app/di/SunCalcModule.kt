package com.sunshine.app.di

import com.sunshine.app.domain.service.SunCalculator
import com.sunshine.app.suncalc.CommonsSunCalculator
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
