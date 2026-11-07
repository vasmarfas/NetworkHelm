package com.vasmarfas.networkhelm.ui.kit

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vasmarfas.networkhelm.ui.workspace.VerticalScrollbar

// the bar has a strip of its own while the content does not fit; maxValue is Int.MAX_VALUE until the content is measured
@Composable
fun ScrollArea(
    modifier: Modifier = Modifier,
    state: ScrollState = rememberScrollState(),
    contentPadding: PaddingValues = PaddingValues(),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    val overflows = state.maxValue in 1 until Int.MAX_VALUE
    Box(modifier) {
        Column(Modifier.fillMaxWidth().verticalScroll(state).padding(contentPadding).padding(end = if (overflows) 12.dp else 0.dp), verticalArrangement, content = content)
        if (overflows) VerticalScrollbar(state, Modifier.align(Alignment.CenterEnd))
    }
}
