package com.vasmarfas.networkhelm.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.vasmarfas.networkhelm.ui.theme.Theme

// the list of changes of a version of RouterOS from the vendor's server, for a router that does not tell it itself
val LocalChangelog = staticCompositionLocalOf<(suspend (String) -> String?)?> { null }

@Composable
internal fun ChangelogBox(own: String?, version: String?, fetchable: Boolean) {
    val fetch = LocalChangelog.current
    val fetched by produceState<String?>(null, own == null, fetchable, version) {
        value = null
        if (own == null && fetchable && fetch != null && !version.isNullOrEmpty()) value = fetch(version)
    }
    val palette = Theme.palette
    val shape = RoundedCornerShape(Theme.metrics.radius)
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(240.dp).clip(shape).background(palette.surface).border(1.dp, palette.line, shape).testTag("changelog")) {
        SelectionContainer {
            Text((own ?: fetched).orEmpty().trimEnd(), Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp), style = Theme.type.mono)
        }
    }
}
