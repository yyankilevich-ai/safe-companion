package com.safecompanion.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val Brand = Color(0xFF1B5E4B)
private val BrandLight = Color(0xFF4C8C77)
private val Amber = Color(0xFFB26A00)
private val Danger = Color(0xFFB3261E)

private val LightColors = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    secondary = BrandLight,
    error = Danger,
    background = Color(0xFFF6F7F6),
    surface = Color.White
)

private val DarkColors = darkColorScheme(
    primary = BrandLight,
    onPrimary = Color(0xFF06231A),
    secondary = Brand,
    error = Color(0xFFF2B8B5),
    background = Color(0xFF10130F),
    surface = Color(0xFF181C18)
)

// Severity accents used by the UI.
val SeverityLow = Color(0xFF3E7C59)
val SeverityMedium = Amber
val SeverityHigh = Danger

@Composable
fun SafeCompanionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(colorScheme = colors, content = content)
}
