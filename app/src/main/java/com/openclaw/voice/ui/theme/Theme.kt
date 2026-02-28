package com.openclaw.voice.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFff6b35),      // Orange accent
    secondary = Color(0xFFff8c5a),    // Lighter orange
    tertiary = Color(0xFFff5a2d),     // Bright orange
    background = Color(0xFF0d1117),   // Dark background
    surface = Color(0xFF161b22),      // Slightly lighter surface
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFFf0f6fc),
    onSurface = Color(0xFFf0f6fc)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFff5a2d),      // Orange accent
    secondary = Color(0xFFff7a3d),   // Lighter orange
    tertiary = Color(0xFFe14a22),    // Darker orange
    background = Color(0xFFf6f6f6),  // Light background
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1a1a1a),
    onSurface = Color(0xFF1a1a1a)
)

@Composable
fun OpenClawVoiceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}