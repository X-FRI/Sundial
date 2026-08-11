package com.myapplication.shared.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun RemindersTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkRemColors else LightRemColors
    CompositionLocalProvider(
        LocalRemColors provides colors,
    ) {
        content()
    }
}
