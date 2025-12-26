package com.sunshine.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = SunshinePrimary,
    onPrimary = SunshineLightOnPrimary,
    primaryContainer = SunshinePrimaryVariant,
    secondary = SunshineSecondary,
    onSecondary = SunshineLightOnSecondary,
    background = SunshineLightBackground,
    onBackground = SunshineLightOnBackground,
    surface = SunshineLightSurface,
    onSurface = SunshineLightOnSurface,
    error = SunshineError,
    onError = SunshineOnError,
)

private val DarkColorScheme = darkColorScheme(
    primary = SunshinePrimary,
    onPrimary = SunshineDarkOnPrimary,
    primaryContainer = SunshinePrimaryVariant,
    secondary = SunshineSecondary,
    onSecondary = SunshineDarkOnSecondary,
    background = SunshineDarkBackground,
    onBackground = SunshineDarkOnBackground,
    surface = SunshineDarkSurface,
    onSurface = SunshineDarkOnSurface,
    error = SunshineError,
    onError = SunshineOnError,
)

@Composable
fun SunshineTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
