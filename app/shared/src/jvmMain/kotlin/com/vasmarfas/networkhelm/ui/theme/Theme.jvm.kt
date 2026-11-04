package com.vasmarfas.networkhelm.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.FontHinting
import androidx.compose.ui.text.FontRasterizationSettings
import androidx.compose.ui.text.FontSmoothing
import androidx.compose.ui.text.PlatformParagraphStyle
import androidx.compose.ui.text.PlatformTextStyle

// Skia on Windows ignores ClearType and draws grey text; whole-pixel glyph positions make the stems crisper
@OptIn(ExperimentalTextApi::class)
internal actual val textRasterization: PlatformTextStyle? =
    if (System.getProperty("os.name").orEmpty().startsWith("Windows")) {
        PlatformTextStyle(null, PlatformParagraphStyle(FontRasterizationSettings(FontSmoothing.AntiAlias, FontHinting.Normal, subpixelPositioning = false, autoHintingForced = false)))
    } else {
        null
    }

internal actual val systemFonts: Boolean = true
