package com.myapplication.shared.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    background = Color(0xFFFAFAFC),
    onBackground = Color(0xFF111111),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFF8E8E93),
    outline = Color(0xFFC7C7CC),
    error = Color(0xFFFF3B30),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    background = Color(0xFF2C2C2E),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF3A3A3C),
    onSurfaceVariant = Color(0xFFAEAEB2),
    outline = Color(0xFF636366),
    error = Color(0xFFFF453A),
    onError = Color.White,
)

@Composable
fun RemindersTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkRemColors else LightRemColors
    CompositionLocalProvider(
        LocalRemColors provides colors,
        LocalTextStyle provides RemType.text13.copy(color = colors.textPrimary),
    ) {
        MaterialTheme(
            colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
            content = content,
        )
    }
}
