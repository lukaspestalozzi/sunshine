package com.sunshine.app

import android.app.Application
import com.sunshine.app.data.repository.ElevationRepositoryImpl
import com.sunshine.app.di.appModule
import com.sunshine.app.di.dataModule
import com.sunshine.app.di.domainModule
import com.sunshine.app.di.sunCalcModule
import com.sunshine.app.domain.repository.ElevationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.osmdroid.config.Configuration
import timber.log.Timber

class SunshineApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        initTimber()
        initOsmdroid()
        initKoin()
        evictStaleElevationCache()
    }

    private fun evictStaleElevationCache() {
        appScope.launch {
            val repo = GlobalContext.get().get<ElevationRepository>()
            (repo as? ElevationRepositoryImpl)?.evictStaleCache()
        }
    }

    private fun initTimber() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
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
