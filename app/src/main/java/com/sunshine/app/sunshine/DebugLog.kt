package com.sunshine.app.sunshine

import android.util.Log
import com.sunshine.app.BuildConfig

/** Timing logs for the performance budget (design of add-terrain-horizon); debug builds only. */
fun debugLog(message: String) {
    if (BuildConfig.DEBUG) Log.d("Sunshine", message)
}
