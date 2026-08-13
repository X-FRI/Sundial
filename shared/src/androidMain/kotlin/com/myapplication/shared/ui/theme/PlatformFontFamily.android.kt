package com.myapplication.shared.ui.theme

import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily

actual fun platformFontFamilyFromName(fontFamilyName: String): FontFamily? {
    val trimmed = fontFamilyName.trim()
    if (trimmed.isEmpty()) return null
    return FontFamily(Typeface.create(trimmed, Typeface.NORMAL))
}
