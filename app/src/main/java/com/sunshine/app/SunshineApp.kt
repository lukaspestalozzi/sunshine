package com.sunshine.app

import android.app.Application
import com.sunshine.app.di.appModule
import com.sunshine.app.di.dataModule
import com.sunshine.app.di.domainModule
import com.sunshine.app.di.sunCalcModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.osmdroid.config.Configuration

class SunshineApp : Application() {
    override fun onCreate() {
        super.onCreate()

        initOsmdroid()
        initKoin()
    }

    private fun initOsmdroid() {
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = filesDir
            osmdroidTileCache = cacheDir.resolve("tiles")
        }
    }

    private fun initKoin() {
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@SunshineApp)
            modules(
                appModule,
                dataModule,
                domainModule,
                sunCalcModule,
            )
        }
    }
}
