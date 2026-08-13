package com.myapplication.shared.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily

@OptIn(ExperimentalTextApi::class)
actual fun platformFontFamilyFromName(fontFamilyName: String): FontFamily? {
    val trimmed = fontFamilyName.trim()
    if (trimmed.isEmpty()) return null
    return FontFamily(trimmed)
}
