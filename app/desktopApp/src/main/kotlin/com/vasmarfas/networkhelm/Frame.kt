package com.vasmarfas.networkhelm

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import com.vasmarfas.networkhelm.ui.theme.Theme
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.default
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.intui.window.styling.dark
import org.jetbrains.jewel.intui.window.styling.defaults
import org.jetbrains.jewel.intui.window.styling.lightWithLightHeader
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.window.styling.TitleBarColors
import org.jetbrains.jewel.window.styling.TitleBarMetrics
import org.jetbrains.jewel.window.styling.TitleBarStyle

@Composable
internal fun Frame(scale: Float, content: @Composable () -> Unit) {
    val palette = Theme.palette
    val dark = palette.raised.luminance() < 0.5f
    val metrics = TitleBarMetrics.defaults(height = (Theme.metrics.toolbar * scale + 8.dp).coerceAtLeast(32.dp))
    val titleBar = if (dark) {
        TitleBarStyle.dark(
            TitleBarColors.dark(backgroundColor = palette.raised, inactiveBackground = palette.raised, contentColor = palette.text, borderColor = palette.line),
            metrics,
        )
    } else {
        TitleBarStyle.lightWithLightHeader(
            TitleBarColors.lightWithLightHeader(backgroundColor = palette.raised, inactiveBackground = palette.raised, contentColor = palette.text, borderColor = palette.line),
            metrics,
        )
    }
    IntUiTheme(
        if (dark) JewelTheme.darkThemeDefinition() else JewelTheme.lightThemeDefinition(),
        ComponentStyling.default().decoratedWindow(titleBarStyle = titleBar),
        content = content,
    )
}
