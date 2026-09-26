package com.sunshine.app

import android.app.Application
import org.maplibre.android.MapLibre

class SunshineApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
    }
}
